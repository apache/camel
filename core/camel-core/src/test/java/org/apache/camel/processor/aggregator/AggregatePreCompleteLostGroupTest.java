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
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A group completed by pre-completion has been removed from the repository, so it must be sent even if the exchange
 * that pre-completed it cannot be aggregated afterwards.
 */
public class AggregatePreCompleteLostGroupTest extends ContextTestSupport {

    private final CountDownLatch removed = new CountDownLatch(1);
    private final CountDownLatch releaseRemove = new CountDownLatch(1);
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
        releaseRemove.countDown();
        executorService.shutdownNow();
        super.tearDown();
    }

    @Test
    public void testOptimisticLockingFailureAfterPreCompletion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a1", "c", "START-b");

        // pauses the thread of START-b right after it removed the pre-completed group from the repository
        AtomicBoolean pauseRemove = new AtomicBoolean(true);
        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public void remove(CamelContext camelContext, String key, Exchange exchange) {
                super.remove(camelContext, key, exchange);
                if (Thread.currentThread().getName().equals("producer-START-b") && pauseRemove.getAndSet(false)) {
                    removed.countDown();
                    await(releaseRemove);
                }
            }
        };

        AggregateProcessor ap = createProcessor();
        ap.setAggregationRepository(repository);
        ap.setOptimisticLocking(true);
        // retry at once in the same thread
        ap.setOptimisticLockRetryPolicy(new OptimisticLockRetryPolicy().retryDelay(0).maximumRetries(5));
        ap.start();

        ap.process(createExchange("a1"));

        // START-b pre-completes the group [a1]: it is removed from the repository, and START-b is paused
        Exchange b = createExchange("START-b");
        Thread producer = new Thread(() -> process(ap, b), "producer-START-b");
        producer.start();
        await(removed);

        // c starts a new group, so START-b fails to add its new group and is retried
        ap.process(createExchange("c"));
        releaseRemove.countDown();
        producer.join(10000);
        assertNull(b.getException());

        // pre-complete the group of START-b
        ap.process(createExchange("START-end"));

        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    public void testAggregationFailureAfterPreCompletion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a1");

        AggregateProcessor ap = createProcessor();
        ap.start();

        ap.process(createExchange("a1"));
        Exchange bad = createExchange("START-fail");
        ap.process(bad);

        assertNotNull(bad.getException());
        assertTrue(ap.getAggregationRepository().getKeys().isEmpty());
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    public void testAggregationFailureDiscardedAfterPreCompletion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a1");

        AggregateProcessor ap = createProcessor();
        ap.setDiscardOnAggregationFailure(true);
        ap.start();

        ap.process(createExchange("a1"));
        Exchange bad = createExchange("START-fail");
        ap.process(bad);

        assertNull(bad.getException());
        assertTrue(ap.getAggregationRepository().getKeys().isEmpty());
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    private AggregateProcessor createProcessor() {
        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        // pre-completes the current group when a body starting with START arrives
        AggregationStrategy strategy = new AggregationStrategy() {
            @Override
            public boolean canPreComplete() {
                return true;
            }

            @Override
            public boolean preComplete(Exchange oldExchange, Exchange newExchange) {
                return oldExchange != null && newExchange.getIn().getBody(String.class).startsWith("START");
            }

            @Override
            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                String body = newExchange.getIn().getBody(String.class);
                if (body.endsWith("fail")) {
                    throw new IllegalArgumentException("Cannot aggregate " + body);
                }
                if (oldExchange == null) {
                    return newExchange;
                }
                oldExchange.getIn().setBody(oldExchange.getIn().getBody(String.class) + "+" + body);
                return oldExchange;
            }
        };
        return new AggregateProcessor(context, done, header("id"), strategy, executorService, true);
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
