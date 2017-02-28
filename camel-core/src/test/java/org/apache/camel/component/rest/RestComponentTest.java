/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.rest;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestEndpointConfigurer;
import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class RestComponentTest {

    public static class TestRestConfigurer implements RestEndpointConfigurer {
        @Override
        public void configureEndpoint(final CamelContext context, final RestEndpoint endpoint, final String uri,
                final String remaining, final Map<String, Object> parameters) {

            endpoint.setMethod("get");
            endpoint.setPath("/base");
            endpoint.setUriTemplate("/cats/{id}");
        }

    }

    RestComponent rest = new RestComponent();

    CamelContext context = new DefaultCamelContext(new SimpleRegistry());

    final RestConfiguration restConfiguration = createRestConfiguration();

    static RestConfiguration createRestConfiguration() {
        final RestConfiguration restConfiguration = new RestConfiguration();
        restConfiguration.setHost("host-from-configuration");
        restConfiguration.setPort(4242);

        return restConfiguration;
    }

    @Test
    public void shouldCreateEndpointFromConfigurer() throws Exception {
        context.setRestConfiguration(restConfiguration);

        rest.setCamelContext(context);

        rest.setApiDoc("definition.file");

        final Endpoint endpoint = rest.createEndpoint("rest:test:getCatsById", "test:getCatsById", new HashMap<>());

        assertThat(endpoint, instanceOf(RestEndpoint.class));

        final RestEndpoint restEndpoint = (RestEndpoint) endpoint;

        assertEquals("rest:test:getCatsById", restEndpoint.getEndpointUri());
        assertEquals("http://host-from-configuration:4242", restEndpoint.getHost());
        assertNull(restEndpoint.getQueryParameters());
        assertEquals("get", restEndpoint.getMethod());
        assertEquals("/base", restEndpoint.getPath());
        assertEquals("/cats/{id}", restEndpoint.getUriTemplate());
    }

    @Test
    public void shouldCreateEndpointWithHostParameterReference() throws Exception {
        final SimpleRegistry registry = context.getRegistry(SimpleRegistry.class);
        registry.put("hostname-ref", "hostname-from-parameter");
        rest.setCamelContext(context);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("routeId", "route1");
        parameters.put("host", "#hostname-ref");

        final Endpoint endpoint = rest.createEndpoint(
                "rest://get:/say/hello:{template}?routeId=route1&host=#hostname-ref", "get:/say/hello:{template}",
                parameters);

        assertThat(endpoint, instanceOf(RestEndpoint.class));

        final RestEndpoint restEndpoint = (RestEndpoint) endpoint;

        assertEquals("rest://get:/say/hello:{template}?routeId=route1&host=#hostname-ref",
                restEndpoint.getEndpointUri());
        assertEquals("http://hostname-from-parameter", restEndpoint.getHost());
        assertEquals("routeId=route1&host=#hostname-ref", restEndpoint.getQueryParameters());
        assertEquals("get", restEndpoint.getMethod());
        assertEquals("/say/hello", restEndpoint.getPath());
        assertEquals("{template}", restEndpoint.getUriTemplate());
    }

    @Test
    public void shouldCreateEndpointWithoutRestConfiguration() throws Exception {
        rest.setCamelContext(context);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("routeId", "route1");

        final Endpoint endpoint = rest.createEndpoint("rest://get:/say/hello:{template}?routeId=route1",
                "get:/say/hello:{template}", parameters);

        assertThat(endpoint, instanceOf(RestEndpoint.class));

        final RestEndpoint restEndpoint = (RestEndpoint) endpoint;

        assertEquals("rest://get:/say/hello:{template}?routeId=route1", restEndpoint.getEndpointUri());
        assertNull(restEndpoint.getHost());
        assertEquals("routeId=route1", restEndpoint.getQueryParameters());
        assertEquals("get", restEndpoint.getMethod());
        assertEquals("/say/hello", restEndpoint.getPath());
        assertEquals("{template}", restEndpoint.getUriTemplate());
    }

    @Test
    public void shouldCreateEndpointWithoutTemplate() throws Exception {
        rest.setCamelContext(context);

        final Map<String, Object> parameters = new HashMap<>();

        final Endpoint endpoint = rest.createEndpoint("rest://get:/say/hello", "get:/say/hello", parameters);

        assertThat(endpoint, instanceOf(RestEndpoint.class));

        final RestEndpoint restEndpoint = (RestEndpoint) endpoint;

        assertEquals("rest://get:/say/hello", restEndpoint.getEndpointUri());
        assertNull(restEndpoint.getHost());
        assertNull(restEndpoint.getQueryParameters());
        assertEquals("get", restEndpoint.getMethod());
        assertEquals("/say/hello", restEndpoint.getPath());
        assertNull(restEndpoint.getUriTemplate());
    }

    @Test
    public void shouldCreateEndpointWithPropertiesFromUri() throws Exception {
        context.setRestConfiguration(restConfiguration);

        rest.setCamelContext(context);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("routeId", "route1");

        final Endpoint endpoint = rest.createEndpoint("rest://get:/say/hello:{template}?routeId=route1",
                "get:/say/hello:{template}", parameters);

        assertThat(endpoint, instanceOf(RestEndpoint.class));

        final RestEndpoint restEndpoint = (RestEndpoint) endpoint;

        assertEquals("rest://get:/say/hello:{template}?routeId=route1", restEndpoint.getEndpointUri());
        assertEquals("http://host-from-configuration:4242", restEndpoint.getHost());
        assertEquals("routeId=route1", restEndpoint.getQueryParameters());
        assertEquals("get", restEndpoint.getMethod());
        assertEquals("/say/hello", restEndpoint.getPath());
        assertEquals("{template}", restEndpoint.getUriTemplate());
    }
}
