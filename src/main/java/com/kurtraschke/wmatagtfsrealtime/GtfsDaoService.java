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

import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs.services.calendar.CalendarServiceDataFactory;

import java.io.File;
import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 *
 * @author kurt
 */
@Singleton
public class GtfsDaoService {

  private File _gtfsPath;
  private GtfsMutableRelationalDao dao;
  private CalendarServiceData csd;

  public GtfsDaoService() {
  }

  @Inject
  public void setGtfsPath(@Named("GTFS.path")
  File gtfsPath) {
    this._gtfsPath = gtfsPath;
  }

  public GtfsRelationalDao getGtfsRelationalDao() {
    return dao;
  }

  public CalendarServiceData getCalendarServiceData() {
    return csd;
  }

  @PostConstruct
  public void start() throws IOException {
    dao = new GtfsRelationalDaoImpl();
    GtfsReader reader = new GtfsReader();
    reader.setInputLocation(_gtfsPath);
    reader.setEntityStore(dao);
    reader.run();
    CalendarServiceDataFactory csdf = new CalendarServiceDataFactoryImpl(dao);
    csd = csdf.createData();
  }
}
