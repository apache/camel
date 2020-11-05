package org.apache.camel.component.vertx.kafka;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.vertx.kafka.configuration.KafkaConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("vertx-kafka")
public class VertxKafkaComponent extends DefaultComponent {

    @Metadata
    private KafkaConfiguration configuration = new KafkaConfiguration();

    public VertxKafkaComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Topic must be configured on endpoint using syntax kafka:topic");
        }

        final KafkaConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new KafkaConfiguration();

        configuration.setTopic(remaining);

        final VertxKafkaEndpoint endpoint = new VertxKafkaEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        return endpoint;
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
