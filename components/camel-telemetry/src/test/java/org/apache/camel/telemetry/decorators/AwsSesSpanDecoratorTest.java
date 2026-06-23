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

public class AwsSesSpanDecoratorTest {

    @Test
    public void testPre() {
        String from = "sender@example.com";
        String subject = "Hello";
        String to = "recipient@example.com";
        String messageId = "msg-1234";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-ses:from@example.com");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(AwsSesSpanDecorator.FROM, String.class)).thenReturn(from);
        Mockito.when(message.getHeader(AwsSesSpanDecorator.SUBJECT, String.class)).thenReturn(subject);
        Mockito.when(message.getHeader(AwsSesSpanDecorator.TO, String.class)).thenReturn(to);
        Mockito.when(message.getHeader(AwsSesSpanDecorator.MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractSpanDecorator decorator = new AwsSesSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(from, span.tags().get(AwsSesSpanDecorator.SES_FROM));
        assertEquals(subject, span.tags().get(AwsSesSpanDecorator.SES_SUBJECT));
        assertEquals(to, span.tags().get(AwsSesSpanDecorator.SES_TO));
        assertEquals(messageId, span.tags().get(AwsSesSpanDecorator.SES_MESSAGE_ID));
    }

}
