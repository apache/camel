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

import org.apache.camel.component.weather.WeatherConfiguration;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreeGeoIpGeoLocationProviderTest {

    @Test
    @SuppressWarnings("unchecked")
    void readsNumericCoordinatesAsText() throws Exception {
        ClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.setEntity(new StringEntity("{\"latitude\":51.5,\"longitude\":-0.12}"));
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpGet.class), any(HttpClientResponseHandler.class))).thenAnswer(
                invocation -> invocation.getArgument(1, HttpClientResponseHandler.class).handleResponse(response));

        WeatherConfiguration configuration = new WeatherConfiguration();
        configuration.setHttpClient(client);
        configuration.setGeolocationAccessKey("key");
        configuration.setGeolocationRequestHostIP("127.0.0.1");

        GeoLocation location = new FreeGeoIpGeoLocationProvider(configuration).getCurrentGeoLocation();

        assertEquals("51.5", location.getLatitude());
        assertEquals("-0.12", location.getLongitude());
    }
}
