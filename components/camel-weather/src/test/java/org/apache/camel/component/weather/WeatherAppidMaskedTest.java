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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The appid is sent in the request, but the CamelWeatherQuery header and the error messages have it masked.
 */
class WeatherAppidMaskedTest extends CamelTestSupport {

    private static final String APPID = "my-api-key";

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private volatile int status = HttpStatus.SC_OK;

    @BindToRegistry("http")
    private final CloseableHttpClient httpClient = createHttpClient();

    @Test
    void producerHeaderHasTheAppidMasked() {
        Exchange out = template.request("weather:foo?location=Madrid&appid=" + APPID + "&httpClient=#http", e -> {
        });

        String query = out.getMessage().getHeader(WeatherConstants.WEATHER_QUERY, String.class);
        assertTrue(query.contains("&APPID=xxxxxx"), query);
        assertFalse(query.contains(APPID), query);
        assertEquals(1, requested.size());
        assertTrue(requested.get(0).contains("&APPID=" + APPID), requested.get(0));
    }

    @Test
    void producerLocationHeaderWithAppidIsMasked() {
        Exchange out = template.request("weather:foo?location=Madrid&appid=" + APPID + "&httpClient=#http",
                e -> e.getMessage().setHeader(WeatherConstants.WEATHER_LOCATION, "Paris,France&appid=other-key"));

        String query = out.getMessage().getHeader(WeatherConstants.WEATHER_QUERY, String.class);
        assertFalse(query.contains(APPID), query);
        assertFalse(query.contains("other-key"), query);
        assertTrue(query.contains("&appid=xxxxxx"), query);
    }

    @Test
    void producerErrorHasTheAppidMasked() {
        status = HttpStatus.SC_UNAUTHORIZED;

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("weather:foo?location=Madrid&appid=" + APPID + "&httpClient=#http",
                        (Object) null));

        IllegalStateException cause = assertInstanceOf(IllegalStateException.class, e.getCause());
        assertFalse(cause.getMessage().contains(APPID), cause.getMessage());
        assertTrue(cause.getMessage().contains("&APPID=xxxxxx"), cause.getMessage());
    }

    @Test
    void consumerHeaderHasTheAppidMasked() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("weather:bar?location=Madrid&appid=" + APPID + "&httpClient=#http&initialDelay=0&delay=60000")
                        .to("mock:result");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        MockEndpoint.assertIsSatisfied(context);

        String query = mock.getReceivedExchanges().get(0).getMessage().getHeader(WeatherConstants.WEATHER_QUERY,
                String.class);
        assertTrue(query.contains("&APPID=xxxxxx"), query);
        assertFalse(query.contains(APPID), query);
        assertTrue(requested.get(0).contains("&APPID=" + APPID), requested.get(0));
    }

    @Test
    void sanitize() {
        assertNull(WeatherQuery.sanitize(null));
        assertEquals("http://h/weather?q=Madrid&lang=en&APPID=xxxxxx",
                WeatherQuery.sanitize("http://h/weather?q=Madrid&lang=en&APPID=" + APPID));
        assertEquals("http://h/weather?q=Madrid&appid=xxxxxx&lang=en&APPID=xxxxxx",
                WeatherQuery.sanitize("http://h/weather?q=Madrid&appid=a$1b&lang=en&APPID=" + APPID));
        assertEquals("http://h/weather?q=Madrid&lang=en", WeatherQuery.sanitize("http://h/weather?q=Madrid&lang=en"));
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
