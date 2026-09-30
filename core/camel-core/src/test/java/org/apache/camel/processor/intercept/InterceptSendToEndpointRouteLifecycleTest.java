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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.InterceptSendToMockEndpointStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The interceptSendToEndpoint of routes when routes are stopped, removed and added again, and when CamelContext is
 * restarted.
 */
public class InterceptSendToEndpointRouteLifecycleTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static RouteBuilder twoRoutes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("mock:target").to("mock:intercepted");

                from("direct:a").routeId("a").to("mock:target");
                from("direct:b").routeId("b").to("mock:target");
            }
        };
    }

    private void assertIntercepted(String uri, String expectedRouteId) throws Exception {
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        MockEndpoint target = getMockEndpoint("mock:target");
        intercepted.reset();
        target.reset();
        intercepted.expectedMessageCount(1);
        if (expectedRouteId != null) {
            intercepted.expectedPropertyReceived(ExchangePropertyKey.INTERCEPTED_ROUTE_ID.getName(), expectedRouteId);
        }
        target.expectedMessageCount(1);

        template.sendBody(uri, "Hello");

        MockEndpoint.assertIsSatisfied(intercepted, target);
    }

    @Test
    public void testRemoveRoute() throws Exception {
        context.addRoutes(twoRoutes());
        context.start();
        assertIntercepted("direct:a", "a");
        assertIntercepted("direct:b", "b");

        // removing a route does not affect the other route
        context.getRouteController().stopRoute("a");
        context.removeRoute("a");
        assertIntercepted("direct:b", "b");

        context.getRouteController().stopRoute("b");
        context.removeRoute("b");
        context.addRoutes(twoRoutes());
        assertIntercepted("direct:a", "a");
        assertIntercepted("direct:b", "b");
    }

    @Test
    public void testStopRoute() throws Exception {
        context.addRoutes(twoRoutes());
        context.start();

        context.getRouteController().stopRoute("a");
        assertIntercepted("direct:b", "b");

        context.getRouteController().startRoute("a");
        assertIntercepted("direct:a", "a");
        assertIntercepted("direct:b", "b");
    }

    @Test
    public void testNoInterceptorWhenAllRoutesRemoved() throws Exception {
        context.addRoutes(twoRoutes());
        context.start();
        assertIntercepted("direct:a", "a");

        context.getRouteController().stopRoute("a");
        context.getRouteController().stopRoute("b");
        context.removeRoute("a");
        context.removeRoute("b");

        // the interceptors of the removed routes are no longer used
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        intercepted.reset();
        intercepted.expectedMessageCount(0);
        template.sendBody("mock:target", "Hello");
        intercepted.assertIsSatisfied();
    }

    @Test
    public void testProducerTemplateUsesRegisteredInterceptor() throws Exception {
        context.addRoutes(twoRoutes());
        context.start();

        // not sent from a route, so the interceptor of the first route is used
        assertIntercepted("mock:target", "a");
    }

    @Test
    public void testRestartCamelContext() throws Exception {
        context.addRoutes(twoRoutes());
        context.start();
        assertIntercepted("direct:a", "a");

        context.stop();
        context.start();
        // the producer template caches producers of the endpoints from before the restart
        template.stop();
        template = context.createProducerTemplate();
        assertIntercepted("direct:a", "a");
        assertIntercepted("direct:b", "b");
    }

    @Test
    public void testTwoRouteBuilders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("mock:target").setHeader("by", constant("x")).to("mock:intercepted");
                from("direct:x").routeId("x").to("mock:target");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("mock:target").setHeader("by", constant("y")).to("mock:intercepted");
                from("direct:y").routeId("y").to("mock:target");
            }
        });
        context.start();

        // each route uses the interceptor of its own RouteBuilder
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        intercepted.expectedHeaderValuesReceivedInAnyOrder("by", "x", "y");
        template.sendBody("direct:x", "Hello");
        template.sendBody("direct:y", "Hello");
        intercepted.assertIsSatisfied();
        assertEquals("x", intercepted.getReceivedExchanges().get(0).getMessage().getHeader("by"));
    }

    @Test
    public void testMockEndpointsAndIntercept() throws Exception {
        // mock the endpoint as well (the mock runs after the interceptor of the route)
        context.getCamelContextExtension().registerEndpointCallback(
                new InterceptSendToMockEndpointStrategy("direct:target"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("direct:target").setHeader("intercepted", constant(true)).to("mock:intercepted");

                from("direct:a").routeId("a").to("direct:target");
                from("direct:target").routeId("target").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:intercepted").expectedMessageCount(1);
        getMockEndpoint("mock:direct:target").expectedMessageCount(1);
        getMockEndpoint("mock:direct:target").expectedHeaderReceived("intercepted", true);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:a", "Hello");

        assertMockEndpointsSatisfied();
    }
}
