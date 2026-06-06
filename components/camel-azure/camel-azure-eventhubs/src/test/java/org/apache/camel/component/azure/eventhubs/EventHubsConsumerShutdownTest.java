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

import java.lang.reflect.Field;

import com.azure.messaging.eventhubs.EventProcessorClient;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventHubsConsumerShutdownTest {

    private final EventHubsEndpoint endpoint = mock();
    private final EventHubsConfiguration configuration = mock();
    private final AsyncProcessor processor = mock();
    private final CamelContext context = mock();
    private final ExtendedCamelContext ecc = mock();
    private final ExchangeFactory ef = mock();
    private final EventProcessorClient processorClient = mock();

    @BeforeEach
    void setUp() {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(ef.create(any(Endpoint.class), anyBoolean()))
                .thenAnswer(invocation -> DefaultExchange.newFromEndpoint(invocation.getArgument(0)));
        when(endpoint.getConfiguration()).thenReturn(configuration);
    }

    @Test
    void deferShutdownReturnsTrueAndStopsClient() throws Exception {
        EventHubsConsumer consumer = new EventHubsConsumer(endpoint, processor);
        setProcessorClient(consumer, processorClient);

        boolean deferred = consumer.deferShutdown(ShutdownRunningTask.CompleteAllTasks);

        assertTrue(deferred);
        verify(processorClient).stop();
    }

    @Test
    void pendingExchangesSizeIsZeroInitially() {
        EventHubsConsumer consumer = new EventHubsConsumer(endpoint, processor);

        assertEquals(0, consumer.getPendingExchangesSize());
    }

    @Test
    void pendingExchangesSizeTracksInflightMessages() throws Exception {
        EventHubsConsumer consumer = new EventHubsConsumer(endpoint, processor);
        setProcessorClient(consumer, processorClient);

        assertEquals(0, consumer.getPendingExchangesSize());

        // simulate onEventListener by getting the completion callback and invoking it
        Exchange exchange = DefaultExchange.newFromEndpoint(endpoint);
        // manually increment to simulate what onEventListener does
        incrementPendingExchanges(consumer);

        assertEquals(1, consumer.getPendingExchangesSize());

        // simulate completion
        decrementPendingExchanges(consumer);

        assertEquals(0, consumer.getPendingExchangesSize());
    }

    @Test
    void doStopStopsClientWithoutNullingIt() throws Exception {
        EventHubsConsumer consumer = new EventHubsConsumer(endpoint, processor);
        setProcessorClient(consumer, processorClient);

        consumer.doStop();

        verify(processorClient).stop();
        // processorClient should still be available for in-flight exchanges
        assertEquals(processorClient, getProcessorClient(consumer));
    }

    @Test
    void doShutdownNullsClient() throws Exception {
        EventHubsConsumer consumer = new EventHubsConsumer(endpoint, processor);
        setProcessorClient(consumer, processorClient);

        consumer.doShutdown();

        assertEquals(null, getProcessorClient(consumer));
    }

    private static void setProcessorClient(EventHubsConsumer consumer, EventProcessorClient client) throws Exception {
        Field field = EventHubsConsumer.class.getDeclaredField("processorClient");
        field.setAccessible(true);
        field.set(consumer, client);
    }

    private static EventProcessorClient getProcessorClient(EventHubsConsumer consumer) throws Exception {
        Field field = EventHubsConsumer.class.getDeclaredField("processorClient");
        field.setAccessible(true);
        return (EventProcessorClient) field.get(consumer);
    }

    private static void incrementPendingExchanges(EventHubsConsumer consumer) throws Exception {
        Field field = EventHubsConsumer.class.getDeclaredField("pendingExchanges");
        field.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicInteger) field.get(consumer)).incrementAndGet();
    }

    private static void decrementPendingExchanges(EventHubsConsumer consumer) throws Exception {
        Field field = EventHubsConsumer.class.getDeclaredField("pendingExchanges");
        field.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicInteger) field.get(consumer)).decrementAndGet();
    }
}
