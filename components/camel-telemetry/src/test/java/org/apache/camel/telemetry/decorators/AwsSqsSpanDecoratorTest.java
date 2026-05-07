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

public class AwsSqsSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String messageId = "abcd";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsSqsSpanDecorator.MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractMessagingSpanDecorator decorator = new AwsSqsSpanDecorator();

        assertEquals(messageId, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String md5OfBody = "098f6bcd4621d373cade4e832627b4f6";
        String receiptHandle = "MbZj6wDWli+JvwwJaBV+3dcjk2YW2vA3";
        Integer delaySeconds = 30;
        String operation = "sendMessage";
        String messageGroupId = "myGroup";
        String sequenceNumber = "1234567890";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-sqs:myQueue");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsSqsSpanDecorator.MD5_OF_BODY, String.class)).thenReturn(md5OfBody);
        Mockito.when(message.getHeader(AwsSqsSpanDecorator.RECEIPT_HANDLE, String.class)).thenReturn(receiptHandle);
        Mockito.when(message.getHeader(AwsSqsSpanDecorator.DELAY_HEADER, Integer.class)).thenReturn(delaySeconds);
        Mockito.when(message.getHeader(AwsSqsSpanDecorator.OPERATION, String.class)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsSqsSpanDecorator.MESSAGE_GROUP_ID, String.class)).thenReturn(messageGroupId);
        Mockito.when(message.getHeader(AwsSqsSpanDecorator.SEQUENCE_NUMBER, String.class)).thenReturn(sequenceNumber);

        AbstractMessagingSpanDecorator decorator = new AwsSqsSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(md5OfBody, span.tags().get(AwsSqsSpanDecorator.SQS_MD5_OF_BODY));
        assertEquals(receiptHandle, span.tags().get(AwsSqsSpanDecorator.SQS_RECEIPT_HANDLE));
        assertEquals(delaySeconds.toString(), span.tags().get(AwsSqsSpanDecorator.SQS_DELAY_SECONDS));
        assertEquals(operation, span.tags().get(AwsSqsSpanDecorator.SQS_OPERATION));
        assertEquals(messageGroupId, span.tags().get(AwsSqsSpanDecorator.SQS_MESSAGE_GROUP_ID));
        assertEquals(sequenceNumber, span.tags().get(AwsSqsSpanDecorator.SQS_SEQUENCE_NUMBER));
    }

}
