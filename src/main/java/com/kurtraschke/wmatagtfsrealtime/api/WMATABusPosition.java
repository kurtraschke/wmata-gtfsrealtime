/*
 * Copyright (C) 2012 Kurt Raschke
 * 
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
package com.kurtraschke.wmatagtfsrealtime.api;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author kurt
 */
public class WMATABusPosition implements Serializable {

    private static final long serialVersionUID = 1L;
    private final SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public Date dateTime;
    public float deviation;
    public int directionNum;
    public String directionText;
    public float lat;
    public float lon;
    public String routeID;
    public String tripHeadsign;
    public String tripID;
    public String tripStartTime;
    public String tripEndTime;
    public String vehicleID;

    public Date getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) throws ParseException {
        this.dateTime = parseFormat.parse(dateTime);
    }

    public float getDeviation() {
        return deviation;
    }

    public void setDeviation(String deviation) {
        setDeviation(Float.parseFloat(deviation));
    }

    public void setDeviation(float deviation) {
        this.deviation = deviation;
    }

    public int getDirectionNum() {
        return directionNum;
    }

    public void setDirectionNum(String directionNum) {
        setDirectionNum(Integer.parseInt(directionNum));

    }

    public void setDirectionNum(int directionNum) {
        this.directionNum = directionNum;
    }

    public String getDirectionText() {
        return directionText;
    }

    public void setDirectionText(String directionText) {
        this.directionText = directionText;
    }

    public float getLat() {
        return lat;
    }

    public void setLat(String lat) {
        setLat(Float.parseFloat(lat));

    }

    public void setLat(float lat) {
        this.lat = lat;
    }

    public float getLon() {
        return lon;
    }

    public void setLon(String lon) {
        setLon(Float.parseFloat(lon));
    }

    public void setLon(float lon) {
        this.lon = lon;
    }

    public String getRouteID() {
        return routeID;
    }

    public void setRouteID(String routeID) {
        this.routeID = routeID;
    }

    public String getTripHeadsign() {
        return tripHeadsign;
    }

    public void setTripHeadsign(String tripHeadsign) {
        this.tripHeadsign = tripHeadsign;
    }

    public String getTripID() {
        return tripID;
    }

    public void setTripID(String tripID) {
        this.tripID = tripID;
    }

    public String getTripStartTime() {
        return tripStartTime;
    }

    public void setTripStartTime(String tripStartTime) {
        this.tripStartTime = tripStartTime;
    }

    public String getTripEndTime() {
        return tripEndTime;
    }

    public void setTripEndTime(String tripEndTime) {
        this.tripEndTime = tripEndTime;
    }

    public String getVehicleID() {
        return vehicleID;
    }

    public void setVehicleID(String vehicleID) {
        this.vehicleID = vehicleID;
    }
}
