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

import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeExporterModule;
import org.onebusaway.guice.jsr250.JSR250Module;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.kurtraschke.wmata.gtfsrealtime.services.AgencyTimeZoneProvider;
import com.kurtraschke.wmata.gtfsrealtime.services.CalendarServiceDataProvider;
import com.kurtraschke.wmata.gtfsrealtime.services.GtfsRelationalDaoProvider;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

import java.util.Set;
import java.util.TimeZone;

public class WMATARealtimeModule extends AbstractModule {

  public static void addModuleAndDependencies(Set<Module> modules) {
    modules.add(new WMATARealtimeModule());
    GtfsRealtimeExporterModule.addModuleAndDependencies(modules);
    JSR250Module.addModuleAndDependencies(modules);
  }

  @Override
  protected void configure() {
    bind(CacheManager.class).toInstance(CacheManager.getInstance());

    bind(Cache.class).annotatedWith(Names.named("caches.api")).toInstance(
        CacheManager.getInstance().getCache("wmataapi"));
    bind(Cache.class).annotatedWith(Names.named("caches.trip")).toInstance(
        CacheManager.getInstance().getCache("wmatatrip"));
    bind(Cache.class).annotatedWith(Names.named("caches.alertID")).toInstance(
        CacheManager.getInstance().getCache("wmataalertid"));

    bind(CalendarServiceData.class).toProvider(
        CalendarServiceDataProvider.class).in(Scopes.SINGLETON);
    bind(GtfsRelationalDao.class).toProvider(GtfsRelationalDaoProvider.class).in(
        Scopes.SINGLETON);
    bind(TimeZone.class).annotatedWith(AgencyTimeZone.class).toProvider(
        AgencyTimeZoneProvider.class).in(Scopes.SINGLETON);

    requestStaticInjection(DateTimeUtils.class);
  }

  /**
   * Implement hashCode() and equals() such that two instances of the module
   * will be equal.
   */
  @Override
  public int hashCode() {
    return this.getClass().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    return this.getClass().equals(o.getClass());
  }
}