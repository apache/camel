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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With optimistic locking a group must not lose its completion timeout because a group of the same correlation key is
 * completed concurrently.
 */
public class AggregateOptimisticLockingTimeoutTest extends ContextTestSupport {

    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch releaseDownstream = new CountDownLatch(1);
    private final CountDownLatch timeoutGate = new CountDownLatch(1);
    private final AtomicBoolean pause = new AtomicBoolean(true);
    private ScheduledExecutorService timeoutChecker;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // the timeout checker only runs when the gate is open, so no group times out before the test is ready
        timeoutChecker = new ScheduledThreadPoolExecutor(1) {
            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(
                    Runnable command, long initialDelay, long delay, TimeUnit unit) {
                return super.scheduleWithFixedDelay(() -> {
                    if (awaitLatch(timeoutGate)) {
                        command.run();
                    }
                }, initialDelay, delay, unit);
            }
        };
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        release.countDown();
        releaseDownstream.countDown();
        timeoutGate.countDown();
        super.tearDown();
        timeoutChecker.shutdownNow();
    }

    @Test
    public void testCompletionDoesNotRemoveTimeoutOfNewGroup() throws Exception {
        // pauses the thread of b right after it removed the completed group [a, b] from the repository
        MemoryAggregationRepository repository = new MemoryAggregationRepository(true) {
            @Override
            public void remove(CamelContext camelContext, String key, Exchange exchange) {
                super.remove(camelContext, key, exchange);
                pauseIfThread("producer-b");
            }
        };
        addRoute(repository);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a+b", "c");
        mock.message(0).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("size");
        mock.message(1).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("timeout");

        send("a");
        Thread producer = new Thread(() -> send("b"), "producer-b");
        producer.start();
        assertTrue(awaitLatch(paused));

        // c starts a new group for the same key, which registers its completion timeout
        send("c");
        // then the thread of b continues completing the group [a, b]
        release.countDown();
        producer.join(10000);

        timeoutGate.countDown();
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTimeoutWithExchangeIdOfCompletedGroup() throws Exception {
        // pauses the thread of m1 before it adds its new group [m1], after it registered its completion timeout
        IdPreservingRepository repository = new IdPreservingRepository();
        addRoute(repository);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("m2+m3", "m1");
        mock.message(1).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("timeout");

        Thread producer = new Thread(() -> send("m1"), "producer-m1");
        producer.start();
        assertTrue(awaitLatch(paused));

        // m2 starts a group (the timeout entry of the key now holds the exchange id of m2), and m3 completes it;
        // the aggregated exchange (with the exchange id of m2) is in progress until it is released downstream
        send("m2");
        send("m3");
        // m1 adds its group [m1]; the timeout entry of the key still holds the exchange id of m2
        release.countDown();
        producer.join(10000);

        timeoutGate.countDown();
        // the group [m1] must be completed by the timeout while the exchange m2+m3 is in progress
        await("group [m1] completed by timeout").atMost(10, TimeUnit.SECONDS).until(() -> repository.getKeys().isEmpty());

        releaseDownstream.countDown();
        assertMockEndpointsSatisfied();
    }

    private void addRoute(AggregationRepository repository) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(repository)
                        .optimisticLocking().completionSize(2)
                        .completionTimeout(10).completionTimeoutCheckerInterval(10)
                        .timeoutCheckerExecutorService(timeoutChecker)
                        .process(exchange -> {
                            if ("m2+m3".equals(exchange.getIn().getBody(String.class))) {
                                awaitLatch(releaseDownstream);
                            }
                        })
                        .to("mock:result");
            }
        });
        context.start();
    }

    private void send(String body) {
        template.sendBodyAndHeader("direct:start", body, "id", "1");
    }

    private void pauseIfThread(String name) {
        if (Thread.currentThread().getName().equals(name) && pause.compareAndSet(true, false)) {
            paused.countDown();
            awaitLatch(release);
        }
    }

    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static class BodyInAggregatingStrategy implements AggregationStrategy {
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
     * An optimistic locking repository which stores a copy of the exchange (so an exchange keeps its exchange id, like
     * the JDBC repository), with a version per key.
     */
    private final class IdPreservingRepository extends ServiceSupport implements OptimisticLockingAggregationRepository {

        private static final String VERSION = "IdPreservingRepositoryVersion";

        private final Map<String, DefaultExchangeHolder> groups = new HashMap<>();
        private final Map<String, Long> versions = new HashMap<>();
        private final AtomicLong version = new AtomicLong();

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
            pauseIfThread("producer-m1");
            synchronized (this) {
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
        }

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized Exchange get(CamelContext camelContext, String key) {
            DefaultExchangeHolder holder = groups.get(key);
            if (holder == null) {
                return null;
            }
            Exchange answer = new DefaultExchange(camelContext);
            DefaultExchangeHolder.unmarshal(answer, holder);
            answer.setProperty(VERSION, versions.get(key));
            return answer;
        }

        @Override
        public synchronized void remove(CamelContext camelContext, String key, Exchange exchange) {
            Long expected = exchange.getProperty(VERSION, Long.class);
            if (expected == null || !expected.equals(versions.get(key))) {
                throw new OptimisticLockingException();
            }
            groups.remove(key);
            versions.remove(key);
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            // noop
        }

        @Override
        public synchronized Set<String> getKeys() {
            return Set.copyOf(groups.keySet());
        }
    }
}
