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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * This test stops a route, mutates it then restarts it
 */
public class StartStopAndShutdownRouteTest extends ContextTestSupport {

    @Test
    public void testStartStopAndShutdownRoute() throws Exception {

        // there should still be 2 services on the route
        Route myRoute = context.getRoute("foo");
        int services = myRoute.getServices().size();
        assertTrue(services > 0);

        // stop the route
        context.getRouteController().stopRoute("foo");

        // there should still be the same number of services on the route
        assertEquals(services, myRoute.getServices().size());

        // shutting down the route, by stop and remove
        context.getRouteController().stopRoute("foo");
        context.removeRoute("foo");

        // and now no more services as the route is shutdown
        assertEquals(0, myRoute.getServices().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("mock:foo");
            }
        };

    }

}
