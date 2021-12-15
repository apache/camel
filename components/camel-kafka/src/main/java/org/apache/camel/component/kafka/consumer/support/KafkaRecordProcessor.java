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

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.StreamSupport;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaAsyncManualCommit;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaManualCommit;
import org.apache.camel.component.kafka.KafkaManualCommitFactory;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaRecordProcessor {
    public static final long START_OFFSET = -1;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordProcessor.class);

    private final boolean autoCommitEnabled;
    private final KafkaConfiguration configuration;
    private final Processor processor;
    private final Consumer<?, ?> consumer;
    private final KafkaManualCommitFactory manualCommitFactory;
    private final String threadId;
    private final ConcurrentLinkedQueue<KafkaAsyncManualCommit> asyncCommits;

    public KafkaRecordProcessor(boolean autoCommitEnabled, KafkaConfiguration configuration,
                                Processor processor, Consumer<?, ?> consumer,
                                KafkaManualCommitFactory manualCommitFactory,
                                String threadId, ConcurrentLinkedQueue<KafkaAsyncManualCommit> asyncCommits) {
        this.autoCommitEnabled = autoCommitEnabled;
        this.configuration = configuration;
        this.processor = processor;
        this.consumer = consumer;
        this.manualCommitFactory = manualCommitFactory;
        this.threadId = threadId;
        this.asyncCommits = asyncCommits;
    }

    private void setupExchangeMessage(Message message, ConsumerRecord record) {
        message.setHeader(KafkaConstants.PARTITION, record.partition());
        message.setHeader(KafkaConstants.TOPIC, record.topic());
        message.setHeader(KafkaConstants.OFFSET, record.offset());
        message.setHeader(KafkaConstants.HEADERS, record.headers());
        message.setHeader(KafkaConstants.TIMESTAMP, record.timestamp());
        message.setHeader(Exchange.MESSAGE_TIMESTAMP, record.timestamp());

        if (record.key() != null) {
            message.setHeader(KafkaConstants.KEY, record.key());
        }

        message.setBody(record.value());
    }

    private boolean shouldBeFiltered(Header header, Exchange exchange, HeaderFilterStrategy headerFilterStrategy) {
        return !headerFilterStrategy.applyFilterToExternalHeaders(header.key(), header.value(), exchange);
    }

    private void propagateHeaders(ConsumerRecord<Object, Object> record, Exchange exchange) {

        HeaderFilterStrategy headerFilterStrategy = configuration.getHeaderFilterStrategy();
        KafkaHeaderDeserializer headerDeserializer = configuration.getHeaderDeserializer();

        StreamSupport.stream(record.headers().spliterator(), false)
                .filter(header -> shouldBeFiltered(header, exchange, headerFilterStrategy))
                .forEach(header -> exchange.getIn().setHeader(header.key(),
                        headerDeserializer.deserialize(header.key(), header.value())));
    }

    public ProcessingResult processExchange(
            Exchange exchange, TopicPartition partition, boolean partitionHasNext,
            boolean recordHasNext, ConsumerRecord<Object, Object> record, ProcessingResult lastResult,
            ExceptionHandler exceptionHandler) {

        Message message = exchange.getMessage();

        setupExchangeMessage(message, record);

        propagateHeaders(record, exchange);

        // if not auto commit then we have additional information on the exchange
        if (!autoCommitEnabled) {
            message.setHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, !recordHasNext);
            message.setHeader(KafkaConstants.LAST_POLL_RECORD, !recordHasNext && !partitionHasNext);
        }

        if (configuration.isAllowManualCommit()) {
            StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();

            // allow Camel users to access the Kafka consumer API to be able to do for example manual commits
            KafkaManualCommit manual = manualCommitFactory.newInstance(exchange, consumer, partition.topic(), threadId,
                    offsetRepository, partition, record.offset(), configuration.getCommitTimeoutMs(), asyncCommits);
            message.setHeader(KafkaConstants.MANUAL_COMMIT, manual);
            message.setHeader(KafkaConstants.LAST_POLL_RECORD, !recordHasNext && !partitionHasNext);
        }

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);

            boolean breakOnErrorExit = processException(exchange, partition, lastResult.getPartitionLastOffset(),
                    exceptionHandler);

            return new ProcessingResult(breakOnErrorExit, lastResult.getPartitionLastOffset());
        }

        return new ProcessingResult(false, record.offset());
    }

    private boolean processException(
            Exchange exchange, TopicPartition partition, long partitionLastOffset,
            ExceptionHandler exceptionHandler) {

        // processing failed due to an unhandled exception, what should we do
        if (configuration.isBreakOnFirstError()) {
            // we are failing and we should break out
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error during processing {} from topic: {}", exchange, partition.topic(), exchange.getException());
                LOG.warn("Will seek consumer to offset {} and start polling again.", partitionLastOffset);
            }

            // force commit, so we resume on next poll where we failed
            commitOffset(partition, partitionLastOffset, false, true);

            // continue to next partition
            return true;
        } else {
            // will handle/log the exception and then continue to next
            exceptionHandler.handleException("Error during processing", exchange, exchange.getException());
        }

        return false;
    }

    public void commitOffset(
            TopicPartition partition, long partitionLastOffset, boolean stopping, boolean forceCommit) {
        commitOffset(configuration, consumer, partition, partitionLastOffset, stopping, forceCommit, threadId);
    }

    public static void commitOffset(
            KafkaConfiguration configuration, Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset,
            boolean stopping, boolean forceCommit, String threadId) {

        if (partitionLastOffset == START_OFFSET) {
            return;
        }

        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();

        if (!configuration.isAllowManualCommit() && offsetRepository != null) {
            saveStateToOffsetRepository(partition, partitionLastOffset, threadId, offsetRepository);
        } else if (stopping) {
            // if we are stopping then react according to the configured option
            if ("async".equals(configuration.getAutoCommitOnStop())) {
                commitAsync(consumer, partition, partitionLastOffset, threadId);
            } else if ("sync".equals(configuration.getAutoCommitOnStop())) {
                commitSync(configuration, consumer, partition, partitionLastOffset, threadId);

            } else if ("none".equals(configuration.getAutoCommitOnStop())) {
                noCommit(partition, threadId);
            }
        } else if (forceCommit) {
            forceSyncCommit(configuration, consumer, partition, partitionLastOffset, threadId);
        }
    }

    private static void commitOffset(
            KafkaConfiguration configuration, Consumer<?, ?> consumer, TopicPartition partition,
            long partitionLastOffset) {
        long timeout = configuration.getCommitTimeoutMs();
        consumer.commitSync(
                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)),
                Duration.ofMillis(timeout));
    }

    private static void forceSyncCommit(
            KafkaConfiguration configuration, Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset,
            String threadId) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Forcing commitSync {} [topic: {} partition: {} offset: {}]", threadId, partition.topic(),
                    partition.partition(), partitionLastOffset);
        }

        commitOffset(configuration, consumer, partition, partitionLastOffset);
    }

    private static void noCommit(TopicPartition partition, String threadId) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commit on stop {} from topic {} is disabled (none)", threadId, partition.topic());
        }
    }

    private static void commitSync(
            KafkaConfiguration configuration, Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset,
            String threadId) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitSync on stop {} from topic {}", threadId, partition.topic());
        }

        commitOffset(configuration, consumer, partition, partitionLastOffset);
    }

    private static void commitAsync(
            Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset, String threadId) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitAsync on stop {} from topic {}", threadId, partition.topic());
        }

        consumer.commitAsync(
                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)), null);
    }

    private static void saveStateToOffsetRepository(
            TopicPartition partition, long partitionLastOffset, String threadId,
            StateRepository<String, String> offsetRepository) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Saving offset repository state {} [topic: {} partition: {} offset: {}]", threadId, partition.topic(),
                    partition.partition(),
                    partitionLastOffset);
        }
        offsetRepository.setState(serializeOffsetKey(partition), serializeOffsetValue(partitionLastOffset));
    }

    public static String serializeOffsetKey(TopicPartition topicPartition) {
        return topicPartition.topic() + '/' + topicPartition.partition();
    }

    public static String serializeOffsetValue(long offset) {
        return String.valueOf(offset);
    }

    public static long deserializeOffsetValue(String offset) {
        return Long.parseLong(offset);
    }
}
