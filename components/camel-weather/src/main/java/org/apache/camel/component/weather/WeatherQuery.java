/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.weather;

import org.apache.camel.component.weather.geolocation.GeoLocation;

import static org.apache.camel.component.weather.WeatherMode.JSON;
import static org.apache.camel.util.ObjectHelper.isEmpty;

/**
 * Logic for determining the query based on the provided configuration.
 */
public class WeatherQuery {
    private final WeatherConfiguration weatherConfiguration;

    public WeatherQuery(WeatherConfiguration weatherConfiguration) {
        this.weatherConfiguration = weatherConfiguration;
    }

    public String getQuery() throws Exception {
        return getQuery(weatherConfiguration.getLocation());
    }

    public String getQuery(String location) throws Exception {
        String answer = "http://api.openweathermap.org/data/2.5/";
        // the lat and lon of the current geolocation are for this query only: the configuration is shared by all the
        // queries of the endpoint, and a later query can be for another location
        String lat = weatherConfiguration.getLat();
        String lon = weatherConfiguration.getLon();
        if (lat != null && lon != null
                && weatherConfiguration.getRightLon() == null && weatherConfiguration.getTopLat() == null) {
            location = createLatLonQueryString(lat, lon);
        } else if (lat != null && lon != null
                && weatherConfiguration.getRightLon() != null && weatherConfiguration.getTopLat() != null) {
            location = "bbox=" + lon + ","
                       + lat + ","
                       + weatherConfiguration.getRightLon() + ","
                       + weatherConfiguration.getTopLat() + ","
                       + weatherConfiguration.getZoom() + "&cluster=yes";
        } else if (!isEmpty(weatherConfiguration.getZip())) {
            location = "zip=" + weatherConfiguration.getZip();
        } else if (weatherConfiguration.getIds() != null && !weatherConfiguration.getIds().isEmpty()) {
            location = "id=" + weatherConfiguration.getIds();
        } else if (isEmpty(location) || "current".equals(location)) {
            GeoLocation geoLocation = getCurrentGeoLocation();
            lat = geoLocation.getLatitude();
            lon = geoLocation.getLongitude();
            location = createLatLonQueryString(lat, lon);
        } else {
            // assuming the location is a town or country
            location = "q=" + location;
        }

        location = location + "&lang=" + weatherConfiguration.getLanguage();

        String context = createContext(lat, lon);
        answer += context + location;

        if (!isEmpty(weatherConfiguration.getPeriod())) {
            answer += "&cnt=" + weatherConfiguration.getPeriod();
        } else if (weatherConfiguration.getCnt() != null) {
            answer += "&cnt=" + weatherConfiguration.getCnt();
        }

        // append the desired measurement unit if not the default (which is metric)
        if (weatherConfiguration.getUnits() != null) {
            answer += "&units=" + weatherConfiguration.getUnits().name().toLowerCase();
        }

        // append the desired output mode if not the default (which is json)
        if (weatherConfiguration.getMode() != JSON) {
            answer += "&mode=" + weatherConfiguration.getMode().name().toLowerCase();
        }

        if (weatherConfiguration.getAppid() != null) {
            answer += "&APPID=" + weatherConfiguration.getAppid();
        }

        return answer;
    }

    private String createContext(String lat, String lon) {
        String answer;
        if (isBoxedQuery()) {
            if (weatherConfiguration.getWeatherApi() == WeatherApi.Station) {
                answer = "box/station?";
            } else {
                answer = "box/city?";
            }
        } else if (lat != null && lon != null && weatherConfiguration.getCnt() != null) {
            if (weatherConfiguration.getWeatherApi() == WeatherApi.Station) {
                answer = "station/find?";
            } else {
                answer = "find?";
            }
        } else if (weatherConfiguration.getIds() != null && !weatherConfiguration.getIds().isEmpty()) {
            if (weatherConfiguration.getIdsAsList().size() == 1) {
                if (!isEmpty(weatherConfiguration.getPeriod())) {
                    if (weatherConfiguration.getWeatherApi() == WeatherApi.Hourly) {
                        answer = "forecast?";
                    } else {
                        answer = "forecast/daily?";
                    }
                } else if (weatherConfiguration.getWeatherApi() == WeatherApi.Station) {
                    answer = "station?";
                } else {
                    answer = "weather?";
                }
            } else {
                answer = "group?";
            }
        } else if (isEmpty(weatherConfiguration.getPeriod())) {
            answer = "weather?";
        } else {
            if (weatherConfiguration.getWeatherApi() == WeatherApi.Hourly) {
                answer = "forecast?";
            } else {
                answer = "forecast/daily?";
            }
        }
        return answer;
    }

    private static String createLatLonQueryString(String lat, String lon) {
        return "lat=" + lat + "&lon=" + lon;
    }

    private boolean isBoxedQuery() {
        return weatherConfiguration.getTopLat() != null && weatherConfiguration.getRightLon() != null;
    }

    GeoLocation getCurrentGeoLocation() throws Exception {
        return weatherConfiguration.getGeoLocationProvider().getCurrentGeoLocation();
    }

}
