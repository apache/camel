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

import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncCommitManager extends AbstractCommitManager {
    private static final Logger LOG = LoggerFactory.getLogger(SyncCommitManager.class);

    private final Consumer<?, ?> consumer;

    public SyncCommitManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        super(consumer, kafkaConsumer, threadId, printableTopic);

        this.consumer = consumer;
    }

    @Override
    public void commit() {
        LOG.info("Auto commitSync {} from {}", threadId, printableTopic);
        consumer.commitSync();
    }

    @Override
    public void commitOffsetOnStop(TopicPartition partition, long partitionLastOffset) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitSync on stop {} from topic {}", threadId, partition.topic());
        }

        commitSync(partition, partitionLastOffset);
    }

    @Override
    public void commitOffset(TopicPartition partition, long partitionLastOffset) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitSync from thread {} from topic {}", threadId, partition.topic());
        }

        commitSync(partition, partitionLastOffset);
    }

    private void commitSync(TopicPartition partition, long partitionLastOffset) {
        long timeout = configuration.getCommitTimeoutMs();
        consumer.commitSync(
                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)),
                Duration.ofMillis(timeout));
    }
}
