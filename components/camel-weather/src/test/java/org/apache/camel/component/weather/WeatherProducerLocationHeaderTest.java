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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.weather.geolocation.GeoLocation;
import org.apache.camel.component.weather.geolocation.GeoLocationProvider;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The geolocation of a query for the current location is not kept on the shared configuration of the endpoint, so a
 * later CamelWeatherLocation header is the location that is queried.
 */
class WeatherProducerLocationHeaderTest extends CamelTestSupport {

    private static final String CURRENT = "lat=2&lon=1";

    private final List<String> requested = new ArrayList<>();
    private int status = HttpStatus.SC_OK;

    @BindToRegistry("geo")
    private final GeoLocationProvider geoLocationProvider = () -> new GeoLocation("1", "2");

    @BindToRegistry("http")
    private final CloseableHttpClient httpClient = createHttpClient();

    @Test
    void headerLocationIsQueriedOnAnEndpointWithoutLocation() {
        String uri = "weather:foo?appid=x&geoLocationProvider=#geo&httpClient=#http";

        template.requestBodyAndHeader(uri, null, WeatherConstants.WEATHER_LOCATION, "Paris");
        template.requestBody(uri, (Object) null);

        assertEquals(List.of("q=Paris", CURRENT), requestedLocations());
        assertNoLatLonOnTheConfiguration(uri);
    }

    @Test
    void currentLocationIsNotUsedByTheNextQuery() {
        String uri = "weather:foo?appid=x&location=Madrid&geoLocationProvider=#geo&httpClient=#http";

        template.requestBodyAndHeader(uri, null, WeatherConstants.WEATHER_LOCATION, "current");
        template.requestBodyAndHeader(uri, null, WeatherConstants.WEATHER_LOCATION, "Paris");
        template.requestBody(uri, (Object) null);

        assertEquals(List.of(CURRENT, "q=Paris", "q=Madrid"), requestedLocations());
        assertNoLatLonOnTheConfiguration(uri);
    }

    @Test
    void errorNamesTheUriThatWasCalled() {
        String uri = "weather:foo?appid=x&location=Madrid&geoLocationProvider=#geo&httpClient=#http";
        status = HttpStatus.SC_INTERNAL_SERVER_ERROR;

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader(uri, null, WeatherConstants.WEATHER_LOCATION, "Paris"));

        IllegalStateException cause = assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(cause.getMessage().contains("?q=Paris&"), cause.getMessage());
    }

    private List<String> requestedLocations() {
        // the location part of each requested URI, such as q=Paris or lat=2&lon=1
        return requested.stream().map(u -> u.substring(u.indexOf('?') + 1, u.indexOf("&lang="))).toList();
    }

    private void assertNoLatLonOnTheConfiguration(String uri) {
        WeatherConfiguration configuration = context.getEndpoint(uri, WeatherEndpoint.class).getConfiguration();
        assertNull(configuration.getLat());
        assertNull(configuration.getLon());
    }

    private CloseableHttpClient createHttpClient() {
        try {
            CloseableHttpClient client = mock(CloseableHttpClient.class);
            when(client.execute(any(HttpGet.class), any(HttpClientResponseHandler.class))).thenAnswer(invocation -> {
                HttpGet request = invocation.getArgument(0);
                requested.add(request.getRequestUri());
                BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
                response.setEntity(new StringEntity("{}"));
                HttpClientResponseHandler<?> handler = invocation.getArgument(1);
                return handler.handleResponse(response);
            });
            return client;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
