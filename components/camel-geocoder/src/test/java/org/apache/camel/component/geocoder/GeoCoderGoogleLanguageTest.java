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
package org.apache.camel.component.geocoder;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.maps.GeoApiContext;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Google producer against a local HTTP server, to check the language parameter of the requests.
 */
public class GeoCoderGoogleLanguageTest extends CamelTestSupport {

    private final Map<String, String> queries = new ConcurrentHashMap<>();
    private HttpServer server;

    @Override
    protected void setupResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            queries.put(exchange.getRequestURI().getPath(), query != null ? query : "");
            byte[] bytes = "{\"status\":\"OK\",\"results\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop(0);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        GeoApiContext geoApiContext = new GeoApiContext.Builder()
                .apiKey("AIzaTest")
                .baseUrlOverride("http://127.0.0.1:" + server.getAddress().getPort())
                .disableRetries()
                .build();
        camelContext.getComponent("geocoder", GeoCoderComponent.class).setGeoApiContext(geoApiContext);
        return camelContext;
    }

    @Test
    public void testGeocodeSendsLanguage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(GeoCoderConstants.STATUS, GeocoderStatus.ZERO_RESULTS);

        template.sendBody("direct:address", "Test");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(queries.get("/maps/api/geocode/json")).contains("address=Paris").contains("language=fr");
    }

    @Test
    public void testReverseGeocodeSendsLanguage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(GeoCoderConstants.STATUS, GeocoderStatus.ZERO_RESULTS);

        template.sendBody("direct:latlng", "Test");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(queries.get("/maps/api/geocode/json")).contains("latlng=").contains("language=de");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:address")
                        .to("geocoder:address:Paris?language=fr")
                        .to("mock:result");

                from("direct:latlng")
                        .to("geocoder:latlng:48.85661400,2.35222190?language=de")
                        .to("mock:result");
            }
        };
    }
}
