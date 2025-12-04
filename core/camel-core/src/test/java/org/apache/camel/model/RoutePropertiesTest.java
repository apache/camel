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

package org.apache.camel.model;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RoutePropertiesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRouteProperties() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("route-id")
                        .routeProperty("key1", "val1")
                        .routeProperty("key2", "val2")
                        .to("mock:output");
            }
        });

        context.start();

        RouteDefinition definition = context.getRouteDefinition("route-id");
        Route route = context.getRoute("route-id");

        assertNotNull(definition.getRouteProperties());
        assertEquals(2, definition.getRouteProperties().size());

        assertNotNull(route.getProperties());
        assertEquals("val1", route.getProperties().get("key1"));
        assertEquals("val2", route.getProperties().get("key2"));
    }

    @DisplayName("Checks that trying to use a reserved property leads to failure")
    @Test
    public void testRoutePropertiesFailure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("route-id")
                        .routeProperty(Route.ID_PROPERTY, "the id")
                        .to("mock:output");
            }
        });

        Assertions.assertThrows(
                FailedToCreateRouteException.class,
                () -> context.start(),
                "Should have prevented setting a property with a reserved name");
    }
}
