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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.ClosedCorrelationKeyException;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.service.ServiceSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Aggregate EIP must keep the bodies that stream caching spooled to disk until the aggregated exchange is done with
 * them, and must not leave the spool files behind, also when a group or an incoming exchange is discarded.
 */
public class AggregateStreamCachingSpoolTest extends ContextTestSupport {

    private static final byte[] DATA = createData(16 * 1024);

    // the aggregated exchange is processed only when the incoming exchanges are done, so the spool files would already
    // be deleted if the aggregator did not hold its own references to them
    private final CountDownLatch sendersDone = new CountDownLatch(1);
    private final AggregateController controller = new DefaultAggregateController();
    private final FailOnceRepository failOnce = new FailOnceRepository();
    private final FailOnceRepository failOnceAndClose = new FailOnceRepository();
    // whether the on completion that the aggregation strategy added to the first exchange of the group has run
    private final AtomicBoolean strategyCompletionDone = new AtomicBoolean();

    @Test
    public void testGroupedBodies() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBody("direct:grouped", stream());
        template.sendBody("direct:grouped", stream());
        template.sendBody("direct:grouped", stream());
        sendersDone.countDown();

        assertMockEndpointsSatisfied();
        List<?> bodies = result.getReceivedExchanges().get(0).getMessage().getBody(List.class);
        assertEquals(3, bodies.size());
        for (Object body : bodies) {
            assertArrayEquals(DATA, (byte[]) body);
        }
        assertSpoolDirectoryEmpty();
    }

    @Test
    public void testCompletionTimeout() throws Exception {
        sendAndAssertBody("direct:timeout");
    }

    @Test
    public void testCompletionSizeOne() throws Exception {
        // the aggregated exchange is sent on the aggregator's own thread, so even the body of the exchange that
        // completes the group must not depend on the incoming exchange
        sendAndAssertBody("direct:size");
    }

    @Test
    public void testOptimisticLockingRetry() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        // the first attempt to add the first exchange fails, and the retry takes a new copy
        template.sendBody("direct:optimistic", stream());
        template.sendBody("direct:optimistic", stream());
        sendersDone.countDown();

        assertMockEndpointsSatisfied();
        assertEquals(2, failOnce.calls.get());
        assertArrayEquals(DATA, result.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));
        assertSpoolDirectoryEmpty();
    }

    @Test
    public void testClosedCorrelationKeyOnRetry() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        sendersDone.countDown();

        // while the first attempt to add this exchange fails, another exchange completes and closes the group, so the
        // retry finds the correlation key closed
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:closed", stream()));
        assertInstanceOf(ClosedCorrelationKeyException.class, e.getCause());

        assertMockEndpointsSatisfied();
        assertArrayEquals(DATA, result.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));
        assertSpoolDirectoryEmpty();
    }

    @Test
    public void testDiscardOnAggregationFailure() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        // the first exchange fails to aggregate, and is discarded
        template.sendBodyAndHeader("direct:failure", stream(), "fail", true);
        // the second exchange starts a group, and the third fails to aggregate, which discards the group
        template.sendBody("direct:failure", stream());
        template.sendBodyAndHeader("direct:failure", stream(), "fail", true);
        sendersDone.countDown();

        assertMockEndpointsSatisfied();
        assertSpoolDirectoryEmpty();
    }

    @Test
    public void testDiscardOnCompletionTimeout() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:discardTimeout", stream());
        sendersDone.countDown();

        assertSpoolDirectoryEmpty();
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testForceDiscardingOfGroup() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:forceDiscard", stream());
        template.sendBody("direct:forceDiscard", stream());
        sendersDone.countDown();
        assertEquals(1, controller.forceDiscardingOfGroup("group"));

        assertMockEndpointsSatisfied();
        assertSpoolDirectoryEmpty();
    }

    @Test
    public void testRepositoryStoringCopies() throws Exception {
        // like the persistent repositories, the repository reads the body when the exchange is added
        sendAndAssertBody("direct:copies");
    }

    @Test
    public void testMemoryRepositoryStoringCopies() throws Exception {
        // a subclass of the memory repository that does not keep the exchange instance
        sendAndAssertBody("direct:memoryCopies");
    }

    @Test
    public void testStrategyCompletionWithOptimisticLocking() throws Exception {
        sendAndAssertStrategyCompletion("direct:strategyCompletionOptimistic");
    }

    @Test
    public void testStrategyCompletionWithRepositoryStoringCopies() throws Exception {
        sendAndAssertStrategyCompletion("direct:strategyCompletionCopies");
    }

    @Test
    public void testStrategyCompletionWithMemoryRepository() throws Exception {
        sendAndAssertStrategyCompletion("direct:strategyCompletionMemory");
    }

    private void sendAndAssertStrategyCompletion(String uri) throws Exception {
        // the aggregator must only release its own references to the spooled stream caches, and leave the on
        // completions that the aggregation strategy adds to the exchanges alone (such as the ZipAggregationStrategy
        // that deletes its zip file when the aggregated exchange is done)
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBody(uri, stream());
        template.sendBody(uri, stream());
        template.sendBodyAndHeader(uri, stream(), "last", true);
        sendersDone.countDown();

        assertMockEndpointsSatisfied();
        assertArrayEquals(DATA, result.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));
        assertSpoolDirectoryEmpty();
        if (uri.endsWith("Memory")) {
            // the memory repository keeps the exchange, so the on completion runs when the aggregated exchange is done
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilTrue(strategyCompletionDone);
        }
    }

    private void sendAndAssertBody(String uri) throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBody(uri, stream());
        sendersDone.countDown();

        assertMockEndpointsSatisfied();
        assertArrayEquals(DATA, result.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));
        assertSpoolDirectoryEmpty();
    }

    private void assertSpoolDirectoryEmpty() {
        File spoolDir = testDirectory().toFile();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String[] files = spoolDir.list();
            assertNotNull(files);
            assertEquals(0, files.length, "Spool files left behind: " + List.of(files));
        });
    }

    private static InputStream stream() {
        // a stream that is not converted to an in-memory cache, so it is spooled to disk
        return new BufferedInputStream(new ByteArrayInputStream(DATA));
    }

    private static byte[] createData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ('a' + i % 26);
        }
        return data;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setSpoolThreshold(1024);
                context.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(false);
                context.setStreamCaching(true);

                // reads the bodies after the incoming exchanges are done
                Processor read = e -> {
                    sendersDone.await(20, TimeUnit.SECONDS);
                    Object body = e.getMessage().getBody();
                    if (body instanceof List<?> list) {
                        List<byte[]> answer = new ArrayList<>();
                        for (Object o : list) {
                            answer.add(e.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, e, o));
                        }
                        e.getMessage().setBody(answer);
                    } else {
                        e.getMessage().setBody(e.getMessage().getMandatoryBody(byte[].class));
                    }
                };

                // adds an on completion to the first exchange of the group, which must not run before the group completes
                AggregationStrategy addCompletion = (oldExchange, newExchange) -> {
                    if (oldExchange == null) {
                        strategyCompletionDone.set(false);
                        newExchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                strategyCompletionDone.set(true);
                            }
                        });
                        return newExchange;
                    }
                    if (strategyCompletionDone.get()) {
                        throw new IllegalStateException("The on completion of the aggregation strategy has already run");
                    }
                    oldExchange.getMessage().setBody(newExchange.getMessage().getBody());
                    return oldExchange;
                };

                AggregationStrategy failOnHeader = (oldExchange, newExchange) -> {
                    if (newExchange.getMessage().getHeader("fail") != null) {
                        throw new IllegalArgumentException("Forced");
                    }
                    return newExchange;
                };

                from("direct:grouped")
                        .aggregate(constant("group"), new GroupedBodyAggregationStrategy()).completionSize(3)
                        .process(read).to("mock:result");

                from("direct:timeout")
                        .aggregate(constant("group"), new UseLatestAggregationStrategy())
                        .completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .process(read).to("mock:result");

                from("direct:size")
                        .aggregate(constant("group"), new UseLatestAggregationStrategy()).completionSize(1)
                        .process(read).to("mock:result");

                from("direct:optimistic")
                        .aggregate(constant("group"), new UseLatestAggregationStrategy())
                        .aggregationRepository(failOnce).optimisticLocking().completionSize(2)
                        .process(read).to("mock:result");

                from("direct:closed")
                        .aggregate(constant("group"), new UseLatestAggregationStrategy())
                        .aggregationRepository(failOnceAndClose).optimisticLocking()
                        .completionPredicate(header("complete").isNotNull()).closeCorrelationKeyOnCompletion(100)
                        .process(read).to("mock:result");

                from("direct:failure")
                        .aggregate(constant("group"), failOnHeader).discardOnAggregationFailure().completionSize(5)
                        .to("mock:result");

                from("direct:discardTimeout")
                        .aggregate(constant("group"), new UseLatestAggregationStrategy())
                        .completionTimeout(100).completionTimeoutCheckerInterval(10).discardOnCompletionTimeout()
                        .to("mock:result");

                from("direct:forceDiscard")
                        .aggregate(constant("group"), new UseLatestAggregationStrategy()).aggregateController(controller)
                        .completionSize(5)
                        .to("mock:result");

                from("direct:strategyCompletionOptimistic")
                        .aggregate(constant("group"), addCompletion)
                        .aggregationRepository(new MemoryAggregationRepository(true)).optimisticLocking()
                        .completionPredicate(header("last").isNotNull()).eagerCheckCompletion()
                        .process(read).to("mock:result");

                from("direct:strategyCompletionCopies")
                        .aggregate(constant("group"), addCompletion)
                        .aggregationRepository(new CopyingRepository())
                        .completionPredicate(header("last").isNotNull()).eagerCheckCompletion()
                        .process(read).to("mock:result");

                from("direct:strategyCompletionMemory")
                        .aggregate(constant("group"), addCompletion)
                        .completionPredicate(header("last").isNotNull()).eagerCheckCompletion()
                        .process(read).to("mock:result");

                from("direct:memoryCopies")
                        .aggregate(constant("group"), new UseLatestAggregationStrategy())
                        .aggregationRepository(new MemoryAggregationRepository() {
                            @Override
                            public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
                                Exchange copy = exchange.copy();
                                copy.getMessage().setBody(exchange.getMessage().getBody(byte[].class));
                                return super.add(camelContext, key, copy);
                            }
                        })
                        .completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .process(read).to("mock:result");

                from("direct:copies")
                        .aggregate(constant("group"), new UseLatestAggregationStrategy())
                        .aggregationRepository(new CopyingRepository())
                        .completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .process(read).to("mock:result");
            }
        };
    }

    /**
     * An optimistic locking repository whose first add fails. When it closes the group, it first sends an exchange that
     * completes the group, which closes the correlation key.
     */
    private final class FailOnceRepository extends MemoryAggregationRepository {
        private final AtomicInteger calls = new AtomicInteger();

        private FailOnceRepository() {
            super(true);
        }

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
            if (calls.incrementAndGet() == 1) {
                if (this == failOnceAndClose) {
                    template.sendBodyAndHeader("direct:closed", stream(), "complete", true);
                }
                throw new OptimisticLockingAggregationRepository.OptimisticLockingException();
            }
            return super.add(camelContext, key, oldExchange, newExchange);
        }
    }

    /**
     * A repository that stores a copy of the exchange with the body read into memory, as the persistent repositories
     * store a serialized copy, and returns a new exchange instance on every get.
     */
    private static final class CopyingRepository extends ServiceSupport implements AggregationRepository {
        private final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
            Exchange old = exchanges.put(key, copy(exchange, exchange.getMessage().getBody(byte[].class)));
            return old != null ? copy(old, old.getMessage().getBody()) : null;
        }

        @Override
        public Exchange get(CamelContext camelContext, String key) {
            Exchange exchange = exchanges.get(key);
            return exchange != null ? copy(exchange, exchange.getMessage().getBody()) : null;
        }

        @Override
        public void remove(CamelContext camelContext, String key, Exchange exchange) {
            exchanges.remove(key);
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            // noop
        }

        @Override
        public Set<String> getKeys() {
            return exchanges.keySet();
        }

        private static Exchange copy(Exchange exchange, Object body) {
            Exchange copy = new DefaultExchange(exchange.getContext());
            copy.setExchangeId(exchange.getExchangeId());
            copy.getProperties().putAll(exchange.getProperties());
            copy.getMessage().setHeaders(exchange.getMessage().getHeaders());
            copy.getMessage().setBody(body);
            return copy;
        }
    }
}
