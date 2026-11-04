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
package org.apache.camel.component.jetty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.camel.Consumer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * When stopping the last consumer on a port fails (here Jetty fails to stop its selectors on an interrupted thread, as
 * when a route shutdown times out), the connector is still removed, so a new route on the same port gets a new server
 * that accepts connections.
 */
class JettyConsumerStopFailedTest extends BaseJettyTest {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void newRouteOnSamePortAnswersAfterFailedStop() throws Exception {
        assertEquals("first", get("first"));

        Consumer consumer = context.getRoute("first").getConsumer();
        Thread.currentThread().interrupt();
        try {
            consumer.stop();
        } catch (Exception e) {
            // stopping may fail on an interrupted thread, which is what this test is about
        } finally {
            Thread.interrupted();
        }
        assertFalse(JettyHttpComponent.CONNECTORS.containsKey("http:localhost:" + getPort()),
                "the connector of the stopped consumer is still registered");

        context.getRouteController().stopRoute("first");
        context.removeRoute("first");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty://http://localhost:{{port}}/second").routeId("second").setBody(constant("second"));
            }
        });

        assertEquals("second", get("second"));
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + getPort() + "/" + path))
                .timeout(Duration.ofSeconds(5)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty://http://localhost:{{port}}/first").routeId("first").setBody(constant("first"));
            }
        };
    }
}
