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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RestProducerAdvancedTest {

    private CamelContext camelContext;
    private RestComponent component;

    @Mock
    private Producer mockProducer;

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
    void testPrepareExchangeWithMultiplePathPlaceholders() throws Exception {
        // The colon is used in the URI syntax as path:uriTemplate, so we test with slashes
        RestEndpoint endpoint
                = (RestEndpoint) camelContext.getEndpoint("rest:get:users/{userId}/orders/{orderId}?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("userId", "123");
        exchange.getMessage().setHeader("orderId", "456");
        producer.prepareExchange(exchange);

        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isEqualTo("http://localhost/users/123/orders/456");
    }

    @Test
    void testPrepareExchangeWithUnresolvedPlaceholder() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users:{userId}?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        // Don't set the header, so placeholder won't be resolved
        producer.prepareExchange(exchange);

        // When placeholder is not resolved, REST_HTTP_URI should not be set
        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isNull();
    }

    @Test
    void testPrepareExchangeWithMalformedPlaceholder() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users:{userId?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("userId", "123");
        producer.prepareExchange(exchange);

        // Malformed placeholder with unclosed brace should not crash
        assertThat(exchange.getException()).isNull();
    }

    @Test
    void testPrepareExchangeWithPathOnly() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        producer.prepareExchange(exchange);

        // No template resolution needed, so no REST_HTTP_URI
        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isNull();
    }

    @Test
    void testPrepareExchangeWithBasePathAndTemplate() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:api/v1:users/{id}?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("id", "999");
        producer.prepareExchange(exchange);

        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isEqualTo("http://localhost/api/v1/users/999");
    }

    @Test
    void testPrepareExchangeWithEmptyQueryParameters() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");
        endpoint.setQueryParameters("");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        producer.prepareExchange(exchange);

        String query = exchange.getMessage().getHeader(RestConstants.REST_HTTP_QUERY, String.class);
        // Empty query parameters result in empty string, not null
        assertThat(query).isEmpty();
    }

    @Test
    void testPrepareExchangePreservesExistingHeaders() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:post:users?host=localhost");
        endpoint.setProduces("application/json");
        endpoint.setConsumes("application/xml");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("Custom-Header", "custom-value");
        producer.prepareExchange(exchange);

        assertThat(exchange.getMessage().getHeader("Custom-Header")).isEqualTo("custom-value");
        assertThat(exchange.getMessage().getHeader(RestConstants.HTTP_METHOD)).isEqualTo("POST");
    }

    @Test
    void testPrepareExchangeWithAllMethodTypes() throws Exception {
        String[] methods = { "get", "post", "put", "delete", "patch", "head", "options", "trace", "connect" };

        for (String method : methods) {
            RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:" + method + ":test?host=localhost");
            endpoint.setParameters(new HashMap<>());

            RestConfiguration config = new RestConfiguration();
            RestProducer producer = new RestProducer(endpoint, mockProducer, config);

            Exchange exchange = new DefaultExchange(camelContext);
            producer.prepareExchange(exchange);

            assertThat(exchange.getMessage().getHeader(RestConstants.HTTP_METHOD))
                    .isEqualTo(method.toUpperCase());
        }
    }

    @Test
    void testPrepareExchangeWithNullMethod() throws Exception {
        RestEndpoint endpoint = new RestEndpoint("rest:null:test", component);
        endpoint.setPath("test");
        endpoint.setMethod(null);
        endpoint.setHost("http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        producer.prepareExchange(exchange);

        // Method is null, so HTTP_METHOD should not be set
        assertThat(exchange.getMessage().getHeader(RestConstants.HTTP_METHOD)).isNull();
    }

    @Test
    void testPrepareExchangeWithNullProducesAndConsumes() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");
        endpoint.setProduces(null);
        endpoint.setConsumes(null);
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        producer.prepareExchange(exchange);

        // No content-type or accept headers should be set
        assertThat(exchange.getMessage().getHeader(RestConstants.CONTENT_TYPE)).isNull();
        assertThat(exchange.getMessage().getHeader(RestConstants.ACCEPT)).isNull();
    }

    @Test
    void testProcessWithExceptionDuringPrepare() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");
        // Set query parameters that will cause an exception during parsing
        endpoint.setQueryParameters("invalid=%%");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        boolean[] callbackDone = { false };

        boolean result = producer.process(exchange, doneSync -> callbackDone[0] = true);

        assertThat(result).isTrue();
        assertThat(callbackDone[0]).isTrue();
        assertThat(exchange.getException()).isNotNull();
    }

    @Test
    void testRestProducerLifecycle() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        config.setBindingMode(RestConfiguration.RestBindingMode.off);
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        // Test lifecycle
        producer.doInit();
        producer.doStart();

        assertThat(producer.getEndpoint()).isSameAs(endpoint);

        producer.doStop();
    }

    @Test
    void testCreateQueryParametersWithTrailingAmpersand() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("a", "1");
        // b is optional and not set

        String result = RestProducer.createQueryParameters("a={a}&b={b?}", exchange);

        assertThat(result).isEqualTo("a=1");
        assertThat(result).doesNotEndWith("&");
    }

    @Test
    void testCreateQueryParametersWithAllOptionalMissing() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        // Don't set any headers

        String result = RestProducer.createQueryParameters("a={a?}&b={b?}&c={c?}", exchange);

        assertThat(result).isEmpty();
    }

    @Test
    void testCreateQueryParametersWithMixedValues() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("a", "value1");
        exchange.setVariable("c", "value3");
        // b is optional and not set

        String result = RestProducer.createQueryParameters("a={a}&b={b?}&c={c}", exchange);

        assertThat(result).contains("a=value1");
        assertThat(result).contains("c=value3");
        assertThat(result).doesNotContain("b=");
    }

    @Test
    void testCreateQueryParametersWithSpecialCharacters() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("name", "John Doe");
        exchange.getMessage().setHeader("email", "john@example.com");

        String result = RestProducer.createQueryParameters("name={name}&email={email}", exchange);

        assertThat(result).contains("name=John+Doe");
        assertThat(result).contains("email=john%40example.com");
    }

    @Test
    void testPrepareExchangeWithLeadingSlashInUriTemplate() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:api:/{id}?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("id", "123");
        producer.prepareExchange(exchange);

        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isEqualTo("http://localhost/api/123");
    }
}
