/**
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

import java.net.URL;
import java.util.Scanner;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import static org.apache.camel.component.weather.WeatherMode.JSON;
import static org.apache.camel.component.weather.WeatherUnits.METRIC;
import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;

@UriParams
public class WeatherConfiguration {

    private final WeatherComponent component;

    @UriPath(description = "The name value is not used.")
    private String name;
    @UriParam
    private String location = "";
    @UriParam
    private String lat;
    @UriParam
    private String lon;
    @UriParam
    private String period = "";
    @UriParam(defaultValue = "JSON")
    private WeatherMode mode = JSON;
    @UriParam(defaultValue = "METRIC")
    private WeatherUnits units = METRIC;
    @UriParam
    private String headerName;

    public WeatherConfiguration(WeatherComponent component) {
        this.component = notNull(component, "component");
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        notNull(period, "period");
        int result = 0;
        try {
            result = new Scanner(period).useDelimiter("\\D+").nextInt();
        } catch (Exception e) {
            // ignore and fallback the period to be an empty string
        }
        if (result != 0) {
            this.period = "" + result;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WeatherMode getMode() {
        return mode;
    }

    public void setMode(WeatherMode mode) {
        this.mode = notNull(mode, "mode");
    }

    public WeatherUnits getUnits() {
        return units;
    }

    public void setUnits(WeatherUnits units) {
        this.units = notNull(units, "units");
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
    }

    public String getQuery() throws Exception {
        return getQuery(getLocation());
    }

    public String getQuery(String location) throws Exception {
        String answer = "http://api.openweathermap.org/data/2.5/";

        if (lat != null && lon != null) {
            location = "lat=" + lat + "&lon=" + lon;
        } else if (isEmpty(location) || "current".equals(location)) {
            location = getCurrentGeoLocation();
        } else {
            // assuming the location is a town or country
            location = "q=" + location;
        }

        if (isEmpty(getPeriod())) {
            answer += "weather?" + location;
        } else {
            answer += "forecast/daily?" + location + "&cnt=" + getPeriod();
        }

        // append the desired measurement unit if not the default (which is metric)
        if (getUnits() != METRIC) {
            answer += "&units=" + getUnits().name().toLowerCase();
        }

        // append the desired output mode if not the default (which is json)
        if (getMode() != JSON) {
            answer += "&mode=" + getMode().name().toLowerCase();
        }

        return answer;
    }

    private String getCurrentGeoLocation() throws Exception {
        String geoLocation = component.getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, new URL("http://freegeoip.net/json/"));
        if (isEmpty(geoLocation)) {
            throw new IllegalStateException("Got the unexpected value '" + geoLocation + "' for the geolocation");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readValue(geoLocation, JsonNode.class);
        JsonNode latitudeNode = notNull(node.get("latitude"), "latitude");
        JsonNode longitudeNode = notNull(node.get("longitude"), "longitude");

        return "lat=" + latitudeNode + "&lon=" + longitudeNode;
    }
}
