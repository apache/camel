package org.apache.camel.component.vertx.kafka;

import org.apache.camel.Processor;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxKafkaConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaConsumer.class);

    public VertxKafkaConsumer(final VertxKafkaEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    public VertxKafkaConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public VertxKafkaEndpoint getEndpoint() {
        return (VertxKafkaEndpoint) super.getEndpoint();
    }

}
