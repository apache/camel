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
import java.util.Map;

import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncCommitManager extends AbstractCommitManager {
    private static final Logger LOG = LoggerFactory.getLogger(SyncCommitManager.class);

    private final OffsetCache offsetCache = new OffsetCache();
    private final Consumer<?, ?> consumer;
    private final StateRepository<String, String> offsetRepository;

    public SyncCommitManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        super(consumer, kafkaConsumer, threadId, printableTopic);

        this.consumer = consumer;

        offsetRepository = configuration.getOffsetRepository();
    }

    @Override
    public void commit() {
        if (kafkaConsumer.getEndpoint().getConfiguration().isAutoCommitEnable()) {
            LOG.info("Auto commitSync {} from {}", threadId, printableTopic);
            consumer.commitSync();
        }
    }

    @Override
    public void commit(TopicPartition partition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitSync from thread {} from topic {}", threadId, partition.topic());
        }

        commitSync(partition);
    }

    private void commitSync(TopicPartition partition) {
        Long offset = offsetCache.getOffset(partition);
        if (offset == null) {
            return;
        }

        final long lastOffset = offset + 1;

        final Map<TopicPartition, OffsetAndMetadata> offsets
                = Collections.singletonMap(partition, new OffsetAndMetadata(lastOffset));
        long timeout = configuration.getCommitTimeoutMs();
        consumer.commitSync(offsets, Duration.ofMillis(timeout));

        if (offsetRepository != null) {
            saveStateToOffsetRepository(partition, lastOffset, offsetRepository);
        }

        offsetCache.removeCommittedEntries(offsets, null);
    }

    @Override
    public void recordOffset(TopicPartition partition, long partitionLastOffset) {
        offsetCache.recordOffset(partition, partitionLastOffset);
    }
}
