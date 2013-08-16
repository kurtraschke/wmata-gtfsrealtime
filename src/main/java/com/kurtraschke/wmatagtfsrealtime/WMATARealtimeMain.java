/*
 * Copyright (C) 2012 Google, Inc.
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

import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.nnsoft.guice.rocoto.Rocoto;
import org.nnsoft.guice.rocoto.configuration.ConfigurationModule;
import org.nnsoft.guice.rocoto.converters.FileConverter;
import org.nnsoft.guice.rocoto.converters.PropertiesConverter;
import org.nnsoft.guice.rocoto.converters.URLConverter;
import org.onebusaway.cli.CommandLineInterfaceLibrary;
import org.onebusaway.cli.Daemonizer;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeExporter;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeFileWriter;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.Alerts;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.VehiclePositions;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeServlet;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.slf4j.LoggerFactory;

public class WMATARealtimeMain {

    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(WMATARealtimeMain.class);
    private final String ARG_CONFIG_FILE = "config";
    private File _tripUpdatesPath;
    private URL _tripUpdatesUrl;
    private File _vehiclePositionsPath;
    private URL _vehiclePositionsUrl;
    private File _alertsPath;
    private URL _alertsUrl;
    private Injector _injector;
    private GTFSRealtimeProviderImpl _provider;
    private LifecycleService _lifecycleService;
    private GtfsRealtimeExporter _vehiclePositionsExporter;
    private GtfsRealtimeExporter _tripUpdatesExporter;
    private GtfsRealtimeExporter _alertsExporter;

    public static void main(String[] args) throws Exception {
        System.setProperty("net.sf.ehcache.enableShutdownHook", "true");
        WMATARealtimeMain m = new WMATARealtimeMain();
        try {
            m.run(args);
        } catch (CreationException e) {
            e.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    @Inject
    public void setProvider(GTFSRealtimeProviderImpl provider) {
        _provider = provider;
    }

    @Inject
    public void setLifecycleService(LifecycleService lifecycleService) {
        _lifecycleService = lifecycleService;
    }

    @Inject
    public void setVehiclePositionsExporter(@VehiclePositions GtfsRealtimeExporter exporter) {
        _vehiclePositionsExporter = exporter;
    }

    @Inject
    public void setTripUpdatesExporter(@TripUpdates GtfsRealtimeExporter exporter) {
        _tripUpdatesExporter = exporter;
    }

    @Inject
    public void setAlertsExporter(@Alerts GtfsRealtimeExporter exporter) {
        _alertsExporter = exporter;
    }

    public void run(String[] args) throws Exception {
        if (args.length == 0 || CommandLineInterfaceLibrary.wantsHelp(args)) {
            printUsage();
            System.exit(-1);
        }

        Options options = new Options();
        buildOptions(options);
        Daemonizer.buildOptions(options);
        Parser parser = new GnuParser();
        final CommandLine cli = parser.parse(options, args);
        Daemonizer.handleDaemonization(cli);

        Set<Module> modules = new HashSet<Module>();
        WMATARealtimeModule.addModuleAndDependencies(modules);

        _injector = Guice.createInjector(
                new URLConverter(),
                new FileConverter(),
                new PropertiesConverter(),
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

        _injector.injectMembers(this);

        _tripUpdatesUrl = getConfigurationValue(URL.class, "tripUpdates.url");
        if (_tripUpdatesUrl != null) {
            GtfsRealtimeServlet servlet = _injector.getInstance(GtfsRealtimeServlet.class);
            servlet.setUrl(_tripUpdatesUrl);
            servlet.setSource(_tripUpdatesExporter);

        }

        _tripUpdatesPath = getConfigurationValue(File.class, "tripUpdates.path");
        if (_tripUpdatesPath != null) {
            GtfsRealtimeFileWriter writer = _injector.getInstance(GtfsRealtimeFileWriter.class);
            writer.setPath(_tripUpdatesPath);
            writer.setSource(_tripUpdatesExporter);
        }

        _vehiclePositionsUrl = getConfigurationValue(URL.class, "vehiclePositions.url");
        if (_vehiclePositionsUrl != null) {
            GtfsRealtimeServlet servlet = _injector.getInstance(GtfsRealtimeServlet.class);
            servlet.setUrl(_vehiclePositionsUrl);
            servlet.setSource(_vehiclePositionsExporter);
        }

        _vehiclePositionsPath = getConfigurationValue(File.class, "vehiclePositions.path");
        if (_vehiclePositionsPath != null) {
            GtfsRealtimeFileWriter writer = _injector.getInstance(GtfsRealtimeFileWriter.class);
            writer.setPath(_vehiclePositionsPath);
            writer.setSource(_vehiclePositionsExporter);
        }

        _alertsUrl = getConfigurationValue(URL.class, "alerts.url");
        if (_alertsUrl != null) {
            GtfsRealtimeServlet servlet = _injector.getInstance(GtfsRealtimeServlet.class);
            servlet.setUrl(_alertsUrl);
            servlet.setSource(_alertsExporter);
        }

        _alertsPath = getConfigurationValue(File.class, "alerts.path");
        if (_alertsPath != null) {
            GtfsRealtimeFileWriter writer = _injector.getInstance(GtfsRealtimeFileWriter.class);
            writer.setPath(_alertsPath);
            writer.setSource(_alertsExporter);
        }

        _lifecycleService.start();
    }

    private <T> T getConfigurationValue(Class<T> type, String configurationKey) {
        try {
            return _injector.getInstance(Key.get(type, Names.named(configurationKey)));
        } catch (ConfigurationException e) {
            _log.error("Could not get configuration parameter", e);
            return null;
        }
    }

    private void printUsage() {
        CommandLineInterfaceLibrary.printUsage(getClass());
    }

    private void buildOptions(Options options) {
        Option configFile = new Option(ARG_CONFIG_FILE, true, "configuration file path");
        configFile.setRequired(true);
        options.addOption(configFile);
    }
}