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
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RestApiEndpoint focusing on behavior and error handling.
 */
class RestApiEndpointTest {

    private CamelContext camelContext;
    private RestApiComponent component;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        component = new RestApiComponent();
        component.setCamelContext(camelContext);
        camelContext.addComponent("rest-api", component);
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
    }

    @Test
    void testDefaultExchangePattern() throws Exception {
        RestApiEndpoint endpoint = (RestApiEndpoint) camelContext.getEndpoint("rest-api:api-doc");
        assertThat(endpoint.getExchangePattern()).isEqualTo(ExchangePattern.InOut);
    }

    @Test
    void testIsNotRemote() throws Exception {
        RestApiEndpoint endpoint = (RestApiEndpoint) camelContext.getEndpoint("rest-api:api-doc");
        assertThat(endpoint.isRemote()).isFalse();
    }

    @Test
    void testIsLenientProperties() throws Exception {
        RestApiEndpoint endpoint = (RestApiEndpoint) camelContext.getEndpoint("rest-api:api-doc");
        assertThat(endpoint.isLenientProperties()).isTrue();
    }

    @Test
    void testCreateProducerWithoutFactory() throws Exception {
        RestApiEndpoint endpoint = (RestApiEndpoint) camelContext.getEndpoint("rest-api:api-doc");

        assertThatThrownBy(endpoint::createProducer)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot find RestApiProcessorFactory");
    }

    @Test
    void testCreateConsumerWithoutFactory() throws Exception {
        RestApiEndpoint endpoint = (RestApiEndpoint) camelContext.getEndpoint("rest-api:api-doc");

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot find RestApiConsumerFactory");
    }

    @Test
    void testPathWithLeadingSlash() throws Exception {
        RestApiEndpoint endpoint = (RestApiEndpoint) camelContext.getEndpoint("rest-api:/api-doc");
        assertThat(endpoint.getPath()).isEqualTo("/api-doc");
    }

    @Test
    void testPathWithNestedPath() throws Exception {
        RestApiEndpoint endpoint = (RestApiEndpoint) camelContext.getEndpoint("rest-api:api/v2/doc");
        assertThat(endpoint.getPath()).isEqualTo("api/v2/doc");
    }
}
