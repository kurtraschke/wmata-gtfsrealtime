/*
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

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.kurtraschke.wmatagtfsrealtime.api.WMATARoute;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 * @author kurt
 */
@Singleton
public class WMATARouteMapperService {

    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(WMATARouteMapperService.class);
    private WMATAAPIService _api;
    private GtfsDaoService _daoService;
    private String[] badRoutes;
    private final Pattern routeExtract = Pattern.compile("^([A-Z0-9]+)(c?v?S?[0-9]?).*$");
    private Map<String, AgencyAndId> routeMappings = new HashMap<String, AgencyAndId>();
    private Predicate<String> matchBadRoutes;
    @Inject
    @Named("WMATA.agencyID")
    private String AGENCY_ID;
    @Inject
    @Named("WMATA.staticMappings")
    private Properties staticMappings;
    private List<Route> gtfsRoutes;

    @Inject
    public void setGtfsDaoService(GtfsDaoService daoService) {
        _daoService = daoService;
    }

    @Inject
    public void setWMATAAPIService(WMATAAPIService api) {
        _api = api;
    }

    @Inject
    public void setBadRoutes(@Named("WMATA.badRoutes") String badRoutesString) {
        badRoutes = badRoutesString.split(",");
        matchBadRoutes = Predicates.in(Arrays.asList(badRoutes));
    }

    @PostConstruct
    public void start() throws IOException, SAXException {
        GtfsRelationalDao dao = _daoService.getGtfsRelationalDao();
        gtfsRoutes = dao.getRoutesForAgency(dao.getAgencyForId(AGENCY_ID));
        primeCaches();
    }

    public void primeCaches() throws IOException, SAXException {
        for (WMATARoute r : _api.downloadRouteList()) {
            AgencyAndId mapResult = mapBusRoute(r.getRouteID());

            if (mapResult != null) {
                routeMappings.put(r.getRouteID(), mapResult);
            }
        }

        String[] railRoutes = new String[]{"RED", "ORANGE", "YELLOW", "GREEN", "BLUE"};

        for (String r : railRoutes) {
            AgencyAndId mapResult = mapRailRoute(r);

            if (mapResult != null) {
                routeMappings.put(r, mapResult);
            }
        }
    }

    private AgencyAndId mapBusRoute(String routeID) {
        if (staticMappings.containsKey(routeID)) {
            Route matchedRoute = Iterables.find(gtfsRoutes,
                    new ShortNameFilterPredicate(staticMappings.getProperty(routeID), true));
            AgencyAndId mappedRouteID = matchedRoute.getId();
            _log.info("Mapped WMATA route " + routeID + " to GTFS route " + mappedRouteID + " (using override)");
            return mappedRouteID;
        }

        Matcher m = routeExtract.matcher(routeID);

        if (m.matches()) {
            final String filteredRouteID = m.group(1);
            if (!matchBadRoutes.apply(filteredRouteID)) {
                Optional<Route> matchedRoute = Iterables.tryFind(gtfsRoutes,
                        new ShortNameFilterPredicate(filteredRouteID, true));

                if (matchedRoute.isPresent()) {
                    AgencyAndId mappedRouteID = matchedRoute.get().getId();
                    _log.info("Mapped WMATA route " + routeID + " to GTFS route " + mappedRouteID);
                    return mappedRouteID;
                } else {
                    _log.warn("Could not map WMATA route: " + routeID);
                    return null;
                }
            } else {
                _log.warn("Not mapping blacklisted WMATA route: " + routeID);
                return null;
            }
        } else {
            _log.warn("Not mapping malformed WMATA route: " + routeID);
            return null;
        }
    }

    private AgencyAndId mapRailRoute(String routeName) {
        Optional<Route> matchedRoute = Iterables.tryFind(gtfsRoutes,
                new ShortNameFilterPredicate(routeName, false));

        if (matchedRoute.isPresent()) {
            AgencyAndId mappedRouteID = matchedRoute.get().getId();
            _log.info("Mapped WMATA route " + routeName + " to GTFS route " + mappedRouteID);
            return mappedRouteID;
        } else {
            _log.warn("Could not map WMATA route: " + routeName);
            return null;
        }
    }

    public AgencyAndId getRouteMapping(String routeID) {
        if (routeMappings.containsKey(routeID)) {
            return routeMappings.get(routeID);
        } else {
            return null;
        }
    }

    public Map<String, AgencyAndId> getRouteMappings() {
        return ImmutableMap.<String, AgencyAndId>copyOf(routeMappings);
    }

    private static class ShortNameFilterPredicate implements Predicate<Route> {

        private String shortName;
        private boolean caseSensitive;

        public ShortNameFilterPredicate(String shortName, boolean caseSensitive) {
            this.shortName = shortName;
            this.caseSensitive = caseSensitive;
        }

        public boolean apply(Route r) {
            if (r.getShortName() != null) {
                if (caseSensitive) {
                    return r.getShortName().equals(shortName);
                } else {
                    return r.getShortName().equalsIgnoreCase(shortName);
                }
            } else {
                return false;
            }
        }
    }
}
