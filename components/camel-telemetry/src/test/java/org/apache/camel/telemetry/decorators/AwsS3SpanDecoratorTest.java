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

public class AwsS3SpanDecoratorTest {

    @Test
    public void testPre() {
        String bucketName = "my-bucket";
        String key = "my/object/key.txt";
        String versionId = "version-1234";
        String operation = "putObject";
        String storageClass = "STANDARD";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-s3:myBucket");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(AwsS3SpanDecorator.BUCKET_NAME, String.class)).thenReturn(bucketName);
        Mockito.when(message.getHeader(AwsS3SpanDecorator.KEY, String.class)).thenReturn(key);
        Mockito.when(message.getHeader(AwsS3SpanDecorator.VERSION_ID, String.class)).thenReturn(versionId);
        Mockito.when(message.getHeader(AwsS3SpanDecorator.OPERATION, String.class)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsS3SpanDecorator.STORAGE_CLASS, String.class)).thenReturn(storageClass);

        AbstractSpanDecorator decorator = new AwsS3SpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(bucketName, span.tags().get(AwsS3SpanDecorator.S3_BUCKET_NAME));
        assertEquals(key, span.tags().get(AwsS3SpanDecorator.S3_KEY));
        assertEquals(versionId, span.tags().get(AwsS3SpanDecorator.S3_VERSION_ID));
        assertEquals(operation, span.tags().get(AwsS3SpanDecorator.S3_OPERATION));
        assertEquals(storageClass, span.tags().get(AwsS3SpanDecorator.S3_STORAGE_CLASS));
    }

}
