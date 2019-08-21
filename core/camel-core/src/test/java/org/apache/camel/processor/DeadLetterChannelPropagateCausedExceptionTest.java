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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class DeadLetterChannelPropagateCausedExceptionTest extends ContextTestSupport {

    @Test
    public void testDLCPropagateCaused() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // goes directly to mock:dead but we want the caused exception
                // propagated
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").to("mock:a").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // the caused exception should be propagated
        Exception cause = getMockEndpoint("mock:dead").getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);
        assertIsInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Damn", cause.getMessage());
    }

    @Test
    public void testDLCPropagateCausedInRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("direct:dead"));

                // use a route as DLC to test the cause exception is still
                // propagated
                from("direct:dead").to("log:dead").to("mock:dead");

                from("direct:start").to("mock:a").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // the caused exception should be propagated
        Exception cause = getMockEndpoint("mock:dead").getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);
        assertIsInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Damn", cause.getMessage());
    }

    @Test
    public void testDLCPropagateCausedUseOriginalMessage() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // goes directly to mock:dead but we want the caused exception
                // propagated
                errorHandler(deadLetterChannel("mock:dead").useOriginalMessage());

                from("direct:start").to("mock:a").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // the caused exception should be propagated
        Exception cause = getMockEndpoint("mock:dead").getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);
        assertIsInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Damn", cause.getMessage());
    }

    @Test
    public void testDLCPropagateCausedInRouteUseOriginalMessage() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("direct:dead").useOriginalMessage());

                // use a route as DLC to test the cause exception is still
                // propagated
                from("direct:dead").to("log:dead").to("mock:dead");

                from("direct:start").to("mock:a").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // the caused exception should be propagated
        Exception cause = getMockEndpoint("mock:dead").getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);
        assertIsInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Damn", cause.getMessage());
    }

    @Test
    public void testDLCPropagateCausedInSplitter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").to("mock:a").split(body().tokenize(",")).stopOnException().process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if ("Kaboom".equals(body)) {
                            throw new IllegalArgumentException("Damn");
                        }
                    }
                }).to("mock:line");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:line").expectedBodiesReceived("A", "B", "C");
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", "A,B,C,Kaboom");

        assertMockEndpointsSatisfied();

        // the caused exception should be propagated
        Exception cause = getMockEndpoint("mock:dead").getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);
        assertIsInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Damn", cause.getMessage());
    }

}
