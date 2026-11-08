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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.KeyValueAggregationRepository;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aggregation strategy whose onCompletion throws (its javadoc says it must not): the aggregated exchange is booked
 * as in progress already, and its group has been removed from the repository. The aggregated exchange must then be
 * failed, as when the thread pool rejects it, so that the aggregator no longer counts it as in progress and a
 * recoverable repository recovers it, instead of skipping it forever as being in progress.
 */
class AggregateOnCompletionThrowsTest extends ContextTestSupport {

    private final AtomicBoolean throwOnce = new AtomicBoolean(true);
    private final Set<String> alwaysThrow = ConcurrentHashMap.newKeySet();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private final List<Exchange> handled = new CopyOnWriteArrayList<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testFailedAggregatedExchangeIsRecovered() throws Exception {
        AggregateProcessor aggregator = addRoute(recoverableRepository(), false);

        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("A1+A2");
        mock.expectedHeaderReceived(Exchange.REDELIVERED, true);
        mock.setAssertPeriod(500);

        send("A1", "A");
        Exchange last = send("A2", "A");
        // the message that completed the group fails with the exception from onCompletion, as before
        assertInstanceOf(IllegalStateException.class, last.getException());

        // A1+A2 is still in the completed store of the repository, so it is recovered
        assertMockEndpointsSatisfied();
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, aggregator.getInProgressCompleteExchanges()));
    }

    @Test
    void testFailedAggregatedExchangeIsNoLongerInProgress() throws Exception {
        AggregateProcessor aggregator = addRoute(null, false);

        getMockEndpoint("mock:aggregated").expectedBodiesReceived("B1+B2");

        send("A1", "A");
        Exchange last = send("A2", "A");
        assertInstanceOf(IllegalStateException.class, last.getException());

        // the aggregated exchange is handed to the exception handler, and its on completions run as failed
        assertEquals(1, handled.size());
        assertEquals("A1+A2", handled.get(0).getIn().getBody(String.class));
        assertInstanceOf(IllegalStateException.class, handled.get(0).getException());
        assertEquals(List.of("A1+A2"), failures);
        assertEquals(0, aggregator.getInProgressCompleteExchanges());

        // the next group is sent as usual
        send("B1", "B");
        send("B2", "B");
        assertMockEndpointsSatisfied();
    }

    @Test
    void testFailedBatchCompletionSendsOtherGroups() throws Exception {
        AggregateProcessor aggregator = addRoute(recoverableRepository(), true);

        // the batch consumer completes the groups A and B at once: whichever group's onCompletion throws is
        // recovered, and the other one is still sent
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceivedInAnyOrder("A1+A2", "B1");
        mock.setAssertPeriod(500);

        sendBatch("A1", "A");
        sendBatch("B1", "B");
        Exchange last = sendBatch("A2", "A");
        assertInstanceOf(IllegalStateException.class, last.getException());

        assertMockEndpointsSatisfied();
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, aggregator.getInProgressCompleteExchanges()));
    }

    @Test
    void testRecoverGoesOnAfterAnExchangeThatFailsAgain() throws Exception {
        addRoute(new OrderedRecoverableRepository(), false);
        // onCompletion of A1+A2 throws each time, so it fails again each time the recover task resubmits it, and it is
        // the first one the recover task finds: the recover task must still go on and recover B1+B2
        alwaysThrow.add("A1+A2");

        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("B1+B2");
        mock.setAssertPeriod(500);

        send("A1", "A");
        send("A2", "A");
        // onCompletion of B1+B2 throws once (throwOnce), so it is in the completed store too
        send("B1", "B");
        Exchange last = send("B2", "B");
        assertInstanceOf(IllegalStateException.class, last.getException());

        assertMockEndpointsSatisfied();
        // and A1+A2 was recovered and failed again
        assertTrue(failures.stream().filter("A1+A2"::equals).count() >= 2);
    }

    private static KeyValueAggregationRepository recoverableRepository() {
        KeyValueAggregationRepository repository = new KeyValueAggregationRepository(new MemoryKeyValueRepository());
        repository.setUseRecovery(true);
        repository.setRecoveryInterval(100);
        return repository;
    }

    private Exchange send(String body, String id) {
        return template.send("direct:start", exchange -> {
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader("id", id);
        });
    }

    private Exchange sendBatch(String body, String id) {
        return template.send("direct:start", exchange -> {
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader("id", id);
            exchange.setProperty(Exchange.BATCH_SIZE, 3);
        });
    }

    private AggregateProcessor addRoute(AggregationRepository repository, boolean batch) throws Exception {
        // onCompletion throws the first time only (and each time for the bodies in alwaysThrow), after adding an on
        // completion to the aggregated exchange (as the zip and tar strategies do to delete their temporary file)
        BodyInAggregatingStrategy strategy = new BodyInAggregatingStrategy() {
            @Override
            public void onCompletion(Exchange exchange) {
                String body = exchange.getIn().getBody(String.class);
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onFailure(Exchange exchange) {
                        failures.add(body);
                    }
                });
                if (alwaysThrow.contains(body) || throwOnce.compareAndSet(true, false)) {
                    throw new IllegalStateException("onCompletion failed for " + body);
                }
            }
        };

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                var aggregate = from("direct:start").aggregate(header("id"), strategy).id("aggregator");
                if (batch) {
                    aggregate.completionFromBatchConsumer();
                } else {
                    aggregate.completionSize(2);
                }
                if (repository != null) {
                    aggregate.aggregationRepository(repository);
                }
                aggregate.to("mock:aggregated");
            }
        });
        context.start();

        AggregateProcessor aggregator = context.getProcessor("aggregator", AggregateProcessor.class);
        aggregator.setExceptionHandler(new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handleException(null, null, exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handleException(message, null, exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                if (exchange != null) {
                    handled.add(exchange);
                }
            }
        });
        return aggregator;
    }

    /**
     * A recoverable repository whose scan returns the exchanges in the order they were completed.
     */
    private static final class OrderedRecoverableRepository extends KeyValueAggregationRepository {
        private final Set<String> completed = new CopyOnWriteArraySet<>();

        private OrderedRecoverableRepository() {
            super(new MemoryKeyValueRepository());
            setUseRecovery(true);
            setRecoveryInterval(100);
            // no limit (as the JDBC repository by default), so the exchange that fails again is never exhausted
            setMaximumRedeliveries(0);
        }

        @Override
        public void remove(CamelContext camelContext, String key, Exchange exchange) {
            super.remove(camelContext, key, exchange);
            completed.add(exchange.getExchangeId());
        }

        @Override
        public Set<String> scan(CamelContext camelContext) {
            Set<String> scanned = super.scan(camelContext);
            Set<String> answer = new LinkedHashSet<>();
            for (String exchangeId : completed) {
                if (scanned.contains(exchangeId)) {
                    answer.add(exchangeId);
                }
            }
            return answer;
        }
    }
}
