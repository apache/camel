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

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.pulsar.PulsarConfiguration;
import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.camel.component.pulsar.PulsarMessageListener;
import org.apache.camel.util.ObjectHelper;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.DeadLetterPolicy.DeadLetterPolicyBuilder;
import org.apache.pulsar.client.api.KeySharedPolicy;

public final class CommonCreationStrategyImpl {

    private CommonCreationStrategyImpl() {
    }

    protected static ConsumerBuilder<byte[]> getBuilder(
            final String name, final PulsarEndpoint pulsarEndpoint, final PulsarConsumer pulsarConsumer) {
        final PulsarConfiguration endpointConfiguration = pulsarEndpoint.getPulsarConfiguration();

        ConsumerBuilder<byte[]> builder = pulsarEndpoint.getPulsarClient().newConsumer();
        if (endpointConfiguration.getKeySharedPolicy() != null) {
            if ("AUTO_SPLIT".equalsIgnoreCase(endpointConfiguration.getKeySharedPolicy())) {
                builder.keySharedPolicy(KeySharedPolicy.autoSplitHashRange());
            } else if ("STICKY".equalsIgnoreCase(endpointConfiguration.getKeySharedPolicy())) {
                builder.keySharedPolicy(KeySharedPolicy.stickyHashRange());
            } else {
                throw new IllegalArgumentException(
                        "Unsupported KeySharedPolicy: " + endpointConfiguration.getKeySharedPolicy());
            }
        }
        if (endpointConfiguration.isTopicsPattern()) {
            builder.topicsPattern(pulsarEndpoint.getUri());
            if (endpointConfiguration.getSubscriptionTopicsMode() != null) {
                builder.subscriptionTopicsMode(endpointConfiguration.getSubscriptionTopicsMode());
            }
        } else {
            builder.topic(pulsarEndpoint.getUri());
        }
        builder.subscriptionName(endpointConfiguration.getSubscriptionName())
                .receiverQueueSize(endpointConfiguration.getConsumerQueueSize()).consumerName(name)
                .ackTimeout(endpointConfiguration.getAckTimeoutMillis(), TimeUnit.MILLISECONDS)
                .subscriptionInitialPosition(
                        endpointConfiguration.getSubscriptionInitialPosition().toPulsarSubscriptionInitialPosition())
                .acknowledgmentGroupTime(endpointConfiguration.getAckGroupTimeMillis(), TimeUnit.MILLISECONDS)
                .negativeAckRedeliveryDelay(endpointConfiguration.getNegativeAckRedeliveryDelayMicros(), TimeUnit.MICROSECONDS)
                .readCompacted(endpointConfiguration.isReadCompacted());

        if (endpointConfiguration.isMessageListener()) {
            builder.messageListener(new PulsarMessageListener(pulsarEndpoint, pulsarConsumer));
        }

        if (endpointConfiguration.isEnableRetry()) {
            // retry mode
            builder.enableRetry(true);
            DeadLetterPolicyBuilder policy = DeadLetterPolicy.builder()
                    .maxRedeliverCount(endpointConfiguration.getMaxRedeliverCount());
            if (endpointConfiguration.getRetryLetterTopic() != null) {
                policy.retryLetterTopic(endpointConfiguration.getRetryLetterTopic());
            }
            builder.deadLetterPolicy(policy.build());
        } else if (endpointConfiguration.getMaxRedeliverCount() != null) {
            // or potentially dead-letter-topic mode
            DeadLetterPolicyBuilder policy = DeadLetterPolicy.builder()
                    .maxRedeliverCount(endpointConfiguration.getMaxRedeliverCount());
            if (endpointConfiguration.getDeadLetterTopic() != null) {
                policy.deadLetterTopic(endpointConfiguration.getDeadLetterTopic());
            }

            builder.deadLetterPolicy(policy.build());
        }

        if (ObjectHelper.isNotEmpty(endpointConfiguration.getAckTimeoutRedeliveryBackoff())) {
            builder.ackTimeoutRedeliveryBackoff(endpointConfiguration.getAckTimeoutRedeliveryBackoff());
        }

        if (ObjectHelper.isNotEmpty(endpointConfiguration.getNegativeAckRedeliveryBackoff())) {
            builder.negativeAckRedeliveryBackoff(endpointConfiguration.getNegativeAckRedeliveryBackoff());
        }

        return builder;
    }
}
