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
package com.kurtraschke.wmatagtfsrealtime;

import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.io.Serializable;

/**
 *
 * @author kurt
 */
public class TripMapKey implements Serializable {

  private static final long serialVersionUID = 2L;
  public ServiceDate serviceDate;
  public String tripID;

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 23 * hash
        + (this.serviceDate != null ? this.serviceDate.hashCode() : 0);
    hash = 23 * hash + (this.tripID != null ? this.tripID.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final TripMapKey other = (TripMapKey) obj;
    if (this.serviceDate != other.serviceDate
        && (this.serviceDate == null || !this.serviceDate.equals(other.serviceDate))) {
      return false;
    }
    if ((this.tripID == null) ? (other.tripID != null)
        : !this.tripID.equals(other.tripID)) {
      return false;
    }
    return true;
  }

  public TripMapKey(ServiceDate serviceDate, String tripID) {
    this.serviceDate = serviceDate;
    this.tripID = tripID;
  }
}