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
package org.apache.camel.component.pulsar;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.camel.Processor;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategy;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategyFactory;
import org.apache.camel.support.DefaultConsumer;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClientException;

import static org.apache.camel.component.pulsar.utils.PulsarUtils.stopConsumers;

public class PulsarConsumer extends DefaultConsumer {

    private final PulsarEndpoint pulsarEndpoint;
    private final ConsumerCreationStrategyFactory consumerCreationStrategyFactory;

    private Queue<Consumer<byte[]>> pulsarConsumers;

    public PulsarConsumer(PulsarEndpoint pulsarEndpoint, Processor processor) {
        super(pulsarEndpoint, processor);
        this.pulsarEndpoint = pulsarEndpoint;
        this.pulsarConsumers = new ConcurrentLinkedQueue<>();
        this.consumerCreationStrategyFactory = ConsumerCreationStrategyFactory.create(this);
    }

    @Override
    protected void doStart() throws Exception {
        pulsarConsumers = stopConsumers(pulsarConsumers);

        Collection<Consumer<byte[]>> consumers = createConsumers(pulsarEndpoint, consumerCreationStrategyFactory);

        pulsarConsumers.addAll(consumers);
    }

    @Override
    protected void doStop() throws PulsarClientException {
        pulsarConsumers = stopConsumers(pulsarConsumers);
    }

    @Override
    protected void doSuspend() throws PulsarClientException {
        pulsarConsumers = stopConsumers(pulsarConsumers);
    }

    @Override
    protected void doResume() throws Exception {
        pulsarConsumers = stopConsumers(pulsarConsumers);

        Collection<Consumer<byte[]>> consumers = createConsumers(pulsarEndpoint, consumerCreationStrategyFactory);

        pulsarConsumers.addAll(consumers);
    }

    private Collection<Consumer<byte[]>> createConsumers(final PulsarEndpoint endpoint, final ConsumerCreationStrategyFactory factory) throws Exception {

        ConsumerCreationStrategy strategy = factory.getStrategy(endpoint.getPulsarConfiguration().getSubscriptionType());

        return strategy.create(endpoint);
    }

}
