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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.support.OrderedComparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class RoutesConfigurationBuilderIdOrPatternTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRoutesConfigurationOnException() throws Exception {
        List<RoutesBuilder> routes = new ArrayList<>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                        .throwException(new IllegalArgumentException("Foo"));
            }
        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2").routeId("foo2")
                        .routeConfigurationId("handleError")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // named routes configuration
                routeConfiguration("handleError").onException(Exception.class).handled(true).to("mock:error");
            }
        });
        context.start();

        // sort routes according to ordered
        routes.sort(OrderedComparator.get());

        // first add the routes configurations as they are globally for all routes
        for (RoutesBuilder builder : routes) {
            if (builder instanceof RouteConfigurationsBuilder) {
                RouteConfigurationsBuilder rcb = (RouteConfigurationsBuilder) builder;
                context.addRoutesConfigurations(rcb);
            }
        }
        // then add the routes
        for (RoutesBuilder builder : routes) {
            context.addRoutes(builder);
        }

        getMockEndpoint("mock:error").expectedBodiesReceived("Bye World");

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();

        assertNull(context.getRoute("foo").getConfigurationId());
        assertEquals("handleError", context.getRoute("foo2").getConfigurationId());
    }

    @Test
    public void testRoutesConfigurationOnExceptionPattern() throws Exception {
        List<RoutesBuilder> routes = new ArrayList<>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                        .routeConfigurationId("general*")
                        .throwException(new IllegalArgumentException("Foo"));
            }
        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2").routeId("foo2")
                        .routeConfigurationId("io*")
                        .throwException(new IOException("Foo2"));
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // named routes configuration
                routeConfiguration("generalError").onException(Exception.class).handled(true).to("mock:error");
                routeConfiguration("ioError").onException(IOException.class).maximumRedeliveries(3).redeliveryDelay(0)
                        .handled(true).to("mock:io");
            }
        });
        context.start();

        // sort routes according to ordered
        routes.sort(OrderedComparator.get());

        // first add the routes configurations as they are globally for all routes
        for (RoutesBuilder builder : routes) {
            if (builder instanceof RouteConfigurationsBuilder) {
                RouteConfigurationsBuilder rcb = (RouteConfigurationsBuilder) builder;
                context.addRoutesConfigurations(rcb);
            }
        }
        // then add the routes
        for (RoutesBuilder builder : routes) {
            context.addRoutes(builder);
        }

        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:io").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals("generalError", context.getRoute("foo").getConfigurationId());
        assertEquals("ioError", context.getRoute("foo2").getConfigurationId());
    }

    @Test
    public void testRoutesConfigurationOnExceptionDefault() throws Exception {
        List<RoutesBuilder> routes = new ArrayList<>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                        .throwException(new IllegalArgumentException("Foo"));
            }
        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2").routeId("foo2")
                        .throwException(new IOException("Foo2"));
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // has no name so its the default
                routeConfiguration().onException(Exception.class).handled(true).to("mock:error");
                // special for io, but only if included
                routeConfiguration("ioError").onException(IOException.class).maximumRedeliveries(3).redeliveryDelay(0)
                        .handled(true).to("mock:io");
            }
        });
        context.start();

        // sort routes according to ordered
        routes.sort(OrderedComparator.get());

        // first add the routes configurations as they are globally for all routes
        for (RoutesBuilder builder : routes) {
            if (builder instanceof RouteConfigurationsBuilder) {
                RouteConfigurationsBuilder rcb = (RouteConfigurationsBuilder) builder;
                context.addRoutesConfigurations(rcb);
            }
        }
        // then add the routes
        for (RoutesBuilder builder : routes) {
            context.addRoutes(builder);
        }

        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:io").expectedMessageCount(0);
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");
        assertMockEndpointsSatisfied();

        assertEquals("<default>", context.getRoute("foo").getConfigurationId());
        assertEquals("<default>", context.getRoute("foo2").getConfigurationId());

        context.removeRoute("foo2");

        // now re-configure route2 to use ioError route configuration
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2").routeId("foo2")
                        .routeConfigurationId("ioError")
                        .throwException(new IOException("Foo2"));
            }
        });
        // try again
        resetMocks();
        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:io").expectedBodiesReceived("Bye World");
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");
        assertMockEndpointsSatisfied();

        assertEquals("<default>", context.getRoute("foo").getConfigurationId());
        assertEquals("ioError", context.getRoute("foo2").getConfigurationId());
    }

    @Test
    public void testRoutesConfigurationIdClash() throws Exception {
        RouteConfigurationBuilder rcb = new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration().onException(Exception.class).handled(true).to("mock:foo");
                routeConfiguration("foo").onException(IOException.class).handled(true).to("mock:foo");
                routeConfiguration("bar").onException(FileNotFoundException.class).handled(true).to("mock:bar");
                routeConfiguration("foo").onException(IllegalArgumentException.class).handled(true).to("mock:foo");
            }
        };
        try {
            context.addRoutesConfigurations(rcb);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Route configuration already exists with id: foo", e.getMessage());
        }
    }

}
