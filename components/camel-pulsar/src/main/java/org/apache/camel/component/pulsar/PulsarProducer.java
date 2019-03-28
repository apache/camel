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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.camel.impl.DefaultProducer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;

public class PulsarProducer extends DefaultProducer {

    private final PulsarEndpoint pulsarEndpoint;

    public PulsarProducer(PulsarEndpoint pulsarEndpoint) {
        super(pulsarEndpoint);

        this.pulsarEndpoint = pulsarEndpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Message message = exchange.getIn();
        final String topic = pulsarEndpoint.getTopic();
        final String producerName = topic + "-" + Thread.currentThread().getId();

        final Map properties = message.getHeader(PulsarMessageHeaders.PROPERTIES, Map.class);

        final ProducerBuilder<byte[]> producerBuilder = pulsarEndpoint
            .getPulsarClient()
            .newProducer()
            .producerName(producerName)
            .topic(topic);

        if (properties != null && !properties.isEmpty()) {
            producerBuilder.properties(properties);
        }

        final Producer<byte[]> producer = producerBuilder.create();

        try {
            byte[] body = exchange.getContext().getTypeConverter()
                .mandatoryConvertTo(byte[].class, exchange, message.getBody());
            producer.send(body);
        } catch (NoTypeConversionAvailableException | TypeConversionException exception) {
            // fallback to try serialize the data
            byte[] body = PulsarMessageUtils.serialize(message.getBody());
            producer.send(body);
        } finally {
            producer.close();
        }
    }
}
