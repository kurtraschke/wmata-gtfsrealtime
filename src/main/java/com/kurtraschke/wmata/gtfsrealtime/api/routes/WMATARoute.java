package com.kurtraschke.wmata.gtfsrealtime.api.routes;

import java.io.Serializable;

public class WMATARoute implements Serializable {

  private static final long serialVersionUID = 1L;
  private String name;
  private String routeID;

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

  @Override
  public String toString() {
    return "Route [name=" + name + ", routeID=" + routeID + "]";
  }

}
