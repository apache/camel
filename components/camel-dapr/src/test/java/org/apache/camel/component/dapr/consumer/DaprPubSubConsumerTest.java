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
package org.apache.camel.component.dapr.consumer;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import io.dapr.client.DaprClientBuilder;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.Subscription;
import io.dapr.client.SubscriptionListener;
import io.dapr.client.domain.CloudEvent;
import io.dapr.utils.TypeRef;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.component.dapr.DaprConfiguration;
import org.apache.camel.component.dapr.DaprConstants;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DaprPubSubConsumerTest extends CamelTestSupport {

    private final CamelContext context = mock(CamelContext.class);
    private final ExtendedCamelContext ecc = mock(ExtendedCamelContext.class);
    private final ExchangeFactory ef = mock(ExchangeFactory.class);
    private final AsyncProcessor processor = mock(AsyncProcessor.class);
    private final DaprEndpoint endpoint = mock(DaprEndpoint.class);
    private final DaprConfiguration configuration = mock(DaprConfiguration.class);
    private final DaprPreviewClient mockClient = mock(DaprPreviewClient.class);
    private final Subscription mockSubscription = mock(Subscription.class);

    private final ArgumentCaptor<SubscriptionListener<byte[]>> listenerCaptor
            = ArgumentCaptor.forClass(SubscriptionListener.class);
    private final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
    private final ArgumentCaptor<AsyncCallback> callbackCaptor = ArgumentCaptor.forClass(AsyncCallback.class);

    private DaprPubSubConsumer consumer;

    @BeforeEach
    void beforeEach() throws Exception {
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(ef.create(any(), anyBoolean()))
                .thenAnswer(inv -> DefaultExchange.newFromEndpoint(inv.getArgument(0)));
        when(endpoint.getCamelContext()).thenReturn(context);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(configuration.getPubSubName()).thenReturn("testPubSub");
        when(configuration.getTopic()).thenReturn("testTopic");
        when(configuration.getPreviewClient()).thenReturn(mockClient);

        DaprClientBuilder builder = mock(DaprClientBuilder.class);
        when(builder.buildPreviewClient()).thenReturn(mockClient);

        doReturn(mockSubscription).when(mockClient).subscribeToEvents(
                anyString(),
                anyString(),
                listenerCaptor.capture(),
                any(TypeRef.class));

        consumer = new DaprPubSubConsumer(endpoint, processor);
    }

    @Test
    void testConsumer() throws Exception {
        consumer.doStart();

        verify(mockClient).subscribeToEvents(anyString(), anyString(), any(), any(TypeRef.class));

        String payload = "testBody";
        String pubSubName = "myPubSub";
        String topic = "myTopic";
        String id = "myId";
        String ver = "myVer";
        String contentType = "myContentType";
        OffsetDateTime time = OffsetDateTime.now();
        String traceParent = "myTrace";
        String traceState = "myState";

        CloudEvent<byte[]> cloudEvent = mock(CloudEvent.class);
        when(cloudEvent.getData()).thenReturn(payload.getBytes());
        when(cloudEvent.getPubsubName()).thenReturn(pubSubName);
        when(cloudEvent.getTopic()).thenReturn(topic);
        when(cloudEvent.getId()).thenReturn(id);
        when(cloudEvent.getSpecversion()).thenReturn(ver);
        when(cloudEvent.getBinaryData()).thenReturn(payload.getBytes());
        when(cloudEvent.getDatacontenttype()).thenReturn(contentType);
        when(cloudEvent.getTime()).thenReturn(time);
        when(cloudEvent.getTraceParent()).thenReturn(traceParent);
        when(cloudEvent.getTraceState()).thenReturn(traceState);

        listenerCaptor.getValue().onEvent(cloudEvent).block();

        verify(processor).process(exchangeCaptor.capture(), callbackCaptor.capture());

        Exchange exchange = exchangeCaptor.getValue();
        assertNotNull(exchange);

        String body = new String(exchange.getIn().getBody(byte[].class), StandardCharsets.UTF_8);
        assertEquals(payload, body);
        assertEquals(pubSubName, exchange.getIn().getHeader(DaprConstants.PUBSUB_NAME));
        assertEquals(topic, exchange.getIn().getHeader(DaprConstants.TOPIC));
        assertEquals(id, exchange.getIn().getHeader(DaprConstants.ID));
        assertEquals(ver, exchange.getIn().getHeader(DaprConstants.SPECIFIC_VERSION));
        assertArrayEquals(payload.getBytes(), (byte[]) exchange.getIn().getHeader(DaprConstants.BINARY_DATA));
        assertEquals(contentType, exchange.getIn().getHeader(DaprConstants.DATA_CONTENT_TYPE));
        assertEquals(time, exchange.getIn().getHeader(DaprConstants.TIME));
        assertEquals(traceParent, exchange.getIn().getHeader(DaprConstants.TRACE_PARENT));
        assertEquals(traceState, exchange.getIn().getHeader(DaprConstants.TRACE_STATE));

        consumer.doStop();
        verify(mockSubscription).close();
        verify(mockClient).close();
    }
}
