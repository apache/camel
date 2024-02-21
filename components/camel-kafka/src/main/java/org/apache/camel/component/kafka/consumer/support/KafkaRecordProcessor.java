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

package org.apache.camel.component.kafka.consumer.support;

import java.util.stream.StreamSupport;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KafkaRecordProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordProcessor.class);

    protected void setupExchangeMessage(Message message, ConsumerRecord<Object, Object> consumerRecord) {
        message.setHeader(KafkaConstants.PARTITION, consumerRecord.partition());
        message.setHeader(KafkaConstants.TOPIC, consumerRecord.topic());
        message.setHeader(KafkaConstants.OFFSET, consumerRecord.offset());
        message.setHeader(KafkaConstants.HEADERS, consumerRecord.headers());
        message.setHeader(KafkaConstants.TIMESTAMP, consumerRecord.timestamp());
        message.setHeader(Exchange.MESSAGE_TIMESTAMP, consumerRecord.timestamp());

        if (consumerRecord.key() != null) {
            message.setHeader(KafkaConstants.KEY, consumerRecord.key());
        }

        LOG.debug("Setting up the exchange for message from partition {} and offset {}",
                consumerRecord.partition(), consumerRecord.offset());

        message.setBody(consumerRecord.value());
    }

    protected boolean shouldBeFiltered(Header header, Exchange exchange, HeaderFilterStrategy headerFilterStrategy) {
        return !headerFilterStrategy.applyFilterToExternalHeaders(header.key(), header.value(), exchange);
    }

    protected void propagateHeaders(
            KafkaConfiguration configuration, ConsumerRecord<Object, Object> consumerRecord, Exchange exchange) {

        HeaderFilterStrategy headerFilterStrategy = configuration.getHeaderFilterStrategy();
        KafkaHeaderDeserializer headerDeserializer = configuration.getHeaderDeserializer();

        StreamSupport.stream(consumerRecord.headers().spliterator(), false)
                .filter(header -> shouldBeFiltered(header, exchange, headerFilterStrategy))
                .forEach(header -> exchange.getIn().setHeader(header.key(),
                        headerDeserializer.deserialize(header.key(), header.value())));
    }
}
