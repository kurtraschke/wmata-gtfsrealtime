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

package com.kurtraschke.wmata.gtfsrealtime;

import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.inject.Inject;

public class DateTimeUtils {

  @Inject
  @AgencyTimeZone
  private static TimeZone _agencyTimeZone;

  private DateTimeUtils() {

  }

  public static Date parse(String date) throws ParseException {
    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    parseFormat.setTimeZone(_agencyTimeZone);
    return parseFormat.parse(date);
  }

  public static Date parseRssTimestamp(String date) throws ParseException {
    SimpleDateFormat parseFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
    parseFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return parseFormat.parse(date);
  }

  public static ServiceDate serviceDateFromDate(Date date) {
    Calendar c = GregorianCalendar.getInstance(_agencyTimeZone);
    c.setTime(date);
    return new ServiceDate(c);
  }

  public static String apiDateStringForServiceDate(ServiceDate serviceDate) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    return dateFormat.format(serviceDate.getAsDate(_agencyTimeZone));
  }
}
