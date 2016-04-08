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
package org.apache.camel.component.kafka;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.InterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumer.class);

    protected ExecutorService executor;
    private final KafkaEndpoint endpoint;
    private final Processor processor;

    public KafkaConsumer(KafkaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;

        if (endpoint.getBrokers() == null) {
            throw new IllegalArgumentException("BootStrap servers must be specified");
        }
        if (endpoint.getGroupId() == null) {
            throw new IllegalArgumentException("groupId must not be null");
        }
    }

    Properties getProps() {
        Properties props = endpoint.getConfiguration().createConsumerProperties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, endpoint.getBrokers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, endpoint.getGroupId());
        return props;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Starting Kafka consumer");
        executor = endpoint.createExecutor();
        for (int i = 0; i < endpoint.getConsumersCount(); i++) {
            executor.submit(new KafkaFetchRecords(endpoint.getTopic(), i + "", getProps()));
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        LOG.info("Stopping Kafka consumer");

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    class KafkaFetchRecords implements Runnable {

        private final org.apache.kafka.clients.consumer.KafkaConsumer consumer;
        private final String topicName;
        private final String threadId;
        private final Properties kafkaProps;

        public KafkaFetchRecords(String topicName, String id, Properties kafkaProps) {
            this.topicName = topicName;
            this.threadId = topicName + "-" + "Thread " + id;
            this.kafkaProps = kafkaProps;
            this.consumer = new org.apache.kafka.clients.consumer.KafkaConsumer(kafkaProps);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            int processed = 0;
            try {
                LOG.debug("Subscribing {} to topic {}", threadId, topicName);
                consumer.subscribe(Arrays.asList(topicName.split(",")));

                if (endpoint.isSeekToBeginning()) {
                    LOG.debug("{} is seeking to the beginning on topic {}", threadId, topicName);
                    // This poll to ensures we have an assigned partition otherwise seek won't work
                    consumer.poll(100);
                    consumer.seekToBeginning();
                }
                while (isRunAllowed() && !isSuspendingOrSuspended()) {
                    ConsumerRecords<Object, Object> records = consumer.poll(Long.MAX_VALUE);
                    for (ConsumerRecord<Object, Object> record : records) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("offset = {}, key = {}, value = {}", record.offset(), record.key(), record.value());
                        }
                        Exchange exchange = endpoint.createKafkaExchange(record);
                        try {
                            processor.process(exchange);
                        } catch (Exception e) {
                            getExceptionHandler().handleException("Error during processing", exchange, e);
                        }
                        processed++;
                        // if autocommit is false
                        if (endpoint.isAutoCommitEnable() != null && !endpoint.isAutoCommitEnable()) {
                            if (processed >= endpoint.getBatchSize()) {
                                consumer.commitSync();
                                processed = 0;
                            }
                        }
                    }
                }
                LOG.debug("Unsubscribing {} from topic {}", threadId, topicName);
                consumer.unsubscribe();
                LOG.debug("Closing {} ", threadId);
                consumer.close();
            } catch (InterruptException e) {
                getExceptionHandler().handleException("Interrupted while consuming " + threadId + " from kafka topic", e);
                consumer.unsubscribe();
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                getExceptionHandler().handleException("Error consuming " + threadId + " from kafka topic", e);
            } finally {
                LOG.debug("Closing {} ", threadId);
                consumer.close();
            }
        }

    }

}

