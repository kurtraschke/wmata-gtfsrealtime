/*
 * Copyright (C) 2014 Kurt Raschke <kurt@kurtraschke.com>
 * Copyright (C) 2012 OneBusAway
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.kurtraschke.wmata.gtfsrealtime.WMATAAPIException;
import com.kurtraschke.wmata.gtfsrealtime.api.alerts.Rss;
import com.kurtraschke.wmata.gtfsrealtime.api.buspositions.BusPositions;
import com.kurtraschke.wmata.gtfsrealtime.api.routes.Routes;
import com.kurtraschke.wmata.gtfsrealtime.api.routeschedule.RouteSchedule;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 *
 *
 * @author kurt
 */
@Singleton
public class WMATAAPIService {

  private static final Logger _log = LoggerFactory.getLogger(WMATAAPIService.class);
  private static final String API_KEY_PARAM_NAME = "subscription-key";

  private String _apiKey;
  private double _apiRateLimit;
  private Cache _cache;
  private ObjectMapper _jsonMapper;
  private XmlMapper _xmlMapper;
  private HttpClientConnectionManager _connectionManager;
  private RateLimiter _limiter;

  @PostConstruct
  public void start() {
    _jsonMapper = new ObjectMapper();
    _jsonMapper.setPropertyNamingStrategy(PropertyNamingStrategy.PASCAL_CASE_TO_CAMEL_CASE);
    _xmlMapper = new XmlMapper();
    _connectionManager = new BasicHttpClientConnectionManager();
    _limiter = RateLimiter.create(_apiRateLimit);

    if (_apiRateLimit > 9) {
      _log.warn("API rate limit set to {}, greater than default rate limit of 9 queries/second");
    }
  }

  @PreDestroy
  public void stop() {
    _connectionManager.shutdown();
  }

  @Inject
  public void setApiKey(@Named("WMATA.key")
  String apiKey) {
    _apiKey = apiKey;

  }

  @Inject
  public void setApiRateLimit(@Named("WMATA.rateLimit")
  double apiRateLimit) {
    _apiRateLimit = apiRateLimit;
  }

  @Inject
  public void setCache(@Named("caches.api")
  Cache cache) {
    _cache = cache;

  }

  public Routes downloadRouteList() throws WMATAAPIException {
    try {
      URIBuilder b = new URIBuilder("http://api.wmata.com/Bus.svc/json/JRoutes");
      b.addParameter(API_KEY_PARAM_NAME, _apiKey);

      return mapUrl(b.build(), true, Routes.class, _jsonMapper);
    } catch (Exception e) {
      throw new WMATAAPIException(e);
    }
  }

  public RouteSchedule downloadRouteScheduleInfo(String routeId, String date)
      throws WMATAAPIException {
    try {
      URIBuilder b = new URIBuilder(
          "http://api.wmata.com/Bus.svc/json/JRouteSchedule");
      b.addParameter(API_KEY_PARAM_NAME, _apiKey);
      b.addParameter("includeVariations", "false");
      b.addParameter("date", date);
      b.addParameter("routeID", routeId);

      return mapUrl(b.build(), true, RouteSchedule.class, _jsonMapper);
    } catch (Exception e) {
      throw new WMATAAPIException(e);
    }
  }

  public BusPositions downloadBusPositions() throws WMATAAPIException {
    try {
      URIBuilder b = new URIBuilder(
          "http://api.wmata.com/Bus.svc/json/JBusPositions");
      b.addParameter(API_KEY_PARAM_NAME, _apiKey);

      return mapUrl(b.build(), false, BusPositions.class, _jsonMapper);
    } catch (Exception e) {
      throw new WMATAAPIException(e);
    }
  }

  public Rss downloadBusAlerts() throws WMATAAPIException {
    try {
      URIBuilder b = new URIBuilder("http://www.metroalerts.info/rss.aspx?bus");
      return mapUrl(b.build(), false, Rss.class, _xmlMapper);
    } catch (Exception e) {
      throw new WMATAAPIException(e);
    }
  }

  public Rss downloadRailAlerts() throws WMATAAPIException {
    try {
      URIBuilder b = new URIBuilder("http://www.metroalerts.info/rss.aspx?rs");
      return mapUrl(b.build(), false, Rss.class, _xmlMapper);
    } catch (Exception e) {
      throw new WMATAAPIException(e);
    }
  }

  private <T> T mapUrl(URI url, boolean cache, Class<T> theClass,
      ObjectMapper mapper) throws IOException {

    Element e = _cache.get(url);

    if (cache && e != null) {
      Object value = e.getObjectValue();
      if (theClass.isInstance(value)) {
        return theClass.cast(value);
      }
    }

    CloseableHttpClient client = HttpClients.custom().setConnectionManager(
        _connectionManager).build();

    HttpGet httpget = new HttpGet(url);
    _limiter.acquire();
    try (CloseableHttpResponse response = client.execute(httpget);
        InputStream responseInputStream = response.getEntity().getContent()) {

      T value = mapper.readValue(responseInputStream, theClass);

      if (cache) {
        _cache.put(new Element(url, value));
      }

      return value;
    }
  }
}
