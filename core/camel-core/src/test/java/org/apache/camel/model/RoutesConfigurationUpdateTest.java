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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

public class RoutesConfigurationUpdateTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRoutesConfigurationUpdate() throws Exception {
        context.start();

        RouteConfigurationBuilder rcb = new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration("myConfig").onException(Exception.class).handled(true).to("mock:error");
            }
        };
        rcb.addRouteConfigurationsToCamelContext(context);
        RouteBuilder rb = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start").routeConfigurationId("myConfig")
                        .throwException(new IllegalArgumentException("Foo"));

                from("direct:start2").routeId("start2").routeConfigurationId("myConfig")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        };
        rb.addRoutesToCamelContext(context);

        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:error2").expectedMessageCount(0);
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");
        assertMockEndpointsSatisfied();

        // update route configuration and routes (remove routes first)
        context.getRouteController().removeAllRoutes();
        RouteConfigurationBuilder rcb2 = new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration("myConfig").onException(Exception.class).handled(true).to("mock:error2");
            }
        };
        rcb2.updateRouteConfigurationsToCamelContext(context);
        RouteBuilder rb2 = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start").routeConfigurationId("myConfig")
                        .throwException(new IllegalArgumentException("Foo"));

                from("direct:start2").routeId("start2").routeConfigurationId("myConfig")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        };
        rb2.updateRoutesToCamelContext(context);

        resetMocks();

        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:error2").expectedBodiesReceived("Hello World2", "Bye World2");
        template.sendBody("direct:start", "Hello World2");
        template.sendBody("direct:start2", "Bye World2");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRoutesConfigurationGlobalUpdate() throws Exception {
        context.start();

        RouteConfigurationBuilder rcb = new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration().onException(Exception.class).handled(true).to("mock:error");
            }
        };
        rcb.addRouteConfigurationsToCamelContext(context);
        RouteBuilder rb = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start")
                        .throwException(new IllegalArgumentException("Foo"));

                from("direct:start2").routeId("start2")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        };
        rb.addRoutesToCamelContext(context);

        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:error2").expectedMessageCount(0);
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");
        assertMockEndpointsSatisfied();

        // update route configuration and routes (remove routes first)
        context.getRouteController().removeAllRoutes();
        RouteConfigurationBuilder rcb2 = new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration().onException(Exception.class).handled(true).to("mock:error2");
            }
        };
        rcb2.updateRouteConfigurationsToCamelContext(context);
        RouteBuilder rb2 = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start")
                        .throwException(new IllegalArgumentException("Foo"));

                from("direct:start2").routeId("start2")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        };
        rb2.updateRoutesToCamelContext(context);

        resetMocks();

        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:error2").expectedBodiesReceived("Hello World2", "Bye World2");
        template.sendBody("direct:start", "Hello World2");
        template.sendBody("direct:start2", "Bye World2");
        assertMockEndpointsSatisfied();
    }

}
