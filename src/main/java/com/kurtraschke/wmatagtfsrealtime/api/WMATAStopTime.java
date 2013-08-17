/*
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
package com.kurtraschke.wmatagtfsrealtime.api;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 *
 * @author kurt
 */
public class WMATAStopTime implements Serializable, Comparable<WMATAStopTime> {

    private static final long serialVersionUID = 1L;
    private final SimpleDateFormat parseFormat;
    private String stopID;
    private String stopName;
    private int stopSeq;
    private Date time;
    private WMATAStop stop;

    public WMATAStopTime() {
        parseFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        parseFormat.setTimeZone(TimeZone.getTimeZone("US/Eastern"));
    }

    public String getStopID() {
        return stopID;
    }

    public void setStopID(String stopID) {
        this.stopID = stopID;
    }

    public String getStopName() {
        return stopName;
    }

    public void setStopName(String stopName) {
        this.stopName = stopName;
    }

    public int getStopSeq() {
        return stopSeq;
    }

    public void setStopSeq(String stopSeq) {
        this.stopSeq = Integer.parseInt(stopSeq);
    }

    public Date getTime() {
        return time;
    }

    public void setTime(String time) throws ParseException {
        this.time = parseFormat.parse(time);
    }

    public WMATAStop getStop() {
        return stop;
    }

    public void setStop(WMATAStop stop) {
        this.stop = stop;
    }

    public int compareTo(WMATAStopTime o) {
        return this.getTime().compareTo(o.getTime());
    }
}
