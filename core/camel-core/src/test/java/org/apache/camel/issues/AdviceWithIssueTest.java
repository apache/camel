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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

public class AdviceWithIssueTest extends ContextTestSupport {

    @Test
    public void testNoAdvice() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");
        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceWithErrorHandler() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        try {
            RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    errorHandler(deadLetterChannel("mock:dead"));
                }
            });
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("You can not advice with error handlers. Remove the error handlers from the route builder.", e.getMessage());
        }
    }

    @Test
    public void testAdviceWithOnException() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).handled(true).to("mock:error");
            }
        });

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:error").expectedBodiesReceived("Kaboom");

        template.sendBody("direct:start", "World");
        template.sendBody("direct:start", "Kaboom");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceWithInterceptFrom() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptFrom().to("mock:from");
            }
        });

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:from").expectedBodiesReceived("World");
        getMockEndpoint("mock:from").expectedHeaderReceived(Exchange.INTERCEPTED_ENDPOINT, "direct://start");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceWithInterceptSendToEndpoint() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:result").to("mock:to");
            }
        });

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:to").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:to").expectedHeaderReceived(Exchange.INTERCEPTED_ENDPOINT, "mock://result");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceWithOnCompletion() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion().to("mock:done");
            }
        });

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:done").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").process(new MyProcessor()).to("mock:result");
            }
        };
    }

    private static final class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Kaboom".equals(body)) {
                throw new IllegalArgumentException("Kaboom");
            }
            exchange.getIn().setBody("Hello " + body);
        }
    }

}
