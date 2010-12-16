/**
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
import org.apache.camel.Endpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;

public class FromEndpointRouteDefinitionTest extends ContextTestSupport {
    private MockEndpoint results;
    private Object expectedBody = "<hello>world!</hello>";

    public void testReceivedMessageHasFromEndpointSet() throws Exception {
        RouteDefinition route = new RouteDefinition();
        //build a route
        route.from("direct:start");
        route.to("mock:results");

        context.startRoute(route);

        results = getMockEndpoint("mock:results");
        results.expectedBodiesReceived(expectedBody);

        template.sendBody("direct:start", expectedBody);

        results.assertIsSatisfied();

        Endpoint fromEndpoint = route.getInputs().get(0).getEndpoint();
        assertNotNull("exchange.fromEndpoint() is null!", fromEndpoint);
        assertEquals("fromEndpoint URI", "direct://start", fromEndpoint.getEndpointUri());
    }
}

