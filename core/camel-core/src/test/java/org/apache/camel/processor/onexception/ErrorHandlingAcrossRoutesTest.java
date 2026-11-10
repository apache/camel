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
package org.apache.camel.processor.onexception;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The statements of the section "Error handling across routes" of error-handler.adoc: a failure in a called route is
 * dealt with by the error handler of the called route, and the error handlers of the calling route do not deal with it
 * again. Each case runs with direct, and with seda where the caller waits for the reply (InOut).
 */
@Timeout(20)
class ErrorHandlingAcrossRoutesTest extends ContextTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private Processor failing() {
        return e -> {
            attempts.incrementAndGet();
            throw new IOException("Forced");
        };
    }

    /**
     * The calling route: its onException handles any exception after 2 redeliveries, so a redelivery by the caller
     * would call the sub route again.
     */
    private void caller(RouteBuilder builder, String sub) {
        builder.from("direct:start")
                .onException(Exception.class).handled(true).maximumRedeliveries(2).redeliveryDelay(0)
                .to("mock:callerOnException").end()
                .to(sub)
                .to("mock:afterCall");
    }

    private Exchange send() {
        return template.send("direct:start", ExchangePattern.InOut, e -> e.getMessage().setBody("Hello"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:sub", "seda:sub" })
    void calledRouteDoesNotHandleTheFailure(String sub) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, sub);
                from(sub)
                        .onException(IOException.class).maximumRedeliveries(2).redeliveryDelay(0)
                        .to("mock:subOnException").end()
                        .process(failing());
            }
        });
        context.start();

        getMockEndpoint("mock:subOnException").expectedMessageCount(1);
        getMockEndpoint("mock:callerOnException").expectedMessageCount(0);
        getMockEndpoint("mock:afterCall").expectedMessageCount(0);

        Exchange out = send();

        assertMockEndpointsSatisfied();
        // the redeliveries of the called route only: the caller does not redeliver
        assertEquals(3, attempts.get());
        assertInstanceOf(IOException.class, out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:sub", "seda:sub" })
    void calledRouteWithOnlyTheDefaultErrorHandler(String sub) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, sub);
                from(sub).process(failing());
            }
        });
        context.start();

        getMockEndpoint("mock:callerOnException").expectedMessageCount(0);
        getMockEndpoint("mock:afterCall").expectedMessageCount(0);

        Exchange out = send();

        assertMockEndpointsSatisfied();
        assertEquals(1, attempts.get());
        assertInstanceOf(IOException.class, out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:sub", "seda:sub" })
    void calledRouteHandlesTheFailure(String sub) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, sub);
                from(sub)
                        .onException(IOException.class).handled(true).setBody(constant("Handled by the sub route")).end()
                        .process(failing());
            }
        });
        context.start();

        getMockEndpoint("mock:callerOnException").expectedMessageCount(0);
        getMockEndpoint("mock:afterCall").expectedMessageCount(0);

        Exchange out = send();

        assertMockEndpointsSatisfied();
        assertEquals(1, attempts.get());
        assertNull(out.getException());
        assertEquals("Handled by the sub route", out.getMessage().getBody());
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:sub", "seda:sub" })
    void calledRouteWithDeadLetterChannel(String sub) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, sub);
                from(sub).errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .process(failing());
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:callerOnException").expectedMessageCount(0);
        getMockEndpoint("mock:afterCall").expectedMessageCount(0);

        Exchange out = send();

        assertMockEndpointsSatisfied();
        assertEquals(3, attempts.get());
        assertNull(out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:sub", "seda:sub" })
    void calledRouteContinuesTheFailure(String sub) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, sub);
                from(sub)
                        .onException(IOException.class).continued(true).end()
                        .process(failing());
            }
        });
        context.start();

        getMockEndpoint("mock:callerOnException").expectedMessageCount(0);
        getMockEndpoint("mock:afterCall").expectedMessageCount(1);

        Exchange out = send();

        assertMockEndpointsSatisfied();
        assertEquals(1, attempts.get());
        assertNull(out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:sub", "seda:sub" })
    void exceptionThrownFromTheOnExceptionOfTheCalledRoute(String sub) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, sub);
                from(sub)
                        .onException(IOException.class)
                        .throwException(new IllegalStateException("Thrown from onException")).end()
                        .process(failing());
            }
        });
        context.start();

        getMockEndpoint("mock:callerOnException").expectedMessageCount(0);
        getMockEndpoint("mock:afterCall").expectedMessageCount(0);

        Exchange out = send();

        assertMockEndpointsSatisfied();
        assertEquals(1, attempts.get());
        assertInstanceOf(IllegalStateException.class, out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:sub", "seda:sub" })
    void noErrorHandlerOnTheCalledRoute(String sub) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, sub);
                from(sub).errorHandler(noErrorHandler())
                        .onException(IOException.class).maximumRedeliveries(5).redeliveryDelay(0)
                        .to("mock:subOnException").end()
                        .process(failing());
            }
        });
        context.start();

        getMockEndpoint("mock:subOnException").expectedMessageCount(0);
        getMockEndpoint("mock:callerOnException").expectedMessageCount(1);
        getMockEndpoint("mock:afterCall").expectedMessageCount(0);

        Exchange out = send();

        assertMockEndpointsSatisfied();
        // the onException of the called route does not run, and the redeliveries of the caller call the whole sub route again
        assertEquals(3, attempts.get());
        assertNull(out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:inventory", "seda:inventory" })
    void doCatchInTheCallingRoute(String sub) throws Exception {
        context.getRegistry().bind("inventory", new Inventory());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // the example of the documentation, with no redelivery delay
                from("direct:order")
                        .doTry()
                            .to(sub)
                        .doCatch(IOException.class)
                            .setBody(constant("Inventory not available"))
                        .end()
                        .to("mock:afterTry");

                from(sub)
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0))
                        .bean("inventory", "reserve");
            }
        });
        context.start();

        getMockEndpoint("mock:afterTry").expectedBodiesReceived("Inventory not available");

        Exchange out = template.send("direct:order", ExchangePattern.InOut, e -> e.getMessage().setBody("Order"));

        assertMockEndpointsSatisfied();
        // the error handler of the called route redelivers first, then the doCatch of the caller catches the failure
        assertEquals(4, attempts.get());
        assertNull(out.getException());
        assertEquals("Inventory not available", out.getMessage().getBody());
    }

    @Test
    void sedaInOnlyDoesNotWaitForTheCalledRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, "seda:sub");
                from("seda:sub").process(failing()).to("mock:sub");
            }
        });
        context.start();

        getMockEndpoint("mock:afterCall").expectedMessageCount(1);
        getMockEndpoint("mock:callerOnException").expectedMessageCount(0);

        Exchange out = template.send("direct:start", ExchangePattern.InOnly, e -> e.getMessage().setBody("Hello"));

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
    }

    public class Inventory {
        public void reserve(String body) throws IOException {
            attempts.incrementAndGet();
            throw new IOException("Inventory service not reachable");
        }
    }
}
