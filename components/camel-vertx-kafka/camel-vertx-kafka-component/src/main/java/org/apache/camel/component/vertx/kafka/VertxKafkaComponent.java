package org.apache.camel.component.vertx.kafka;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("vertx-kafka")
public class VertxKafkaComponent extends DefaultComponent {

    @Metadata
    private VertxKafkaConfiguration configuration = new VertxKafkaConfiguration();

    public VertxKafkaComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Topic must be configured on endpoint using syntax kafka:topic");
        }

        final VertxKafkaConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new VertxKafkaConfiguration();

        configuration.setTopic(remaining);

        final VertxKafkaEndpoint endpoint = new VertxKafkaEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        validateConfigurations(configuration);

        return endpoint;
    }

    /**
     * The component configurations
     */
    public VertxKafkaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(VertxKafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    private void validateConfigurations(final VertxKafkaConfiguration configuration) {
        if (ObjectHelper.isEmpty(configuration.getBootstrapServers())) {
            throw new IllegalArgumentException("Kafka bootstrap servers must be configured in 'bootstrapServers' option.");
        }
    }
}
