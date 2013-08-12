/*
 * Copyright (C) 2012 Google, Inc.
 * Copyright (C) 2013 Kurt Raschke
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeFullUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.Alerts;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.VehiclePositions;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeLibrary;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;
import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.trips.TripDetailsBean;
import org.onebusaway.transit_data.model.trips.TripDetailsInclusionBean;
import org.onebusaway.transit_data.model.trips.TripDetailsQueryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * This class produces GTFS-realtime trip updates and vehicle positions by
 * periodically polling the custom WMATA vehicle data API and converting the
 * resulting vehicle data into the GTFS-realtime format.
 *
 *
 * @author bdferris
 *
 */
@Singleton
public class GTFSRealtimeProviderImpl {

    private static final Logger _log = LoggerFactory.getLogger(GTFSRealtimeProviderImpl.class);
    private ScheduledExecutorService _executor;
    private WMATAAPIService _api;
    private WMATARouteMapperService _routeMapperService;
    private WMATATripMapperService _tripMapperService;
    private TransitDataServiceService _tds;
    private CacheManager _cacheManager;
    private Cache _alertIDCache;
    /**
     * How often vehicle data will be downloaded, in seconds.
     */
    @Inject
    @Named("refreshInterval.vehicles")
    private int _vehicleRefreshInterval;
    @Inject
    @Named("refreshInterval.alerts")
    private int _alertRefreshInterval;
    private GtfsRealtimeSink _vehiclePositionsSink;
    private GtfsRealtimeSink _tripUpdatesSink;
    private GtfsRealtimeSink _alertsSink;

    @Inject
    public void setVehiclePositionsSink(@VehiclePositions GtfsRealtimeSink sink) {
        _vehiclePositionsSink = sink;
    }

    @Inject
    public void setTripUpdateSink(@TripUpdates GtfsRealtimeSink sink) {
        _tripUpdatesSink = sink;
    }

    @Inject
    public void setAlertsSink(@Alerts GtfsRealtimeSink sink) {
        _alertsSink = sink;
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
    public void setWMATATripMapperService(WMATATripMapperService tripMapperService) {
        _tripMapperService = tripMapperService;
    }

    @Inject
    public void setTransitDataServiceService(TransitDataServiceService tds) {
        _tds = tds;
    }

    @Inject
    public void setCacheManager(CacheManager cacheManager) {
        _cacheManager = cacheManager;
    }

    @Inject
    public void setAlertIDCache(@Named("caches.alertID") Cache alertIDCache) {
        _alertIDCache = alertIDCache;
    }

    /**
     * The start method automatically starts up a recurring task that
     * periodically downloads the latest vehicle and alert data from the WMATA
     * API and processes them.
     */
    @PostConstruct
    public void start() {
        _log.info("starting GTFS-realtime service");
        _executor = Executors.newSingleThreadScheduledExecutor();
        _executor.scheduleWithFixedDelay(new VehiclesRefreshTask(), 0,
                _vehicleRefreshInterval, TimeUnit.SECONDS);
        _executor.scheduleWithFixedDelay(new AlertsRefreshTask(), 0,
                _alertRefreshInterval, TimeUnit.SECONDS);
    }

    /**
     * The stop method cancels the recurring vehicle data downloader task.
     */
    @PreDestroy
    public void stop() {
        _log.info("stopping GTFS-realtime service");
        _executor.shutdownNow();
        _cacheManager.shutdown();
    }

    /**
     *
     * Private Methods - Here is where the real work happens
     */
    /**
     * This method downloads the latest vehicle data, processes each vehicle in
     * turn, and create a GTFS-realtime feed of trip updates and vehicle
     * positions as a result.
     */
    private void refreshVehicles() throws IOException, SAXException {


        /**
         * We download the vehicle details as an array of objects.
         */
        List<WMATABusPosition> busPositions = _api.downloadBusPositions();

        GtfsRealtimeFullUpdate tripUpdatesUpdate = new GtfsRealtimeFullUpdate();
        GtfsRealtimeFullUpdate vehiclePositionsUpdate = new GtfsRealtimeFullUpdate();

        /**
         * We iterate over every vehicle object.
         */
        for (WMATABusPosition bp : busPositions) {
            try {
                ProcessedVehicleResponse pvr = processVehicle(bp);

                tripUpdatesUpdate.addEntity(pvr.tripUpdateEntity.build());
                vehiclePositionsUpdate.addEntity(pvr.vehiclePositionEntity.build());
            } catch (Exception e) {
                _log.warn("Error constructing update for vehicle " + bp.getVehicleID() + " on route " + bp.getRouteID() + " to " + bp.getTripHeadsign(), e);
            }
        }

        /*
         * Build out the final GTFS-realtime feed messages and save them.
         */

        _tripUpdatesSink.handleFullUpdate(tripUpdatesUpdate);
        _vehiclePositionsSink.handleFullUpdate(vehiclePositionsUpdate);

        _log.info("vehicles extracted: " + vehiclePositionsUpdate.getEntities().size());
    }

    private ProcessedVehicleResponse processVehicle(WMATABusPosition bp) throws IOException, SAXException {
        ProcessedVehicleResponse pvr = new ProcessedVehicleResponse();

        String route = bp.getRouteID();
        String vehicle = bp.getVehicleID();
        String trip = bp.getTripID();
        Date dateTime = bp.getDateTime();

        float lat = bp.getLat();
        float lon = bp.getLon();
        float deviation = bp.getDeviation();

        String gtfsRouteID;
        String gtfsTripID = null;

        gtfsRouteID = _routeMapperService.getBusRouteMapping(route);

        if (gtfsRouteID != null) {
            GregorianCalendar serviceDate = new GregorianCalendar();
            serviceDate.setTime(bp.getTripStartTime());
            serviceDate.set(Calendar.HOUR_OF_DAY, 0);
            serviceDate.set(Calendar.MINUTE, 0);
            serviceDate.set(Calendar.SECOND, 0);
            serviceDate.set(Calendar.MILLISECOND, 0);

            gtfsTripID = _tripMapperService.getTripMapping(serviceDate.getTime(), trip, route);
        }
        /**
         * We construct a TripDescriptor and VehicleDescriptor, which will be
         * used in both trip updates and vehicle positions to identify the trip
         * and vehicle.
         */
        TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
        if (gtfsRouteID != null) {
            tripDescriptor.setRouteId(stripID(gtfsRouteID));
        }
        if (gtfsTripID != null) {
            tripDescriptor.setTripId(stripID(gtfsTripID));
        }

        VehicleDescriptor.Builder vehicleDescriptor = VehicleDescriptor.newBuilder();
        vehicleDescriptor.setId(vehicle);

        /**
         * To construct our TripUpdate, we create a stop-time arrival event for
         * the first stop for the vehicle, with the specified arrival delay. We
         * add the stop-time update to a TripUpdate builder, along with the trip
         * and vehicle descriptors.
         */
        StopTimeEvent.Builder arrival = StopTimeEvent.newBuilder();
        arrival.setDelay(Math.round(deviation * -60));

        StopTimeUpdate.Builder stopTimeUpdate = StopTimeUpdate.newBuilder();
        stopTimeUpdate.setArrival(arrival);
        if (gtfsTripID != null) {
            stopTimeUpdate.setStopId(stripID(getStopIdForTripId(gtfsTripID)));
        }

        TripUpdate.Builder tripUpdate = TripUpdate.newBuilder();
        tripUpdate.addStopTimeUpdate(stopTimeUpdate);
        tripUpdate.setTrip(tripDescriptor);
        tripUpdate.setVehicle(vehicleDescriptor);
        /**
         * Create a new feed entity to wrap the trip update and add it to the
         * GTFS-realtime trip updates feed.
         */
        FeedEntity.Builder tripUpdateEntity = FeedEntity.newBuilder();
        tripUpdateEntity.setId(vehicle);
        tripUpdateEntity.setTripUpdate(tripUpdate);
        pvr.tripUpdateEntity = tripUpdateEntity;
        /**
         * To construct our VehiclePosition, we create a position for the
         * vehicle. We add the position to a VehiclePosition builder, along with
         * the trip and vehicle descriptors.
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
         * Create a new feed entity to wrap the vehicle position and add it to
         * the GTFS-realtime vehicle positions feed.
         */
        FeedEntity.Builder vehiclePositionEntity = FeedEntity.newBuilder();
        vehiclePositionEntity.setId(vehicle);
        vehiclePositionEntity.setVehicle(vehiclePosition);
        pvr.vehiclePositionEntity = vehiclePositionEntity;

        return pvr;
    }

    private String getStopIdForTripId(String tripID) {
        TripDetailsQueryBean tdqb = new TripDetailsQueryBean();
        tdqb.setTripId(tripID);
        tdqb.setInclusion(new TripDetailsInclusionBean(true, true, true));
        ListBean<TripDetailsBean> b = _tds.getService().getTripDetails(tdqb);

        TripDetailsBean tdb = Iterables.getOnlyElement(b.getList());
        
        if (tdb.getStatus() != null && tdb.getStatus().getNextStop() != null) {
            return tdb.getStatus().getNextStop().getId();
        } else {
            return tdb.getSchedule().getStopTimes().get(1).getStop().getId();
        }
        
    }

    private String stripID(String id) {
        return id.split("_", 2)[1];
    }

    @SuppressWarnings("unchecked")
    private void refreshAlerts() throws IOException, SAXException {

        List<WMATAAlert> busAlerts = _api.downloadBusAlerts();
        List<WMATAAlert> railAlerts = _api.downloadRailAlerts();

        GtfsRealtimeFullUpdate alertsUpdate = new GtfsRealtimeFullUpdate();

        for (WMATAAlert busAlert : busAlerts) {
            Alert.Builder alert = Alert.newBuilder();

            alert.setDescriptionText(GtfsRealtimeLibrary.getTextAsTranslatedString(busAlert.getDescription()));

            String[] routes = busAlert.getTitle().split(", ");

            for (String route : routes) {
                EntitySelector.Builder entity = EntitySelector.newBuilder();
                String gtfsRoute = _routeMapperService.getBusRouteMapping(route);
                if (gtfsRoute != null) {
                    entity.setRouteId(stripID(gtfsRoute));
                    alert.addInformedEntity(entity);
                }
            }

            if (alert.getInformedEntityCount() > 0) {
                FeedEntity.Builder alertEntity = FeedEntity.newBuilder();
                alertEntity.setId(busAlert.getGuid().toString());
                alertEntity.setAlert(alert);
                alertsUpdate.addEntity(alertEntity.build());
                _alertIDCache.put(new Element(busAlert.getGuid().toString(), null));
            }
        }

        for (WMATAAlert railAlert : railAlerts) {
            Alert.Builder alert = Alert.newBuilder();

            alert.setDescriptionText(GtfsRealtimeLibrary.getTextAsTranslatedString(railAlert.getDescription()));

            String[] routes = railAlert.getTitle().split(", ");

            for (String route : routes) {

                String mappedRoute = _routeMapperService.getRailRouteMapping(route);

                if (mappedRoute != null) {
                    EntitySelector.Builder entity = EntitySelector.newBuilder();
                    entity.setRouteId(stripID(mappedRoute));
                    alert.addInformedEntity(entity);
                }
            }

            if (alert.getInformedEntityCount() > 0) {
                FeedEntity.Builder alertEntity = FeedEntity.newBuilder();
                alertEntity.setId(railAlert.getGuid().toString());
                alertEntity.setAlert(alert);
                alertsUpdate.addEntity(alertEntity.build());
                _alertIDCache.put(new Element(railAlert.getGuid().toString(), null));
            }

        }

        Set<String> currentAlertIDs = new HashSet<String>();

        for (FeedEntity e : alertsUpdate.getEntities()) {
            currentAlertIDs.add(e.getId());
        }

        ImmutableSet<String> allAlertIDs = ImmutableSet.copyOf(_alertIDCache.getKeysWithExpiryCheck());

        /*
         * If an alert was in the feed previously, and is not now,
         * then add it back to the feed with the isDeleted flag set, so
         * clients will remove it from their UI.
         */
        for (String removedAlert : Sets.difference(allAlertIDs, currentAlertIDs)) {
            FeedEntity.Builder alertEntity = FeedEntity.newBuilder();
            alertEntity.setId(removedAlert);
            alertEntity.setIsDeleted(true);
            alertsUpdate.addEntity(alertEntity.build());
        }

        _alertsSink.handleFullUpdate(alertsUpdate);

        _log.info("alerts extracted: " + alertsUpdate.getEntities().size());
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

    private class ProcessedVehicleResponse {

        FeedEntity.Builder tripUpdateEntity;
        FeedEntity.Builder vehiclePositionEntity;
    }
}
