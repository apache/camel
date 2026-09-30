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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.ClosedCorrelationKeyException;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An exchange that passed the closed correlation key check before the group of its key was completed (and the key
 * closed) must not start a new group for the closed key.
 */
class AggregateClosedCorrelationKeyRaceTest extends ContextTestSupport {

    private final CountDownLatch inAggregate = new CountDownLatch(1);
    private final CountDownLatch releaseAggregate = new CountDownLatch(1);
    private final CountDownLatch passedClosedKeyCheck = new CountDownLatch(1);
    private ExecutorService executorService;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        releaseAggregate.countDown();
        executorService.shutdownNow();
        super.tearDown();
    }

    @Test
    void testExchangeWaitingForLockWhileKeyIsClosed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");

        AggregateProcessor ap = createProcessor(false);
        ap.setAggregationRepository(new MemoryAggregationRepository());
        ap.start();

        ap.process(createExchange("A"));

        // B completes the group, and is paused in the aggregation strategy while it holds the aggregation lock
        Exchange b = createExchange("B");
        Thread producerB = new Thread(() -> process(ap, b), "producer-B");
        producerB.start();
        await(inAggregate);

        // C passes the closed correlation key check (the key is not closed yet) and waits for the lock
        Exchange c = createExchange("C");
        Thread producerC = new Thread(() -> process(ap, c), "producer-C");
        producerC.start();
        await(passedClosedKeyCheck);

        // B completes the group and closes the key, then C gets the lock
        releaseAggregate.countDown();
        producerB.join(10000);
        producerC.join(10000);
        assertFalse(producerB.isAlive(), "producer-B did not complete within 10 s");
        assertFalse(producerC.isAlive(), "producer-C did not complete within 10 s");

        assertTrue(c.getException() instanceof ClosedCorrelationKeyException,
                "Expected ClosedCorrelationKeyException but was: " + c.getException());
        assertTrue(ap.getAggregationRepository().getKeys().isEmpty(),
                "No new group should be started for the closed key, but was: " + ap.getAggregationRepository().getKeys());
        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    void testOptimisticLockingRetryAfterKeyIsClosed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");

        // pauses the first get of C, so B can complete the group and close the key before C continues
        AtomicBoolean pauseGet = new AtomicBoolean(true);
        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public Exchange get(CamelContext camelContext, String key) {
                Exchange answer = super.get(camelContext, key);
                if (Thread.currentThread().getName().equals("producer-C") && pauseGet.getAndSet(false)) {
                    passedClosedKeyCheck.countDown();
                    AggregateClosedCorrelationKeyRaceTest.await(releaseAggregate);
                }
                return answer;
            }
        };

        AggregateProcessor ap = createProcessor(true);
        ap.setAggregationRepository(repository);
        ap.setOptimisticLocking(true);
        // retry at once in the same thread
        ap.setOptimisticLockRetryPolicy(new OptimisticLockRetryPolicy().retryDelay(0).maximumRetries(5));
        ap.start();

        ap.process(createExchange("A"));

        // C reads the group [A] and is paused
        Exchange c = createExchange("C");
        Thread producerC = new Thread(() -> process(ap, c), "producer-C");
        producerC.start();
        await(passedClosedKeyCheck);

        // B completes the group [A, B] and closes the key
        ap.process(createExchange("B"));

        // C fails to remove the group it read (optimistic locking) and is retried
        releaseAggregate.countDown();
        producerC.join(10000);
        assertFalse(producerC.isAlive(), "producer-C did not complete within 10 s");

        assertTrue(c.getException() instanceof ClosedCorrelationKeyException,
                "Expected ClosedCorrelationKeyException but was: " + c.getException());
        assertTrue(ap.getAggregationRepository().getKeys().isEmpty(),
                "No new group should be started for the closed key, but was: " + ap.getAggregationRepository().getKeys());
        assertMockEndpointsSatisfied();

        ap.stop();
    }

    private AggregateProcessor createProcessor(boolean optimistic) {
        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        AggregationStrategy strategy = (oldExchange, newExchange) -> {
            String body = newExchange.getIn().getBody(String.class);
            if (!optimistic && "B".equals(body)) {
                inAggregate.countDown();
                await(releaseAggregate);
            }
            if (oldExchange == null) {
                return newExchange;
            }
            oldExchange.getIn().setBody(oldExchange.getIn().getBody(String.class) + "+" + body);
            return oldExchange;
        };

        AggregateProcessor ap = new AggregateProcessor(context, done, header("id"), strategy, executorService, true) {
            @Override
            protected boolean doProcess(Exchange exchange, String key, AsyncCallback callback, boolean sync) {
                // called after the closed correlation key check in process(Exchange, AsyncCallback)
                if (!optimistic && "C".equals(exchange.getIn().getBody(String.class))) {
                    passedClosedKeyCheck.countDown();
                }
                return super.doProcess(exchange, key, callback, sync);
            }
        };
        ap.setCompletionSize(2);
        ap.setCloseCorrelationKeyOnCompletion(100);
        return ap;
    }

    private Exchange createExchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader("id", 1);
        return exchange;
    }

    private static void process(AggregateProcessor ap, Exchange exchange) {
        try {
            ap.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                fail("Timeout waiting for latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted");
        }
    }
}
