package com.kurtraschke.wmata.gtfsrealtime;

public class WMATAAPIException extends Exception {
  private static final long serialVersionUID = 1L;

  public WMATAAPIException(Exception e) {
    super(e);
  }

}
