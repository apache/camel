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
package org.apache.camel.component.kafka.consumer.errorhandler;

import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.kafka.KafkaFetchRecords;
import org.apache.camel.component.kafka.PollExceptionStrategy;
import org.apache.camel.component.kafka.PollExceptionStrategyFactory;
import org.apache.camel.component.kafka.PollOnError;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KafkaErrorStrategiesTest {

    private final Consumer<byte[], byte[]> consumer = mock(Consumer.class);

    private KafkaEndpoint endpointWith(KafkaComponent component, PollOnError pollOnError) {
        KafkaConfiguration configuration = mock(KafkaConfiguration.class);
        when(configuration.getPollOnError()).thenReturn(pollOnError);

        KafkaEndpoint endpoint = mock(KafkaEndpoint.class);
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        return endpoint;
    }

    @Test
    void defaultsToTheBuiltInStrategyForThePollOnErrorConfiguration() {
        KafkaComponent component = mock(KafkaComponent.class);
        when(component.getPollExceptionStrategyFactory()).thenReturn(null);
        when(component.getPollExceptionStrategy()).thenReturn(null);
        KafkaEndpoint endpoint = endpointWith(component, PollOnError.RECONNECT);

        PollExceptionStrategy strategy = KafkaErrorStrategies.strategies(
                mock(KafkaFetchRecords.class), endpoint, consumer);

        assertInstanceOf(ReconnectErrorStrategy.class, strategy);
    }

    @Test
    void honoursTheLegacyComponentStrategyWhenNoFactoryIsSet() {
        PollExceptionStrategy legacy = mock(PollExceptionStrategy.class);
        KafkaComponent component = mock(KafkaComponent.class);
        when(component.getPollExceptionStrategyFactory()).thenReturn(null);
        when(component.getPollExceptionStrategy()).thenReturn(legacy);
        KafkaEndpoint endpoint = endpointWith(component, PollOnError.RECONNECT);

        PollExceptionStrategy strategy = KafkaErrorStrategies.strategies(
                mock(KafkaFetchRecords.class), endpoint, consumer);

        assertSame(legacy, strategy);
    }

    @Test
    void factoryTakesPrecedenceOverTheLegacyComponentStrategy() {
        PollExceptionStrategy legacy = mock(PollExceptionStrategy.class);
        PollExceptionStrategy fromFactory = mock(PollExceptionStrategy.class);
        PollExceptionStrategyFactory factory = mock(PollExceptionStrategyFactory.class);
        when(factory.create(any(), any(), any())).thenReturn(fromFactory);

        KafkaComponent component = mock(KafkaComponent.class);
        when(component.getPollExceptionStrategyFactory()).thenReturn(factory);
        when(component.getPollExceptionStrategy()).thenReturn(legacy);
        KafkaEndpoint endpoint = endpointWith(component, PollOnError.RECONNECT);
        KafkaFetchRecords fetcher = mock(KafkaFetchRecords.class);

        PollExceptionStrategy strategy = KafkaErrorStrategies.strategies(fetcher, endpoint, consumer);

        assertSame(fromFactory, strategy);
        verify(factory).create(fetcher, endpoint, consumer);
    }

    @Test
    void factoryIsInvokedPerConsumerCreationSoEachFetchThreadGetsItsOwnStrategy() {
        // the factory creates a new strategy each time a fetch thread creates its consumer (on start and on every
        // reconnect), which is what makes the fetcher-driving built-in strategies work correctly when consumersCount > 1
        PollExceptionStrategyFactory factory
                = (recordFetcher, endpoint, consumer) -> new ReconnectErrorStrategy(recordFetcher);

        KafkaComponent component = mock(KafkaComponent.class);
        when(component.getPollExceptionStrategyFactory()).thenReturn(factory);
        KafkaEndpoint endpoint = endpointWith(component, PollOnError.RECONNECT);

        PollExceptionStrategy first = KafkaErrorStrategies.strategies(
                mock(KafkaFetchRecords.class), endpoint, consumer);
        PollExceptionStrategy second = KafkaErrorStrategies.strategies(
                mock(KafkaFetchRecords.class), endpoint, consumer);

        assertNotSame(first, second);
    }
}
