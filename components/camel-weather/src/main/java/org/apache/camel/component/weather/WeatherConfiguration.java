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
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import static org.apache.camel.component.weather.WeatherUnits.METRIC;
import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;

public class WeatherConfiguration {

    @UriParam
    private String location = "";
    @UriParam
    private String period = "";
    @UriParam
    private WeatherUnits units = METRIC;
    private WeatherComponent component;

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
        } catch (Throwable e) {
            // ignore and fallback the period to be an empty string
        }
        if (result != 0) {
            this.period = "" + result;
        }
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

    public String getQuery() throws Exception {
        String result = "http://api.openweathermap.org/data/2.5/";
        String location = "";
        if (isEmpty(getLocation())) {
            location = getGeoLocation();
        } else {
            // assuming the location is a town or country
            location = "q=" + getLocation();
        }

        if (isEmpty(getPeriod())) {
            result += "weather?" + location;
        } else {
            result += "forecast/daily?" + location + "&cnt=" + getPeriod();
        }
        result += "&units=" + units.name().toLowerCase();

        return result;
    }

    private String getGeoLocation() throws Exception {
        String geoLocation = component.getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, new URL("http://freegeoip.net/json/"));
        if (isEmpty(geoLocation)) {
            throw new IllegalStateException("Retrieved an unexpected value: '" + geoLocation + "' for the geographical location");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readValue(geoLocation, JsonNode.class);
        JsonNode latitudeNode = notNull(node.get("latitude"), "latitude");
        JsonNode longitudeNode = notNull(node.get("longitude"), "longitude");

        return "lat=" + latitudeNode.toString() + "&lon=" + longitudeNode.toString();
    }
}
