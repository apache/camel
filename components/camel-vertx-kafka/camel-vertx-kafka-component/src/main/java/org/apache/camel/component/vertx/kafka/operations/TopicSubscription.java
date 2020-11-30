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
