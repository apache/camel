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

public class AwsTranscribeSpanDecoratorTest {

    @Test
    public void testPre() {
        String transcriptionJobName = "meeting-2024-01-15";
        String languageCode = "en-US";
        String mediaFormat = "mp3";
        String mediaUri = "s3://my-audio/meeting.mp3";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-transcribe:default");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(AwsTranscribeSpanDecorator.TRANSCRIPTION_JOB_NAME, String.class))
                .thenReturn(transcriptionJobName);
        Mockito.when(message.getHeader(AwsTranscribeSpanDecorator.LANGUAGE_CODE, String.class)).thenReturn(languageCode);
        Mockito.when(message.getHeader(AwsTranscribeSpanDecorator.MEDIA_FORMAT, String.class)).thenReturn(mediaFormat);
        Mockito.when(message.getHeader(AwsTranscribeSpanDecorator.MEDIA_URI, String.class)).thenReturn(mediaUri);

        AbstractSpanDecorator decorator = new AwsTranscribeSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(transcriptionJobName, span.tags().get(AwsTranscribeSpanDecorator.TRANSCRIBE_TRANSCRIPTION_JOB_NAME));
        assertEquals(languageCode, span.tags().get(AwsTranscribeSpanDecorator.TRANSCRIBE_LANGUAGE_CODE));
        assertEquals(mediaFormat, span.tags().get(AwsTranscribeSpanDecorator.TRANSCRIBE_MEDIA_FORMAT));
        assertEquals(mediaUri, span.tags().get(AwsTranscribeSpanDecorator.TRANSCRIBE_MEDIA_URI));
    }

}
