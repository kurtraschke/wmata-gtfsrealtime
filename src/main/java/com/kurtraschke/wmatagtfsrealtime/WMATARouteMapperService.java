/**
 * Copyright (C) 2012 Kurt Raschke
 * 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kurtraschke.wmatagtfsrealtime;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.kurtraschke.wmatagtfsrealtime.api.WMATARoute;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.onebusaway.transit_data.model.RouteBean;
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
    private TransitDataServiceService _service;
    private List<WMATARoute> wmataRoutes;
    private Map<String, String> routeMap = new HashMap<String, String>();
    private Map<String, String> filteredRouteMap = new HashMap<String, String>();
    @Inject
    @Named("WMATA.agencyID")
    private String AGENCY_ID;
    private final String[] BAD_ROUTES = new String[]{"B99", "F99", "F99c", "F99v1",
        "L99", "P99", "PATBL", "PATFM", "PATLA", "PATMG", "PATNO",
        "PATRO", "PATSH", "PATSO", "PATWN", "PATWO", "SH99", "W99",
        "W99v1"};

    @Inject
    public void setWMATAAPIService(WMATAAPIService api) {
        _api = api;
    }

    @Inject
    public void setTransitDataServiceService(TransitDataServiceService service) {
        _service = service;
    }

    private void doMapping() throws IOException, SAXException {

        List<RouteBean> gtfsRoutes = _service.getService().getRoutesForAgencyId(AGENCY_ID).getList();

        wmataRoutes = _api.downloadRouteList();

        Pattern p = Pattern.compile("^([A-Z0-9]+)(c?v?S?[0-9]?).*$");

        Predicate<String> matchBadRoutes = Predicates.in(Arrays.asList(BAD_ROUTES));

        for (WMATARoute r : wmataRoutes) {

            Matcher m = p.matcher(r.getRouteID());

            if (m.matches()) {
                final String filteredRouteID = m.group(1);
                if (!matchBadRoutes.apply(filteredRouteID)) {

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
                        routeMap.put(r.getRouteID(), matchedRoute.get().getId());
                        filteredRouteMap.put(filteredRouteID, matchedRoute.get().getId());
                    } else {
                        _log.warn("Could not map WMATA route: " + r.getRouteID());
                    }
                }

            }
        }
    }

    public Map<String, String> getRouteMap() {
        return Collections.unmodifiableMap(routeMap);
    }

    public Map<String, String> getFilteredRouteMap() {
        return Collections.unmodifiableMap(filteredRouteMap);
    }

    @PostConstruct
    public void start() throws IOException, SAXException {
        doMapping();
    }
}
