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
package org.apache.camel.component.camelevent;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EventComponentTest extends CamelTestSupport {

    @Test
    void testRouteEvents() throws Exception {
        // The event consumer route itself triggers RouteStarted events
        // during context startup. We need to add a route dynamically
        // to capture its events.
        MockEndpoint mock = getMockEndpoint("mock:routeEvents");
        mock.expectedMinimumMessageCount(2); // at least RouteStarted + RouteStopped for the dynamic route

        // Add a dynamic route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:dynamicRoute").routeId("dynamicRoute").to("mock:dynamicResult");
            }
        });

        // Stop the dynamic route to trigger RouteStopped
        context.getRouteController().stopRoute("dynamicRoute");

        mock.assertIsSatisfied();

        // Verify event types
        boolean hasStarted = mock.getExchanges().stream()
                .anyMatch(e -> "RouteStarted".equals(e.getIn().getHeader("CamelEventType")));
        boolean hasStopped = mock.getExchanges().stream()
                .anyMatch(e -> "RouteStopped".equals(e.getIn().getHeader("CamelEventType")));
        assertEquals(true, hasStarted, "Should have received RouteStarted event");
        assertEquals(true, hasStopped, "Should have received RouteStopped event");

        // Verify the body is the actual CamelEvent
        CamelEvent event = mock.getExchanges().get(0).getIn().getBody(CamelEvent.class);
        assertNotNull(event);
    }

    @Test
    void testExchangeEvents() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exchangeEvents");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:testExchange", "Hello");

        mock.assertIsSatisfied();

        // Verify event type header
        String eventType = mock.getExchanges().get(0).getIn().getHeader("CamelEventType", String.class);
        assertEquals("ExchangeCompleted", eventType);

        // Verify the body is ExchangeEvent
        CamelEvent event = mock.getExchanges().get(0).getIn().getBody(CamelEvent.class);
        assertInstanceOf(CamelEvent.ExchangeCompletedEvent.class, event);
    }

    @Test
    void testFilterByRouteId() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:filteredEvents");
        mock.expectedMinimumMessageCount(1);

        // Send to the filtered route
        template.sendBody("direct:filteredRoute", "Hello Filtered");

        // Also send to another route to verify filtering
        template.sendBody("direct:testExchange", "Hello Other");

        mock.assertIsSatisfied();

        // All received exchanges should be from filteredRoute
        mock.getExchanges().forEach(e -> {
            CamelEvent event = e.getIn().getBody(CamelEvent.class);
            if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
                assertEquals("filteredRoute", exchangeEvent.getExchange().getFromRouteId());
            }
        });
    }

    @Test
    void testMultipleEventTypes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:multipleEvents");
        mock.expectedMinimumMessageCount(2);

        // Trigger exchange events
        template.sendBody("direct:testExchange", "Hello");

        mock.assertIsSatisfied();

        // Should receive both ExchangeCreated and ExchangeCompleted
        boolean hasCreated = mock.getExchanges().stream()
                .anyMatch(e -> "ExchangeCreated".equals(e.getIn().getHeader("CamelEventType")));
        boolean hasCompleted = mock.getExchanges().stream()
                .anyMatch(e -> "ExchangeCompleted".equals(e.getIn().getHeader("CamelEventType")));
        assertEquals(true, hasCreated, "Should have received ExchangeCreated event");
        assertEquals(true, hasCompleted, "Should have received ExchangeCompleted event");
    }

    @Test
    void testInvalidEventType() {
        assertThrows(IllegalArgumentException.class, () -> EventEndpoint.resolveEventType("InvalidEventType"));
    }

    @Test
    void testConsumerOnly() {
        assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:test").to("event:RouteStarted");
                }
            });
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route to capture route events
                from("event:RouteStarted,RouteStopped")
                        .routeId("routeEventConsumer")
                        .to("mock:routeEvents");

                // Route to capture exchange completed events
                from("event:ExchangeCompleted")
                        .routeId("exchangeEventConsumer")
                        .to("mock:exchangeEvents");

                // Route with filter
                from("event:ExchangeCompleted?filter=filteredRoute")
                        .routeId("filteredEventConsumer")
                        .to("mock:filteredEvents");

                // Route with multiple event types
                from("event:ExchangeCreated,ExchangeCompleted")
                        .routeId("multipleEventConsumer")
                        .to("mock:multipleEvents");

                // Test routes that generate exchange events
                from("direct:testExchange").routeId("testExchangeRoute")
                        .log("Processing test exchange");

                from("direct:filteredRoute").routeId("filteredRoute")
                        .log("Processing filtered exchange");
            }
        };
    }
}
