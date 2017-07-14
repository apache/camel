/**
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
package org.apache.camel.opentracing.decorators;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.opentracing.SpanDecorator;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

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
        };

        MockTracer tracer = new MockTracer();
        MockSpan span = (MockSpan)tracer.buildSpan("TestSpan").start();

        decorator.pre(span, null, endpoint);

        assertEquals("MyQueue", span.tags().get(Tags.MESSAGE_BUS_DESTINATION.getKey()));
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
            public String getMessageId(Exchange exchange) {
                return messageId;
            }
        };

        MockTracer tracer = new MockTracer();
        MockSpan span = (MockSpan)tracer.buildSpan("TestSpan").start();

        decorator.pre(span, exchange, endpoint);

        assertEquals(messageId, span.tags().get(AwsSqsSpanDecorator.MESSAGE_BUS_ID));
    }

}
