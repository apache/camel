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

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.camel.component.kafka.consumer.support.AbstractKafkaRecordProcessorFacade;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaRecordStreamingProcessorFacade extends AbstractKafkaRecordProcessorFacade {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordStreamingProcessorFacade.class);
    private final KafkaRecordStreamingProcessor kafkaRecordProcessor;

    public KafkaRecordStreamingProcessorFacade(
                                               KafkaConsumer camelKafkaConsumer, String threadId,
                                               CommitManager commitManager, KafkaConsumerListener consumerListener) {
        super(camelKafkaConsumer, threadId, commitManager, consumerListener);

        kafkaRecordProcessor = buildKafkaRecordProcessor(commitManager);

    }

    private KafkaRecordStreamingProcessor buildKafkaRecordProcessor(CommitManager commitManager) {
        return new KafkaRecordStreamingProcessor(
                camelKafkaConsumer.getEndpoint().getConfiguration(),
                camelKafkaConsumer.getProcessor(),
                commitManager);
    }

    /**
     * Process a single record retrieved from Kafka
     *
     * @param  partition            the partition
     * @param  partitionHasNext     whether there are more partitions to process
     * @param  recordHasNext        whether more records to be processed exist in that partition
     * @param  kafkaRecordProcessor the record processor
     * @param  consumerRecord       the consumer record retrieved from Kafka to process
     * @return                      The result of processing this set of records
     */
    private ProcessingResult processRecord(
            TopicPartition partition, boolean partitionHasNext, boolean recordHasNext,
            KafkaRecordStreamingProcessor kafkaRecordProcessor,
            ConsumerRecord<Object, Object> consumerRecord) {

        logRecord(consumerRecord);

        return kafkaRecordProcessor.processExchange(camelKafkaConsumer, partition, partitionHasNext,
                recordHasNext, consumerRecord);
    }

    @Override
    public ProcessingResult processPolledRecords(ConsumerRecords<Object, Object> allRecords) {
        logRecords(allRecords);

        ProcessingResult result = ProcessingResult.newUnprocessed();

        Set<TopicPartition> partitions = allRecords.partitions();
        Iterator<TopicPartition> partitionIterator = partitions.iterator();

        LOG.debug("Poll received records on {} partitions", partitions.size());

        while (partitionIterator.hasNext() && !isStopping()) {
            TopicPartition partition = partitionIterator.next();

            LOG.debug("Processing records on partition {}", partition.partition());

            List<ConsumerRecord<Object, Object>> partitionRecords = allRecords.records(partition);
            Iterator<ConsumerRecord<Object, Object>> recordIterator = partitionRecords.iterator();

            logRecordsInPartition(partitionRecords, partition);

            while (!result.isBreakOnErrorHit() && recordIterator.hasNext() && !isStopping()) {
                ConsumerRecord<Object, Object> consumerRecord = recordIterator.next();

                LOG.debug("Processing record on partition {} with offset {}",
                        consumerRecord.partition(),
                        consumerRecord.offset());

                result = processRecord(partition, partitionIterator.hasNext(), recordIterator.hasNext(),
                        kafkaRecordProcessor, consumerRecord);

                LOG.debug("Processed record on partition {} with offset {}",
                        consumerRecord.partition(),
                        consumerRecord.offset());

                if (consumerListener != null) {
                    if (!consumerListener.afterProcess(result)) {
                        commitManager.commit(partition);
                        return result;
                    }
                }
            }

            if (!result.isBreakOnErrorHit()) {
                LOG.debug("Committing offset on successful execution");
                // all records processed from partition so commit them
                commitManager.commit(partition);
            }
        }

        return result;
    }

}
