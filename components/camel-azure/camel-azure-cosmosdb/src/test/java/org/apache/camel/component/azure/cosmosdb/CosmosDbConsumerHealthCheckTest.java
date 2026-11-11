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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.azure.cosmos.ChangeFeedProcessor;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The readiness health check of the consumer: DOWN while the change feed processor cannot start (with the last failure
 * and the number of failed attempts), UP once it has started, and back to its initial state after a stop.
 */
class CosmosDbConsumerHealthCheckTest extends CamelTestSupport {

    @Test
    void downWhileTheChangeFeedProcessorCannotStartThenUp() throws Exception {
        AtomicBoolean reachable = new AtomicBoolean();
        AtomicInteger starts = new AtomicInteger();
        ChangeFeedProcessor processor = processor(reachable, starts);
        CosmosDbConsumer consumer = consumer(processor);

        consumer.start();
        try {
            HealthCheck check = consumer.getHealthCheck();
            assertInstanceOf(CosmosDbConsumerHealthCheck.class, check);

            await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getLastStartFailure() != null);
            HealthCheck.Result down = check.call();
            assertEquals(HealthCheck.State.DOWN, down.getState());
            assertTrue(down.getError().isPresent());
            assertEquals("Resource Not Found: lease container (test)", down.getError().get().getMessage());
            assertTrue(((Number) down.getDetails().get("failed.starts")).longValue() >= 1, down.getDetails().toString());
            assertTrue(down.getMessage().orElse("").startsWith("The change feed processor has not started yet"),
                    down.getMessage().toString());

            reachable.set(true);
            await().atMost(20, TimeUnit.SECONDS).until(() -> check.call().getState() == HealthCheck.State.UP);
        } finally {
            consumer.stop();
        }

        // after a stop: no failure of the stopped start, and not started
        HealthCheck.Result stopped = consumer.getHealthCheck().call();
        assertEquals(HealthCheck.State.DOWN, stopped.getState());
        assertFalse(stopped.getError().isPresent());
        assertEquals(0L, ((Number) stopped.getDetails().get("failed.starts")).longValue());
        assertFalse(consumer.isChangeFeedProcessorStarted());
    }

    @Test
    void aStartThatEndsAfterARestartDoesNotMakeItUp() throws Exception {
        // the change feed processor of the first start is still starting when the consumer is stopped and started
        // again; the one of the second start cannot start. When the first one's start ends, the check stays DOWN
        CompletableFuture<Void> firstStart = new CompletableFuture<>();
        ChangeFeedProcessor first = mock(ChangeFeedProcessor.class);
        when(first.start()).thenReturn(Mono.fromFuture(firstStart));
        when(first.isStarted()).thenReturn(false);
        AtomicInteger starts = new AtomicInteger();
        ChangeFeedProcessor second = processor(new AtomicBoolean(), starts);
        Deque<ChangeFeedProcessor> processors = new ArrayDeque<>(List.of(first, second));
        CosmosDbConsumer consumer = consumer(() -> processors.size() > 1 ? processors.poll() : processors.peek());

        consumer.start();
        try {
            verify(first, timeout(20000)).start();
            consumer.stop();
            consumer.start();
            await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getLastStartFailure() != null);

            firstStart.complete(null);
            // the attempt of the first start sees that the consumer stopped meanwhile, and stops that processor
            verify(first, timeout(20000).atLeast(2)).isStarted();
            HealthCheck.Result result = consumer.getHealthCheck().call();
            assertEquals(HealthCheck.State.DOWN, result.getState());
            assertFalse(consumer.isChangeFeedProcessorStarted());
        } finally {
            consumer.stop();
        }
    }

    @Test
    void aDisabledCheckIsUnknown() throws Exception {
        CosmosDbConsumer consumer = consumer(processor(new AtomicBoolean(), new AtomicInteger()));
        consumer.start();
        try {
            await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getLastStartFailure() != null);
            consumer.getHealthCheck().setEnabled(false);
            HealthCheck.Result result = consumer.getHealthCheck().call();
            assertEquals(HealthCheck.State.UNKNOWN, result.getState());
            assertEquals("Disabled", result.getMessage().orElse(null));
        } finally {
            consumer.stop();
        }
    }

    @Test
    void upOnceTheFirstStartSucceeds() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        ChangeFeedProcessor processor = processor(new AtomicBoolean(true), starts);
        CosmosDbConsumer consumer = consumer(processor);

        consumer.start();
        try {
            await().atMost(20, TimeUnit.SECONDS)
                    .until(() -> consumer.getHealthCheck().call().getState() == HealthCheck.State.UP);
            assertEquals(0, consumer.getFailedStarts());
            assertEquals(1, starts.get());
        } finally {
            consumer.stop();
        }

        // started again: UP again once its change feed processor started
        consumer.start();
        try {
            await().atMost(20, TimeUnit.SECONDS)
                    .until(() -> consumer.getHealthCheck().call().getState() == HealthCheck.State.UP);
            assertEquals(2, starts.get());
        } finally {
            consumer.stop();
        }
    }

    /**
     * A change feed processor that cannot start while reachable is false (as when the lease container does not exist
     * yet), and starts once it is true.
     */
    private static ChangeFeedProcessor processor(AtomicBoolean reachable, AtomicInteger starts) {
        ChangeFeedProcessor processor = mock(ChangeFeedProcessor.class);
        AtomicBoolean started = new AtomicBoolean();
        when(processor.isStarted()).thenAnswer(invocation -> started.get());
        when(processor.stop()).thenAnswer(invocation -> {
            if (!started.compareAndSet(true, false)) {
                throw new IllegalStateException("The ChangeFeedProcessor instance has not fully started");
            }
            return Mono.empty();
        });
        when(processor.start()).thenAnswer(invocation -> {
            starts.incrementAndGet();
            if (!reachable.get()) {
                return Mono.error(new IllegalStateException("Resource Not Found: lease container (test)"));
            }
            started.set(true);
            return Mono.empty();
        });
        return processor;
    }

    private CosmosDbConsumer consumer(ChangeFeedProcessor processor) throws Exception {
        return consumer(() -> processor);
    }

    private CosmosDbConsumer consumer(Supplier<ChangeFeedProcessor> processors) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("accountKey", "myKey");
        CosmosDbEndpoint endpoint = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint("azure-cosmosdb://mydb/myContainer", "mydb/myContainer", params);
        return new CosmosDbConsumer(endpoint, exchange -> {
        }) {
            @Override
            ChangeFeedProcessor createChangeFeedProcessor() {
                return processors.get();
            }
        };
    }
}
