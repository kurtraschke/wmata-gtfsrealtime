/*
 * Copyright (C) 2012 Google, Inc.
 * Copyright (C) 2012 Kurt Raschke
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.sf.ehcache.CacheManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.nnsoft.guice.rocoto.Rocoto;
import org.nnsoft.guice.rocoto.configuration.ConfigurationModule;
import org.onebusaway.cli.CommandLineInterfaceLibrary;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.slf4j.LoggerFactory;

public class WMATARealtimeTester {

    private static final String ARG_CONFIG_FILE = "config";
    private static final String ARG_SERVICE_DATE = "serviceDate";
    private static final String ARG_TRIP_ID = "tripID";
    private static final String ARG_ROUTE_ID = "routeID";
    private LifecycleService _lifecycleService;
    private WMATATripMapperService _service;
    private CacheManager _cacheManager;

    public static void main(String[] args) throws Exception {
        WMATARealtimeTester t = new WMATARealtimeTester();
        t.run(args);
    }

    @Inject
    public void setLifecycleService(LifecycleService lifecycleService) {
        _lifecycleService = lifecycleService;
    }

    @Inject
    public void setCacheManager(CacheManager cacheManager) {
        _cacheManager = cacheManager;
    }

    @Inject
    public void setWMATATripMapperService(WMATATripMapperService service) {
        _service = service;
    }

    public void run(String[] args) throws Exception {
        //Force on debug logging (otherwise disabled in logback.xml)
        Logger root = (Logger) LoggerFactory.getLogger(WMATATripMapperService.class);
        root.setLevel(Level.ALL);

        if (args.length == 0 || CommandLineInterfaceLibrary.wantsHelp(args)) {
            printUsage();
            System.exit(-1);
        }

        Options options = new Options();
        buildOptions(options);
        Parser parser = new GnuParser();
        final CommandLine cli = parser.parse(options, args);

        Set<Module> modules = new HashSet<Module>();
        WMATARealtimeModule.addModuleAndDependencies(modules);


        Injector injector = Guice.createInjector(new ConfigurationModule() {
            @Override
            protected void bindConfigurations() {
                bindEnvironmentVariables();
                bindSystemProperties();

                if (cli.hasOption(ARG_CONFIG_FILE)) {
                    bindProperties(new File(cli.getOptionValue(ARG_CONFIG_FILE)));
                }
            }
        },
                Rocoto.expandVariables(modules));
        injector.injectMembers(this);
        _lifecycleService.start();


        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        AgencyAndId mappedTripID = _service.getTripMapping(new ServiceDate(dateFormat.parse(cli.getOptionValue(ARG_SERVICE_DATE))),
                cli.getOptionValue(ARG_TRIP_ID), cli.getOptionValue(ARG_ROUTE_ID));
        System.out.println(mappedTripID);
        _cacheManager.shutdown();
        _lifecycleService.stop();
    }

    private void printUsage() {
        CommandLineInterfaceLibrary.printUsage(getClass());
    }

    protected void buildOptions(Options options) {
        options.addOption(ARG_CONFIG_FILE, true, "configuration file path");
        options.addOption(ARG_SERVICE_DATE, true, "configuration file path");
        options.addOption(ARG_ROUTE_ID, true, "configuration file path");
        options.addOption(ARG_TRIP_ID, true, "configuration file path");

    }
}