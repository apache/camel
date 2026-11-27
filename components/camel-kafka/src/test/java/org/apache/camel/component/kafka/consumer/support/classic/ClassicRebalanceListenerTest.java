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
package org.apache.camel.component.kafka.consumer.support.classic;

import java.util.List;
import java.util.Set;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.SeekPolicy;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With a cooperative assignor (CooperativeStickyAssignor) or the consumer group protocol, a rebalance passes only the
 * partitions it added to onPartitionsAssigned, and the consumer keeps reading the partitions it already had. The seek
 * of seekTo and offsetRepository must then only move the added partitions.
 */
class ClassicRebalanceListenerTest {

    private static final TopicPartition KEPT = new TopicPartition("myTopic", 0);
    private static final TopicPartition ADDED = new TopicPartition("myTopic", 1);

    private Consumer<?, ?> consumer;
    private KafkaConfiguration configuration;

    @BeforeEach
    void setUp() {
        consumer = mock(Consumer.class);
        // the consumer owns both partitions after the rebalance, but only ADDED was assigned by it
        when(consumer.assignment()).thenReturn(Set.of(KEPT, ADDED));
        configuration = new KafkaConfiguration();
    }

    private ClassicRebalanceListener listener() {
        return new ClassicRebalanceListener("thread-1", configuration, mock(CommitManager.class), consumer);
    }

    @Test
    void seekToBeginningOnlyMovesTheAssignedPartitions() {
        configuration.setSeekTo(SeekPolicy.BEGINNING);

        listener().onPartitionsAssigned(List.of(ADDED));

        verify(consumer).seekToBeginning(List.of(ADDED));
        verify(consumer, never()).seekToBeginning(Set.of(KEPT, ADDED));
    }

    @Test
    void seekToEndOnlyMovesTheAssignedPartitions() {
        configuration.setSeekTo(SeekPolicy.END);

        listener().onPartitionsAssigned(List.of(ADDED));

        verify(consumer).seekToEnd(List.of(ADDED));
        verify(consumer, never()).seekToEnd(Set.of(KEPT, ADDED));
    }

    @Test
    void rebalanceWithoutAddedPartitionsDoesNotSeek() {
        configuration.setSeekTo(SeekPolicy.BEGINNING);

        // a cooperative rebalance calls onPartitionsAssigned even when it added no partition
        listener().onPartitionsAssigned(List.of());

        verify(consumer, never()).seekToBeginning(anyCollection());
    }

    @Test
    void offsetRepositoryOnlyMovesTheAssignedPartitions() {
        MemoryStateRepository repository = new MemoryStateRepository();
        repository.setState(OffsetPartitionAssignmentAdapter.serializeOffsetKey(KEPT), "10");
        repository.setState(OffsetPartitionAssignmentAdapter.serializeOffsetKey(ADDED), "20");
        configuration.setOffsetRepository(repository);

        listener().onPartitionsAssigned(List.of(ADDED));

        verify(consumer).seek(ADDED, 21L);
        verify(consumer, never()).seek(eq(KEPT), anyLong());
    }

    @Test
    void eagerRebalanceMovesAllPartitions() {
        configuration.setSeekTo(SeekPolicy.BEGINNING);

        // an eager assignor (RangeAssignor) revokes everything and assigns all the partitions of the consumer again
        listener().onPartitionsAssigned(List.of(KEPT, ADDED));

        // the same as before: every partition of the consumer is moved
        verify(consumer).seekToBeginning(argThat(partitions -> Set.copyOf(partitions).equals(Set.of(KEPT, ADDED))));
    }
}
