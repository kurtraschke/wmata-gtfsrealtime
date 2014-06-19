package com.kurtraschke.wmata.gtfsrealtime.api.buspositions;

import java.io.Serializable;
import java.util.List;

public class BusPositions implements Serializable {
  private static final long serialVersionUID = 1L;

  private List<BusPosition> busPositions;

  public List<BusPosition> getBusPositions() {
    return busPositions;
  }

  public void setBusPositions(List<BusPosition> busPositions) {
    this.busPositions = busPositions;
  }

  @Override
  public String toString() {
    return "BusPositions [busPositions=" + busPositions + "]";
  }


}
