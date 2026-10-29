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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DoTryCalledRouteErrorHandlerTest extends ContextTestSupport {

    private final AtomicInteger failAttempts = new AtomicInteger();
    private final AtomicInteger routerCalls = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void routes(boolean doTryCaller, String eip) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);
                if (doTryCaller) {
                    from("direct:r0").doTry().to("direct:r1").doFinally().to("mock:fin").end();
                } else {
                    from("direct:r0").to("direct:r1");
                }
                switch (eip) {
                    case "slip" -> from("direct:r1").routingSlip(constant("mock:m1,direct:fail"));
                    case "asyncSlip" -> from("direct:r1").threads(1, 1).routingSlip(constant("mock:m1,direct:fail"));
                    case "multicast" -> from("direct:r1").multicast().to("mock:m1", "direct:fail").end();
                    case "recipientList" -> from("direct:r1").recipientList(constant("mock:m1,direct:fail"));
                    case "split" -> from("direct:r1").split(constant("mock:m1,direct:fail")).toD("${body}").end();
                    case "router" -> from("direct:r1").dynamicRouter(method(new Object() {
                        public String route() {
                            int n = routerCalls.getAndIncrement();
                            return n == 0 ? "mock:m1" : n == 1 ? "direct:fail" : null;
                        }
                    }, "route"));
                    default -> from("direct:r1").to("mock:m1").to("direct:fail");
                }
                failingRoute(this);
            }
        });
        context.start();
    }

    private void check(boolean doTryCaller, String eip) throws Exception {
        routes(doTryCaller, eip);
        Exchange out = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> template.send("direct:r0", e -> e.getMessage().setBody("b")), "the send did not complete");
        assertInstanceOf(IllegalStateException.class, out.getException(), "the failure must reach the caller");
        assertEquals(1, getMockEndpoint("mock:m1").getReceivedCounter(), "mock:m1 (no duplicates)");
        assertEquals(3, failAttempts.get(), "attempts on the failing output (1 + 2 redeliveries)");
    }

    @Test
    void slipPlainCaller() throws Exception {
        check(false, "slip");
    }

    @Test
    void slipDoTryCaller() throws Exception {
        check(true, "slip");
    }

    @Test
    void multicastPlainCaller() throws Exception {
        check(false, "multicast");
    }

    @Test
    void multicastDoTryCaller() throws Exception {
        check(true, "multicast");
    }

    @Test
    void recipientListDoTryCaller() throws Exception {
        check(true, "recipientList");
    }

    @Test
    void splitPlainCaller() throws Exception {
        check(false, "split");
    }

    // control: the steps of a split are channels with the error handler of the route, so a split was not affected
    @Test
    void splitDoTryCaller() throws Exception {
        check(true, "split");
    }

    @Test
    void routerPlainCaller() throws Exception {
        check(false, "router");
    }

    @Test
    void routerDoTryCaller() throws Exception {
        check(true, "router");
    }

    @Test
    void plainToDoTryCaller() throws Exception {
        check(true, "to");
    }

    @Test
    void asynchronousCalledRouteUsesOwnErrorHandler() throws Exception {
        check(true, "asyncSlip");
    }

    @Test
    void returningFromCalledRouteRestoresOuterTry() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));
                from("direct:start").doTry()
                        .to("direct:sub")
                        .recipientList(constant("direct:fail")).end()
                    .doCatch(Exception.class).to("mock:caught").end();
                from("direct:sub").threads(1, 1).to("mock:sub");
                failingRoute(this);
            }
        });
        context.start();
        template.sendBody("direct:start", "body");
        assertEquals(1, failAttempts.get());
        assertEquals(1, getMockEndpoint("mock:caught").getReceivedCounter());
        assertEquals(0, getMockEndpoint("mock:dead").getReceivedCounter());
    }

    @Test
    void recursiveRouteInvocationHasItsOwnTryScope() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);
                from("direct:recursive").choice()
                    .when(header("nested").isNull())
                        .doTry().setHeader("nested", constant(true)).to("direct:recursive")
                        .doCatch(Exception.class).to("mock:caught").end().endChoice()
                    .otherwise().routingSlip(constant("mock:m1,direct:fail")).end();
                failingRoute(this);
            }
        });
        context.start();
        template.sendBody("direct:recursive", "body");
        assertEquals(1, getMockEndpoint("mock:m1").getReceivedCounter());
        assertEquals(3, failAttempts.get());
        assertEquals(1, getMockEndpoint("mock:caught").getReceivedCounter());
    }

    @Test
    void doCatchOfTheCallerCatchesAfterTheCalledRouteRedelivered() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);
                from("direct:r0").doTry().to("direct:r1").doCatch(Exception.class).to("mock:caught").end();
                from("direct:r1").routingSlip(constant("mock:m1,direct:fail"));
                failingRoute(this);
            }
        });
        context.start();
        Exchange out = template.send("direct:r0", e -> e.getMessage().setBody("b"));
        assertNull(out.getException());
        assertEquals(1, getMockEndpoint("mock:m1").getReceivedCounter());
        assertEquals(3, failAttempts.get());
        assertEquals(1, getMockEndpoint("mock:caught").getReceivedCounter());
    }

    @Test
    void sedaCallFromDoTryUsesTheCalledRouteErrorHandler() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);
                from("direct:r0").doTry().to("seda:r1?waitForTaskToComplete=Always").doCatch(Exception.class)
                        .to("mock:caught").end();
                from("seda:r1").routingSlip(constant("mock:m1,direct:fail"));
                failingRoute(this);
            }
        });
        context.start();
        template.sendBody("direct:r0", "b");
        assertEquals(1, getMockEndpoint("mock:m1").getReceivedCounter());
        assertEquals(3, failAttempts.get());
        assertEquals(1, getMockEndpoint("mock:caught").getReceivedCounter());
    }

    @Test
    void enrichFromDoTryUsesTheCalledRouteErrorHandler() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);
                from("direct:r0").doTry().enrich("direct:r1").doCatch(Exception.class).to("mock:caught").end();
                from("direct:r1").multicast().to("mock:m1", "direct:fail").end();
                failingRoute(this);
            }
        });
        context.start();
        template.sendBody("direct:r0", "b");
        assertEquals(1, getMockEndpoint("mock:m1").getReceivedCounter());
        assertEquals(3, failAttempts.get());
        assertEquals(1, getMockEndpoint("mock:caught").getReceivedCounter());
    }

    @Test
    void doTryInTheCalledRouteStillTurnsOffItsHandlerPerOutput() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);
                from("direct:r0").doTry().to("direct:r1").doCatch(Exception.class).to("mock:outer").end();
                from("direct:r1").doTry().recipientList(constant("direct:fail")).end()
                        .doCatch(Exception.class).to("mock:inner").end();
                failingRoute(this);
            }
        });
        context.start();
        template.sendBody("direct:r0", "b");
        assertEquals(1, failAttempts.get());
        assertEquals(1, getMockEndpoint("mock:inner").getReceivedCounter());
        assertEquals(0, getMockEndpoint("mock:outer").getReceivedCounter());
    }

    @Test
    void returningFromACalledRouteThatContinuedAFailureRestoresOuterTry() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));
                from("direct:r0").doTry()
                        .to("direct:r1")
                        .recipientList(constant("direct:fail")).end()
                    .doCatch(Exception.class).to("mock:caught").end();
                from("direct:r1")
                        .onException(IllegalArgumentException.class).continued(true).end()
                        .throwException(new IllegalArgumentException("continued"));
                failingRoute(this);
            }
        });
        context.start();
        template.sendBody("direct:r0", "b");
        // the recipient list after the call is in the try block of the caller again (no error handler per output)
        assertEquals(1, failAttempts.get());
        assertEquals(1, getMockEndpoint("mock:caught").getReceivedCounter());
        assertEquals(0, getMockEndpoint("mock:dead").getReceivedCounter());
    }

    @Test
    void multicastFailureHandledByTheCalledRouteNoLongerReachesTheCallersDoCatch() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:r0").doTry().to("direct:r1").doCatch(Exception.class).to("mock:caught").end();
                from("direct:r1").errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .multicast().to("mock:m1", "direct:fail").end();
                failingRoute(this);
            }
        });
        context.start();
        Exchange out = template.send("direct:r0", e -> e.getMessage().setBody("b"));
        // the dead letter channel of the called route handles the failed output after its redeliveries
        assertNull(out.getException());
        assertEquals(1, getMockEndpoint("mock:m1").getReceivedCounter());
        assertEquals(3, failAttempts.get());
        assertEquals(1, getMockEndpoint("mock:dead").getReceivedCounter());
        assertEquals(0, getMockEndpoint("mock:caught").getReceivedCounter());
    }

    private void failingRoute(RouteBuilder builder) {
        builder.from("direct:fail").errorHandler(builder.noErrorHandler()).process(e -> {
            failAttempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });
    }
}
