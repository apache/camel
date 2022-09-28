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

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaRecordProcessorFacade {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordProcessorFacade.class);

    private final KafkaConsumer camelKafkaConsumer;
    private final String threadId;
    private final KafkaRecordProcessor kafkaRecordProcessor;
    private final CommitManager commitManager;
    private final KafkaConsumerListener consumerListener;

    public KafkaRecordProcessorFacade(KafkaConsumer camelKafkaConsumer, String threadId,
                                      CommitManager commitManager, KafkaConsumerListener consumerListener) {
        this.camelKafkaConsumer = camelKafkaConsumer;
        this.threadId = threadId;
        this.commitManager = commitManager;

        kafkaRecordProcessor = buildKafkaRecordProcessor(commitManager);
        this.consumerListener = consumerListener;
    }

    private boolean isStopping() {
        return camelKafkaConsumer.isStopping();
    }

    public ProcessingResult processPolledRecords(
            ConsumerRecords<Object, Object> allRecords, ProcessingResult resultFromPreviousPoll) {
        logRecords(allRecords);

        Set<TopicPartition> partitions = allRecords.partitions();
        Iterator<TopicPartition> partitionIterator = partitions.iterator();

        ProcessingResult lastResult
                = resultFromPreviousPoll == null ? ProcessingResult.newUnprocessed() : resultFromPreviousPoll;

        while (partitionIterator.hasNext() && !isStopping()) {
            TopicPartition partition = partitionIterator.next();

            List<ConsumerRecord<Object, Object>> partitionRecords = allRecords.records(partition);
            Iterator<ConsumerRecord<Object, Object>> recordIterator = partitionRecords.iterator();

            logRecordsInPartition(partitionRecords, partition);

            while (!lastResult.isBreakOnErrorHit() && recordIterator.hasNext() && !isStopping()) {
                ConsumerRecord<Object, Object> record = recordIterator.next();

                lastResult = processRecord(partition, partitionIterator.hasNext(), recordIterator.hasNext(), lastResult,
                        kafkaRecordProcessor, record);

                if (consumerListener != null) {
                    if (!consumerListener.afterProcess(lastResult)) {
                        commitManager.commit(partition);
                        return lastResult;
                    }
                }
            }

            if (!lastResult.isBreakOnErrorHit()) {
                LOG.debug("Committing offset on successful execution");
                // all records processed from partition so commit them
                commitManager.commit(partition);
            }
        }

        return lastResult;
    }

    private void logRecordsInPartition(List<ConsumerRecord<Object, Object>> partitionRecords, TopicPartition partition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Records count {} received for partition {}", partitionRecords.size(),
                    partition);
        }
    }

    private void logRecords(ConsumerRecords<Object, Object> allRecords) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Last poll on thread {} resulted on {} records to process", threadId, allRecords.count());
        }
    }

    private ProcessingResult processRecord(
            TopicPartition partition,
            boolean partitionHasNext,
            boolean recordHasNext,
            final ProcessingResult lastResult,
            KafkaRecordProcessor kafkaRecordProcessor,
            ConsumerRecord<Object, Object> record) {

        logRecord(record);

        Exchange exchange = camelKafkaConsumer.createExchange(false);

        ProcessingResult currentResult
                = kafkaRecordProcessor.processExchange(exchange, partition, partitionHasNext,
                        recordHasNext, record, lastResult, camelKafkaConsumer.getExceptionHandler());

        if (!currentResult.isBreakOnErrorHit()) {
            commitManager.recordOffset(partition, currentResult.getPartitionLastOffset());
        }

        // success so release the exchange
        camelKafkaConsumer.releaseExchange(exchange, false);

        return currentResult;
    }

    private void logRecord(ConsumerRecord<Object, Object> record) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Partition = {}, offset = {}, key = {}, value = {}", record.partition(),
                    record.offset(), record.key(), record.value());
        }
    }

    private KafkaRecordProcessor buildKafkaRecordProcessor(CommitManager commitManager) {
        return new KafkaRecordProcessor(
                camelKafkaConsumer.getEndpoint().getConfiguration(),
                camelKafkaConsumer.getProcessor(),
                commitManager);
    }
}
