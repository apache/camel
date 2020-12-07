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

import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Sent and receive messages to/from an Apache Kafka broker using vert.x Kafka client
 */
@UriEndpoint(firstVersion = "3.7.0", scheme = "vertx-kafka", title = "Vert.x Kafka", syntax = "vertx-kafka:topic",
             category = { Category.MESSAGING })
public class VertxKafkaEndpoint extends DefaultEndpoint {

    @UriParam
    private VertxKafkaConfiguration configuration = new VertxKafkaConfiguration();
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy;

    public VertxKafkaEndpoint() {
    }

    public VertxKafkaEndpoint(final String uri, final Component component, final VertxKafkaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new VertxKafkaProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final VertxKafkaConsumer vertxKafkaConsumer = new VertxKafkaConsumer(this, processor);
        configureConsumer(vertxKafkaConsumer);

        return vertxKafkaConsumer;
    }

    @Override
    public VertxKafkaComponent getComponent() {
        return (VertxKafkaComponent) super.getComponent();
    }

    public Vertx getVertx() {
        return getComponent().getVertx();
    }

    public Exchange createExchange(final KafkaConsumerRecord<Object, Object> record) {
        final Exchange exchange = createExchange();
        final Message message = exchange.getIn();

        // set body as byte[] and let camel typeConverters do the job to convert
        message.setBody(record.record().value());

        // set headers
        message.setHeader(VertxKafkaConstants.PARTITION_ID, record.partition());
        message.setHeader(VertxKafkaConstants.TOPIC, record.topic());
        message.setHeader(VertxKafkaConstants.OFFSET, record.offset());
        message.setHeader(VertxKafkaConstants.HEADERS, record.headers());
        message.setHeader(VertxKafkaConstants.TIMESTAMP, record.timestamp());
        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, record.key());

        return exchange;
    }

    /**
     * The component configurations
     */
    public VertxKafkaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(VertxKafkaConfiguration configuration) {
        this.configuration = configuration;
    }
}
