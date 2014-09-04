package org.apache.camel.pgasync;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link PGAsyncEndpoint}.
 */
public class PGAsyncComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new PGAsyncEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
