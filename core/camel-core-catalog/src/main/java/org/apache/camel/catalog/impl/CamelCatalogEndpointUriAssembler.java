package org.apache.camel.catalog.impl;

import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.spi.EndpointUriAssembler;
import org.apache.camel.spi.annotations.JdkService;

import static org.apache.camel.catalog.RuntimeCamelCatalog.ENDPOINT_URI_ASSEMBLER_FACTORY;

/**
 * Uses {@link RuntimeCamelCatalog} to assemble the endpoint uri.
 */
@JdkService(ENDPOINT_URI_ASSEMBLER_FACTORY)
public class CamelCatalogEndpointUriAssembler implements EndpointUriAssembler {

    @Override
    public String buildUri(CamelContext camelContext, String scheme, Map<String, Object> parameters) {
        try {
            Map<String, String> copy = new LinkedHashMap<>();
            parameters.forEach((k, v) -> copy.put(k, v != null ? v.toString() : null));
            return camelContext.adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog().asEndpointUri(scheme, copy,
                    false);
        } catch (URISyntaxException e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }
}
