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

import org.apache.camel.component.pulsar.PulsarConsumer;

public final class ConsumerCreationStrategyFactory {

    private final PulsarConsumer pulsarConsumer;

    private ConsumerCreationStrategyFactory(PulsarConsumer pulsarConsumer) {
        this.pulsarConsumer = pulsarConsumer;
    }

    public static ConsumerCreationStrategyFactory create(PulsarConsumer pulsarConsumer) {
        validate(pulsarConsumer);
        return new ConsumerCreationStrategyFactory(pulsarConsumer);
    }

    private static void validate(PulsarConsumer pulsarConsumer) {
        if (pulsarConsumer == null) {
            throw new IllegalArgumentException("Pulsar Consumer cannot be null");
        }
    }

    public ConsumerCreationStrategy getStrategy(final SubscriptionType subscriptionType) {
        final SubscriptionType type = subscriptionType == null ? SubscriptionType.EXCLUSIVE : subscriptionType;

        switch (type) {
            case SHARED:
                return new SharedConsumerStrategy(pulsarConsumer);
            case EXCLUSIVE:
                return new ExclusiveConsumerStrategy(pulsarConsumer);
            case FAILOVER:
                return new FailoverConsumerStrategy(pulsarConsumer);
            case KEY_SHARED:
                return new KeySharedConsumerStrategy(pulsarConsumer);
            default:
                return new ExclusiveConsumerStrategy(pulsarConsumer);
        }
    }
}
