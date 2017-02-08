package org.apache.camel.swagger;

import java.util.Collections;

import org.apache.camel.component.rest.RestComponent;
import org.apache.camel.component.rest.RestEndpoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SwaggerEndpointConfigurerTest {

    @Test
    public void shouldConfigureRestEndpoints() {
        final SwaggerEndpointConfigurer configurer = new SwaggerEndpointConfigurer();

        final String uri = "rest:swagger:getPetById?apiDoc=petstore.json";
        final RestEndpoint endpoint = new RestEndpoint(uri, new RestComponent());
        endpoint.setApiDoc("petstore.json");

        configurer.configureEndpoint(null, endpoint, uri, "swagger:getPetById", Collections.emptyMap());

        assertEquals("GET", endpoint.getMethod());
        assertEquals("/v2", endpoint.getPath());
        assertEquals("/pet/{petId}", endpoint.getUriTemplate());
    }
}
