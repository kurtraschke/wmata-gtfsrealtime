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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author kurt
 */
public class WMATADirection implements Serializable {

    private static final long serialVersionUID = 1L;
    private int directionNum;
    private List<WMATATrip> trips = new ArrayList<WMATATrip>();

    public int getDirectionNum() {
        return directionNum;
    }

    public void setDirectionNum(int directionNum) {
        this.directionNum = directionNum;
    }

    public List<WMATATrip> getTrips() {
        return trips;
    }

    public void addTrip(WMATATrip trip) {
        trips.add(trip);
    }

    public List<WMATAStop> getStops() {
        Set<WMATAStop> stops = new HashSet<WMATAStop>();

        for (WMATATrip t : trips) {

            for (WMATAStopTime st : t.getStopTimes()) {
                stops.add(st.getStop());
            }
        }
        return new ArrayList<WMATAStop>(stops);

    }
}
