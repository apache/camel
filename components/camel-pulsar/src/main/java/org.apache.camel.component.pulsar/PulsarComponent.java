package org.apache.camel.component.pulsar;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.impl.DefaultComponent;

public class PulsarComponent extends DefaultComponent {

    private final PulsarEndpointConfiguration configuration;

    public PulsarComponent(CamelContext context, PulsarEndpointConfiguration configuration) {
        super(context);
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String path, Map<String, Object> parameters) throws Exception {

        setProperties(configuration, parameters);

        return PulsarEndpoint.create(uri, path, configuration, this);
    }
}
