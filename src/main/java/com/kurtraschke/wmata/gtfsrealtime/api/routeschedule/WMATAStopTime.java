/*
 * Copyright (C) 2014 Kurt Raschke <kurt@kurtraschke.com>
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
package com.kurtraschke.wmata.gtfsrealtime.api.routeschedule;

import com.kurtraschke.wmata.gtfsrealtime.DateTimeUtils;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

public class WMATAStopTime implements Serializable, Comparable<WMATAStopTime> {

  private static final long serialVersionUID = 1L;
  private String stopID;
  private String stopName;
  private int stopSeq;
  private Date time;

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
    this.time = DateTimeUtils.parse(time);
  }

  @Override
  public int compareTo(WMATAStopTime o) {
    return this.getTime().compareTo(o.getTime());
  }

  @Override
  public String toString() {
    return "WMATAStopTime [stopID=" + stopID + ", stopName=" + stopName
        + ", stopSeq=" + stopSeq + ", time=" + time + "]";
  }



}
