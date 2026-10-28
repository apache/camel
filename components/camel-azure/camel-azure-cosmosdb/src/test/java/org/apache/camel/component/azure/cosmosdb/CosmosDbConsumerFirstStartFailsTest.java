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

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.azure.cosmos.ChangeFeedProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The change feed processor cannot start when the consumer starts (Cosmos DB not reachable yet, the lease container not
 * created yet, a role assignment not applied yet), and can a moment later: the consumer must start it again, or it
 * stays started without ever receiving a change.
 */
class CosmosDbConsumerFirstStartFailsTest extends CamelTestSupport {

    @Test
    void consumerStartsTheChangeFeedAgainWhenTheFirstStartFails() throws Exception {
        List<Object> routed = new CopyOnWriteArrayList<>();
        CountDownLatch received = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        AtomicReference<CosmosDbConsumer> consumerRef = new AtomicReference<>();

        // a failure before the processor built its partition manager (reading the feed or lease container): the real
        // processor (azure-cosmos 4.75) can be started again then
        ChangeFeedProcessor processor = mock(ChangeFeedProcessor.class);
        when(processor.start()).thenAnswer(invocation -> {
            if (starts.incrementAndGet() == 1) {
                return Mono.error(new IllegalStateException("Resource Not Found: lease container (test)"));
            }
            // started: the change feed has one change for the consumer
            return Mono.fromRunnable(() -> {
                Exchange exchange = new DefaultExchange(context);
                exchange.getIn().setBody(List.of(Map.of("id", "1")));
                consumerRef.get().processBatch(exchange);
            });
        });
        when(processor.stop()).thenReturn(Mono.empty());
        when(processor.isStarted()).thenAnswer(invocation -> starts.get() > 1);

        CosmosDbConsumer consumer = new CosmosDbConsumer(endpoint(), exchange -> {
            routed.add(exchange.getIn().getBody());
            received.countDown();
        }) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                // the change feed processor of the test instead of one built against Cosmos DB
                return processor;
            }
        };
        consumerRef.set(consumer);

        consumer.start();
        try {
            assertTrue(received.await(20, TimeUnit.SECONDS),
                    "the change feed processor was started " + starts.get()
                                                             + " time(s), the consumer is started but receives nothing");
            assertEquals(1, routed.size());
        } finally {
            consumer.stop();
        }
        verify(processor).stop();
    }

    @Test
    void consumerStartsANewChangeFeedProcessorWhenTheFirstOneFailedWhileInitializingTheLeases() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<CosmosDbConsumer> consumerRef = new AtomicReference<>();

        // as the real processor (azure-cosmos 4.75) whose start failed after it built its partition manager (while the
        // leases were initialized): its partition load balancer is already marked as started, so every later start()
        // throws "Partition load balancer already started" at once
        AtomicInteger firstStarts = new AtomicInteger();
        ChangeFeedProcessor failed = mock(ChangeFeedProcessor.class);
        when(failed.start()).thenAnswer(invocation -> {
            if (firstStarts.incrementAndGet() == 1) {
                return Mono.error(new IllegalStateException("Request rate is large (test)"));
            }
            throw new IllegalStateException("Partition load balancer already started");
        });
        ChangeFeedProcessor working = mock(ChangeFeedProcessor.class);
        when(working.start()).thenAnswer(invocation -> Mono.fromRunnable(() -> {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(List.of(Map.of("id", "1")));
            consumerRef.get().processBatch(exchange);
        }));
        when(working.stop()).thenReturn(Mono.empty());
        when(working.isStarted()).thenReturn(true);
        Deque<ChangeFeedProcessor> processors = new ArrayDeque<>(List.of(failed, working));

        CosmosDbConsumer consumer = new CosmosDbConsumer(endpoint(), exchange -> received.countDown()) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                return processors.size() > 1 ? processors.poll() : processors.peek();
            }
        };
        consumerRef.set(consumer);

        consumer.start();
        try {
            assertTrue(received.await(20, TimeUnit.SECONDS),
                    "the failed change feed processor was started " + firstStarts.get()
                                                             + " time(s), no new one was started");
        } finally {
            consumer.stop();
        }
        verify(working).stop();
    }

    @Test
    void consumerStartsANewChangeFeedProcessorWhenTheStartFailsOnAReactorThread() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<CosmosDbConsumer> consumerRef = new AtomicReference<>();

        // as the real processor: the failure is signalled on a thread of a Reactor scheduler (here the parallel
        // scheduler of Mono.delay; the Cosmos DB client signals on its own non-blocking threads)
        ChangeFeedProcessor failed = mock(ChangeFeedProcessor.class);
        when(failed.start()).thenAnswer(invocation -> Mono.delay(Duration.ofMillis(1))
                .then(Mono.error(new IllegalStateException("Service is currently unavailable (test)"))));
        ChangeFeedProcessor working = mock(ChangeFeedProcessor.class);
        when(working.start()).thenAnswer(invocation -> Mono.fromRunnable(() -> {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(List.of(Map.of("id", "1")));
            consumerRef.get().processBatch(exchange);
        }));
        when(working.stop()).thenReturn(Mono.empty());
        when(working.isStarted()).thenReturn(true);
        Deque<ChangeFeedProcessor> processors = new ArrayDeque<>(List.of(failed, working));
        List<String> blockedOn = new CopyOnWriteArrayList<>();

        CosmosDbConsumer consumer = new CosmosDbConsumer(endpoint(), exchange -> received.countDown()) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                ChangeFeedProcessor next = processors.size() > 1 ? processors.poll() : processors.peek();
                // the real one blocks until the containers are read (CosmosDbContainerOperations
                // .captureEventsWithChangeFeed zips them and calls block()), and block() throws on a thread where
                // blocking is not allowed
                blockedOn.add(Thread.currentThread().getName());
                return Mono.zip(Mono.just(next), Mono.just(next)).map(Tuple2::getT1).block();
            }
        };
        consumerRef.set(consumer);

        consumer.start();
        try {
            assertTrue(received.await(20, TimeUnit.SECONDS),
                    "no new change feed processor was started, built on the threads " + blockedOn);
        } finally {
            consumer.stop();
        }
        verify(working).stop();
    }

    @Test
    void consumerTriesAgainWhenANewChangeFeedProcessorCannotBeBuilt() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<CosmosDbConsumer> consumerRef = new AtomicReference<>();
        AtomicInteger builds = new AtomicInteger();

        ChangeFeedProcessor failed = mock(ChangeFeedProcessor.class);
        when(failed.start()).thenReturn(Mono.error(new IllegalStateException("Service is currently unavailable (test)")));
        ChangeFeedProcessor working = mock(ChangeFeedProcessor.class);
        when(working.start()).thenAnswer(invocation -> Mono.fromRunnable(() -> {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(List.of(Map.of("id", "1")));
            consumerRef.get().processBatch(exchange);
        }));
        when(working.stop()).thenReturn(Mono.empty());
        when(working.isStarted()).thenReturn(true);

        CosmosDbConsumer consumer = new CosmosDbConsumer(endpoint(), exchange -> received.countDown()) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                switch (builds.incrementAndGet()) {
                    case 1:
                        return failed;
                    case 2:
                        // as the real one with createLeaseContainerIfNotExists=true while Cosmos DB cannot be reached
                        throw new IllegalStateException("Cannot create the lease container (test)");
                    default:
                        return working;
                }
            }
        };
        consumerRef.set(consumer);

        consumer.start();
        try {
            assertTrue(received.await(20, TimeUnit.SECONDS),
                    "no change feed processor was started after " + builds.get() + " build(s)");
        } finally {
            consumer.stop();
        }
        verify(working).stop();
    }

    private CosmosDbEndpoint endpoint() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("accountKey", "myKey");
        return (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint("azure-cosmosdb://mydb/myContainer", "mydb/myContainer", params);
    }
}
