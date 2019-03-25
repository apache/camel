package org.apache.camel.component.pulsar.utils;

enum TopicType {
    PERSISTENT("persistent"),
    NON_PERSISTENT("non-persistent");

    private String topicType;

    TopicType(String topicType) {
        this.topicType = topicType;
    }

    public String getTopicType() {
        return topicType;
    }
}

public final class TopicTypeUtils {

    public static String parse(final String value) {
        if(TopicType.PERSISTENT.getTopicType().equalsIgnoreCase(value)) {
            return TopicType.PERSISTENT.getTopicType();
        } else if(TopicType.NON_PERSISTENT.getTopicType().equalsIgnoreCase(value)) {
            return TopicType.NON_PERSISTENT.getTopicType();
        } else {
            throw new IllegalArgumentException("Invalid topic type - " + value);
        }
    }
}
