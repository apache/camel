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

public class AwsRekognitionSpanDecoratorTest {

    @Test
    public void testPre() {
        String operation = "indexFaces";
        String collectionId = "MyCollection";
        String jobId = "job-1234";
        String jobName = "media-analysis-1";
        String faceId = "face-abcd";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-rekognition:default");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(AwsRekognitionSpanDecorator.OPERATION, String.class)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsRekognitionSpanDecorator.COLLECTION_ID, String.class)).thenReturn(collectionId);
        Mockito.when(message.getHeader(AwsRekognitionSpanDecorator.JOB_ID, String.class)).thenReturn(jobId);
        Mockito.when(message.getHeader(AwsRekognitionSpanDecorator.JOB_NAME, String.class)).thenReturn(jobName);
        Mockito.when(message.getHeader(AwsRekognitionSpanDecorator.FACE_ID, String.class)).thenReturn(faceId);

        AbstractSpanDecorator decorator = new AwsRekognitionSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(operation, span.tags().get(AwsRekognitionSpanDecorator.REKOGNITION_OPERATION));
        assertEquals(collectionId, span.tags().get(AwsRekognitionSpanDecorator.REKOGNITION_COLLECTION_ID));
        assertEquals(jobId, span.tags().get(AwsRekognitionSpanDecorator.REKOGNITION_JOB_ID));
        assertEquals(jobName, span.tags().get(AwsRekognitionSpanDecorator.REKOGNITION_JOB_NAME));
        assertEquals(faceId, span.tags().get(AwsRekognitionSpanDecorator.REKOGNITION_FACE_ID));
    }

}
