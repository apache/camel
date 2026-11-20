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
package org.apache.camel.component.seda;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request-reply message that the seda producer discards fails at once with a RejectedExecutionException, as a message
 * discarded by SedaEndpoint.purgeQueue: the producer does not wait for a reply that never comes. A discarded InOnly
 * message is still dropped without failure.
 */
class SedaInOutDiscardTest extends ContextTestSupport {

    // a timeout longer than the test waits: the request must not end with the timeout
    private static final String FULL = "seda:full?size=1&discardWhenFull=true&timeout=20000";

    private final CountDownLatch taken = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseConsumer() {
        release.countDown();
    }

    @Test
    void testDiscardIfNoConsumers() throws Exception {
        String uri = "seda:none?discardIfNoConsumers=true&timeout=20000";

        Exchange reply = request(uri, "Hello World").get(5, TimeUnit.SECONDS);
        assertRejected("Exchange discarded as the SEDA queue has no consumers", reply);

        // InOnly is not changed: the message is dropped, the exchange does not fail
        Exchange inOnly = template.send(uri, e -> e.getMessage().setBody("Bye World"));
        assertNull(inOnly.getException());
    }

    @Test
    void testDiscardWhenFull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:full");
        mock.expectedBodiesReceived("A", "B");

        // A is taken by the consumer, which waits, and B fills the queue
        template.sendBody(FULL, "A");
        assertTrue(taken.await(5, TimeUnit.SECONDS));
        template.sendBody(FULL, "B");

        // the queue is full, so the request is discarded
        Exchange reply = request(FULL, "C").get(5, TimeUnit.SECONDS);
        assertRejected("Exchange discarded as the SEDA queue is full", reply);

        release.countDown();
        assertMockEndpointsSatisfied();
    }

    private Future<Exchange> request(String uri, String body) {
        return template.asyncSend(uri, e -> {
            e.setPattern(ExchangePattern.InOut);
            e.getMessage().setBody(body);
        });
    }

    private static void assertRejected(String message, Exchange reply) {
        RejectedExecutionException e = assertIsInstanceOf(RejectedExecutionException.class, reply.getException());
        assertEquals(message, e.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(FULL)
                        .process(e -> {
                            taken.countDown();
                            release.await(10, TimeUnit.SECONDS);
                        })
                        .to("mock:full");
            }
        };
    }
}
