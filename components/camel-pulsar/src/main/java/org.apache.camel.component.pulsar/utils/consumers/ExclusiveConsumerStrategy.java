/**
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
import java.util.Collections;
import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.camel.component.pulsar.utils.retry.PulsarClientRetryPolicy;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExclusiveConsumerStrategy implements ConsumerCreationStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExclusiveConsumerStrategy.class);

    private final PulsarConsumer pulsarConsumer;
    private final PulsarClientRetryPolicy retryPolicy;

    ExclusiveConsumerStrategy(PulsarConsumer pulsarConsumer, PulsarClientRetryPolicy retryPolicy) {
        this.pulsarConsumer = pulsarConsumer;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public Collection<Consumer<byte[]>> create(final PulsarEndpoint pulsarEndpoint) {
        String consumerName = pulsarEndpoint.getConfiguration().getConsumerName();

        ConsumerBuilder<byte[]> builder = CommonCreationStrategyUtils.create(consumerName, pulsarEndpoint, pulsarConsumer);

        try {
            return Collections.singletonList(builder.subscriptionType(SubscriptionType.Exclusive).subscribe());
        } catch (PulsarClientException exception) {
            LOGGER.error("An error occurred when creating the consumer {}", exception);
            // TODO what is the purpose of the retry policy?
            // I'm assuming that when it is implemented, the return emptyList will be removed?
            retryPolicy.retry();
            return Collections.emptyList();
        }
    }
}
