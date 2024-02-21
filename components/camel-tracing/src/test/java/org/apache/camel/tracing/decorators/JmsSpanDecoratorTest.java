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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String messageId = "abcd";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(JmsSpanDecorator.JMS_MESSAGE_ID, String.class)).thenReturn(messageId);

        AbstractMessagingSpanDecorator decorator = new JmsSpanDecorator();

        assertEquals(messageId, decorator.getMessageId(exchange));
    }

    @Test
    public void testGetDestination() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(endpoint.getEndpointUri()).thenReturn("jms:cheese?clientId=123");

        AbstractMessagingSpanDecorator decorator = new JmsSpanDecorator();
        assertEquals("cheese", decorator.getDestination(exchange, endpoint));
    }

    @Test
    public void testGetDestinationDynamic() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(exchange.getMessage().getHeader("CamelJmsDestinationName", String.class)).thenReturn("gauda");
        Mockito.when(endpoint.getEndpointUri()).thenReturn("jms:${header.foo}?clientId=123");

        AbstractMessagingSpanDecorator decorator = new JmsSpanDecorator();
        assertEquals("gauda", decorator.getDestination(exchange, endpoint));
    }

}
