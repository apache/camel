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

package org.apache.camel.component.kafka.consumer;

import java.time.Duration;
import java.util.Collections;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCommitManager implements CommitManager {
    public static final long START_OFFSET = -1;
    public static final long NON_PARTITION = -1;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommitManager.class);

    protected final KafkaConsumer kafkaConsumer;
    protected final String threadId;
    protected final String printableTopic;
    protected final KafkaConfiguration configuration;

    private final Consumer<?, ?> consumer;

    protected AbstractCommitManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId,
                                    String printableTopic) {
        this.consumer = consumer;
        this.kafkaConsumer = kafkaConsumer;
        this.threadId = threadId;
        this.printableTopic = printableTopic;
        this.configuration = kafkaConsumer.getEndpoint().getConfiguration();
    }

    protected KafkaManualCommit getManualCommit(
            Exchange exchange, TopicPartition partition, ConsumerRecord<Object, Object> record,
            KafkaManualCommitFactory manualCommitFactory) {

        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();
        long commitTimeoutMs = configuration.getCommitTimeoutMs();

        KafkaManualCommitFactory.CamelExchangePayload camelExchangePayload = new KafkaManualCommitFactory.CamelExchangePayload(
                exchange, consumer, threadId, offsetRepository);
        KafkaManualCommitFactory.KafkaRecordPayload kafkaRecordPayload = new KafkaManualCommitFactory.KafkaRecordPayload(
                partition,
                record.offset(), commitTimeoutMs);

        return manualCommitFactory.newInstance(camelExchangePayload, kafkaRecordPayload, this);
    }

    @Override
    public KafkaManualCommit getManualCommit(
            Exchange exchange, TopicPartition partition, ConsumerRecord<Object, Object> consumerRecord) {

        KafkaManualCommitFactory manualCommitFactory = kafkaConsumer.getEndpoint().getKafkaManualCommitFactory();
        if (manualCommitFactory == null) {
            manualCommitFactory = new DefaultKafkaManualCommitFactory();
        }

        return getManualCommit(exchange, partition, consumerRecord, manualCommitFactory);
    }

    @Override
    public void forceCommit(TopicPartition partition, long partitionLastOffset) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Forcing commitSync {} [topic: {} partition: {} offset: {}]", threadId, partition.topic(),
                    partition.partition(), partitionLastOffset);
        }

        long timeout = configuration.getCommitTimeoutMs();
        consumer.commitSync(
                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)),
                Duration.ofMillis(timeout));
    }

    protected void saveStateToOffsetRepository(
            TopicPartition partition, long partitionLastOffset,
            StateRepository<String, String> offsetRepository) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Saving offset repository state {} [topic: {} partition: {} offset: {}]", threadId, partition.topic(),
                    partition.partition(),
                    partitionLastOffset);
        }
        offsetRepository.setState(serializeOffsetKey(partition), serializeOffsetValue(partitionLastOffset));
    }

    protected static String serializeOffsetKey(TopicPartition topicPartition) {
        return topicPartition.topic() + '/' + topicPartition.partition();
    }

    protected static String serializeOffsetValue(long offset) {
        return String.valueOf(offset);
    }

}
