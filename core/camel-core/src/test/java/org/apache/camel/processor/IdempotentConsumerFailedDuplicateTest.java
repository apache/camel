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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With skipDuplicate=false a duplicate is routed on. If the duplicate fails, it must not remove the key that the
 * original exchange added.
 */
class IdempotentConsumerFailedDuplicateTest extends ContextTestSupport {

    private final IdempotentRepository repo = new KeyValueIdempotentRepository();
    private final CountDownLatch firstInProgress = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testFailedDuplicateDoesNotRemoveKey() throws Exception {
        addRoute(true, false);
        assertFailedDuplicateDoesNotRemoveKey();
    }

    @Test
    void testFailedDuplicateDoesNotRemoveKeyCompletionEager() throws Exception {
        addRoute(true, true);
        assertFailedDuplicateDoesNotRemoveKey();
    }

    @Test
    void testFailedDuplicateDoesNotRemoveKeyNonEager() throws Exception {
        addRoute(false, false);
        assertFailedDuplicateDoesNotRemoveKey();
    }

    @Test
    void testFailedDuplicateDoesNotRemoveKeyOfInflightExchange() throws Exception {
        addRoute(true, false);

        MockEndpoint newMessages = getMockEndpoint("mock:new");
        newMessages.expectedBodiesReceived("first");
        MockEndpoint duplicates = getMockEndpoint("mock:duplicate");
        duplicates.expectedBodiesReceived("second", "third");

        // the first exchange adds the key (eager) and waits inside the route
        Future<Exchange> first = template.asyncSend("direct:start", e -> {
            e.getIn().setHeader("messageId", "1");
            e.getIn().setHeader("block", true);
            e.getIn().setBody("first");
        });
        try {
            assertTrue(firstInProgress.await(10, TimeUnit.SECONDS));

            // a duplicate that fails while the first exchange is still in progress
            Exchange second = send("second");
            assertTrue(second.isFailed());
            assertTrue(repo.contains("1"), "The failed duplicate must not remove the key of the in-flight exchange");

            // so another copy is still a duplicate, and is not processed concurrently with the first exchange
            Exchange third = send("third");
            assertEquals(Boolean.TRUE, third.getProperty(Exchange.DUPLICATE_MESSAGE));
        } finally {
            releaseFirst.countDown();
        }
        Exchange out = first.get(10, TimeUnit.SECONDS);
        assertFalse(out.isFailed());
        assertNull(out.getProperty(Exchange.DUPLICATE_MESSAGE));

        assertMockEndpointsSatisfied();
        assertTrue(repo.contains("1"));
    }

    private void assertFailedDuplicateDoesNotRemoveKey() throws Exception {
        MockEndpoint newMessages = getMockEndpoint("mock:new");
        newMessages.expectedBodiesReceived("first");
        MockEndpoint duplicates = getMockEndpoint("mock:duplicate");
        duplicates.expectedBodiesReceived("second", "third");

        Exchange first = send("first");
        assertFalse(first.isFailed());
        assertTrue(repo.contains("1"));

        Exchange second = send("second");
        assertTrue(second.isFailed());
        assertEquals(Boolean.TRUE, second.getProperty(Exchange.DUPLICATE_MESSAGE));
        assertTrue(repo.contains("1"), "The failed duplicate must not remove the key added by the first exchange");

        Exchange third = send("third");
        assertEquals(Boolean.TRUE, third.getProperty(Exchange.DUPLICATE_MESSAGE));

        assertMockEndpointsSatisfied();
    }

    private Exchange send(String body) {
        return template.send("direct:start", e -> {
            e.getIn().setHeader("messageId", "1");
            e.getIn().setBody(body);
        });
    }

    private void addRoute(boolean eager, boolean completionEager) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .idempotentConsumer(header("messageId")).idempotentRepository(repo)
                        .eager(eager).completionEager(completionEager).skipDuplicate(false)
                        .choice()
                        .when(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                        .to("mock:duplicate")
                        .throwException(new IllegalStateException("Cannot handle the duplicate"))
                        .otherwise()
                        .process(e -> {
                            if (e.getIn().getHeader("block", false, Boolean.class)) {
                                firstInProgress.countDown();
                                releaseFirst.await(10, TimeUnit.SECONDS);
                            }
                        })
                        .to("mock:new")
                        .end()
                        .end();
            }
        });
        context.start();
    }
}
