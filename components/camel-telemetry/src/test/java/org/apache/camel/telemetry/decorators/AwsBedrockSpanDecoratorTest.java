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

public class AwsBedrockSpanDecoratorTest {

    @Test
    public void testPre() {
        String operation = "invokeModel";
        String modelContentType = "application/json";
        String stopReason = "end_turn";
        Integer tokenCount = 128;

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws-bedrock:claude");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(AwsBedrockSpanDecorator.OPERATION, String.class)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsBedrockSpanDecorator.MODEL_CONTENT_TYPE, String.class)).thenReturn(modelContentType);
        Mockito.when(message.getHeader(AwsBedrockSpanDecorator.CONVERSE_STOP_REASON, String.class)).thenReturn(stopReason);
        Mockito.when(message.getHeader(AwsBedrockSpanDecorator.STREAMING_TOKEN_COUNT, Integer.class)).thenReturn(tokenCount);

        AbstractSpanDecorator decorator = new AwsBedrockSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(operation, span.tags().get(AwsBedrockSpanDecorator.BEDROCK_OPERATION));
        assertEquals(modelContentType, span.tags().get(AwsBedrockSpanDecorator.BEDROCK_MODEL_CONTENT_TYPE));
        assertEquals(stopReason, span.tags().get(AwsBedrockSpanDecorator.BEDROCK_STOP_REASON));
        assertEquals(tokenCount.toString(), span.tags().get(AwsBedrockSpanDecorator.BEDROCK_TOKEN_COUNT));
    }

}
