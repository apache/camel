package org.apache.camel.component.pulsar;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.component.pulsar.utils.TopicTypeUtils;
import org.apache.camel.impl.DefaultComponent;

public class PulsarComponent extends DefaultComponent {

    public PulsarComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final String[] urlComponents = uri.split(":");
        final String urlPath = urlComponents[1];

        final String[] pathVariables = urlPath.split("/");

        final String topicType = TopicTypeUtils.parse(pathVariables[0]);
        final String tenant = pathVariables[1];
        final String nameSpace = pathVariables[2];
        final String topic = pathVariables[3].split("/?")[0];

        PulsarEndpointConfiguration configuration = new PulsarEndpointConfiguration(topicType, tenant, nameSpace, topic);

        setProperties(configuration, parameters);

        return new PulsarEndpoint(configuration, configuration.getPulsarClient());
    }
}
