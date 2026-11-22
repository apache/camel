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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.kafka.consumer.support.classic.ClassicRebalanceListener;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.consumer.RetriableCommitFailedException;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The offsets that a commit manager records for a partition must not outlive the ownership of the partition: an offset
 * kept after the partition was lost, or after its commit failed on a revoke, is committed when the partition comes back
 * to the consumer, which moves back the offset that another consumer committed in the meantime. An earlier commit must
 * also not make the commit manager forget a newer offset.
 */
class CommitManagerRebalanceTest {

    private static final TopicPartition P0 = new TopicPartition("orders", 0);

    private final GroupMockConsumer consumer = new GroupMockConsumer();
    private final KafkaConfiguration configuration = new KafkaConfiguration();
    private final List<RuntimeException> pollErrors = new ArrayList<>();
    private CommitManager commitManager;
    private ClassicRebalanceListener listener;

    @Test
    void testLostPartitionIsNotCommittedSync() {
        setup(false);
        lostPartitionIsNotCommitted();
    }

    @Test
    void testLostPartitionIsNotCommittedAsync() {
        setup(true);
        lostPartitionIsNotCommitted();
    }

    private void lostPartitionIsNotCommitted() {
        // the records up to offset 4 are processed, and the consumer is then evicted from the group (it exceeded
        // max.poll.interval.ms): its next poll reports the partition as lost, and its commits are refused
        commitManager.recordOffset(P0, 4);
        consumer.commitFailure = new CommitFailedException();
        rebalance(() -> listener.onPartitionsLost(List.of(P0)));
        consumer.commitFailure = null;

        partitionComesBackAfterAnotherConsumerCommitted(10);
        assertEquals(10L, consumer.groupOffsets.get(P0), "the offset committed by the other consumer must be kept");
        assertEquals(0, consumer.commitRequests, "a lost partition must not be committed");
    }

    @Test
    void testRevokeWithFailedCommitForgetsTheOffsetSync() {
        setup(false);
        revokeWithFailedCommitForgetsTheOffset(new TimeoutException("Simulated commit timeout"));
    }

    @Test
    void testRevokeWithFailedCommitForgetsTheOffsetAsync() {
        setup(true);
        revokeWithFailedCommitForgetsTheOffset(new RetriableCommitFailedException("Simulated commit failure"));
    }

    private void revokeWithFailedCommitForgetsTheOffset(RuntimeException failure) {
        commitManager.recordOffset(P0, 4);
        consumer.commitFailure = failure;
        rebalance(() -> listener.onPartitionsRevoked(List.of(P0)));
        consumer.commitFailure = null;
        assertEquals(1, consumer.commitRequests);

        partitionComesBackAfterAnotherConsumerCommitted(10);
        assertEquals(10L, consumer.groupOffsets.get(P0), "the offset committed by the other consumer must be kept");
    }

    @Test
    void testEarlierCommitKeepsNewerOffsetAsync() {
        setup(true);
        // a batch up to offset 4 is committed, the callback runs in a later call of the consumer
        commitManager.recordOffset(P0, 4);
        commitManager.commit(P0);
        // the next batch up to offset 9: its commit runs the callback of the first one, and then fails
        commitManager.recordOffset(P0, 9);
        consumer.commitFailure = new RetriableCommitFailedException("Simulated commit failure");
        commitManager.commit(P0);
        consumer.commitFailure = null;
        consumer.poll(Duration.ZERO);
        assertEquals(5L, consumer.groupOffsets.get(P0));

        // the revoke commits the offset that is not committed yet
        rebalance(() -> listener.onPartitionsRevoked(List.of(P0)));
        consumer.poll(Duration.ZERO);
        assertEquals(10L, consumer.groupOffsets.get(P0), "the revoke must commit the records up to offset 9");
    }

    @Test
    void testRevokeCommitsTheRecordedOffset() {
        setup(false);
        commitManager.recordOffset(P0, 4);
        rebalance(() -> listener.onPartitionsRevoked(List.of(P0)));
        assertEquals(5L, consumer.groupOffsets.get(P0));
        assertEquals(List.of(), pollErrors);

        // nothing is committed again once the partition came back
        partitionComesBackAfterAnotherConsumerCommitted(10);
        assertEquals(10L, consumer.groupOffsets.get(P0));
    }

    @Test
    void testLostPartitionIsNotSavedInTheOffsetRepository() {
        MemoryStateRepository repository = new MemoryStateRepository();
        configuration.setOffsetRepository(repository);
        configuration.setAutoCommitEnable(false);
        commitManager = new CommitToOffsetManager(consumer, kafkaConsumer(), "t1", "orders");
        listener = new ClassicRebalanceListener("t1", configuration, commitManager, consumer);
        consumer.assign(List.of(P0));

        commitManager.recordOffset(P0, 4);
        // the consumer was evicted, and the new owner of the partition went on to offset 9
        repository.setState("orders/0", "9");
        rebalance(() -> listener.onPartitionsLost(List.of(P0)));

        assertEquals("9", repository.getState("orders/0"), "the lost partition must not be saved");
    }

    private void setup(boolean async) {
        configuration.setAutoCommitEnable(false);
        commitManager = async
                ? new AsyncCommitManager(consumer, kafkaConsumer(), "t1", "orders")
                : new SyncCommitManager(consumer, kafkaConsumer(), "t1", "orders");
        listener = new ClassicRebalanceListener("t1", configuration, commitManager, consumer);
        consumer.assign(List.of(P0));
    }

    private KafkaConsumer kafkaConsumer() {
        configuration.setTopic("orders");
        KafkaEndpoint endpoint = mock(KafkaEndpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        KafkaConsumer kafkaConsumer = mock(KafkaConsumer.class);
        when(kafkaConsumer.getEndpoint()).thenReturn(endpoint);
        return kafkaConsumer;
    }

    private void partitionComesBackAfterAnotherConsumerCommitted(long offset) {
        consumer.poll(Duration.ZERO);
        consumer.groupOffsets.put(P0, offset);
        // the partition comes back, and is revoked again before a record of it was polled
        rebalance(() -> listener.onPartitionsAssigned(List.of(P0)));
        rebalance(() -> listener.onPartitionsRevoked(List.of(P0)));
        consumer.poll(Duration.ZERO);
    }

    /**
     * Runs a rebalance callback as the Kafka client does: an exception of the listener is thrown from the poll once the
     * rebalance has completed.
     */
    private void rebalance(Runnable callback) {
        try {
            callback.run();
        } catch (KafkaException e) {
            pollErrors.add(e);
        }
    }

    /**
     * A consumer of a group: the group offsets survive the rebalances (MockConsumer only returns those of the assigned
     * partitions), a commit can be refused, and the callback of an async commit runs in a later poll or commitAsync, as
     * with the Kafka client.
     */
    private static final class GroupMockConsumer extends MockConsumer<Object, Object> {
        final Map<TopicPartition, Long> groupOffsets = new ConcurrentHashMap<>();
        final List<Runnable> completedCommits = new ArrayList<>();
        volatile RuntimeException commitFailure;
        int commitRequests;

        GroupMockConsumer() {
            super("earliest");
            updateBeginningOffsets(Map.of(P0, 0L));
        }

        @Override
        public synchronized void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets, Duration timeout) {
            commitSync(offsets);
        }

        @Override
        public synchronized void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets) {
            commitRequests++;
            if (commitFailure != null) {
                throw commitFailure;
            }
            offsets.forEach((partition, offset) -> groupOffsets.put(partition, offset.offset()));
        }

        @Override
        public synchronized void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
            runCompletedCommitCallbacks();
            commitRequests++;
            RuntimeException failure = commitFailure;
            if (failure == null) {
                offsets.forEach((partition, offset) -> groupOffsets.put(partition, offset.offset()));
            }
            if (callback != null) {
                completedCommits.add(() -> callback.onComplete(offsets, failure));
            }
        }

        @Override
        public synchronized ConsumerRecords<Object, Object> poll(Duration timeout) {
            runCompletedCommitCallbacks();
            return super.poll(timeout);
        }

        private void runCompletedCommitCallbacks() {
            List<Runnable> callbacks = new ArrayList<>(completedCommits);
            completedCommits.clear();
            callbacks.forEach(Runnable::run);
        }
    }
}
