package org.apache.camel.component.nsq;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

import java.util.Map;

import static org.apache.camel.util.IntrospectionSupport.setProperties;

/**
 * Represents the component that manages {@link NsqEndpoint}.
 */
public class NsqComponent extends DefaultComponent {
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NsqConfiguration config = new NsqConfiguration();
        setProperties(config, parameters);
        config.setServers(remaining);

        NsqEndpoint endpoint = new NsqEndpoint(uri, this, config);
        return endpoint;
    }
}
