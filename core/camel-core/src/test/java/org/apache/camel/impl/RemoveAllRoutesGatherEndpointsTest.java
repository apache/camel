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

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.LifecycleStrategySupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * removeAllRoutes() gathers the endpoints in use by the routes once, instead of once for each removed route, and still
 * keeps the endpoints that a route added in the meantime uses.
 */
public class RemoveAllRoutesGatherEndpointsTest extends ContextTestSupport {

    private final CountingEndpointAware countingA = new CountingEndpointAware();
    private final CountingEndpointAware countingB = new CountingEndpointAware();
    private final CountingEndpointAware countingC = new CountingEndpointAware();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // ignore what starting the context did
        countingA.calls.set(0);
        countingB.calls.set(0);
        countingC.calls.set(0);
    }

    @Test
    public void testRemoveAllRoutesGathersTheEndpointsOfEachRouteOnce() throws Exception {
        context.getRouteController().removeAllRoutes();

        assertEquals(0, context.getRoutes().size());
        assertEquals(1, countingA.calls.get(), "endpoints of route a gathered");
        assertEquals(1, countingB.calls.get(), "endpoints of route b gathered");
        assertEquals(1, countingC.calls.get(), "endpoints of route c gathered");
    }

    @Test
    public void testRemoveAllRoutesRemovesSharedAndPrivateEndpoints() throws Exception {
        context.getRouteController().removeAllRoutes();

        assertNull(context.hasEndpoint("mock://shared"));
        assertNull(context.hasEndpoint("mock://c"));
        assertNull(context.hasEndpoint("direct://a"));
    }

    @Test
    public void testRemoveAllRoutesKeepsTheEndpointsOfARouteAddedMeanwhile() throws Exception {
        // while route a is removed, add a route that uses the endpoint shared by routes a and b
        context.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onRoutesRemove(Collection<Route> routes) {
                if (routes.stream().anyMatch(route -> "a".equals(route.getRouteId()))) {
                    try {
                        context.addRoutes(new RouteBuilder() {
                            @Override
                            public void configure() {
                                from("direct:late").routeId("late").to("mock:shared");
                            }
                        });
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        });

        context.getRouteController().removeAllRoutes();

        assertNotNull(context.getRoute("late"));
        assertNotNull(context.hasEndpoint("mock://shared"), "endpoint of the added route removed");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").process(countingA).to("mock:shared");
                from("direct:b").routeId("b").process(countingB).to("mock:shared");
                from("direct:c").routeId("c").process(countingC).to("mock:c");
            }
        };
    }

    private static final class CountingEndpointAware implements Processor, EndpointAware, Service {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Endpoint getEndpoint() {
            calls.incrementAndGet();
            return null;
        }

        @Override
        public void process(Exchange exchange) {
            // noop
        }

        @Override
        public void start() {
            // noop
        }

        @Override
        public void stop() {
            // noop
        }
    }
}
