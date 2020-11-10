package org.apache.camel.component.vertx.kafka;

public final class VertxKafkaConstants {
    private static final String HEADER_PREFIX = "CamelVertxKafka";
    // common headers, set by the consumer and evaluated by the producer
    public static final String PARTITION_ID = HEADER_PREFIX + "PartitionId";
    public static final String MESSAGE_KEY = HEADER_PREFIX + "MessageKey";
    // headers set by the consumer only
    // headers evaluated by the producer only
    public static final String OVERRIDE_TOPIC = HEADER_PREFIX + "OverrideTopic";

    private VertxKafkaConstants() {
    }
}
