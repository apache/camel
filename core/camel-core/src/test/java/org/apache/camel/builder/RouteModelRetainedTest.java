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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.model.RouteDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that {@link Route#getRoute()} and {@link Route#getErrorHandlerFactory()} are retained at runtime for routes
 * created at startup (CAMEL-25631).
 */
public class RouteModelRetainedTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:startup")
                        .routeId("startup-route")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testStartupRouteModelRetained() {
        Route route = context.getRoute("startup-route");
        assertNotNull(route, "Route should exist");
        assertInstanceOf(RouteDefinition.class, route.getRoute(),
                "getRoute() should return a RouteDefinition for a startup route");
        assertNotNull(route.getErrorHandlerFactory(),
                "getErrorHandlerFactory() should not be null for a startup route");
    }

    @Test
    public void testRuntimeAddedRouteModelRetained() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:runtime")
                        .routeId("runtime-route")
                        .to("mock:result");
            }
        });

        Route route = context.getRoute("runtime-route");
        assertNotNull(route, "Route should exist");
        assertInstanceOf(RouteDefinition.class, route.getRoute(),
                "getRoute() should return a RouteDefinition for a runtime-added route");
        assertNotNull(route.getErrorHandlerFactory(),
                "getErrorHandlerFactory() should not be null for a runtime-added route");
    }
}
