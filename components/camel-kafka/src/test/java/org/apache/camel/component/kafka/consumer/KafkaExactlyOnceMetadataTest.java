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

import java.util.List;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaExactlyOnceMetadataTest {

    @Test
    void aggregatesHighestNextOffsetForEachPartition() {
        ConsumerGroupMetadata group = groupMetadata("orders-group");
        DefaultKafkaManualCommit first = manualCommit("orders", 0, 10, group, Optional.of(4));
        DefaultKafkaManualCommit second = manualCommit("orders", 0, 12, group, Optional.of(5));
        DefaultKafkaManualCommit third = manualCommit("orders", 1, 7, group, Optional.of(8));

        KafkaExactlyOnceMetadata metadata
                = KafkaExactlyOnceMetadata.fromManualCommits(List.of(first, second, third));

        assertEquals(13L, metadata.getOffsetsToCommit().get(new TopicPartition("orders", 0)).offset());
        assertEquals(Optional.of(5),
                metadata.getOffsetsToCommit().get(new TopicPartition("orders", 0)).leaderEpoch());
        assertEquals(8L, metadata.getOffsetsToCommit().get(new TopicPartition("orders", 1)).offset());
        assertEquals(Optional.of(8),
                metadata.getOffsetsToCommit().get(new TopicPartition("orders", 1)).leaderEpoch());
        assertEquals(group, metadata.getConsumerGroupMetadata());
    }

    @Test
    void rejectsTokensFromDifferentConsumerGroups() {
        DefaultKafkaManualCommit first
                = manualCommit("orders", 0, 10, groupMetadata("orders-group"));
        DefaultKafkaManualCommit second
                = manualCommit("orders", 1, 7, groupMetadata("another-group"));

        assertThrows(IllegalStateException.class,
                () -> KafkaExactlyOnceMetadata.fromManualCommits(List.of(first, second)));
    }

    @Test
    void rejectsTokensFromDifferentGroupGenerations() {
        DefaultKafkaManualCommit first
                = manualCommit("orders", 0, 10, groupMetadata("orders-group", 3, "member-1"));
        DefaultKafkaManualCommit second
                = manualCommit("orders", 1, 7, groupMetadata("orders-group", 4, "member-1"));

        assertThrows(IllegalStateException.class,
                () -> KafkaExactlyOnceMetadata.fromManualCommits(List.of(first, second)));
    }

    @Test
    void rejectsTokensFromDifferentGroupMembers() {
        DefaultKafkaManualCommit first
                = manualCommit("orders", 0, 10, groupMetadata("orders-group", 3, "member-1"));
        DefaultKafkaManualCommit second
                = manualCommit("orders", 1, 7, groupMetadata("orders-group", 3, "member-2"));

        assertThrows(IllegalStateException.class,
                () -> KafkaExactlyOnceMetadata.fromManualCommits(List.of(first, second)));
    }

    @Test
    void sendFailureIsNotRecordedForANonExactlyOnceExchange() {
        Exchange exchange = mock(Exchange.class);
        RuntimeException failure = new RuntimeException("ordinary Kafka send failed");

        KafkaExactlyOnceMetadata.recordSendFailure(exchange, failure);

        verify(exchange, never()).setProperty(KafkaExactlyOnceMetadata.SEND_FAILURE_PROPERTY, failure);
    }

    @Test
    void metadataCanOnlyBeClaimedByTheOriginalExchange() {
        DefaultKafkaManualCommit manual = manualCommit("orders", 0, 10, groupMetadata("orders-group"));
        KafkaExactlyOnceMetadata metadata = KafkaExactlyOnceMetadata.fromManualCommits(List.of(manual), "source-1");

        metadata.claim("source-1");
        assertTrue(metadata.isClaimed());
        assertThrows(IllegalStateException.class, () -> metadata.claim("wiretap-copy"));
    }

    @Test
    void metadataCommitsOffsetsDirectlyWhenNoKafkaProducerClaimsThem() {
        @SuppressWarnings("unchecked")
        Consumer<String, String> consumer = mock(Consumer.class);
        ConsumerGroupMetadata group = groupMetadata("orders-group");
        KafkaManualCommitFactory.CamelExchangePayload camelPayload
                = new KafkaManualCommitFactory.CamelExchangePayload(null, consumer, "poll-thread", null, group);
        KafkaManualCommitFactory.KafkaRecordPayload recordPayload
                = new KafkaManualCommitFactory.KafkaRecordPayload(
                        new TopicPartition("orders", 0), 10, Optional.of(4), 1000);
        DefaultKafkaManualCommit manual
                = new DefaultKafkaManualSyncCommit(camelPayload, recordPayload, mock(CommitManager.class));
        KafkaExactlyOnceMetadata metadata
                = KafkaExactlyOnceMetadata.fromManualCommits(List.of(manual), "source-2");

        metadata.commitOffsets();

        org.mockito.Mockito.verify(consumer).commitSync(metadata.getOffsetsToCommit());
    }

    private static ConsumerGroupMetadata groupMetadata(String groupId) {
        return groupMetadata(groupId, 1, "member-1");
    }

    private static ConsumerGroupMetadata groupMetadata(String groupId, int generationId, String memberId) {
        ConsumerGroupMetadata group = mock(ConsumerGroupMetadata.class);
        when(group.groupId()).thenReturn(groupId);
        when(group.generationId()).thenReturn(generationId);
        when(group.memberId()).thenReturn(memberId);
        when(group.groupInstanceId()).thenReturn(Optional.empty());
        return group;
    }

    private static DefaultKafkaManualCommit manualCommit(
            String topic, int partition, long offset, ConsumerGroupMetadata group) {
        return manualCommit(topic, partition, offset, group, Optional.empty());
    }

    private static DefaultKafkaManualCommit manualCommit(
            String topic, int partition, long offset, ConsumerGroupMetadata group, Optional<Integer> leaderEpoch) {
        DefaultKafkaManualCommit manual = mock(DefaultKafkaManualCommit.class);
        when(manual.getPartition()).thenReturn(new TopicPartition(topic, partition));
        when(manual.getRecordOffset()).thenReturn(offset);
        when(manual.getLeaderEpoch()).thenReturn(leaderEpoch);
        when(manual.getConsumerGroupMetadata()).thenReturn(group);
        return manual;
    }
}
