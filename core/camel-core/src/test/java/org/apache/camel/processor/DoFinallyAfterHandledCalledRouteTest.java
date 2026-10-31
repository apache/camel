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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The doTry calls a route whose error handler handles a failure (dead letter channel or a handled onException). The
 * doFinally block must run all its steps. The caller does not continue routing after the doTry, as before
 * (CAMEL-19441).
 */
class DoFinallyAfterHandledCalledRouteTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private Exchange send(String errorHandler, boolean calleeFails) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                switch (errorHandler) {
                    case "dlc" -> errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(0));
                    case "dlcAsync" -> errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1)
                            .redeliveryDelay(10).asyncDelayedRedelivery());
                    default -> onException(IllegalArgumentException.class).handled(true).to("mock:dead");
                }

                from("direct:start")
                        .process(e -> e.setProperty("startThread", Thread.currentThread().getName()))
                        .doTry()
                            .to("direct:callee")
                            .to("mock:afterCallee")
                        .doCatch(Exception.class)
                            .to("mock:catch")
                        .doFinally()
                            .to("mock:f1")
                            .to("mock:f2")
                            .setHeader("f3", constant("yes"))
                            .process(e -> e.setProperty("finallyThread", Thread.currentThread().getName()))
                            .to("mock:f4")
                        .end()
                        .to("mock:end");

                if (calleeFails) {
                    from("direct:callee").setBody(constant("in callee"))
                            .throwException(new IllegalArgumentException("Forced"));
                } else {
                    from("direct:callee").setBody(constant("in callee"));
                }
            }
        });
        context.start();

        return assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> template.send("direct:start", e -> e.getMessage().setBody("Hello")), "the send did not complete");
    }

    private void assertAllFinallySteps(Exchange out) {
        assertEquals(1, getMockEndpoint("mock:f1").getReceivedCounter(), "mock:f1");
        assertEquals(1, getMockEndpoint("mock:f2").getReceivedCounter(), "mock:f2");
        assertEquals(1, getMockEndpoint("mock:f4").getReceivedCounter(), "mock:f4");
        assertEquals("yes", out.getMessage().getHeader("f3"), "the header set by the doFinally");
    }

    private void assertHandledByCalledRoute(Exchange out) {
        assertNull(out.getException(), "the called route handled the failure");
        assertEquals(1, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        assertEquals(0, getMockEndpoint("mock:afterCallee").getReceivedCounter(), "mock:afterCallee");
        assertEquals(0, getMockEndpoint("mock:catch").getReceivedCounter(), "there is no exception to catch");
        // the caller does not continue routing after the doTry (CAMEL-19441)
        assertEquals(0, getMockEndpoint("mock:end").getReceivedCounter(), "mock:end");
    }

    @Test
    void deadLetterChannel() throws Exception {
        Exchange out = send("dlc", true);
        assertHandledByCalledRoute(out);
        assertAllFinallySteps(out);
    }

    @Test
    void deadLetterChannelAsyncDelayedRedelivery() throws Exception {
        Exchange out = send("dlcAsync", true);
        assertHandledByCalledRoute(out);
        assertAllFinallySteps(out);
        // the redelivery, and so the doFinally block, ran on another thread than the one that started the route
        assertNotEquals(out.getProperty("startThread"), out.getProperty("finallyThread"));
    }

    @Test
    void handledOnException() throws Exception {
        Exchange out = send("handled", true);
        assertHandledByCalledRoute(out);
        assertAllFinallySteps(out);
    }

    @Test
    void controlCalledRouteDoesNotFail() throws Exception {
        Exchange out = send("dlc", false);
        assertNull(out.getException());
        assertEquals(0, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        assertEquals(1, getMockEndpoint("mock:afterCallee").getReceivedCounter(), "mock:afterCallee");
        assertAllFinallySteps(out);
        assertEquals(1, getMockEndpoint("mock:end").getReceivedCounter(), "mock:end");
    }

    @Test
    void routeCalledFromTheDoFinallyHandlesItsOwnFailure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(0));

                from("direct:start")
                        .doTry()
                            .to("direct:callee")
                        .doFinally()
                            .to("mock:f1")
                            .to("direct:callee2")
                            .to("mock:f2")
                        .end()
                        .to("mock:end");

                from("direct:callee").throwException(new IllegalArgumentException("Forced"));
                from("direct:callee2").throwException(new IllegalStateException("Forced in doFinally"));
            }
        });
        context.start();

        Exchange out = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> template.send("direct:start", e -> e.getMessage().setBody("Hello")), "the send did not complete");
        // the dead letter channel of the route called from the doFinally handles that failure too, as when the doTry
        // did not fail before
        assertNull(out.getException(), "the called routes handled their failures");
        assertEquals(2, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        assertEquals(1, getMockEndpoint("mock:f1").getReceivedCounter(), "mock:f1");
        // a handled failure of a called route stops the caller, also in the doFinally block
        assertEquals(0, getMockEndpoint("mock:f2").getReceivedCounter(), "mock:f2");
        assertEquals(0, getMockEndpoint("mock:end").getReceivedCounter(), "mock:end");
    }

    @Test
    void routeCalledFromTheDoFinallyContinuesItsOwnFailure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(0));
                onException(NumberFormatException.class).continued(true);

                from("direct:start")
                        .doTry()
                            .to("direct:callee")
                        .doFinally()
                            .to("mock:f1")
                            .to("direct:callee2")
                            .to("mock:f2")
                        .end()
                        .to("mock:end");

                from("direct:callee").throwException(new IllegalArgumentException("Forced"));
                from("direct:callee2").throwException(new NumberFormatException("Forced in doFinally"))
                        .to("mock:callee2End");
            }
        });
        context.start();

        Exchange out = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> template.send("direct:start", e -> e.getMessage().setBody("Hello")), "the send did not complete");
        assertNull(out.getException(), "the called routes handled or continued their failures");
        assertEquals(1, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        // the onException of the route called from the doFinally continues its failure
        assertEquals(1, getMockEndpoint("mock:callee2End").getReceivedCounter(), "mock:callee2End");
        assertEquals(1, getMockEndpoint("mock:f1").getReceivedCounter(), "mock:f1");
        assertEquals(1, getMockEndpoint("mock:f2").getReceivedCounter(), "mock:f2");
        // the caller does not continue routing after the doTry (CAMEL-19441)
        assertEquals(0, getMockEndpoint("mock:end").getReceivedCounter(), "mock:end");
    }
}
