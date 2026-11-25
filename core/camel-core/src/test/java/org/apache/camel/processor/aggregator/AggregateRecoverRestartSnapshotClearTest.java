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

import java.lang.reflect.Field;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.concurrent.SizedScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recover check of the old start that ends while the route is started again must not clear the snapshot of the
 * exchanges in progress that the recover check of the new start has taken: the new check would then recover and send an
 * exchange that is still being processed, or that was processed and is not confirmed yet.
 * <p>
 * The test replaces the snapshot set with a set that behaves the same, and that holds the old check in the clear() of
 * its finally block, so the route can be restarted at that point.
 */
class AggregateRecoverRestartSnapshotClearTest extends ContextTestSupport {

    private final RecoverableRepository repository = new RecoverableRepository();
    private final AtomicInteger deliveries = new AtomicInteger();
    private final AtomicInteger concurrent = new AtomicInteger();
    private final AtomicInteger maxConcurrent = new AtomicInteger();
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final CountDownLatch firstDeliveryStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstDelivery = new CountDownLatch(1);
    private final List<ScheduledExecutorService> scheduledPools = new CopyOnWriteArrayList<>();
    private volatile boolean blockFirstDelivery;
    private GatedSet gated;

    /**
     * Whether the aggregator sends with parallelProcessing (a thread pool) instead of the default single thread
     * executor.
     */
    protected boolean parallelProcessing() {
        return false;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        releaseFirstDelivery.countDown();
        if (gated != null) {
            gated.release();
        }
        repository.releaseAll();
        super.tearDown();
    }

    /**
     * The aggregated exchange is processed and its confirm returns false: it is kept as unconfirmed, so a recover check
     * must only try the confirm again, and never send it again.
     */
    @Test
    void testProcessedUnconfirmedExchangeIsNotSentAgainAfterRestart() throws Exception {
        installGatedSnapshot();
        repository.confirmFails.set(true);

        template.sendBodyAndHeader("direct:start", "A", "id", "1");
        template.sendBodyAndHeader("direct:start", "B", "id", "1");
        await().atMost(20, TimeUnit.SECONDS).until(() -> received.size() == 1);

        Gate newCheck = restartWhileOldCheckIsInFinally();

        // let the later confirms succeed, so everything settles
        repository.confirmFails.set(false);
        newCheck.release();
        await().atMost(20, TimeUnit.SECONDS).until(repository.completed::isEmpty);
        int scans = repository.scans.get();
        await().atMost(20, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scans + 2);

        assertEquals(0, repository.recovered.get(), "A processed (unconfirmed) exchange must not be recovered");
        assertEquals(List.of("A+B"), received, "A processed exchange must not be sent again");
    }

    /**
     * The aggregated exchange is still being processed when the route is restarted: it must not be recovered, so no
     * other thread processes it again (with parallelProcessing at the same time).
     */
    @Test
    void testInProgressExchangeIsNotSentAgainAfterRestart() throws Exception {
        installGatedSnapshot();
        blockFirstDelivery = true;

        template.sendBodyAndHeader("direct:start", "A", "id", "1");
        template.sendBodyAndHeader("direct:start", "B", "id", "1");
        assertTrue(firstDeliveryStarted.await(20, TimeUnit.SECONDS));

        Gate newCheck = restartWhileOldCheckIsInFinally();
        newCheck.release();
        int scans = repository.scans.get();
        await().atMost(20, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scans + 2);

        assertEquals(0, repository.recovered.get(), "An exchange in progress must not be recovered");
        assertEquals(1, maxConcurrent.get(), "The exchange must not be processed by two threads at once");

        // the first delivery ends and is confirmed, and nothing is sent again
        releaseFirstDelivery.countDown();
        await().atMost(20, TimeUnit.SECONDS).until(repository.completed::isEmpty);
        int scansAfter = repository.scans.get();
        await().atMost(20, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scansAfter + 2);
        assertEquals(1, deliveries.get(), "The exchange must be processed once: " + received);
        assertEquals(0, repository.recovered.get(), "An exchange in progress must not be recovered");
    }

    /**
     * Holds the next recover check of the old start in the clear() of its finally block, restarts the route, and lets
     * the recover task of the new start run at least once (without the fix it takes its snapshot and waits in scan).
     * Then lets the old check end. Returns the gate where the first scan of the new start waits.
     */
    private Gate restartWhileOldCheckIsInFinally() throws Exception {
        gated.armFinallyClear();
        assertTrue(gated.entered.await(20, TimeUnit.SECONDS), "The old recover check should reach its finally block");
        Thread oldCheck = gated.thread;

        Gate newCheck = repository.gateNextScan();
        context.getRouteController().stopRoute("agg");
        scheduledPools.clear();
        context.getRouteController().startRoute("agg");
        // the recover task of the aggregator is the only scheduled thread pool of the route
        assertEquals(1, scheduledPools.size());
        SizedScheduledExecutorService newRecoverService = (SizedScheduledExecutorService) scheduledPools.get(0);
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> newCheck.entered.getCount() == 0 || newRecoverService.getCompletedTaskCount() > 0);

        // the old check now clears the snapshot and ends
        gated.release();
        oldCheck.join(TimeUnit.SECONDS.toMillis(20));
        assertFalse(oldCheck.isAlive(), "The old recover check should have ended");

        // the first scan of the new start (during or after the old check) waits until released
        newCheck.awaitEntered();
        return newCheck;
    }

    private void installGatedSnapshot() throws Exception {
        AggregateProcessor aggregator = context.getProcessor("aggregator", AggregateProcessor.class);
        Field f = AggregateProcessor.class.getDeclaredField("inProgressCompleteExchangesForRecoveryTask");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> current = (Set<String>) f.get(aggregator);
        gated = new GatedSet();
        gated.delegate.addAll(current);
        f.set(aggregator, gated);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        DefaultThreadPoolFactory factory = new DefaultThreadPoolFactory() {
            @Override
            public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
                ScheduledExecutorService pool = super.newScheduledThreadPool(profile, threadFactory);
                scheduledPools.add(pool);
                return pool;
            }
        };
        factory.setCamelContext(answer);
        answer.getExecutorServiceManager().setThreadPoolFactory(factory);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("agg")
                        .aggregate(header("id"), new BodyInAggregatingStrategy()).id("aggregator")
                        .aggregationRepository(repository)
                        .completionSize(2).parallelProcessing(parallelProcessing())
                        .process(e -> {
                            int n = deliveries.incrementAndGet();
                            int c = concurrent.incrementAndGet();
                            maxConcurrent.accumulateAndGet(c, Math::max);
                            try {
                                received.add(e.getMessage().getBody(String.class));
                                if (n == 1 && blockFirstDelivery) {
                                    firstDeliveryStarted.countDown();
                                    releaseFirstDelivery.await(30, TimeUnit.SECONDS);
                                }
                            } finally {
                                concurrent.decrementAndGet();
                            }
                        });
            }
        };
    }

    /**
     * A set that behaves as ConcurrentHashMap.newKeySet(), and can hold the clear() in the finally block of a recover
     * check (the first clear() of the recover thread after it took its snapshot with addAll).
     */
    private static final class GatedSet extends AbstractSet<String> {
        final Set<String> delegate = ConcurrentHashMap.newKeySet();
        final AtomicBoolean armed = new AtomicBoolean();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        final ThreadLocal<Boolean> snapshotTaken = ThreadLocal.withInitial(() -> Boolean.FALSE);
        volatile Thread thread;

        void armFinallyClear() {
            armed.set(true);
        }

        void release() {
            released.countDown();
        }

        @Override
        public boolean addAll(Collection<? extends String> c) {
            // the recover check takes its snapshot with addAll, so the next clear() of this thread is the one in finally
            snapshotTaken.set(Boolean.TRUE);
            return delegate.addAll(c);
        }

        @Override
        public void clear() {
            boolean finallyClear = snapshotTaken.get();
            snapshotTaken.set(Boolean.FALSE);
            if (finallyClear && Thread.currentThread().getName().contains("AggregateRecoverChecker")
                    && armed.compareAndSet(true, false)) {
                thread = Thread.currentThread();
                entered.countDown();
                try {
                    released.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            delegate.clear();
        }

        @Override
        public boolean add(String s) {
            return delegate.add(s);
        }

        @Override
        public boolean remove(Object o) {
            return delegate.remove(o);
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public Iterator<String> iterator() {
            return delegate.iterator();
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }

    /**
     * Lets a test hold the next call of scan until it is released.
     */
    private static final class Gate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        void pass() {
            entered.countDown();
            try {
                released.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void awaitEntered() throws InterruptedException {
            assertTrue(entered.await(20, TimeUnit.SECONDS), "The recover check should reach scan");
        }

        void release() {
            released.countDown();
        }
    }

    /**
     * An in-memory recoverable repository which moves a removed exchange to a completed store (like the JDBC
     * repository), and whose confirm can return false.
     */
    private static final class RecoverableRepository extends ServiceSupport implements RecoverableAggregationRepository {

        private final Map<String, DefaultExchangeHolder> groups = new HashMap<>();
        private final Map<String, DefaultExchangeHolder> completed = new ConcurrentHashMap<>();
        private final AtomicInteger scans = new AtomicInteger();
        private final AtomicInteger recovered = new AtomicInteger();
        private final AtomicBoolean confirmFails = new AtomicBoolean();
        private final AtomicReference<Gate> nextScanGate = new AtomicReference<>();
        private final Set<Gate> gates = ConcurrentHashMap.newKeySet();

        Gate gateNextScan() {
            Gate gate = new Gate();
            gates.add(gate);
            nextScanGate.set(gate);
            return gate;
        }

        void releaseAll() {
            gates.forEach(Gate::release);
        }

        @Override
        public synchronized Exchange add(CamelContext camelContext, String key, Exchange exchange) {
            Exchange old = get(camelContext, key);
            groups.put(key, DefaultExchangeHolder.marshal(exchange, true));
            return old;
        }

        @Override
        public synchronized Exchange get(CamelContext camelContext, String key) {
            DefaultExchangeHolder holder = groups.get(key);
            return holder != null ? unmarshal(camelContext, holder) : null;
        }

        @Override
        public synchronized void remove(CamelContext camelContext, String key, Exchange exchange) {
            groups.remove(key);
            completed.put(exchange.getExchangeId(), DefaultExchangeHolder.marshal(exchange, true));
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            completed.remove(exchangeId);
        }

        @Override
        public boolean confirmWithResult(CamelContext camelContext, String exchangeId) {
            if (confirmFails.get()) {
                return false;
            }
            return completed.remove(exchangeId) != null;
        }

        @Override
        public synchronized Set<String> getKeys() {
            return Set.copyOf(groups.keySet());
        }

        @Override
        public Set<String> scan(CamelContext camelContext) {
            Gate gate = nextScanGate.getAndSet(null);
            if (gate != null) {
                gate.pass();
            }
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

        private static Exchange unmarshal(CamelContext camelContext, DefaultExchangeHolder holder) {
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
