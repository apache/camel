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
package org.apache.camel.opentracing.decorators;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.opentracing.SpanDecorator;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class KafkaSpanDecoratorTest {

    @Test
    public void testGetDestinationHeaderTopic() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(KafkaSpanDecorator.TOPIC)).thenReturn("test");

        KafkaSpanDecorator decorator = new KafkaSpanDecorator();

        assertEquals("test", decorator.getDestination(exchange, null));
    }

    @Test
    public void testGetDestinationNoHeaderTopic() {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(endpoint.getEndpointUri())
            .thenReturn("kafka:localhost:9092?topic=test&groupId=testing&consumersCount=1");

        KafkaSpanDecorator decorator = new KafkaSpanDecorator();

        assertEquals("test", decorator.getDestination(exchange, endpoint));
    }

    @Test
    public void testPreOffsetAndPartitionAsStringHeader() {
        String testKey = "TestKey";
        String testOffset = "TestOffset";
        String testPartition = "TestPartition";
        String testPartitionKey = "TestPartitionKey";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("test");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(KafkaSpanDecorator.KEY)).thenReturn(testKey);
        Mockito.when(message.getHeader(KafkaSpanDecorator.OFFSET, String.class)).thenReturn(testOffset);
        Mockito.when(message.getHeader(KafkaSpanDecorator.PARTITION, String.class)).thenReturn(testPartition);
        Mockito.when(message.getHeader(KafkaSpanDecorator.PARTITION_KEY)).thenReturn(testPartitionKey);

        SpanDecorator decorator = new KafkaSpanDecorator();

        MockTracer tracer = new MockTracer();
        MockSpan span = tracer.buildSpan("TestSpan").start();

        decorator.pre(span, exchange, endpoint);

        assertEquals(testKey, span.tags().get(KafkaSpanDecorator.KAFKA_KEY_TAG));
        assertEquals(testOffset, span.tags().get(KafkaSpanDecorator.KAFKA_OFFSET_TAG));
        assertEquals(testPartition, span.tags().get(KafkaSpanDecorator.KAFKA_PARTITION_TAG));
        assertEquals(testPartitionKey, span.tags().get(KafkaSpanDecorator.KAFKA_PARTITION_KEY_TAG));
    }

    @Test
    public void testPrePartitionAsIntegerHeaderAndOffsetAsLongHeader() {
        Long testOffset = 4875454L;
        Integer testPartition = 0;

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("test");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(KafkaSpanDecorator.OFFSET, Long.class)).thenReturn(testOffset);
        Mockito.when(message.getHeader(KafkaSpanDecorator.PARTITION, Integer.class)).thenReturn(testPartition);

        SpanDecorator decorator = new KafkaSpanDecorator();

        MockTracer tracer = new MockTracer();
        MockSpan span = tracer.buildSpan("TestSpan").start();

        decorator.pre(span, exchange, endpoint);

        assertEquals(String.valueOf(testOffset), span.tags().get(KafkaSpanDecorator.KAFKA_OFFSET_TAG));
        assertEquals(String.valueOf(testPartition), span.tags().get(KafkaSpanDecorator.KAFKA_PARTITION_TAG));
    }

}
