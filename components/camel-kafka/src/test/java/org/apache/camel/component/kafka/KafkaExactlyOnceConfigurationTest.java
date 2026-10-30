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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualAsyncCommitFactory;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommitFactory;
import org.apache.camel.component.kafka.consumer.KafkaManualCommitFactory;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaExactlyOnceConfigurationTest {

    private KafkaConfiguration configuration;
    private KafkaEndpoint endpoint;
    private CamelContext context;
    private ExtendedCamelContext extendedContext;
    private ExchangeFactory exchangeFactory;

    @BeforeEach
    void setUp() {
        configuration = new KafkaConfiguration();
        configuration.setExactlyOnce(true);
        configuration.setGroupId("orders-group");
        configuration.setAllowManualCommit(true);
        configuration.setAutoCommitEnable(false);
        configuration.setBreakOnFirstError(true);
        endpoint = mock(KafkaEndpoint.class);
        context = mock(CamelContext.class);
        extendedContext = mock(ExtendedCamelContext.class);
        exchangeFactory = mock(ExchangeFactory.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getComponent()).thenReturn(mock(KafkaComponent.class));
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(extendedContext);
        when(extendedContext.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);
    }

    @Test
    void acceptsValidSourceConfigurationWithDefaultFactory() {
        when(endpoint.getKafkaManualCommitFactory()).thenReturn(null);
        assertDoesNotThrow(() -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    void acceptsBuiltInSynchronousManualCommitFactory() {
        when(endpoint.getKafkaManualCommitFactory()).thenReturn(new DefaultKafkaManualCommitFactory());
        assertDoesNotThrow(() -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    void acceptsBuiltInAsynchronousManualCommitFactory() {
        when(endpoint.getKafkaManualCommitFactory()).thenReturn(new DefaultKafkaManualAsyncCommitFactory());
        assertDoesNotThrow(() -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    void rejectsAutoCommit() {
        configuration.setAutoCommitEnable(true);
        assertThrows(IllegalArgumentException.class, () -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    void rejectsAutoCommitEnabledThroughAdditionalProperties() {
        configuration.setAdditionalProperties(Map.<String, Object> of(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true));
        assertThrows(IllegalArgumentException.class, () -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    void rejectsDisabledManualCommit() {
        configuration.setAllowManualCommit(false);
        assertThrows(IllegalArgumentException.class, () -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    void rejectsDisabledBreakOnFirstError() {
        configuration.setBreakOnFirstError(false);
        assertThrows(IllegalArgumentException.class, () -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    void rejectsMissingOrBlankGroupId() {
        configuration.setGroupId(null);
        assertThrows(IllegalArgumentException.class, () -> consumer().validateExactlyOnceConfiguration());
        configuration.setGroupId("  ");
        assertThrows(IllegalArgumentException.class, () -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsOffsetRepository() {
        configuration.setOffsetRepository(mock(StateRepository.class));
        assertThrows(IllegalArgumentException.class, () -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    void rejectsCustomManualCommitFactory() {
        KafkaManualCommitFactory customFactory = (camel, record, manager) -> null;
        when(endpoint.getKafkaManualCommitFactory()).thenReturn(customFactory);
        assertThrows(IllegalArgumentException.class, () -> consumer().validateExactlyOnceConfiguration());
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotValidateNonExactlyOnceConsumers() {
        configuration.setExactlyOnce(false);
        configuration.setAutoCommitEnable(true);
        configuration.setAllowManualCommit(false);
        configuration.setBreakOnFirstError(false);
        configuration.setOffsetRepository(mock(StateRepository.class));
        assertDoesNotThrow(() -> consumer().validateExactlyOnceConfiguration());
    }

    private KafkaConsumer consumer() {
        return new KafkaConsumer(endpoint, mock(Processor.class));
    }
}
