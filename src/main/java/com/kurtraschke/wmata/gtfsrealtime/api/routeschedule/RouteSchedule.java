package com.kurtraschke.wmata.gtfsrealtime.api.routeschedule;

import com.google.common.collect.Iterables;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class RouteSchedule implements Serializable {

  private static final long serialVersionUID = 1L;

  private List<WMATATrip> direction0;
  private List<WMATATrip> direction1;

  private String name;

  public List<WMATATrip> getDirection0() {
    return direction0;
  }

  public void setDirection0(List<WMATATrip> direction0) {
    this.direction0 = direction0;
  }

  public List<WMATATrip> getDirection1() {
    return direction1;
  }

  public void setDirection1(List<WMATATrip> direction1) {
    this.direction1 = direction1;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Iterable<WMATATrip> getTrips() {

    return Iterables.concat(
        (direction0 != null) ? direction0 : Collections.<WMATATrip> emptyList(),
        (direction1 != null) ? direction1 : Collections.<WMATATrip> emptyList());
  }

}
