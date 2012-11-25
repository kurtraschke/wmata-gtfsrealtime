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

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.onebusaway.transit_data.model.RouteBean;
import org.slf4j.LoggerFactory;

/**
 *
 * @author kurt
 */
@Singleton
public class WMATARouteMapperService {

    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(WMATARouteMapperService.class);
    private TransitDataServiceService _tds;
    private Cache _busRouteCache;
    private Cache _railRouteCache;
    @Inject
    @Named("WMATA.agencyID")
    private String AGENCY_ID;
    private final String[] BAD_ROUTES = new String[]{"B99", "F99", "F99c", "F99v1",
        "L99", "P99", "PATBL", "PATFM", "PATLA", "PATMG", "PATNO",
        "PATRO", "PATSH", "PATSO", "PATWN", "PATWO", "SH99", "W99",
        "W99v1"};
    private final static Map<String, String> overrideMappings = new HashMap<String, String>();
    private final Pattern routeExtract = Pattern.compile("^([A-Z0-9]+)(c?v?S?[0-9]?).*$");
    private final Predicate<String> matchBadRoutes = Predicates.in(Arrays.asList(BAD_ROUTES));

    static {
        overrideMappings.put("R99", "3030-2_259");
        overrideMappings.put("R99v1", "3030-2_259");
        overrideMappings.put("REX", "3030-2_259");
    }

    @Inject
    public void setTransitDataServiceService(TransitDataServiceService tds) {
        _tds = tds;
    }

    @Inject
    public void setBusRouteCache(@Named("caches.busRoute") Cache cache) {
        _busRouteCache = cache;

    }

    @Inject
    public void setRailRouteCache(@Named("caches.railRoute") Cache cache) {
        _railRouteCache = cache;

    }

    private void mapBusRoute(String routeID) {

        if (overrideMappings.containsKey(routeID)) {
            String mappedRouteID = overrideMappings.get(routeID);
            _log.info("Mapped WMATA route " + routeID + " to GTFS route " + mappedRouteID + " (using override)");
            _busRouteCache.put(new Element(routeID, mappedRouteID));
            return;
        }

        Matcher m = routeExtract.matcher(routeID);

        if (m.matches()) {
            final String filteredRouteID = m.group(1);
            if (!matchBadRoutes.apply(filteredRouteID)) {

                List<RouteBean> gtfsRoutes = _tds.getService().getRoutesForAgencyId(AGENCY_ID).getList();

                Optional<RouteBean> matchedRoute = Iterables.tryFind(gtfsRoutes, new Predicate<RouteBean>() {
                    public boolean apply(RouteBean gr) {
                        if (gr.getShortName() != null) {
                            return gr.getShortName().equals(filteredRouteID);
                        } else {
                            return false;
                        }
                    }
                });

                if (matchedRoute.isPresent()) {
                    _log.info("Mapped WMATA route " + routeID + " to GTFS route " + matchedRoute.get().getId());
                    _busRouteCache.put(new Element(routeID, matchedRoute.get().getId()));

                } else {
                    _log.warn("Could not map WMATA route: " + routeID);
                    _busRouteCache.put(new Element(routeID, null));

                }
            } else {
                _log.warn("Not mapping blacklisted WMATA route: " + routeID);
                _busRouteCache.put(new Element(routeID, null));
            }
        } else {
            _log.warn("Not mapping malformed WMATA route: " + routeID);
            _busRouteCache.put(new Element(routeID, null));
        }
    }

    private void mapRailRoute(final String routeName) {
        List<RouteBean> gtfsRoutes = _tds.getService().getRoutesForAgencyId(AGENCY_ID).getList();


        Optional<RouteBean> matchedRoute = Iterables.tryFind(gtfsRoutes, new Predicate<RouteBean>() {
            public boolean apply(RouteBean gr) {
                if (gr.getShortName() != null) {
                    return gr.getShortName().equalsIgnoreCase(routeName);
                } else {
                    return false;
                }
            }
        });

        if (matchedRoute.isPresent()) {
            _log.info("Mapped WMATA route " + routeName + " to GTFS route " + matchedRoute.get().getId());
            _railRouteCache.put(new Element(routeName, matchedRoute.get().getId()));
        } else {
            _log.warn("Could not map WMATA route: " + routeName);
            _railRouteCache.put(new Element(routeName, null));
        }

    }

    public String getBusRouteMapping(String routeID) {
        if (!_busRouteCache.isKeyInCache(routeID)) {
            mapBusRoute(routeID);
        }

        return (String) _busRouteCache.get(routeID).getObjectValue();

    }

    public String getRailRouteMapping(String routeID) {
        if (!_railRouteCache.isKeyInCache(routeID)) {
            mapRailRoute(routeID);
        }

        return (String) _railRouteCache.get(routeID).getObjectValue();

    }
}
