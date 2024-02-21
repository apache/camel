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
package org.apache.camel.tracing.decorators;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.tracing.MockSpanAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AzureServiceBusSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String messageId = "abcd";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractMessagingSpanDecorator decorator = new AzureServiceBusSpanDecorator();

        assertEquals(messageId, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String contentType = "application/json";
        String correlationId = "1234";
        Long deliveryCount = 27L;
        Long enqueuedSequenceNumber = 1L;
        OffsetDateTime enqueuedTime = OffsetDateTime.now();
        OffsetDateTime expiresAt = OffsetDateTime.now();
        String partitionKey = "MyPartitionKey";
        String replyToSessionId = "MyReplyToSessionId";
        String sessionId = "4321";
        Duration ttl = Duration.ofDays(7);

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("azure-servicebus:topicOrQueueName");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.CONTENT_TYPE, String.class)).thenReturn(contentType);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.CORRELATION_ID, String.class)).thenReturn(correlationId);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.DELIVERY_COUNT, Long.class)).thenReturn(deliveryCount);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.ENQUEUED_SEQUENCE_NUMBER, Long.class))
                .thenReturn(enqueuedSequenceNumber);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.ENQUEUED_TIME, OffsetDateTime.class))
                .thenReturn(enqueuedTime);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.EXPIRES_AT, OffsetDateTime.class)).thenReturn(expiresAt);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.PARTITION_KEY, String.class)).thenReturn(partitionKey);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.REPLY_TO_SESSION_ID, String.class))
                .thenReturn(replyToSessionId);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.SESSION_ID, String.class)).thenReturn(sessionId);
        Mockito.when(message.getHeader(AzureServiceBusSpanDecorator.TIME_TO_LIVE, Duration.class)).thenReturn(ttl);

        AbstractMessagingSpanDecorator decorator = new AzureServiceBusSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, exchange, endpoint);

        assertEquals(contentType, span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_CONTENT_TYPE));
        assertEquals(correlationId, span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_CORRELATION_ID));
        assertEquals(deliveryCount, span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_DELIVERY_COUNT));
        assertEquals(enqueuedSequenceNumber, span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_ENQUEUED_SEQUENCE_NUMBER));
        assertEquals(enqueuedTime.toString(), span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_ENQUEUED_TIME));
        assertEquals(expiresAt.toString(), span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_EXPIRES_AT));
        assertEquals(partitionKey, span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_PARTITION_KEY));
        assertEquals(replyToSessionId, span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_REPLY_TO_SESSION_ID));
        assertEquals(sessionId, span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_SESSION_ID));
        assertEquals(ttl.toString(), span.tags().get(AzureServiceBusSpanDecorator.SERVICEBUS_TIME_TO_LIVE));
    }

}
