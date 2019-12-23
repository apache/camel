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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class RouteIdAutoAssignClashIssue extends ContextTestSupport {

    public void testRouteIdAutoAssignClash() throws Exception {
        getMockEndpoint("mock:start1").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:start2").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:start").expectedBodiesReceived("Hi Camel");

        template.sendBody("direct:start1", "Hello World");
        template.sendBody("direct:start2", "Bye World");
        template.sendBody("direct:start", "Hi Camel");

        assertMockEndpointsSatisfied();

        assertEquals(3, context.getRouteDefinitions().size());
        assertEquals(3, context.getRoutes().size());

        assertNotNull(context.getRoute("route1"));
        assertNotNull(context.getRoute("route2"));
        // we will auto assign the route as route3
        assertNotNull(context.getRoute("route3"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use route1, route2 to clash with Camel's auto assigning
                from("direct:start1").routeId("route1").to("mock:start1");

                // use route1, route2 to clash with Camel's auto assigning
                from("direct:start2").routeId("route2").to("mock:start2");

                // no assigned route id which should be auto assigned
                from("direct:start").to("mock:start");
            }
        };
    }
}
