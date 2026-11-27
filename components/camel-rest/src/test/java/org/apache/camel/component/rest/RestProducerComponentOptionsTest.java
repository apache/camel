/*
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

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.component.EndpointUriFactorySupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The options of the producer component given on the rest endpoint uri are applied to the producer, and are not sent as
 * query parameters, however the producer component is chosen.
 */
class RestProducerComponentOptionsTest {

    private CamelContext context;
    private DummyRestProducerComponent dummy;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        // the generated uri factory of the dummy component, which knows the options of the dummy component
        context.getRegistry().bind("dummyUriFactory", new DummyEndpointUriFactory());
        dummy = new DummyRestProducerComponent();
        context.addComponent("dummy", dummy);
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void testProducerComponentFromEndpoint() throws Exception {
        RestEndpoint endpoint = context.getEndpoint(
                "rest:get:users?host=localhost&producerComponentName=dummy&authPassword=secret&foo=bar",
                RestEndpoint.class);
        assertThat(endpoint.getQueryParameters()).isEqualTo("foo=bar");

        endpoint.createProducer();
        assertThat(dummy.queryParameters).isEqualTo("foo=bar");
        assertThat(dummy.parameters).containsEntry("authPassword", "secret");
    }

    @Test
    void testProducerComponentFromRestConfiguration() throws Exception {
        context.getRestConfiguration().setProducerComponent("dummy");

        RestEndpoint endpoint = context.getEndpoint(
                "rest:get:users?host=localhost&authPassword=secret&foo=bar", RestEndpoint.class);
        assertThat(endpoint.getQueryParameters()).isEqualTo("foo=bar");

        endpoint.createProducer();
        assertThat(dummy.queryParameters).isEqualTo("foo=bar");
        assertThat(dummy.parameters).containsEntry("authPassword", "secret");
    }

    @Test
    void testProducerComponentAutoDetected() throws Exception {
        RestEndpoint endpoint = context.getEndpoint(
                "rest:get:users?host=localhost&authPassword=secret&foo=bar", RestEndpoint.class);

        endpoint.createProducer();
        assertThat(dummy.queryParameters).isEqualTo("foo=bar");
        assertThat(dummy.parameters).containsEntry("authPassword", "secret");
        assertThat(endpoint.getQueryParameters()).isEqualTo("foo=bar");
    }

    @Test
    void testOnlyProducerOptions() throws Exception {
        RestEndpoint endpoint = context.getEndpoint(
                "rest:get:users?host=localhost&authPassword=secret", RestEndpoint.class);

        endpoint.createProducer();
        assertThat(dummy.queryParameters).isNull();
        assertThat(endpoint.getQueryParameters()).isNull();
    }

    @Test
    void testExplicitQueryParametersAreKept() throws Exception {
        RestEndpoint endpoint = context.getEndpoint(
                "rest:get:users?host=localhost&queryParameters=RAW(page=1&authPassword=given)&authPassword=secret&foo=bar",
                RestEndpoint.class);

        endpoint.createProducer();
        assertThat(dummy.queryParameters).isEqualTo("foo=bar&page=1&authPassword=given");
        assertThat(dummy.parameters).containsEntry("authPassword", "secret");
    }

    private static final class DummyRestProducerComponent extends DefaultComponent implements RestProducerFactory {

        private String queryParameters;
        private Map<String, Object> parameters;

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Producer createProducer(
                CamelContext camelContext, String host, String verb, String basePath, String uriTemplate,
                String queryParameters, String consumes, String produces, RestConfiguration configuration,
                Map<String, Object> parameters) {
            this.queryParameters = queryParameters;
            this.parameters = parameters;
            return mock(Producer.class);
        }
    }

    private static final class DummyEndpointUriFactory extends EndpointUriFactorySupport {

        @Override
        public boolean isEnabled(String scheme) {
            return "dummy".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) {
            return scheme;
        }

        @Override
        public Set<String> propertyNames() {
            return Set.of("authPassword", "authUsername");
        }

        @Override
        public Set<String> secretPropertyNames() {
            return Set.of("authPassword", "authUsername");
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Map.of();
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }
    }
}
