package org.apache.camel.component.vertx.kafka;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.vertx.kafka.configuration.KafkaConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Sent and receive messages to/from an Apache Kafka broker using vert.x Kafka client
 */
@UriEndpoint(firstVersion = "3.7.0", scheme = "vertx-kafka", title = "Vert.x Kafka", syntax = "vertx-kafka:topic",
             category = { Category.MESSAGING })
public class VertxKafkaEndpoint extends DefaultEndpoint {

    @UriParam
    private KafkaConfiguration configuration = new KafkaConfiguration();

    public VertxKafkaEndpoint() {
    }

    public VertxKafkaEndpoint(final String uri, final Component component, final KafkaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return null;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return null;
    }

    /**
     * The component configurations
     */
    public KafkaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(KafkaConfiguration configuration) {
        this.configuration = configuration;
    }
}
