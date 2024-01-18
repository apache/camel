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

import java.util.List;

import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common code for processing consumer records retrieved from Kafka
 */
public abstract class AbstractKafkaRecordProcessorFacade implements KafkaRecordProcessorFacade {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractKafkaRecordProcessorFacade.class);
    protected final KafkaConsumer camelKafkaConsumer;
    protected final String threadId;
    protected final CommitManager commitManager;
    protected final KafkaConsumerListener consumerListener;

    protected AbstractKafkaRecordProcessorFacade(
                                                 KafkaConsumer camelKafkaConsumer, String threadId, CommitManager commitManager,
                                                 KafkaConsumerListener consumerListener) {
        this.camelKafkaConsumer = camelKafkaConsumer;
        this.threadId = threadId;
        this.commitManager = commitManager;
        this.consumerListener = consumerListener;
    }

    /**
     * Whether the the Camel consumer is stopping
     *
     * @return true if is stopping or false otherwise
     */
    protected boolean isStopping() {
        return camelKafkaConsumer.isStopping();
    }

    /**
     * Utility to log record information along with partition
     *
     * @param partitionRecords records from partition
     * @param partition        topic/partition information
     */
    protected void logRecordsInPartition(List<ConsumerRecord<Object, Object>> partitionRecords, TopicPartition partition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Records count {} received for partition {}", partitionRecords.size(),
                    partition);
        }
    }

    /**
     * Utility to log record information
     *
     * @param allRecords records retrieved from Kafka
     */
    protected void logRecords(ConsumerRecords<Object, Object> allRecords) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Last poll on thread {} resulted on {} records to process", threadId, allRecords.count());
        }
    }

    protected void logRecord(ConsumerRecord<Object, Object> consumerRecord) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Partition = {}, offset = {}, key = {}, value = {}", consumerRecord.partition(),
                    consumerRecord.offset(), consumerRecord.key(), consumerRecord.value());
        }
    }

}
