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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * After a doCatch caught the failure of a route that the doTry called and that failed unhandled, the exchange carries
 * no failure handled mark, as when the doTry failed in the route itself: the error handlers that come later handle new
 * failures, and the readers of the mark at the end of the exchange see a message that was processed. Each case runs
 * twice: with the doTry calling the failing route ("viaCallee") and with the doTry failing in the route ("inRoute"),
 * with the same expected outcome.
 */
class DoCatchFailureHandledMarkTest extends ContextTestSupport {

    private final IdempotentRepository viaCalleeRepository = MemoryIdempotentRepository.memoryIdempotentRepository();
    private final IdempotentRepository inRouteRepository = MemoryIdempotentRepository.memoryIdempotentRepository();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                for (String variant : new String[] { "viaCallee", "inRoute" }) {
                    // a later failure in the calling route, after the doTry
                    failingTry(from("direct:laterFailure-" + variant)
                            .onException(IOException.class).handled(true).to("mock:callerHandled").end(), variant)
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                            .end()
                            .throwException(new IOException("after the doTry"));

                    // a route called from the doFinally after such a doCatch
                    failingTry(from("direct:finallyAfterCatch-" + variant), variant)
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                            .doFinally()
                                .to("direct:dlcRoute")
                            .end();

                    // the doCatch throws, inside a route called by a route with a dead letter channel
                    from("direct:outer-" + variant)
                            .errorHandler(deadLetterChannel("mock:outerDead").redeliveryDelay(0))
                            .to("direct:inner-" + variant);
                    failingTry(from("direct:inner-" + variant), variant)
                            .doCatch(IllegalArgumentException.class)
                                .throwException(new IllegalStateException("in catch"))
                            .end();

                    // a route called with a plain to after the doTry
                    failingTry(from("direct:plainTo-" + variant), variant)
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                            .end()
                            .to("direct:dlcRoute")
                            .to("mock:after");

                    // an idempotent consumer: the message was processed (the doCatch handled its failure), so its id
                    // is confirmed and a duplicate is skipped
                    failingTry(from("direct:idempotent-" + variant)
                            .idempotentConsumer(header("id"), repository(variant)), variant)
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                            .end()
                            .end();

                    // the advice of the upgrade guide: the doCatch block throws, the exchange fails, and the
                    // idempotent consumer removes the id (removeOnFailure, the default), so the message is processed
                    // when it comes again
                    failingTry(from("direct:rethrow-" + variant)
                            .idempotentConsumer(header("id"), repository(variant)), variant)
                            .doCatch(IllegalArgumentException.class)
                                .to("mock:catch")
                                .throwException(new IllegalStateException("rethrown"))
                            .end()
                            .end();
                }

                // the callee fails unhandled (default error handler)
                from("direct:failing").throwException(new IllegalArgumentException("callee"));

                // the doTry fails with an exchange marked as handled by an error handler (both marks, as an error
                // handler that handled a failure sets them). Such an exchange rarely gets to a doCatch (for example an
                // aggregation strategy that combines a part whose failure was handled with a part that failed), so the
                // marks are set directly
                from("direct:markedFailure")
                        .doTry()
                        .process(e -> {
                            e.getExchangeExtension().setFailureHandled(true);
                            e.getExchangeExtension().setErrorHandlerHandled(true);
                            throw new IllegalArgumentException("marked");
                        })
                        .doCatch(IllegalArgumentException.class)
                        .to("mock:catch")
                        .end();

                from("direct:dlcRoute").errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(0))
                        .throwException(new IOException("in called route"));
            }
        };
    }

    // a doTry that calls a route that fails unhandled (viaCallee), or that fails in the route itself (inRoute)
    private static TryDefinition failingTry(ProcessorDefinition<?> parent, String variant) {
        TryDefinition doTry = parent.doTry();
        if ("viaCallee".equals(variant)) {
            return doTry.to("direct:failing");
        }
        return doTry.throwException(new IllegalArgumentException("in route"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void laterFailureIsHandledByTheOnException(String variant) {
        Exchange out = template.send("direct:laterFailure-" + variant, e -> e.getMessage().setBody("hello"));
        assertNull(out.getException(), "the onException of the calling route must handle the later failure");
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:callerHandled"), "mock:callerHandled");
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void routeCalledFromTheDoFinallyHandlesItsFailure(String variant) {
        Exchange out = template.send("direct:finallyAfterCatch-" + variant, e -> e.getMessage().setBody("hello"));
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:dead"), "the dead letter channel of the route called from the doFinally");
        assertNull(out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void failureOfTheDoCatchGoesToTheDeadLetterChannelOfTheOuterRoute(String variant) {
        Exchange out = template.send("direct:outer-" + variant, e -> e.getMessage().setBody("hello"));
        assertEquals(1, count("mock:outerDead"), "the dead letter channel of the outer route");
        assertNull(out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void failureOfTheDoCatchIsNotFailureHandled(String variant) {
        // the route whose doCatch throws, called directly: its default error handler does not handle the new failure
        Exchange out = template.send("direct:inner-" + variant, e -> e.getMessage().setBody("hello"));
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertFalse(out.getExchangeExtension().isFailureHandled(), "failure handled");
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void routeCalledLaterHandlesItsFailure(String variant) {
        Exchange out = template.send("direct:plainTo-" + variant, e -> e.getMessage().setBody("hello"));
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertEquals(1, count("mock:dead"), "the dead letter channel of the route called after the doTry");
        // the called route handled its failure, so the calling route does not continue
        assertEquals(0, count("mock:after"), "mock:after");
        assertNull(out.getException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void processedMessageIsConfirmedByTheIdempotentConsumer(String variant) {
        Exchange out = template.send("direct:idempotent-" + variant, e -> e.getMessage().setHeader("id", "m1"));
        assertNull(out.getException());
        assertFalse(out.getExchangeExtension().isFailureHandled(), "failure handled");
        assertTrue(repository(variant).contains("m1"), "the id of a processed message is kept");

        template.send("direct:idempotent-" + variant, e -> e.getMessage().setHeader("id", "m1"));
        assertEquals(1, count("mock:catch"), "a duplicate is skipped");
    }

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void rethrowInTheDoCatchRemovesTheIdempotentId(String variant) {
        Exchange out = template.send("direct:rethrow-" + variant, e -> e.getMessage().setHeader("id", "m2"));
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertFalse(repository(variant).contains("m2"), "the id of a failed message is removed");

        template.send("direct:rethrow-" + variant, e -> e.getMessage().setHeader("id", "m2"));
        assertEquals(2, count("mock:catch"), "the message is processed again");
    }

    @Test
    void doCatchLeavesNoHandledMarkOfTheCaughtFailure() {
        // the doCatch handled the failure: the exchange carries neither mark of it, and never one without the other
        Exchange out = template.send("direct:markedFailure", e -> e.getMessage().setBody("hello"));
        assertNull(out.getException());
        assertEquals(1, count("mock:catch"), "mock:catch");
        assertFalse(out.getExchangeExtension().isFailureHandled(), "failure handled");
        assertFalse(out.getExchangeExtension().isErrorHandlerHandledSet(), "handled by the error handler");
    }

    private IdempotentRepository repository(String variant) {
        return "viaCallee".equals(variant) ? viaCalleeRepository : inRouteRepository;
    }

    private int count(String uri) {
        return getMockEndpoint(uri).getReceivedCounter();
    }
}
