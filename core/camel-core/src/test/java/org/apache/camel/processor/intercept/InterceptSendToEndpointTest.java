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
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit test for intercepting sending to endpoint
 */
public class InterceptSendToEndpointTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testInterceptEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                // START SNIPPET: e1
                // we intercept by endpoint, that means that whenever an
                // exchange is about to be sent to
                // this endpoint, its intercepted and routed with this detour
                // route beforehand
                // afterwards its send to the original intended destination. So
                // this is kinda AOP before.
                // That means mock:foo will receive the message (Bye World).
                interceptSendToEndpoint("mock:foo").to("mock:detour").transform(constant("Bye World"));

                from("direct:first").to("mock:bar").to("mock:foo").to("mock:result");
                // END SNIPPET: e1

            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:detour").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:first", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptEndpointWithPredicate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e2
                // we can also attach a predicate to the endpoint interceptor.
                // So in this example the exchange is
                // only intercepted if the body is Hello World
                interceptSendToEndpoint("mock:foo").when(body().isEqualTo("Hello World")).to("mock:detour").transform(constant("Bye World"));

                from("direct:second").to("mock:bar").to("mock:foo").to("mock:result");
                // END SNIPPET: e2

            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World", "Hi");
        // Hi is filtered out with the predicate on the intercept
        getMockEndpoint("mock:detour").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye World", "Hi");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World", "Hi");

        template.sendBody("direct:second", "Hello World");
        template.sendBody("direct:second", "Hi");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptEndpointStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                // START SNIPPET: e3
                // since we use the skipSendToOriginalEndpoint() we instruct
                // Camel to skip
                // sending the exchange to the original intended destination
                // after the intercept
                // route is complete.
                // That means that mock:foo will NOT receive the message, but
                // the message
                // is skipped and continued in the original route, so
                // mock:result will receive
                // the message.
                interceptSendToEndpoint("mock:foo").skipSendToOriginalEndpoint().transform(constant("Bye World")).to("mock:detour");

                from("direct:third").to("mock:bar").to("mock:foo").to("mock:result");
                // END SNIPPET: e3
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:detour").expectedBodiesReceived("Bye World");
        // as we stop the original destination do not receive the exchange
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        // but we continue to route afterwards
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:third", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptEndpointDirectly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:start").to("mock:detour").transform(constant("Bye World"));

                from("direct:start").to("mock:foo").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:detour").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptEndpointWithStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:start").to("mock:detour").stop();

                from("direct:start").to("mock:foo").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:detour").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptEndpointOnce() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:intercept1").to("mock:detour1");
                interceptSendToEndpoint("direct:intercept2").to("mock:detour2");

                from("direct:input1").to("direct:intercept1");
                from("direct:input2").to("direct:intercept2");

                from("direct:intercept1").to("log:1");
                from("direct:intercept2").to("log:2");
            }
        });
        context.start();

        getMockEndpoint("mock:detour1").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:detour2").expectedBodiesReceived("Hello World");

        template.sendBody("direct:input1", "Hello World");
        template.sendBody("direct:input2", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
