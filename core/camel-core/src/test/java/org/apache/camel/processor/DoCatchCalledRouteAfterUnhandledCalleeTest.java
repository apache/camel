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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The doTry calls a route that fails unhandled (default error handler), and the doCatch then calls a route with an
 * error handler of its own. That route must handle (or continue) its own failure, as when the doTry failed in the same
 * route. Most cases that go through a failing callee have a control where the doTry fails in the same route, with the
 * same expected outcome.
 */
class DoCatchCalledRouteAfterUnhandledCalleeTest extends ContextTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the route called from the doCatch, for each case: a dead letter channel, a handled onException, a
                // continued onException, a dead letter channel that fails asynchronously, and a default error handler
                // that redelivers
                for (String target : new String[] {
                        "dlcRoute", "onExceptionRoute", "continuedRoute", "dlcRouteAsync", "redeliveryRoute" }) {
                    from("direct:viaCallee-" + target)
                            .doTry()
                                .to("direct:failing")
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                                .to("direct:" + target)
                                .to("mock:catchEnd")
                            .end()
                            .to("mock:after");
                    from("direct:inRoute-" + target)
                            .doTry()
                                .throwException(new IllegalArgumentException("in route"))
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                                .to("direct:" + target)
                                .to("mock:catchEnd")
                            .end()
                            .to("mock:after");
                }

                // a doTry and doCatch inside a handled onException: the onException still handles the failure
                from("direct:onExceptionViaCallee")
                        .onException(IOException.class).handled(true)
                            .doTry()
                                .to("direct:failing")
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                            .end()
                            .to("mock:onExceptionEnd")
                        .end()
                        .throwException(new IOException("in route"));
                from("direct:onExceptionInRoute")
                        .onException(IOException.class).handled(true)
                            .doTry()
                                .throwException(new IllegalArgumentException("in onException"))
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                            .end()
                            .to("mock:onExceptionEnd")
                        .end()
                        .throwException(new IOException("in route"));

                // a second doTry in the same route, after a doCatch caught the failure of a called route
                from("direct:secondDoTryViaCallee")
                        .doTry()
                            .to("direct:failing")
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                        .end()
                        .doTry()
                            .throwException(new IllegalArgumentException("second doTry"))
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:secondCatch")
                            .to("direct:dlcRoute")
                        .end()
                        .to("mock:after");
                from("direct:secondDoTryInRoute")
                        .doTry()
                            .throwException(new IllegalArgumentException("in route"))
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                        .end()
                        .doTry()
                            .throwException(new IllegalArgumentException("second doTry"))
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:secondCatch")
                            .to("direct:dlcRoute")
                        .end()
                        .to("mock:after");

                // a split after a doCatch that caught the failure of a called route: the split copies take over the
                // handled mark of the exchange
                from("direct:splitViaCallee")
                        .doTry()
                            .to("direct:failing")
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                        .end()
                        .split(body().tokenize(",")).to("direct:dlcRoute").end()
                        .to("mock:after");
                from("direct:splitInRoute")
                        .doTry()
                            .throwException(new IllegalArgumentException("in route"))
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                        .end()
                        .split(body().tokenize(",")).to("direct:dlcRoute").end()
                        .to("mock:after");

                // the doTry calls a route that fails asynchronously
                from("direct:viaAsyncCallee")
                        .process(e -> e.setProperty("startThread", Thread.currentThread().getName()))
                        .doTry()
                            .to("direct:failingAsync")
                        .doCatch(IllegalArgumentException.class)
                            .process(e -> e.setProperty("catchThread", Thread.currentThread().getName()))
                            .to("mock:catch")
                            .to("direct:dlcRoute")
                            .to("mock:catchEnd")
                        .end()
                        .to("mock:after");

                // the doCatch block does not call a failing route: the caller continues after the doTry, as before
                from("direct:catchOnly")
                        .doTry()
                            .to("direct:failing")
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                        .end()
                        .to("mock:after");

                // the doCatch block fails itself: the exchange fails with that exception, as before
                from("direct:catchFails")
                        .doTry()
                            .to("direct:failing")
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                            .throwException(new IllegalStateException("in catch"))
                        .end()
                        .to("mock:after");

                // nested: the inner doCatch handles the failure with the route it calls
                from("direct:nestedInnerHandles")
                        .doTry()
                            .doTry()
                                .to("direct:failing")
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                                .to("direct:dlcRoute")
                            .end()
                        .endDoTry()
                        .doCatch(Exception.class)
                            .to("mock:outerCatch")
                        .end()
                        .to("mock:after");

                // nested: the inner doCatch fails, and the outer doCatch calls the route with the dead letter channel
                from("direct:nestedOuterHandles")
                        .doTry()
                            .doTry()
                                .to("direct:failing")
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                                .throwException(new IllegalStateException("in inner catch"))
                            .end()
                        .endDoTry()
                        .doCatch(IllegalStateException.class)
                            .to("mock:outerCatch")
                            .to("direct:dlcRoute")
                        .end()
                        .to("mock:after");

                // the callees fail unhandled (default error handler)
                from("direct:failing").throwException(new IllegalArgumentException("callee"));
                from("direct:failingAsync")
                        .delay(10).asyncDelayed()
                        .throwException(new IllegalArgumentException("async callee"));

                // the routes called from the doCatch, with error handlers of their own
                from("direct:dlcRoute").errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(0))
                        .throwException(new IOException("in catch"));
                from("direct:dlcRouteAsync").errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(0))
                        .delay(10).asyncDelayed()
                        .process(e -> e.setProperty("dlcRouteThread", Thread.currentThread().getName()))
                        .throwException(new IOException("in catch"));
                from("direct:onExceptionRoute")
                        .onException(IOException.class).handled(true).to("mock:handled").end()
                        .throwException(new IOException("in catch"));
                from("direct:redeliveryRoute")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        // the error handler redelivers the step that failed, so count in that step
                        .process(e -> {
                            attempts.incrementAndGet();
                            throw new IOException("in catch");
                        });
                from("direct:continuedRoute")
                        .onException(IOException.class).continued(true).end()
                        .throwException(new IOException("in catch"))
                        .to("mock:continued");
            }
        };
    }

    private Exchange send(String uri) {
        return template.send(uri, e -> e.getMessage().setBody("Hello"));
    }

    private int count(String uri) {
        return getMockEndpoint(uri).getReceivedCounter();
    }

    /**
     * A route called from the doCatch that handles its failure with its error handler stops the caller, as a handled
     * failure of a called route does anywhere else: the rest of the doCatch block and the route after the doTry do not
     * run.
     */
    private void assertHandledInCatch(Exchange out, String handledBy) {
        assertNull(out.getException(), "the route called from the doCatch handled its failure");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count(handledBy), handledBy);
        assertEquals(0, count("mock:catchEnd"), "mock:catchEnd");
        assertEquals(0, count("mock:after"), "mock:after");
    }

    @Test
    void deadLetterChannelOfRouteCalledFromDoCatchAfterUnhandledCallee() {
        assertHandledInCatch(send("direct:viaCallee-dlcRoute"), "mock:dead");
    }

    @Test
    void controlDeadLetterChannelOfRouteCalledFromDoCatchAfterInRouteFailure() {
        assertHandledInCatch(send("direct:inRoute-dlcRoute"), "mock:dead");
    }

    @Test
    void onExceptionOfRouteCalledFromDoCatchAfterUnhandledCallee() {
        assertHandledInCatch(send("direct:viaCallee-onExceptionRoute"), "mock:handled");
    }

    @Test
    void controlOnExceptionOfRouteCalledFromDoCatchAfterInRouteFailure() {
        assertHandledInCatch(send("direct:inRoute-onExceptionRoute"), "mock:handled");
    }

    @Test
    void asynchronousDeadLetterChannelOfRouteCalledFromDoCatchAfterUnhandledCallee() {
        Exchange out = send("direct:viaCallee-dlcRouteAsync");
        assertHandledInCatch(out, "mock:dead");
        assertTrue(out.getProperty("dlcRouteThread", String.class).contains("Delay"),
                "the route called from the doCatch ran on the delayer's thread: " + out.getProperty("dlcRouteThread"));
    }

    @Test
    void controlAsynchronousDeadLetterChannelOfRouteCalledFromDoCatchAfterInRouteFailure() {
        assertHandledInCatch(send("direct:inRoute-dlcRouteAsync"), "mock:dead");
    }

    private void assertContinuedInCatch(Exchange out) {
        assertNull(out.getException(), "the route called from the doCatch continued its failure");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:continued"), "mock:continued");
        assertEquals(1, count("mock:catchEnd"), "mock:catchEnd");
        assertEquals(1, count("mock:after"), "mock:after");
    }

    @Test
    void continuedOnExceptionOfRouteCalledFromDoCatchAfterUnhandledCallee() {
        assertContinuedInCatch(send("direct:viaCallee-continuedRoute"));
    }

    @Test
    void controlContinuedOnExceptionOfRouteCalledFromDoCatchAfterInRouteFailure() {
        assertContinuedInCatch(send("direct:inRoute-continuedRoute"));
    }

    @Test
    void asynchronousCallee() {
        Exchange out = send("direct:viaAsyncCallee");
        assertHandledInCatch(out, "mock:dead");
        // the callee failed on the delayer's thread, so the doCatch ran on another thread than the route started on
        assertNotEquals(out.getProperty("startThread"), out.getProperty("catchThread"));
    }

    @Test
    void callerContinuesAfterTheDoTryWhenTheDoCatchDoesNotFail() {
        Exchange out = send("direct:catchOnly");
        assertNull(out.getException());
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:after"), "mock:after");
        // failure handled is set again after the doCatch block, as before; the "not handled" of the caught failure is
        // not, as when the doTry failed in this route
        assertTrue(out.getExchangeExtension().isFailureHandled(), "failure handled");
        assertNull(out.getExchangeExtension().getErrorHandlerHandled(), "handled by the error handler");
    }

    @Test
    void doCatchThatFailsFailsTheExchangeAsBefore() {
        Exchange out = send("direct:catchFails");
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("in catch", out.getException().getMessage());
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(0, count("mock:after"), "mock:after");
        assertTrue(out.getExchangeExtension().isFailureHandled(), "failure handled");
        assertNull(out.getExchangeExtension().getErrorHandlerHandled(), "handled by the error handler");
    }

    /**
     * The error handler of the route called from the doCatch runs in full also when it does not handle the failure: it
     * redelivers, and the exchange then fails with that route's exception.
     */
    private void assertRedeliveredInCatch(Exchange out) {
        assertInstanceOf(IOException.class, out.getException());
        assertEquals("in catch", out.getException().getMessage());
        assertEquals(3, attempts.get(), "the delivery and 2 redeliveries");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(0, count("mock:catchEnd"), "mock:catchEnd");
        assertEquals(0, count("mock:after"), "mock:after");
    }

    @Test
    void redeliveryOfRouteCalledFromDoCatchAfterUnhandledCallee() {
        assertRedeliveredInCatch(send("direct:viaCallee-redeliveryRoute"));
    }

    @Test
    void controlRedeliveryOfRouteCalledFromDoCatchAfterInRouteFailure() {
        assertRedeliveredInCatch(send("direct:inRoute-redeliveryRoute"));
    }

    private void assertHandledByOnException(Exchange out) {
        assertNull(out.getException(), "the onException handled the failure");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:onExceptionEnd"), "mock:onExceptionEnd");
    }

    @Test
    void doTryInHandledOnExceptionCallsRouteThatFails() {
        // the caught failure of the callee must not come back when the onException completes
        assertHandledByOnException(send("direct:onExceptionViaCallee"));
    }

    @Test
    void controlDoTryInHandledOnExceptionFailsInRoute() {
        assertHandledByOnException(send("direct:onExceptionInRoute"));
    }

    private void assertSecondDoTry(Exchange out) {
        assertNull(out.getException(), "the route called from the second doCatch handled its failure");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:secondCatch"), "mock:secondCatch");
        assertEquals(1, count("mock:dead"), "mock:dead");
        assertEquals(0, count("mock:after"), "mock:after");
    }

    @Test
    void secondDoTryAfterCaughtCalleeFailure() {
        // the failure handled mark that is set again after the first doCatch is cleared again by the second doCatch
        assertSecondDoTry(send("direct:secondDoTryViaCallee"));
    }

    @Test
    void controlSecondDoTryAfterCaughtInRouteFailure() {
        assertSecondDoTry(send("direct:secondDoTryInRoute"));
    }

    private void assertSplitAfterDoCatch(Exchange out) {
        assertNull(out.getException(), "the route called from the split handled its failure");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:dead"), "mock:dead");
        assertEquals(1, count("mock:after"), "mock:after");
    }

    @Test
    void splitAfterCaughtCalleeFailure() {
        assertSplitAfterDoCatch(send("direct:splitViaCallee"));
    }

    @Test
    void controlSplitAfterCaughtInRouteFailure() {
        assertSplitAfterDoCatch(send("direct:splitInRoute"));
    }

    @Test
    void nestedDoTryInnerDoCatchCallsTheRouteThatHandlesItsFailure() {
        Exchange out = send("direct:nestedInnerHandles");
        assertNull(out.getException(), "the route called from the inner doCatch handled its failure");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:dead"), "mock:dead");
        assertEquals(0, count("mock:outerCatch"), "mock:outerCatch");
        assertEquals(0, count("mock:after"), "mock:after");
    }

    @Test
    void nestedDoTryOuterDoCatchCallsTheRouteThatHandlesItsFailure() {
        Exchange out = send("direct:nestedOuterHandles");
        assertNull(out.getException(), "the route called from the outer doCatch handled its failure");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:outerCatch"), "mock:outerCatch");
        assertEquals(1, count("mock:dead"), "mock:dead");
        assertEquals(0, count("mock:after"), "mock:after");
    }
}
