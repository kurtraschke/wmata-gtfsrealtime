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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.kurtraschke.wmatagtfsrealtime.api.WMATARouteScheduleInfo;
import com.kurtraschke.wmatagtfsrealtime.api.WMATAStopTime;
import com.kurtraschke.wmatagtfsrealtime.api.WMATATrip;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.onebusaway.collections.Min;
import org.onebusaway.transit_data.model.TripStopTimeBean;
import org.onebusaway.transit_data.model.trips.TripDetailsBean;
import org.onebusaway.transit_data.model.trips.TripDetailsInclusionBean;
import org.onebusaway.transit_data.model.trips.TripsForRouteQueryBean;
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
    private TransitDataServiceService _tds;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private Cache _tripCache;
    private final int TIME_OFFSET_MIN = 5;
    private final int SCORE_LIMIT = 1500;

    @Inject
    public void setWMATARouteMapperService(WMATARouteMapperService mapperService) {
        _routeMapperService = mapperService;
    }

    @Inject
    public void setWMATAAPIService(WMATAAPIService api) {
        _api = api;
    }

    @Inject
    public void setTransitDataServiceService(TransitDataServiceService tds) {
        _tds = tds;
    }

    @Inject
    public void setTripCache(@Named("caches.trip") Cache tripCache) {
        _tripCache = tripCache;

    }

    private String mapTrip(Date serviceDate, final String tripID, String routeID) throws IOException, SAXException {
        String mappedRouteID = _routeMapperService.getBusRouteMapping(routeID);

        if (mappedRouteID != null) {
            WMATARouteScheduleInfo rsi = _api.downloadRouteScheduleInfo(routeID, dateFormat.format(serviceDate));

            List<WMATATrip> routeTrips = rsi.getTrips();

            WMATATrip thisTrip = Iterables.find(routeTrips, new Predicate<WMATATrip>() {
                public boolean apply(WMATATrip t) {
                    return t.getTripID().equals(tripID);
                }
            });

            List<TripDetailsBean> candidateTrips = getTripsForTime(mappedRouteID,
                    thisTrip.getStopTimes().get(0).getTime().getTime());

            if (candidateTrips.size() > 0) {

                Map<List<TripStopTimeBean>, String> tripMap = new HashMap<List<TripStopTimeBean>, String>();

                for (TripDetailsBean b : candidateTrips) {
                    tripMap.put(b.getSchedule().getStopTimes(), b.getTripId());
                }

                List<TripStopTimeBean> bestStopTimesForBlock = new ArrayList<TripStopTimeBean>();

                List<List<TripStopTimeBean>> gtfsStopTimesByTrip = new ArrayList<List<TripStopTimeBean>>(tripMap.keySet());

                Double result = findBestGtfsTripForNextBusTrip(thisTrip.getStopTimes(), gtfsStopTimesByTrip, bestStopTimesForBlock, serviceDate);

                if (result < SCORE_LIMIT) {
                    String mappedTripID = tripMap.get(bestStopTimesForBlock);
                    _log.info("Mapped WMATA trip " + tripID + " to GTFS trip " + mappedTripID);
                    return mappedTripID;
                } else {
                    /*
                     * In this case, we had one or more candidate trips from the
                     * TDS to evaluate, but the best of them produced a score that 
                     * was too high to consider a reliable match.
                     */
                    _log.warn("Could not map WMATA trip " + tripID + " on route " + thisTrip.getRouteID() + " with score " + Math.round(result));
                    return null;
                }
            } else {
                /* 
                 * This is the case where the TDS simply doesn't return any active
                 * trips for that route and time.
                 */
                _log.warn("Could not map WMATA trip " + tripID + " on route " + thisTrip.getRouteID() + " (no candidates from TDS)");
                return null;
            }
        } else {
            /* 
             * This is the case where we could not map the route;
             * no sense trying to map the trip when we don't know the route.
             */
            _log.warn("Could not map WMATA trip " + tripID + " (could not map route)");
            return null;
        }
    }

    private List<TripDetailsBean> getTripsForTime(String routeID, long tripTime) {
        List<TripDetailsBean> candidateTrips;
        Map<String, TripDetailsBean> allCandidateTrips = new HashMap<String, TripDetailsBean>();
        TripsForRouteQueryBean tfrqb = new TripsForRouteQueryBean();

        tfrqb.setRouteId(routeID);
        tfrqb.setTime(tripTime - (TIME_OFFSET_MIN * 60 * 1000));
        tfrqb.setInclusion(new TripDetailsInclusionBean(true, true, false));

        _log.debug("Querying for trips on route " + routeID + " at time " + tripTime);

        candidateTrips = _tds.getService().getTripsForRoute(tfrqb).getList();

        for (TripDetailsBean t : candidateTrips) {
            allCandidateTrips.put(t.getTripId(), t);
        }

        tfrqb.setTime(tripTime + (TIME_OFFSET_MIN * 60 * 1000));


        candidateTrips = _tds.getService().getTripsForRoute(tfrqb).getList();

        for (TripDetailsBean t : candidateTrips) {
            allCandidateTrips.put(t.getTripId(), t);
        }

        return new ArrayList<TripDetailsBean>(allCandidateTrips.values());
    }

    public String getTripMapping(Date serviceDate, String tripID, String routeID) throws IOException, SAXException {
        TripMapKey k = new TripMapKey(serviceDate, tripID);

        Element e = _tripCache.get(k);

        if (e == null) {

            String mappedTripID = mapTrip(serviceDate, tripID, routeID);
            _tripCache.put(new Element(k, mappedTripID));
            return mappedTripID;
        } else {

            return (String) e.getObjectValue();
        }

    }

    private static class TripMapKey implements Serializable {

        private static final long serialVersionUID = 1L;
        public Date serviceDate;
        public String tripID;

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + (this.serviceDate != null ? this.serviceDate.hashCode() : 0);
            hash = 23 * hash + (this.tripID != null ? this.tripID.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TripMapKey other = (TripMapKey) obj;
            if (this.serviceDate != other.serviceDate && (this.serviceDate == null || !this.serviceDate.equals(other.serviceDate))) {
                return false;
            }
            if ((this.tripID == null) ? (other.tripID != null) : !this.tripID.equals(other.tripID)) {
                return false;
            }
            return true;
        }

        public TripMapKey(Date serviceDate, String tripID) {
            this.serviceDate = serviceDate;
            this.tripID = tripID;
        }
    }

    private double findBestGtfsTripForNextBusTrip(List<WMATAStopTime> nextBusTrip,
            List<List<TripStopTimeBean>> gtfsStopTimesByTrip,
            List<TripStopTimeBean> bestStopTimesForBlock,
            Date serviceDate) {

        Collections.sort(nextBusTrip);

        Min<List<TripStopTimeBean>> m = new Min<List<TripStopTimeBean>>();
        for (List<TripStopTimeBean> gtfsTrip : gtfsStopTimesByTrip) {
            double score = computeStopTimeAlignmentScore(nextBusTrip, gtfsTrip, serviceDate);
            m.add(score, gtfsTrip);
        }

        if (m.getMinValue() > SCORE_LIMIT) {
            StringBuilder b = new StringBuilder();
            for (WMATAStopTime stopTime : nextBusTrip) {
                b.append("\n  ");
                b.append(stopTime.getStopID());
                b.append(" ");
                b.append(stopTime.getStopName());
                b.append(" ");
                b.append(stopTime.getTime());
                b.append(" ");
            }
            b.append("\n-----\n");
            for (TripStopTimeBean stopTime : m.getMinElement()) {
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
            List<TripStopTimeBean> bestStopTimes = m.getMinElement();
            bestStopTimesForBlock.addAll(bestStopTimes);

        }
        return m.getMinValue();
    }

    private double computeStopTimeAlignmentScore(List<WMATAStopTime> nbStopTimes,
            List<TripStopTimeBean> gtfsStopTimes, Date serviceDate) {

        Map<String, StopTimes> gtfsStopIdToStopTimes = new HashMap<String, StopTimes>();
        for (int index = 0; index < gtfsStopTimes.size(); index++) {
            TripStopTimeBean stopTime = gtfsStopTimes.get(index);
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

        for (WMATAStopTime nbStopTime : nbStopTimes) {
            StopTimes stopTimes = gtfsStopIdToStopTimes.get(nbStopTime.getStopID());
            if (stopTimes == null) {
                mapping.put(nbStopTime, -1);
            } else {
                int bestIndex = stopTimes.computeBestStopTimeIndex((int) (nbStopTime.getTime().getTime() / 1000L));
                mapping.put(nbStopTime, bestIndex);
            }
        }

        int lastIndex = -1;
        int score = 0;
        boolean allMisses = true;

        for (Map.Entry<WMATAStopTime, Integer> entry : mapping.entrySet()) {
            WMATAStopTime nbStopTime = entry.getKey();
            int index = entry.getValue();
            TripStopTimeBean gtfsStopTime = null;
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
                        ((int) (nbStopTime.getTime().getTime() / 1000L))
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

    private static class StopTimes {

        private List<TripStopTimeBean> stopTimes = new ArrayList<TripStopTimeBean>();
        private List<Integer> indices = new ArrayList<Integer>();
        private int[] times;

        public void addStopTime(TripStopTimeBean stopTime, int index) {
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

        public void pack(Date serviceDate) {
            times = new int[stopTimes.size()];
            for (int i = 0; i < stopTimes.size(); ++i) {
                TripStopTimeBean stopTime = stopTimes.get(i);
                times[i] = getTime(stopTime, serviceDate);
            }
        }
    }

    private static int getTime(TripStopTimeBean stopTime, Date serviceDate) {
        return ((stopTime.getDepartureTime() + stopTime.getArrivalTime()) / 2) + (int) (serviceDate.getTime() / 1000);
    }
    
    private static class TimeConverter {
        private Date serviceDate;
        
        public TimeConverter(Date serviceDate) {
            this.serviceDate = serviceDate;
        }
        
        public int convert(int time) {
            return (int)(serviceDate.getTime() / 1000L) + time;
        }
    }
}
