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
import org.apache.camel.telemetry.TagConstants;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GooglePubsubSpanDecoratorTest {

    @Test
    public void testPre() {
        String messageId = "message-1";
        String orderingKey = "order-key-1";
        String ackId = "ack-1";
        Integer deliveryAttempt = 2;

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("google-pubsub:myProject:myTopic");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(GooglePubsubSpanDecorator.MESSAGE_ID, String.class)).thenReturn(messageId);
        Mockito.when(message.getHeader(GooglePubsubSpanDecorator.ORDERING_KEY, String.class)).thenReturn(orderingKey);
        Mockito.when(message.getHeader(GooglePubsubSpanDecorator.ACK_ID, String.class)).thenReturn(ackId);
        Mockito.when(message.getHeader(GooglePubsubSpanDecorator.DELIVERY_ATTEMPT, Integer.class))
                .thenReturn(deliveryAttempt);

        AbstractSpanDecorator decorator = new GooglePubsubSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(messageId, span.tags().get(TagConstants.MESSAGE_ID));
        assertEquals(orderingKey, span.tags().get(GooglePubsubSpanDecorator.PUBSUB_ORDERING_KEY));
        assertEquals(ackId, span.tags().get(GooglePubsubSpanDecorator.PUBSUB_ACK_ID));
        assertEquals(deliveryAttempt.toString(), span.tags().get(GooglePubsubSpanDecorator.PUBSUB_DELIVERY_ATTEMPT));
    }

}
