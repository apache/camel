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
package org.apache.camel.component.azure.eventhubs;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.PartitionContext;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The events completed after a checkpoint by batch size must be checkpointed by the batch timeout, also when no further
 * event arrives (end of a burst). Uses a real scheduler with a short batch timeout.
 */
class EventHubsConsumerCheckpointAfterBatchSizeTest {

    private final EventHubsEndpoint endpoint = mock();
    private final EventHubsConfiguration configuration = new EventHubsConfiguration();
    private final AsyncProcessor processor = mock();
    private final CamelContext context = mock();
    private final ExtendedCamelContext ecc = mock();
    private final ExchangeFactory exchangeFactory = mock();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private EventHubsConsumer consumer;
    private Method processCommit;

    @BeforeEach
    void setUp() throws Exception {
        configuration.setCheckpointBatchSize(2);
        configuration.setCheckpointBatchTimeout(1000);

        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);
        when(exchangeFactory.create(any(Endpoint.class), anyBoolean()))
                .thenAnswer(invocation -> DefaultExchange.newFromEndpoint(invocation.getArgument(0)));
        when(endpoint.getConfiguration()).thenReturn(configuration);
        consumer = new EventHubsConsumer(endpoint, processor);

        var field = EventHubsConsumer.class.getDeclaredField("scheduledExecutorService");
        field.setAccessible(true);
        field.set(consumer, scheduler);

        processCommit = EventHubsConsumer.class.getDeclaredMethod("processCommit", Exchange.class, EventContext.class);
        processCommit.setAccessible(true);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void eventAfterBatchSizeCheckpointIsCheckpointedByTimeout() throws Exception {
        EventContext first = eventContext("0");
        EventContext second = eventContext("0");
        EventContext third = eventContext("0");

        complete(first);
        complete(second);
        // the batch size is reached: checkpoint at the second event
        verify(second, times(1)).updateCheckpointAsync();

        // the last event of the burst: no other event follows
        complete(third);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(third, times(1)).updateCheckpointAsync());
    }

    @Test
    void eventsAfterSecondBatchSizeCheckpointAreCheckpointedByTimeout() throws Exception {
        EventContext[] events = new EventContext[5];
        for (int i = 0; i < events.length; i++) {
            events[i] = eventContext("0");
            complete(events[i]);
        }
        // checkpoints by batch size at the second and the fourth event
        verify(events[1], times(1)).updateCheckpointAsync();
        verify(events[3], times(1)).updateCheckpointAsync();

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(events[4], times(1)).updateCheckpointAsync());
    }

    @Test
    void eventCompletedWhileTheTimeoutCheckpointIsStoredIsCheckpointedByTheNextTimeout() throws Exception {
        configuration.setCheckpointBatchSize(10);
        // the checkpoint of the first window is stored only when the test says so
        Sinks.Empty<Void> stored = Sinks.empty();
        AtomicLong checkpointedAt = new AtomicLong();
        EventContext first = eventContext("0");
        doAnswer(invocation -> {
            checkpointedAt.set(System.currentTimeMillis());
            return stored.asMono();
        }).when(first).updateCheckpointAsync();
        EventContext second = eventContext("0");

        complete(first);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(first, times(1)).updateCheckpointAsync());
        // the window of the first task is over, so the next event starts a new task
        await().atMost(10, TimeUnit.SECONDS).until(() -> System.currentTimeMillis() > checkpointedAt.get());

        // an event completes while the checkpoint of the first window is being stored
        complete(second);
        stored.tryEmitEmpty();

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(second, times(1)).updateCheckpointAsync());
    }

    @Test
    void eventAfterBatchSizeCheckpointDuringTimeoutCheckpointIsCheckpointed() throws Exception {
        configuration.setCheckpointBatchSize(3);
        // the checkpoint of the first window is stored only when the test says so
        Sinks.Empty<Void> stored = Sinks.empty();
        AtomicLong checkpointedAt = new AtomicLong();
        EventContext first = eventContext("0");
        doAnswer(invocation -> {
            checkpointedAt.set(System.currentTimeMillis());
            return stored.asMono();
        }).when(first).updateCheckpointAsync();
        EventContext second = eventContext("0");
        EventContext third = eventContext("0");
        EventContext fourth = eventContext("0");

        complete(first);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(first, times(1)).updateCheckpointAsync());
        await().atMost(10, TimeUnit.SECONDS).until(() -> System.currentTimeMillis() > checkpointedAt.get());

        // while the timeout checkpoint of the first event is stored, more events complete, and the count climbs back
        // to the value that checkpoint started with
        complete(second);
        complete(third);
        complete(fourth);
        stored.tryEmitEmpty();

        // the last event of the burst must still be checkpointed, by batch size or by the next timeout
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(fourth, times(1)).updateCheckpointAsync());
    }

    private void complete(EventContext eventContext) throws Exception {
        processCommit.invoke(consumer, DefaultExchange.newFromEndpoint(endpoint), eventContext);
    }

    private static EventContext eventContext(String partitionId) {
        EventContext eventContext = mock(EventContext.class);
        PartitionContext partitionContext = mock(PartitionContext.class);
        when(partitionContext.getPartitionId()).thenReturn(partitionId);
        when(eventContext.getPartitionContext()).thenReturn(partitionContext);
        when(eventContext.updateCheckpointAsync()).thenReturn(Mono.empty());
        return eventContext;
    }
}
