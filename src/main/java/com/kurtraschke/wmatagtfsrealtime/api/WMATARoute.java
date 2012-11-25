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

/**
 *
 * @author kurt
 */
public class WMATARoute implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String routeID;

    private WMATARouteScheduleInfo scheduleInfo;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRouteID() {
        return routeID;
    }

    public void setRouteID(String routeID) {
        this.routeID = routeID;
    }

    public WMATARouteScheduleInfo getScheduleInfo() {
        return scheduleInfo;
    }

    public void setScheduleInfo(WMATARouteScheduleInfo scheduleInfo) {
        this.scheduleInfo = scheduleInfo;
    }

}
