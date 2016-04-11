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

import org.apache.camel.spi.Metadata;
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

    @UriPath(description = "The name value is not used.") @Metadata(required = "true")
    private String name;
    @UriParam @Metadata(required = "true")
    private String appid;
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

    /**
     * If null, the current weather will be returned, else use values of 5, 7, 14 days.
     * Only the numeric value for the forecast period is actually parsed, so spelling, capitalisation of the time period is up to you (its ignored)
     */
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

    /**
     * The output format of the weather data.
     */
    public void setMode(WeatherMode mode) {
        this.mode = notNull(mode, "mode");
    }

    public WeatherUnits getUnits() {
        return units;
    }

    /**
     * The units for temperature measurement.
     */
    public void setUnits(WeatherUnits units) {
        this.units = notNull(units, "units");
    }

    public String getLocation() {
        return location;
    }

    /**
     * If null Camel will try and determine your current location using the geolocation of your ip address,
     * else specify the city,country. For well known city names, Open Weather Map will determine the best fit,
     * but multiple results may be returned. Hence specifying and country as well will return more accurate data.
     * If you specify "current" as the location then the component will try to get the current latitude and longitude
     * and use that to get the weather details. You can use lat and lon options instead of location.
     */
    public void setLocation(String location) {
        this.location = location;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * To store the weather result in this header instead of the message body. This is useable if you want to keep current message body as-is.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getLat() {
        return lat;
    }

    /**
     * Latitude of location. You can use lat and lon options instead of location.
     */
    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    /**
     * Longitude of location. You can use lat and lon options instead of location.
     */
    public void setLon(String lon) {
        this.lon = lon;
    }
    
    /**
     * APPID ID used to authenticate the user connected to the API Server
     */
    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getAppid() {
        return appid;
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

        if (getAppid() != null) {
            answer += "&APPID=" + getAppid();
        }
        
        return answer;
    }

    private String getCurrentGeoLocation() throws Exception {
        String geoLocation = component.getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, new URL("http://freegeoip.io/json/"));
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
