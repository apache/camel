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
package org.apache.camel.component.vertx.kafka;

import io.vertx.kafka.client.producer.KafkaProducer;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.component.vertx.kafka.operations.VertxKafkaProducerOperations;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxKafkaProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaProducer.class);

    private KafkaProducer<Object, Object> kafkaProducer;
    private VertxKafkaProducerOperations producerOperations;

    public VertxKafkaProducer(final VertxKafkaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() {
        String brokers = getEndpoint().getComponent().getVertxKafkaClientFactory().getBootstrapBrokers(getConfiguration());
        if (brokers != null) {
            LOG.debug("Creating KafkaConsumer connecting to BootstrapBrokers: {}", brokers);
        }

        // create kafka client
        kafkaProducer = getEndpoint().getComponent().getVertxKafkaClientFactory()
                .getVertxKafkaProducer(getEndpoint().getVertx(), getConfiguration().createProducerConfiguration());

        // create our operations
        producerOperations = new VertxKafkaProducerOperations(kafkaProducer, getConfiguration());
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            return producerOperations.sendEvents(exchange.getIn(),
                    recordMetadata -> exchange.getMessage().setHeader(VertxKafkaConstants.RECORD_METADATA, recordMetadata),
                    callback);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (kafkaProducer != null) {
            // shutdown the producer
            kafkaProducer.close();
        }

        super.doStop();
    }

    @Override
    public VertxKafkaEndpoint getEndpoint() {
        return (VertxKafkaEndpoint) super.getEndpoint();
    }

    public VertxKafkaConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
