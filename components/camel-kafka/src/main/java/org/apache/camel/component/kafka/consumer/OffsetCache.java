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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OffsetCache {
    private static final Logger LOG = LoggerFactory.getLogger(OffsetCache.class);
    private final Map<TopicPartition, Long> lastProcessedOffset = new ConcurrentHashMap<>();

    public void recordOffset(TopicPartition partition, long partitionLastOffset) {
        lastProcessedOffset.put(partition, partitionLastOffset);
    }

    public void removeCommittedEntries(Map<TopicPartition, OffsetAndMetadata> committed, Exception exception) {
        if (exception == null) {
            committed.forEach(this::removeCommittedEntry);
        } else {
            LOG.error("Failed to commit offset: {}", exception.getMessage(), exception);
        }
    }

    private void removeCommittedEntry(TopicPartition topicPartition, OffsetAndMetadata offsetAndMetadata) {
        LOG.debug(
                "Offset {} from topic {} from partition {} has been successfully committed and is being removed from tracking",
                offsetAndMetadata.offset(),
                topicPartition.topic(), topicPartition.partition());

        lastProcessedOffset.remove(topicPartition);
    }

    public Long getOffset(TopicPartition partition) {
        return lastProcessedOffset.get(partition);
    }

    public long cacheSize() {
        return lastProcessedOffset.size();
    }

    public boolean contains(TopicPartition topicPartition) {
        return lastProcessedOffset.containsKey(topicPartition);
    }
}
