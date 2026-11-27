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
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncCommitManager extends AbstractCommitManager {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncCommitManager.class);
    private final Consumer<?, ?> consumer;

    // the offsets of the last processed records
    private final OffsetCache offsetCache = new OffsetCache();
    // the offsets of the manual commits, which may come from any thread: the consumer thread commits them
    private final Map<TopicPartition, Long> requestedOffsets = new ConcurrentHashMap<>();
    // the assignment of each partition to this consumer: a number that changes each time the partition is revoked, lost
    // or read again (see discardRecordedOffset). A manual commit keeps the assignment in which its record was polled
    private final Map<TopicPartition, Long> assignments = new ConcurrentHashMap<>();
    private final StateRepository<String, String> offsetRepository;
    private final boolean manualCommit;

    public AsyncCommitManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        super(consumer, kafkaConsumer, threadId, printableTopic);

        this.consumer = consumer;

        offsetRepository = configuration.getOffsetRepository();
        manualCommit = configuration.isAllowManualCommit();
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
        Long offset;
        if (manualCommit) {
            // only what the route committed: the route may still hold the records it processed after that, for
            // example in an aggregation group that is not complete
            offset = requested;
        } else {
            offset = offsetCache.getOffset(partition);
            if (offset == null) {
                offset = requested;
            }
        }
        if (offset == null) {
            return;
        }

        commitAsync(consumer, partition, offset);
    }

    @Override
    public void commitProcessedOffset(TopicPartition partition) {
        // the partition is committed now: its requested offset is not committed later
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
        requestCommit(partition, partitionLastOffset, assignmentOf(partition));
    }

    /**
     * Requests the commit of the given offset, for a record polled in the given assignment of the partition (see
     * {@link #assignmentOf(TopicPartition)}). The commit is not made if the partition was revoked, lost or read again
     * since, also when it is assigned to this consumer again: it could move back the offset committed in the meantime,
     * and replace the commit of a record of the current assignment.
     */
    void requestCommit(TopicPartition partition, long partitionLastOffset, long assignment) {
        // the Kafka consumer must only be used by its own thread, which commits before its next poll
        requestedOffsets.compute(partition, (p, requested) -> {
            if (assignment == assignmentOf(p)) {
                return partitionLastOffset;
            }
            LOG.debug("Dropping the requested commit of offset {} on {} from {}: it is from an earlier assignment",
                    partitionLastOffset, p, threadId);
            return requested;
        });
    }

    /**
     * The current assignment of the given partition to this consumer, which a manual commit keeps when it is created
     */
    long assignmentOf(TopicPartition partition) {
        return assignments.getOrDefault(partition, 0L);
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
                LOG.debug("Dropping the requested commit of offset {} on {} from {}: the partition is no longer assigned",
                        offset, partition, threadId);
            }
        }
        if (!offsets.isEmpty()) {
            LOG.debug("Committing the requested offsets {} from {}", offsets, threadId);
            consumer.commitAsync(offsets, commitCallback(offsets.keySet()));
        }
    }

    private void commitAsync(Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitAsync on stop {} from topic {}", threadId, partition.topic());
        }

        final Map<TopicPartition, OffsetAndMetadata> topicPartitionOffsetAndMetadataMap
                = Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1));
        consumer.commitAsync(topicPartitionOffsetAndMetadataMap, commitCallback(Set.of(partition)));
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
        // a new assignment: the manual commits of the records polled before are not made (see requestCommit)
        assignments.merge(partition, 1L, Long::sum);
        requestedOffsets.remove(partition);
        offsetCache.remove(partition);
    }

    private OffsetCommitCallback commitCallback(Set<TopicPartition> partitions) {
        // the assignments in which the offsets are committed
        Map<TopicPartition, Long> sentIn = new HashMap<>();
        partitions.forEach(partition -> sentIn.put(partition, assignmentOf(partition)));
        return (committed, exception) -> postCommitCallback(committed, exception, sentIn);
    }

    private void postCommitCallback(
            Map<TopicPartition, OffsetAndMetadata> committed, Exception exception, Map<TopicPartition, Long> sentIn) {
        if (exception == null) {
            if (offsetRepository != null) {
                for (var entry : committed.entrySet()) {
                    // Store the last read offset (Kafka committed offset - 1) to match
                    // the contract in OffsetPartitionAssignmentAdapter.resumeFromOffset
                    // which seeks to state + 1
                    saveCommittedOffset(entry.getKey(), entry.getValue().offset() - 1, sentIn.get(entry.getKey()));
                }
            }
        }

        offsetCache.removeCommittedEntries(committed, exception);
    }

    private void saveCommittedOffset(TopicPartition partition, long partitionLastOffset, Long sentIn) {
        // the repository is shared by the consumers of the endpoint, and the callback runs in a later poll: when the
        // partition was revoked from this consumer since the commit, another consumer may own it and have saved a newer
        // offset, which must be kept (the next assignment of the partition seeks to the saved offset)
        synchronized (offsetRepository) {
            if ((sentIn != null && sentIn == assignmentOf(partition))
                    || isNewerThanSavedOffset(partition, partitionLastOffset)) {
                saveStateToOffsetRepository(partition, partitionLastOffset, offsetRepository);
            } else {
                LOG.debug("Not saving the offset {} of {} from {}: the partition was revoked, and a newer offset is saved",
                        partitionLastOffset, partition, threadId);
            }
        }
    }

    private boolean isNewerThanSavedOffset(TopicPartition partition, long partitionLastOffset) {
        String saved = offsetRepository.getState(serializeOffsetKey(partition));
        try {
            return saved == null || saved.isEmpty() || Long.parseLong(saved) < partitionLastOffset;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
