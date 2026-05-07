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
package org.apache.camel.processor.aggregator;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that optimistic locking retries happen synchronously in the same thread when optimisticLockingSyncRetry is
 * enabled.
 */
class AggregateOptimisticLockSyncRetryTest extends ContextTestSupport {

    private static final int FAIL_FIRST_N_ATTEMPTS = 3;

    private final AtomicInteger addCounter = new AtomicInteger();
    private volatile String aggregateThreadName;

    /**
     * Repository that throws OptimisticLockingException for the first N attempts, then succeeds.
     */
    private final MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
            int count = addCounter.incrementAndGet();
            // Record the thread name on every attempt
            aggregateThreadName = Thread.currentThread().getName();
            if (count <= FAIL_FIRST_N_ATTEMPTS) {
                throw new OptimisticLockingException();
            }
            return super.add(camelContext, key, oldExchange, newExchange);
        }
    };

    @Test
    void testSyncRetryHappensInSameThread() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);

        mock.assertIsSatisfied();

        assertTrue(addCounter.get() > FAIL_FIRST_N_ATTEMPTS,
                "Expected more than " + FAIL_FIRST_N_ATTEMPTS + " attempts, got " + addCounter.get());

        assertNotNull(aggregateThreadName, "Expected aggregateThreadName to be set");
        assertFalse(aggregateThreadName.contains("AggregateOptimisticLockingExecutor"),
                "Expected synchronous retry but found async executor thread: " + aggregateThreadName);
    }

    @Test
    void testSyncRetryCompletes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");

        template.sendBodyAndHeader("direct:start", "A", "id", 1);

        // Reset counter so next message triggers failures and retries
        addCounter.set(0);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);

        mock.assertIsSatisfied();
    }

    @Test
    void testSyncRetryWithNonZeroDelayStaysOnCallingThread() throws Exception {
        AtomicInteger delayedCounter = new AtomicInteger();
        AtomicReference<String> retryThreadName = new AtomicReference<>();

        MemoryAggregationRepository delayedRepo = new MemoryAggregationRepository(true) {
            @Override
            public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
                int count = delayedCounter.incrementAndGet();
                retryThreadName.set(Thread.currentThread().getName());
                if (count <= 2) {
                    throw new OptimisticLockingException();
                }
                return super.add(camelContext, key, oldExchange, newExchange);
            }
        };

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:delay-start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .aggregationRepository(delayedRepo)
                        .optimisticLocking()
                        .optimisticLockingSyncRetry()
                        .optimisticLockRetryPolicy(
                                new OptimisticLockRetryPolicy().maximumRetries(10).retryDelay(10))
                        .completionSize(2)
                        .to("mock:delay-result");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:delay-result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:delay-start", "A", "id", 2);
        template.sendBodyAndHeader("direct:delay-start", "B", "id", 2);

        mock.assertIsSatisfied();

        assertTrue(delayedCounter.get() > 2, "Expected retries to occur");
        assertFalse(retryThreadName.get().contains("AggregateOptimisticLockingExecutor"),
                "Expected synchronous retry thread but got: " + retryThreadName.get());
    }

    @Test
    void testSyncRetryInterrupted() throws Exception {
        AtomicInteger interruptCounter = new AtomicInteger();

        MemoryAggregationRepository alwaysFailRepo = new MemoryAggregationRepository(true) {
            @Override
            public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
                interruptCounter.incrementAndGet();
                throw new OptimisticLockingException();
            }
        };

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:interrupt-start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .aggregationRepository(alwaysFailRepo)
                        .optimisticLocking()
                        .optimisticLockingSyncRetry()
                        .optimisticLockRetryPolicy(
                                new OptimisticLockRetryPolicy().maximumRetries(100).retryDelay(500))
                        .completionSize(2)
                        .to("mock:interrupt-result");
            }
        });

        AtomicReference<Exchange> resultExchange = new AtomicReference<>();
        Thread sender = new Thread(() -> {
            Exchange exchange = template.request("direct:interrupt-start",
                    e -> {
                        e.getMessage().setHeader("id", "interrupt-group");
                        e.getMessage().setBody("X");
                    });
            resultExchange.set(exchange);
        });
        sender.start();

        // Wait until at least one retry attempt has occurred before interrupting
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> interruptCounter.get() > 0);
        sender.interrupt();
        sender.join(5000);

        // The exchange should have been completed with an InterruptedException
        Exchange result = resultExchange.get();
        assertNotNull(result, "Expected exchange to be set after sender completed");
        assertNotNull(result.getException(), "Expected exception on interrupted exchange");
        assertTrue(result.getException() instanceof InterruptedException,
                "Expected InterruptedException but got: " + result.getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .aggregationRepository(repository)
                        .optimisticLocking()
                        .optimisticLockingSyncRetry()
                        .optimisticLockRetryPolicy(
                                new OptimisticLockRetryPolicy().maximumRetries(10).retryDelay(0))
                        .completionSize(2)
                        .to("mock:result");
            }
        };
    }
}
