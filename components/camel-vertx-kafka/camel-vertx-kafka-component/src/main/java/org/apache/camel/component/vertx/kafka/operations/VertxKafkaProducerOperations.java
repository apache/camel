package org.apache.camel.component.vertx.kafka.operations;

import io.vertx.kafka.client.producer.KafkaProducer;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxKafkaProducerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaProducerOperations.class);

    private final KafkaProducer<Object, Object> kafkaProducer;
    private final VertxKafkaConfiguration configuration;

    public VertxKafkaProducerOperations(final KafkaProducer<Object, Object> kafkaProducer,
                                        final VertxKafkaConfiguration configuration) {
        ObjectHelper.notNull(kafkaProducer, "kafkaProducer");
        ObjectHelper.notNull(configuration, "configuration");

        this.kafkaProducer = kafkaProducer;
        this.configuration = configuration;
    }

    public boolean sendEvents(final Exchange exchange, final AsyncCallback callback) {
        ObjectHelper.notNull(exchange, "exchange");
        ObjectHelper.notNull(callback, "callback");

        return true;
    }
}
