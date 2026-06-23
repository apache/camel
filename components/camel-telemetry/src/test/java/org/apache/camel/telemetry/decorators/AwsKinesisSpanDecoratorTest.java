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

public class AwsKinesisSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String sequenceNumber = "49545115243490985018280067714973144582180062593244200961";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsKinesisSpanDecorator.SEQUENCE_NUMBER, String.class)).thenReturn(sequenceNumber);

        AbstractMessagingSpanDecorator decorator = new AwsKinesisSpanDecorator();

        assertEquals(sequenceNumber, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String partitionKey = "partition-1";
        String shardId = "shardId-000000000000";
        String approxArrivalTimestamp = "2026-04-30T12:00:00Z";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-kinesis:myStream");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsKinesisSpanDecorator.PARTITION_KEY, String.class)).thenReturn(partitionKey);
        Mockito.when(message.getHeader(AwsKinesisSpanDecorator.SHARD_ID, String.class)).thenReturn(shardId);
        Mockito.when(message.getHeader(AwsKinesisSpanDecorator.APPROX_ARRIVAL_TIME, String.class))
                .thenReturn(approxArrivalTimestamp);

        AbstractMessagingSpanDecorator decorator = new AwsKinesisSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(partitionKey, span.tags().get(AwsKinesisSpanDecorator.KINESIS_PARTITION_KEY));
        assertEquals(shardId, span.tags().get(AwsKinesisSpanDecorator.KINESIS_SHARD_ID));
        assertEquals(approxArrivalTimestamp, span.tags().get(AwsKinesisSpanDecorator.KINESIS_APPROX_ARRIVAL_TIME));
    }

}
