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
