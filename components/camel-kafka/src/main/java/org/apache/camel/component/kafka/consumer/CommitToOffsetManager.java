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

import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;

public class CommitToOffsetManager extends AbstractCommitManager {
    private final OffsetCache offsetCache = new OffsetCache();
    private final StateRepository<String, String> offsetRepository;

    public CommitToOffsetManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        super(consumer, kafkaConsumer, threadId, printableTopic);

        offsetRepository = configuration.getOffsetRepository();
    }

    @Override
    public void commit(TopicPartition partition) {
        Long offset = offsetCache.getOffset(partition);
        if (offset == null) {
            return;
        }

        saveStateToOffsetRepository(partition, offset, offsetRepository);
    }

    @Override
    public void forceCommit(TopicPartition partition, long partitionLastOffset) {
        saveStateToOffsetRepository(partition, partitionLastOffset, offsetRepository);
    }

    @Override
    public void commit() {
        // NO-OP ... commits to offset only
    }

    @Override
    public void recordOffset(TopicPartition partition, long partitionLastOffset) {
        if (partitionLastOffset == START_OFFSET) {
            return;
        }

        offsetCache.recordOffset(partition, partitionLastOffset);
    }
}
