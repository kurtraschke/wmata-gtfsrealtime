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

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.Alerts;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.VehiclePositions;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeIncrementalUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeLibrary;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;

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
import com.kurtraschke.wmatagtfsrealtime.api.WMATARoute;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * This class produces GTFS-realtime trip updates and vehicle positions by
 * periodically polling the custom WMATA vehicle data API and converting the
 * resulting vehicle data into the GTFS-realtime format.
 *
 *
 */
@Singleton
public class GTFSRealtimeProviderImpl {

  private static final Logger _log = LoggerFactory.getLogger(GTFSRealtimeProviderImpl.class);
  private ScheduledExecutorService _executor;
  private WMATAAPIService _api;
  private WMATARouteMapperService _routeMapperService;
  private WMATATripMapperService _tripMapperService;
  private CacheManager _cacheManager;
  private Cache _alertIDCache;
  private GtfsRealtimeSink _vehiclePositionsSink;
  private GtfsRealtimeSink _tripUpdatesSink;
  private GtfsRealtimeSink _alertsSink;
  private Map<String, Date> lastUpdateByVehicle = new HashMap<String, Date>();
  private Map<UUID, Date> lastUpdateByAlert = new HashMap<UUID, Date>();
  /**
   * How often vehicle data will be downloaded, in seconds.
   */
  @Inject
  @Named("refreshInterval.vehicles")
  private int _vehicleRefreshInterval;
  /**
   * How often alert data will be downloaded, in seconds.
   */
  @Inject
  @Named("refreshInterval.alerts")
  private int _alertRefreshInterval;

  @Inject
  public void setVehiclePositionsSink(@VehiclePositions
  GtfsRealtimeSink sink) {
    _vehiclePositionsSink = sink;
  }

  @Inject
  public void setTripUpdateSink(@TripUpdates
  GtfsRealtimeSink sink) {
    _tripUpdatesSink = sink;
  }

  @Inject
  public void setAlertsSink(@Alerts
  GtfsRealtimeSink sink) {
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
  public void setCacheManager(CacheManager cacheManager) {
    _cacheManager = cacheManager;
  }

  @Inject
  public void setAlertIDCache(@Named("caches.alertID")
  Cache alertIDCache) {
    _alertIDCache = alertIDCache;
  }

  /**
   * The start method automatically starts up a recurring task that periodically
   * downloads the latest vehicle and alert data from the WMATA API and
   * processes them.
   */
  @PostConstruct
  public void start() {
    _log.info("Starting GTFS-realtime service");
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
    _log.info("Stopping GTFS-realtime service");
    _executor.shutdownNow();
    _cacheManager.shutdown();
  }

  /**
   * This method downloads the latest vehicle data, processes each vehicle in
   * turn, and create a GTFS-realtime feed of trip updates and vehicle positions
   * as a result.
   */
  private void refreshVehicles() throws IOException, SAXException {
    /**
     * We download the vehicle details as an array of objects.
     */
    List<WMATABusPosition> allBusPositions = new ArrayList<WMATABusPosition>();

    for (WMATARoute r : _api.downloadRouteList()) {
      List<WMATABusPosition> busPositions = _api.downloadBusPositions(r.getRouteID());
      allBusPositions.addAll(busPositions);
    }

    /**
     * We iterate over every vehicle object.
     */
    for (WMATABusPosition bp : allBusPositions) {
      //checkConsistency(bp);

      if ((!lastUpdateByVehicle.containsKey(bp.getVehicleID()))
          || bp.getDateTime().after(lastUpdateByVehicle.get(bp.getVehicleID()))) {
        try {
          ProcessedVehicleResponse pvr = processVehicle(bp);

          GtfsRealtimeIncrementalUpdate tripUpdateUpdate = new GtfsRealtimeIncrementalUpdate();
          tripUpdateUpdate.addUpdatedEntity(pvr.tripUpdateEntity.build());
          _tripUpdatesSink.handleIncrementalUpdate(tripUpdateUpdate);

          GtfsRealtimeIncrementalUpdate vehiclePositionUpdate = new GtfsRealtimeIncrementalUpdate();
          vehiclePositionUpdate.addUpdatedEntity(pvr.vehiclePositionEntity.build());
          _vehiclePositionsSink.handleIncrementalUpdate(vehiclePositionUpdate);

          lastUpdateByVehicle.put(bp.getVehicleID(), bp.getDateTime());

        } catch (Exception e) {
          _log.warn(
              "Error constructing update for vehicle " + bp.getVehicleID()
                  + " on route " + bp.getRouteID() + " to "
                  + bp.getTripHeadsign(), e);
        }
      }
    }

    _log.info("vehicles extracted: " + allBusPositions.size());
  }

  private void checkConsistency(WMATABusPosition bp) {
    boolean endAfterStart;
    boolean timestampWithinTrip;

    endAfterStart = bp.getTripEndTime().after(bp.getTripStartTime());
    timestampWithinTrip = bp.getDateTime().after(bp.getTripStartTime())
        && bp.getDateTime().before(bp.getTripEndTime());

    if (!endAfterStart || !timestampWithinTrip) {
      StringBuilder sb = new StringBuilder();
      sb.append("Update for vehicle ");
      sb.append(bp.getVehicleID());
      sb.append(" on route ");
      sb.append(bp.getRouteID());
      sb.append(" is inconsistent: ");
      if (!endAfterStart) {
        sb.append("\nTrip end time precedes trip start time");
      }
      if (!timestampWithinTrip) {
        sb.append("\nUpdate timestamp not between trip start and end times");
      }
      _log.warn(sb.toString());
    }
  }

  private ProcessedVehicleResponse processVehicle(WMATABusPosition bp)
      throws IOException, SAXException {
    ProcessedVehicleResponse pvr = new ProcessedVehicleResponse();

    String route = bp.getRouteID();
    String vehicle = bp.getVehicleID();
    Date dateTime = bp.getDateTime();

    float lat = bp.getLat();
    float lon = bp.getLon();
    float deviation = bp.getDeviation();

    AgencyAndId gtfsRouteID;
    AgencyAndId gtfsTripID = null;

    gtfsRouteID = _routeMapperService.getRouteMapping(route);

    if (gtfsRouteID != null) {
      gtfsTripID = _tripMapperService.getTripMapping(bp);
    }
    /**
     * We construct a TripDescriptor and VehicleDescriptor, which will be used
     * in both trip updates and vehicle positions to identify the trip and
     * vehicle.
     */
    TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
    if (gtfsRouteID != null) {
      tripDescriptor.setRouteId(gtfsRouteID.getId());
    }
    if (gtfsTripID != null) {
      tripDescriptor.setTripId(gtfsTripID.getId());
    }

    VehicleDescriptor.Builder vehicleDescriptor = VehicleDescriptor.newBuilder();
    vehicleDescriptor.setId(vehicle);

    /**
     * To construct our TripUpdate, we create a stop-time arrival event for the
     * next stop for the vehicle, with the specified arrival delay. We add the
     * stop-time update to a TripUpdate builder, along with the trip and vehicle
     * descriptors.
     */
    StopTimeEvent.Builder departure = StopTimeEvent.newBuilder();
    departure.setDelay(Math.round(deviation * -60));

    StopTimeUpdate.Builder stopTimeUpdate = StopTimeUpdate.newBuilder();
    stopTimeUpdate.setDeparture(departure);
    stopTimeUpdate.setStopSequence(1);

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
     * To construct our VehiclePosition, we create a position for the vehicle.
     * We add the position to a VehiclePosition builder, along with the trip and
     * vehicle descriptors.
     */
    Position.Builder position = Position.newBuilder();
    position.setLatitude(lat);
    position.setLongitude(lon);

    VehiclePosition.Builder vehiclePosition = VehiclePosition.newBuilder();
    vehiclePosition.setTimestamp(dateTime.getTime() / 1000L);
    vehiclePosition.setPosition(position);
    vehiclePosition.setTrip(tripDescriptor);
    vehiclePosition.setVehicle(vehicleDescriptor);

    /**
     * Create a new feed entity to wrap the vehicle position and add it to the
     * GTFS-realtime vehicle positions feed.
     */
    FeedEntity.Builder vehiclePositionEntity = FeedEntity.newBuilder();
    vehiclePositionEntity.setId(vehicle);
    vehiclePositionEntity.setVehicle(vehiclePosition);
    pvr.vehiclePositionEntity = vehiclePositionEntity;

    return pvr;
  }

  @SuppressWarnings("unchecked")
  private void refreshAlerts() throws IOException, SAXException {
    List<WMATAAlert> busAlerts = _api.downloadBusAlerts();
    List<WMATAAlert> railAlerts = _api.downloadRailAlerts();

    Set<UUID> currentAlertIDs = new HashSet<UUID>();

    for (WMATAAlert theAlert : Iterables.concat(busAlerts, railAlerts)) {
      if ((!lastUpdateByAlert.containsKey(theAlert.getGuid()))
          || theAlert.getPubDate().after(
              lastUpdateByAlert.get(theAlert.getGuid()))) {

        Alert.Builder alert = Alert.newBuilder();

        alert.setDescriptionText(GtfsRealtimeLibrary.getTextAsTranslatedString(theAlert.getDescription()));

        String[] routes = theAlert.getTitle().split(", ");

        for (String route : routes) {
          AgencyAndId gtfsRoute = _routeMapperService.getRouteMapping(route);
          if (gtfsRoute != null) {
            EntitySelector.Builder entity = EntitySelector.newBuilder();
            entity.setRouteId(gtfsRoute.getId());
            alert.addInformedEntity(entity);
          }
        }

        if (alert.getInformedEntityCount() > 0) {
          FeedEntity.Builder alertEntity = FeedEntity.newBuilder();
          alertEntity.setId(theAlert.getGuid().toString());
          alertEntity.setAlert(alert);

          GtfsRealtimeIncrementalUpdate alertUpdate = new GtfsRealtimeIncrementalUpdate();
          alertUpdate.addUpdatedEntity(alertEntity.build());
          _alertsSink.handleIncrementalUpdate(alertUpdate);
          _alertIDCache.put(new Element(theAlert.getGuid(), null));
          lastUpdateByAlert.put(theAlert.getGuid(), theAlert.getPubDate());
        }
      }
      currentAlertIDs.add(theAlert.getGuid());
    }

    /*
     * Flush the cache manually so we do not lose any deleted alert IDs in the
     * event of an unclean shutdown.
     */
    _alertIDCache.flush();

    ImmutableSet<UUID> allAlertIDs = ImmutableSet.copyOf(_alertIDCache.getKeysWithExpiryCheck());

    /*
     * If an alert was in the feed previously, and is not now, then add it back
     * to the feed with the isDeleted flag set, so clients will remove it from
     * their UI.
     */
    for (UUID removedAlert : Sets.difference(allAlertIDs, currentAlertIDs)) {
      GtfsRealtimeIncrementalUpdate alertUpdate = new GtfsRealtimeIncrementalUpdate();
      alertUpdate.addDeletedEntity(removedAlert.toString());
      _alertsSink.handleIncrementalUpdate(alertUpdate);
      lastUpdateByAlert.remove(removedAlert);
    }

    _log.info("alerts extracted: " + (railAlerts.size() + busAlerts.size()));
  }

  /**
   * Task that will download new vehicle data from the remote data source when
   * executed.
   */
  private class VehiclesRefreshTask implements Runnable {

    public void run() {
      try {
        _log.info("Refreshing vehicles");
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

    public void run() {
      try {
        _log.info("Refreshing alerts");
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
