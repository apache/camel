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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The exception instance a throwException step is configured with is shared by all the messages, so the error handlers
 * and the EIPs must not add other exceptions of each message to it as suppressed.
 */
public class ThrowExceptionSharedInstanceTest extends ContextTestSupport {

    private final IllegalStateException translated = new IllegalStateException("Translated");
    private final IllegalStateException afterCatch = new IllegalStateException("After catch");
    private final IOException handledThrows = new IOException("Handled throws");
    private final IOException finallyThrows = new IOException("Finally throws");
    private final IOException onCompletionThrows = new IOException("OnCompletion throws");
    private final IOException multicastThrows = new IOException("Multicast throws");
    private final IllegalStateException onCopy = new IllegalStateException("On copy");

    @Test
    public void testOnExceptionThrowsInstance() {
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:onException", e -> {
            });
            assertSame(translated, out.getException());
        }
        assertEquals(0, translated.getSuppressed().length);
    }

    @Test
    public void testThrowsInstanceAfterDoCatch() {
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:afterCatch", e -> {
            });
            assertSame(afterCatch, out.getException());
        }
        assertEquals(0, afterCatch.getSuppressed().length);
    }

    @Test
    public void testHandledPredicateThrows() {
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:handledThrows", e -> {
            });
            assertSame(handledThrows, out.getException());
        }
        assertEquals(0, handledThrows.getSuppressed().length);
    }

    @Test
    public void testDoFinallyThrows() {
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:finallyThrows", e -> {
            });
            assertSame(finallyThrows, out.getException());
        }
        assertEquals(0, finallyThrows.getSuppressed().length);
    }

    @Test
    public void testOnCompletionThrows() {
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:onCompletionThrows", e -> {
            });
            assertSame(onCompletionThrows, out.getException());
        }
        assertEquals(0, onCompletionThrows.getSuppressed().length);
    }

    @Test
    public void testMulticastCompletionThrows() {
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:multicastThrows", e -> {
            });
            assertSame(multicastThrows, out.getException());
        }
        assertEquals(0, multicastThrows.getSuppressed().length);
    }

    @Test
    public void testOnExceptionThrowsInstanceOnCopy() {
        // the instance is thrown on a copy of the exchange (by the topic load balancer), and then set on the exchange
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:onCopy", e -> {
            });
            assertSame(onCopy, out.getException());
        }
        assertEquals(0, onCopy.getSuppressed().length);
    }

    @Test
    public void testOnExceptionThrowsTypeAndMessage() {
        Exchange first = template.send("direct:typeAndMessage", e -> {
        });
        Exchange second = template.send("direct:typeAndMessage", e -> {
        });
        assertNotSame(first.getException(), second.getException());
        for (Exchange out : new Exchange[] { first, second }) {
            Exception e = assertInstanceOf(IllegalStateException.class, out.getException());
            assertEquals("Translated", e.getMessage());
            assertEquals(1, e.getSuppressed().length);
            assertInstanceOf(IOException.class, e.getSuppressed()[0]);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:onException")
                        .onException(IOException.class).throwException(translated).end()
                        .throwException(IOException.class, "Failure of ${exchangeId}");

                from("direct:afterCatch")
                        .doTry()
                            .throwException(IOException.class, "Failure of ${exchangeId}")
                        .doCatch(IOException.class)
                            .to("mock:catch")
                        .end()
                        .throwException(afterCatch);

                from("direct:typeAndMessage")
                        .onException(IOException.class).throwException(IllegalStateException.class, "Translated").end()
                        .throwException(IOException.class, "Failure of ${exchangeId}");

                from("direct:handledThrows")
                        .onException(IOException.class).handled(e -> {
                            throw new IllegalArgumentException("Handled failed of " + e.getExchangeId());
                        }).end()
                        .throwException(handledThrows);

                from("direct:finallyThrows")
                        .doTry()
                            .throwException(finallyThrows)
                        .doFinally()
                            .throwException(IllegalStateException.class, "Finally failed of ${exchangeId}")
                        .end();

                from("direct:onCompletionThrows")
                        .onCompletion().onFailureOnly().modeBeforeConsumer()
                            .throwException(IllegalStateException.class, "OnCompletion failed of ${exchangeId}")
                        .end()
                        .throwException(onCompletionThrows);

                from("direct:multicastThrows")
                        .multicast(new FailingOnCompletion())
                            .to("direct:multicastBranch")
                        .end();
                from("direct:multicastBranch")
                        .throwException(multicastThrows);

                from("direct:onCopy")
                        .onException(IOException.class).to("direct:topic").end()
                        .throwException(IOException.class, "Failure of ${exchangeId}");
                from("direct:topic").errorHandler(noErrorHandler())
                        .loadBalance().topic().to("direct:onCopyBranch");
                from("direct:onCopyBranch")
                        .throwException(onCopy);
            }
        };
    }

    private static final class FailingOnCompletion implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            return oldExchange != null ? oldExchange : newExchange;
        }

        @Override
        public void onCompletion(Exchange exchange, Exchange inputExchange) {
            throw new IllegalStateException("Completion failed of " + inputExchange.getExchangeId());
        }
    }
}
