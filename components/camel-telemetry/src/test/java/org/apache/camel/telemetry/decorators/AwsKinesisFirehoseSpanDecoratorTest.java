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

public class AwsKinesisFirehoseSpanDecoratorTest {

    @Test
    public void testGetMessageId() {
        String recordId = "record-1";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsKinesisFirehoseSpanDecorator.RECORD_ID, String.class)).thenReturn(recordId);

        AbstractMessagingSpanDecorator decorator = new AwsKinesisFirehoseSpanDecorator();

        assertEquals(recordId, decorator.getMessageId(exchange));
    }

    @Test
    public void testPre() {
        String operation = "putRecord";
        String streamName = "myStream";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-kinesis-firehose:myStream");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AwsKinesisFirehoseSpanDecorator.OPERATION, String.class)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsKinesisFirehoseSpanDecorator.STREAM_NAME, String.class)).thenReturn(streamName);

        AbstractMessagingSpanDecorator decorator = new AwsKinesisFirehoseSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(operation, span.tags().get(AwsKinesisFirehoseSpanDecorator.FIREHOSE_OPERATION));
        assertEquals(streamName, span.tags().get(AwsKinesisFirehoseSpanDecorator.FIREHOSE_STREAM_NAME));
    }

}
