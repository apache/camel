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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class RouteAutoStartupTest extends ContextTestSupport {

    @Test
    public void testRouteAutoStartedUsingBoolean() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").autoStartup(true).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRouteNotAutoStartedUsingBoolean() throws Exception {
        context.getComponent("direct", DirectComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").id("route1").autoStartup(false).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("route shouldn't be started yet");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        // reset mock, start route and resend message
        mock.reset();
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute("route1");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRouteAutoStartedUsingString() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").autoStartup("true").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRouteNotAutoStartedUsingString() throws Exception {
        context.getComponent("direct", DirectComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").id("route1").autoStartup("false").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("route shouldn't be started yet");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        // reset mock, start route and resend message
        mock.reset();
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute("route1");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRouteAutoStartedUsingProperties() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getPropertiesComponent().setLocation("classpath:org/apache/camel/processor/routeAutoStartupTest.properties");

                from("direct:start").autoStartup("{{autoStartupProp}}").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRouteNotAutoStartedUsingProperties() throws Exception {
        context.getComponent("direct", DirectComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getPropertiesComponent().setLocation("classpath:org/apache/camel/processor/routeAutoStartupTest.properties");

                from("direct:start").id("route1").autoStartup("{{noAutoStartupProp}}").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("route shouldn't be started yet");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        // reset mock, start route and resend message
        mock.reset();
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute("route1");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }
}
