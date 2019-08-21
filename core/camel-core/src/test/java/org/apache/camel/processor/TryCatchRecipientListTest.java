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

public class TryCatchRecipientListTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testTryCatchTo() throws Exception {
        context.addRoutes(createTryCatchToRouteBuilder());
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("doCatch");
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:catch").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTryCatchRecipientList() throws Exception {
        context.addRoutes(createTryCatchRecipientListRouteBuilder());
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("doCatch");
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:catch").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDualTryCatchRecipientList() throws Exception {
        context.addRoutes(createDualTryCatchRecipientListRouteBuilder());
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("doCatch");
        getMockEndpoint("mock:result").expectedBodiesReceived("doCatch");
        getMockEndpoint("mock:result2").expectedBodiesReceived("doCatch2");
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:catch").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);
        getMockEndpoint("mock:catch2").expectedBodiesReceived("doCatch");
        getMockEndpoint("mock:catch2").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTo() throws Exception {
        context.addRoutes(createToRouteBuilder());
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:dead").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRecipientList() throws Exception {
        context.addRoutes(createRecipientListRouteBuilder());
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:dead").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createTryCatchToRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").doTry().to("direct:foo").doCatch(Exception.class).to("mock:catch").transform().constant("doCatch").end().to("mock:result");

                from("direct:foo").errorHandler(noErrorHandler()).to("mock:foo").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced");
                    }
                });
            }
        };
    }

    protected RouteBuilder createTryCatchRecipientListRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").doTry().recipientList(constant("direct:foo")).end().doCatch(Exception.class).to("mock:catch").transform().constant("doCatch").end()
                    .to("mock:result");

                from("direct:foo").errorHandler(noErrorHandler()).to("mock:foo").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced");
                    }
                });
            }
        };
    }

    protected RouteBuilder createDualTryCatchRecipientListRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").doTry().recipientList(constant("direct:foo")).end().doCatch(Exception.class).to("mock:catch").transform().constant("doCatch").end()
                    .to("mock:result").doTry().recipientList(constant("direct:bar")).end().doCatch(Exception.class).to("mock:catch2").transform().constant("doCatch2").end()
                    .to("mock:result2");

                from("direct:foo").errorHandler(noErrorHandler()).to("mock:foo").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced");
                    }
                });

                from("direct:bar").errorHandler(noErrorHandler()).to("mock:bar").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced Again");
                    }
                });
            }
        };
    }

    protected RouteBuilder createToRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").to("direct:foo").to("mock:result");

                from("direct:foo").errorHandler(noErrorHandler()).to("mock:foo").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced");
                    }
                });
            }
        };
    }

    protected RouteBuilder createRecipientListRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").recipientList(constant("direct:foo")).end().to("mock:result");

                from("direct:foo").errorHandler(noErrorHandler()).to("mock:foo").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced");
                    }
                });
            }
        };
    }
}
