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
package org.apache.camel.component.pulsar;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.camel.Processor;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategy;
import org.apache.camel.component.pulsar.utils.consumers.ConsumerCreationStrategyFactory;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarConsumer extends DefaultConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarConsumer.class);

    private final PulsarEndpoint pulsarEndpoint;
    private final ConcurrentLinkedQueue<Consumer<byte[]>> pulsarConsumers;
    private final ConsumerCreationStrategyFactory consumerCreationStrategyFactory;

    private PulsarConsumer(PulsarEndpoint pulsarEndpoint, Processor processor) {
        super(pulsarEndpoint, processor);
        this.pulsarEndpoint = pulsarEndpoint;
        this.pulsarConsumers = new ConcurrentLinkedQueue<>();
        this.consumerCreationStrategyFactory = new ConsumerCreationStrategyFactory(this);
    }

    public static PulsarConsumer create(PulsarEndpoint pulsarEndpoint, Processor processor) {
        if(pulsarEndpoint == null || processor == null) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("Pulsar endpoint nor processor must be supplied");
            LOGGER.error("An exception occurred when creating PulsarConsumer :: {}", illegalArgumentException);
            throw illegalArgumentException;
        }
        return new PulsarConsumer(pulsarEndpoint, processor);
    }

    @Override
    protected void doStart() throws PulsarClientException {
        stopConsumers(pulsarConsumers);

        pulsarConsumers.addAll(createConsumers(pulsarEndpoint));
    }

    @Override
    protected void doStop() throws PulsarClientException {
        stopConsumers(pulsarConsumers);
    }

    @Override
    protected void doSuspend() throws PulsarClientException {
        stopConsumers(pulsarConsumers);
    }

    @Override
    protected void doResume() throws PulsarClientException {
        stopConsumers(pulsarConsumers);
        createConsumers(pulsarEndpoint);
    }

    void stopConsumers(final Queue<Consumer<byte[]>> consumers) throws PulsarClientException {
        while (!consumers.isEmpty()) {
            Consumer<byte[]> consumer = pulsarConsumers.poll();
            if(null != consumer) {
                consumer.unsubscribe();
                consumer.close();
            }
        }
    }

    Collection<Consumer<byte[]>> createConsumers(final PulsarEndpoint pulsarEndpoint) {
        ConsumerCreationStrategy strategy = consumerCreationStrategyFactory.getStrategy(pulsarEndpoint.getConfiguration().getSubscriptionType());

        return strategy.create(pulsarEndpoint);
    }
}