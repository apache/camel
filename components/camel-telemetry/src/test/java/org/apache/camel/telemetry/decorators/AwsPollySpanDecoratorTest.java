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

public class AwsPollySpanDecoratorTest {

    @Test
    public void testPre() {
        String operation = "synthesizeSpeech";
        String voiceId = "Joanna";
        String outputFormat = "mp3";
        String engine = "neural";
        String languageCode = "en-US";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-polly:default");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(AwsPollySpanDecorator.OPERATION, String.class)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsPollySpanDecorator.VOICE_ID, String.class)).thenReturn(voiceId);
        Mockito.when(message.getHeader(AwsPollySpanDecorator.OUTPUT_FORMAT, String.class)).thenReturn(outputFormat);
        Mockito.when(message.getHeader(AwsPollySpanDecorator.ENGINE, String.class)).thenReturn(engine);
        Mockito.when(message.getHeader(AwsPollySpanDecorator.LANGUAGE_CODE, String.class)).thenReturn(languageCode);

        AbstractSpanDecorator decorator = new AwsPollySpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(operation, span.tags().get(AwsPollySpanDecorator.POLLY_OPERATION));
        assertEquals(voiceId, span.tags().get(AwsPollySpanDecorator.POLLY_VOICE_ID));
        assertEquals(outputFormat, span.tags().get(AwsPollySpanDecorator.POLLY_OUTPUT_FORMAT));
        assertEquals(engine, span.tags().get(AwsPollySpanDecorator.POLLY_ENGINE));
        assertEquals(languageCode, span.tags().get(AwsPollySpanDecorator.POLLY_LANGUAGE_CODE));
    }

}
