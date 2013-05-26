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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

import org.apache.camel.spi.UriParam;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

public class WeatherConfiguration {

    @UriParam
    private String location = "";
    @UriParam
    private String period = "";
    @UriParam
    private String units = "metric";


    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        if (period != null) {
            int result = 0;
            try {
                result = new Scanner(period).useDelimiter("\\D+").nextInt();
            } catch (Throwable e) {
            }
            if (result != 0) {
                this.period = "" + result;
            } else {
                this.period = "";
            }
        }
    }

    public String getUnits() {
        return units;
    }


    public void setUnits(String units) {
        if (units != null) {
            units = units.trim();
            if (units.equalsIgnoreCase("IMPERIAL")) {
                this.units = "imperial";
            } else {
                this.units = "metric";
            }
        }
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getQuery() {
        String result = "http://api.openweathermap.org/data/2.5/";
        String location = "";
        if (getLocation() == null || getLocation().isEmpty()) {
            location = getGeoLocation();
        } else {
            //assuming the location is a town,country
            location = "q=" + getLocation();
        }
        if (getPeriod() != null && getPeriod().isEmpty()) {

            result += "weather?" + location;

        } else {
            result += "forecast/daily?" + location + "&cnt=" + getPeriod();
        }
        result += "&units=" + this.units;
        return result;
    }

    private String getGeoLocation() {
        final String LATITUDE = "latitude";
        final String LONGITUDE = "longitude";
        String result = "";

        try {
            String urlStr = "http://freegeoip.net/json/";
            URL url = new URL(urlStr);
            URLConnection urlConnection = url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String inputLine;
            String temp = "";
            while ((inputLine = in.readLine()) != null) {
                temp += inputLine;
            }

            if (temp != null && !temp.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readValue(temp, JsonNode.class);
                JsonNode latitudeNode = node.get(LATITUDE);
                JsonNode longitudeNode = node.get(LONGITUDE);
                if (latitudeNode != null && longitudeNode != null) {
                    result = "lat=" + latitudeNode.toString() + "&lon=" + longitudeNode.toString();
                }
            }
        } catch (Exception e) {
            //this is going to fail if using this offline
        }
        return result;
    }
}
