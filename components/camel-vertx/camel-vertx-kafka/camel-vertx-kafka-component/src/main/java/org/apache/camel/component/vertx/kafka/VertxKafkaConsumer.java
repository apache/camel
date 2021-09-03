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

import java.util.Map;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommit;
import org.apache.camel.component.vertx.kafka.operations.VertxKafkaConsumerOperations;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.SynchronizationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxKafkaConsumer extends DefaultConsumer implements Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaConsumer.class);

    private Synchronization onCompletion;
    private KafkaConsumer<Object, Object> kafkaConsumer;

    public VertxKafkaConsumer(final VertxKafkaEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.onCompletion = new ConsumerOnCompletion();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String brokers = getEndpoint().getComponent().getVertxKafkaClientFactory().getBootstrapBrokers(getConfiguration());
        if (brokers != null) {
            LOG.debug("Creating KafkaConsumer connecting to BootstrapBrokers: {}", brokers);
        }

        // create the consumer client
        kafkaConsumer = getEndpoint().getComponent().getVertxKafkaClientFactory()
                .getVertxKafkaConsumer(getEndpoint().getVertx(), getConfiguration().createConsumerConfiguration());

        // create the consumer operation
        final VertxKafkaConsumerOperations consumerOperations
                = new VertxKafkaConsumerOperations(kafkaConsumer, getConfiguration());

        // process our records
        consumerOperations.receiveEvents(this::onEventListener, this::onErrorListener);
    }

    @Override
    protected void doStop() throws Exception {
        if (kafkaConsumer != null) {
            kafkaConsumer.close();
        }

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (kafkaConsumer != null) {
            kafkaConsumer.pause();
        }
    }

    @Override
    protected void doResume() throws Exception {
        if (kafkaConsumer != null) {
            kafkaConsumer.resume();
        }
    }

    public VertxKafkaConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public VertxKafkaEndpoint getEndpoint() {
        return (VertxKafkaEndpoint) super.getEndpoint();
    }

    private void onEventListener(final KafkaConsumerRecord<Object, Object> record) {
        final Exchange exchange = createExchange(record);
        final Map<String, Buffer> propagatedHeaders
                = new VertxKafkaHeadersPropagation(getConfiguration().getHeaderFilterStrategy())
                        .getPropagatedHeaders(record.headers(), exchange.getIn());

        // set propagated headers on exchange
        propagatedHeaders.forEach((key, value) -> exchange.getIn().setHeader(key, value));

        // add exchange callback
        exchange.adapt(ExtendedExchange.class).addOnCompletion(onCompletion);
        // send message to next processor in the route
        getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
    }

    private void onErrorListener(final Throwable error) {
        getExceptionHandler().handleException("Error from Kafka consumer.", error);
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    protected void processRollback(Exchange exchange) {
        final Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException("Error during processing exchange.", exchange, cause);
        }
    }

    private Exchange createExchange(final KafkaConsumerRecord<Object, Object> record) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        // set body as byte[] and let camel typeConverters do the job to convert
        message.setBody(record.record().value());

        // set headers
        message.setHeader(VertxKafkaConstants.PARTITION_ID, record.partition());
        message.setHeader(VertxKafkaConstants.TOPIC, record.topic());
        message.setHeader(VertxKafkaConstants.OFFSET, record.offset());
        message.setHeader(VertxKafkaConstants.HEADERS, record.headers());
        message.setHeader(VertxKafkaConstants.TIMESTAMP, record.timestamp());
        message.setHeader(Exchange.MESSAGE_TIMESTAMP, record.timestamp());
        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, record.key());

        // set headers for the manual offsets commit
        if (getConfiguration().isAllowManualCommit()) {
            message.setHeader(VertxKafkaConstants.MANUAL_COMMIT, createKafkaManualCommit(kafkaConsumer, record.topic(),
                    new TopicPartition(record.topic(), record.partition()), record.offset()));
        }

        return exchange;
    }

    private VertxKafkaManualCommit createKafkaManualCommit(
            final KafkaConsumer<Object, Object> consumer, final String topicName,
            final TopicPartition partition, final long offset) {
        return getEndpoint().getComponent().getKafkaManualCommitFactory().create(consumer, topicName, partition, offset);
    }

    private class ConsumerOnCompletion extends SynchronizationAdapter {

        @Override
        public void onFailure(Exchange exchange) {
            processRollback(exchange);
        }
    }
}
