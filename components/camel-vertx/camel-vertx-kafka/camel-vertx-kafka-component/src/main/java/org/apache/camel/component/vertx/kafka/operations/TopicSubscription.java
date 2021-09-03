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
package org.apache.camel.component.vertx.kafka.operations;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.vertx.kafka.client.common.TopicPartition;
import org.apache.camel.util.ObjectHelper;

public class TopicSubscription {
    private final String configuredTopicName;
    private final Integer partitionId;
    private final Long seekToOffset;
    private final OffsetPosition seekToPosition;

    public TopicSubscription(String configuredTopicName, Integer partitionId, Long seekToOffset, String seekToPosition) {
        this.configuredTopicName = configuredTopicName;
        this.partitionId = partitionId;
        this.seekToOffset = seekToOffset;

        if (ObjectHelper.equal(seekToPosition, OffsetPosition.BEGINNING.string)) {
            this.seekToPosition = OffsetPosition.BEGINNING;
        } else if (ObjectHelper.equal(seekToPosition, OffsetPosition.END.string)) {
            this.seekToPosition = OffsetPosition.END;
        } else {
            this.seekToPosition = null;
        }
    }

    public String getConfiguredTopicName() {
        return configuredTopicName;
    }

    public Set<String> getTopics() {
        return new HashSet<>(Arrays.asList(configuredTopicName.split(",")));
    }

    public Integer getPartitionId() {
        return partitionId;
    }

    public Long getSeekToOffset() {
        return seekToOffset;
    }

    public OffsetPosition getSeekToPosition() {
        return seekToPosition;
    }

    public Set<TopicPartition> getTopicPartitions() {
        return getTopics()
                .stream()
                .map(topic -> new TopicPartition().setPartition(partitionId).setTopic(topic))
                .collect(Collectors.toSet());
    }

    public enum OffsetPosition {
        END("end"),
        BEGINNING("beginning");

        final String string;

        OffsetPosition(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }
    }
}
