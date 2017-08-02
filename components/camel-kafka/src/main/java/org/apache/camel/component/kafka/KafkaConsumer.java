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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;

public class KafkaConsumer extends DefaultConsumer {

    protected ExecutorService executor;
    private final KafkaEndpoint endpoint;
    private final Processor processor;
    private final Long pollTimeoutMs;
    // This list helps working around the infinite loop of KAFKA-1894
    private final List<KafkaFetchRecords> tasks = new ArrayList<>();

    public KafkaConsumer(KafkaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        this.pollTimeoutMs = endpoint.getConfiguration().getPollTimeoutMs();

        // brokers can be configured on endpoint or component level
        String brokers = endpoint.getConfiguration().getBrokers();
        if (brokers == null) {
            brokers = endpoint.getComponent().getBrokers();
        }
        if (ObjectHelper.isEmpty(brokers)) {
            throw new IllegalArgumentException("Brokers must be configured");
        }

        if (endpoint.getConfiguration().getGroupId() == null) {
            throw new IllegalArgumentException("groupId must not be null");
        }
    }

    Properties getProps() {
        Properties props = endpoint.getConfiguration().createConsumerProperties();
        endpoint.updateClassProperties(props);

        // brokers can be configured on endpoint or component level
        String brokers = endpoint.getConfiguration().getBrokers();
        if (brokers == null) {
            brokers = endpoint.getComponent().getBrokers();
        }

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, endpoint.getConfiguration().getGroupId());
        return props;
    }

    @Override
    protected void doStart() throws Exception {
        log.info("Starting Kafka consumer on topic: {} with breakOnFirstError: {}",
            endpoint.getConfiguration().getTopic(), endpoint.getConfiguration().isBreakOnFirstError());
        super.doStart();

        executor = endpoint.createExecutor();
        for (int i = 0; i < endpoint.getConfiguration().getConsumersCount(); i++) {
            KafkaFetchRecords task = new KafkaFetchRecords(endpoint.getConfiguration().getTopic(), i + "", getProps());
            executor.submit(task);
            tasks.add(task);
        }
    }

    @Override
    protected void doStop() throws Exception {
        log.info("Stopping Kafka consumer on topic: {}", endpoint.getConfiguration().getTopic());

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
            } else {
                executor.shutdownNow();
            }
            if (!executor.isTerminated()) {
                tasks.forEach(KafkaFetchRecords::shutdown);
                executor.shutdownNow();
            }
        }
        tasks.clear();
        executor = null;

        super.doStop();
    }

    class KafkaFetchRecords implements Runnable {

        private org.apache.kafka.clients.consumer.KafkaConsumer consumer;
        private final String topicName;
        private final String threadId;
        private final Properties kafkaProps;

        KafkaFetchRecords(String topicName, String id, Properties kafkaProps) {
            this.topicName = topicName;
            this.threadId = topicName + "-" + "Thread " + id;
            this.kafkaProps = kafkaProps;
        }

        @Override
        public void run() {
            boolean first = true;
            boolean reConnect = true;

            while (reConnect) {

                // create consumer
                ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    // Kafka uses reflection for loading authentication settings, use its classloader
                    Thread.currentThread().setContextClassLoader(org.apache.kafka.clients.consumer.KafkaConsumer.class.getClassLoader());
                    this.consumer = new org.apache.kafka.clients.consumer.KafkaConsumer(kafkaProps);
                } finally {
                    Thread.currentThread().setContextClassLoader(threadClassLoader);
                }

                if (!first) {
                    // skip one poll timeout before trying again
                    long delay = endpoint.getConfiguration().getPollTimeoutMs();
                    log.info("Reconnecting {} to topic {} after {} ms", threadId, topicName, delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                first = false;

                // doRun keeps running until we either shutdown or is told to re-connect
                reConnect = doRun();
            }
        }

        @SuppressWarnings("unchecked")
        protected boolean doRun() {
            // allow to re-connect thread in case we use that to retry failed messages
            boolean reConnect = false;

            try {
                log.info("Subscribing {} to topic {}", threadId, topicName);
                consumer.subscribe(Arrays.asList(topicName.split(",")));

                StateRepository<String, String> offsetRepository = endpoint.getConfiguration().getOffsetRepository();
                if (offsetRepository != null) {
                    // This poll to ensures we have an assigned partition otherwise seek won't work
                    ConsumerRecords poll = consumer.poll(100);

                    for (TopicPartition topicPartition : (Set<TopicPartition>) consumer.assignment()) {
                        String offsetState = offsetRepository.getState(serializeOffsetKey(topicPartition));
                        if (offsetState != null && !offsetState.isEmpty()) {
                            // The state contains the last read offset so you need to seek from the next one
                            long offset = deserializeOffsetValue(offsetState) + 1;
                            log.debug("Resuming partition {} from offset {} from state", topicPartition.partition(), offset);
                            consumer.seek(topicPartition, offset);
                        } else {
                            // If the init poll has returned some data of a currently unknown topic/partition in the state
                            // then resume from their offset in order to avoid losing data
                            List<ConsumerRecord<Object, Object>> partitionRecords = poll.records(topicPartition);
                            if (!partitionRecords.isEmpty()) {
                                long offset = partitionRecords.get(0).offset();
                                log.debug("Resuming partition {} from offset {}", topicPartition.partition(), offset);
                                consumer.seek(topicPartition, offset);
                            }
                        }
                    }
                } else if (endpoint.getConfiguration().getSeekTo() != null) {
                    if (endpoint.getConfiguration().getSeekTo().equals("beginning")) {
                        log.debug("{} is seeking to the beginning on topic {}", threadId, topicName);
                        // This poll to ensures we have an assigned partition otherwise seek won't work
                        consumer.poll(100);
                        consumer.seekToBeginning(consumer.assignment());
                    } else if (endpoint.getConfiguration().getSeekTo().equals("end")) {
                        log.debug("{} is seeking to the end on topic {}", threadId, topicName);
                        // This poll to ensures we have an assigned partition otherwise seek won't work
                        consumer.poll(100);
                        consumer.seekToEnd(consumer.assignment());
                    }
                }

                while (isRunAllowed() && !reConnect && !isStoppingOrStopped() && !isSuspendingOrSuspended()) {

                    // flag to break out processing on the first exception
                    boolean breakOnErrorHit = false;
                    log.trace("Polling {} from topic: {} with timeout: {}", threadId, topicName, pollTimeoutMs);
                    ConsumerRecords<Object, Object> allRecords = consumer.poll(pollTimeoutMs);

                    for (TopicPartition partition : allRecords.partitions()) {

                        long partitionLastOffset = -1;

                        Iterator<ConsumerRecord<Object, Object>> recordIterator = allRecords.records(partition).iterator();
                        if (!breakOnErrorHit && recordIterator.hasNext()) {
                            ConsumerRecord<Object, Object> record;

                            while (!breakOnErrorHit && recordIterator.hasNext()) {
                                record = recordIterator.next();
                                if (log.isTraceEnabled()) {
                                    log.trace("Partition = {}, offset = {}, key = {}, value = {}", record.partition(), record.offset(), record.key(),
                                              record.value());
                                }
                                Exchange exchange = endpoint.createKafkaExchange(record);
                                if (endpoint.getConfiguration().isAutoCommitEnable() != null && !endpoint.getConfiguration().isAutoCommitEnable()) {
                                    exchange.getIn().setHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, !recordIterator.hasNext());
                                }

                                try {
                                    processor.process(exchange);
                                } catch (Exception e) {
                                    exchange.setException(e);
                                }

                                if (exchange.getException() != null) {
                                    // processing failed due to an unhandled exception, what should we do
                                    if (endpoint.getConfiguration().isBreakOnFirstError()) {
                                        // we are failing and we should break out
                                        log.warn("Error during processing {} from topic: {}. Will seek consumer to offset: {} and re-connect and start polling again.",
                                            exchange, topicName, partitionLastOffset);
                                        // force commit so we resume on next poll where we failed
                                        commitOffset(offsetRepository, partition, partitionLastOffset, true);
                                        // continue to next partition
                                        breakOnErrorHit = true;
                                    } else {
                                        // will handle/log the exception and then continue to next
                                        getExceptionHandler().handleException("Error during processing", exchange, exchange.getException());
                                    }
                                } else {
                                    // record was success so remember its offset
                                    partitionLastOffset = record.offset();
                                }
                            }

                            if (!breakOnErrorHit) {
                                // all records processed from partition so commit them
                                commitOffset(offsetRepository, partition, partitionLastOffset, false);
                            }
                        }
                    }

                    if (breakOnErrorHit) {
                        // force re-connect
                        reConnect = true;
                    }
                }

                if (!reConnect) {
                    if (endpoint.getConfiguration().isAutoCommitEnable() != null && endpoint.getConfiguration().isAutoCommitEnable()) {
                        if ("async".equals(endpoint.getConfiguration().getAutoCommitOnStop())) {
                            log.info("Auto commitAsync on stop {} from topic {}", threadId, topicName);
                            consumer.commitAsync();
                        } else if ("sync".equals(endpoint.getConfiguration().getAutoCommitOnStop())) {
                            log.info("Auto commitSync on stop {} from topic {}", threadId, topicName);
                            consumer.commitSync();
                        }
                    }
                }

                log.info("Unsubscribing {} from topic {}", threadId, topicName);
                consumer.unsubscribe();
            } catch (InterruptException e) {
                getExceptionHandler().handleException("Interrupted while consuming " + threadId + " from kafka topic", e);
                log.info("Unsubscribing {} from topic {}", threadId, topicName);
                consumer.unsubscribe();
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                getExceptionHandler().handleException("Error consuming " + threadId + " from kafka topic", e);
            } finally {
                log.debug("Closing {} ", threadId);
                IOHelper.close(consumer);
            }

            return reConnect;
        }

        private void commitOffset(StateRepository<String, String> offsetRepository, TopicPartition partition, long partitionLastOffset, boolean forceCommit) {
            if (partitionLastOffset != -1) {
                if (offsetRepository != null) {
                    offsetRepository.setState(serializeOffsetKey(partition), serializeOffsetValue(partitionLastOffset));
                } else if (forceCommit) {
                    log.debug("Forcing commitSync {} from topic {} with offset: {}", threadId, topicName, partitionLastOffset);
                    consumer.commitSync(Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)));
                } else if (endpoint.getConfiguration().isAutoCommitEnable() != null && !endpoint.getConfiguration().isAutoCommitEnable()) {
                    log.debug("Auto commitSync {} from topic {} with offset: {}", threadId, topicName, partitionLastOffset);
                    consumer.commitSync(Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)));
                }
            }
        }

        private void shutdown() {
            // As advised in the KAFKA-1894 ticket, calling this wakeup method breaks the infinite loop
            consumer.wakeup();
        }
    }

    protected String serializeOffsetKey(TopicPartition topicPartition) {
        return topicPartition.topic() + '/' + topicPartition.partition();
    }

    protected String serializeOffsetValue(long offset) {
        return String.valueOf(offset);
    }

    protected long deserializeOffsetValue(String offset) {
        return Long.parseLong(offset);
    }
}

