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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
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
 * A route stop does not wait for a recover pass that is running. When the route is started again before that pass ends,
 * the old pass must not run next to the recover task of the new start: an exchange must be recovered and sent once.
 */
class AggregateRecoverRestartTest extends ContextTestSupport {

    private final RecoverableRepository repository = new RecoverableRepository();
    private final CountDownLatch deliveryStarted = new CountDownLatch(1);
    private final CountDownLatch releaseDelivery = new CountDownLatch(1);
    private final List<ScheduledExecutorService> scheduledPools = new CopyOnWriteArrayList<>();

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        releaseDelivery.countDown();
        repository.releaseAll();
        super.tearDown();
    }

    /**
     * Without the fix the new pass waits in scan with an empty snapshot, the old pass sends Z and clears the shared
     * snapshot when it ends, and the new pass sends Z again. With the fix the new task skips while the old pass runs,
     * and Z is sent once.
     */
    @Test
    void testRecoveredOnceWhenRouteRestartedDuringScan() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Z");

        // a completed exchange that was not confirmed (such as after a crash), the next recover pass waits in scan
        Gate oldPass = repository.gateNextScan();
        repository.addCompleted(context, "Z", "1");
        oldPass.awaitEntered();

        // restart the route while the old recover pass runs, a new pass would wait in scan as well
        Gate newPass = repository.gateNextScan();
        SizedScheduledExecutorService newRecoverService = restartRoute();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> newPass.entered.getCount() == 0 || newRecoverService.getCompletedTaskCount() > 0);

        // the old pass goes on and ends (its thread pool was shut down by the stop), then the new pass goes on
        oldPass.release();
        oldPass.thread.join(TimeUnit.SECONDS.toMillis(20));
        assertFalse(oldPass.thread.isAlive(), "The old recover pass should have ended");
        newPass.release();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> newPass.scan.get() > 0 && repository.scans.get() > newPass.scan.get());

        // the recovered exchange is sent and confirmed
        assertTrue(deliveryStarted.await(20, TimeUnit.SECONDS), "The recovered exchange should be sent");
        releaseDelivery.countDown();
        assertSentOnce(mock);
    }

    /**
     * Without the fix the new pass (with the new lock of the new start) sends Z while the old pass loads it, and the
     * old pass then sends the copy it loaded. With the fix the new task skips while the old pass runs, and Z is sent
     * once.
     */
    @Test
    void testRecoveredOnceWhenRouteRestartedDuringRecover() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Z");
        releaseDelivery.countDown();

        // the old recover pass waits while it loads the exchange to recover (it has checked it is not in progress)
        Gate oldPass = repository.gateNextRecover();
        repository.addCompleted(context, "Z", "1");
        oldPass.awaitEntered();

        // restart the route, and let the recover task of the new start run once
        SizedScheduledExecutorService newRecoverService = restartRoute();
        await().atMost(20, TimeUnit.SECONDS).until(() -> newRecoverService.getCompletedTaskCount() > 0);

        // the old pass goes on and ends
        oldPass.release();
        oldPass.thread.join(TimeUnit.SECONDS.toMillis(20));
        assertFalse(oldPass.thread.isAlive(), "The old recover pass should have ended");

        assertSentOnce(mock);
    }

    private SizedScheduledExecutorService restartRoute() throws Exception {
        context.getRouteController().stopRoute("agg");
        scheduledPools.clear();
        context.getRouteController().startRoute("agg");
        // the recover task of the aggregator is the only scheduled thread pool of the route
        assertEquals(1, scheduledPools.size());
        return (SizedScheduledExecutorService) scheduledPools.get(0);
    }

    private void assertSentOnce(MockEndpoint mock) throws Exception {
        mock.assertIsSatisfied();
        await().atMost(20, TimeUnit.SECONDS).until(repository.completed::isEmpty);

        // and nothing is sent again by a later pass
        int scans = repository.scans.get();
        await().atMost(20, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scans + 2);
        assertEquals(1, mock.getReceivedCounter(), "The recovered exchange should be sent once");
        assertEquals(1, repository.recovered.get(), "The exchange should be recovered once");
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
                        .aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(repository)
                        .completionSize(2)
                        .process(e -> {
                            deliveryStarted.countDown();
                            releaseDelivery.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:result");
            }
        };
    }

    /**
     * Lets a test hold the next call of scan or recover until it is released.
     */
    private static final class Gate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicInteger scan = new AtomicInteger();
        private volatile Thread thread;

        void pass() {
            thread = Thread.currentThread();
            entered.countDown();
            try {
                released.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void awaitEntered() throws InterruptedException {
            assertTrue(entered.await(20, TimeUnit.SECONDS), "The recover task should reach the gate");
        }

        void release() {
            released.countDown();
        }
    }

    /**
     * An in-memory recoverable repository which moves a removed exchange to a completed store (like the JDBC
     * repository).
     */
    private static final class RecoverableRepository extends ServiceSupport implements RecoverableAggregationRepository {

        private final Map<String, DefaultExchangeHolder> groups = new HashMap<>();
        private final Map<String, DefaultExchangeHolder> completed = new ConcurrentHashMap<>();
        private final AtomicInteger scans = new AtomicInteger();
        private final AtomicInteger recovered = new AtomicInteger();
        private final AtomicReference<Gate> nextScanGate = new AtomicReference<>();
        private final AtomicReference<Gate> nextRecoverGate = new AtomicReference<>();
        private final Set<Gate> gates = ConcurrentHashMap.newKeySet();

        Gate gateNextScan() {
            Gate gate = new Gate();
            gates.add(gate);
            nextScanGate.set(gate);
            return gate;
        }

        Gate gateNextRecover() {
            Gate gate = new Gate();
            gates.add(gate);
            nextRecoverGate.set(gate);
            return gate;
        }

        void releaseAll() {
            gates.forEach(Gate::release);
        }

        void addCompleted(CamelContext camelContext, String body, String key) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().setBody(body);
            exchange.setProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY, key);
            completed.put(exchange.getExchangeId(), DefaultExchangeHolder.marshal(exchange, true));
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
        public synchronized Set<String> getKeys() {
            return Set.copyOf(groups.keySet());
        }

        @Override
        public Set<String> scan(CamelContext camelContext) {
            Gate gate = nextScanGate.getAndSet(null);
            if (gate != null) {
                gate.pass();
            }
            int scan = scans.incrementAndGet();
            if (gate != null) {
                gate.scan.set(scan);
            }
            return Set.copyOf(completed.keySet());
        }

        @Override
        public Exchange recover(CamelContext camelContext, String exchangeId) {
            DefaultExchangeHolder holder = completed.get(exchangeId);
            // a slow load returns what it read at its start
            Gate gate = nextRecoverGate.getAndSet(null);
            if (gate != null) {
                gate.pass();
            }
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
