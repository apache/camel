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
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.PartitionContext;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventHubsConsumerPerPartitionCheckpointTest {

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
    private Method processCommit;

    @BeforeEach
    void setUp() throws Exception {
        configuration.setCheckpointBatchSize(3);
        configuration.setCheckpointBatchTimeout(60_000);

        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);
        when(exchangeFactory.create(any(Endpoint.class), anyBoolean()))
                .thenAnswer(invocation -> DefaultExchange.newFromEndpoint(invocation.getArgument(0)));
        when(endpoint.getConfiguration()).thenReturn(configuration);
        consumer = new EventHubsConsumer(endpoint, processor);

        setField(consumer, "scheduledExecutorService", scheduledExecutorService);
        when(scheduledExecutorService.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(scheduledFuture);

        processCommit = EventHubsConsumer.class.getDeclaredMethod("processCommit", Exchange.class, EventContext.class);
        processCommit.setAccessible(true);
    }

    @Test
    void processedEventsAreTrackedPerPartition() throws Exception {
        invokeProcessCommit(exchange(), eventContext("0"));
        invokeProcessCommit(exchange(), eventContext("0"));
        invokeProcessCommit(exchange(), eventContext("1"));

        assertEquals(2, processedEventsForPartition("0"));
        assertEquals(1, processedEventsForPartition("1"));
    }

    @Test
    void batchSizeCheckpointOnOnePartitionDoesNotResetOtherPartition() throws Exception {
        configuration.setCheckpointBatchSize(2);
        EventContext partitionZeroContext = eventContext("0");
        when(partitionZeroContext.updateCheckpointAsync()).thenReturn(Mono.empty());

        invokeProcessCommit(exchange(), eventContext("1"));
        invokeProcessCommit(exchange(), partitionZeroContext);
        invokeProcessCommit(exchange(), partitionZeroContext);

        assertEquals(0, processedEventsForPartition("0"));
        assertEquals(1, processedEventsForPartition("1"));
        verify(partitionZeroContext, times(1)).updateCheckpointAsync();
    }

    @Test
    void eachPartitionSchedulesOwnCheckpointTask() throws Exception {
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);

        invokeProcessCommit(exchange(), eventContext("0"));
        invokeProcessCommit(exchange(), eventContext("1"));

        verify(scheduledExecutorService, times(2)).schedule(tasks.capture(), anyLong(), any(TimeUnit.class));
        assertNotSame(tasks.getAllValues().get(0), tasks.getAllValues().get(1));

        Map<String, EventHubsCheckpointUpdaterTask> checkpointTasks = getCheckpointTasksByPartition();
        assertEquals(2, checkpointTasks.size());
        assertNotSame(checkpointTasks.get("0"), checkpointTasks.get("1"));
    }

    @Test
    void reusesCheckpointTaskWithinSamePartition() throws Exception {
        invokeProcessCommit(exchange(), eventContext("3"));
        invokeProcessCommit(exchange(), eventContext("3"));

        verify(scheduledExecutorService, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        assertEquals(1, getCheckpointTasksByPartition().size());
        assertEquals(2, processedEventsForPartition("3"));
    }

    private void invokeProcessCommit(Exchange exchange, EventContext eventContext) throws Exception {
        processCommit.invoke(consumer, exchange, eventContext);
    }

    private Exchange exchange() {
        return DefaultExchange.newFromEndpoint(endpoint);
    }

    private EventContext eventContext(String partitionId) {
        EventContext eventContext = mock(EventContext.class);
        PartitionContext partitionContext = mock(PartitionContext.class);
        when(partitionContext.getPartitionId()).thenReturn(partitionId);
        when(eventContext.getPartitionContext()).thenReturn(partitionContext);
        return eventContext;
    }

    @SuppressWarnings("unchecked")
    private Map<String, AtomicInteger> getProcessedEventsByPartition() throws Exception {
        return (Map<String, AtomicInteger>) getField(consumer, "processedEventsByPartition");
    }

    @SuppressWarnings("unchecked")
    private Map<String, EventHubsCheckpointUpdaterTask> getCheckpointTasksByPartition() throws Exception {
        return (Map<String, EventHubsCheckpointUpdaterTask>) getField(consumer, "checkpointTasksByPartition");
    }

    private int processedEventsForPartition(String partitionId) throws Exception {
        AtomicInteger counter = getProcessedEventsByPartition().get(partitionId);
        return counter == null ? 0 : counter.get();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
