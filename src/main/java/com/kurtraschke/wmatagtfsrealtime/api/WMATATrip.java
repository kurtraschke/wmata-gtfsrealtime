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

import com.kurtraschke.wmatagtfsrealtime.DateParser;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author kurt
 */
public class WMATATrip implements Serializable {

  private static final long serialVersionUID = 1L;
  private int directionNum;
  private Date endTime;
  private String routeID;
  private Date startTime;
  private String tripDirectionText;
  private String tripHeadsign;
  private String tripID;
  private List<WMATAStopTime> stopTimes = new ArrayList<WMATAStopTime>();

  public int getDirectionNum() {
    return directionNum;
  }

  public void setDirectionNum(String directionNum) {
    setDirectionNum(Integer.parseInt(directionNum));
  }

  public void setDirectionNum(int directionNum) {
    this.directionNum = directionNum;
  }

  public Date getEndTime() {
    return endTime;
  }

  public void setEndTime(String endTime) throws ParseException {
    this.endTime = DateParser.parse(endTime);
  }

  public String getRouteID() {
    return routeID;
  }

  public void setRouteID(String routeID) {
    this.routeID = routeID;
  }

  public Date getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) throws ParseException {
    this.startTime = DateParser.parse(startTime);
  }

  public String getTripDirectionText() {
    return tripDirectionText;
  }

  public void setTripDirectionText(String tripDirectionText) {
    this.tripDirectionText = tripDirectionText;
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

  public void addStopTime(WMATAStopTime stopTime) {
    stopTimes.add(stopTime);
  }

  public List<WMATAStopTime> getStopTimes() {
    return stopTimes;
  }
}
