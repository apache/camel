package org.apache.camel.component.pulsar;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.impl.DefaultComponent;

public class PulsarComponent extends DefaultComponent {

    public PulsarComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        PulsarEndpointConfiguration configuration = new PulsarEndpointConfiguration(new PulsarUri(uri));

        setProperties(configuration, parameters);

        return PulsarEndpoint.create(configuration, configuration.getPulsarClient(), uri, this);
    }


}
