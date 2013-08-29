package org.apache.camel.facebook;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.facebook.config.FacebookConfiguration;
import org.apache.camel.facebook.config.FacebookEndpointConfiguration;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IntrospectionSupport;

/**
 * Represents the component that manages {@link FacebookEndpoint}.
 */
public class FacebookComponent extends UriEndpointComponent {

    @UriParam
    private FacebookConfiguration configuration;

    public FacebookComponent() {
        this(new FacebookConfiguration());
    }

    public FacebookComponent(FacebookConfiguration configuration) {
        this(null, configuration);
    }

    public FacebookComponent(CamelContext context) {
        this(context, new FacebookConfiguration());
    }

    public FacebookComponent(CamelContext context, FacebookConfiguration configuration) {
        super(context, FacebookEndpoint.class);
        this.configuration = configuration;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        FacebookEndpointConfiguration config = copyComponentProperties();
        return new FacebookEndpoint(uri, this, remaining, config);
    }

    private FacebookEndpointConfiguration copyComponentProperties() throws Exception {
        Map<String, Object> componentProperties = new HashMap<String, Object>();
        IntrospectionSupport.getProperties(configuration, componentProperties, null, false);

        // create endpoint configuration with component properties
        FacebookEndpointConfiguration config = new FacebookEndpointConfiguration();
        IntrospectionSupport.setProperties(config, componentProperties, null);
        return config;
    }

    public FacebookConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(FacebookConfiguration configuration) {
        this.configuration = configuration;
    }

}
