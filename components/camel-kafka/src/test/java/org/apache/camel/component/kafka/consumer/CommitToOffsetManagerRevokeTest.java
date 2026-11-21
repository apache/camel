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

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.kafka.consumer.support.classic.ClassicRebalanceListener;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The offset repository is shared by the consumers of an endpoint (consumersCount) and can be shared by several
 * applications. A consumer that gets a partition back, and loses it again before it processed a record of it, must not
 * write the offset it saved when it owned the partition before (another consumer has gone on since).
 */
class CommitToOffsetManagerRevokeTest {

    private static final TopicPartition P0 = new TopicPartition("orders", 0);
    private static final String KEY = "orders/0";

    private final MemoryStateRepository repository = new MemoryStateRepository();
    private final MockConsumer<Object, Object> consumer = new MockConsumer<>("earliest");
    private CommitToOffsetManager commitManager;
    private ClassicRebalanceListener listener;

    @BeforeEach
    void setup() {
        KafkaConfiguration configuration = new KafkaConfiguration();
        configuration.setTopic("orders");
        configuration.setOffsetRepository(repository);
        KafkaEndpoint endpoint = mock(KafkaEndpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        KafkaConsumer kafkaConsumer = mock(KafkaConsumer.class);
        when(kafkaConsumer.getEndpoint()).thenReturn(endpoint);

        consumer.assign(List.of(P0));
        commitManager = new CommitToOffsetManager(consumer, kafkaConsumer, "t1", "orders");
        listener = new ClassicRebalanceListener("t1", configuration, commitManager, consumer);
    }

    @Test
    void revokeAfterPartitionCameBackKeepsNewerOffset() {
        // this consumer processes up to offset 100 and commits it (as after each poll), then loses the partition
        commitManager.recordOffset(P0, 100);
        commitManager.commit(P0);
        listener.onPartitionsRevoked(List.of(P0));
        assertEquals("100", repository.getState(KEY));

        // another consumer of the group processes up to offset 500
        repository.setState(KEY, "500");

        // the partition comes back: this consumer resumes after 500
        listener.onPartitionsAssigned(List.of(P0));
        assertEquals(501, consumer.position(P0));

        // and is revoked again before it polled a record of it
        listener.onPartitionsRevoked(List.of(P0));

        assertEquals("500", repository.getState(KEY), "the revoke must not move the offset back to 100");
    }

    @Test
    void commitAfterNewRecordsSavesTheirOffset() {
        commitManager.recordOffset(P0, 100);
        commitManager.commit(P0);
        repository.setState(KEY, "500");
        listener.onPartitionsAssigned(List.of(P0));

        // records processed after the partition came back are saved
        commitManager.recordOffset(P0, 520);
        commitManager.commit(P0);
        assertEquals("520", repository.getState(KEY));

        listener.onPartitionsRevoked(List.of(P0));
        assertEquals("520", repository.getState(KEY));
    }

    @Test
    void forceCommitSavesTheGivenOffset() {
        commitManager.recordOffset(P0, 100);
        commitManager.forceCommit(P0, 90);
        assertEquals("90", repository.getState(KEY));
    }
}
