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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RestComponent focusing on URI parsing and endpoint creation behavior.
 */
class RestComponentTest {

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

    // ==================== URI Parsing Tests ====================

    @Test
    void testCreateEndpointParsesMethodAndPath() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=localhost:8080");

        assertThat(endpoint).isInstanceOf(RestEndpoint.class);

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getMethod()).isEqualTo("get");
        assertThat(restEndpoint.getPath()).isEqualTo("users");
        assertThat(restEndpoint.getUriTemplate()).isNull();
    }

    @Test
    void testCreateEndpointParsesMethodPathAndUriTemplate() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users:{id}?host=localhost:8080");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getMethod()).isEqualTo("get");
        assertThat(restEndpoint.getPath()).isEqualTo("users");
        assertThat(restEndpoint.getUriTemplate()).isEqualTo("{id}");
    }

    @Test
    void testCreateEndpointSupportsAllHttpMethods() throws Exception {
        String[] methods = { "get", "post", "put", "delete", "patch", "head", "options" };

        for (String method : methods) {
            Endpoint endpoint = camelContext.getEndpoint("rest:" + method + ":test?host=localhost");
            RestEndpoint restEndpoint = (RestEndpoint) endpoint;
            assertThat(restEndpoint.getMethod()).isEqualTo(method);
        }
    }

    @Test
    void testCreateEndpointThrowsExceptionForInvalidSyntax() {
        assertThatThrownBy(() -> camelContext.getEndpoint("rest:get"))
                .hasMessageContaining("Invalid syntax");
    }

    @Test
    void testCreateEndpointStripsTrailingSlashFromPath() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users/?host=localhost");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getPath()).isEqualTo("users");
    }

    // ==================== Host Handling Tests ====================

    @Test
    void testCreateEndpointAddsHttpPrefixWhenMissing() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=localhost:8080");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getHost()).isEqualTo("http://localhost:8080");
    }

    @Test
    void testCreateEndpointPreservesHttpScheme() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=http://localhost:8080");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getHost()).isEqualTo("http://localhost:8080");
    }

    @Test
    void testCreateEndpointPreservesHttpsScheme() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=https://localhost:8443");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getHost()).isEqualTo("https://localhost:8443");
    }

    // ==================== Component Property Propagation Tests ====================

    @Test
    void testComponentConsumerNamePropagatestoEndpoint() throws Exception {
        component.setConsumerComponentName("servlet");
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=localhost");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getConsumerComponentName()).isEqualTo("servlet");
    }

    @Test
    void testComponentProducerNamePropagatestoEndpoint() throws Exception {
        component.setProducerComponentName("undertow");
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=localhost");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getProducerComponentName()).isEqualTo("undertow");
    }

    @Test
    void testComponentApiDocPropagatestoEndpoint() throws Exception {
        component.setApiDoc("swagger.json");
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=localhost");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getApiDoc()).isEqualTo("swagger.json");
    }

    // ==================== Endpoint Options Tests ====================

    @Test
    void testCreateEndpointWithConsumesOption() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:post:users?host=localhost&consumes=application/json");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getConsumes()).isEqualTo("application/json");
    }

    @Test
    void testCreateEndpointWithProducesOption() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=localhost&produces=application/xml");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getProduces()).isEqualTo("application/xml");
    }

    @Test
    void testCreateEndpointWithBindingModeOption() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint("rest:get:users?host=localhost&bindingMode=json");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getBindingMode()).isNotNull();
    }

    @Test
    void testCreateEndpointWithInTypeAndOutType() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint(
                "rest:post:users?host=localhost&inType=com.example.User&outType=com.example.UserResponse");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getInType()).isEqualTo("com.example.User");
        assertThat(restEndpoint.getOutType()).isEqualTo("com.example.UserResponse");
    }

    @Test
    void testCreateEndpointWithComponentNames() throws Exception {
        Endpoint endpoint = camelContext.getEndpoint(
                "rest:get:users?host=localhost&consumerComponentName=jetty&producerComponentName=http");

        RestEndpoint restEndpoint = (RestEndpoint) endpoint;
        assertThat(restEndpoint.getConsumerComponentName()).isEqualTo("jetty");
        assertThat(restEndpoint.getProducerComponentName()).isEqualTo("http");
    }
}
