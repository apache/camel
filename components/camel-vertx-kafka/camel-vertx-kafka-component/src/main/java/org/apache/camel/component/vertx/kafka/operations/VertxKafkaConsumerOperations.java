package org.apache.camel.component.vertx.kafka.operations;

import io.vertx.kafka.client.consumer.KafkaConsumer;
import org.apache.camel.component.vertx.kafka.VertxKafkaConfigurationOptionsProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxKafkaConsumerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaConsumerOperations.class);

    private final KafkaConsumer<Object, Object> kafkaConsumer;
    private final VertxKafkaConfigurationOptionsProxy configurationOptionsProxy;

    public VertxKafkaConsumerOperations(final KafkaConsumer<Object, Object> kafkaConsumer, final VertxKafkaConfigurationOptionsProxy vertxKafkaConfigurationOptionsProxy) {
        this.kafkaConsumer = kafkaConsumer;
        this.configurationOptionsProxy = vertxKafkaConfigurationOptionsProxy;
    }
}
