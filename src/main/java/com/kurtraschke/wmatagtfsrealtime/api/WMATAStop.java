/**
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
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author kurt
 */
public class WMATAStop implements Serializable {

    private static final long serialVersionUID = 1L;
    private float lat;
    private float lon;
    private String name;
    private String stopID;
    private List<String> routes = new ArrayList<String>();

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

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + (this.stopID != null ? this.stopID.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final WMATAStop other = (WMATAStop) obj;
        if ((this.stopID == null) ? (other.stopID != null) : !this.stopID.equals(other.stopID)) {
            return false;
        }
        return true;
    }

    public void setLon(float lon) {
        this.lon = lon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStopID() {
        return stopID;
    }

    public void setStopID(String stopID) {
        this.stopID = stopID;
    }

    public void addRoute(String route) {
        routes.add(route);
    }

    public List<String> getRoutes() {
        return routes;
    }
}
