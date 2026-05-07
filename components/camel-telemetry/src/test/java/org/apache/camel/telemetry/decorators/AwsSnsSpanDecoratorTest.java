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

public class AwsSnsSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String messageId = "abcd";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsSnsSpanDecorator.MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractMessagingSpanDecorator decorator = new AwsSnsSpanDecorator();

        assertEquals(messageId, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String subject = "my-subject";
        String messageStructure = "json";
        String messageGroupId = "myGroup";
        String sequenceNumber = "1234567890";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-sns:myTopic");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsSnsSpanDecorator.SUBJECT, String.class)).thenReturn(subject);
        Mockito.when(message.getHeader(AwsSnsSpanDecorator.MESSAGE_STRUCTURE, String.class)).thenReturn(messageStructure);
        Mockito.when(message.getHeader(AwsSnsSpanDecorator.MESSAGE_GROUP_ID, String.class)).thenReturn(messageGroupId);
        Mockito.when(message.getHeader(AwsSnsSpanDecorator.SEQUENCE_NUMBER, String.class)).thenReturn(sequenceNumber);

        AbstractMessagingSpanDecorator decorator = new AwsSnsSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(subject, span.tags().get(AwsSnsSpanDecorator.SNS_SUBJECT));
        assertEquals(messageStructure, span.tags().get(AwsSnsSpanDecorator.SNS_MESSAGE_STRUCTURE));
        assertEquals(messageGroupId, span.tags().get(AwsSnsSpanDecorator.SNS_MESSAGE_GROUP_ID));
        assertEquals(sequenceNumber, span.tags().get(AwsSnsSpanDecorator.SNS_SEQUENCE_NUMBER));
    }

}
