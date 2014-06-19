package com.kurtraschke.wmata.gtfsrealtime.api.routes;

import java.io.Serializable;
import java.util.List;

public class Routes implements Serializable {
  private static final long serialVersionUID = 1L;
  private List<WMATARoute> routes;

  public List<WMATARoute> getRoutes() {
    return routes;
  }

  public void setRoutes(List<WMATARoute> routes) {
    this.routes = routes;
  }

  @Override
  public String toString() {
    return "Routes [routes=" + routes + "]";
  }
}
