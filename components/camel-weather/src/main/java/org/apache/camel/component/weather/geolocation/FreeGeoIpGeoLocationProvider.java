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
package org.apache.camel.component.weather.geolocation;

import org.apache.camel.component.weather.WeatherComponent;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;

public class FreeGeoIpGeoLocationProvider implements GeoLocationProvider {

    private final WeatherComponent component;

    public FreeGeoIpGeoLocationProvider(WeatherComponent component) {
        this.component = component;
    }

    @Override
    public GeoLocation getCurrentGeoLocation() throws Exception {
        HttpClient httpClient = component.getHttpClient();
        GetMethod getMethod = new GetMethod("https://freegeoip.io/json/");
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

            return new GeoLocation(longitudeNode.asText(), latitudeNode.asText());
        } finally {
            getMethod.releaseConnection();
        }

    }

}
