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
package org.apache.camel.component.kafka.consumer.support.streaming;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.KafkaExactlyOnceMetadata;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KafkaRecordStreamingProcessor extends KafkaRecordProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordStreamingProcessor.class);

    private final boolean autoCommitEnabled;
    private final KafkaConfiguration configuration;
    private final Processor processor;
    private final CommitManager commitManager;

    public KafkaRecordStreamingProcessor(KafkaConfiguration configuration, Processor processor, CommitManager commitManager) {
        this.autoCommitEnabled = configuration.isAutoCommitEnable();
        this.configuration = configuration;
        this.processor = processor;
        this.commitManager = commitManager;
    }

    public ProcessingResult processExchange(
            KafkaConsumer camelKafkaConsumer, TopicPartition topicPartition, boolean partitionHasNext,
            boolean recordHasNext, ConsumerRecord<Object, Object> consumerRecord) {

        final Exchange exchange = camelKafkaConsumer.createExchange(false);

        Message message = exchange.getMessage();

        setupExchangeMessage(message, consumerRecord);

        propagateHeaders(configuration, consumerRecord, exchange);

        // if not auto commit then we have additional information on the exchange
        if (!autoCommitEnabled) {
            message.setHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, !recordHasNext);
            message.setHeader(KafkaConstants.LAST_POLL_RECORD, !recordHasNext && !partitionHasNext);
        }

        if (configuration.isAllowManualCommit()) {
            // allow Camel users to access the Kafka consumer API to be able to do for example manual commits
            KafkaManualCommit manual = commitManager.getManualCommit(exchange, topicPartition, consumerRecord);

            message.setHeader(KafkaConstants.MANUAL_COMMIT, manual);
            message.setHeader(KafkaConstants.LAST_POLL_RECORD, !recordHasNext && !partitionHasNext);
            if (configuration.isExactlyOnce()) {
                // Keep the source offsets outside message headers: removeHeaders() and message replacement must not
                // discard the information needed by the destination producer.
                exchange.setProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY,
                        KafkaExactlyOnceMetadata.fromManualCommits(List.of(manual), exchange.getExchangeId()));
            }
        }

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        completeExactlyOnceProcessing(exchange);

        ProcessingResult result;
        if (exchange.getException() != null) {
            LOG.debug("An exception was thrown for consumerRecord at partition {} and offset {}",
                    consumerRecord.partition(), consumerRecord.offset());
            final ExceptionHandler exceptionHandler = camelKafkaConsumer.getExceptionHandler();

            boolean breakOnErrorExit = processException(exchange, topicPartition, consumerRecord, exceptionHandler);
            result = new ProcessingResult(
                    breakOnErrorExit, true, consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset());
        } else {
            result = new ProcessingResult(
                    false, exchange.getException() != null, consumerRecord.topic(), consumerRecord.partition(),
                    consumerRecord.offset());
        }

        if (!result.isBreakOnErrorHit()) {
            commitManager.recordOffset(topicPartition, consumerRecord.offset());
        }

        // success so release the exchange
        camelKafkaConsumer.releaseExchange(exchange, false);

        return result;
    }

    /**
     * A route that did not claim the source metadata with an exactly-once producer still has to advance the source
     * offset (for example, a filter branch). Run this after route completion on the consumer polling thread.
     */
    private void completeExactlyOnceProcessing(Exchange exchange) {
        if (!configuration.isExactlyOnce()) {
            return;
        }

        KafkaExactlyOnceMetadata metadata = exchange.getProperty(
                KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
        boolean transactionAborted = Boolean.TRUE.equals(
                exchange.getProperty(KafkaExactlyOnceMetadata.TRANSACTION_ABORTED_PROPERTY));
        if (transactionAborted || exchange.isRollbackOnly()) {
            if (exchange.getException() == null) {
                exchange.setException(new IllegalStateException(
                        "The Kafka exactlyOnce transaction was rolled back; the source record must be replayed"));
            }
            return;
        }
        if (exchange.getException() != null) {
            return;
        }

        Throwable sendFailure = exchange.getProperty(KafkaExactlyOnceMetadata.SEND_FAILURE_PROPERTY, Throwable.class);
        if (sendFailure != null && (metadata == null || !metadata.isClaimed())) {
            // A producer failed before it could register a transaction synchronization. Do not mistake a handled
            // configuration/startup failure for a filter branch and commit its source offset.
            exchange.setException(new IllegalStateException(
                    "The exactlyOnce Kafka producer failed before it could claim the source offsets", sendFailure));
            return;
        }
        if (metadata == null) {
            exchange.setException(new IllegalStateException(
                    "exactlyOnce source metadata was not captured for the consumed record"));
            return;
        }
        if (!metadata.isClaimed()) {
            try {
                metadata.commitOffsets();
            } catch (Exception commitFailure) {
                exchange.setException(commitFailure);
            }
        }
    }

    private boolean processException(
            Exchange exchange, TopicPartition topicPartition,
            ConsumerRecord<Object, Object> consumerRecord, ExceptionHandler exceptionHandler) {

        // processing failed due to an unhandled exception, what should we do
        if (configuration.isBreakOnFirstError()) {
            // we are failing and we should break out
            if (LOG.isWarnEnabled()) {
                Exception exc = exchange.getException();
                LOG.warn("Error during processing {} from topic: {} due to {}", exchange, topicPartition.topic(),
                        exc.getMessage());
                LOG.warn("Will seek consumer to offset {} on partition {} and start polling again.",
                        consumerRecord.offset(), consumerRecord.partition());
            }

            // we should just do a commit (vs the original forceCommit)
            // when route uses NOOP Commit Manager it will rely
            // on the route implementation to explicitly commit offset
            // when route uses Synch/Asynch Commit Manager it will
            // ALWAYS commit the offset for the failing record
            // and will ALWAYS retry it
            commitManager.commit(topicPartition);

            // continue to next partition
            return true;
        } else {
            // will handle/log the exception and then continue to next
            exceptionHandler.handleException("Error during processing", exchange, exchange.getException());
        }

        return false;
    }
}
