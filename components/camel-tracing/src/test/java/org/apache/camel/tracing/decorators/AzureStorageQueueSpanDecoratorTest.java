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

public class AzureStorageQueueSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String messageId = "abcd";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AzureStorageQueueSpanDecorator.MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractMessagingSpanDecorator decorator = new AzureStorageQueueSpanDecorator();

        assertEquals(messageId, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        OffsetDateTime insertionTime = OffsetDateTime.now();
        OffsetDateTime expirationTime = OffsetDateTime.now();
        OffsetDateTime timeNextVisible = OffsetDateTime.now();
        Long dequeueCount = 20020504L;
        String name = "MyName";
        Duration visibilityTimeout = Duration.ofDays(7);
        Duration timeToLive = Duration.ofDays(7);

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("azure-storage-queue:queueName");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AzureStorageQueueSpanDecorator.INSERTION_TIME, OffsetDateTime.class))
                .thenReturn(insertionTime);
        Mockito.when(message.getHeader(AzureStorageQueueSpanDecorator.EXPIRATION_TIME, OffsetDateTime.class))
                .thenReturn(expirationTime);
        Mockito.when(message.getHeader(AzureStorageQueueSpanDecorator.TIME_NEXT_VISIBLE, OffsetDateTime.class))
                .thenReturn(timeNextVisible);
        Mockito.when(message.getHeader(AzureStorageQueueSpanDecorator.DEQUEUE_COUNT, Long.class)).thenReturn(dequeueCount);
        Mockito.when(message.getHeader(AzureStorageQueueSpanDecorator.NAME, String.class)).thenReturn(name);
        Mockito.when(message.getHeader(AzureStorageQueueSpanDecorator.VISIBILITY_TIMEOUT, Duration.class))
                .thenReturn(visibilityTimeout);
        Mockito.when(message.getHeader(AzureStorageQueueSpanDecorator.TIME_TO_LIVE, Duration.class)).thenReturn(timeToLive);

        AbstractMessagingSpanDecorator decorator = new AzureStorageQueueSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, exchange, endpoint);

        assertEquals(insertionTime.toString(), span.tags().get(AzureStorageQueueSpanDecorator.STORAGE_QUEUE_INSERTION_TIME));
        assertEquals(expirationTime.toString(), span.tags().get(AzureStorageQueueSpanDecorator.STORAGE_QUEUE_EXPIRATION_TIME));
        assertEquals(timeNextVisible.toString(),
                span.tags().get(AzureStorageQueueSpanDecorator.STORAGE_QUEUE_TIME_NEXT_VISIBLE));
        assertEquals(dequeueCount, span.tags().get(AzureStorageQueueSpanDecorator.STORAGE_QUEUE_DEQUEUE_COUNT));
        assertEquals(name, span.tags().get(AzureStorageQueueSpanDecorator.STORAGE_QUEUE_NAME));
        assertEquals(visibilityTimeout.toString(),
                span.tags().get(AzureStorageQueueSpanDecorator.STORAGE_QUEUE_VISIBILITY_TIMEOUT));
        assertEquals(timeToLive.toString(), span.tags().get(AzureStorageQueueSpanDecorator.STORAGE_QUEUE_TIME_TO_LIVE));
    }
}
