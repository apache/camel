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
package org.apache.camel.component.pulsar.utils.consumers;

import java.util.Collection;
import java.util.LinkedList;

import org.apache.camel.component.pulsar.PulsarConfiguration;
import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeySharedConsumerStrategy implements ConsumerCreationStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeySharedConsumerStrategy.class);

    private final PulsarConsumer pulsarConsumer;

    KeySharedConsumerStrategy(PulsarConsumer pulsarConsumer) {
        this.pulsarConsumer = pulsarConsumer;
    }

    @Override
    public Collection<Consumer<byte[]>> create(final PulsarEndpoint pulsarEndpoint) {
        return createMultipleConsumers(pulsarEndpoint);
    }

    private Collection<Consumer<byte[]>> createMultipleConsumers(final PulsarEndpoint pulsarEndpoint) {
        final Collection<Consumer<byte[]>> consumers = new LinkedList<>();
        final PulsarConfiguration configuration = pulsarEndpoint.getPulsarConfiguration();

        for (int i = 0; i < configuration.getNumberOfConsumers(); i++) {
            final String consumerName = configuration.getConsumerNamePrefix() + i;
            try {
                ConsumerBuilder<byte[]> builder = CommonCreationStrategyImpl.create(consumerName, pulsarEndpoint, pulsarConsumer);

                consumers.add(builder.subscriptionType(SubscriptionType.Key_Shared).subscribe());
            } catch (PulsarClientException exception) {
                LOGGER.error("A PulsarClientException occurred when creating Consumer {}, {}", consumerName, exception);
            }
        }
        return consumers;
    }
}
