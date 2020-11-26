package org.apache.camel.component.vertx.kafka.operations;

import org.apache.camel.util.ObjectHelper;

public class TopicSubscription {
    private final String topicName;
    private final Integer partitionId;
    private final Long seekToOffset;
    private final OffsetPosition seekToPosition;

    public TopicSubscription(String topicName, Integer partitionId, Long seekToOffset, String seekToPosition) {
        this.topicName = topicName;
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

    public String getTopicName() {
        return topicName;
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
