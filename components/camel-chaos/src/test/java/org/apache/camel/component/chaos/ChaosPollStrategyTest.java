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
package org.apache.camel.component.chaos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.support.ScheduledPollConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The poll strategy of chaos on its own: a delay that is interrupted (the consumer is stopping) skips the poll and
 * keeps the interrupted status. The virtual clock is static, so this class runs alone.
 */
@Isolated
class ChaosPollStrategyTest {

    private CamelContext context;
    private FakeClock clock;

    @BeforeEach
    void start() {
        clock = FakeClock.install();
        context = new DefaultCamelContext();
        context.getPropertiesComponent().addInitialProperty("camel.chaos.enabled", "true");
        context.start();
    }

    @AfterEach
    void stop() {
        try {
            context.stop();
        } finally {
            clock.close();
        }
    }

    @Test
    void anInterruptedDelaySkipsThePollAndKeepsTheInterrupt() {
        ChaosManager chaos = ChaosManager.get(context);
        String id = chaos.addRule("endpoint=testpoll:p* delay duration=1s");
        Counting delegate = new Counting();
        ChaosPollStrategy strategy = new ChaosPollStrategy(chaos, delegate);
        Endpoint endpoint = context.getEndpoint("mock:x");

        // the endpoint does not match: the strategy of the consumer is asked
        assertThat(strategy.begin(null, endpoint)).isTrue();
        assertThat(delegate.begins.get()).isEqualTo(1);

        TestPollComponent component = new TestPollComponent();
        context.addComponent("testpoll", component);
        Endpoint polled = context.getEndpoint("testpoll:p");
        Thread.currentThread().interrupt();
        try {
            assertThat(strategy.begin(null, polled)).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        // the poll was skipped before the strategy of the consumer was asked
        assertThat(delegate.begins.get()).isEqualTo(1);
        assertThat(clock.sleeps()).isEmpty();

        // not interrupted: it waits, then asks the strategy of the consumer
        assertThat(strategy.begin(null, polled)).isTrue();
        assertThat(clock.sleeps()).containsExactly(1000L);
        assertThat(delegate.begins.get()).isEqualTo(2);
        assertThat(chaos.report().toJson().toJson()).contains("\"id\":\"" + id + "\"");
    }

    @Test
    void aFaultAfterAPollThatFailedByItselfGetsTheNextRetryCounter() throws Exception {
        ChaosManager chaos = ChaosManager.get(context);
        context.addComponent("testpoll", new TestPollComponent());
        Endpoint polled = context.getEndpoint("testpoll:p");
        chaos.addRule("endpoint=testpoll:p* error rate=every:2");
        Counting delegate = new Counting();
        delegate.retry = true;
        ChaosPollStrategy strategy = new ChaosPollStrategy(chaos, delegate);

        // the first poll begins, and fails by itself: the consumer counts it as attempt 0
        assertThat(strategy.begin(null, polled)).isTrue();
        assertThat(strategy.rollback(null, polled, 0, new IllegalStateException("by itself"))).isTrue();
        // the retry fails in begin with the fault: the consumer passes 0 again, the strategy of the consumer gets 1
        Exception fault = null;
        try {
            strategy.begin(null, polled);
        } catch (ChaosException e) {
            fault = e;
        }
        assertThat(fault).isNotNull();
        assertThat(strategy.rollback(null, polled, 0, fault)).isTrue();
        assertThat(delegate.counters).containsExactly(0, 1);
    }

    @Test
    void aPollThatTheStrategyOfTheConsumerSkipsEndsTheRun() throws Exception {
        ChaosManager chaos = ChaosManager.get(context);
        context.addComponent("testpoll", new TestPollComponent());
        Endpoint polled = context.getEndpoint("testpoll:p");
        Counting delegate = new Counting();
        delegate.retry = true;
        ChaosPollStrategy strategy = new ChaosPollStrategy(chaos, delegate);

        chaos.addRule("endpoint=testpoll:p* error until=1");
        assertThat(strategy.rollback(null, polled, -1, faultOf(strategy, polled))).isTrue();
        // the retry: the rule ended, and the strategy of the consumer skips the poll, which ends the run
        delegate.skip = true;
        assertThat(strategy.begin(null, polled)).isFalse();
        // the next run counts from 0 again
        delegate.skip = false;
        chaos.addRule("endpoint=testpoll:p* error until=1");
        assertThat(strategy.rollback(null, polled, -1, faultOf(strategy, polled))).isTrue();
        assertThat(delegate.counters).containsExactly(0, 0);
    }

    private static Exception faultOf(ChaosPollStrategy strategy, Endpoint endpoint) {
        try {
            strategy.begin(null, endpoint);
        } catch (ChaosException e) {
            return e;
        }
        throw new AssertionError("The rule did not fail the poll");
    }

    @Test
    void aPollThatWaitedIsSkippedWhenTheConsumerWasSuspendedMeanwhile() throws Exception {
        ChaosManager chaos = ChaosManager.get(context);
        context.addComponent("testpoll", new TestPollComponent());
        Endpoint polled = context.getEndpoint("testpoll:p?initialDelay=3600000&delay=3600000");
        Consumer consumer = polled.createConsumer(exchange -> {
        });
        consumer.start();
        try {
            chaos.addRule("endpoint=testpoll:p* delay duration=1s");
            Counting delegate = new Counting();
            ChaosPollStrategy strategy = new ChaosPollStrategy(chaos, delegate);
            ((ScheduledPollConsumer) consumer).suspend();

            assertThat(strategy.begin(consumer, polled)).isFalse();

            assertThat(clock.sleeps()).containsExactly(1000L);
            assertThat(delegate.begins.get()).isZero();
        } finally {
            consumer.stop();
        }
    }

    @Test
    void concurrentPollRunsOfOneConsumerEachCountTheirOwnRetries() throws Exception {
        // a consumer with concurrentConsumers runs several polls at the same time, each with its own retries
        ChaosManager chaos = ChaosManager.get(context);
        context.addComponent("testpoll", new TestPollComponent());
        Endpoint polled = context.getEndpoint("testpoll:p");
        chaos.addRule("endpoint=testpoll:p* error");
        Map<String, List<Integer>> counters = new ConcurrentHashMap<>();
        PollingConsumerPollStrategy retrying = new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                // nothing
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) {
                counters.computeIfAbsent(Thread.currentThread().getName(), k -> new ArrayList<>()).add(retryCounter);
                return retryCounter < 2;
            }
        };
        ChaosPollStrategy strategy = new ChaosPollStrategy(chaos, retrying);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                runs.add(pool.submit(() -> {
                    for (int i = 0; i < 500; i++) {
                        pollRun(strategy, polled);
                    }
                    return null;
                }));
            }
            for (Future<?> f : runs) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(counters).hasSize(4);
        for (List<Integer> list : counters.values()) {
            assertThat(list).hasSize(1500);
            for (int i = 0; i < list.size(); i++) {
                assertThat(list.get(i)).isEqualTo(i % 3);
            }
        }
    }

    // a poll run with its retries, as ScheduledPollConsumer does it, with a poll that finds nothing
    private static void pollRun(PollingConsumerPollStrategy strategy, Endpoint endpoint) throws Exception {
        int retryCounter = -1;
        boolean done = false;
        while (!done) {
            done = true;
            try {
                if (strategy.begin(null, endpoint)) {
                    retryCounter++;
                    strategy.commit(null, endpoint, 0);
                }
            } catch (Exception e) {
                done = !strategy.rollback(null, endpoint, retryCounter, e);
            }
        }
    }

    @Test
    void withoutAStrategyTheDefaultOneIsUsed() {
        ChaosPollStrategy strategy = new ChaosPollStrategy(ChaosManager.get(context), null);
        assertThat(strategy.getDelegate()).isNotNull();
        assertThat(strategy.begin(null, context.getEndpoint("mock:x"))).isTrue();
    }

    private static final class Counting implements PollingConsumerPollStrategy {
        final AtomicInteger begins = new AtomicInteger();
        final List<Integer> counters = new CopyOnWriteArrayList<>();
        boolean retry;
        boolean skip;

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            begins.incrementAndGet();
            return !skip;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            // nothing
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) {
            counters.add(retryCounter);
            return retry;
        }
    }
}
