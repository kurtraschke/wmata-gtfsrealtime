/*
 * Copyright (C) 2014 Kurt Raschke <kurt@kurtraschke.com>
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
package com.kurtraschke.wmata.gtfsrealtime.services;

import org.onebusaway.collections.Min;
import org.onebusaway.collections.tuple.T2;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.GtfsRelationalDao;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.kurtraschke.wmata.gtfsrealtime.AgencyTimeZone;
import com.kurtraschke.wmata.gtfsrealtime.DateTimeUtils;
import com.kurtraschke.wmata.gtfsrealtime.WMATAAPIException;
import com.kurtraschke.wmata.gtfsrealtime.api.buspositions.BusPosition;
import com.kurtraschke.wmata.gtfsrealtime.api.routeschedule.RouteSchedule;
import com.kurtraschke.wmata.gtfsrealtime.api.routeschedule.WMATAStopTime;
import com.kurtraschke.wmata.gtfsrealtime.api.routeschedule.WMATATrip;
import com.kurtraschke.wmata.gtfsrealtime.model.TripMapKey;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Named;

/**
 *
 * @author kurt
 */
public class WMATATripMapperService {

  private static final Logger _log = LoggerFactory.getLogger(WMATATripMapperService.class);

  private WMATARouteMapperService _routeMapperService;
  private WMATAAPIService _api;
  private Cache _tripCache;
  private CalendarServiceData _csd;
  private GtfsRelationalDao _dao;
  private TimeZone _agencyTimeZone;
  private int _scoreLimit;


  @Inject
  public void setWMATARouteMapperService(WMATARouteMapperService mapperService) {
    _routeMapperService = mapperService;
  }

  @Inject
  public void setWMATAAPIService(WMATAAPIService api) {
    _api = api;
  }

  @Inject
  public void setTripCache(@Named("caches.trip")
  Cache tripCache) {
    _tripCache = tripCache;
  }

  @Inject
  public void setCalendarServiceData(CalendarServiceData csd) {
    _csd = csd;
  }

  @Inject
  public void setGtfsRelationalDao(GtfsRelationalDao dao) {
    _dao = dao;
  }

  @Inject
  public void setAgencyTimeZone(@AgencyTimeZone TimeZone timeZone) {
    _agencyTimeZone = timeZone;
  }

  @Inject
  public void setScoreLimit(@Named("tripMapping.scoreLimit") int scoreLimit) {
    _scoreLimit = scoreLimit;
  }

  public AgencyAndId getTripMapping(BusPosition bp) throws WMATAAPIException {
    ServiceDate serviceDate = bp.getServiceDate();
    String tripID = bp.getTripID();

    TripMapKey k = new TripMapKey(serviceDate, tripID);

    Element e = _tripCache.get(k);

    if (e == null) {
      AgencyAndId mappedTripID = mapTrip(bp);
      _tripCache.put(new Element(k, mappedTripID));
      return mappedTripID;
    } else {
      return (AgencyAndId) e.getObjectValue();
    }
  }

  private AgencyAndId mapTrip(BusPosition bp) throws WMATAAPIException {
    WMATATrip theTrip = getWMATATrip(bp.getServiceDate(), bp.getRouteID(),
        bp.getTripStartTime(), bp.getTripEndTime(), bp.getDirectionText());
    ServiceDate serviceDate = bp.getServiceDate();

    if (theTrip != null) {
      return mapTrip(serviceDate, theTrip);
    } else {
      return null;
    }
  }

  private WMATATrip getWMATATrip(ServiceDate serviceDate, String routeID,
      Date tripStartTime, Date tripEndTime, String tripDirection)
      throws WMATAAPIException {
    RouteSchedule rsi = _api.downloadRouteScheduleInfo(routeID, DateTimeUtils.apiDateStringForServiceDate(serviceDate));

    for (WMATATrip t : rsi.getTrips()) {
      if (t.getStartTime().equals(tripStartTime)
          && t.getEndTime().equals(tripEndTime)
          && t.getTripDirectionText().equals(tripDirection)) {
        return t;
      }
    }
    return null;
  }

  private AgencyAndId mapTrip(ServiceDate serviceDate, WMATATrip theTrip) {
    AgencyAndId mappedRouteID = _routeMapperService.getRouteMapping(theTrip.getRouteID());

    if (mappedRouteID != null) {

      Collection<Trip> candidateTrips = tripsForServiceDateAndRoute(
          serviceDate, mappedRouteID);

      if (candidateTrips.size() > 0) {
        T2<Double, Trip> result = findBestGtfsTripForWMATATrip(theTrip,
            candidateTrips, serviceDate);
        double mappingScore = result.getFirst();
        Trip mappedTrip = result.getSecond();

        if (mappingScore < _scoreLimit) {
          AgencyAndId mappedTripID = mappedTrip.getId();
          _log.info("Mapped WMATA trip " + theTrip.getTripID()
              + " to GTFS trip " + mappedTripID + " with score "
              + Math.round(mappingScore));
          return mappedTripID;
        } else {
          /*
           * In this case, we had one or more candidate trips from the GTFS
           * schedule to evaluate, but the best of them produced a score that
           * was too high to consider a reliable match.
           */
          _log.warn("Could not map WMATA trip " + theTrip.getTripID()
              + " on route " + theTrip.getRouteID() + " with score "
              + Math.round(mappingScore));
          return null;
        }
      } else {
        /*
         * This is the case where the GTFS schedule simply doesn't return any
         * active trips for that route and time.
         */
        _log.warn("Could not map WMATA trip " + theTrip.getTripID()
            + " on route " + theTrip.getRouteID()
            + " (no candidates from GTFS schedule)");
        return null;
      }
    } else {
      /*
       * This is the case where we could not map the route; no sense trying to
       * map the trip when we don't know the route.
       */
      _log.warn("Could not map WMATA trip " + theTrip.getTripID()
          + " (could not map route " + theTrip.getRouteID() + ")");
      return null;
    }
  }

  private Collection<Trip> tripsForServiceDateAndRoute(ServiceDate serviceDate,
      AgencyAndId route) {
    Route r = _dao.getRouteForId(route);

    final Set<AgencyAndId> services = _csd.getServiceIdsForDate(serviceDate);

    List<Trip> allTrips = _dao.getTripsForRoute(r);

    return Collections2.<Trip> filter(allTrips, new Predicate<Trip>() {
      @Override
      public boolean apply(Trip t) {
        return services.contains(t.getServiceId());
      }
    });
  }

  private T2<Double, Trip> findBestGtfsTripForWMATATrip(WMATATrip wmataTrip,
      Collection<Trip> gtfsTrips, ServiceDate serviceDate) {

    List<WMATAStopTime> wmataStopTimes = wmataTrip.getStopTimes();
    Collections.sort(wmataStopTimes);

    Min<Trip> m = new Min<>();
    for (Trip gtfsTrip : gtfsTrips) {
      double score = computeStopTimeAlignmentScore(wmataStopTimes,
          _dao.getStopTimesForTrip(gtfsTrip), serviceDate);
      m.add(score, gtfsTrip);
    }

    if (m.getMinValue() > _scoreLimit) {
      StringBuilder b = new StringBuilder();
      for (WMATAStopTime stopTime : wmataTrip.getStopTimes()) {
        b.append("\n  ");
        b.append(stopTime.getStopID());
        b.append(" ");
        b.append(stopTime.getStopName());
        b.append(" ");
        b.append(stopTime.getTime());
        b.append(" ");
      }
      b.append("\n-----");
      for (StopTime stopTime : _dao.getStopTimesForTrip(
          m.getMinElement())) {
        b.append("\n  ");
        b.append(stopTime.getStop().getCode());
        b.append(" ");
        b.append(stopTime.getStop().getName());
        b.append(" ");
        b.append(new Date((getTime(stopTime) * 1000L)
            + serviceDate.getAsDate(_agencyTimeZone).getTime()));
        b.append(" ");
      }
      _log.warn("no good match found for trip:" + b.toString());
    }
    return Tuples.<Double, Trip> tuple(m.getMinValue(), m.getMinElement());
  }

  private double computeStopTimeAlignmentScore(
      List<WMATAStopTime> wmataStopTimes, List<StopTime> gtfsStopTimes,
      ServiceDate serviceDate) {

    Map<String, StopTimes> gtfsStopIdToStopTimes = new HashMap<>();
    for (int index = 0; index < gtfsStopTimes.size(); index++) {
      StopTime stopTime = gtfsStopTimes.get(index);
      String stopId = stopTime.getStop().getCode();
      StopTimes stopTimes = gtfsStopIdToStopTimes.get(stopId);
      if (stopTimes == null) {
        stopTimes = new StopTimes();
        gtfsStopIdToStopTimes.put(stopId, stopTimes);
      }
      stopTimes.addStopTime(stopTime, index);
    }

    for (StopTimes stopTimes : gtfsStopIdToStopTimes.values()) {
      stopTimes.pack();
    }

    Map<WMATAStopTime, Integer> mapping = new HashMap<>();

    for (WMATAStopTime wmataStopTime : wmataStopTimes) {
      StopTimes stopTimes = gtfsStopIdToStopTimes.get(wmataStopTime.getStopID());
      if (stopTimes == null) {
        mapping.put(wmataStopTime, -1);
      } else {
        int bestIndex = stopTimes.computeBestStopTimeIndex((int) ((wmataStopTime.getTime().getTime() - serviceDate.getAsDate(
            _agencyTimeZone).getTime()) / 1000L));
        mapping.put(wmataStopTime, bestIndex);
      }
    }

    int lastIndex = -1;
    int score = 0;
    boolean allMisses = true;

    for (Map.Entry<WMATAStopTime, Integer> entry : mapping.entrySet()) {
      WMATAStopTime wmataStopTime = entry.getKey();
      int index = entry.getValue();
      StopTime gtfsStopTime = null;
      if (0 <= index && index < gtfsStopTimes.size()) {
        gtfsStopTime = gtfsStopTimes.get(index);
      }

      if (gtfsStopTime == null) {
        score += 15; // A miss is a 15 minute penalty
      } else {
        allMisses = false;
        if (index < lastIndex) {
          score += 15; // Out of order is a 10 minute penalty
        }
        int delta = Math.abs(((int) (wmataStopTime.getTime().getTime() / 1000L))
            - (getTime(gtfsStopTime) + (int) (serviceDate.getAsDate(_agencyTimeZone).getTime() / 1000L))) / 60;
        score += delta;
        lastIndex = index;
      }
    }

    if (allMisses) {
      return 4 * 60 * 60;
    }
    return score;
  }

  private static int getTime(StopTime stopTime) {
    return ((stopTime.getDepartureTime() + stopTime.getArrivalTime()) / 2);
  }

  private static class StopTimes {

    private List<StopTime> stopTimes = new ArrayList<>();
    private List<Integer> indices = new ArrayList<>();
    private int[] times;

    public void addStopTime(StopTime stopTime, int index) {
      stopTimes.add(stopTime);
      indices.add(index);
    }

    public int computeBestStopTimeIndex(int time) {
      int index = Arrays.binarySearch(times, time);
      if (index < 0) {
        index = -(index + 1);
      }
      if (index < 0 || index >= indices.size()) {
        return -1;
      }
      return indices.get(index);
    }

    public void pack() {
      times = new int[stopTimes.size()];
      for (int i = 0; i < stopTimes.size(); ++i) {
        StopTime stopTime = stopTimes.get(i);
        times[i] = getTime(stopTime);
      }
    }
  }
}
