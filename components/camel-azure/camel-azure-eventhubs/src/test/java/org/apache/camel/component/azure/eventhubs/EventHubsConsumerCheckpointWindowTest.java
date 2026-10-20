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

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.PartitionContext;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The timer of a checkpoint window and the events of the partition: every completed event must be checkpointed by a
 * timer that runs after it was counted, whenever the timer runs relative to the wall clock and to the batch size.
 */
class EventHubsConsumerCheckpointWindowTest {

    private final EventHubsEndpoint endpoint = mock();
    private final EventHubsConfiguration configuration = new EventHubsConfiguration();
    private final AsyncProcessor processor = mock();
    private final CamelContext context = mock();
    private final ExtendedCamelContext ecc = mock();
    private final ExchangeFactory exchangeFactory = mock();
    private final ScheduledExecutorService scheduledExecutorService = mock();
    @SuppressWarnings("rawtypes")
    private final ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);

    private EventHubsConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        configuration.setCheckpointBatchSize(500);
        configuration.setCheckpointBatchTimeout(60_000);

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
        field.set(consumer, scheduledExecutorService);
        when(scheduledExecutorService.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(scheduledFuture);
    }

    @Test
    void eventCompletedAfterTheTimerRanBeforeItsScheduledTimeIsCheckpointed() throws Exception {
        EventContext first = eventContext();
        EventContext second = eventContext();

        complete(first);
        // the timer runs while the wall clock has not passed its scheduled time yet (the same millisecond)
        scheduledTasks().get(0).run();
        verify(first).updateCheckpointAsync();

        complete(second);
        runTasksFrom(1);

        verify(second).updateCheckpointAsync();
    }

    @Test
    void eventCompletedAfterTheScheduledTimeBeforeTheTimerRanIsCheckpointed() throws Exception {
        EventContext first = eventContext();
        EventContext second = eventContext();

        complete(first);
        // the wall clock has passed the scheduled time, the timer has not run yet (late by a GC pause, a busy thread)
        EventHubsCheckpointUpdaterTask firstTimer = (EventHubsCheckpointUpdaterTask) scheduledTasks().get(0);
        firstTimer.setScheduledTime(System.currentTimeMillis() - 1);

        complete(second);
        runTasksFrom(0);

        verify(second).updateCheckpointAsync();
    }

    @Test
    void timerStartedBeforeABatchSizeCheckpointDoesNotTakeTheNextWindow() throws Exception {
        configuration.setCheckpointBatchSize(2);
        EventContext first = eventContext();
        EventContext second = eventContext();
        EventContext third = eventContext();

        complete(first);
        complete(second); // batch size reached: checkpoint, the timer of the window is cancelled
        verify(second).updateCheckpointAsync();
        complete(third); // starts a new window

        // the timer of the first window had already started, so the cancel did not stop it
        runTasksFrom(0);

        verify(third).updateCheckpointAsync();
    }

    private void complete(EventContext eventContext) throws Exception {
        consumer.processCommit(DefaultExchange.newFromEndpoint(endpoint), eventContext);
    }

    private List<Runnable> scheduledTasks() {
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduledExecutorService, atLeastOnce())
                .schedule(tasks.capture(), anyLong(), any(TimeUnit.class));
        return tasks.getAllValues();
    }

    private void runTasksFrom(int index) {
        List<Runnable> tasks = scheduledTasks();
        for (int i = index; i < tasks.size(); i++) {
            tasks.get(i).run();
        }
    }

    private static EventContext eventContext() {
        EventContext eventContext = mock(EventContext.class);
        PartitionContext partitionContext = mock(PartitionContext.class);
        when(partitionContext.getPartitionId()).thenReturn("0");
        when(eventContext.getPartitionContext()).thenReturn(partitionContext);
        when(eventContext.updateCheckpointAsync()).thenReturn(Mono.empty());
        return eventContext;
    }
}
