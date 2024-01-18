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

import java.util.Set;

import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.camel.component.kafka.consumer.support.AbstractKafkaRecordProcessorFacade;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaRecordBatchingProcessorFacade extends AbstractKafkaRecordProcessorFacade {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordBatchingProcessorFacade.class);
    private final KafkaRecordBatchingProcessor kafkaRecordProcessor;

    public KafkaRecordBatchingProcessorFacade(
                                              KafkaConsumer camelKafkaConsumer, String threadId,
                                              CommitManager commitManager, KafkaConsumerListener consumerListener) {
        super(camelKafkaConsumer, threadId, commitManager, consumerListener);

        kafkaRecordProcessor = buildKafkaRecordProcessor(commitManager);

    }

    private KafkaRecordBatchingProcessor buildKafkaRecordProcessor(CommitManager commitManager) {
        return new KafkaRecordBatchingProcessor(
                camelKafkaConsumer.getEndpoint().getConfiguration(),
                camelKafkaConsumer.getProcessor(),
                commitManager);
    }

    @Override
    public ProcessingResult processPolledRecords(ConsumerRecords<Object, Object> allRecords) {
        logRecords(allRecords);

        Set<TopicPartition> partitions = allRecords.partitions();
        LOG.debug("Poll received records on {} partitions", partitions.size());

        return kafkaRecordProcessor.processExchange(camelKafkaConsumer, allRecords);
    }

}
