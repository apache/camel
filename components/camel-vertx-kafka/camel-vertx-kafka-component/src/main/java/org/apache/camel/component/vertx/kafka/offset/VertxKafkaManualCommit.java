package org.apache.camel.component.vertx.kafka.offset;

/**
 * Can be used for forcing manual offset commit when using Kafka consumer.
 */
public interface VertxKafkaManualCommit {

    /**
     * Commit offsets to Kafka
     */
    void commit();
}
