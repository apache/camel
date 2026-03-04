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

import java.util.HashMap;
import java.util.Map;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dapr.client.domain.SubscribeConfigurationRequest;
import io.dapr.client.domain.SubscribeConfigurationResponse;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DaprConfigurationConsumerTest extends CamelTestSupport {

    private final CamelContext context = mock(CamelContext.class);
    private final ExtendedCamelContext ecc = mock(ExtendedCamelContext.class);
    private final ExchangeFactory ef = mock(ExchangeFactory.class);
    private final AsyncProcessor processor = mock(AsyncProcessor.class);
    private final DaprEndpoint endpoint = mock(DaprEndpoint.class);
    private final DaprConfiguration configuration = mock(DaprConfiguration.class);
    private final DaprClient mockClient = mock(DaprClient.class);

    private final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
    private final ArgumentCaptor<AsyncCallback> callbackCaptor = ArgumentCaptor.forClass(AsyncCallback.class);

    private DaprConfigurationConsumer consumer;
    private final String configKeys = "myKey1,myKey2";

    @BeforeEach
    void beforeEach() throws Exception {
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(ef.create(any(), anyBoolean()))
                .thenAnswer(inv -> DefaultExchange.newFromEndpoint(inv.getArgument(0)));
        when(endpoint.getCamelContext()).thenReturn(context);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getClient()).thenReturn(mockClient);
        when(configuration.getConfigStore()).thenReturn("myStore");
        when(configuration.getConfigKeys()).thenReturn(configKeys);

        consumer = new DaprConfigurationConsumer(endpoint, processor);
    }

    @Test
    void testConfigurationConsumer() throws Exception {
        final FluxSink<SubscribeConfigurationResponse>[] sinkHolder = new FluxSink[1];
        Flux<SubscribeConfigurationResponse> flux = Flux.create(sink -> sinkHolder[0] = sink);
        when(mockClient.subscribeConfiguration(any())).thenReturn(flux);

        consumer.doStart();

        verify(mockClient).subscribeConfiguration(any(SubscribeConfigurationRequest.class));

        Map<String, ConfigurationItem> items = new HashMap<>();
        items.put("myKey1", new ConfigurationItem("myKey1", "myVal1", "myVer1"));
        items.put("myKey2", new ConfigurationItem("myKey2", "myVal2", "myVer2"));
        SubscribeConfigurationResponse response = new SubscribeConfigurationResponse("mySubId", items);
        Map<String, String> mockBody = Map.of("myKey1", "myVal1", "myKey2", "myVal2");

        sinkHolder[0].next(response);

        verify(processor).process(exchangeCaptor.capture(), callbackCaptor.capture());

        Exchange exchange = exchangeCaptor.getValue();
        assertNotNull(exchange);

        assertEquals("mySubId", exchange.getIn().getHeader(DaprConstants.SUBSCRIPTION_ID));
        assertEquals(items, exchange.getIn().getHeader(DaprConstants.RAW_CONFIG_RESPONSE));
        assertEquals(mockBody, exchange.getIn().getBody());

        consumer.doStop();
        verify(mockClient).unsubscribeConfiguration(any(), any());
        verify(mockClient).close();
    }
}
