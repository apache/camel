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
package org.apache.camel.component.kafka.consumer.support.batching;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.StopWatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KafkaRecordBatchingProcessor extends KafkaRecordProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordBatchingProcessor.class);

    private final KafkaConfiguration configuration;
    private final Processor processor;
    private final CommitManager commitManager;
    private final StopWatch watch = new StopWatch();
    private List<Exchange> exchangeList;

    private final class CommitSynchronization implements Synchronization {
        private final ExceptionHandler exceptionHandler;
        private ProcessingResult result;

        public CommitSynchronization(ExceptionHandler exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void onComplete(Exchange exchange) {
            final List<?> exchanges = exchange.getMessage().getBody(List.class);

            // Ensure we are actually receiving what we are asked for
            if (exchanges == null || exchanges.isEmpty()) {
                LOG.warn("The exchange is {}", exchanges == null ? "not of the expected type (null)" : "empty");
                return;
            }

            LOG.debug("Calling commit on {} exchanges using {}", exchanges.size(), commitManager.getClass().getSimpleName());
            commitManager.commit();
            result = new ProcessingResult(false, false);
        }

        @Override
        public void onFailure(Exchange exchange) {
            Exception cause = exchange.getException();
            if (cause != null) {
                exceptionHandler.handleException(
                        "Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
            } else {
                LOG.warn(
                        "Skipping auto-commit on the batch because processing the exchanged has failed and the error was not correctly handled");
            }

            result = new ProcessingResult(false, true);
        }
    }

    public KafkaRecordBatchingProcessor(KafkaConfiguration configuration, Processor processor, CommitManager commitManager) {
        this.configuration = configuration;
        this.processor = processor;
        this.commitManager = commitManager;
    }

    public Exchange toExchange(
            KafkaConsumer camelKafkaConsumer, TopicPartition topicPartition, ConsumerRecord<Object, Object> consumerRecord) {
        final Exchange exchange = camelKafkaConsumer.createExchange(false);
        Message message = exchange.getMessage();

        setupExchangeMessage(message, consumerRecord);

        propagateHeaders(configuration, consumerRecord, exchange);

        if (configuration.isAllowManualCommit()) {
            KafkaManualCommit manual = commitManager.getManualCommit(exchange, topicPartition, consumerRecord);

            message.setHeader(KafkaConstants.MANUAL_COMMIT, manual);
        }

        return exchange;
    }

    public ProcessingResult processExchange(KafkaConsumer camelKafkaConsumer, ConsumerRecords<Object, Object> consumerRecords) {
        LOG.debug("There's {} records to process ... max poll is set to {}", consumerRecords.count(),
                configuration.getMaxPollRecords());
        // Aggregate all consumer records in a single exchange
        if (exchangeList == null) {
            exchangeList = new ArrayList<>(configuration.getMaxPollRecords());
            watch.takenAndRestart();
        }

        if (hasExpiredRecords(consumerRecords)) {
            LOG.debug(
                    "The polling timeout has expired with {} records in cache. Dispatching the incomplete batch for processing",
                    exchangeList.size());

            // poll timeout has elapsed, so check for expired records
            processBatch(camelKafkaConsumer);
            exchangeList = null;

            return ProcessingResult.newUnprocessed();
        }

        for (ConsumerRecord<Object, Object> consumerRecord : consumerRecords) {
            TopicPartition tp = new TopicPartition(consumerRecord.topic(), consumerRecord.partition());
            Exchange childExchange = toExchange(camelKafkaConsumer, tp, consumerRecord);

            exchangeList.add(childExchange);

            if (exchangeList.size() == configuration.getMaxPollRecords()) {
                processBatch(camelKafkaConsumer);
                exchangeList = null;
            }
        }

        // None of the states provided by the processing result are relevant for batch processing. We can simply return the
        // default state
        return ProcessingResult.newUnprocessed();

    }

    private boolean hasExpiredRecords(ConsumerRecords<Object, Object> consumerRecords) {
        return !exchangeList.isEmpty() && consumerRecords.isEmpty() && watch.taken() >= configuration.getPollTimeoutMs();
    }

    private ProcessingResult processBatch(KafkaConsumer camelKafkaConsumer) {
        // Create the bundle exchange
        final Exchange exchange = camelKafkaConsumer.createExchange(false);
        final Message message = exchange.getMessage();
        message.setBody(exchangeList);

        try {
            if (configuration.isAllowManualCommit()) {
                return manualCommitResultProcessing(camelKafkaConsumer, exchange);
            } else {
                return autoCommitResultProcessing(camelKafkaConsumer, exchange);
            }
        } finally {
            // Release the exchange
            camelKafkaConsumer.releaseExchange(exchange, false);
        }
    }

    /*
     * The flow to execute when using auto-commit
     */
    private ProcessingResult autoCommitResultProcessing(KafkaConsumer camelKafkaConsumer, Exchange exchange) {
        final ExceptionHandler exceptionHandler = camelKafkaConsumer.getExceptionHandler();
        final CommitSynchronization commitSynchronization = new CommitSynchronization(exceptionHandler);
        exchange.getExchangeExtension().addOnCompletion(commitSynchronization);

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        return commitSynchronization.result;
    }

    /*
     * The flow to execute when the integrations perform manual commit on their own
     */
    private ProcessingResult manualCommitResultProcessing(KafkaConsumer camelKafkaConsumer, Exchange exchange) {
        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        ProcessingResult result;
        if (exchange.getException() != null) {
            LOG.debug("An exception was thrown for batch records");
            final ExceptionHandler exceptionHandler = camelKafkaConsumer.getExceptionHandler();
            boolean handled = processException(exchange, exceptionHandler);
            result = new ProcessingResult(false, handled);
        } else {
            result = new ProcessingResult(false, false);
        }

        return result;
    }

    private boolean processException(Exchange exchange, ExceptionHandler exceptionHandler) {
        // will handle/log the exception and then continue to next
        exceptionHandler.handleException("Error during processing", exchange, exchange.getException());
        return true;
    }
}
