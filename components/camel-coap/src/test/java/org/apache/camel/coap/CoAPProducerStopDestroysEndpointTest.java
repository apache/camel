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
package org.apache.camel.coap;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each start of a CoAP producer creates a CoapClient with its own CoapEndpoint (a bound UDP socket and its receiver and
 * sender threads). CoapClient.shutdown() does not destroy that endpoint, so the producer must do it when it stops.
 */
class CoAPProducerStopDestroysEndpointTest extends CoAPTestSupport {

    @Test
    void routeRestartsReleaseTheEndpointsOfTheStoppedProducers() throws Exception {
        assertEquals("Hello Camel", template.requestBody("direct:start", "Camel", String.class));
        Set<Thread> before = Thread.getAllStackTraces().keySet();
        for (int i = 0; i < 5; i++) {
            context.getRouteController().stopRoute("producer");
            context.getRouteController().startRoute("producer");
            assertEquals("Hello Camel", template.requestBody("direct:start", "Camel", String.class));
        }
        context.getRouteController().stopRoute("producer");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long receivers = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> !before.contains(t) && t.isAlive() && t.getName().startsWith("UDP-Receiver"))
                    .count();
            assertEquals(0, receivers, "UDP receiver threads of stopped CoAP producers still alive after 5 restarts");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/hello", PORT.getPort()).transform(simple("Hello ${body}"));
                from("direct:start").routeId("producer").toF("coap://localhost:%d/hello", PORT.getPort());
            }
        };
    }
}
