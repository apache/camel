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
 * The statements of the section "Error handling across routes" of error-handler.adoc that no other test covers: the
 * onException of the calling route does not run for a failure the called route's error handler dealt with. Covered
 * elsewhere: the redeliveries of the called route (DoTryCalledRouteErrorHandlerTest), a dead letter channel in the
 * called route (MultiErrorHandlerInRouteTest, SedaInOutWithErrorDeadLetterChannelTest), a continued failure
 * (OnExceptionContinueSubRouteTest) and the redelivery of a whole called route without error handler
 * (RedeliverToSubRouteTest).
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
        // the caller does not redeliver
        assertEquals(1, attempts.get());
        assertInstanceOf(IOException.class, out.getException());
    }

    @Test
    void calledRouteHandlesTheFailure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, "direct:sub");
                from("direct:sub")
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

    @Test
    void exceptionThrownFromTheOnExceptionOfTheCalledRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, "direct:sub");
                from("direct:sub")
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

    /**
     * The control of the cases above: with no error handler in the called route, the same onException of the caller
     * does run.
     */
    @Test
    void noErrorHandlerOnTheCalledRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                caller(this, "direct:sub");
                from("direct:sub").errorHandler(noErrorHandler())
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
        // the onException of the called route does not run, the redeliveries of the caller call the sub route again
        assertEquals(3, attempts.get());
        assertNull(out.getException());
    }

    @Test
    void doCatchInTheCallingRoute() throws Exception {
        context.getRegistry().bind("inventory", new Inventory());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // the example of the documentation, with no redelivery delay
                from("direct:order")
                        .doTry()
                            .to("direct:inventory")
                        .doCatch(IOException.class)
                            .setBody(constant("Inventory not available"))
                        .end()
                        .to("mock:afterTry");

                from("direct:inventory")
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

    public class Inventory {
        public void reserve(String body) throws IOException {
            attempts.incrementAndGet();
            throw new IOException("Inventory service not reachable");
        }
    }
}
