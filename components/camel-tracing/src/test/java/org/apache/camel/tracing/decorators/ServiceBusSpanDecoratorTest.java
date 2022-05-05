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

public class ServiceBusSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String messageId = "abcd";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractMessagingSpanDecorator decorator = new ServiceBusSpanDecorator();

        assertEquals(messageId, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String contentType = "application/json";
        String correlationId = "1234";
        Long deliveryCount = 27L;
        OffsetDateTime enqueuedSequenceNumber = OffsetDateTime.now();
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
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.CONTENT_TYPE, String.class)).thenReturn(contentType);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.CORRELATION_ID, String.class)).thenReturn(correlationId);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.DELIVERY_COUNT, Long.class)).thenReturn(deliveryCount);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.ENQUEUED_SEQUENCE_NUMBER, OffsetDateTime.class))
                .thenReturn(enqueuedSequenceNumber);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.ENQUEUED_TIME, OffsetDateTime.class)).thenReturn(enqueuedTime);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.EXPIRES_AT, OffsetDateTime.class)).thenReturn(expiresAt);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.PARTITION_KEY, String.class)).thenReturn(partitionKey);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.REPLY_TO_SESSION_ID, String.class)).thenReturn(replyToSessionId);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.SESSION_ID, String.class)).thenReturn(sessionId);
        Mockito.when(message.getHeader(ServiceBusSpanDecorator.TIME_TO_LIVE, Duration.class)).thenReturn(ttl);

        AbstractMessagingSpanDecorator decorator = new ServiceBusSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, exchange, endpoint);

        assertEquals(contentType, span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_CONTENT_TYPE));
        assertEquals(correlationId, span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_CORRELATION_ID));
        assertEquals(deliveryCount, span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_DELIVERY_COUNT));
        assertEquals(enqueuedSequenceNumber.toString(),
                span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_ENQUEUED_SEQUENCE_NUMBER));
        assertEquals(enqueuedTime.toString(), span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_ENQUEUED_TIME));
        assertEquals(expiresAt.toString(), span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_EXPIRES_AT));
        assertEquals(partitionKey, span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_PARTITION_KEY));
        assertEquals(replyToSessionId, span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_REPLY_TO_SESSION_ID));
        assertEquals(sessionId, span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_SESSION_ID));
        assertEquals(ttl.toString(), span.tags().get(ServiceBusSpanDecorator.SERVICEBUS_TIME_TO_LIVE));
    }

}
