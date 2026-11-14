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
package org.apache.camel.impl.engine;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * An Error that is not a VirtualMachineError, thrown by the result processor of a send of a ProducerTemplate, or by a
 * producer in a transacted send, fails the exchange, as an exception does, instead of skipping the end of the unit of
 * work of the send (and, for an asynchronous send, its answer).
 */
class SharedCamelInternalProcessorThrowsErrorTest extends ContextTestSupport {

    private static final String BY_THE_RESULT_PROCESSOR = "Forced by the result processor";
    private static final String BY_THE_PRODUCER = "Forced by the producer";

    private final Processor throwingError = exchange -> {
        throw new AssertionError(BY_THE_RESULT_PROCESSOR);
    };

    @Test
    void testResultProcessorThrowsErrorFailsTheAsyncSend() throws Exception {
        // as asyncRequestBody(uri, body, type) does, with a result processor that converts the body (here the answer
        // comes on the same thread, and the future completed with the Error before)
        Exchange out = ((DefaultProducerTemplate) template).asyncSend(context.getEndpoint("direct:start"),
                ExchangePattern.InOut, e -> e.getMessage().setBody("Hello"), throwingError).get(10, TimeUnit.SECONDS);

        assertFailedWithTheError(out, BY_THE_RESULT_PROCESSOR);
    }

    @Test
    void testResultProcessorThrowsErrorFailsTheAsyncSendAnsweredOnAnotherThread() throws Exception {
        // the producer answers on another thread: before, the Error was thrown on that thread, and the future never
        // completed
        Exchange out = ((DefaultProducerTemplate) template).asyncSend(context.getEndpoint("fake:answerOnAnotherThread"),
                ExchangePattern.InOut, e -> e.getMessage().setBody("Hello"), throwingError).get(10, TimeUnit.SECONDS);

        assertFailedWithTheError(out, BY_THE_RESULT_PROCESSOR);
    }

    @Test
    void testResultProcessorThrowsErrorFailsTheSend() {
        Exchange out = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> template.send(context.getEndpoint("direct:start"), ExchangePattern.InOut,
                        e -> e.getMessage().setBody("Hello"), throwingError));

        assertFailedWithTheError(out, BY_THE_RESULT_PROCESSOR);
    }

    @Test
    void testResultProcessorThrowsExceptionFailsTheSend() {
        // control: an exception was already set on the exchange
        IllegalStateException forced = new IllegalStateException("Forced");
        Exchange out = template.send(context.getEndpoint("direct:start"), ExchangePattern.InOut,
                e -> e.getMessage().setBody("Hello"), e -> {
                    throw forced;
                });

        assertSame(forced, out.getException());
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    void testProducerThrowsErrorInATransactedSendFailsTheExchange() {
        Exchange out = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> template.send("fake:throw", e -> e.getExchangeExtension().setTransacted(true)));

        assertFailedWithTheError(out, BY_THE_PRODUCER);
    }

    @Test
    void testProducerThrowsErrorFailsTheExchange() {
        // control: not transacted, the adapter of the producer already failed the exchange (CAMEL-26741)
        Exchange out = template.send("fake:throw", e -> e.getMessage().setBody("Hello"));

        assertFailedWithTheError(out, BY_THE_PRODUCER);
    }

    private void assertFailedWithTheError(Exchange out, String message) {
        Throwable cause = assertInstanceOf(CamelExecutionException.class, out.getException()).getCause();
        assertEquals(message, assertInstanceOf(AssertionError.class, cause).getMessage());
        // the unit of work of the send is done
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, context.getInflightRepository().size()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("fake", new FakeComponent());

                from("direct:start").to("mock:result");
            }
        };
    }

    private static final class FakeComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new FakeEndpoint(uri, this);
        }
    }

    private static final class FakeEndpoint extends DefaultEndpoint {
        FakeEndpoint(String uri, Component component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            if (getEndpointUri().endsWith("answerOnAnotherThread")) {
                // answers on another thread
                return new DefaultAsyncProducer(this) {
                    @Override
                    public boolean process(Exchange exchange, AsyncCallback callback) {
                        new Thread(() -> callback.done(false), "answer").start();
                        return false;
                    }
                };
            }
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    throw new AssertionError(BY_THE_PRODUCER);
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }
}
