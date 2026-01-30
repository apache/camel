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

import java.net.URISyntaxException;
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

/**
 * Tests for RestProducer focusing on query parameter resolution and exchange preparation.
 */
@ExtendWith(MockitoExtension.class)
class RestProducerTest {

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

    // ==================== Query Parameter Resolution Tests ====================

    @Test
    void testCreateQueryParametersReturnsUnmodifiedQueryWhenNoPlaceholders() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);

        String result = RestProducer.createQueryParameters("page=1&size=10", exchange);

        assertThat(result).isEqualTo("page=1&size=10");
    }

    @Test
    void testCreateQueryParametersResolvesPlaceholderFromHeader() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("userId", "123");

        String result = RestProducer.createQueryParameters("id={userId}", exchange);

        assertThat(result).isEqualTo("id=123");
    }

    @Test
    void testCreateQueryParametersResolvesMultiplePlaceholders() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("page", "2");
        exchange.getMessage().setHeader("size", "50");

        String result = RestProducer.createQueryParameters("page={page}&size={size}", exchange);

        assertThat(result).isEqualTo("page=2&size=50");
    }

    @Test
    void testCreateQueryParametersRemovesOptionalPlaceholderWhenNotSet() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);

        String result = RestProducer.createQueryParameters("filter={filter?}", exchange);

        assertThat(result).isEmpty();
    }

    @Test
    void testCreateQueryParametersResolvesOptionalPlaceholderWhenSet() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("filter", "active");

        String result = RestProducer.createQueryParameters("filter={filter?}", exchange);

        assertThat(result).isEqualTo("filter=active");
    }

    @Test
    void testCreateQueryParametersHandlesMixedOptionalAndRequired() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("page", "1");
        // filter is optional and not set

        String result = RestProducer.createQueryParameters("page={page}&filter={filter?}", exchange);

        assertThat(result).isEqualTo("page=1");
    }

    @Test
    void testCreateQueryParametersFallsBackToVariable() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setVariable("orderId", "456");

        String result = RestProducer.createQueryParameters("order={orderId}", exchange);

        assertThat(result).isEqualTo("order=456");
    }

    @Test
    void testCreateQueryParametersReturnsNullForNullInput() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);

        String result = RestProducer.createQueryParameters(null, exchange);

        assertThat(result).isNull();
    }

    @Test
    void testCreateQueryParametersHandlesUrlEncodedPlaceholder() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("name", "John Doe");

        String result = RestProducer.createQueryParameters("name=%7Bname%7D", exchange);

        assertThat(result).isEqualTo("name=John+Doe");
    }

    @Test
    void testCreateQueryParametersKeepsUnresolvedRequiredPlaceholder() throws URISyntaxException {
        Exchange exchange = new DefaultExchange(camelContext);

        String result = RestProducer.createQueryParameters("id={userId}", exchange);

        assertThat(result).isEqualTo("id=%7BuserId%7D");
    }

    // ==================== PrepareExchange Tests ====================

    @Test
    void testPrepareExchangeSetsHttpMethodHeader() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:post:users?host=localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        producer.prepareExchange(exchange);

        assertThat(exchange.getMessage().getHeader(RestConstants.HTTP_METHOD)).isEqualTo("POST");
    }

    @Test
    void testPrepareExchangeSetsContentTypeFromProduces() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:post:users?host=localhost");
        endpoint.setProduces("application/json");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        producer.prepareExchange(exchange);

        assertThat(exchange.getMessage().getHeader(RestConstants.CONTENT_TYPE)).isEqualTo("application/json");
    }

    @Test
    void testPrepareExchangeSetsAcceptFromConsumes() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");
        endpoint.setConsumes("application/xml");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        producer.prepareExchange(exchange);

        assertThat(exchange.getMessage().getHeader(RestConstants.ACCEPT)).isEqualTo("application/xml");
    }

    @Test
    void testPrepareExchangeDoesNotOverrideExistingContentType() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:post:users?host=localhost");
        endpoint.setProduces("application/json");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "text/plain");
        producer.prepareExchange(exchange);

        assertThat(exchange.getMessage().getHeader(RestConstants.CONTENT_TYPE)).isEqualTo("text/plain");
    }

    @Test
    void testPrepareExchangeDoesNotOverrideExistingAccept() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");
        endpoint.setConsumes("application/xml");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(RestConstants.ACCEPT, "text/html");
        producer.prepareExchange(exchange);

        assertThat(exchange.getMessage().getHeader(RestConstants.ACCEPT)).isEqualTo("text/html");
    }

    @Test
    void testPrepareExchangeResolvesUriTemplateFromHeader() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users:{id}?host=http://localhost:8080");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("id", "123");
        producer.prepareExchange(exchange);

        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isEqualTo("http://localhost:8080/users/123");
    }

    @Test
    void testPrepareExchangeResolvesUriTemplateFromVariable() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:orders:{orderId}?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setVariable("orderId", "789");
        producer.prepareExchange(exchange);

        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isEqualTo("http://localhost/orders/789");
    }

    @Test
    void testPrepareExchangeSetsQueryParametersHeader() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");
        endpoint.setQueryParameters("page=1&size=10");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        producer.prepareExchange(exchange);

        String query = exchange.getMessage().getHeader(RestConstants.REST_HTTP_QUERY, String.class);
        assertThat(query).isEqualTo("page=1&size=10");
    }

    @Test
    void testPrepareExchangeResolvesDynamicQueryParameters() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");
        endpoint.setQueryParameters("userId={id}");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("id", "456");
        producer.prepareExchange(exchange);

        String query = exchange.getMessage().getHeader(RestConstants.REST_HTTP_QUERY, String.class);
        assertThat(query).isEqualTo("userId=456");
    }

    @Test
    void testPrepareExchangeRemovesHttpPathWhenUriIsSet() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users:{id}?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("id", "123");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/old/path");
        producer.prepareExchange(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_PATH)).isNull();
    }

    @Test
    void testPrepareExchangeCombinesPathAndUriTemplate() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:api:users/{id}?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("id", "999");
        producer.prepareExchange(exchange);

        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isEqualTo("http://localhost/api/users/999");
    }

    @Test
    void testPrepareExchangeDoesNotResolveTemplateWhenPrepareUriTemplateIsFalse() throws Exception {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users:{id}?host=http://localhost");
        endpoint.setParameters(new HashMap<>());

        RestConfiguration config = new RestConfiguration();
        RestProducer producer = new RestProducer(endpoint, mockProducer, config);
        producer.setPrepareUriTemplate(false);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader("id", "123");
        producer.prepareExchange(exchange);

        String uri = exchange.getMessage().getHeader(RestConstants.REST_HTTP_URI, String.class);
        assertThat(uri).isNull();
    }
}
