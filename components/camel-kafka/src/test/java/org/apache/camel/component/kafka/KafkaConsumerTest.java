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
package org.apache.camel.component.kafka;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KafkaConsumerTest {

    private final KafkaConfiguration configuration = mock(KafkaConfiguration.class);
    private final KafkaClientFactory clientFactory = mock(KafkaClientFactory.class);
    private final KafkaComponent component = mock(KafkaComponent.class);
    private final KafkaEndpoint endpoint = mock(KafkaEndpoint.class);
    private final Processor processor = mock(Processor.class);
    private final CamelContext context = mock(CamelContext.class);
    private final ExtendedCamelContext ecc = mock(ExtendedCamelContext.class);
    private final ExchangeFactory ef = mock(ExchangeFactory.class);

    @Test
    public void consumerRequiresBootstrapServers() {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getConfiguration().getGroupId()).thenReturn("groupOne");
        when(endpoint.getKafkaClientFactory()).thenReturn(clientFactory);
        when(component.getKafkaClientFactory()).thenReturn(clientFactory);
        when(clientFactory.getBrokers(any())).thenThrow(new IllegalArgumentException());
        final KafkaConsumer kafkaConsumer = new KafkaConsumer(endpoint, processor);

        assertThrows(IllegalArgumentException.class, () -> kafkaConsumer.getProps());
    }

    @Test
    public void consumerOnlyRequiresBootstrapServers() {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getConfiguration().getBrokers()).thenReturn("localhost:2181");

        assertDoesNotThrow(() -> new KafkaConsumer(endpoint, processor));
    }
}
