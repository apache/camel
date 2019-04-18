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
import java.util.Collections;

import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.SubscriptionType;

public class ExclusiveConsumerStrategy implements ConsumerCreationStrategy {

    private final PulsarConsumer pulsarConsumer;

    ExclusiveConsumerStrategy(PulsarConsumer pulsarConsumer) {
        this.pulsarConsumer = pulsarConsumer;
    }

    @Override
    public Collection<Consumer<byte[]>> create(final PulsarEndpoint pulsarEndpoint) throws Exception {
        String consumerName = pulsarEndpoint.getPulsarConfiguration().getConsumerName();

        ConsumerBuilder<byte[]> builder = CommonCreationStrategyImpl.create(consumerName, pulsarEndpoint, pulsarConsumer);
        return Collections.singletonList(builder.subscriptionType(SubscriptionType.Exclusive).subscribe());
    }
}
