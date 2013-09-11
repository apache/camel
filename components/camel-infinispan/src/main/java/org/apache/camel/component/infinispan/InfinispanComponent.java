package org.apache.camel.component.infinispan;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

public class InfinispanComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        InfinispanConfiguration configuration = new InfinispanConfiguration();
        configuration.setHost(remaining);
        setProperties(configuration, parameters);
        return new InfinispanEndpoint(uri, this, configuration);
    }
}
