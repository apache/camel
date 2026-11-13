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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.TryDefinition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CAMEL-26857: after a doCatch caught the failure of a route that the doTry called and that was redelivered before it
 * failed, a later error handler gets its own redeliveries, as when the doTry failed in the route itself. The redelivery
 * headers of the called route stay readable in the doCatch block. Each case runs twice: with the doTry calling the
 * failing route ("viaCallee") and with the doTry failing in the route ("inRoute", the control), with the same expected
 * outcome.
 */
class DoCatchRedeliveryOverTest extends ContextTestSupport {

    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void laterFailureInTheCallingRouteGetsItsOwnRedeliveries(String variant) throws Exception {
        MockEndpoint catchMock = getMockEndpoint("mock:catch");
        catchMock.expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:laterFailure-" + variant, "Hello");

        MockEndpoint.assertIsSatisfied(context);
        // the step after the doTry: the first call and the 3 redeliveries of the calling route's error handler
        assertEquals(4, calls("later-" + variant));
        assertCatchSawTheRedeliveriesOfTheCallee(catchMock, variant);
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void routeCalledFromTheDoCatchGetsItsOwnRedeliveries(String variant) throws Exception {
        MockEndpoint catchMock = getMockEndpoint("mock:catch");
        catchMock.expectedMessageCount(1);
        getMockEndpoint("mock:recovered").expectedMessageCount(1);

        template.sendBody("direct:catchCalls-" + variant, "Hello");

        MockEndpoint.assertIsSatisfied(context);
        // sendBody returned: the exchange is done, and did not go to the dead letter channel
        assertEquals(0, getMockEndpoint("mock:dead").getReceivedCounter());
        // the route called from the doCatch fails 3 times and succeeds at its third redelivery
        assertEquals(4, calls("recovering-" + variant));
        assertCatchSawTheRedeliveriesOfTheCallee(catchMock, variant);
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void callingRouteGetsItsOwnRedeliveriesWhenTheDoCatchThrows(String variant) throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:caller-" + variant, "Hello");

        MockEndpoint.assertIsSatisfied(context);
        // the route with the doTry fails (its doCatch throws), so the calling route redelivers its call: the doCatch
        // runs at the first call and at each of the 3 redeliveries
        assertEquals(4, calls("catchBlock-" + variant));
    }

    private void assertCatchSawTheRedeliveriesOfTheCallee(MockEndpoint catchMock, String variant) {
        Exchange caught = catchMock.getReceivedExchanges().get(0);
        if ("viaCallee".equals(variant)) {
            // the called route: the first call and its 2 redeliveries
            assertEquals(3, calls("callee-" + variant));
            assertEquals(Boolean.TRUE, caught.getMessage().getHeader(Exchange.REDELIVERED));
            assertEquals(2, caught.getMessage().getHeader(Exchange.REDELIVERY_COUNTER));
        } else {
            assertEquals(1, calls("callee-" + variant));
            assertNull(caught.getMessage().getHeader(Exchange.REDELIVERY_COUNTER));
        }
    }

    private int calls(String name) {
        return calls.computeIfAbsent(name, k -> new AtomicInteger()).get();
    }

    private int call(String name) {
        return calls.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the error handler of the calling routes
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(3).redeliveryDelay(0));

                for (String variant : new String[] { "viaCallee", "inRoute" }) {
                    // a failure after the doTry, in the calling route
                    failingTry(from("direct:laterFailure-" + variant), variant)
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                            .end()
                            .process(e -> {
                                call("later-" + variant);
                                throw new IllegalStateException("after the doTry");
                            });

                    // a route called from the doCatch block, which recovers at its third redelivery
                    failingTry(from("direct:catchCalls-" + variant), variant)
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                                .to("direct:recovering-" + variant)
                            .end()
                            .to("mock:recovered");
                    from("direct:recovering-" + variant)
                            .process(e -> {
                                if (call("recovering-" + variant) < 4) {
                                    throw new IllegalStateException("not yet");
                                }
                            });

                    // a route that calls a route whose doCatch throws
                    from("direct:caller-" + variant)
                            .to("direct:catchThrows-" + variant);
                    failingTry(from("direct:catchThrows-" + variant), variant)
                            .doCatch(IllegalArgumentException.class)
                                .process(e -> call("catchBlock-" + variant))
                                .throwException(new IllegalStateException("in the doCatch"))
                            .end();

                    // the called route: redelivered twice by its own error handler, then fails unhandled
                    from("direct:callee-" + variant)
                            .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                            .process(e -> {
                                call("callee-" + variant);
                                throw new IllegalArgumentException("callee fails");
                            });
                }
            }

            private TryDefinition failingTry(RouteDefinition route, String variant) {
                if ("viaCallee".equals(variant)) {
                    return route.doTry().to("direct:callee-" + variant);
                }
                return route.doTry().process(e -> {
                    call("callee-" + variant);
                    throw new IllegalArgumentException("fails in the route");
                });
            }
        };
    }
}
