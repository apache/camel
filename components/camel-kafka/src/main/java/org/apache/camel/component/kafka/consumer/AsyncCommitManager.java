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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncCommitManager extends AbstractCommitManager {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncCommitManager.class);
    private final Consumer<?, ?> consumer;

    private final OffsetCache offsetCache = new OffsetCache();
    // the offsets of the manual commits, which may come from any thread: the consumer thread commits them
    private final Map<TopicPartition, Long> requestedOffsets = new ConcurrentHashMap<>();
    private final StateRepository<String, String> offsetRepository;

    public AsyncCommitManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        super(consumer, kafkaConsumer, threadId, printableTopic);

        this.consumer = consumer;

        offsetRepository = configuration.getOffsetRepository();
    }

    @Override
    public void commit() {
        if (kafkaConsumer.getEndpoint().getConfiguration().isAutoCommitEnable()) {
            LOG.info("Auto commitAsync {} from {}", threadId, printableTopic);
            consumer.commitAsync();
        }
    }

    @Override
    public void commit(TopicPartition partition) {
        // the partition is committed now (for example, it is revoked): its requested offset is not committed later
        Long requested = requestedOffsets.remove(partition);
        Long offset = offsetCache.getOffset(partition);
        if (offset == null) {
            offset = requested;
        }
        if (offset == null) {
            return;
        }

        commitAsync(consumer, partition, offset);
    }

    @Override
    public void requestCommit(TopicPartition partition, long partitionLastOffset) {
        // the Kafka consumer must only be used by its own thread, which commits before its next poll
        offsetCache.recordOffset(partition, partitionLastOffset);
        requestedOffsets.put(partition, partitionLastOffset);
    }

    @Override
    public void commitRequestedOffsets() {
        if (requestedOffsets.isEmpty()) {
            return;
        }

        // a commit requested after its partition was revoked is not committed: it could move back the offset that the
        // new owner of the partition committed
        Set<TopicPartition> assignment = consumer.assignment();
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        for (TopicPartition partition : requestedOffsets.keySet()) {
            Long offset = requestedOffsets.remove(partition);
            if (offset == null) {
                continue;
            }
            if (assignment.contains(partition)) {
                offsets.put(partition, new OffsetAndMetadata(offset + 1));
            } else {
                // requestCommit also recorded the offset in the cache: a later revoke of the partition must not commit it
                offsetCache.remove(partition);
                LOG.debug("Dropping the requested commit of offset {} on {} from {}: the partition is no longer assigned",
                        offset, partition, threadId);
            }
        }
        if (!offsets.isEmpty()) {
            LOG.debug("Committing the requested offsets {} from {}", offsets, threadId);
            consumer.commitAsync(offsets, this::postCommitCallback);
        }
    }

    private void commitAsync(Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitAsync on stop {} from topic {}", threadId, partition.topic());
        }

        final Map<TopicPartition, OffsetAndMetadata> topicPartitionOffsetAndMetadataMap
                = Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1));
        consumer.commitAsync(topicPartitionOffsetAndMetadataMap, this::postCommitCallback);
    }

    @Override
    public KafkaManualCommit getManualCommit(
            Exchange exchange, TopicPartition partition, ConsumerRecord<Object, Object> consumerRecord) {

        KafkaManualCommitFactory manualCommitFactory = kafkaConsumer.getEndpoint().getKafkaManualCommitFactory();
        if (manualCommitFactory == null) {
            manualCommitFactory = new DefaultKafkaManualAsyncCommitFactory();
        }

        return getManualCommit(exchange, partition, consumerRecord, manualCommitFactory);
    }

    @Override
    public void recordOffset(TopicPartition partition, long partitionLastOffset) {
        offsetCache.recordOffset(partition, partitionLastOffset);
    }

    @Override
    public void discardRecordedOffset(TopicPartition partition) {
        requestedOffsets.remove(partition);
        offsetCache.remove(partition);
    }

    private void postCommitCallback(Map<TopicPartition, OffsetAndMetadata> committed, Exception exception) {
        if (exception == null) {
            if (offsetRepository != null) {
                for (var entry : committed.entrySet()) {
                    // Store the last read offset (Kafka committed offset - 1) to match
                    // the contract in OffsetPartitionAssignmentAdapter.resumeFromOffset
                    // which seeks to state + 1
                    saveStateToOffsetRepository(entry.getKey(), entry.getValue().offset() - 1, offsetRepository);
                }
            }
        }

        offsetCache.removeCommittedEntries(committed, exception);
    }
}
