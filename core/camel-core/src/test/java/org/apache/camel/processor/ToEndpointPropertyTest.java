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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ToEndpointPropertyTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSimpleToEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://result");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMediumToEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("direct:foo");

                from("direct:foo").to("mock:result");

            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://result");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRecipientListToEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").recipientList(header("foo"));
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://result");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:result");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRoutingSlipToEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routingSlip(header("foo"));
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://result");

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        a.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://a");

        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedMessageCount(1);
        b.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://b");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:a,mock:b,mock:result");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWireTapToEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").wireTap("mock:tap").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://result");

        MockEndpoint tap = getMockEndpoint("mock:tap");
        tap.expectedMessageCount(1);
        tap.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://tap");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMulticastToEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").multicast().to("direct:a", "direct:b").end().process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String to = exchange.getProperty(Exchange.TO_ENDPOINT, String.class);
                        assertEquals("direct://b", to);
                    }
                }).to("mock:result");

                from("direct:a").transform(constant("A"));
                from("direct:b").transform(constant("B"));
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).exchangeProperty(Exchange.FAILURE_ENDPOINT).isNull();
        result.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://result");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDLCToEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").disableRedelivery());

                from("direct:start").to("direct:foo").to("mock:result");

                from("direct:foo").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.message(0).exchangeProperty(Exchange.FAILURE_ENDPOINT).isEqualTo("direct://foo");
        dead.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://dead");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMediumDLCToEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("direct:dead").disableRedelivery());

                from("direct:start").to("direct:foo").to("mock:result");

                from("direct:foo").throwException(new IllegalArgumentException("Damn"));

                from("direct:dead").to("mock:a").to("mock:b").to("mock:dead");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.message(0).exchangeProperty(Exchange.FAILURE_ENDPOINT).isEqualTo("direct://foo");
        dead.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://dead");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMulticastDLC() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").disableRedelivery());

                from("direct:start").multicast().to("direct:a", "direct:b");

                from("direct:a").transform(constant("A"));
                from("direct:b").throwException(new IllegalArgumentException("Damn"));

                from("direct:dead").to("mock:dead");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.message(0).exchangeProperty(Exchange.FAILURE_ENDPOINT).isEqualTo("direct://b");
        dead.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://dead");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
