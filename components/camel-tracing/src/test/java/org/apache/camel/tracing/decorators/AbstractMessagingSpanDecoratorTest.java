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
import org.apache.camel.tracing.MockSpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.Tag;
import org.apache.camel.tracing.TagConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractMessagingSpanDecoratorTest {

    @Test
    public void testOperationName() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("jms://MyQueue?hello=world");

        SpanDecorator decorator = new AbstractMessagingSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        assertEquals("MyQueue", decorator.getOperationName(null, endpoint));
    }

    @Test
    public void testPreMessageBusDestination() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("jms://MyQueue?hello=world");

        SpanDecorator decorator = new AbstractMessagingSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, null, endpoint);

        assertEquals("MyQueue", span.tags().get(Tag.MESSAGE_BUS_DESTINATION.name()));
        assertEquals("MyQueue", span.tags().get(TagConstants.MESSAGE_BUS_DESTINATION));
    }

    @Test
    public void testPreMessageId() {
        String messageId = "abcd";
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("test");

        SpanDecorator decorator = new AbstractMessagingSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }

            @Override
            public String getMessageId(Exchange exchange) {
                return messageId;
            }
        };

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, exchange, endpoint);

        assertEquals(messageId, span.tags().get(AbstractMessagingSpanDecorator.MESSAGE_BUS_ID));
        assertEquals(messageId, span.tags().get(TagConstants.MESSAGE_ID));
    }

}
