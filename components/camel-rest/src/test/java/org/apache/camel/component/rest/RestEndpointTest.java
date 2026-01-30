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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RestEndpoint focusing on behavior and error handling.
 */
class RestEndpointTest {

    private CamelContext camelContext;
    private RestComponent component;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        component = new RestComponent();
        component.setCamelContext(camelContext);
        camelContext.addComponent("rest", component);
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
    }

    @Test
    void testCreateProducerWithoutHostThrowsIllegalArgumentException() throws Exception {
        RestEndpoint endpoint = new RestEndpoint("rest:get:users", component);
        endpoint.setMethod("get");
        endpoint.setPath("users");
        endpoint.setParameters(new HashMap<>());

        assertThatThrownBy(endpoint::createProducer)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hostname must be configured");
    }

    @Test
    void testCreateConsumerWithoutFactoryThrowsIllegalStateException() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot find RestConsumerFactory");
    }

    @Test
    void testConfigurePropertiesMergesParametersMap() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");

        Map<String, Object> options = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("customKey", "customValue");
        options.put("parameters", params);

        endpoint.configureProperties(options);

        assertThat(endpoint.getParameters()).containsEntry("customKey", "customValue");
    }

    @Test
    void testBindingModeCanBeSetFromString() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost&bindingMode=auto");

        assertThat(endpoint.getBindingMode()).isEqualTo(RestConfiguration.RestBindingMode.auto);
    }

    @Test
    void testBindingModeCanBeSetFromEnum() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");

        endpoint.setBindingMode(RestConfiguration.RestBindingMode.json);
        assertThat(endpoint.getBindingMode()).isEqualTo(RestConfiguration.RestBindingMode.json);

        endpoint.setBindingMode("xml");
        assertThat(endpoint.getBindingMode()).isEqualTo(RestConfiguration.RestBindingMode.xml);
    }

    @Test
    void testHostWithoutSchemeGetsHttpPrefixAdded() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost:8080");

        assertThat(endpoint.getHost()).isEqualTo("http://localhost:8080");
    }

    @Test
    void testHostWithHttpSchemeIsPreserved() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=http://localhost:8080");

        assertThat(endpoint.getHost()).isEqualTo("http://localhost:8080");
    }

    @Test
    void testHostWithHttpsSchemeIsPreserved() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=https://localhost:8443");

        assertThat(endpoint.getHost()).isEqualTo("https://localhost:8443");
    }

    @Test
    void testIsLenientPropertiesReturnsTrue() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");

        assertThat(endpoint.isLenientProperties()).isTrue();
    }
}
