package org.apache.camel.component.vertx.kafka;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxKafkaProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaProducer.class);

    public VertxKafkaProducer(final VertxKafkaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        return false;
    }

    @Override
    public VertxKafkaEndpoint getEndpoint() {
        return (VertxKafkaEndpoint) super.getEndpoint();
    }

    public VertxKafkaConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
