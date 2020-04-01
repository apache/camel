package org.apache.camel.component.djl;

import java.util.Map;

import org.apache.camel.Endpoint;

import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the component that manages {@link DJLEndpoint}.
 */
@Component("djl")
public class DJLComponent extends DefaultComponent {
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Application must be configured on endpoint using syntax djl:application");
        }
        Endpoint endpoint = new DJLEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
