/**
 * Copyright (C) 2012 OneBusAway.
 * Copyright (C) 2012 Kurt Raschke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kurtraschke.wmatagtfsrealtime;

import com.kurtraschke.wmatagtfsrealtime.api.WMATAAlert;
import com.kurtraschke.wmatagtfsrealtime.api.WMATABusPosition;
import com.kurtraschke.wmatagtfsrealtime.api.WMATARoute;
import com.kurtraschke.wmatagtfsrealtime.api.WMATARouteScheduleInfo;
import com.kurtraschke.wmatagtfsrealtime.api.WMATAStop;
import com.kurtraschke.wmatagtfsrealtime.api.WMATAStopTime;
import com.kurtraschke.wmatagtfsrealtime.api.WMATATrip;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

/**
 *
 * @author kurt
 */
@Singleton
public class WMATAAPIService {

    private DownloaderService _downloader;
    @Inject
    @Named( "WMATA.key")
    private String API_KEY;
    @Inject
    @Named( "cache.directory")
    private String CACHE_DIRECTORY;
    private Digester _stopDigester = getStopDigester();
    private Digester _routeDigester = getRouteDigester();
    private Digester _routeScheduleDigester = getRouteScheduleDigester();
    private Digester _busPositionDigester = getBusPositionDigester();
    private Digester _alertDigester = getAlertDigester();

    private Digester getStopDigester() {
        Digester stopDigester = new Digester();

        stopDigester.addObjectCreate("StopsResp/Stops", ArrayList.class);
        stopDigester.addObjectCreate("StopsResp/Stops/Stop", WMATAStop.class);

        stopDigester.addCallMethod("StopsResp/Stops/Stop/Lat", "setLat", 0);
        stopDigester.addCallMethod("StopsResp/Stops/Stop/Lon", "setLon", 0);
        stopDigester.addCallMethod("StopsResp/Stops/Stop/Name", "setName", 0);
        stopDigester.addCallMethod("StopsResp/Stops/Stop/StopID", "setStopID", 0);

        stopDigester.addCallMethod("StopsResp/Stops/Stop/Routes/a:string", "addRoute", 0); //so much for namespace support

        stopDigester.addSetNext("StopsResp/Stops/Stop", "add");

        return stopDigester;
    }

    private Digester getRouteDigester() {
        Digester routeDigester = new Digester();

        routeDigester.addObjectCreate("RoutesResp/Routes", ArrayList.class);
        routeDigester.addObjectCreate("RoutesResp/Routes/Route", WMATARoute.class);

        routeDigester.addCallMethod("RoutesResp/Routes/Route/RouteID", "setRouteID", 0);
        routeDigester.addCallMethod("RoutesResp/Routes/Route/Name", "setName", 0);

        routeDigester.addSetNext("RoutesResp/Routes/Route", "add");

        return routeDigester;
    }

    private Digester getRouteScheduleDigester() {
        Digester routeScheduleDigester = new Digester();

        routeScheduleDigester.addObjectCreate("RouteScheduleInfo", WMATARouteScheduleInfo.class);

        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Name", "setName", 0);

        routeScheduleDigester.addObjectCreate("RouteScheduleInfo/Direction0/Trip", WMATATrip.class);

        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/DirectionNum", "setDirectionNum", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/EndTime", "setEndTime", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/RouteID", "setRouteID", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/StartTime", "setStartTime", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/TripDirectionText", "setTripDirectionText", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/TripHeadsign", "setTripHeadsign", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/TripID", "setTripID", 0);


        routeScheduleDigester.addObjectCreate("RouteScheduleInfo/Direction0/Trip/StopTimes/StopTime", WMATAStopTime.class);

        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/StopTimes/StopTime/StopID", "setStopID", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/StopTimes/StopTime/StopName", "setStopName", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/StopTimes/StopTime/StopSeq", "setStopSeq", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction0/Trip/StopTimes/StopTime/Time", "setTime", 0);


        routeScheduleDigester.addSetNext("RouteScheduleInfo/Direction0/Trip/StopTimes/StopTime", "addStopTime");
        routeScheduleDigester.addSetNext("RouteScheduleInfo/Direction0/Trip", "addDirection0Trip");


        routeScheduleDigester.addObjectCreate("RouteScheduleInfo/Direction1/Trip", WMATATrip.class);

        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/DirectionNum", "setDirectionNum", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/EndTime", "setEndTime", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/RouteID", "setRouteID", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/StartTime", "setStartTime", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/TripDirectionText", "setTripDirectionText", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/TripHeadsign", "setTripHeadsign", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/TripID", "setTripID", 0);


        routeScheduleDigester.addObjectCreate("RouteScheduleInfo/Direction1/Trip/StopTimes/StopTime", WMATAStopTime.class);

        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/StopTimes/StopTime/StopID", "setStopID", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/StopTimes/StopTime/StopName", "setStopName", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/StopTimes/StopTime/StopSeq", "setStopSeq", 0);
        routeScheduleDigester.addCallMethod("RouteScheduleInfo/Direction1/Trip/StopTimes/StopTime/Time", "setTime", 0);


        routeScheduleDigester.addSetNext("RouteScheduleInfo/Direction1/Trip/StopTimes/StopTime", "addStopTime");
        routeScheduleDigester.addSetNext("RouteScheduleInfo/Direction1/Trip", "addDirection1Trip");



        return routeScheduleDigester;
    }

    private Digester getBusPositionDigester() {
        Digester busPositionDigester = new Digester();

        busPositionDigester.addObjectCreate("BusPositionsResp/BusPositions", ArrayList.class);
        busPositionDigester.addObjectCreate("BusPositionsResp/BusPositions/BusPosition", WMATABusPosition.class);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/DateTime", "setDateTime", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/Deviation", "setDeviation", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/DirectionNum", "setDirectionNum", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/Lat", "setLat", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/Lon", "setLon", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/RouteID", "setRouteID", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/TripHeadsign", "setTripHeadsign", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/TripID", "setTripID", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/TripStartTime", "setTripStartTime", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/TripEndTime", "setTripEndTime", 0);
        busPositionDigester.addCallMethod("BusPositionsResp/BusPositions/BusPosition/VehicleID", "setVehicleID", 0);


        busPositionDigester.addSetNext("BusPositionsResp/BusPositions/BusPosition", "add");


        return busPositionDigester;
    }

    private Digester getAlertDigester() {
        Digester alertDigester = new Digester();

        alertDigester.addObjectCreate("rss/channel", ArrayList.class);
        alertDigester.addObjectCreate("rss/channel/item", WMATAAlert.class);

        alertDigester.addCallMethod("rss/channel/item/title", "setTitle", 0);
        alertDigester.addCallMethod("rss/channel/item/link", "setLink", 0);
        alertDigester.addCallMethod("rss/channel/item/description", "setDescription", 0);
        alertDigester.addCallMethod("rss/channel/item/source", "setSource", 0);
        alertDigester.addCallMethod("rss/channel/item/pubDate", "setPubDate", 0);
        alertDigester.addCallMethod("rss/channel/item/guid", "setGuid", 0);

        alertDigester.addSetNext("rss/channel/item", "add");

        return alertDigester;

    }

    @Inject
    public void setDownloader(DownloaderService downloader) {
        _downloader = downloader;
    }

    @SuppressWarnings("unchecked")
    public List<WMATAStop> downloadStopList() throws IOException, SAXException {
        String url = "http://api.wmata.com/Bus.svc/Stops?api_key=" + API_KEY;
        return (List<WMATAStop>) digestUrl(url, true, _stopDigester);
    }

    @SuppressWarnings("unchecked")
    public List<WMATARoute> downloadRouteList() throws IOException, SAXException {
        String url = "http://api.wmata.com/Bus.svc/Routes?api_key=" + API_KEY;
        return (List<WMATARoute>) digestUrl(url, true, _routeDigester);
    }

    @SuppressWarnings("unchecked")
    public WMATARouteScheduleInfo downloadRouteScheduleInfo(String routeID, String date) throws IOException, SAXException {
        String url = "http://api.wmata.com/Bus.svc/RouteSchedule?includeVariations=true&date=" + date + "&routeId=" + URLEncoder.encode(routeID, "utf-8") + "&api_key=" + API_KEY;
        return (WMATARouteScheduleInfo) digestUrl(url, true, _routeScheduleDigester);
    }

    @SuppressWarnings("unchecked")
    public List<WMATABusPosition> downloadBusPositions() throws IOException, SAXException {
        String url = "http://api.wmata.com/Bus.svc/BusPositions?api_key=" + API_KEY;
        return (List<WMATABusPosition>) digestUrl(url, false, _busPositionDigester);
    }

    @SuppressWarnings("unchecked")
    public List<WMATAAlert> downloadBusAlerts() throws IOException, SAXException {
        String url = "http://www.metroalerts.info/rss.aspx?bus";
        return (List<WMATAAlert>) digestUrl(url, false, _alertDigester);
    }

    @SuppressWarnings("unchecked")
    public List<WMATAAlert> downloadRailAlerts() throws IOException, SAXException {
        String url = "http://www.metroalerts.info/rss.aspx?rs";
        return (List<WMATAAlert>) digestUrl(url, false, _alertDigester);
    }

    private Object digestUrl(String url, boolean cache, Digester digester) throws IOException,
            SAXException {
        File cacheFile = getCacheFileForUrl(url);
        if (cache && cacheFile != null && cacheFile.exists()) {
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
                    new FileInputStream(cacheFile)));
            try {
                Object object = ois.readObject();
                ois.close();
                return object;
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }
        InputStream in = _downloader.openUrl(url);
        Object result = digester.parse(in);
        if (cache && cacheFile != null) {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(
                    new FileOutputStream(cacheFile)));
            oos.writeObject(result);
            oos.close();
        }
        return result;
    }

    private File getCacheFileForUrl(String url)
            throws UnsupportedEncodingException {

        if (CACHE_DIRECTORY == null) {
            return null;
        }
        try {
            MessageDigest cript = MessageDigest.getInstance("SHA-1");
            cript.reset();
            cript.update(url.getBytes("utf8"));
            String name = new String(Hex.encodeHex(cript.digest()));
            return new File(CACHE_DIRECTORY, name);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
