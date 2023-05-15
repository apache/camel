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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarProducer.class);

    private final Object mutex = new Object();
    private final PulsarEndpoint pulsarEndpoint;
    private volatile Producer<byte[]> producer;

    public PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);
        this.pulsarEndpoint = pulsarEndpoint;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            final Message message = exchange.getIn();
            byte[] body = serialize(exchange, message.getBody());
            TypedMessageBuilder<byte[]> messageBuilder = producer.newMessage();
            messageBuilder.value(body);

            String key = exchange.getIn().getHeader(PulsarMessageHeaders.KEY_OUT, String.class);
            if (ObjectHelper.isNotEmpty(key)) {
                messageBuilder.key(key);
            }

            Map<String, String> properties
                    = CastUtils.cast(exchange.getIn().getHeader(PulsarMessageHeaders.PROPERTIES_OUT, Map.class));
            if (ObjectHelper.isNotEmpty(properties)) {
                messageBuilder.properties(properties);
            }

            Long eventTime = exchange.getIn().getHeader(PulsarMessageHeaders.EVENT_TIME_OUT, Long.class);
            if (eventTime != null) {
                messageBuilder.eventTime(eventTime);
            }

            Long deliverAt = exchange.getIn().getHeader(PulsarMessageHeaders.DELIVER_AT_OUT, Long.class);
            if (deliverAt != null) {
                messageBuilder.deliverAt(deliverAt);
            }

            messageBuilder.sendAsync()
                    .thenAccept(r -> exchange.getIn().setBody(r))
                    .whenComplete(
                            (r, e) -> {
                                try {
                                    if (e != null) {
                                        exchange.setException(new CamelExchangeException(
                                                "An error occurred while sending a message to pulsar", exchange, e));
                                    }
                                } finally {
                                    callback.done(false);
                                }
                            });
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    /**
     * Serialize the given content using the appropriate converter if any, otherwise it relies on
     * {@link PulsarMessageUtils#serialize(Object)}.
     *
     * @param  exchange    the exchange used as context for the serialization process.
     * @param  content     the content to serialize.
     * @return             the serialized counterpart of the given content
     * @throws IOException if an error occurs while serializing the content.
     */
    private static byte[] serialize(Exchange exchange, Object content) throws IOException {
        byte[] result;
        try {
            result = exchange.getContext().getTypeConverter()
                    .mandatoryConvertTo(byte[].class, exchange, content);
        } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
            // fallback to try to serialize the data
            result = PulsarMessageUtils.serialize(content);
        }
        return result;
    }

    private void createProducer() throws PulsarClientException {
        synchronized (mutex) {
            if (producer == null) {
                final String topicUri = pulsarEndpoint.getUri();
                PulsarConfiguration configuration = pulsarEndpoint.getPulsarConfiguration();
                String producerName = configuration.getProducerName();
                final ProducerBuilder<byte[]> producerBuilder = pulsarEndpoint.getPulsarClient().newProducer().topic(topicUri)
                        .sendTimeout(configuration.getSendTimeoutMs(), TimeUnit.MILLISECONDS)
                        .blockIfQueueFull(configuration.isBlockIfQueueFull())
                        .maxPendingMessages(configuration.getMaxPendingMessages())
                        .maxPendingMessagesAcrossPartitions(configuration.getMaxPendingMessagesAcrossPartitions())
                        .batchingMaxPublishDelay(configuration.getBatchingMaxPublishDelayMicros(), TimeUnit.MICROSECONDS)
                        .batchingMaxMessages(configuration.getMaxPendingMessages())
                        .enableBatching(configuration.isBatchingEnabled()).batcherBuilder(configuration.getBatcherBuilder())
                        .initialSequenceId(configuration.getInitialSequenceId())
                        .compressionType(configuration.getCompressionType())
                        .enableChunking(configuration.isChunkingEnabled());
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
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Starting the pulsar producer: {}", this);
        if (producer == null) {
            createProducer();
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping the pulsar producer: {}", this);
        if (producer != null) {
            producer.close();
            producer = null;
        }
    }
}
