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

import java.util.Properties;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25771: each route has a checksum of the route as written, the same in every run, and a revision that counts the
 * times it came back changed in this run, so tools can tell which routes changed.
 */
public class RouteChecksumTest {

    private static RouteBuilder orders(String target) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .split(body()).parallelProcessing()
                        .to("log:line")
                        .end()
                        .to(target);
            }
        };
    }

    private static String checksum(String routeId, RoutesBuilder... builders) throws Exception {
        return checksum(routeId, null, builders);
    }

    private static String checksum(String routeId, Consumer<CamelContext> setup, RoutesBuilder... builders)
            throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            if (setup != null) {
                setup.accept(context);
            }
            for (RoutesBuilder builder : builders) {
                context.addRoutes(builder);
            }
            context.start();
            Route route = context.getRoute(routeId);
            assertNotNull(route, routeId);
            return route.getChecksum();
        }
    }

    @Test
    public void testSameRouteSameChecksumInEveryRun() throws Exception {
        String first = checksum("orders", orders("mock:out"));
        String second = checksum("orders", orders("mock:out"));

        assertNotNull(first);
        assertTrue(first.matches("[0-9a-f]{12}"), first);
        assertEquals(first, second);
    }

    @Test
    public void testEditedRouteNewChecksum() throws Exception {
        String before = checksum("orders", orders("mock:out"));

        assertNotEquals(before, checksum("orders", orders("mock:other")));
        // an option of an EIP is part of the route too
        String sequential = checksum("orders", new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .split(body())
                        .to("log:line")
                        .end()
                        .to("mock:out");
            }
        });
        assertNotEquals(before, sequential);
    }

    @Test
    public void testGeneratedIdsAndOtherRoutesLeftOut() throws Exception {
        String alone = checksum("orders", orders("mock:out"));
        // another route first: the ids Camel generates for the steps of orders are not the same
        String after = checksum("orders", new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:audit").to("log:audit").split(body()).to("log:a").end();
            }
        }, orders("mock:out"));

        assertEquals(alone, after);
    }

    @Test
    public void testRouteConfigurationsLeftOut() throws Exception {
        String plain = checksum("orders", orders("mock:out"));
        // the onException of a route configuration is merged into every route, which are not changed by it
        String configured = checksum("orders", new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration().onException(Exception.class).handled(true).to("log:failed");
            }
        }, orders("mock:out"));
        assertEquals(plain, configured);

        // one the route has itself is part of it
        String own = checksum("orders", new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .onException(Exception.class).handled(true).to("log:failed").end()
                        .split(body()).parallelProcessing()
                        .to("log:line")
                        .end()
                        .to("mock:out");
            }
        });
        assertNotEquals(plain, own);
    }

    @Test
    public void testPlaceholdersAsWritten() throws Exception {
        String a = checksum("orders", context -> context.getPropertiesComponent().setInitialProperties(props("mock:a")),
                orders("{{target}}"));
        String b = checksum("orders", context -> context.getPropertiesComponent().setInitialProperties(props("mock:b")),
                orders("{{target}}"));

        // another value is configuration, not another route
        assertEquals(a, b);
    }

    @Test
    public void testSourceLocationsLeftOutAlsoWhileDebugging() throws Exception {
        String plain = checksum("orders", orders("mock:out"));
        String debugging = checksum("orders", context -> {
            context.setSourceLocationEnabled(true);
            context.setDebugging(true);
        }, orders("mock:out"));

        assertEquals(plain, debugging);
    }

    @Test
    public void testRevisionCountsTheChanges() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(orders("mock:out"));
            context.start();
            assertEquals(1, context.getRoute("orders").getRevision());

            // removed and added again as it was: the same revision
            replace(context, orders("mock:out"));
            assertEquals(1, context.getRoute("orders").getRevision());

            // changed: the next revision, which it keeps when it comes back the same
            replace(context, orders("mock:other"));
            assertEquals(2, context.getRoute("orders").getRevision());
            replace(context, orders("mock:other"));
            assertEquals(2, context.getRoute("orders").getRevision());

            // changed back: still a change in this run
            replace(context, orders("mock:out"));
            assertEquals(3, context.getRoute("orders").getRevision());
        }
    }

    private static void replace(CamelContext context, RoutesBuilder builder) throws Exception {
        context.getRouteController().stopRoute("orders");
        context.removeRoute("orders");
        context.addRoutes(builder);
    }

    private static Properties props(String target) {
        Properties answer = new Properties();
        answer.put("target", target);
        return answer;
    }
}
