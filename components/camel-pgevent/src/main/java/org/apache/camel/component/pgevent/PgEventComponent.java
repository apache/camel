package org.apache.camel.component.pgevent;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link PgEventEndpoint}.
 */
public class PgEventComponent extends UriEndpointComponent {

    public PgEventComponent(CamelContext context, Class<? extends Endpoint> endpointClass) {
        super(context, endpointClass);
    }

    public PgEventComponent(Class<? extends Endpoint> endpointClass) {
        super(endpointClass);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new PgEventEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
