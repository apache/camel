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
package org.apache.camel.component.weather.geolocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.weather.WeatherComponent;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;

public class FreeGeoIpGeoLocationProvider implements GeoLocationProvider {

    private final WeatherComponent component;

    public FreeGeoIpGeoLocationProvider(WeatherComponent component, String accessKey) {
        this.component = component;
    }

    @Override
    public GeoLocation getCurrentGeoLocation() throws Exception {
        HttpClient httpClient = component.getHttpClient();
        if (isEmpty(component.getGeolocationAccessKey())) {
            throw new IllegalStateException("The geolocation service requires a mandatory geolocationAccessKey");
        }
        if (isEmpty(component.getGeolocationRequestHostIP())) {
            throw new IllegalStateException("The geolocation service requires a mandatory geolocationRequestHostIP");
        }

        String url = String.format("http://api.ipstack.com/%s?access_key=%s&legacy=1&output=json", component.getGeolocationRequestHostIP(), component.getGeolocationAccessKey());
        HttpGet getMethod = new HttpGet(url);
        try {
            HttpResponse response = httpClient.execute(getMethod);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new IllegalStateException("Got the unexpected http-status '" + response.getStatusLine().getStatusCode() + "' for the geolocation");
            }
            String geoLocation = EntityUtils.toString(response.getEntity(), "UTF-8");
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
