/*
 * Copyright (C) 2013 Kurt Raschke
 *
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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.kurtraschke.wmatagtfsrealtime.api.WMATARoute;
import com.kurtraschke.wmatagtfsrealtime.api.WMATARouteScheduleInfo;
import com.kurtraschke.wmatagtfsrealtime.api.WMATAStopTime;
import com.kurtraschke.wmatagtfsrealtime.api.WMATATrip;
import java.io.IOException;
import java.text.SimpleDateFormat;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.onebusaway.collections.Min;
import org.onebusaway.collections.tuple.T2;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs.services.calendar.CalendarServiceDataFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 * @author kurt
 */
public class WMATATripMapperService {

    private static final Logger _log = LoggerFactory.getLogger(WMATATripMapperService.class);
    private WMATARouteMapperService _routeMapperService;
    private WMATAAPIService _api;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private Cache _tripCache;
    private GtfsRelationalDao _dao;
    private CalendarServiceData csd;
    private final int SCORE_LIMIT = 1500; //FIXME: make configurable
    Map<ServiceDate, Multimap<AgencyAndId, Trip>> gtfsTripsByDateAndRoute = new HashMap<ServiceDate, Multimap<AgencyAndId, Trip>>();
    BiMap<Trip, List<StopTime>> tripStopTimeMap = HashBiMap.<Trip, List<StopTime>>create();

    @Inject
    public void setWMATARouteMapperService(WMATARouteMapperService mapperService) {
        _routeMapperService = mapperService;
    }

    @Inject
    public void setWMATAAPIService(WMATAAPIService api) {
        _api = api;
    }

    @Inject
    public void setTripCache(@Named("caches.trip") Cache tripCache) {
        _tripCache = tripCache;

    }

    @Inject
    public void setGtfsRelationalDao(GtfsDaoService dao) {
        _dao = dao.getDao();
    }

    @PostConstruct
    public void start() throws IOException, SAXException {
        CalendarServiceDataFactory csdf = new CalendarServiceDataFactoryImpl(_dao);
        csd = csdf.createData();

        for (Trip t : _dao.getAllTrips()) {
            tripStopTimeMap.put(t, _dao.getStopTimesForTrip(t));
        }
    }

    private List<List<StopTime>> getStopTimesForTrips(Collection<Trip> trips) {
        List<List<StopTime>> out = new ArrayList<List<StopTime>>();

        for (Trip t : trips) {
            out.add(tripStopTimeMap.get(t));
        }

        return out;
    }

    private AgencyAndId mapTrip(ServiceDate serviceDate, WMATATrip theTrip) throws IOException, SAXException {
        AgencyAndId mappedRouteID = _routeMapperService.getRouteMapping(theTrip.getRouteID());

        if (mappedRouteID != null) {

            Collection<Trip> candidateTrips = gtfsTripsByDateAndRoute.get(serviceDate).get(mappedRouteID);

            if (candidateTrips.size() > 0) {
                List<StopTime> bestStopTimesForBlock = new ArrayList<StopTime>();

                List<List<StopTime>> gtfsStopTimesByTrip = getStopTimesForTrips(candidateTrips);

                Double result = findBestGtfsTripForWMATATrip(theTrip.getStopTimes(), gtfsStopTimesByTrip, bestStopTimesForBlock, serviceDate);

                if (result < SCORE_LIMIT) {
                    AgencyAndId mappedTripID = tripStopTimeMap.inverse().get(bestStopTimesForBlock).getId();
                    _log.info("Mapped WMATA trip " + theTrip.getTripID() + " to GTFS trip " + mappedTripID + " with score " + Math.round(result));
                    return mappedTripID;
                } else {
                    /*
                     * In this case, we had one or more candidate trips from the
                     * GTFS schedule to evaluate, but the best of them produced a score that 
                     * was too high to consider a reliable match.
                     */
                    _log.warn("Could not map WMATA trip " + theTrip.getTripID() + " on route " + theTrip.getRouteID() + " with score " + Math.round(result));
                    return null;
                }
            } else {
                /* 
                 * This is the case where the GTFS schedule simply doesn't return any active
                 * trips for that route and time.
                 */
                _log.warn("Could not map WMATA trip " + theTrip.getTripID() + " on route " + theTrip.getRouteID() + " (no candidates from GTFS schedule)");
                return null;
            }
        } else {
            /* 
             * This is the case where we could not map the route;
             * no sense trying to map the trip when we don't know the route.
             */
            _log.warn("Could not map WMATA trip " + theTrip.getTripID() + " (could not map route " + theTrip.getRouteID() + ")");
            return null;
        }
    }

    public void setupForServiceDates(Set<ServiceDate> serviceDates) throws IOException, SAXException, InterruptedException {
        serviceDates.removeAll(gtfsTripsByDateAndRoute.keySet());

        for (ServiceDate sd : serviceDates) {
            setupForServiceDate(sd);
        }
    }

    private void setupForServiceDate(ServiceDate serviceDate) throws IOException, SAXException, InterruptedException {
        Set<String> mappedRouteIds = _routeMapperService.getRouteMappings().keySet();
        Set<AgencyAndId> services = csd.getServiceIdsForDate(serviceDate);
        Multimap<AgencyAndId, Trip> tripsByRoute = HashMultimap.<AgencyAndId, Trip>create();

        for (AgencyAndId service : services) {
            List<Trip> trips = _dao.getTripsForServiceId(service);

            for (Trip t : trips) {
                tripsByRoute.put(t.getRoute().getId(), t);
            }
        }

        gtfsTripsByDateAndRoute.put(serviceDate, tripsByRoute);

        List<Callable<T2<TripMapKey, AgencyAndId>>> mappingTasks = new ArrayList<Callable<T2<TripMapKey, AgencyAndId>>>();

        for (WMATARoute r : _api.downloadRouteList()) {
            if (!mappedRouteIds.contains(r.getRouteID())) {
                //Don't bother downloading schedules for routes we won't be able to map anyway
                continue;
            }

            WMATARouteScheduleInfo rsi = _api.downloadRouteScheduleInfo(r.getRouteID(), dateFormat.format(serviceDate.getAsDate(TimeZone.getTimeZone("US/Eastern"))));

            for (WMATATrip t : rsi.getTrips()) {
                mappingTasks.add(new MappingTask(serviceDate, t));
            }
        }

        ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<T2<TripMapKey, AgencyAndId>>> mappingTaskResults = es.invokeAll(mappingTasks);
        for (Future<T2<TripMapKey, AgencyAndId>> f : mappingTaskResults) {
            try {
                Element e = new Element(f.get().getFirst(), f.get().getSecond());
                _tripCache.put(e);
            } catch (ExecutionException ex) {
                _log.warn("Failure mapping trip", ex);

            }
        }

        es.shutdown();
    }

    public AgencyAndId getTripMapping(ServiceDate serviceDate, String tripID, String routeID) throws IOException, SAXException {
        TripMapKey k = new TripMapKey(serviceDate, tripID);
        Element e = _tripCache.get(k);
        return (AgencyAndId) e.getObjectValue();
    }

    private double findBestGtfsTripForWMATATrip(List<WMATAStopTime> wmataTrip,
            List<List<StopTime>> gtfsStopTimesByTrip,
            List<StopTime> bestStopTimesForBlock,
            ServiceDate serviceDate) {

        Collections.sort(wmataTrip);

        Min<List<StopTime>> m = new Min<List<StopTime>>();
        for (List<StopTime> gtfsTrip : gtfsStopTimesByTrip) {
            double score = computeStopTimeAlignmentScore(wmataTrip, gtfsTrip, serviceDate);
            m.add(score, gtfsTrip);
        }

        if (m.getMinValue() > SCORE_LIMIT) {
            StringBuilder b = new StringBuilder();
            for (WMATAStopTime stopTime : wmataTrip) {
                b.append("\n  ");
                b.append(stopTime.getStopID());
                b.append(" ");
                b.append(stopTime.getStopName());
                b.append(" ");
                b.append(stopTime.getTime());
                b.append(" ");
            }
            b.append("\n-----");
            for (StopTime stopTime : m.getMinElement()) {
                b.append("\n  ");
                b.append(stopTime.getStop().getCode());
                b.append(" ");
                b.append(stopTime.getStop().getName());
                b.append(" ");
                b.append(new Date(getTime(stopTime, serviceDate) * 1000L));
                b.append(" ");
            }
            _log.warn("no good match found for trip:" + b.toString());
        } else {
            List<StopTime> bestStopTimes = m.getMinElement();
            bestStopTimesForBlock.addAll(bestStopTimes);

        }
        return m.getMinValue();
    }

    private double computeStopTimeAlignmentScore(List<WMATAStopTime> wmataStopTimes,
            List<StopTime> gtfsStopTimes, ServiceDate serviceDate) {

        Map<String, StopTimes> gtfsStopIdToStopTimes = new HashMap<String, StopTimes>();
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
            stopTimes.pack(serviceDate);
        }

        Map<WMATAStopTime, Integer> mapping = new HashMap<WMATAStopTime, Integer>();

        for (WMATAStopTime wmataStopTime : wmataStopTimes) {
            StopTimes stopTimes = gtfsStopIdToStopTimes.get(wmataStopTime.getStopID());
            if (stopTimes == null) {
                mapping.put(wmataStopTime, -1);
            } else {
                int bestIndex = stopTimes.computeBestStopTimeIndex((int) (wmataStopTime.getTime().getTime() / 1000L));
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
                int delta = Math.abs(
                        ((int) (wmataStopTime.getTime().getTime() / 1000L))
                        - getTime(gtfsStopTime, serviceDate)) / 60;
                score += delta;
                lastIndex = index;
            }
        }

        if (allMisses) {
            return 4 * 60 * 60;
        }
        return score;
    }

    private static int getTime(StopTime stopTime, ServiceDate serviceDate) {
        return ((stopTime.getDepartureTime() + stopTime.getArrivalTime()) / 2) + (int) (serviceDate.getAsDate(TimeZone.getTimeZone("US/Eastern")).getTime() / 1000);
    }

    private class MappingTask implements Callable<T2<TripMapKey, AgencyAndId>> {

        ServiceDate serviceDate;
        WMATATrip theTrip;

        public MappingTask(ServiceDate serviceDate, WMATATrip theTrip) {
            this.serviceDate = serviceDate;
            this.theTrip = theTrip;
        }

        public T2<TripMapKey, AgencyAndId> call() throws Exception {
            return Tuples.<TripMapKey, AgencyAndId>tuple(new TripMapKey(serviceDate, theTrip.getTripID()), mapTrip(serviceDate, theTrip));
        }
    }

    private static class StopTimes {

        private List<StopTime> stopTimes = new ArrayList<StopTime>();
        private List<Integer> indices = new ArrayList<Integer>();
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

        public void pack(ServiceDate serviceDate) {
            times = new int[stopTimes.size()];
            for (int i = 0; i < stopTimes.size(); ++i) {
                StopTime stopTime = stopTimes.get(i);
                times[i] = getTime(stopTime, serviceDate);
            }
        }
    }
}
