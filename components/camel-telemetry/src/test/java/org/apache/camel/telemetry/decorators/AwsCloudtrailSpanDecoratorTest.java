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

public class AwsCloudtrailSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String eventId = "abcd-1234";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsCloudtrailSpanDecorator.EVENT_ID, String.class)).thenReturn(eventId);

        AbstractMessagingSpanDecorator decorator = new AwsCloudtrailSpanDecorator();

        assertEquals(eventId, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String eventName = "ConsoleLogin";
        String eventSource = "signin.amazonaws.com";
        String username = "alice";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws-cloudtrail:default");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsCloudtrailSpanDecorator.EVENT_NAME, String.class)).thenReturn(eventName);
        Mockito.when(message.getHeader(AwsCloudtrailSpanDecorator.EVENT_SOURCE, String.class)).thenReturn(eventSource);
        Mockito.when(message.getHeader(AwsCloudtrailSpanDecorator.USERNAME, String.class)).thenReturn(username);

        AbstractMessagingSpanDecorator decorator = new AwsCloudtrailSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(eventName, span.tags().get(AwsCloudtrailSpanDecorator.CLOUDTRAIL_EVENT_NAME));
        assertEquals(eventSource, span.tags().get(AwsCloudtrailSpanDecorator.CLOUDTRAIL_EVENT_SOURCE));
        assertEquals(username, span.tags().get(AwsCloudtrailSpanDecorator.CLOUDTRAIL_USERNAME));
    }

}
