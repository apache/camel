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

import java.time.Duration;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A route called from a doTry body continues a failure ({@code onException(...).continued(true)}). A later exception in
 * the same doTry body must still be caught by the doCatch.
 */
class DoCatchAfterContinuedFailureInCalledRouteTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private Exchange send(String call, boolean redelivery) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (redelivery) {
                    onException(NumberFormatException.class).maximumRedeliveries(1).redeliveryDelay(0).continued(true);
                } else {
                    onException(NumberFormatException.class).continued(true);
                }

                var body = from("direct:start").doTry();
                switch (call) {
                    case "to" -> body.to("direct:callee");
                    case "routingSlip" -> body.routingSlip(constant("direct:callee"));
                    case "recipientList" -> body.recipientList(constant("direct:callee"));
                    case "dynamicRouter" -> body.dynamicRouter(method(DoCatchAfterContinuedFailureInCalledRouteTest.class,
                            "callCalleeOnce"));
                    case "calleeOk" -> body.to("direct:calleeOk");
                    default -> {
                        // nothing before the exception
                    }
                }
                body.to("mock:next")
                        .throwException(new IllegalStateException("after the call"))
                        .doCatch(IllegalArgumentException.class)
                        .to("mock:otherCatch")
                        .doCatch(IllegalStateException.class)
                        .to("mock:catch")
                        .doCatch(Exception.class)
                        .to("mock:lastCatch")
                        .end()
                        .to("mock:result");

                from("direct:callee").throwException(new NumberFormatException("in callee")).to("mock:calleeEnd");
                from("direct:calleeOk").to("mock:calleeEnd");
            }
        });
        context.start();

        return sendBounded();
    }

    private Exchange sendBounded() {
        return assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> template.send("direct:start", e -> e.getMessage().setBody("Hello")), "the send did not complete");
    }

    public static String callCalleeOnce(Exchange exchange) {
        return exchange.getProperty(Exchange.SLIP_ENDPOINT) == null ? "direct:callee" : null;
    }

    private void assertCaughtOnce(Exchange out) {
        assertNull(out.getException(), "the doCatch handles the exception");
        assertEquals(1, getMockEndpoint("mock:next").getReceivedCounter(), "mock:next");
        assertEquals(1, getMockEndpoint("mock:catch").getReceivedCounter(), "mock:catch");
        // only the first matching doCatch runs
        assertEquals(0, getMockEndpoint("mock:otherCatch").getReceivedCounter(), "mock:otherCatch");
        assertEquals(0, getMockEndpoint("mock:lastCatch").getReceivedCounter(), "mock:lastCatch");
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        // the doCatch sees the exception it caught, not the continued one
        Exchange caught = getMockEndpoint("mock:catch").getReceivedExchanges().get(0);
        Exception e = caught.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertInstanceOf(IllegalStateException.class, e);
        assertEquals("after the call", e.getMessage());
        assertEquals(0, e.getSuppressed().length, "the continued exception is not a previous exception");
    }

    @Test
    void continuedInCalledRoute() throws Exception {
        Exchange out = send("to", false);
        // the called route continued after its failure
        assertEquals(1, getMockEndpoint("mock:calleeEnd").getReceivedCounter(), "mock:calleeEnd");
        // the doCatch (and the debugger, CatchDefinition.acceptDebugger) reads this property as "a previous doCatch
        // of the doTry handled the exception"
        Exchange next = getMockEndpoint("mock:next").getReceivedExchanges().get(0);
        assertNull(next.getProperty(Exchange.EXCEPTION_HANDLED), "a continued failure is not a handled doCatch");
        assertCaughtOnce(out);
    }

    @Test
    void continuedInRoutingSlip() throws Exception {
        assertCaughtOnce(send("routingSlip", false));
    }

    @Test
    void continuedInRecipientList() throws Exception {
        assertCaughtOnce(send("recipientList", false));
    }

    @Test
    void continuedInDynamicRouter() throws Exception {
        assertCaughtOnce(send("dynamicRouter", false));
    }

    @Test
    void continuedAfterRedeliveryInCalledRoute() throws Exception {
        assertCaughtOnce(send("to", true));
    }

    @Test
    void controlNothingBeforeTheException() throws Exception {
        assertCaughtOnce(send("none", false));
    }

    @Test
    void controlCalledRouteDoesNotFail() throws Exception {
        Exchange out = send("calleeOk", false);
        assertEquals(1, getMockEndpoint("mock:calleeEnd").getReceivedCounter(), "mock:calleeEnd");
        assertCaughtOnce(out);
    }

    @Test
    void continuedBeforeTheDoTryIsNotAPreviousException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(NumberFormatException.class).continued(true);

                from("direct:start")
                        .to("direct:callee")
                        .doTry()
                        .to("direct:fail")
                        .doCatch(IllegalStateException.class)
                        .to("mock:catch")
                        .end();

                from("direct:callee").throwException(new NumberFormatException("in callee"));
                from("direct:fail").throwException(new IllegalStateException("in fail"));
            }
        });
        context.start();

        Exchange out = sendBounded();
        assertNull(out.getException(), "the doCatch handles the exception");
        assertEquals(1, getMockEndpoint("mock:catch").getReceivedCounter(), "mock:catch");
        Exception e = getMockEndpoint("mock:catch").getReceivedExchanges().get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertEquals("in fail", e.getMessage());
        // as without the doTry (ErrorHandlerFailurePathsTest.testContinuedThenUnrelatedFailure)
        assertEquals(0, e.getSuppressed().length, "the continued exception is not a previous exception");
    }

    @Test
    void controlExceptionThrownInDoCatchIsNotCaughtByTheNextDoCatch() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(NumberFormatException.class).continued(true);

                from("direct:start")
                        .doTry()
                        .throwException(new IllegalArgumentException("in try"))
                        .doCatch(IllegalArgumentException.class)
                        .to("mock:catch")
                        // continues a failure, then this doCatch fails
                        .to("direct:callee")
                        .throwException(new IllegalStateException("in catch"))
                        .doCatch(IllegalStateException.class)
                        .to("mock:otherCatch")
                        .end()
                        .to("mock:result");

                from("direct:callee").throwException(new NumberFormatException("in callee")).to("mock:calleeEnd");
            }
        });
        context.start();

        Exchange out = sendBounded();
        assertEquals(1, getMockEndpoint("mock:catch").getReceivedCounter(), "mock:catch");
        assertEquals(1, getMockEndpoint("mock:calleeEnd").getReceivedCounter(), "mock:calleeEnd");
        // as in Java, an exception thrown by a doCatch is not caught by another doCatch of the same doTry
        assertEquals(0, getMockEndpoint("mock:otherCatch").getReceivedCounter(), "mock:otherCatch");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("in catch", out.getException().getMessage());
    }
}
