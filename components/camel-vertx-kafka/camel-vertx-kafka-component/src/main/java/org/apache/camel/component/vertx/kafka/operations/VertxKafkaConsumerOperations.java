package org.apache.camel.component.vertx.kafka.operations;

import java.util.function.Consumer;

import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.camel.component.vertx.kafka.VertxKafkaConfigurationOptionsProxy;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxKafkaConsumerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaConsumerOperations.class);

    private final KafkaConsumer<Object, Object> kafkaConsumer;
    private final VertxKafkaConfigurationOptionsProxy configurationOptionsProxy;

    public VertxKafkaConsumerOperations(final KafkaConsumer<Object, Object> kafkaConsumer,
                                        final VertxKafkaConfiguration configuration) {
        this.kafkaConsumer = kafkaConsumer;
        this.configurationOptionsProxy = new VertxKafkaConfigurationOptionsProxy(configuration);
    }

    public void receiveEvents(
            final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler, final Consumer<Throwable> errorHandler) {
        // register our exception
        registerExceptionHandler(errorHandler);

        // subscribe to topic or partition
        subscribeToTopicOrPartition(errorHandler);

        // add our record handler
        registerRecordHandler(recordHandler);
    }

    private void registerRecordHandler(final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler) {
        // here we can handle the commits by ourselves if needed plus tracking the offsets
        kafkaConsumer.handler(recordHandler::accept);
    }

    private void registerExceptionHandler(final Consumer<Throwable> errorHandler) {
        kafkaConsumer.exceptionHandler(errorHandler::accept);
    }

    private void subscribeToTopicOrPartition(final Consumer<Throwable> errorHandler) {
        kafkaConsumer.subscribe(configurationOptionsProxy.getTopic(null), result -> {
            if (result.failed()) {
                LOG.debug("Subscription error: " + result.cause().getMessage());
                errorHandler.accept(result.cause());
            }
        });
    }
}
