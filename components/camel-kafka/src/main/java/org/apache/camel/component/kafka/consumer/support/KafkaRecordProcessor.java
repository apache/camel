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
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.AbstractCommitManager;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaRecordProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordProcessor.class);

    private final boolean autoCommitEnabled;
    private final KafkaConfiguration configuration;
    private final Processor processor;
    private final CommitManager commitManager;

    public KafkaRecordProcessor(KafkaConfiguration configuration, Processor processor, CommitManager commitManager) {
        this.autoCommitEnabled = configuration.isAutoCommitEnable();
        this.configuration = configuration;
        this.processor = processor;
        this.commitManager = commitManager;
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

        LOG.debug("Setting up the exchange for message from partition {} and offset {}",
                record.partition(), record.offset());

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
            Exchange exchange, TopicPartition topicPartition, boolean partitionHasNext,
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
            // allow Camel users to access the Kafka consumer API to be able to do for example manual commits
            KafkaManualCommit manual = commitManager.getManualCommit(exchange, topicPartition, record);

            message.setHeader(KafkaConstants.MANUAL_COMMIT, manual);
            message.setHeader(KafkaConstants.LAST_POLL_RECORD, !recordHasNext && !partitionHasNext);
        }

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            LOG.debug("An exception was thrown for record at partition {} and offset {}",
                    record.partition(), record.offset());

            boolean breakOnErrorExit = processException(exchange, topicPartition, record, lastResult,
                    exceptionHandler);
            return new ProcessingResult(breakOnErrorExit, lastResult.getPartition(), lastResult.getPartitionLastOffset(), true);
        } else {
            return new ProcessingResult(false, record.partition(), record.offset(), exchange.getException() != null);
        }
    }

    private boolean processException(
            Exchange exchange, TopicPartition topicPartition,
            ConsumerRecord<Object, Object> record, ProcessingResult lastResult,
            ExceptionHandler exceptionHandler) {

        // processing failed due to an unhandled exception, what should we do
        if (configuration.isBreakOnFirstError()) {
            if (lastResult.getPartition() != -1 &&
                    lastResult.getPartition() != record.partition()) {
                LOG.error("About to process an exception with UNEXPECTED partition & offset. Got topic partition {}. " +
                          " The last result was on partition {} with offset {} but was expecting partition {} with offset {}",
                        topicPartition.partition(), lastResult.getPartition(), lastResult.getPartitionLastOffset(),
                        record.partition(), record.offset());
            }

            // we are failing and we should break out
            if (LOG.isWarnEnabled()) {
                Exception exc = exchange.getException();
                LOG.warn("Error during processing {} from topic: {} due to {}", exchange, topicPartition.topic(),
                        exc.getMessage());
                LOG.warn("Will seek consumer to offset {} on partition {} and start polling again.",
                        record.offset(), record.partition());
            }

            // force commit, so we resume on next poll where we failed 
            // except when the failure happened at the first message in a poll
            if (lastResult.getPartitionLastOffset() != AbstractCommitManager.START_OFFSET) {
                // we should just do a commit (vs the original forceCommit)
                // when route uses NOOP Commit Manager it will rely
                // on the route implementation to explicitly commit offset
                // when route uses Synch/Asynch Commit Manager it will 
                // ALWAYS commit the offset for the failing record
                // and will ALWAYS retry it
                commitManager.commit(topicPartition);
            }

            // continue to next partition
            return true;
        } else {
            // will handle/log the exception and then continue to next
            exceptionHandler.handleException("Error during processing", exchange, exchange.getException());
        }

        return false;
    }

    public static String serializeOffsetKey(TopicPartition topicPartition) {
        return topicPartition.topic() + '/' + topicPartition.partition();
    }

    public static long deserializeOffsetValue(String offset) {
        return Long.parseLong(offset);
    }
}
