/*
 * Copyright (C) 2012 Kurt Raschke
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
import com.google.common.primitives.Doubles;
import com.kurtraschke.wmatagtfsrealtime.api.WMATARouteScheduleInfo;
import com.kurtraschke.wmatagtfsrealtime.api.WMATAStop;
import com.kurtraschke.wmatagtfsrealtime.api.WMATAStopTime;
import com.kurtraschke.wmatagtfsrealtime.api.WMATATrip;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.onebusaway.transit_data.model.StopBean;
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
    private Cache _stopByIDCache;
    private final int NOT_MAPPED = -1;
    private final int TIME_OFFSET_MIN = 5;
    private final int SCORE_LIMIT = 10000;

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

    @Inject
    public void setStopsByIDCache(@Named("caches.stopByID") Cache stopsByIDCache) {
        _stopByIDCache = stopsByIDCache;

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

                List<TripScoreKey> scoredTrips = new ArrayList<TripScoreKey>();

                for (TripDetailsBean t : candidateTrips) {

                    double score = scoreTripMatch(serviceDate, thisTrip, t);

                    if (score >= 0) {
                        scoredTrips.add(new TripScoreKey(score, t));
                    }

                }

                if (scoredTrips.size() > 0) {
                    TripScoreKey bestMatch = Collections.min(scoredTrips, new Comparator<TripScoreKey>() {
                        public int compare(TripScoreKey t, TripScoreKey t1) {
                            return Doubles.compare(t.score, t1.score);
                        }
                    });

                    TripDetailsBean bestTrip = bestMatch.trip;

                    if (bestMatch.score < SCORE_LIMIT) {
                        String mappedTripID = bestTrip.getTripId();
                        _log.info("Mapped WMATA trip " + tripID + " to GTFS trip " + mappedTripID);
                        return mappedTripID;
                    } else {
                        /*
                         * In this case, we had one or more candidate trips from the
                         * TDS to evaluate, but the best of them produced a score that 
                         * was too high to consider a reliable match.
                         */
                        _log.warn("Could not map WMATA trip " + tripID + " on route " + thisTrip.getRouteID() + " with score " + Math.round(bestMatch.score));
                        return null;
                    }

                } else {
                    /* 
                     * This is the case where all of the candidate trips failed
                     * to map any stoptimes.
                     */
                    _log.warn("Could not map WMATA trip " + tripID + " on route " + thisTrip.getRouteID() + " (all trips matched no stoptimes)");
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

    private double scoreTripMatch(Date serviceDate, WMATATrip wmataTrip, TripDetailsBean gtfsTrip) throws IOException, SAXException {
        long baseTime = serviceDate.getTime() / 1000L;

        Map<WMATAStopTime, StopTimeScoreKey> stopTimeMap = new HashMap<WMATAStopTime, StopTimeScoreKey>();

        for (WMATAStopTime st : wmataTrip.getStopTimes()) {

            WMATAStop thisStop = getWMATAStopByID(st.getStopID());

            if (st.getStopID().length() == 7 && thisStop != null) {
                /*
                 * Don't bother trying to map stoptimes at fake stops
                 * or stops that don't exist.
                 */
                List<StopTimeScoreKey> options = new ArrayList<StopTimeScoreKey>();
                for (TripStopTimeBean gst : gtfsTrip.getSchedule().getStopTimes()) {

                    double score = stopDistanceMetric(gst.getStop(),
                            (baseTime + gst.getArrivalTime()) * 1000L,
                            thisStop,
                            st.getTime().getTime());

                    if (score < 500) {
                        options.add(new StopTimeScoreKey(score, gst));
                    }

                }

                if (options.size() > 0) {
                    StopTimeScoreKey best = Collections.min(options, new Comparator<StopTimeScoreKey>() {
                        public int compare(StopTimeScoreKey t, StopTimeScoreKey t1) {
                            return Doubles.compare(t.score, t1.score);
                        }
                    });

                    stopTimeMap.put(st, best);

                }
            }

        }

        _log.debug("Mapping for WMATA trip " + wmataTrip.getTripID() + " to GTFS trip " + gtfsTrip.getTripId());
        for (Map.Entry<WMATAStopTime, StopTimeScoreKey> e : stopTimeMap.entrySet()) {
            WMATAStopTime w = e.getKey();
            TripStopTimeBean g = e.getValue().stopTime;
            double score = e.getValue().score;

            _log.debug("W: " + w.getStopName() + " " + w.getTime());
            _log.debug("G: " + g.getStop().getName() + " " + new Date((baseTime + g.getArrivalTime()) * 1000L));
            _log.debug(Double.toString(score));
        }

        if (stopTimeMap.size() > wmataTrip.getStopTimes().size()/2) {
            double score = 0;

            for (StopTimeScoreKey v : stopTimeMap.values()) {
                score += v.score;
            }
            _log.debug("Total score is " + score);

            return score;

        } else {
            return NOT_MAPPED;
        }

    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        int radius = 20902200;

        double dlat = Math.toRadians(lat2 - lat1);
        double dlon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double d = radius * c;

        return d;
    }

    private static double stopDistanceMetric(StopBean gtfsStop, long gtfsStopTime, WMATAStop wmataStop, long wmataStopTime) {
        double d = distance(gtfsStop.getLat(), gtfsStop.getLon(), wmataStop.getLat(), wmataStop.getLon());

        return Math.sqrt((4 * Math.pow(d, 2)) + Math.pow(((gtfsStopTime / 1000) - (wmataStopTime / 1000)), 2));

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

    private WMATAStop getWMATAStopByID(final String stopID) throws IOException, SAXException {

        Element e = _stopByIDCache.get(stopID);

        if (e == null) {
            List<WMATAStop> stops = _api.downloadStopList();

            WMATAStop thisStop = Iterables.find(stops, new Predicate<WMATAStop>() {
                public boolean apply(WMATAStop s) {
                    return s.getStopID().equals(stopID);
                }
            });

            _stopByIDCache.put(new Element(stopID, thisStop));
            return thisStop;
        } else {
            return (WMATAStop) e.getObjectValue();
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

    private static class StopTimeScoreKey {

        double score;
        TripStopTimeBean stopTime;

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 37 * hash + (int) (Double.doubleToLongBits(this.score) ^ (Double.doubleToLongBits(this.score) >>> 32));
            hash = 37 * hash + (this.stopTime != null ? this.stopTime.hashCode() : 0);
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
            final StopTimeScoreKey other = (StopTimeScoreKey) obj;
            if (Double.doubleToLongBits(this.score) != Double.doubleToLongBits(other.score)) {
                return false;
            }
            if (this.stopTime != other.stopTime && (this.stopTime == null || !this.stopTime.equals(other.stopTime))) {
                return false;
            }
            return true;
        }

        public StopTimeScoreKey(double score, TripStopTimeBean stopTime) {
            this.score = score;
            this.stopTime = stopTime;
        }
    }

    private static class TripScoreKey {

        double score;
        TripDetailsBean trip;

        public TripScoreKey(double score, TripDetailsBean trip) {
            this.score = score;
            this.trip = trip;
        }
    }
}
