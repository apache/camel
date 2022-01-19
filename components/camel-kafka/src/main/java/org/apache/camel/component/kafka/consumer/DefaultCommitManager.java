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
import java.util.concurrent.ConcurrentLinkedQueue;

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

public class DefaultCommitManager implements CommitManager {
    public static final long START_OFFSET = -1;

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCommitManager.class);
    private final Consumer<?, ?> consumer;
    private final KafkaConsumer kafkaConsumer;
    private final String threadId;
    private final String printableTopic;
    private final KafkaConfiguration configuration;

    private final ConcurrentLinkedQueue<KafkaAsyncManualCommit> asyncCommits = new ConcurrentLinkedQueue<>();

    public DefaultCommitManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        this.consumer = consumer;
        this.kafkaConsumer = kafkaConsumer;
        this.threadId = threadId;
        this.printableTopic = printableTopic;
        this.configuration = kafkaConsumer.getEndpoint().getConfiguration();
    }

    public void processAsyncCommits() {
        while (!asyncCommits.isEmpty()) {
            asyncCommits.poll().processAsyncCommit();
        }
    }

    @Override
    public KafkaManualCommit getManualCommit(
            Exchange exchange, TopicPartition partition, ConsumerRecord<Object, Object> record) {
        KafkaManualCommitFactory manualCommitFactory = kafkaConsumer.getEndpoint().getKafkaManualCommitFactory();
        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();
        long commitTimeoutMs = configuration.getCommitTimeoutMs();

        return manualCommitFactory.newInstance(exchange, consumer, partition.topic(), threadId,
                offsetRepository, partition, record.offset(), commitTimeoutMs, asyncCommits);
    }

    @Override
    public void commit() {
        processAsyncCommits();
        if (kafkaConsumer.getEndpoint().getConfiguration().isAutoCommitEnable()) {
            if ("async".equals(configuration.getAutoCommitOnStop())) {
                LOG.info("Auto commitAsync on stop {} from {}", threadId, printableTopic);
                consumer.commitAsync();
            } else if ("sync".equals(configuration.getAutoCommitOnStop())) {
                LOG.info("Auto commitSync on stop {} from {}", threadId, printableTopic);
                consumer.commitSync();
            } else if ("none".equals(configuration.getAutoCommitOnStop())) {
                LOG.info("Auto commit on stop {} from {} is disabled (none)", threadId, printableTopic);
            }
        }
    }

    @Override
    public void commitOffset(TopicPartition partition, long partitionLastOffset) {
        if (partitionLastOffset == START_OFFSET) {
            return;
        }

        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();

        if (!configuration.isAllowManualCommit() && offsetRepository != null) {
            saveStateToOffsetRepository(partition, partitionLastOffset, offsetRepository);
        }
    }

    @Override
    public void commitOffsetOnStop(TopicPartition partition, long partitionLastOffset) {
        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();

        if (!configuration.isAllowManualCommit() && offsetRepository != null) {
            saveStateToOffsetRepository(partition, partitionLastOffset, offsetRepository);
        } else {
            // if we are stopping then react according to the configured option
            if ("async".equals(configuration.getAutoCommitOnStop())) {
                commitAsync(consumer, partition, partitionLastOffset);
            } else if ("sync".equals(configuration.getAutoCommitOnStop())) {
                commitSync(configuration, consumer, partition, partitionLastOffset);

            } else if ("none".equals(configuration.getAutoCommitOnStop())) {
                noCommit(partition);
            }
        }
    }

    @Override
    public void commitOffsetForce(TopicPartition partition, long partitionLastOffset) {
        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();

        if (!configuration.isAllowManualCommit() && offsetRepository != null) {
            saveStateToOffsetRepository(partition, partitionLastOffset, offsetRepository);
        } else {
            forceSyncCommit(configuration, consumer, partition, partitionLastOffset);
        }
    }

    private void commitOffset(
            KafkaConfiguration configuration, Consumer<?, ?> consumer, TopicPartition partition,
            long partitionLastOffset) {
        long timeout = configuration.getCommitTimeoutMs();
        consumer.commitSync(
                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)),
                Duration.ofMillis(timeout));
    }

    private void forceSyncCommit(
            KafkaConfiguration configuration, Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Forcing commitSync {} [topic: {} partition: {} offset: {}]", threadId, partition.topic(),
                    partition.partition(), partitionLastOffset);
        }

        commitOffset(configuration, consumer, partition, partitionLastOffset);
    }

    private void noCommit(TopicPartition partition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commit on stop {} from topic {} is disabled (none)", threadId, partition.topic());
        }
    }

    private void commitSync(
            KafkaConfiguration configuration, Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitSync on stop {} from topic {}", threadId, partition.topic());
        }

        commitOffset(configuration, consumer, partition, partitionLastOffset);
    }

    private void commitAsync(Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitAsync on stop {} from topic {}", threadId, partition.topic());
        }

        consumer.commitAsync(
                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)), null);
    }

    private void saveStateToOffsetRepository(
            TopicPartition partition, long partitionLastOffset,
            StateRepository<String, String> offsetRepository) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Saving offset repository state {} [topic: {} partition: {} offset: {}]", threadId, partition.topic(),
                    partition.partition(),
                    partitionLastOffset);
        }
        offsetRepository.setState(serializeOffsetKey(partition), serializeOffsetValue(partitionLastOffset));
    }

    private static String serializeOffsetKey(TopicPartition topicPartition) {
        return topicPartition.topic() + '/' + topicPartition.partition();
    }

    private static String serializeOffsetValue(long offset) {
        return String.valueOf(offset);
    }
}
