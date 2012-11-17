/**
 * Copyright (C) 2012 Google, Inc.
 * Copyright (C) 2012 Kurt Raschke
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.kurtraschke.wmatagtfsrealtime;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.kurtraschke.wmatagtfsrealtime.api.WMATAAlert;
import com.kurtraschke.wmatagtfsrealtime.api.WMATABusPosition;
import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.trips.TripDetailsBean;
import org.onebusaway.transit_data.model.trips.TripDetailsInclusionBean;
import org.onebusaway.transit_data.model.trips.TripDetailsQueryBean;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeExporterModule;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeLibrary;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeMutableProvider;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * This class produces GTFS-realtime trip updates and vehicle positions by
 * periodically polling the custom SEPTA vehicle data API and converting the
 * resulting vehicle data into the GTFS-realtime format.
 *
 * Since this class implements {@link GtfsRealtimeProvider}, it will
 * automatically be queried by the {@link GtfsRealtimeExporterModule} to export
 * the GTFS-realtime feeds to file or to host them using a simple web-server, as
 * configured by the client.
 *
 * @author bdferris
 *
 */
@Singleton
public class GTFSRealtimeProviderImpl {

    private static final Logger _log = LoggerFactory.getLogger(GTFSRealtimeProviderImpl.class);
    private ScheduledExecutorService _executor;
    private GtfsRealtimeMutableProvider _gtfsRealtimeProvider;
    private WMATAAPIService _api;
    private WMATARouteMapperService _routeMapperService;
    private WMATATripMapperService _tripMapperService;
    private TransitDataServiceService _tds;
    /**
     * How often vehicle data will be downloaded, in seconds.
     */
    private int _refreshInterval = 30;

    @Inject
    public void setGtfsRealtimeProvider(GtfsRealtimeMutableProvider gtfsRealtimeProvider) {
        _gtfsRealtimeProvider = gtfsRealtimeProvider;
    }

    @Inject
    public void setWMATAAPIService(WMATAAPIService api) {
        _api = api;
    }

    @Inject
    public void setWMATARouteMapperService(WMATARouteMapperService mapperService) {
        _routeMapperService = mapperService;
    }

    @Inject
    public void setWMATATripMapperSerivice(WMATATripMapperService tripMapperService) {
        _tripMapperService = tripMapperService;
    }

    @Inject
    public void setTransitDataServiceService(TransitDataServiceService tds) {
        _tds = tds;
    }

    /**
     * @param refreshInterval how often vehicle data will be downloaded, in
     * seconds.
     */
    public void setRefreshInterval(int refreshInterval) {
        _refreshInterval = refreshInterval;
    }

    /**
     * The start method automatically starts up a recurring task that
     * periodically downloads the latest vehicle data from the SEPTA vehicle
     * stream and processes them.
     */
    @PostConstruct
    public void start() {
        _log.info("starting GTFS-realtime service");
        _executor = Executors.newSingleThreadScheduledExecutor();
        _executor.scheduleWithFixedDelay(new VehiclesRefreshTask(), 0,
         _refreshInterval, TimeUnit.SECONDS);
        _executor.scheduleWithFixedDelay(new AlertsRefreshTask(), 0,
                2 * _refreshInterval, TimeUnit.SECONDS);
    }

    /**
     * The stop method cancels the recurring vehicle data downloader task.
     */
    @PreDestroy
    public void stop() {
        _log.info("stopping GTFS-realtime service");
        _executor.shutdownNow();
    }

    /**
     * **
     * Private Methods - Here is where the real work happens **
     */
    /**
     * This method downloads the latest vehicle data, processes each vehicle in
     * turn, and create a GTFS-realtime feed of trip updates and vehicle
     * positions as a result.
     */
    private void refreshVehicles() throws IOException, SAXException {


        /**
         * We download the vehicle details as an array of JSON objects.
         */
        List<WMATABusPosition> busPositions = _api.downloadBusPositions();

        /**
         * The FeedMessage.Builder is what we will use to build up our
         * GTFS-realtime feeds. We create a feed for both trip updates and
         * vehicle positions.
         */
        FeedMessage.Builder tripUpdates = GtfsRealtimeLibrary.createFeedMessageBuilder();
        FeedMessage.Builder vehiclePositions = GtfsRealtimeLibrary.createFeedMessageBuilder();

        /**
         * We iterate over every JSON vehicle object.
         */
        for (WMATABusPosition bp : busPositions) {

            String route = bp.getRouteID();
            String vehicle = bp.getVehicleID();
            String trip = bp.getTripID();
            Date dateTime = bp.getDateTime();

            float lat = bp.getLat();
            float lon = bp.getLon();
            float deviation = bp.getDeviation();

            String gtfsRouteID;
            String gtfsTripID = null;

            gtfsRouteID = _routeMapperService.getRouteMap().get(route);

            if (gtfsRouteID != null) {
                Date serviceDate = new GregorianCalendar(2012, 11 - 1, 17).getTime();

                gtfsTripID = _tripMapperService.getTripMapping(serviceDate, trip, route);

                System.out.println("Mapped WMATA trip " + trip + " to GTFS trip " + gtfsTripID);
            }

            /**
             * We construct a TripDescriptor and VehicleDescriptor, which will
             * be used in both trip updates and vehicle positions to identify
             * the trip and vehicle. Ideally, we would have a trip id to use for
             * the trip descriptor, but the SEPTA api doesn't include it, so we
             * settle for a route id instead.
             */
            TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
            if (gtfsRouteID != null) {
                //tripDescriptor.setRouteId(stripID(gtfsRouteID));
            }
            if (gtfsTripID != null) {
                tripDescriptor.setTripId(stripID(gtfsTripID));
            }

            VehicleDescriptor.Builder vehicleDescriptor = VehicleDescriptor.newBuilder();
            vehicleDescriptor.setId(vehicle);

            /**
             * To construct our TripUpdate, we create a stop-time arrival event
             * for the next stop for the vehicle, with the specified arrival
             * delay. We add the stop-time update to a TripUpdate builder, along
             * with the trip and vehicle descriptors.
             */
            StopTimeEvent.Builder arrival = StopTimeEvent.newBuilder();
            arrival.setDelay(Math.round(deviation * -60));

            StopTimeUpdate.Builder stopTimeUpdate = StopTimeUpdate.newBuilder();
            stopTimeUpdate.setArrival(arrival);
            if (gtfsTripID != null) {
                stopTimeUpdate.setStopId(stripID(getLastStopForTrip(gtfsTripID)));
            }

            TripUpdate.Builder tripUpdate = TripUpdate.newBuilder();
            tripUpdate.addStopTimeUpdate(stopTimeUpdate);
            tripUpdate.setTrip(tripDescriptor);
            tripUpdate.setVehicle(vehicleDescriptor);
            /**
             * Create a new feed entity to wrap the trip update and add it to
             * the GTFS-realtime trip updates feed.
             */
            FeedEntity.Builder tripUpdateEntity = FeedEntity.newBuilder();
            tripUpdateEntity.setId(vehicle);
            tripUpdateEntity.setTripUpdate(tripUpdate);
            tripUpdates.addEntity(tripUpdateEntity);
            /**
             * To construct our VehiclePosition, we create a position for the
             * vehicle. We add the position to a VehiclePosition builder, along
             * with the trip and vehicle descriptors.
             */
            Position.Builder position = Position.newBuilder();
            position.setLatitude((float) lat);
            position.setLongitude((float) lon);

            VehiclePosition.Builder vehiclePosition = VehiclePosition.newBuilder();
            vehiclePosition.setTimestamp(dateTime.getTime() / 1000L);
            vehiclePosition.setPosition(position);
            vehiclePosition.setTrip(tripDescriptor);
            vehiclePosition.setVehicle(vehicleDescriptor);

            /**
             * Create a new feed entity to wrap the vehicle position and add it
             * to the GTFS-realtime vehicle positions feed.
             */
            FeedEntity.Builder vehiclePositionEntity = FeedEntity.newBuilder();
            vehiclePositionEntity.setId(vehicle);
            vehiclePositionEntity.setVehicle(vehiclePosition);
            vehiclePositions.addEntity(vehiclePositionEntity);
        }

        /**
         * Build out the final GTFS-realtime feed messagse and save them.
         */
        _gtfsRealtimeProvider.setTripUpdates(tripUpdates.build());
        _gtfsRealtimeProvider.setVehiclePositions(vehiclePositions.build());

        _log.info("vehicles extracted: " + tripUpdates.getEntityCount());
    }

    private String getLastStopForTrip(String tripID) {
        TripDetailsQueryBean tdqb = new TripDetailsQueryBean();
        tdqb.setTripId(tripID);
        tdqb.setInclusion(new TripDetailsInclusionBean(true, true, false));
        ListBean<TripDetailsBean> b = _tds.getService().getTripDetails(tdqb);

        return Iterables.getLast(Iterables.getOnlyElement(b.getList()).getSchedule().getStopTimes()).getStop().getId();


    }

    private String stripID(String id) {
        return id.split("_", 2)[1];
    }

    private void refreshAlerts() throws IOException, SAXException {

        List<WMATAAlert> busAlerts = _api.downloadBusAlerts();
        List<WMATAAlert> railAlerts = _api.downloadRailAlerts();

        FeedMessage.Builder alerts = GtfsRealtimeLibrary.createFeedMessageBuilder();


        for (WMATAAlert busAlert : busAlerts) {
            Alert.Builder alert = Alert.newBuilder();

            alert.setDescriptionText(GtfsRealtimeLibrary.getTextAsTranslatedString(busAlert.getDescription()));

            String[] routes = busAlert.getTitle().split(", ");

            for (String route : routes) {
                EntitySelector.Builder entity = EntitySelector.newBuilder();
                String gtfsRoute = _routeMapperService.getFilteredRouteMap().get(route);
                if (gtfsRoute != null) {
                    entity.setRouteId(stripID(gtfsRoute));
                    alert.addInformedEntity(entity);
                }
            }

            if (alert.getInformedEntityCount() > 0) {
                FeedEntity.Builder alertEntity = FeedEntity.newBuilder();
                alertEntity.setId(busAlert.getGuid().toString());
                alertEntity.setAlert(alert);
                alerts.addEntity(alertEntity);
            }
        }

        List<RouteBean> gtfsRoutes = _tds.getService().getRoutesForAgencyId("3030-2").getList();


        for (WMATAAlert railAlert : railAlerts) {

            Alert.Builder alert = Alert.newBuilder();

            alert.setDescriptionText(GtfsRealtimeLibrary.getTextAsTranslatedString(railAlert.getDescription()));

            String[] routes = railAlert.getTitle().split(", ");

            for (final String route : routes) {
                EntitySelector.Builder entity = EntitySelector.newBuilder();
                
                Optional<RouteBean> matchedRoute = Iterables.tryFind(gtfsRoutes, new Predicate<RouteBean>() {
                        public boolean apply(RouteBean gr) {
                            if (gr.getShortName() != null) {
                                return gr.getShortName().equalsIgnoreCase(route);
                            } else {
                                return false;
                            }
                        }
                    });
                
                if (matchedRoute.isPresent()) {
                    entity.setRouteId(stripID(matchedRoute.get().getId()));
                    alert.addInformedEntity(entity);
                }
            }

            if (alert.getInformedEntityCount() > 0) {
                FeedEntity.Builder alertEntity = FeedEntity.newBuilder();
                alertEntity.setId(railAlert.getGuid().toString());
                alertEntity.setAlert(alert);
                alerts.addEntity(alertEntity);
            }

        }

        _gtfsRealtimeProvider.setAlerts(alerts.build());

        _log.info("alerts extracted: " + alerts.getEntityCount());
    }

    /**
     * Task that will download new vehicle data from the remote data source when
     * executed.
     */
    private class VehiclesRefreshTask implements Runnable {

        @Override
        public void run() {
            try {
                _log.info("refreshing vehicles");
                refreshVehicles();
            } catch (Exception ex) {
                _log.warn("Error in vehicle refresh task", ex);
            }
        }
    }

    /**
     * Task that will download new alert data from the remote data source when
     * executed.
     */
    private class AlertsRefreshTask implements Runnable {

        @Override
        public void run() {
            try {
                _log.info("refreshing alerts");
                refreshAlerts();
            } catch (Exception ex) {
                _log.warn("Error in alert refresh task", ex);
            }
        }
    }
}