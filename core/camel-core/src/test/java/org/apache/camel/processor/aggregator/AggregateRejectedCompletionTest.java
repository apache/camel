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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.KeyValueAggregationRepository;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the thread pool of the aggregator rejects the task that sends an aggregated exchange, the group has already been
 * removed from the aggregation repository. The aggregated exchange must then be failed, so that its on completions run,
 * the aggregator no longer counts it as in progress, and a recoverable repository recovers it. When the batch consumer
 * completes several groups at once, this holds for each of them. The exchange that completed the group still fails with
 * the rejection, as AggregateParallelThreadPoolAbortTest expects.
 */
class AggregateRejectedCompletionTest extends ContextTestSupport {

    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private final List<Exchange> handled = new CopyOnWriteArrayList<>();

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
    void testRejectedAggregatedExchangeIsFailed() throws Exception {
        AggregateProcessor aggregator = addRoute(null, false);

        getMockEndpoint("mock:aggregated").expectedBodiesReceived("A1+A2", "B1+B2");

        fillThreadPool();
        // the thread pool is busy with A1+A2, and its queue holds B1+B2, so the task for C1+C2 is rejected
        sendBody("C1", "C");
        Exchange last = send("C2", "C");

        release.countDown();
        assertMockEndpointsSatisfied();

        // the aggregated exchange is handed to the exception handler, and its on completions run as failed
        assertEquals(1, handled.size());
        assertEquals("C1+C2", handled.get(0).getIn().getBody(String.class));
        assertInstanceOf(RejectedExecutionException.class, handled.get(0).getException());
        assertEquals(List.of("C1+C2"), failures);
        // and it is no longer in progress
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, aggregator.getInProgressCompleteExchanges()));
        // the message that completed the group fails with the rejection, as before
        assertInstanceOf(RejectedExecutionException.class, last.getException());
    }

    @Test
    void testRejectedAggregatedExchangeIsRecovered() throws Exception {
        addRoute(recoverableRepository(), false);

        getMockEndpoint("mock:aggregated").expectedBodiesReceivedInAnyOrder("A1+A2", "B1+B2", "C1+C2");

        fillThreadPool();
        sendBody("C1", "C");
        send("C2", "C");

        release.countDown();
        // the rejected C1+C2 is still in the completed store of the repository, so it is recovered
        assertMockEndpointsSatisfied();
    }

    @Test
    void testRejectedBatchCompletionFailsEveryGroup() throws Exception {
        AggregateProcessor aggregator = addRoute(null, true);

        getMockEndpoint("mock:aggregated").expectedBodiesReceived("A1", "B1");

        fillThreadPoolByBatches();
        // the batch consumer completes the groups C, D and E at once, and the thread pool rejects all three tasks
        sendBatch("C1", "C");
        sendBatch("D1", "D");
        Exchange last = sendBatch("E1", "E");

        release.countDown();
        assertMockEndpointsSatisfied();

        // every group of the batch is failed, not only the first one that was rejected
        assertEquals(List.of("C1", "D1", "E1"), handled.stream().map(e -> e.getIn().getBody(String.class)).toList());
        assertEquals(List.of("C1", "D1", "E1"), failures);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, aggregator.getInProgressCompleteExchanges()));
        assertInstanceOf(RejectedExecutionException.class, last.getException());
    }

    @Test
    void testRejectedBatchCompletionIsRecovered() throws Exception {
        addRoute(recoverableRepository(), true);

        // E1 completed its group on arrival, so its group was never stored in the repository: its sender gets the
        // rejection instead
        getMockEndpoint("mock:aggregated").expectedBodiesReceivedInAnyOrder("A1", "B1", "C1", "D1");

        fillThreadPoolByBatches();
        sendBatch("C1", "C");
        sendBatch("D1", "D");
        sendBatch("E1", "E");

        release.countDown();
        // the rejected C1 and D1 are still in the completed store of the repository, so both are recovered
        assertMockEndpointsSatisfied();
    }

    private static KeyValueAggregationRepository recoverableRepository() {
        KeyValueAggregationRepository repository = new KeyValueAggregationRepository(new MemoryKeyValueRepository());
        repository.setUseRecovery(true);
        repository.setRecoveryInterval(100);
        return repository;
    }

    private void fillThreadPoolByBatches() throws InterruptedException {
        // batches of one message each: A blocks the thread and B waits in the queue
        sendBatch("A1", "A", 1);
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
        sendBatch("B1", "B", 1);
    }

    private Exchange sendBatch(String body, String id) {
        return sendBatch(body, id, 3);
    }

    private Exchange sendBatch(String body, String id, int batchSize) {
        return template.send("direct:start", exchange -> {
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader("id", id);
            exchange.setProperty(Exchange.BATCH_SIZE, batchSize);
        });
    }

    private void fillThreadPool() throws InterruptedException {
        sendBody("A1", "A");
        sendBody("A2", "A");
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
        sendBody("B1", "B");
        sendBody("B2", "B");
    }

    private void sendBody(String body, String id) {
        template.sendBodyAndHeader("direct:start", body, "id", id);
    }

    private Exchange send(String body, String id) {
        return template.send("direct:start", exchange -> {
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader("id", id);
        });
    }

    private AggregateProcessor addRoute(AggregationRepository repository, boolean batch) throws Exception {
        addRoutes(repository, batch);
        AggregateProcessor aggregator = context.getProcessor("aggregator", AggregateProcessor.class);
        ExceptionHandler handler = new ExceptionHandler() {
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
        };
        aggregator.setExceptionHandler(handler);
        return aggregator;
    }

    private void addRoutes(AggregationRepository repository, boolean batch) throws Exception {
        ThreadPoolProfile profile = new ThreadPoolProfile("oneThread");
        profile.setPoolSize(1);
        profile.setMaxPoolSize(1);
        profile.setMaxQueueSize(1);
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        context.getExecutorServiceManager().registerThreadPoolProfile(profile);

        // the strategy adds an on completion to the aggregated exchange, as the zip and tar strategies do to delete
        // their temporary file
        BodyInAggregatingStrategy strategy = new BodyInAggregatingStrategy() {
            @Override
            public void onCompletion(Exchange exchange) {
                String body = exchange.getIn().getBody(String.class);
                Synchronization cleanup = new SynchronizationAdapter() {
                    @Override
                    public void onFailure(Exchange exchange) {
                        failures.add(body);
                    }
                };
                exchange.getExchangeExtension().addOnCompletion(cleanup);
            }
        };

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                var aggregate = from("direct:start")
                        .aggregate(header("id"), strategy).id("aggregator")
                        .executorService("oneThread");
                if (batch) {
                    aggregate.completionFromBatchConsumer();
                } else {
                    aggregate.completionSize(2);
                }
                if (repository != null) {
                    aggregate.aggregationRepository(repository);
                }
                aggregate.process(exchange -> {
                    if (exchange.getIn().getBody(String.class).startsWith("A1")) {
                        blocked.countDown();
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    }
                }).to("mock:aggregated");
            }
        });
        context.start();
    }
}
