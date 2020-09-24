package org.apache.camel.catalog.impl;

import java.net.URISyntaxException;
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
    public String buildUri(CamelContext camelContext, String scheme, Map<String, String> parameters) {
        try {
            return camelContext.adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog().asEndpointUri(scheme, parameters,
                    false);
        } catch (URISyntaxException e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }
}
