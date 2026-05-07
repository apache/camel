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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AwsDdbStreamSpanDecoratorTest {

    @Test
    public void testGetMessageIdEventId() {
        String eventId = "event-1";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsDdbStreamSpanDecorator.EVENT_ID, String.class)).thenReturn(eventId);

        AbstractMessagingSpanDecorator decorator = new AwsDdbStreamSpanDecorator();

        assertEquals(eventId, decorator.getMessageId(exchange));
    }

    @Test
    public void testGetMessageIdSequenceNumberFallback() {
        String sequenceNumber = "1234567890";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsDdbStreamSpanDecorator.EVENT_ID, String.class)).thenReturn(null);
        Mockito.when(message.getHeader(AwsDdbStreamSpanDecorator.SEQUENCE_NUMBER, String.class)).thenReturn(sequenceNumber);

        AbstractMessagingSpanDecorator decorator = new AwsDdbStreamSpanDecorator();

        assertEquals(sequenceNumber, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String eventSource = "aws:dynamodb";
        String eventName = "INSERT";
        Long sizeBytes = 256L;

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-ddbstream:myTable");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsDdbStreamSpanDecorator.EVENT_SOURCE, String.class)).thenReturn(eventSource);
        Mockito.when(message.getHeader(AwsDdbStreamSpanDecorator.EVENT_NAME, String.class)).thenReturn(eventName);
        Mockito.when(message.getHeader(AwsDdbStreamSpanDecorator.SIZE_BYTES, Long.class)).thenReturn(sizeBytes);

        AbstractMessagingSpanDecorator decorator = new AwsDdbStreamSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(eventSource, span.tags().get(AwsDdbStreamSpanDecorator.DDB_STREAM_EVENT_SOURCE));
        assertEquals(eventName, span.tags().get(AwsDdbStreamSpanDecorator.DDB_STREAM_EVENT_NAME));
        assertEquals(sizeBytes.toString(), span.tags().get(AwsDdbStreamSpanDecorator.DDB_STREAM_SIZE_BYTES));
    }

}
