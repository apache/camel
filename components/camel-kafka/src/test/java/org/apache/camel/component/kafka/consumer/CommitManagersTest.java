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

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommitManagersTest {

    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final KafkaConsumer kafkaConsumer = mock(KafkaConsumer.class);
    private final KafkaEndpoint endpoint = mock(KafkaEndpoint.class);
    private final KafkaConfiguration configuration = mock(KafkaConfiguration.class);

    @BeforeEach
    void setup() {
        when(kafkaConsumer.getEndpoint()).thenReturn(endpoint);
        when(endpoint.getConfiguration()).thenReturn(configuration);
    }

    @Test
    void manualCommitWithNoFactoryUsesNoop() {
        when(configuration.isAllowManualCommit()).thenReturn(true);
        when(endpoint.getKafkaManualCommitFactory()).thenReturn(null);

        CommitManager cm = CommitManagers.createCommitManager(consumer, kafkaConsumer, "t1", "topic");

        assertInstanceOf(NoopCommitManager.class, cm);
    }

    @Test
    void manualCommitWithSyncFactoryUsesSync() {
        when(configuration.isAllowManualCommit()).thenReturn(true);
        when(endpoint.getKafkaManualCommitFactory()).thenReturn(new DefaultKafkaManualCommitFactory());

        CommitManager cm = CommitManagers.createCommitManager(consumer, kafkaConsumer, "t1", "topic");

        assertInstanceOf(SyncCommitManager.class, cm);
    }

    @Test
    void manualCommitWithAsyncFactoryUsesAsync() {
        when(configuration.isAllowManualCommit()).thenReturn(true);
        when(endpoint.getKafkaManualCommitFactory()).thenReturn(new DefaultKafkaManualAsyncCommitFactory());

        CommitManager cm = CommitManagers.createCommitManager(consumer, kafkaConsumer, "t1", "topic");

        assertInstanceOf(AsyncCommitManager.class, cm);
    }

    @Test
    void autoCommitWithOffsetRepositoryUsesCommitToOffset() {
        when(configuration.isAllowManualCommit()).thenReturn(false);
        when(configuration.getOffsetRepository()).thenReturn(mock());

        CommitManager cm = CommitManagers.createCommitManager(consumer, kafkaConsumer, "t1", "topic");

        assertInstanceOf(CommitToOffsetManager.class, cm);
    }

    @Test
    void autoCommitWithBatchingUsesAsync() {
        when(configuration.isAllowManualCommit()).thenReturn(false);
        when(configuration.isBatching()).thenReturn(true);

        CommitManager cm = CommitManagers.createCommitManager(consumer, kafkaConsumer, "t1", "topic");

        assertInstanceOf(AsyncCommitManager.class, cm);
    }

    @Test
    void defaultConfigUsesNoop() {
        when(configuration.isAllowManualCommit()).thenReturn(false);

        CommitManager cm = CommitManagers.createCommitManager(consumer, kafkaConsumer, "t1", "topic");

        assertInstanceOf(NoopCommitManager.class, cm);
    }
}
