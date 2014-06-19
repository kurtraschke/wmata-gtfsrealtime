package com.kurtraschke.wmata.gtfsrealtime.api.buspositions;

import org.onebusaway.gtfs.model.calendar.ServiceDate;

import com.kurtraschke.wmata.gtfsrealtime.DateTimeUtils;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

public class BusPosition implements Serializable {

  private static final long serialVersionUID = 1L;
  private Date dateTime;
  private float deviation;
  private int directionNum;
  private String directionText;
  private float lat;
  private float lon;
  private String routeID;
  private String tripHeadsign;
  private String tripID;
  private Date tripStartTime;
  private Date tripEndTime;
  private String vehicleID;

  public Date getDateTime() {
    return dateTime;
  }

  public void setDateTime(String dateTime) throws ParseException {
    this.dateTime = DateTimeUtils.parse(dateTime);
  }

  public float getDeviation() {
    return deviation;
  }

  public void setDeviation(float deviation) {
    this.deviation = deviation;
  }

  public int getDirectionNum() {
    return directionNum;
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

  public void setLat(float lat) {
    this.lat = lat;
  }

  public float getLon() {
    return lon;
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

  public Date getTripStartTime() {
    return tripStartTime;
  }

  public void setTripStartTime(String tripStartTime) throws ParseException {
    this.tripStartTime = DateTimeUtils.parse(tripStartTime);
  }

  public Date getTripEndTime() {
    return tripEndTime;
  }

  public void setTripEndTime(String tripEndTime) throws ParseException {
    this.tripEndTime = DateTimeUtils.parse(tripEndTime);
  }

  public String getVehicleID() {
    return vehicleID;
  }

  public void setVehicleID(String vehicleID) {
    this.vehicleID = vehicleID;
  }

  public ServiceDate getServiceDate() {
    return DateTimeUtils.serviceDateFromDate(this.getTripStartTime());
  }

  @Override
  public String toString() {
    return "BusPosition [dateTime=" + dateTime + ", deviation=" + deviation
        + ", directionNum=" + directionNum + ", directionText=" + directionText
        + ", lat=" + lat + ", lon=" + lon + ", routeID=" + routeID
        + ", tripHeadsign=" + tripHeadsign + ", tripID=" + tripID
        + ", tripStartTime=" + tripStartTime + ", tripEndTime=" + tripEndTime
        + ", vehicleID=" + vehicleID + "]";
  }



}
