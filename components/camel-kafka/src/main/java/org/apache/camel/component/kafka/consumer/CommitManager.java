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

import org.apache.camel.Exchange;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

public interface CommitManager {

    KafkaManualCommit getManualCommit(
            Exchange exchange, TopicPartition partition, ConsumerRecord<Object, Object> consumerRecord);

    /**
     * Commits everything that has been cached
     */
    void commit();

    /**
     * Commits the offsets of the given partition, for example when the partition is revoked or the consumer stops.
     * <p>
     * With manual commits (allowManualCommit=true), only the offset that the route committed through
     * {@link KafkaManualCommit#commit()} is committed, if it is not committed yet: the last processed record may still
     * be held by the route (for example in an aggregation group that is not complete), and is not committed for it.
     * Without manual commits, the offset of the last processed record is committed.
     *
     * @param partition the partition to commit the offsets
     */
    void commit(TopicPartition partition);

    /**
     * Commits the offset of the last processed record of the given partition, also with manual commits, whether the
     * route committed it or not. Used by breakOnFirstError to commit the records before the failed one, so that the
     * failed one is polled again.
     *
     * @param partition the partition to commit the offsets
     */
    default void commitProcessedOffset(TopicPartition partition) {
        commit(partition);
    }

    /**
     * Forcefully commits the offset of the given partition
     *
     * @param partition           the partition to commit the offsets
     * @param partitionLastOffset the last offset to commit
     */
    void forceCommit(TopicPartition partition, long partitionLastOffset);

    /**
     * Record the last processed offset for future commit
     *
     * @param partition           the partition to commit the offsets
     * @param partitionLastOffset the last offset to commit
     */
    void recordOffset(TopicPartition partition, long partitionLastOffset);

    /**
     * Forgets the offset recorded for the given partition without committing it. Called when the partition is revoked
     * (after its commit, whether the commit succeeded or not) or lost: the consumer no longer owns the partition, and
     * an offset kept for it could be committed after the partition is assigned to the consumer again, which would move
     * back the offset that another consumer committed in the meantime.
     *
     * @param partition the partition that is no longer assigned to the consumer
     */
    default void discardRecordedOffset(TopicPartition partition) {
        // NO-OP
    }

    /**
     * Commits the given offset, for an asynchronous manual commit (the route called
     * {@link KafkaManualCommit#commit()}). A commit manager that commits asynchronously leaves the commit to the
     * consumer thread (see {@link #commitRequestedOffsets()}), so this can be called from any thread.
     *
     * @param partition           the partition to commit the offsets
     * @param partitionLastOffset the last offset to commit
     */
    default void requestCommit(TopicPartition partition, long partitionLastOffset) {
        recordOffset(partition, partitionLastOffset);
        commitProcessedOffset(partition);
    }

    /**
     * Commits the offsets given to {@link #requestCommit(TopicPartition, long)} that are not committed yet, for the
     * partitions that are still assigned to the consumer. Called by the consumer thread before each poll.
     */
    default void commitRequestedOffsets() {
        // NO-OP
    }
}
