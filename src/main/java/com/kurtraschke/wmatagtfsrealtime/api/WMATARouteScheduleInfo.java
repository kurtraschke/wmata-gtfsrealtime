/*
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
package com.kurtraschke.wmatagtfsrealtime.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author kurt
 */
public class WMATARouteScheduleInfo implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private Map<Integer, WMATADirection> directions = new HashMap<Integer, WMATADirection>();

    public WMATARouteScheduleInfo() {
        WMATADirection d0 = new WMATADirection();
        d0.setDirectionNum(0);
        directions.put(0, d0);

        WMATADirection d1 = new WMATADirection();
        d1.setDirectionNum(1);
        directions.put(1, d0);

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addDirection0Trip(WMATATrip trip) {
        directions.get(0).addTrip(trip);
    }

    public List<WMATATrip> getDirection0Trips() {
        return directions.get(0).getTrips();
    }

    public void addDirection1Trip(WMATATrip trip) {
        directions.get(1).addTrip(trip);
    }

    public List<WMATATrip> getDirection1Trips() {
        return directions.get(1).getTrips();
    }

    public Collection<WMATADirection> getDirections() {
        return directions.values();
    }

    public Collection<WMATAStop> getStops() {
        Set<WMATAStop> stops = new HashSet<WMATAStop>();

        for (WMATADirection d : directions.values()) {
            stops.addAll(d.getStops());
        }

        return stops;

    }

    public List<WMATATrip> getTrips() {
        List<WMATATrip> trips = new ArrayList<WMATATrip>();

        for (WMATADirection d : directions.values()) {
            trips.addAll(d.getTrips());
        }
        return trips;

    }
}
