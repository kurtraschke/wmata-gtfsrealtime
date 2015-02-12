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

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.services.GtfsRelationalDao;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.kurtraschke.wmata.gtfsrealtime.WMATAAPIException;
import com.kurtraschke.wmata.gtfsrealtime.api.routes.WMATARoute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 *
 * @author kurt
 */
@Singleton
public class WMATARouteMapperService {

  private static final Logger _log = LoggerFactory.getLogger(WMATARouteMapperService.class);

  private WMATAAPIService _api;
  private GtfsRelationalDao _dao;
  private String[] _badRoutes;
  private String _agencyId;
  private Properties _staticMappings;

  private final Pattern _routeExtract = Pattern.compile("^([A-Z0-9]+)(c?v?S?[0-9]?).*$");
  private Map<String, AgencyAndId> _routeMappings = new HashMap<String, AgencyAndId>();
  private Predicate<String> _matchBadRoutes;
  private List<Route> _gtfsRoutes;

  @Inject
  public void setWMATAAPIService(WMATAAPIService api) {
    _api = api;
  }

  @Inject
  public void setGtfsRelationalDao(GtfsRelationalDao dao) {
    _dao = dao;
  }

  @Inject
  public void setBadRoutes(@Named("WMATA.badRoutes")
  String badRoutesString) {
    _badRoutes = badRoutesString.split(",");
    _matchBadRoutes = Predicates.in(Arrays.asList(_badRoutes));
  }

  @Inject
  public void setAgencyId(@Named("WMATA.agencyID")
  String agencyId) {
    _agencyId = agencyId;
  }

  @Inject
  public void setStaticMappings(@Named("WMATA.staticMappings")
  Properties staticMappings) {
    _staticMappings = staticMappings;
  }

  @PostConstruct
  public void start() throws WMATAAPIException {
    _gtfsRoutes = _dao.getRoutesForAgency(_dao.getAgencyForId(_agencyId));
    primeCaches();
  }

  public void primeCaches() throws WMATAAPIException {
    for (WMATARoute r : _api.downloadRouteList().getRoutes()) {
      AgencyAndId mapResult = mapBusRoute(r.getRouteID());

      if (mapResult != null) {
        _routeMappings.put(r.getRouteID(), mapResult);
      }
    }

    String[] railRoutes = new String[] {
        "RED", "ORANGE", "YELLOW", "GREEN", "BLUE", "SILVER"}; //FIXME: avoid hardcoding

    for (String r : railRoutes) {
      AgencyAndId mapResult = mapRailRoute(r);

      if (mapResult != null) {
        _routeMappings.put(r, mapResult);
      }
    }
  }

  private AgencyAndId mapBusRoute(String routeID) {
    if (_staticMappings.containsKey(routeID)) {
      String staticMappedRoute = _staticMappings.getProperty(routeID);
      Optional<Route> matchedRoute = Iterables.tryFind(_gtfsRoutes,
          new ShortNameFilterPredicate(staticMappedRoute,
              true));

      if (matchedRoute.isPresent()) {
        AgencyAndId mappedRouteID = matchedRoute.get().getId();
        _log.info("Mapped WMATA route " + routeID + " to GTFS route "
            + mappedRouteID + " (using override)");
        return mappedRouteID;
      } else {
        _log.warn("Could not apply static mapping of {} to {}; continuing...", routeID, staticMappedRoute);
      }
    }

    Matcher m = _routeExtract.matcher(routeID);

    if (m.matches()) {
      final String filteredRouteID = m.group(1);
      if (!_matchBadRoutes.apply(filteredRouteID)) {
        Optional<Route> matchedRoute = Iterables.tryFind(_gtfsRoutes,
            new ShortNameFilterPredicate(filteredRouteID, true));

        if (matchedRoute.isPresent()) {
          AgencyAndId mappedRouteID = matchedRoute.get().getId();
          _log.info("Mapped WMATA route " + routeID + " to GTFS route "
              + mappedRouteID);
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
    Optional<Route> matchedRoute = Iterables.tryFind(_gtfsRoutes,
        new ShortNameFilterPredicate(routeName, false));

    if (matchedRoute.isPresent()) {
      AgencyAndId mappedRouteID = matchedRoute.get().getId();
      _log.info("Mapped WMATA route " + routeName + " to GTFS route "
          + mappedRouteID);
      return mappedRouteID;
    } else {
      _log.warn("Could not map WMATA route: " + routeName);
      return null;
    }
  }

  public AgencyAndId getRouteMapping(String routeID) {
    if (_routeMappings.containsKey(routeID)) {
      return _routeMappings.get(routeID);
    } else {
      return null;
    }
  }

  public Map<String, AgencyAndId> getRouteMappings() {
    return ImmutableMap.<String, AgencyAndId> copyOf(_routeMappings);
  }

  private static class ShortNameFilterPredicate implements Predicate<Route> {

    private String shortName;
    private boolean caseSensitive;

    public ShortNameFilterPredicate(String shortName, boolean caseSensitive) {
      this.shortName = shortName;
      this.caseSensitive = caseSensitive;
    }

    @Override
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
