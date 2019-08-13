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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.pulsar.configuration.PulsarConfiguration;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.camel.support.DefaultProducer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;

public class PulsarProducer extends DefaultProducer {

    private final PulsarEndpoint pulsarEndpoint;
    private Producer<byte[]> producer;

    public PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);
        this.pulsarEndpoint = pulsarEndpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Message message = exchange.getIn();
        byte[] body;
        try {
            body = exchange.getContext().getTypeConverter()
                    .mandatoryConvertTo(byte[].class, exchange, message.getBody());
        } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
            // fallback to try serialize the data
            body = PulsarMessageUtils.serialize(message.getBody());
        }
        producer.send(body);
    }

    private synchronized void createProducer() throws org.apache.pulsar.client.api.PulsarClientException {
        if (producer == null) {
            final String topicUri = pulsarEndpoint.getUri();
            PulsarConfiguration configuration = pulsarEndpoint.getPulsarConfiguration();
            String producerName = configuration.getProducerName();
            if (producerName == null) {
                producerName = topicUri + "-" + Thread.currentThread().getId();
            }
            final ProducerBuilder<byte[]> producerBuilder = pulsarEndpoint
                    .getPulsarClient()
                    .newProducer()
                    .producerName(producerName)
                    .topic(topicUri)
                    .sendTimeout(configuration.getSendTimeoutMs(), TimeUnit.MILLISECONDS)
                    .blockIfQueueFull(configuration.isBlockIfQueueFull())
                    .maxPendingMessages(configuration.getMaxPendingMessages())
                    .maxPendingMessagesAcrossPartitions(configuration.getMaxPendingMessagesAcrossPartitions())
                    .batchingMaxPublishDelay(configuration.getBatchingMaxPublishDelayMicros(), TimeUnit.MICROSECONDS)
                    .batchingMaxMessages(configuration.getMaxPendingMessages())
                    .enableBatching(configuration.isBatchingEnabled())
                    .initialSequenceId(configuration.getInitialSequenceId())
                    .compressionType(configuration.getCompressionType());
            producer = producerBuilder.create();
        }
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("Starting producer: {}", this);
        if (producer == null) {
            createProducer();
        }
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Stopping producer: {}", this);
        if (producer != null) {
            producer.close();
            producer = null;
        }
    }
}
