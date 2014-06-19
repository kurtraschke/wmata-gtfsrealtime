wmata-gtfsrealtime
==================

GTFS-realtime StopTimeUpdate, VehiclePosition, and Alert feeds for WMATA.  The StopTimeUpdate and VehiclePosition feeds are produced using WMATA's [BusPositions API](http://developer.wmata.com/docs/read/GetBusPositions), while the Alert feed uses RSS feeds from [MetroAlerts](http://www.wmata.com/rider_tools/metro_service_status/rail_bus.cfm?).

Building
--------

Build with Maven:

`mvn clean install`

Running
-------

Copy `config.sample` to `config`, and edit to set values for `WMATA.key` and `GTFS.path`.  You may also need to update `WMATA.agencyID`, `WMATA.badRoutes` and `WMATA.staticMappings`, but the defaults should be fine.

Then, run with:

`java -jar target/wmata-gtfsrealtime-1.0-SNAPSHOT-withAllDependencies.jar --config config`

Visit `http://localhost:9000/tripUpdates?debug` to view the generated feed.
