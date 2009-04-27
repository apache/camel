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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for intercepting endpoint
 * 
 * @version $Revision$
 */
public class InterceptEndpointTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testInterceptEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // we intercept by endpoint, that means that whenever an exchange is about to be sent to
                // this endpoint, its intercepted and routed with this detour route beforehand
                // afterwards its send to the original intended destination. So this is kinda AOP before.
                // That means mock:foo will receive the message (Bye World).
                interceptSendToEndpoint("mock:foo").to("mock:detour").transform(constant("Bye World"));

                from("direct:first")
                    .to("mock:bar")
                    .to("mock:foo")
                    .to("mock:result");
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

    public void testInterceptEndpointWithPredicate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e2
                // we can also attach a predicate to the endpoint interceptor. So in this example the exchange is
                // only intercepted if the body is Hello World
                interceptSendToEndpoint("mock:foo").when(body().isEqualTo("Hello World")).to("mock:detour").transform(constant("Bye World"));

                from("direct:second")
                    .to("mock:bar")
                    .to("mock:foo")
                    .to("mock:result");
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

    public void testInterceptEndpointStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e3
                // since we use the stop() at the end of the detour route we instruct Camel to skip
                // sending the exchange to the original intended destination.
                // That means that mock:foo will NOT receive the message, but the message
                // is skipped and continued in the original route, so mock:result will receive
                // the message.
                interceptSendToEndpoint("mock:foo").transform(constant("Bye World")).to("mock:detour").stop();

                from("direct:third")
                    .to("mock:bar")
                    .to("mock:foo")
                    .to("mock:result");
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

    public void testInterceptEndpointDirectly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:start").to("mock:detour").transform(constant("Bye World"));

                from("direct:start")
                    .to("mock:foo")
                    .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:detour").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
