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

import java.util.Set;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventWildcardTest extends CamelTestSupport {

    @Test
    void testRouteWildcard() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:routeWildcard");
        mock.expectedMinimumMessageCount(2);

        // Add and stop a dynamic route to trigger RouteStarted + RouteStopped
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:wildcardDynamic").routeId("wildcardDynamic").to("mock:wildcardDynamic");
            }
        });
        context.getRouteController().stopRoute("wildcardDynamic");

        mock.assertIsSatisfied();

        // All events should be route-related
        mock.getExchanges().forEach(e -> {
            String eventType = e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_TYPE, String.class);
            assertTrue(eventType.startsWith("Route"), "Expected route event but got: " + eventType);
        });
    }

    @Test
    void testExchangeWildcard() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exchangeWildcard");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:wildcardExchange", "Hello");

        mock.assertIsSatisfied();

        mock.getExchanges().forEach(e -> {
            String eventType = e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_TYPE, String.class);
            assertTrue(eventType.startsWith("Exchange"), "Expected exchange event but got: " + eventType);
        });
    }

    @Test
    void testAllWildcard() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:allWildcard");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:wildcardExchange", "Hello");

        mock.assertIsSatisfied();
        // Should receive events of various types
        assertTrue(mock.getExchanges().size() > 0);
    }

    @Test
    void testResolveWildcardRouteTypes() {
        Set<CamelEvent.Type> types = CamelEventEndpoint.resolveWildcard("Route*");
        assertTrue(types.contains(CamelEvent.Type.RouteStarted));
        assertTrue(types.contains(CamelEvent.Type.RouteStopped));
        assertTrue(types.contains(CamelEvent.Type.RouteAdded));
        assertTrue(types.contains(CamelEvent.Type.RouteRemoved));
        assertTrue(types.contains(CamelEvent.Type.RouteReloaded));
        assertTrue(types.contains(CamelEvent.Type.RouteRestarting));
        assertTrue(types.contains(CamelEvent.Type.RouteRestartingFailure));
        // Routes* events should also match
        assertTrue(types.contains(CamelEvent.Type.RoutesStarted));
        assertTrue(types.contains(CamelEvent.Type.RoutesStopped));
    }

    @Test
    void testResolveWildcardExchangeTypes() {
        Set<CamelEvent.Type> types = CamelEventEndpoint.resolveWildcard("Exchange*");
        assertTrue(types.contains(CamelEvent.Type.ExchangeCreated));
        assertTrue(types.contains(CamelEvent.Type.ExchangeCompleted));
        assertTrue(types.contains(CamelEvent.Type.ExchangeFailed));
        assertTrue(types.contains(CamelEvent.Type.ExchangeSent));
        assertTrue(types.contains(CamelEvent.Type.ExchangeSending));
    }

    @Test
    void testResolveWildcardCamelContextTypes() {
        Set<CamelEvent.Type> types = CamelEventEndpoint.resolveWildcard("CamelContext*");
        assertTrue(types.contains(CamelEvent.Type.CamelContextStarted));
        assertTrue(types.contains(CamelEvent.Type.CamelContextStopped));
        assertTrue(types.contains(CamelEvent.Type.CamelContextInitialized));
        // Should not contain exchange or route types
        assertTrue(types.stream().allMatch(t -> t.name().startsWith("CamelContext")));
    }

    @Test
    void testResolveWildcardStepTypes() {
        Set<CamelEvent.Type> types = CamelEventEndpoint.resolveWildcard("Step*");
        assertEquals(3, types.size());
        assertTrue(types.contains(CamelEvent.Type.StepStarted));
        assertTrue(types.contains(CamelEvent.Type.StepCompleted));
        assertTrue(types.contains(CamelEvent.Type.StepFailed));
    }

    @Test
    void testResolveWildcardServiceTypes() {
        Set<CamelEvent.Type> types = CamelEventEndpoint.resolveWildcard("Service*");
        assertEquals(2, types.size());
        assertTrue(types.contains(CamelEvent.Type.ServiceStartupFailure));
        assertTrue(types.contains(CamelEvent.Type.ServiceStopFailure));
    }

    @Test
    void testResolveWildcardAll() {
        Set<CamelEvent.Type> types = CamelEventEndpoint.resolveWildcard("*");
        assertEquals(CamelEvent.Type.values().length, types.size());
    }

    @Test
    void testInvalidWildcard() {
        assertThrows(IllegalArgumentException.class, () -> CamelEventEndpoint.resolveWildcard("Invalid*"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:Route*")
                        .routeId("routeWildcardConsumer")
                        .to("mock:routeWildcard");

                from("event:Exchange*")
                        .routeId("exchangeWildcardConsumer")
                        .to("mock:exchangeWildcard");

                from("event:*")
                        .routeId("allWildcardConsumer")
                        .to("mock:allWildcard");

                from("direct:wildcardExchange").routeId("wildcardExchangeRoute")
                        .log("Processing wildcard exchange");
            }
        };
    }
}
