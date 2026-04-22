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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that optimistic locking retries happen synchronously in the same thread when syncOptimisticRetry is enabled.
 */
public class AggregateOptimisticLockSyncRetryTest extends ContextTestSupport {

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
    public void testSyncRetryHappensInSameThread() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String callerThread = Thread.currentThread().getName();

        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);

        mock.assertIsSatisfied();

        // The repository should have been called more than FAIL_FIRST_N_ATTEMPTS times
        // (the first N fail, then succeed)
        assertTrue(addCounter.get() > FAIL_FIRST_N_ATTEMPTS,
                "Expected more than " + FAIL_FIRST_N_ATTEMPTS + " attempts, got " + addCounter.get());

        // Since syncOptimisticRetry is enabled, the retry should happen in a Camel thread
        // (the route's thread), NOT in the AggregateOptimisticLockingExecutor thread pool.
        // The key assertion is that the thread name does NOT contain the async executor name.
        if (aggregateThreadName != null) {
            assertFalse(aggregateThreadName.contains("AggregateOptimisticLockingExecutor"),
                    "Expected synchronous retry but found async executor thread: " + aggregateThreadName);
        }
    }

    @Test
    public void testSyncRetryCompletes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");

        template.sendBodyAndHeader("direct:start", "A", "id", 1);

        // Reset counter so next message triggers failures and retries
        addCounter.set(0);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);

        mock.assertIsSatisfied();
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
                        .syncOptimisticRetry()
                        .optimisticLockRetryPolicy(
                                new OptimisticLockRetryPolicy().maximumRetries(10).retryDelay(0))
                        .completionSize(2)
                        .to("mock:result");
            }
        };
    }
}
