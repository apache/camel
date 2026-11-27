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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * With optimistic locking and closeCorrelationKeyOnCompletion, a completion removes the group from the repository
 * before it closes the key. An exchange that reads the repository in between must not start a new group for the key
 * that is being closed.
 */
class AggregateOptimisticLockingClosedKeyTest extends ContextTestSupport {

    private final Map<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private volatile AggregateProcessor processor;
    private volatile Thread producerC;
    private volatile Runnable onAggregation;
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
        latches.values().forEach(CountDownLatch::countDown);
        executorService.shutdownNow();
        super.tearDown();
    }

    @Test
    void testExchangeBetweenRemoveAndCloseIsRejected() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");
        mock.setAssertPeriod(200);

        Exchange c = createExchange("C");
        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public void remove(CamelContext camelContext, String key, Exchange exchange) {
                super.remove(camelContext, key, exchange);
                if (firstCallOn("producer-B", "remove")) {
                    // B has removed the group [A] and not yet closed the key: C runs in between
                    producerC = new Thread(() -> {
                        process(processor, c);
                        latch("cInWindow").countDown();
                    }, "producer-C");
                    producerC.start();
                    await(latch("cInWindow"));
                }
            }
        };

        AggregateProcessor ap = createProcessor(repository, false);
        ap.process(createExchange("A"));

        Exchange b = createExchange("B");
        runProducer(ap, b, "producer-B");
        latch("bDone").countDown();
        join(producerC);

        assertNull(b.getException());
        assertRejected(ap, c);
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    void testExchangeThatReadsAfterRemoveIsRejected() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");
        mock.setAssertPeriod(200);

        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public Exchange get(CamelContext camelContext, String key) {
                if (firstCallOn("producer-C", "get")) {
                    // C has passed the closed correlation key checks, and reads the group after B removed it
                    latch("cBeforeGet").countDown();
                    await(latch("bRemoved"));
                }
                return super.get(camelContext, key);
            }

            @Override
            public void remove(CamelContext camelContext, String key, Exchange exchange) {
                super.remove(camelContext, key, exchange);
                if (firstCallOn("producer-B", "remove")) {
                    // B has removed the group [A] and not yet closed the key
                    latch("bRemoved").countDown();
                    await(latch("cInWindow"));
                }
            }
        };

        AggregateProcessor ap = createProcessor(repository, false);
        ap.process(createExchange("A"));

        Exchange c = createExchange("C");
        producerC = new Thread(() -> {
            process(ap, c);
            latch("cInWindow").countDown();
        }, "producer-C");
        producerC.start();
        await(latch("cBeforeGet"));

        Exchange b = createExchange("B");
        runProducer(ap, b, "producer-B");
        latch("bDone").countDown();
        join(producerC);

        assertNull(b.getException());
        assertRejected(ap, c);
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    void testRemoveFailsAfterAnotherCompletionClosedTheKey() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A");
        mock.setAssertPeriod(200);

        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public void remove(CamelContext camelContext, String key, Exchange exchange) {
                if (firstCallOn("producer-B", "remove")) {
                    // B completes the group [A, B], and the group [A] is force completed before B removes it
                    latch("bBeforeRemove").countDown();
                    await(latch("forced"));
                }
                super.remove(camelContext, key, exchange);
            }
        };

        AggregateProcessor ap = createProcessor(repository, false);
        ap.process(createExchange("A"));

        Exchange b = createExchange("B");
        Thread producerB = new Thread(() -> process(ap, b), "producer-B");
        producerB.start();
        await(latch("bBeforeRemove"));
        ap.forceCompletionOfGroup("1");
        latch("forced").countDown();
        join(producerB);

        // the remove of B fails, and its retry finds the key closed by the force completion
        assertRejected(ap, b);
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    void testRemoveFailsAfterTheGroupChangedLeavesTheKeyOpen() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+D+B");
        mock.setAssertPeriod(200);

        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
                if (firstCallOn("producer-D", "add")) {
                    // D has read the group [A], and adds to it after B started to complete the group [A, B]
                    latch("dBeforeAdd").countDown();
                    await(latch("bBeforeRemove"));
                }
                return super.add(camelContext, key, oldExchange, newExchange);
            }

            @Override
            public void remove(CamelContext camelContext, String key, Exchange exchange) {
                if (firstCallOn("producer-B", "remove")) {
                    latch("bBeforeRemove").countDown();
                    await(latch("dDone"));
                }
                super.remove(camelContext, key, exchange);
            }
        };

        // B completes the group
        AggregateProcessor ap = createProcessor(repository, true);
        ap.process(createExchange("A"));

        Exchange d = createExchange("D");
        Thread producerD = new Thread(() -> process(ap, d), "producer-D");
        producerD.start();
        await(latch("dBeforeAdd"));

        Exchange b = createExchange("B");
        Thread producerB = new Thread(() -> process(ap, b), "producer-B");
        producerB.start();
        join(producerD);
        latch("dDone").countDown();
        join(producerB);

        // the remove of B fails as D changed the group, which must leave the key open, so B completes [A, D, B]
        assertNull(d.getException());
        assertNull(b.getException());
        assertMockEndpointsSatisfied();
        assertTrue(ap.getAggregationRepository().getKeys().isEmpty());
        Exchange e = createExchange("E");
        ap.process(e);
        assertTrue(e.getException() instanceof ClosedCorrelationKeyException,
                "Expected ClosedCorrelationKeyException but was: " + e.getException());
        ap.stop();
    }

    @Test
    void testNewGroupAfterExchangeCompletedAloneIsRejected() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("B");
        mock.setAssertPeriod(200);

        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
                if (firstCallOn("producer-D", "add")) {
                    // D has read no group and passed the closed correlation key checks: B completes on its own (its
                    // group is never stored) and closes the key before D adds its new group
                    latch("dBeforeAdd").countDown();
                    await(latch("bDone"));
                }
                return super.add(camelContext, key, oldExchange, newExchange);
            }
        };

        AggregateProcessor ap = createProcessor(repository, true);
        Exchange d = createExchange("D");
        Thread producerD = new Thread(() -> process(ap, d), "producer-D");
        producerD.start();
        await(latch("dBeforeAdd"));
        Exchange b = createExchange("B");
        ap.process(b);
        latch("bDone").countDown();
        join(producerD);

        assertNull(b.getException());
        assertRejected(ap, d);
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    void testNewGroupAfterStoredGroupWasCompletedIsRejected() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");
        mock.setAssertPeriod(200);

        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
                if (firstCallOn("producer-D", "add")) {
                    // D has read no group and passed the closed correlation key checks: the group [A] is stored and
                    // completed by B before D adds its new group, so the key has no group again
                    latch("dBeforeAdd").countDown();
                    await(latch("bDone"));
                }
                return super.add(camelContext, key, oldExchange, newExchange);
            }
        };

        AggregateProcessor ap = createProcessor(repository, false);
        Exchange d = createExchange("D");
        Thread producerD = new Thread(() -> process(ap, d), "producer-D");
        producerD.start();
        await(latch("dBeforeAdd"));
        ap.process(createExchange("A"));
        Exchange b = createExchange("B");
        ap.process(b);
        latch("bDone").countDown();
        join(producerD);

        assertNull(b.getException());
        assertRejected(ap, d);
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    void testNewGroupRemovedAgainIsConfirmedWithRecoverableRepository() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("B");
        mock.setAssertPeriod(200);

        RecoverableMemoryRepository repository = new RecoverableMemoryRepository() {
            @Override
            public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
                if (firstCallOn("producer-D", "add")) {
                    latch("dBeforeAdd").countDown();
                    await(latch("bDone"));
                }
                return super.add(camelContext, key, oldExchange, newExchange);
            }
        };

        AggregateProcessor ap = createProcessor(repository, true);
        Exchange d = createExchange("D");
        Thread producerD = new Thread(() -> process(ap, d), "producer-D");
        producerD.start();
        await(latch("dBeforeAdd"));
        ap.process(createExchange("B"));
        latch("bDone").countDown();
        join(producerD);

        assertRejected(ap, d);
        // the group [D] went to the completed store when it was removed again, and was confirmed, so the recover task
        // has nothing to send
        assertEquals(1, repository.removed.get());
        assertTrue(repository.scan(context).isEmpty(), "Not confirmed: " + repository.scan(context));
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    void testExchangeThatWouldCompleteAloneAfterKeyClosedIsRejected() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("YB");
        mock.setAssertPeriod(200);

        onAggregation = () -> {
            if (firstCallOn("producer-X", "aggregate")) {
                // X has read no group and passed the closed correlation key checks, and completes on its own: Y
                // completes on its own and closes the key before X does
                latch("xRead").countDown();
                await(latch("yDone"));
            }
        };

        AggregateProcessor ap = createProcessor(new MemoryAggregationRepository(true), true);
        Exchange x = createExchange("XB");
        Thread producerX = new Thread(() -> process(ap, x), "producer-X");
        producerX.start();
        await(latch("xRead"));
        Exchange y = createExchange("YB");
        ap.process(y);
        latch("yDone").countDown();
        join(producerX);

        assertNull(y.getException());
        assertRejected(ap, x);
        assertMockEndpointsSatisfied();
        ap.stop();
    }

    @Test
    void testExchangeThatWouldCompleteAloneJoinsNewGroup() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("D+B");
        mock.setAssertPeriod(200);

        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public Exchange get(CamelContext camelContext, String key) {
                Exchange answer = super.get(camelContext, key);
                if (firstCallOn("producer-B", "get")) {
                    // B has read no group, and would complete on its own: D adds a new group before B completes
                    latch("bRead").countDown();
                    await(latch("dAdded"));
                }
                return answer;
            }

            @Override
            public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
                Exchange answer = super.add(camelContext, key, oldExchange, newExchange);
                if (firstCallOn("producer-D", "add")) {
                    latch("dAdded").countDown();
                    await(latch("bDone"));
                }
                return answer;
            }
        };

        AggregateProcessor ap = createProcessor(repository, true);
        Exchange b = createExchange("B");
        Thread producerB = new Thread(() -> {
            process(ap, b);
            latch("bDone").countDown();
        }, "producer-B");
        producerB.start();
        await(latch("bRead"));
        Exchange d = createExchange("D");
        Thread producerD = new Thread(() -> process(ap, d), "producer-D");
        producerD.start();
        join(producerB);
        join(producerD);

        // B sees the group [D] when it closes the key, and completes [D, B] instead of [B] on its own
        assertNull(b.getException());
        assertNull(d.getException());
        assertMockEndpointsSatisfied();
        Exchange e = createExchange("E");
        ap.process(e);
        assertRejected(ap, e);
        ap.stop();
    }

    /**
     * A memory repository with optimistic locking that moves a removed exchange to a completed store, as a recoverable
     * repository does, until it is confirmed.
     */
    private static class RecoverableMemoryRepository extends MemoryAggregationRepository
            implements RecoverableAggregationRepository {

        private final Map<String, Exchange> completed = new ConcurrentHashMap<>();
        private final AtomicInteger removed = new AtomicInteger();

        RecoverableMemoryRepository() {
            super(true);
        }

        @Override
        public void remove(CamelContext camelContext, String key, Exchange exchange) {
            super.remove(camelContext, key, exchange);
            completed.put(exchange.getExchangeId(), exchange);
            removed.incrementAndGet();
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            completed.remove(exchangeId);
        }

        @Override
        public Set<String> scan(CamelContext camelContext) {
            return Set.copyOf(completed.keySet());
        }

        @Override
        public Exchange recover(CamelContext camelContext, String exchangeId) {
            return completed.get(exchangeId);
        }

        @Override
        public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        }

        @Override
        public void setRecoveryInterval(long interval) {
        }

        @Override
        public long getRecoveryInterval() {
            return 60000;
        }

        @Override
        public void setUseRecovery(boolean useRecovery) {
        }

        @Override
        public boolean isUseRecovery() {
            return true;
        }

        @Override
        public void setDeadLetterUri(String deadLetterUri) {
        }

        @Override
        public String getDeadLetterUri() {
            return null;
        }

        @Override
        public void setMaximumRedeliveries(int maximumRedeliveries) {
        }

        @Override
        public int getMaximumRedeliveries() {
            return 0;
        }
    }

    private AggregateProcessor createProcessor(MemoryAggregationRepository repository, boolean completeOnB)
            throws Exception {
        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        AggregationStrategy strategy = (oldExchange, newExchange) -> {
            if (oldExchange == null) {
                return newExchange;
            }
            oldExchange.getIn().setBody(
                    oldExchange.getIn().getBody(String.class) + "+" + newExchange.getIn().getBody(String.class));
            return oldExchange;
        };

        AggregateProcessor ap = new AggregateProcessor(context, done, header("id"), strategy, executorService, true) {
            @Override
            protected boolean doProcess(Exchange exchange, String key, AsyncCallback callback, boolean sync) {
                // an exchange that is tried again (optimistic locking) while the key is being closed: the completion
                // goes on, and the next attempt runs once it is done
                String body = exchange.getIn().getBody(String.class);
                if ("C".equals(body) && attempts.computeIfAbsent(body, k -> new AtomicInteger()).incrementAndGet() == 2) {
                    latch("cInWindow").countDown();
                    AggregateOptimisticLockingClosedKeyTest.await(latch("bDone"));
                }
                return super.doProcess(exchange, key, callback, sync);
            }

            @Override
            protected Exchange onAggregation(Exchange oldExchange, Exchange newExchange) {
                Runnable hook = onAggregation;
                if (hook != null) {
                    hook.run();
                }
                return super.onAggregation(oldExchange, newExchange);
            }
        };
        processor = ap;
        if (completeOnB) {
            ap.setCompletionPredicate(body().endsWith("B"));
        } else {
            ap.setCompletionSize(2);
        }
        ap.setCloseCorrelationKeyOnCompletion(100);
        ap.setAggregationRepository(repository);
        ap.setOptimisticLocking(true);
        // try again at once in the same thread
        ap.setOptimisticLockRetryPolicy(new OptimisticLockRetryPolicy().retryDelay(0).maximumRetries(5));
        ap.start();
        return ap;
    }

    private boolean firstCallOn(String threadName, String method) {
        return Thread.currentThread().getName().equals(threadName)
                && attempts.computeIfAbsent(threadName + "-" + method, k -> new AtomicInteger()).incrementAndGet() == 1;
    }

    private CountDownLatch latch(String name) {
        return latches.computeIfAbsent(name, k -> new CountDownLatch(1));
    }

    private void assertRejected(AggregateProcessor ap, Exchange exchange) throws Exception {
        assertTrue(exchange.getException() instanceof ClosedCorrelationKeyException,
                "Expected ClosedCorrelationKeyException but was: " + exchange.getException());
        assertTrue(ap.getAggregationRepository().getKeys().isEmpty(),
                "No new group should be started for the closed key, but was: " + ap.getAggregationRepository().getKeys());
        // the key is closed for a later exchange as well
        Exchange later = createExchange("later");
        ap.process(later);
        assertTrue(later.getException() instanceof ClosedCorrelationKeyException,
                "Expected ClosedCorrelationKeyException but was: " + later.getException());
    }

    private Exchange createExchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader("id", 1);
        return exchange;
    }

    private static void runProducer(AggregateProcessor ap, Exchange exchange, String name) throws InterruptedException {
        Thread producer = new Thread(() -> process(ap, exchange), name);
        producer.start();
        join(producer);
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(10000);
        assertFalse(thread.isAlive(), thread.getName() + " did not complete within 10 s");
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
