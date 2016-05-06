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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import static org.apache.camel.component.weather.WeatherMode.JSON;
import static org.apache.camel.component.weather.WeatherUnits.METRIC;
import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Logic for determining the query based on the provided
 * configuration.
 */
public class WeatherQuery {
    private final WeatherConfiguration weatherConfiguration;
    private final WeatherComponent component;

    public WeatherQuery(WeatherComponent component, WeatherConfiguration weatherConfiguration) {
        this.component = component;
        this.weatherConfiguration = weatherConfiguration;
    }

    public String getQuery() throws Exception {
        return getQuery(weatherConfiguration.getLocation());
    }

    public String getQuery(String location) throws Exception {
        String answer = "http://api.openweathermap.org/data/2.5/";

        if (weatherConfiguration.getLat() != null && weatherConfiguration.getLon() != null
                && weatherConfiguration.getRightLon() == null && weatherConfiguration.getTopLat() == null) {
            location = "lat=" + weatherConfiguration.getLat() + "&lon=" + weatherConfiguration.getLon();
        } else if (weatherConfiguration.getLat() != null && weatherConfiguration.getLon() != null
                && weatherConfiguration.getRightLon() != null && weatherConfiguration.getTopLat() != null) {
            location = "bbox=" + weatherConfiguration.getLon() + ","
                    + weatherConfiguration.getLat() + ","
                    + weatherConfiguration.getRightLon() + ","
                    + weatherConfiguration.getTopLat() + ","
                    + weatherConfiguration.getZoom() + "&cluster=yes";
        } else if (isEmpty(location) || "current".equals(location)) {
            location = getCurrentGeoLocation();
        } else {
            // assuming the location is a town or country
            location = "q=" + location;
        }

        location = location + "&lang=" + weatherConfiguration.getLanguage();

        if (weatherConfiguration.getTopLat() != null && weatherConfiguration.getRightLon() != null) {
            answer += "box?" + location;
        } else if (isEmpty(weatherConfiguration.getPeriod())) {
            answer += "weather?" + location;
        } else {
            answer += "forecast/daily?" + location + "&cnt=" + weatherConfiguration.getPeriod();
        }

        // append the desired measurement unit if not the default (which is metric)
        if (weatherConfiguration.getUnits() != METRIC) {
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

    /**
     * TODO: shouldn't this method be moved to a class of its own perhaps with an interface
     * that gets injected. For testing purposes you can inject your own version when testing this
     * class.
     */

    String getCurrentGeoLocation() throws Exception {
        HttpClient httpClient = new HttpClient();
        GetMethod getMethod = new GetMethod("http://freegeoip.io/json/");
        try {
            int statusCode = httpClient.executeMethod(getMethod);
            if (statusCode != HttpStatus.SC_OK) {
                throw new IllegalStateException("Got the unexpected http-status '" + getMethod.getStatusLine() + "' for the geolocation");
            }
            String geoLocation = component.getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, getMethod.getResponseBodyAsStream());
            if (isEmpty(geoLocation)) {
                throw new IllegalStateException("Got the unexpected value '" + geoLocation + "' for the geolocation");
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readValue(geoLocation, JsonNode.class);
            JsonNode latitudeNode = notNull(node.get("latitude"), "latitude");
            JsonNode longitudeNode = notNull(node.get("longitude"), "longitude");

            return "lat=" + latitudeNode + "&lon=" + longitudeNode;
        } finally {
            getMethod.releaseConnection();
        }
    }

}
