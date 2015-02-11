/*
 * Copyright (C) 2014 Kurt Raschke <kurt@kurtraschke.com>
 * Copyright (C) 2012 Google, Inc.
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
package com.kurtraschke.wmata.gtfsrealtime;

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
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.kurtraschke.wmata.gtfsrealtime.api.alerts.Item;
import com.kurtraschke.wmata.gtfsrealtime.api.buspositions.BusPosition;
import com.kurtraschke.wmata.gtfsrealtime.services.WMATAAPIService;
import com.kurtraschke.wmata.gtfsrealtime.services.WMATARouteMapperService;
import com.kurtraschke.wmata.gtfsrealtime.services.WMATATripMapperService;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private Map<String, Date> lastUpdateByVehicle = new HashMap<>();
  private Map<UUID, Date> lastUpdateByAlert = new HashMap<>();
  private int _vehicleRefreshInterval;
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

  @Inject
  public void setVehicleRefreshInterval(@Named("refreshInterval.vehicles")
  int vehicleRefreshInterval) {
    _vehicleRefreshInterval = vehicleRefreshInterval;
  }

  @Inject
  public void setAlertRefreshInterval(@Named("refreshInterval.alerts")
  int alertRefreshInterval) {
    _alertRefreshInterval = alertRefreshInterval;
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
   *
   * @throws WMATAAPIException
   */
  private void refreshVehicles() throws WMATAAPIException {
    /**
     * We download the vehicle details as an array of objects.
     */
    List<BusPosition> busPositions = _api.downloadBusPositions().getBusPositions();

    /**
     * We iterate over every vehicle object.
     */
    for (BusPosition bp : busPositions) {
      // checkConsistency(bp);

      if ((!lastUpdateByVehicle.containsKey(bp.getVehicleID()))
          || bp.getDateTime().after(lastUpdateByVehicle.get(bp.getVehicleID()))) {
        try {
          processVehicle(bp);

        } catch (Exception e) {
          _log.warn(
              "Error constructing update for vehicle " + bp.getVehicleID()
                  + " on route " + bp.getRouteID() + " to "
                  + bp.getTripHeadsign(), e);
        }
      }
    }

    _log.info("vehicles extracted: " + busPositions.size());
  }

  private void checkConsistency(BusPosition bp) {
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

  private void processVehicle(BusPosition bp) throws WMATAAPIException {
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
     * first stop for the vehicle, with the specified arrival delay. We add the
     * stop-time update to a TripUpdate builder, along with the trip and vehicle
     * descriptors.
     */
    if (gtfsTripID != null) {
      // WMATA API is negative for delay, positive for early (in minutes)
      // GTFS-realtime is positive for delay, negative for early (in seconds)
      int delay = Math.round(deviation * -60);

      TripUpdate.Builder tripUpdate = TripUpdate.newBuilder();
      tripUpdate.setDelay(delay);
      tripUpdate.setTrip(tripDescriptor);
      tripUpdate.setVehicle(vehicleDescriptor);
      /**
       * Create a new feed entity to wrap the trip update and add it to the
       * GTFS-realtime trip updates feed.
       */
      FeedEntity.Builder tripUpdateEntity = FeedEntity.newBuilder();
      tripUpdateEntity.setId(vehicle);
      tripUpdateEntity.setTripUpdate(tripUpdate);
      GtfsRealtimeIncrementalUpdate tripUpdateUpdate = new GtfsRealtimeIncrementalUpdate();
      tripUpdateUpdate.addUpdatedEntity(tripUpdateEntity.build());
      _tripUpdatesSink.handleIncrementalUpdate(tripUpdateUpdate);
    }

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

    GtfsRealtimeIncrementalUpdate vehiclePositionUpdate = new GtfsRealtimeIncrementalUpdate();
    vehiclePositionUpdate.addUpdatedEntity(vehiclePositionEntity.build());
    _vehiclePositionsSink.handleIncrementalUpdate(vehiclePositionUpdate);

    lastUpdateByVehicle.put(bp.getVehicleID(), bp.getDateTime());
  }

  private void refreshAlerts() throws WMATAAPIException {
    List<Item> busAlerts = _api.downloadBusAlerts().getChannel().getItems();
    List<Item> railAlerts = _api.downloadRailAlerts().getChannel().getItems();

    Set<UUID> currentAlertIDs = new HashSet<>();

    for (Item theAlert : Iterables.concat(busAlerts, railAlerts)) {
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

    @SuppressWarnings("unchecked")
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

    @Override
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

    @Override
    public void run() {
      try {
        _log.info("Refreshing alerts");
        refreshAlerts();
      } catch (Exception ex) {
        _log.warn("Error in alert refresh task", ex);
      }
    }
  }
}
