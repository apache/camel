package org.apache.camel.component.pulsar;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.impl.DefaultComponent;

import java.util.Map;

public class PulsarComponent extends DefaultComponent {

    public PulsarComponent() {
    }

    PulsarComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String path, final Map<String, Object> parameters) throws Exception {
        final PulsarEndpointConfiguration configuration = new PulsarEndpointConfiguration();
        final AutoConfiguration autoConfiguration = new AutoConfiguration();

        setProperties(configuration, parameters);
        setProperties(autoConfiguration, parameters);

        if (autoConfiguration.getPulsarAdmin() != null) {
            autoConfiguration.ensureNameSpaceAndTenant(path);
        }

        return PulsarEndpoint.create(uri, path, configuration, this);
    }
}
