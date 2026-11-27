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
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.kafka.consumer.support.classic.ClassicRebalanceListener;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With the asynchronous manual commit, the route may commit a record after its partition was revoked from the consumer
 * and assigned to it again (for example after an aggregator). Such a commit is not made: it could move back the offset
 * that another consumer committed in the meantime, or replace a commit of the current assignment. The callback of a
 * commit runs in a later poll, possibly after the partition moved to another consumer of the endpoint, and must then
 * not move back the offset that consumer saved in the offset repository the consumers share.
 */
class AsyncCommitManagerReassignTest {

    private static final TopicPartition P0 = new TopicPartition("orders", 0);

    private final Map<TopicPartition, Long> groupOffsets = new ConcurrentHashMap<>();
    private final KafkaConfiguration configuration = new KafkaConfiguration();

    @Test
    void testCommitOfEarlierAssignmentIsNotMadeAfterReassign() {
        Member member = new Member("t1");
        member.assign();
        KafkaManualCommit earlier = member.manualCommit(0);

        // the partition is revoked while the route holds the record, and another consumer commits up to offset 9
        member.revoke();
        groupOffsets.put(P0, 10L);
        member.assign();

        // the route commits the record of the earlier assignment
        earlier.commit();
        member.commitRequestedOffsetsAndPoll();

        assertEquals(10L, groupOffsets.get(P0), "the offset committed by the other consumer must be kept");
        assertEquals(0, member.consumer.commitRequests);
    }

    @Test
    void testCommitOfEarlierAssignmentDoesNotReplaceCurrentCommit() {
        Member member = new Member("t1");
        member.assign();
        KafkaManualCommit earlier = member.manualCommit(0);
        member.revoke();
        groupOffsets.put(P0, 5L);
        member.assign();
        KafkaManualCommit current = member.manualCommit(6);

        // both commits before the next poll: the one of the current assignment, then the one of the earlier one
        current.commit();
        earlier.commit();
        member.commitRequestedOffsetsAndPoll();

        assertEquals(7L, groupOffsets.get(P0), "the commit of the current assignment must be made");
    }

    @Test
    void testCommitRacingWithRevokeIsNotMadeAfterReassign() throws Exception {
        Member member = new Member("t1");
        member.assign();
        for (int i = 0; i < 500; i++) {
            // the route commits a record while the partition is revoked and assigned again: the revoke commits it,
            // or it is not made at all
            KafkaManualCommit earlier = member.manualCommit(i);
            Thread route = new Thread(earlier::commit);
            route.start();
            member.revoke();
            member.assign();
            route.join(TimeUnit.SECONDS.toMillis(10));
            assertFalse(route.isAlive());

            int commitRequests = member.consumer.commitRequests;
            member.commitRequestedOffsetsAndPoll();
            assertEquals(commitRequests, member.consumer.commitRequests, "iteration " + i);
        }
    }

    @Test
    void testLateCommitCallbackKeepsNewerOffsetInRepository() {
        MemoryStateRepository repository = new MemoryStateRepository();
        configuration.setOffsetRepository(repository);
        Member first = new Member("t1");
        Member second = new Member("t2");

        // the first consumer commits up to offset 2: the callback (which saves the offset) runs in its next poll
        first.assign();
        first.manualCommit(2).commit();
        first.commitManager.commitRequestedOffsets();
        assertEquals(3L, groupOffsets.get(P0));

        // the partition moves to the second consumer, which commits up to offset 5 and saves it
        first.revoke();
        second.assign();
        second.manualCommit(5).commit();
        second.commitRequestedOffsetsAndPoll();
        assertEquals("5", repository.getState("orders/0"));

        // the next poll of the first consumer runs the callback of its commit
        first.consumer.poll(Duration.ZERO);
        assertEquals("5", repository.getState("orders/0"), "the newer offset of the second consumer must be kept");
    }

    @Test
    void testLateCommitCallbackSavesOffsetAfterRevoke() {
        MemoryStateRepository repository = new MemoryStateRepository();
        configuration.setOffsetRepository(repository);
        Member member = new Member("t1");
        member.assign();
        member.manualCommit(1).commit();
        member.commitRequestedOffsetsAndPoll();
        assertEquals("1", repository.getState("orders/0"));

        // the revoke commits up to offset 4, and the callback runs in the next poll, after the revoke
        member.manualCommit(4).commit();
        member.revoke();
        assertEquals(5L, groupOffsets.get(P0));
        member.consumer.poll(Duration.ZERO);
        assertEquals("4", repository.getState("orders/0"), "the offset committed on revoke must be saved");
    }

    private KafkaConsumer kafkaConsumer() {
        configuration.setTopic("orders");
        configuration.setAutoCommitEnable(false);
        configuration.setAllowManualCommit(true);
        KafkaEndpoint endpoint = mock(KafkaEndpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        KafkaConsumer kafkaConsumer = mock(KafkaConsumer.class);
        when(kafkaConsumer.getEndpoint()).thenReturn(endpoint);
        return kafkaConsumer;
    }

    /**
     * A consumer thread of the endpoint: its Kafka consumer, commit manager and rebalance listener.
     */
    private final class Member {
        final GroupMockConsumer consumer = new GroupMockConsumer();
        final AsyncCommitManager commitManager;
        final ClassicRebalanceListener listener;

        Member(String threadId) {
            commitManager = new AsyncCommitManager(consumer, kafkaConsumer(), threadId, "orders");
            listener = new ClassicRebalanceListener(threadId, configuration, commitManager, consumer);
        }

        void assign() {
            consumer.assign(List.of(P0));
            listener.onPartitionsAssigned(List.of(P0));
        }

        void revoke() {
            listener.onPartitionsRevoked(List.of(P0));
        }

        KafkaManualCommit manualCommit(long offset) {
            return commitManager.getManualCommit(mock(Exchange.class), P0,
                    new ConsumerRecord<>(P0.topic(), P0.partition(), offset, null, "r" + offset));
        }

        void commitRequestedOffsetsAndPoll() {
            commitManager.commitRequestedOffsets();
            consumer.poll(Duration.ZERO);
        }
    }

    /**
     * A consumer of the group: the group offsets survive the rebalances, and the callback of an async commit runs in a
     * later poll or commitAsync, as with the Kafka client.
     */
    private final class GroupMockConsumer extends MockConsumer<Object, Object> {
        final List<Runnable> completedCommits = new ArrayList<>();
        int commitRequests;

        GroupMockConsumer() {
            super("earliest");
            updateBeginningOffsets(Map.of(P0, 0L));
        }

        @Override
        public synchronized void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
            runCompletedCommitCallbacks();
            commitRequests++;
            offsets.forEach((partition, offset) -> groupOffsets.put(partition, offset.offset()));
            if (callback != null) {
                completedCommits.add(() -> callback.onComplete(offsets, null));
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
