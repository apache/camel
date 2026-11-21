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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The error handler of a calling route redelivers an exchange that failed inside the idempotent consumer of a sub route
 * without an error handler. The redelivered exchange enters the idempotent consumer again, while the message id it
 * added on the first attempt is still in the repository (completionEager=false removes it when the exchange is done).
 * It must not be dropped as a duplicate of itself: the message would never be processed, the exchange would complete
 * successfully, and the message id would stay in the repository.
 */
class IdempotentConsumerRedeliveryTest extends ContextTestSupport {

    private final IdempotentRepository repository = new KeyValueIdempotentRepository();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));

                from("direct:start").to("direct:sub");
                from("direct:sub").errorHandler(noErrorHandler())
                        .idempotentConsumer(header("messageId"), repository)
                            .process(e -> failFirstAttempts())
                            .to("mock:result")
                        .end()
                        .to("mock:after");

                // the called route keeps its error handler, but an exception that escapes its doTry block is dealt with
                // by the error handler of the calling route, whose redelivery calls the whole route again
                from("direct:doTry").to("direct:subDoTry");
                from("direct:subDoTry")
                        .doTry()
                            .idempotentConsumer(header("messageId"), repository)
                                .process(e -> failFirstAttempts())
                                .to("mock:result")
                            .end()
                        .endDoTry()
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:caught")
                        .end();

                // a doCatch of the calling route sends the failed exchange to the sub route again
                from("direct:catchAndRetry").errorHandler(noErrorHandler())
                        .doTry()
                            .to("direct:sub")
                        .doCatch(IllegalStateException.class)
                            .to("direct:sub")
                        .end();

                from("direct:failAfter").to("direct:subFailAfter");
                from("direct:subFailAfter").errorHandler(noErrorHandler())
                        .idempotentConsumer(header("messageId"), repository)
                            .to("mock:result")
                        .end()
                        .process(e -> failFirstAttempts())
                        .to("mock:after");

                from("direct:routedDuplicate").to("direct:subRoutedDuplicate");
                from("direct:subRoutedDuplicate").errorHandler(noErrorHandler())
                        .idempotentConsumer(header("messageId"), repository).skipDuplicate(false)
                            .choice()
                                .when(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                                    .to("mock:duplicate")
                                .otherwise()
                                    .process(e -> failFirstAttempts())
                                    .to("mock:result")
                            .end()
                        .end();

                from("direct:keepOnFailure").to("direct:subKeepOnFailure");
                from("direct:subKeepOnFailure").errorHandler(noErrorHandler())
                        .idempotentConsumer(header("messageId"), repository).removeOnFailure(false)
                            .process(e -> failFirstAttempts())
                            .to("mock:result")
                        .end();

                from("direct:completionEager").to("direct:subCompletionEager");
                from("direct:subCompletionEager").errorHandler(noErrorHandler())
                        .idempotentConsumer(header("messageId"), repository).completionEager(true)
                            .process(e -> failFirstAttempts())
                            .to("mock:result")
                        .end();

                // another exchange with the same message id arrives while the first one waits for its redelivery
                from("direct:other").errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).redeliveryDelay(0)
                        .onRedelivery(e -> template.send("direct:sub", o -> {
                            o.getIn().setBody("other");
                            o.getIn().setHeader("messageId", "1");
                        })))
                        .to("direct:sub");
            }
        };
    }

    private void failFirstAttempts() {
        attempts.incrementAndGet();
        if (failures.getAndDecrement() > 0) {
            throw new IllegalStateException("Forced failure");
        }
    }

    @Test
    void testRedeliveredExchangeIsProcessed() throws Exception {
        failures.set(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("one");
        getMockEndpoint("mock:after").expectedBodiesReceived("one");
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = send("direct:start");

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
        assertEquals(2, attempts.get(), "The redelivered exchange should run the idempotent consumer block again");
        assertTrue(repository.contains("1"));
    }

    @Test
    void testExhaustedRedeliveriesRemoveMessageId() throws Exception {
        failures.set(3);
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedBodiesReceived("one");

        send("direct:start");

        assertMockEndpointsSatisfied();
        assertEquals(3, attempts.get(), "Every redelivery should run the idempotent consumer block again");
        assertFalse(repository.contains("1"), "The message id of a failed exchange should be removed");
    }

    @Test
    void testExceptionEscapingDoTryOfCalledRoute() throws Exception {
        failures.set(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("one");
        getMockEndpoint("mock:caught").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = send("direct:doTry");

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
        assertEquals(2, attempts.get(), "The redelivered exchange should run the idempotent consumer block again");
        assertTrue(repository.contains("1"));
    }

    @Test
    void testDoCatchOfCallingRouteSendsExchangeAgain() throws Exception {
        failures.set(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("one");
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = send("direct:catchAndRetry");

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
        assertEquals(2, attempts.get(), "The exchange sent again should run the idempotent consumer block again");
        assertTrue(repository.contains("1"));
    }

    @Test
    void testRedeliveryAfterTheBlockDoesNotProcessAgain() throws Exception {
        // the block succeeded, a step after it failed: the redelivery skips the block, as the message was processed
        failures.set(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("one");
        getMockEndpoint("mock:after").expectedBodiesReceived("one");
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = send("direct:failAfter");

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
        assertTrue(repository.contains("1"));
    }

    @Test
    void testRedeliveredExchangeIsNotRoutedAsDuplicate() throws Exception {
        failures.set(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("one");
        getMockEndpoint("mock:duplicate").expectedMessageCount(0);

        Exchange out = send("direct:routedDuplicate");

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
        assertTrue(repository.contains("1"));
    }

    @Test
    void testOtherExchangeIsStillDuplicateWhileFirstIsRedelivered() throws Exception {
        failures.set(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("one");

        Exchange out = send("direct:other");

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
        assertEquals(2, attempts.get(), "Only the first exchange should run the block, twice");
        assertTrue(repository.contains("1"));
    }

    @Test
    void testRedeliveryKeepsMessageIdWhenNotRemovedOnFailure() throws Exception {
        // removeOnFailure=false keeps the message id of a failed attempt, so the redelivery is a duplicate, as before
        failures.set(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        send("direct:keepOnFailure");

        assertMockEndpointsSatisfied();
        assertEquals(1, attempts.get());
        assertTrue(repository.contains("1"));
    }

    @Test
    void testRedeliveryCompletionEager() throws Exception {
        // completionEager=true removes the message id when the block fails, so the redelivery is processed
        failures.set(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("one");

        send("direct:completionEager");

        assertMockEndpointsSatisfied();
        assertEquals(2, attempts.get());
        assertTrue(repository.contains("1"));
    }

    private Exchange send(String uri) {
        return template.send(uri, exchange -> {
            exchange.getIn().setBody("one");
            exchange.getIn().setHeader("messageId", "1");
        });
    }
}
