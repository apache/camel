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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With a {@link RecoverableAggregationRepository}, an aggregated exchange that was processed must be sent once, also
 * when the repository fails to confirm it: the recover task confirms it again, and does not send it again. CAMEL-17613
 * did this for a confirm that returns {@code false}, CAMEL-26995 for a confirm that throws (for example
 * JdbcAggregationRepository while the database is not available).
 */
public class AggregateRecoverableConfirmFailureTest extends ContextTestSupport {

    private final ConfirmFailingRepository repository = new ConfirmFailingRepository();

    @Test
    public void testConfirmThrows() throws Exception {
        repository.throwOnConfirm("A+B", 1);

        MockEndpoint mock = sendGroups("A+B");

        // the recover task confirms A+B again
        await().atMost(10, TimeUnit.SECONDS).until(() -> repository.completedBodies().isEmpty());
        assertSentOnce(mock, "A+B");
        assertEquals(2, repository.confirms("A+B"), "A+B should be confirmed twice");
    }

    @Test
    public void testConfirmReturnsFalse() throws Exception {
        // as JdbcAggregationRepository when its DELETE removes no row (CAMEL-17613)
        repository.falseOnConfirm("A+B", 1);

        MockEndpoint mock = sendGroups("A+B");

        // the recover task confirms A+B again
        await().atMost(10, TimeUnit.SECONDS).until(() -> repository.completedBodies().isEmpty());
        assertSentOnce(mock, "A+B");
        assertEquals(2, repository.confirms("A+B"), "A+B should be confirmed twice");
    }

    @Test
    public void testRetriedConfirmThrows() throws Exception {
        // the confirm of A+B keeps failing, and A+B comes first in the scan: the recover task must confirm C+D anyway
        repository.throwOnConfirm("A+B", Integer.MAX_VALUE);
        repository.throwOnConfirm("C+D", 1);

        MockEndpoint mock = sendGroups("A+B", "C+D");

        await("C+D confirmed while the confirm of A+B keeps failing").atMost(10, TimeUnit.SECONDS)
                .until(() -> List.of("A+B").equals(repository.completedBodies()));
        assertSentOnce(mock, "A+B", "C+D");
        assertTrue(repository.confirms("A+B") > 2, "The recover task should try to confirm A+B again");
        assertEquals(2, repository.confirms("C+D"), "C+D should be confirmed twice");
    }

    /**
     * Sends a group of two exchanges for each aggregated body ("A+B" is sent as A and B), and waits until each
     * aggregated exchange is sent.
     */
    private MockEndpoint sendGroups(String... aggregatedBodies) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived((Object[]) aggregatedBodies);
        for (int i = 0; i < aggregatedBodies.length; i++) {
            for (String body : aggregatedBodies[i].split("\\+")) {
                template.sendBodyAndHeader("direct:start", body, "id", i);
            }
        }
        mock.assertIsSatisfied();
        return mock;
    }

    private void assertSentOnce(MockEndpoint mock, String... aggregatedBodies) {
        // let the recover task run completely at least once more (a pass is complete when the next scan starts)
        int scans = repository.scans.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scans + 2);

        List<String> received = new ArrayList<>();
        for (Exchange exchange : mock.getReceivedExchanges()) {
            boolean redelivered = exchange.getMessage().getHeader(Exchange.REDELIVERED, false, Boolean.class);
            received.add(exchange.getMessage().getBody(String.class) + (redelivered ? " (redelivered)" : ""));
        }
        assertEquals(List.of(aggregatedBodies), received, "Each aggregated exchange should be sent once");
        assertEquals(0, repository.recovered.get(), "No exchange should be recovered");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(repository)
                        .completionSize(2)
                        .to("mock:result");
            }
        };
    }

    /**
     * An in-memory recoverable repository which, like JdbcAggregationRepository, moves the exchange of a completed
     * group to a completed store, and deletes it from there when it is confirmed. The scan returns the completed
     * exchanges in the order they were completed. A confirm can be made to throw, or to return {@code false}, for an
     * aggregated body.
     */
    private static final class ConfirmFailingRepository extends ServiceSupport
            implements RecoverableAggregationRepository {

        private final Map<String, DefaultExchangeHolder> groups = new HashMap<>();
        private final Map<String, Completed> completed = new LinkedHashMap<>();
        private final Map<String, AtomicInteger> throwingConfirms = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> falseConfirms = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> confirms = new ConcurrentHashMap<>();
        private final AtomicInteger scans = new AtomicInteger();
        private final AtomicInteger recovered = new AtomicInteger();

        void throwOnConfirm(String body, int times) {
            throwingConfirms.put(body, new AtomicInteger(times));
        }

        void falseOnConfirm(String body, int times) {
            falseConfirms.put(body, new AtomicInteger(times));
        }

        int confirms(String body) {
            AtomicInteger count = confirms.get(body);
            return count != null ? count.get() : 0;
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
        public synchronized boolean confirmWithResult(CamelContext camelContext, String exchangeId) {
            Completed entry = completed.get(exchangeId);
            if (entry == null) {
                return false;
            }
            String body = entry.body();
            confirms.computeIfAbsent(body, k -> new AtomicInteger()).incrementAndGet();
            if (countDown(throwingConfirms, body)) {
                // as JdbcAggregationRepository when its DELETE fails (the database is not available)
                throw new IllegalStateException("Simulated failure: the repository cannot confirm " + body);
            }
            if (countDown(falseConfirms, body)) {
                return false;
            }
            completed.remove(exchangeId);
            return true;
        }

        private static boolean countDown(Map<String, AtomicInteger> counters, String body) {
            AtomicInteger counter = counters.get(body);
            return counter != null && counter.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
        }

        @Override
        public synchronized Set<String> getKeys() {
            return Set.copyOf(groups.keySet());
        }

        @Override
        public synchronized Set<String> scan(CamelContext camelContext) {
            scans.incrementAndGet();
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
