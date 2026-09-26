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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The recover task must not recover an aggregated exchange that has been moved to the completed store of a recoverable
 * repository but has not been submitted yet by the thread that completed it.
 */
public class AggregateRecoverInProgressTest extends ContextTestSupport {

    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final RecoverableRepository repository = new RecoverableRepository();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        release.countDown();
        super.tearDown();
    }

    @Test
    public void testCompletedByIncomingExchange() throws Exception {
        // the completed exchange B is in the completed store, and not submitted while the exchange A+C is submitted
        // (completion by batch consumer completes several groups at once)
        AggregationStrategy strategy = new BodyStrategy() {
            @Override
            public void onCompletion(Exchange exchange) {
                if ("A+C".equals(exchange.getIn().getBody(String.class))) {
                    pause();
                }
            }
        };
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").aggregate(header("id"), strategy).aggregationRepository(repository)
                        .completionFromBatchConsumer()
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("A+C", "B");

        send("A", "1");
        send("B", "2");
        Thread producer = new Thread(() -> send("C", "1"), "producer-C");
        producer.start();

        assertNotRecoveredWhilePaused(producer);
        assertMockEndpointsSatisfied();
        assertDeliveredOnce(mock);
    }

    @Test
    public void testOptimisticLockingCompletedBySize() throws Exception {
        repository.pauseAfterRemove("producer-B");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").aggregate(header("id"), new BodyStrategy()).aggregationRepository(repository)
                        .optimisticLocking().completionSize(2)
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");

        send("A", "1");
        Thread producer = new Thread(() -> send("B", "1"), "producer-B");
        producer.start();

        assertNotRecoveredWhilePaused(producer);
        assertMockEndpointsSatisfied();
        assertDeliveredOnce(mock);
    }

    @Test
    public void testOptimisticLockingCompletedByTimeout() throws Exception {
        repository.pauseAfterRemove("AggregateTimeoutChecker");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").aggregate(header("id"), new BodyStrategy()).aggregationRepository(repository)
                        .optimisticLocking().completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");

        send("A", "1");
        send("B", "1");

        assertNotRecoveredWhilePaused(null);
        assertMockEndpointsSatisfied();
        assertDeliveredOnce(mock);
    }

    private void assertNotRecoveredWhilePaused(Thread producer) throws Exception {
        awaitLatch(paused);
        assertFalse(repository.completed.isEmpty(), "The completed exchange should be in the completed store");
        // wait until the recover task has run completely at least once after the exchange was moved to the completed
        // store (it runs with a fixed delay, so the run with the next scan is complete when the scan after it starts)
        int scans = repository.scans.get();
        await().atMost(20, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scans + 2);
        assertEquals(0, repository.recovered.get(), "The recover task should not recover an exchange being completed");

        release.countDown();
        if (producer != null) {
            producer.join(10000);
        }
    }

    private void assertDeliveredOnce(MockEndpoint mock) {
        // the completed exchanges are confirmed, and the recover task finds nothing more to recover
        await().atMost(20, TimeUnit.SECONDS).until(repository.completed::isEmpty);
        int scans = repository.scans.get();
        await().atMost(20, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scans + 2);
        assertEquals(0, repository.recovered.get());
        assertEquals(mock.getExpectedCount(), mock.getReceivedCounter());
    }

    private void send(String body, String id) {
        template.send("direct:start", exchange -> {
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader("id", id);
            // used by completionFromBatchConsumer
            exchange.setProperty(Exchange.BATCH_SIZE, 3);
        });
    }

    private void pause() {
        paused.countDown();
        try {
            if (!release.await(20, TimeUnit.SECONDS)) {
                fail("Not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitLatch(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(20, TimeUnit.SECONDS), "Timeout waiting for latch");
    }

    private static class BodyStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            oldExchange.getIn().setBody(
                    oldExchange.getIn().getBody(String.class) + "+" + newExchange.getIn().getBody(String.class));
            return oldExchange;
        }
    }

    /**
     * An in-memory recoverable repository which moves a removed exchange to a completed store (like the JDBC
     * repository), with optimistic locking based on a version.
     */
    private final class RecoverableRepository extends ServiceSupport
            implements RecoverableAggregationRepository, OptimisticLockingAggregationRepository {

        private static final String VERSION = "RecoverableRepositoryVersion";

        private final Map<String, DefaultExchangeHolder> groups = new HashMap<>();
        private final Map<String, Long> versions = new HashMap<>();
        private final Map<String, DefaultExchangeHolder> completed = new ConcurrentHashMap<>();
        private final AtomicLong version = new AtomicLong();
        private final AtomicInteger scans = new AtomicInteger();
        private final AtomicInteger recovered = new AtomicInteger();
        private final AtomicBoolean pauseAfterRemove = new AtomicBoolean();
        private volatile String pauseThread;

        void pauseAfterRemove(String threadName) {
            pauseThread = threadName;
            pauseAfterRemove.set(true);
        }

        @Override
        public synchronized Exchange add(CamelContext camelContext, String key, Exchange exchange) {
            Exchange old = get(camelContext, key);
            groups.put(key, DefaultExchangeHolder.marshal(exchange, true));
            return old;
        }

        @Override
        public synchronized Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
            Long current = versions.get(key);
            Long expected = oldExchange == null ? null : oldExchange.getProperty(VERSION, Long.class);
            if (current == null ? expected != null : !current.equals(expected)) {
                throw new OptimisticLockingException();
            }
            long next = version.incrementAndGet();
            newExchange.setProperty(VERSION, next);
            groups.put(key, DefaultExchangeHolder.marshal(newExchange, true));
            versions.put(key, next);
            return oldExchange;
        }

        @Override
        public synchronized Exchange get(CamelContext camelContext, String key) {
            DefaultExchangeHolder holder = groups.get(key);
            if (holder == null) {
                return null;
            }
            Exchange answer = unmarshal(camelContext, holder);
            if (versions.containsKey(key)) {
                answer.setProperty(VERSION, versions.get(key));
            }
            return answer;
        }

        @Override
        public void remove(CamelContext camelContext, String key, Exchange exchange) {
            synchronized (this) {
                Long expected = exchange.getProperty(VERSION, Long.class);
                if (expected != null && !expected.equals(versions.get(key))) {
                    throw new OptimisticLockingException();
                }
                groups.remove(key);
                versions.remove(key);
                completed.put(exchange.getExchangeId(), DefaultExchangeHolder.marshal(exchange, true));
            }
            if (pauseThread != null && Thread.currentThread().getName().contains(pauseThread)
                    && pauseAfterRemove.compareAndSet(true, false)) {
                pause();
            }
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            completed.remove(exchangeId);
        }

        @Override
        public synchronized Set<String> getKeys() {
            return Set.copyOf(groups.keySet());
        }

        @Override
        public Set<String> scan(CamelContext camelContext) {
            scans.incrementAndGet();
            return Set.copyOf(completed.keySet());
        }

        @Override
        public Exchange recover(CamelContext camelContext, String exchangeId) {
            DefaultExchangeHolder holder = completed.get(exchangeId);
            if (holder == null) {
                return null;
            }
            recovered.incrementAndGet();
            return unmarshal(camelContext, holder);
        }

        private Exchange unmarshal(CamelContext camelContext, DefaultExchangeHolder holder) {
            Exchange answer = new DefaultExchange(camelContext);
            DefaultExchangeHolder.unmarshal(answer, holder);
            return answer;
        }

        @Override
        public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        }

        @Override
        public void setRecoveryInterval(long interval) {
        }

        @Override
        public long getRecoveryInterval() {
            return 100;
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
}
