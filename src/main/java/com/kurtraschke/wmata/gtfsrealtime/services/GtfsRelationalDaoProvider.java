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

import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsRelationalDao;

import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

public class GtfsRelationalDaoProvider implements Provider<GtfsRelationalDao> {

  private static final Logger _log = LoggerFactory.getLogger(GtfsRelationalDaoProvider.class);

  private File _gtfsPath;

  @Inject
  public void setGtfsPath(@Named("GTFS.path")
  File gtfsPath) {
    _gtfsPath = gtfsPath;
  }

  @Override
  public GtfsRelationalDao get() {
    _log.info("Loading GTFS from {}", _gtfsPath.toString());
    GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
    GtfsReader reader = new GtfsReader();
    reader.setEntityStore(dao);
    try {
      reader.setInputLocation(_gtfsPath);
      reader.run();
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException("Failure while reading GTFS", e);
    }
    return dao;
  }
}
