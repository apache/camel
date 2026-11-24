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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Removing a route walks the endpoints of every route in the context. That must only happen when a route is actually
 * removed, not when the route id is unknown or the route is still started, as when routes with explicit ids are added.
 */
public class RouteRemoveGatherEndpointsTest extends ContextTestSupport {

    private final CountingEndpointAware counting = new CountingEndpointAware();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // ignore what starting the context did
        counting.calls.set(0);
    }

    @Test
    public void testRemoveUnknownOrStartedRouteDoesNotGatherEndpoints() throws Exception {
        assertFalse(context.removeRoute("unknown"));
        assertEquals(0, counting.calls.get(), "endpoints gathered for an unknown route id");

        assertFalse(context.removeRoute("foo"));
        assertEquals(0, counting.calls.get(), "endpoints gathered for a started route");

        context.getRouteController().stopRoute("foo");
        assertTrue(context.removeRoute("foo"));
        assertTrue(counting.calls.get() > 0, "endpoints not gathered when the route was removed");
        assertEquals(0, context.getRoutes().size());
    }

    @Test
    public void testAddRouteWithIdDoesNotGatherEndpointsOfOtherRoutes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:bar").routeId("bar").to("mock:bar");
            }
        });

        assertEquals(2, context.getRoutes().size());
        assertEquals(0, counting.calls.get(), "endpoints of the existing route gathered when adding a route");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").process(counting).to("mock:foo");
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
