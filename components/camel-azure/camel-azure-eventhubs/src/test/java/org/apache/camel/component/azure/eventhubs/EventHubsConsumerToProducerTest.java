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

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.PartitionContext;
import com.azure.messaging.eventhubs.models.SendOptions;
import org.apache.camel.Exchange;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An exchange created by the azure-eventhubs consumer and sent to an azure-eventhubs producer, as in a route that
 * consumes from one Event Hub and produces to another.
 */
class EventHubsConsumerToProducerTest extends CamelTestSupport {

    private static final String CONSUMER = "azure-eventhubs:namespace/hub?connectionString=RAW(Endpoint=sb://"
                                           + "namespace.servicebus.windows.net/;SharedAccessKeyName=name;SharedAccessKey=a2V5;EntityPath=hub)";
    private static final String PRODUCER = "azure-eventhubs:?producerAsyncClient=#producerClient";

    private final EventHubProducerAsyncClient producerClient = mock(EventHubProducerAsyncClient.class);

    @BeforeEach
    void bindProducerClient() {
        when(producerClient.send(anyIterable())).thenReturn(Mono.empty());
        when(producerClient.send(anyIterable(), any(SendOptions.class))).thenReturn(Mono.empty());
        context.getRegistry().bind("producerClient", producerClient);
    }

    @Test
    void consumerRecordsThePartitionOfTheReceivedEvent() {
        Exchange exchange = received("3", "device-1");

        assertEquals("3", exchange.getMessage().getHeader(EventHubsConstants.PARTITION_ID));
        assertEquals("device-1", exchange.getMessage().getHeader(EventHubsConstants.PARTITION_KEY));
        assertEquals("3", exchange.getProperty(EventHubsConstants.RECEIVED_PARTITION_ID));
        assertEquals("device-1", exchange.getProperty(EventHubsConstants.RECEIVED_PARTITION_KEY));
    }

    @Test
    void receivedPartitionKeyIsKeptWhenNoPartitionIsChosen() {
        SendOptions options = send(PRODUCER, received("3", "device-1"));

        assertEquals("device-1", options.getPartitionKey());
        assertNull(options.getPartitionId());
    }

    @Test
    void receivedPartitionIdIsNotReused() {
        assertNull(send(PRODUCER, received("3", null)));
        verify(producerClient).send(anyIterable());
    }

    @Test
    void configuredPartitionKeyIsUsed() {
        String producer = PRODUCER + "&partitionKey=configured";

        assertEquals("configured", send(producer, received("3", "device-1")).getPartitionKey());
        assertEquals("configured", send(producer, received("3", null)).getPartitionKey());
    }

    @Test
    void configuredPartitionIdIsUsed() {
        String producer = PRODUCER + "&partitionId=1";

        SendOptions keyed = send(producer, received("3", "device-1"));
        assertNull(keyed.getPartitionKey());
        assertEquals("1", keyed.getPartitionId());

        SendOptions unkeyed = send(producer, received("3", null));
        assertNull(unkeyed.getPartitionKey());
        assertEquals("1", unkeyed.getPartitionId());
    }

    @Test
    void partitionChosenByTheRouteIsUsed() {
        Exchange rekeyed = received("3", "device-1");
        rekeyed.getMessage().setHeader(EventHubsConstants.PARTITION_KEY, "tenant-7");
        SendOptions byKey = send(PRODUCER, rekeyed);
        assertEquals("tenant-7", byKey.getPartitionKey());
        assertNull(byKey.getPartitionId());

        Exchange pinned = received("3", "device-1");
        pinned.getMessage().setHeader(EventHubsConstants.PARTITION_ID, "1");
        SendOptions byId = send(PRODUCER, pinned);
        assertNull(byId.getPartitionKey());
        assertEquals("1", byId.getPartitionId());
    }

    @Test
    void receivedPartitionIsKeptWhenTheRouteRemovesTheReceivedProperty() {
        Exchange mirrored = received("3", null);
        mirrored.removeProperty(EventHubsConstants.RECEIVED_PARTITION_ID);
        SendOptions byId = send(PRODUCER, mirrored);
        assertNull(byId.getPartitionKey());
        assertEquals("3", byId.getPartitionId());

        Exchange keyed = received("3", "device-1");
        keyed.removeProperty(EventHubsConstants.RECEIVED_PARTITION_KEY);
        SendOptions byKey = send(PRODUCER + "&partitionKey=configured", keyed);
        assertEquals("device-1", byKey.getPartitionKey());
        assertNull(byKey.getPartitionId());
    }

    @Test
    void headerOverridesTheEndpointOptionWhenNotFromConsumer() {
        Exchange exchange = ExchangeBuilder.anExchange(context).withBody("event")
                .withHeader(EventHubsConstants.PARTITION_ID, "1").build();

        assertEquals("1", send(PRODUCER + "&partitionId=2", exchange).getPartitionId());
    }

    @Test
    void partitionKeyAndIdSetTogetherAreRejectedWhenNotFromConsumer() {
        Exchange result = template.send(PRODUCER, ExchangeBuilder.anExchange(context).withBody("event")
                .withHeader(EventHubsConstants.PARTITION_KEY, "device-1")
                .withHeader(EventHubsConstants.PARTITION_ID, "1").build());

        assertInstanceOf(IllegalArgumentException.class, result.getException());
    }

    @Test
    void camelHeadersAreNotSentAsEventProperties() {
        Exchange exchange = received("3", "device-1");
        exchange.getMessage().setHeader("tenant", "acme");

        send(PRODUCER, exchange);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<EventData>> events = ArgumentCaptor.forClass(Iterable.class);
        verify(producerClient).send(events.capture(), any(SendOptions.class));
        EventData sent = events.getValue().iterator().next();
        assertEquals("acme", sent.getProperties().get("tenant"));
        assertTrue(sent.getProperties().keySet().stream().noneMatch(name -> name.startsWith("Camel")),
                "Camel headers sent as event properties: " + sent.getProperties().keySet());
    }

    private SendOptions send(String uri, Exchange exchange) {
        clearInvocations(producerClient);
        Exchange result = template.send(uri, exchange);
        assertNull(result.getException());

        ArgumentCaptor<SendOptions> options = ArgumentCaptor.forClass(SendOptions.class);
        verify(producerClient, atMostOnce()).send(anyIterable(), options.capture());
        return options.getAllValues().isEmpty() ? null : options.getValue();
    }

    private Exchange received(String partitionId, String partitionKey) {
        EventData eventData = mock(EventData.class);
        when(eventData.getBody()).thenReturn("event".getBytes(StandardCharsets.UTF_8));
        when(eventData.getPartitionKey()).thenReturn(partitionKey);
        when(eventData.getEnqueuedTime()).thenReturn(Instant.now());
        PartitionContext partitionContext = mock(PartitionContext.class);
        when(partitionContext.getPartitionId()).thenReturn(partitionId);
        EventContext eventContext = mock(EventContext.class);
        when(eventContext.getEventData()).thenReturn(eventData);
        when(eventContext.getPartitionContext()).thenReturn(partitionContext);

        EventHubsConsumer consumer = new EventHubsConsumer(
                context.getEndpoint(CONSUMER, EventHubsEndpoint.class),
                exchange -> {
                });
        return consumer.createAzureEventHubExchange(eventContext);
    }
}
