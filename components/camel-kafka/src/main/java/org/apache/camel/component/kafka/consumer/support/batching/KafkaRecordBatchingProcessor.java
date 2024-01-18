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

        // Batching is always in manual commit mode
        KafkaManualCommit manual = commitManager.getManualCommit(exchange, topicPartition, consumerRecord);

        message.setHeader(KafkaConstants.MANUAL_COMMIT, manual);

        return exchange;
    }

    public ProcessingResult processExchange(KafkaConsumer camelKafkaConsumer, ConsumerRecords<Object, Object> consumerRecords) {

        List<Exchange> exchangeList = new ArrayList<>(consumerRecords.count());

        for (ConsumerRecord<Object, Object> consumerRecord : consumerRecords) {
            TopicPartition tp = new TopicPartition(consumerRecord.topic(), consumerRecord.partition());
            Exchange exchange = toExchange(camelKafkaConsumer, tp, consumerRecord);

            exchangeList.add(exchange);
        }

        final Exchange exchange = camelKafkaConsumer.createExchange(false);
        final Message message = exchange.getMessage();
        message.setBody(exchangeList);

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

        // Release the exchange
        camelKafkaConsumer.releaseExchange(exchange, false);

        return result;
    }

    private boolean processException(Exchange exchange, ExceptionHandler exceptionHandler) {
        // will handle/log the exception and then continue to next
        exceptionHandler.handleException("Error during processing", exchange, exchange.getException());

        return true;
    }
}
