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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.impl.DefaultProducer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarProducer extends DefaultProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarProducer.class);

    private Producer<byte[]> producer;
    private final PulsarEndpoint pulsarEndpoint;

    private PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);
        this.pulsarEndpoint = pulsarEndpoint;
    }

    public static PulsarProducer create(final PulsarEndpoint pulsarEndpoint) {
        return new PulsarProducer(pulsarEndpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final PulsarEndpointConfiguration configuration = pulsarEndpoint.getConfiguration();
        final Message message = exchange.getIn();

        if (producer == null) {
            producer = createProducer(configuration);
        }

        try {
            byte[] body = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, message);

            producer.sendAsync(body);
        } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
            LOGGER.error("", exception);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        producer = createProducer(pulsarEndpoint.getConfiguration());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (producer != null && !producer.isConnected()) {
            producer.close();
        }
    }

    private Producer<byte[]> createProducer(final PulsarEndpointConfiguration configuration) throws PulsarClientException {
        return pulsarEndpoint.getPulsarClient()
            .newProducer()
            .topic(pulsarEndpoint.getTopic())
            .producerName(configuration.getProducerName())
            .create();
    }
}
