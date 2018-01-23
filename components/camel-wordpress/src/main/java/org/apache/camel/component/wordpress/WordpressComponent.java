package org.apache.camel.component.wordpress;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.wordpress.config.WordpressComponentConfiguration;
import org.apache.camel.component.wordpress.config.WordpressEndpointConfiguration;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IntrospectionSupport;

/**
 * Represents the component that manages {@link WordpressEndpoint}.
 */
public class WordpressComponent extends DefaultComponent {

    private static final String OP_SEPARATOR = ":";

    @Metadata(label = "advanced", description = "Wordpress component configuration")
    private WordpressComponentConfiguration configuration;

    public WordpressComponent() {
        this(new WordpressComponentConfiguration());
    }

    public WordpressComponent(WordpressComponentConfiguration configuration) {
        this.configuration = configuration;
    }

    public WordpressComponent(CamelContext camelContext) {
        super(camelContext);
        this.configuration = new WordpressComponentConfiguration();
    }

    public WordpressComponentConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(WordpressComponentConfiguration configuration) {
        this.configuration = configuration;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final WordpressEndpointConfiguration endpointConfiguration = this.copyComponentProperties();

        WordpressEndpoint endpoint = new WordpressEndpoint(uri, this, endpointConfiguration);
        setProperties(endpoint, parameters);

        this.discoverOperations(endpoint, remaining);
        endpoint.configureProperties(parameters);

        return endpoint;
    }

    private void discoverOperations(WordpressEndpoint endpoint, String remaining) {
        final String[] operations = remaining.split(OP_SEPARATOR);
        endpoint.setOperation(operations[0]);
        if (operations.length > 1) {
            endpoint.setOperationDetail(operations[1]);
        }
    }

    private WordpressEndpointConfiguration copyComponentProperties() throws Exception {
        Map<String, Object> componentProperties = new HashMap<String, Object>();
        IntrospectionSupport.getProperties(configuration, componentProperties, null, false);

        // create endpoint configuration with component properties
        WordpressEndpointConfiguration config = new WordpressEndpointConfiguration();
        IntrospectionSupport.setProperties(config, componentProperties);
        return config;
    }
}
