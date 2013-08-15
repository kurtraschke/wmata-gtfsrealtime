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

import java.io.File;
import java.io.IOException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs.services.GtfsRelationalDao;

/**
 *
 * @author kurt
 */
@Singleton
public class GtfsDaoService {

    @Inject
    @Named("GTFS.path")
    public File gtfsPath;
    public GtfsMutableRelationalDao dao;

    public GtfsRelationalDao getDao() {
        return dao;
    }

    public GtfsDaoService() {
        dao = new GtfsRelationalDaoImpl();
    }

    @PostConstruct
    public void start() throws IOException {
        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(gtfsPath);
        reader.setEntityStore(dao);
        reader.run();
    }
}
