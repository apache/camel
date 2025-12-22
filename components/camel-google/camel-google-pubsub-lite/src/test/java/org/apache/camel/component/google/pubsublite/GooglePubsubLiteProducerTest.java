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
package org.apache.camel.component.google.pubsublite;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFutures;
import com.google.cloud.pubsublite.cloudpubsub.Publisher;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class GooglePubsubLiteProducerTest extends CamelTestSupport {

    @Mock
    private GooglePubsubLiteEndpoint mockEndpoint;
    @Mock
    private Exchange mockExchange;
    @Mock
    private Message mockMessage;
    @Mock
    private Publisher mockPublisher;

    @Override
    public void doPreSetup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProcess() throws Exception {
        GooglePubsubLiteProducer producer = new GooglePubsubLiteProducer(mockEndpoint);
        String testPayload = "Test Payload";

        when(mockExchange.getIn()).thenReturn(mockMessage);
        when(mockEndpoint.getProjectId()).thenReturn(123456789012L);
        when(mockEndpoint.getLocation()).thenReturn("europe-west3");
        when(mockEndpoint.getDestinationName()).thenReturn("testDestination");
        when(mockEndpoint.getComponent()).thenReturn(mock(GooglePubsubLiteComponent.class));
        when(mockEndpoint.getComponent().getPublisher(any(), any())).thenReturn(mockPublisher);

        when(mockExchange.getMessage()).thenReturn(mockMessage);
        when(mockMessage.getBody()).thenReturn(testPayload.getBytes());
        when(mockExchange.getMessage().getHeader(GooglePubsubLiteConstants.ATTRIBUTES, Map.class)).thenReturn(null);
        when(mockExchange.getMessage().getHeader(GooglePubsubLiteConstants.ORDERING_KEY, String.class)).thenReturn(null);

        when(mockPublisher.publish(any())).thenReturn(ApiFutures.immediateFuture("messageId"));

        producer.process(mockExchange);

        verify(mockPublisher, times(1)).publish(any());
    }

    @Test
    public void testProcessException() throws ExecutionException {
        GooglePubsubLiteProducer producer = new GooglePubsubLiteProducer(mockEndpoint);
        String testPayload = "Test Payload";

        when(mockEndpoint.getProjectId()).thenReturn(123456789012L);
        when(mockEndpoint.getLocation()).thenReturn("europe-west3");
        when(mockEndpoint.getDestinationName()).thenReturn("testDestination");
        when(mockEndpoint.getComponent()).thenReturn(mock(GooglePubsubLiteComponent.class));

        // Make getPublisher() throw an ExecutionException
        when(mockEndpoint.getComponent().getPublisher(any(), any()))
                .thenThrow(new ExecutionException("Test exception", new Throwable()));

        when(mockExchange.getIn()).thenReturn(mockMessage);
        when(mockMessage.getBody()).thenReturn(testPayload.getBytes());
        when(mockExchange.getIn().getHeader(GooglePubsubLiteConstants.ATTRIBUTES, Map.class)).thenReturn(null);
        when(mockExchange.getIn().getHeader(GooglePubsubLiteConstants.ORDERING_KEY, String.class)).thenReturn(null);

        assertThrows(ExecutionException.class, () -> producer.process(mockExchange));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("google-pubsub-lite:123456789012:europe-west3:test");
            }
        };
    }
}
