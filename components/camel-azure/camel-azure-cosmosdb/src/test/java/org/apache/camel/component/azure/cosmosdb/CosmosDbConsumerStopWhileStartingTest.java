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
package org.apache.camel.component.azure.cosmosdb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.azure.cosmos.ChangeFeedProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer is stopped (and started again) while it starts the change feed processor: a processor whose start
 * completes after the consumer stopped must be stopped too, and a restart must not reuse it.
 */
class CosmosDbConsumerStopWhileStartingTest extends CamelTestSupport {

    @Test
    void consumerStopsAChangeFeedProcessorThatFinishesStartingAfterTheConsumerStopped() throws Exception {
        TestProcessor processor = new TestProcessor();
        processor.startCompletes = new CompletableFuture<>();
        CosmosDbConsumer consumer = consumer(processor.processor);

        consumer.start();
        assertTrue(processor.startRequested.await(20, TimeUnit.SECONDS), "the change feed processor was not started");
        // its start has not completed: it cannot be stopped yet
        consumer.stop();
        processor.startCompletes.complete(null);

        assertTrue(processor.stopped.await(5, TimeUnit.SECONDS),
                "the change feed processor finished starting after the consumer stopped and was never stopped");
        assertFalse(processor.running.get());
    }

    @Test
    void consumerStopsAChangeFeedProcessorThatIsLoadingTheLeasesAtOnce() throws Exception {
        // the stop comes while the leases are loaded (isStarted() is true then), and the start fails after that
        TestProcessor processor = new TestProcessor();
        processor.partitionManagerBuiltAtOnce = true;
        processor.startCompletes = new CompletableFuture<>();
        CosmosDbConsumer consumer = consumer(processor.processor);

        consumer.start();
        assertTrue(processor.startRequested.await(20, TimeUnit.SECONDS), "the change feed processor was not started");
        consumer.stop();
        assertEquals(1, processor.stops.get(), "the change feed processor that loads the leases was not stopped");
        processor.startCompletes.completeExceptionally(new IllegalStateException("Request rate is large (test)"));

        assertFalse(processor.running.get());
        assertEquals(1, processor.stops.get());
    }

    @Test
    void consumerStopsAChangeFeedProcessorWhoseStartFailedWhileLoadingTheLeases() throws Exception {
        // the start fails after it started the workers of the leases loaded before: they must not go on next to the
        // ones of the next processor
        TestProcessor partial = new TestProcessor();
        partial.partitionManagerBuiltAtOnce = true;
        partial.startCompletes
                = CompletableFuture.failedFuture(new IllegalStateException("Request rate is large (test)"));
        TestProcessor next = new TestProcessor();
        AtomicReference<CosmosDbConsumer> consumerRef = new AtomicReference<>();
        CountDownLatch received = new CountDownLatch(1);
        next.onStart = () -> deliverOneChange(consumerRef.get());
        AtomicInteger builds = new AtomicInteger();

        CosmosDbConsumer consumer = new CosmosDbConsumer(endpoint(), exchange -> received.countDown()) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                return builds.incrementAndGet() == 1 ? partial.processor : next.processor;
            }
        };
        consumerRef.set(consumer);

        consumer.start();
        try {
            assertTrue(received.await(20, TimeUnit.SECONDS), "no new change feed processor was started");
            assertFalse(partial.running.get(), "the workers of the failed change feed processor were not stopped");
            assertEquals(1, partial.stops.get());
        } finally {
            consumer.stop();
        }
        assertFalse(next.running.get());
    }

    @Test
    void consumerDoesNotStartAChangeFeedProcessorBuiltAfterTheConsumerStopped() throws Exception {
        // the first one cannot start, so the next attempt builds a new one, which is built after the consumer stopped
        ChangeFeedProcessor failed = mock(ChangeFeedProcessor.class);
        when(failed.start()).thenReturn(Mono.error(new IllegalStateException("Service is currently unavailable (test)")));
        TestProcessor built = new TestProcessor();
        AtomicInteger builds = new AtomicInteger();
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);

        CosmosDbConsumer consumer = new CosmosDbConsumer(endpoint(), exchange -> {
        }) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                if (builds.incrementAndGet() == 1) {
                    return failed;
                }
                building.countDown();
                // the real one blocks until the containers are read; stop interrupts this thread, the build goes on
                boolean interrupted = false;
                while (true) {
                    try {
                        stopped.await();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                return built.processor;
            }
        };

        consumer.start();
        assertTrue(building.await(20, TimeUnit.SECONDS), "no new change feed processor was built");
        consumer.stop();
        stopped.countDown();

        verify(built.processor, after(1000).never()).start();
        assertFalse(built.running.get());
    }

    @Test
    void consumerStartsTheChangeFeedProcessorAgainWhenRestarted() throws Exception {
        TestProcessor processor = new TestProcessor();
        AtomicInteger builds = new AtomicInteger();
        AtomicReference<CosmosDbConsumer> consumerRef = new AtomicReference<>();
        CountDownLatch received = new CountDownLatch(2);
        processor.onStart = () -> deliverOneChange(consumerRef.get());

        CosmosDbConsumer consumer = new CosmosDbConsumer(endpoint(), exchange -> received.countDown()) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                builds.incrementAndGet();
                return processor.processor;
            }
        };
        consumerRef.set(consumer);

        consumer.start();
        assertTrue(processor.firstStartCompleted.await(20, TimeUnit.SECONDS),
                "the change feed processor was not started");
        consumer.stop();
        assertEquals(1, processor.stops.get(), "the change feed processor was not stopped");
        consumer.start();
        try {
            assertTrue(received.await(20, TimeUnit.SECONDS),
                    "the change feed processor was started " + processor.starts.get() + " time(s)");
        } finally {
            consumer.stop();
        }
        assertEquals(2, processor.starts.get());
        assertEquals(2, processor.stops.get());
        // a restart initializes the consumer again, which builds the processor again
        assertEquals(2, builds.get());
    }

    @Test
    void consumerStartsANewChangeFeedProcessorWhenRestartedWhileTheOldOneIsStarting() throws Exception {
        TestProcessor old = new TestProcessor();
        old.startCompletes = new CompletableFuture<>();
        TestProcessor next = new TestProcessor();
        AtomicReference<CosmosDbConsumer> consumerRef = new AtomicReference<>();
        CountDownLatch received = new CountDownLatch(1);
        next.onStart = () -> deliverOneChange(consumerRef.get());
        AtomicInteger builds = new AtomicInteger();

        CosmosDbConsumer consumer = new CosmosDbConsumer(endpoint(), exchange -> received.countDown()) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                return builds.incrementAndGet() == 1 ? old.processor : next.processor;
            }
        };
        consumerRef.set(consumer);

        consumer.start();
        assertTrue(old.startRequested.await(20, TimeUnit.SECONDS), "the change feed processor was not started");
        consumer.stop();
        consumer.start();
        try {
            assertTrue(received.await(20, TimeUnit.SECONDS),
                    "no new change feed processor was started, the old one was started " + old.starts.get()
                                                             + " time(s)");
            // the old one finishes starting now: it is stopped, the new one goes on
            old.startCompletes.complete(null);
            assertTrue(old.stopped.await(5, TimeUnit.SECONDS),
                    "the old change feed processor finished starting after the restart and was never stopped");
            assertTrue(next.running.get());
            assertEquals(1, old.starts.get());
        } finally {
            consumer.stop();
        }
        assertFalse(next.running.get());
    }

    private CosmosDbConsumer consumer(ChangeFeedProcessor processor) throws Exception {
        return new CosmosDbConsumer(endpoint(), exchange -> {
        }) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                return processor;
            }
        };
    }

    private void deliverOneChange(CosmosDbConsumer consumer) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(List.of(Map.of("id", "1")));
        consumer.processBatch(exchange);
    }

    private CosmosDbEndpoint endpoint() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("accountKey", "myKey");
        return (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint("azure-cosmosdb://mydb/myContainer", "mydb/myContainer", params);
    }

    /**
     * A change feed processor that behaves as the one of azure-cosmos 4.75 (pkversion
     * IncrementalChangeFeedProcessorImpl). isStarted() is true once its partition manager is built, and stop() throws
     * before that. The partition manager is built when the start completes, or with partitionManagerBuiltAtOnce at
     * once, before the start loads the leases. Loading them starts the workers of the leases this host owns (running),
     * outside the Mono that start() returns, also when the start fails after that; a stop() before cancels them.
     */
    private static final class TestProcessor {
        final ChangeFeedProcessor processor = mock(ChangeFeedProcessor.class);
        final AtomicBoolean loadBalancerStarted = new AtomicBoolean();
        // the shutdown token of the workers was cancelled by stop()
        final AtomicBoolean shutdown = new AtomicBoolean();
        // its workers deliver changes
        final AtomicBoolean running = new AtomicBoolean();
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();
        final CountDownLatch startRequested = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        // the consumer was told that the first start completed
        final CountDownLatch firstStartCompleted = new CountDownLatch(1);
        volatile boolean partitionManagerBuiltAtOnce;
        // completed, normally or exceptionally, when its start ends
        volatile CompletableFuture<Void> startCompletes = CompletableFuture.completedFuture(null);
        volatile Runnable onStart = () -> {
        };

        TestProcessor() {
            when(processor.start()).thenAnswer(invocation -> Mono.<Void> create(sink -> {
                starts.incrementAndGet();
                if (partitionManagerBuiltAtOnce) {
                    shutdown.set(false);
                    loadBalancerStarted.set(true);
                }
                startRequested.countDown();
                startCompletes.whenComplete((aVoid, failure) -> {
                    if (!partitionManagerBuiltAtOnce) {
                        if (failure != null) {
                            // failed before it built its partition manager: nothing started
                            sink.error(failure);
                            return;
                        }
                        shutdown.set(false);
                        loadBalancerStarted.set(true);
                    }
                    running.set(!shutdown.get());
                    if (failure != null) {
                        sink.error(failure);
                        return;
                    }
                    sink.success();
                    firstStartCompleted.countDown();
                    onStart.run();
                });
            }));
            when(processor.isStarted()).thenAnswer(invocation -> loadBalancerStarted.get());
            when(processor.stop()).thenAnswer(invocation -> {
                if (!loadBalancerStarted.compareAndSet(true, false)) {
                    throw new IllegalStateException("The ChangeFeedProcessor instance has not fully started");
                }
                shutdown.set(true);
                running.set(false);
                stops.incrementAndGet();
                stopped.countDown();
                return Mono.empty();
            });
        }
    }
}
