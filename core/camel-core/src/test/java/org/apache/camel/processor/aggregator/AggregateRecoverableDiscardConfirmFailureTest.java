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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With a {@link RecoverableAggregationRepository}, a discarded group must never be sent, also when the repository fails
 * to confirm it: the recover task confirms it again, and does not recover it. This is the same as for a processed
 * exchange (CAMEL-26995) and for an exchange moved to the dead letter channel (CAMEL-27008).
 */
class AggregateRecoverableDiscardConfirmFailureTest extends ContextTestSupport {

    private final DiscardRepository timeoutRepository = new DiscardRepository();
    private final DiscardRepository failureRepository = new DiscardRepository();
    private final DiscardRepository forceRepository = new DiscardRepository();
    private final AggregateController controller = new DefaultAggregateController();

    @Test
    void testDiscardOnCompletionTimeoutConfirmThrows() throws Exception {
        timeoutRepository.throwOnConfirm(false);

        send("direct:timeout", "A", "B");

        assertDiscardedOnce(timeoutRepository);
    }

    @Test
    void testDiscardOnCompletionTimeoutConfirmThrowsDuringRecoverCheck() throws Exception {
        // the confirm fails after the recover task has scanned the completed exchange: the check that is running must
        // not recover it either
        timeoutRepository.throwOnConfirm(true);

        send("direct:timeout", "A", "B");

        assertDiscardedOnce(timeoutRepository);
        assertTrue(timeoutRepository.scannedBeforeThrow, "The recover task should scan A+B before its confirm fails");
    }

    @Test
    void testDiscardOnAggregationFailureConfirmThrows() throws Exception {
        failureRepository.throwOnConfirm(false);

        Exchange out = send("direct:failure", "A", "B", "Kaboom");

        assertDiscardedOnce(failureRepository);
        assertNull(out.getException(), "The exchange that discarded the group should not fail");
    }

    @Test
    void testDiscardOnAggregationFailureConfirmThrowsDuringRecoverCheck() throws Exception {
        failureRepository.throwOnConfirm(true);

        Exchange out = send("direct:failure", "A", "B", "Kaboom");

        assertDiscardedOnce(failureRepository);
        assertTrue(failureRepository.scannedBeforeThrow, "The recover task should scan A+B before its confirm fails");
        assertNull(out.getException(), "The exchange that discarded the group should not fail");
    }

    @Test
    void testForceDiscardingConfirmThrows() throws Exception {
        forceRepository.throwOnConfirm(false);

        send("direct:force", "A", "B");
        assertEquals(1, controller.forceDiscardingOfGroup("1"));

        assertDiscardedOnce(forceRepository);
    }

    /**
     * Sends the bodies to the endpoint, with the same correlation key, and returns the last exchange.
     */
    private Exchange send(String uri, String... bodies) {
        Exchange out = null;
        for (String body : bodies) {
            out = template.send(uri, exchange -> {
                exchange.getMessage().setBody(body);
                exchange.getMessage().setHeader("id", 1);
            });
        }
        return out;
    }

    private void assertDiscardedOnce(DiscardRepository repository) {
        // the recover task confirms A+B again
        await().atMost(10, TimeUnit.SECONDS).until(() -> repository.completedBodies().isEmpty());
        // let the recover task run completely at least once more (a pass is complete when the next scan starts)
        int scans = repository.scans.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scans + 2);

        MockEndpoint mock = getMockEndpoint("mock:result");
        assertEquals(List.of(), mock.getReceivedExchanges().stream().map(e -> e.getMessage().getBody(String.class)).toList(),
                "The discarded group should not be sent");
        assertEquals(0, repository.recovered.get(), "The discarded group should not be recovered");
        assertEquals(2, repository.confirms.get(), "A+B should be confirmed twice");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:timeout")
                        .aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(timeoutRepository)
                        .completionSize(3).completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .discardOnCompletionTimeout()
                        .to("mock:result");

                from("direct:failure")
                        .aggregate(header("id"), new KaboomAggregationStrategy()).aggregationRepository(failureRepository)
                        .completionSize(3).discardOnAggregationFailure()
                        .to("mock:result");

                from("direct:force")
                        .aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(forceRepository)
                        .completionSize(3).aggregateController(controller)
                        .to("mock:result");
            }
        };
    }

    private static final class KaboomAggregationStrategy implements AggregationStrategy {
        private final AggregationStrategy delegate = new BodyInAggregatingStrategy();

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if ("Kaboom".equals(newExchange.getMessage().getBody(String.class))) {
                throw new IllegalArgumentException("Simulated failure: cannot aggregate Kaboom");
            }
            return delegate.aggregate(oldExchange, newExchange);
        }
    }

    /**
     * An in-memory recoverable repository which, like JdbcAggregationRepository, moves the exchange of a completed
     * group to a completed store, and deletes it from there when it is confirmed. Its first confirm throws, optionally
     * after a recover check has scanned the completed exchange (the check then waits for the lock that the discard
     * holds).
     */
    private static final class DiscardRepository extends ServiceSupport implements RecoverableAggregationRepository {

        private final Map<String, DefaultExchangeHolder> groups = new HashMap<>();
        private final Map<String, Completed> completed = new LinkedHashMap<>();
        private final AtomicInteger throwingConfirms = new AtomicInteger();
        private final AtomicInteger confirms = new AtomicInteger();
        private final AtomicInteger scans = new AtomicInteger();
        private final AtomicInteger recovered = new AtomicInteger();
        private final CountDownLatch scanned = new CountDownLatch(1);
        private volatile boolean throwAfterScan;
        private volatile boolean scannedBeforeThrow;

        void throwOnConfirm(boolean afterScan) {
            throwingConfirms.set(1);
            throwAfterScan = afterScan;
        }

        synchronized List<String> completedBodies() {
            return completed.values().stream().map(Completed::body).toList();
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
            completed.put(exchange.getExchangeId(),
                    new Completed(DefaultExchangeHolder.marshal(exchange, true), exchange.getIn().getBody(String.class)));
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            confirmWithResult(camelContext, exchangeId);
        }

        @Override
        public boolean confirmWithResult(CamelContext camelContext, String exchangeId) {
            synchronized (this) {
                Completed entry = completed.get(exchangeId);
                if (entry == null) {
                    return false;
                }
                confirms.incrementAndGet();
                if (throwingConfirms.getAndUpdate(n -> n > 0 ? n - 1 : 0) == 0) {
                    completed.remove(exchangeId);
                    return true;
                }
            }
            // outside the lock of the repository, so the recover task can scan
            if (throwAfterScan) {
                try {
                    scannedBeforeThrow = scanned.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            // as JdbcAggregationRepository when its DELETE fails (the database is not available)
            throw new IllegalStateException("Simulated failure: the repository cannot confirm " + exchangeId);
        }

        @Override
        public synchronized Set<String> getKeys() {
            return Set.copyOf(groups.keySet());
        }

        @Override
        public synchronized Set<String> scan(CamelContext camelContext) {
            scans.incrementAndGet();
            if (!completed.isEmpty()) {
                scanned.countDown();
            }
            return new LinkedHashSet<>(completed.keySet());
        }

        @Override
        public synchronized Exchange recover(CamelContext camelContext, String exchangeId) {
            Completed entry = completed.get(exchangeId);
            if (entry == null) {
                return null;
            }
            recovered.incrementAndGet();
            return unmarshal(camelContext, entry.holder());
        }

        private record Completed(DefaultExchangeHolder holder, String body) {
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
            return "mock:dead";
        }

        @Override
        public void setMaximumRedeliveries(int maximumRedeliveries) {
        }

        @Override
        public int getMaximumRedeliveries() {
            return 2;
        }
    }
}
