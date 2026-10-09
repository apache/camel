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
package org.apache.camel.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25500: stopping or removing all routes (as a route reload does) is one graceful shutdown, so a route that sends
 * to another over direct completes its inflight exchanges, instead of being cut off when the route it sends to was
 * stopped first.
 */
public class StopAllRoutesDrainTest extends ContextTestSupport {

    private MockEndpoint shipped;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        context.getShutdownStrategy().setTimeout(10);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        shipped = getMockEndpoint("mock:shipped");
        template.sendBody("seda:start", List.of("A", "B", "C", "D"));
        // the split is still sending when the routes are stopped
        await().atMost(5, TimeUnit.SECONDS).until(() -> shipped.getReceivedCounter() >= 1);
    }

    @Test
    public void removeAllRoutesDrainsTheSplit() throws Exception {
        context.getRouteController().removeAllRoutes();

        assertEquals(List.of("A", "B", "C", "D"), shippedBodies());
        assertTrue(context.getRoutes().isEmpty());
    }

    @Test
    public void stopAllRoutesDrainsTheSplit() throws Exception {
        context.getRouteController().stopAllRoutes();

        assertEquals(List.of("A", "B", "C", "D"), shippedBodies());
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("picked"));
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("shipment"));
    }

    private List<Object> shippedBodies() {
        return shipped.getReceivedExchanges().stream().map(e -> e.getMessage().getBody()).toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").routeId("picked")
                        .split(body())
                            .delay(300)
                            .to("direct:shipment")
                        .end();
                from("direct:shipment").routeId("shipment")
                        .to("mock:shipped");
            }
        };
    }
}
