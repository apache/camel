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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarProducer.class);

    private final PulsarEndpoint pulsarEndpoint;
    private Producer<byte[]> producer;

    public PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);
        this.pulsarEndpoint = pulsarEndpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Message message = exchange.getIn();

        TypedMessageBuilder<byte[]> messageBuilder = producer.newMessage();
        byte[] body;
        try {
            body = exchange.getContext().getTypeConverter()
                    .mandatoryConvertTo(byte[].class, exchange, message.getBody());
        } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
            // fallback to try serialize the data
            body = PulsarMessageUtils.serialize(message.getBody());
        }
        messageBuilder.value(body);

        String key = exchange.getIn().getHeader(PulsarMessageHeaders.KEY_OUT, String.class);
        if (ObjectHelper.isNotEmpty(key)) {
            messageBuilder.key(key);
        }

        Map<String, String> properties = CastUtils.cast(exchange.getIn().getHeader(PulsarMessageHeaders.PROPERTIES_OUT, Map.class));
        if (ObjectHelper.isNotEmpty(properties)) {
            messageBuilder.properties(properties);
        }

        Long eventTime = exchange.getIn().getHeader(PulsarMessageHeaders.EVENT_TIME_OUT, Long.class);
        if (eventTime != null) {
            messageBuilder.eventTime(eventTime);
        }

        messageBuilder.send();
    }

    private synchronized void createProducer() throws org.apache.pulsar.client.api.PulsarClientException {
        if (producer == null) {
            final String topicUri = pulsarEndpoint.getUri();
            PulsarConfiguration configuration = pulsarEndpoint.getPulsarConfiguration();
            String producerName = configuration.getProducerName();
            final ProducerBuilder<byte[]> producerBuilder = pulsarEndpoint.getPulsarClient().newProducer().topic(topicUri)
                .sendTimeout(configuration.getSendTimeoutMs(), TimeUnit.MILLISECONDS).blockIfQueueFull(configuration.isBlockIfQueueFull())
                .maxPendingMessages(configuration.getMaxPendingMessages()).maxPendingMessagesAcrossPartitions(configuration.getMaxPendingMessagesAcrossPartitions())
                .batchingMaxPublishDelay(configuration.getBatchingMaxPublishDelayMicros(), TimeUnit.MICROSECONDS).batchingMaxMessages(configuration.getMaxPendingMessages())
                .enableBatching(configuration.isBatchingEnabled()).batcherBuilder(configuration.getBatcherBuilder())
                .initialSequenceId(configuration.getInitialSequenceId()).compressionType(configuration.getCompressionType());
            if (ObjectHelper.isNotEmpty(configuration.getMessageRouter())) {
                producerBuilder.messageRouter(configuration.getMessageRouter());
            } else {
                producerBuilder.messageRoutingMode(configuration.getMessageRoutingMode());
            }
            if (producerName != null) {
                producerBuilder.producerName(producerName);
            }
            producer = producerBuilder.create();
        }
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Starting producer: {}", this);
        if (producer == null) {
            createProducer();
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping producer: {}", this);
        if (producer != null) {
            producer.close();
            producer = null;
        }
    }
}
