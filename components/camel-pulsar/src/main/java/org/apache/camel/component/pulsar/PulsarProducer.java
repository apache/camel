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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.camel.impl.DefaultProducer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarProducer extends DefaultProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarProducer.class);

    private final PulsarEndpoint pulsarEndpoint;
    private Producer<byte[]> producer;

    public PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);
        this.pulsarEndpoint = pulsarEndpoint;
    }

    public static PulsarProducer create(final PulsarEndpoint pulsarEndpoint) {
        return new PulsarProducer(pulsarEndpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Message message = exchange.getIn();
        if(producer == null) {
            createProducer();
        }
        byte[] body;
        try {
            body = exchange.getContext().getTypeConverter()
                .mandatoryConvertTo(byte[].class, exchange, message.getBody());
        } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
            LOGGER.info("An error occurred while serializing to byte array, using fall back strategy", exception);
            body = PulsarMessageUtils.serialize(message.getBody());
        }
        producer.send(body);
    }

    private synchronized void createProducer() throws org.apache.pulsar.client.api.PulsarClientException {
        if (producer == null) {
            final String topic = pulsarEndpoint.getTopic();
            String producerName;
            if (pulsarEndpoint.getConfiguration().getProducerName() == null) {
                producerName = topic + "-" + Thread.currentThread().getId();
            } else {
                producerName = pulsarEndpoint.getConfiguration().getProducerName();
            }
            final ProducerBuilder<byte[]> producerBuilder = pulsarEndpoint
                    .getPulsarClient()
                    .newProducer()
                    .producerName(producerName)
                    .topic(topic);
            producer = producerBuilder.create();
        }
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Stopping producer: {}", this);
        if (producer != null) {
            producer.close();
        }
    }
}
