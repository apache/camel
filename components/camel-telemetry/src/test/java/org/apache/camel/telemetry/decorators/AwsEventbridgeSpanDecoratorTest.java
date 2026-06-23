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

public class AwsEventbridgeSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String messageId = "abcd";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsEventbridgeSpanDecorator.MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractMessagingSpanDecorator decorator = new AwsEventbridgeSpanDecorator();

        assertEquals(messageId, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String operation = "putRule";
        String ruleName = "myRule";
        String eventSource = "my.source";
        String eventDetailType = "MyEventType";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-eventbridge:default");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsEventbridgeSpanDecorator.OPERATION, String.class)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsEventbridgeSpanDecorator.RULE_NAME, String.class)).thenReturn(ruleName);
        Mockito.when(message.getHeader(AwsEventbridgeSpanDecorator.EVENT_SOURCE, String.class)).thenReturn(eventSource);
        Mockito.when(message.getHeader(AwsEventbridgeSpanDecorator.EVENT_DETAIL_TYPE, String.class))
                .thenReturn(eventDetailType);

        AbstractMessagingSpanDecorator decorator = new AwsEventbridgeSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(operation, span.tags().get(AwsEventbridgeSpanDecorator.EVENTBRIDGE_OPERATION));
        assertEquals(ruleName, span.tags().get(AwsEventbridgeSpanDecorator.EVENTBRIDGE_RULE_NAME));
        assertEquals(eventSource, span.tags().get(AwsEventbridgeSpanDecorator.EVENTBRIDGE_EVENT_SOURCE));
        assertEquals(eventDetailType, span.tags().get(AwsEventbridgeSpanDecorator.EVENTBRIDGE_EVENT_DETAIL_TYPE));
    }

}
