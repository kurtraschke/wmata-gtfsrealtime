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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.nnsoft.guice.rocoto.Rocoto;
import org.nnsoft.guice.rocoto.configuration.ConfigurationModule;
import org.onebusaway.cli.CommandLineInterfaceLibrary;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusway.gtfs_realtime.exporter.AlertsFileWriter;
import org.onebusway.gtfs_realtime.exporter.AlertsServlet;
import org.onebusway.gtfs_realtime.exporter.TripUpdatesFileWriter;
import org.onebusway.gtfs_realtime.exporter.TripUpdatesServlet;
import org.onebusway.gtfs_realtime.exporter.VehiclePositionsFileWriter;
import org.onebusway.gtfs_realtime.exporter.VehiclePositionsServlet;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class WMATARealtimeMain {

    private static final String ARG_TRIP_UPDATES_PATH = "tripUpdatesPath";
    private static final String ARG_TRIP_UPDATES_URL = "tripUpdatesUrl";
    private static final String ARG_VEHICLE_POSITIONS_PATH = "vehiclePositionsPath";
    private static final String ARG_VEHICLE_POSITIONS_URL = "vehiclePositionsUrl";
    private static final String ARG_ALERTS_PATH = "alertsPath";
    private static final String ARG_ALERTS_URL = "alertsUrl";
    private static final String ARG_CONFIG_FILE = "config";

    public static void main(String[] args) throws Exception {
        System.setProperty("net.sf.ehcache.enableShutdownHook", "true");
        WMATARealtimeMain m = new WMATARealtimeMain();
        m.run(args);
    }
    private GTFSRealtimeProviderImpl _provider;
    private LifecycleService _lifecycleService;

    @Inject
    public void setProvider(GTFSRealtimeProviderImpl provider) {
        _provider = provider;
    }

    @Inject
    public void setLifecycleService(LifecycleService lifecycleService) {
        _lifecycleService = lifecycleService;
    }

    public void run(String[] args) throws Exception {
        //The Hessian client uses java.util.logging, so we bridge it to
        //slf4j, so all logging is funneled into logback.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();


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

        Injector injector = Guice.createInjector(
                new ConfigurationModule() {
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

        if (cli.hasOption(ARG_TRIP_UPDATES_URL)) {
            URL url = new URL(cli.getOptionValue(ARG_TRIP_UPDATES_URL));
            TripUpdatesServlet servlet = injector.getInstance(TripUpdatesServlet.class);
            servlet.setUrl(url);
        }

        if (cli.hasOption(ARG_TRIP_UPDATES_PATH)) {
            File path = new File(cli.getOptionValue(ARG_TRIP_UPDATES_PATH));
            TripUpdatesFileWriter writer = injector.getInstance(TripUpdatesFileWriter.class);
            writer.setPath(path);
        }

        if (cli.hasOption(ARG_VEHICLE_POSITIONS_URL)) {
            URL url = new URL(cli.getOptionValue(ARG_VEHICLE_POSITIONS_URL));
            VehiclePositionsServlet servlet = injector.getInstance(VehiclePositionsServlet.class);
            servlet.setUrl(url);
        }

        if (cli.hasOption(ARG_VEHICLE_POSITIONS_PATH)) {
            File path = new File(cli.getOptionValue(ARG_VEHICLE_POSITIONS_PATH));
            VehiclePositionsFileWriter writer = injector.getInstance(VehiclePositionsFileWriter.class);
            writer.setPath(path);
        }

        if (cli.hasOption(ARG_ALERTS_URL)) {
            URL url = new URL(cli.getOptionValue(ARG_ALERTS_URL));
            AlertsServlet servlet = injector.getInstance(AlertsServlet.class);
            servlet.setUrl(url);
        }

        if (cli.hasOption(ARG_ALERTS_PATH)) {
            File path = new File(cli.getOptionValue(ARG_ALERTS_PATH));
            AlertsFileWriter writer = injector.getInstance(AlertsFileWriter.class);
            writer.setPath(path);
        }

        _lifecycleService.start();
    }

    private void printUsage() {
        CommandLineInterfaceLibrary.printUsage(getClass());
    }

    protected void buildOptions(Options options) {
        /*
         * FIXME: make the configuration file a required option;
         * consider moving the rest into the configuration file.
         */
        options.addOption(ARG_TRIP_UPDATES_PATH, true, "trip updates path");
        options.addOption(ARG_TRIP_UPDATES_URL, true, "trip updates url");
        options.addOption(ARG_VEHICLE_POSITIONS_PATH, true,
                "vehicle positions path");
        options.addOption(ARG_VEHICLE_POSITIONS_URL, true, "vehicle positions url");
        options.addOption(ARG_ALERTS_PATH, true,
                "alerts path");
        options.addOption(ARG_ALERTS_URL, true, "alerts url");
        options.addOption(ARG_CONFIG_FILE, true, "configuration file path");

    }
}