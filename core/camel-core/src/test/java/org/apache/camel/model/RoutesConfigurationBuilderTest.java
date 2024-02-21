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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.support.OrderedComparator;
import org.junit.jupiter.api.Test;

public class RoutesConfigurationBuilderTest extends ContextTestSupport {

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
                from("direct:start")
                        .throwException(new IllegalArgumentException("Foo"));
            }
        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // global routes configuration
                routeConfiguration().onException(Exception.class).handled(true).to("mock:error");
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

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRoutesConfigurationOnCompletion() throws Exception {
        List<RoutesBuilder> routes = new ArrayList<>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("mock:result");
            }
        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2")
                        // route scoped that overrides the global scoped
                        .onCompletion().to("mock:done2").end()
                        .to("mock:result");
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // global routes configuration
                routeConfiguration().onCompletion().to("mock:done");
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

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:done").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:done2").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRoutesConfigurationIntercept() throws Exception {
        List<RoutesBuilder> routes = new ArrayList<>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody(constant("A"))
                        .setBody(constant("B"))
                        .to("mock:result");
            }
        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2")
                        .setBody(constant("C"))
                        .setBody(constant("D"))
                        .to("mock:result");
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // global routes configuration
                routeConfiguration().intercept().to("mock:step");
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

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:step").expectedBodiesReceived("Hello World", "A", "B", "Bye World", "C", "D");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRoutesConfigurationInterceptFrom() throws Exception {
        List<RoutesBuilder> routes = new ArrayList<>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                        .setBody(constant("A"))
                        .setBody(constant("B"))
                        .to("mock:result");
            }
        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2")
                        .setBody(constant("C"))
                        .setBody(constant("D"))
                        .to("mock:result");
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // global routes configuration
                routeConfiguration().interceptFrom("direct*").to("mock:step");
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

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:step").expectedBodiesReceived("Bye World");

        template.sendBody("seda:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRoutesConfigurationInterceptSendTo() throws Exception {
        List<RoutesBuilder> routes = new ArrayList<>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody(constant("A"))
                        .to("mock:foo")
                        .setBody(constant("B"))
                        .to("mock:bar")
                        .to("mock:result");
            }
        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2")
                        .setBody(constant("C"))
                        .to("mock:foo")
                        .setBody(constant("D"))
                        .to("mock:bar")
                        .to("mock:result");
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // global routes configuration
                routeConfiguration().interceptSendToEndpoint("mock:foo").to("mock:step");
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

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:step").expectedBodiesReceived("A", "C");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

}
