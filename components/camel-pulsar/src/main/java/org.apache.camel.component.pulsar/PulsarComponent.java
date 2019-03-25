package org.apache.camel.component.pulsar;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.impl.DefaultComponent;

import java.util.Map;

public class PulsarComponent extends DefaultComponent {

    private PulsarEndpointConfiguration configuration;
    private AutoConfiguration autoConfiguration;

    public PulsarComponent(CamelContext context, PulsarEndpointConfiguration configuration, AutoConfiguration autoConfiguration) {
        super(context);
        this.configuration = configuration;
        this.autoConfiguration = autoConfiguration;
    }

    public PulsarComponent() {}

    public PulsarComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String path, Map<String, Object> parameters) throws Exception {

        setProperties(configuration, parameters);

        if(autoConfiguration != null) {
            autoConfiguration.ensureNameSpaceAndTenant(path);
        }
        return PulsarEndpoint.create(uri, path, configuration, this);
    }

    public PulsarEndpointConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(PulsarEndpointConfiguration configuration) {
        this.configuration = configuration;
    }

    public AutoConfiguration getAutoConfiguration() {
        return autoConfiguration;
    }

    public void setAutoConfiguration(AutoConfiguration autoConfiguration) {
        this.autoConfiguration = autoConfiguration;
    }
}
