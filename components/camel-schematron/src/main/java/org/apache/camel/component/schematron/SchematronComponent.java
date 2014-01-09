package org.apache.camel.component.schematron;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link SchematronEndpoint}.
 */
public class SchematronComponent extends DefaultComponent {


    /**
     * Creates the Schematron Endpoint.
     *
     * @param uri
     * @param remaining
     * @param parameters
     * @return
     * @throws Exception
     */
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new SchematronEndpoint(uri,remaining,this);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
