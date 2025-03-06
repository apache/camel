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

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

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
    private final StopWatch timeoutWatch = new StopWatch();
    private final StopWatch intervalWatch = new StopWatch();
    private final Queue<Exchange> exchangeList;

    private final class CommitSynchronization implements Synchronization {
        private final ExceptionHandler exceptionHandler;
        private final int size;

        public CommitSynchronization(ExceptionHandler exceptionHandler, int size) {
            this.exceptionHandler = exceptionHandler;
            this.size = size;
        }

        @Override
        public void onComplete(Exchange exchange) {
            LOG.debug("Calling commit on {} exchanges using {}", size, commitManager.getClass().getSimpleName());
            commitManager.commit();
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
        }
    }

    public KafkaRecordBatchingProcessor(KafkaConfiguration configuration, Processor processor, CommitManager commitManager) {
        this.configuration = configuration;
        this.processor = processor;
        this.commitManager = commitManager;
        this.exchangeList = new ArrayBlockingQueue<>(configuration.getMaxPollRecords());
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
        if (exchangeList.isEmpty()) {
            timeoutWatch.takenAndRestart();
        }

        if (hasExpiredRecords(consumerRecords)) {
            LOG.debug(
                    "The polling timeout has expired with {} records in cache. Dispatching the incomplete batch for processing",
                    exchangeList.size());

            // poll timeout has elapsed, so check for expired records
            processBatch(camelKafkaConsumer);
            exchangeList.clear();
            return ProcessingResult.newUnprocessed();
        }

        for (ConsumerRecord<Object, Object> consumerRecord : consumerRecords) {
            TopicPartition tp = new TopicPartition(consumerRecord.topic(), consumerRecord.partition());
            Exchange childExchange = toExchange(camelKafkaConsumer, tp, consumerRecord);

            exchangeList.add(childExchange);

            if (exchangeList.size() >= configuration.getMaxPollRecords()) {
                processBatch(camelKafkaConsumer);
                exchangeList.clear();
            }
        }

        // None of the states provided by the processing result are relevant for batch processing. We can simply return the
        // default state
        return ProcessingResult.newUnprocessed();
    }

    private boolean hasExpiredRecords(ConsumerRecords<Object, Object> consumerRecords) {
        // no records in batch
        if (exchangeList.isEmpty()) {
            return false;
        }
        // timeout is only triggered if we no new records
        boolean timeout = consumerRecords.isEmpty() && timeoutWatch.taken() >= configuration.getPollTimeoutMs();
        // interval is triggered if enabled, and it has been X time since last batch completion
        boolean interval = configuration.getBatchingIntervalMs() != null
                && intervalWatch.taken() >= configuration.getBatchingIntervalMs();
        return timeout || interval;
    }

    private void processBatch(KafkaConsumer camelKafkaConsumer) {
        intervalWatch.restart();

        // Create the bundle exchange
        Exchange exchange = camelKafkaConsumer.createExchange(false);
        Message message = exchange.getMessage();
        var exchanges = exchangeList.stream().toList();
        message.setBody(exchanges);

        try {
            if (configuration.isAllowManualCommit()) {
                Exchange last = exchanges.isEmpty() ? null : exchanges.get(exchanges.size() - 1);
                if (last != null) {
                    message.setHeader(KafkaConstants.MANUAL_COMMIT, last.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT));
                }
                manualCommitResultProcessing(camelKafkaConsumer, exchange);
            } else {
                autoCommitResultProcessing(camelKafkaConsumer, exchange, exchanges.size());
            }
        } finally {
            // Release the exchange
            camelKafkaConsumer.releaseExchange(exchange, false);
        }
    }

    /*
     * The flow to execute when using auto-commit
     */
    private void autoCommitResultProcessing(KafkaConsumer camelKafkaConsumer, Exchange exchange, int size) {
        ExceptionHandler exceptionHandler = camelKafkaConsumer.getExceptionHandler();
        CommitSynchronization commitSynchronization = new CommitSynchronization(exceptionHandler, size);
        exchange.getExchangeExtension().addOnCompletion(commitSynchronization);
        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            processException(exchange, exceptionHandler);
        }
    }

    /*
     * The flow to execute when the integrations perform manual commit on their own
     */
    private void manualCommitResultProcessing(KafkaConsumer camelKafkaConsumer, Exchange exchange) {
        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            ExceptionHandler exceptionHandler = camelKafkaConsumer.getExceptionHandler();
            processException(exchange, exceptionHandler);
        }
    }

    private void processException(Exchange exchange, ExceptionHandler exceptionHandler) {
        // will handle/log the exception and then continue to next
        exceptionHandler.handleException("Error during processing", exchange, exchange.getException());
    }
}
