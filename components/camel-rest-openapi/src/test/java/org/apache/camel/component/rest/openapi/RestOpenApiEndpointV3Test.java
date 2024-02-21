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
package org.apache.camel.component.rest.openapi;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.spi.RestConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestOpenApiEndpointV3Test {

    URI componentJsonUri = URI.create("component.json");

    URI endpointUri = URI.create("endpoint.json");

    @Test
    public void shouldComplainForUnknownOperations() {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        final RestOpenApiComponent component = new RestOpenApiComponent(camelContext);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:unknown", "unknown", component,
                Collections.emptyMap());

        assertThrows(IllegalArgumentException.class,
                () -> endpoint.createProducer());
    }

    @Test
    public void shouldComputeQueryParameters() {
        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("literal", "value");
        assertThat(endpoint.queryParameter(new Parameter())).isEqualTo("");
        assertThat(endpoint.queryParameter(new Parameter().name("param"))).isEqualTo("param={param?}");
        assertThat(endpoint.queryParameter(new Parameter().name("literal"))).isEqualTo("literal=value");
    }

    @Test
    public void shouldCreateQueryParameterExpressions() {
        Parameter oas30Parameter = new Parameter().name("q").required(true);
        assertThat(RestOpenApiEndpoint.queryParameterExpression(oas30Parameter))
                .isEqualTo("q={q}");
        oas30Parameter = new Parameter().name("q").required(false);
        assertThat(RestOpenApiEndpoint.queryParameterExpression(oas30Parameter))
                .isEqualTo("q={q?}");
    }

    @Test
    public void shouldDetermineBasePath() {
        final RestConfiguration restConfiguration = new RestConfiguration();

        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getRestConfiguration()).thenReturn(restConfiguration);

        final OpenAPI openapi = new OpenAPI();

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setCamelContext(camelContext);
        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:getPetById", "getPetById", component,
                Collections.emptyMap());

        assertThat(endpoint.determineBasePath(openapi))
                .as("When no base path is specified on component, endpoint or rest configuration it should default to `/`")
                .isEqualTo("/");

        restConfiguration.setContextPath("/rest");
        assertThat(endpoint.determineBasePath(openapi)).as(
                "When base path is specified in REST configuration and not specified in component the base path should be from the REST configuration")
                .isEqualTo("/rest");

        openapi.addServersItem(new Server().url("http://petstore.openapi.io").description("v3 test"));

        component.setBasePath("/component");
        assertThat(endpoint.determineBasePath(openapi)).as(
                "When base path is specified on the component it should take precedence over OpenApi specification and REST configuration")
                .isEqualTo("/component");

        endpoint.setBasePath("/endpoint");
        assertThat(endpoint.determineBasePath(openapi))
                .as("When base path is specified on the endpoint it should take precedence over any other")
                .isEqualTo("/endpoint");
    }

    @Test
    public void shouldDetermineEndpointParameters() {
        final CamelContext camelContext = mock(CamelContext.class);

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setCamelContext(camelContext);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "uri", "remaining", component,
                Collections.emptyMap());
        endpoint.setHost("http://petstore.openapi.io");

        final OpenAPI openapi = new OpenAPI();
        final Operation operation = new Operation().operationId("get");
        assertThat(endpoint.determineEndpointParameters(openapi, operation))
                .containsOnly(entry("host", "http://petstore.openapi.io"));

        component.setComponentName("xyz");
        assertThat(endpoint.determineEndpointParameters(openapi, operation))
                .containsOnly(entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "xyz"));

        List<String> consumers = new ArrayList<String>();
        consumers.add("application/json");
        List<String> produces = new ArrayList<String>();
        produces.add("application/xml");
        //        operation.requestBody = operation.createRequestBody();
        //        operation.responses = operation.createResponses();
        //        operation.responses.addResponse("200", operation.responses.createResponse("200"));
        for (String consumer : consumers) {
            operation.requestBody(new RequestBody().content(new Content().addMediaType(consumer, new MediaType()))); // MediaType object should be schema?
        }
        ApiResponses apiResponses = new ApiResponses();
        operation.responses(apiResponses);
        // Can only have one content per response...
        for (String produce : produces) {
            apiResponses.addApiResponse("200", new ApiResponse().content(new Content().addMediaType(produce, new MediaType())));
        }

        assertThat(endpoint.determineEndpointParameters(openapi, operation)).containsOnly(
                entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "xyz"),
                entry("consumes", "application/xml"), entry("produces", "application/json"));

        component.setProduces("application/json");
        component.setConsumes("application/atom+xml");
        assertThat(endpoint.determineEndpointParameters(openapi, operation)).containsOnly(
                entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "xyz"),
                entry("consumes", "application/atom+xml"), entry("produces", "application/json"));

        endpoint.setProduces("application/atom+xml");
        endpoint.setConsumes("application/json");
        assertThat(endpoint.determineEndpointParameters(openapi, operation)).containsOnly(
                entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "xyz"),
                entry("consumes", "application/json"), entry("produces", "application/atom+xml"));

        endpoint.setComponentName("zyx");
        assertThat(endpoint.determineEndpointParameters(openapi, operation)).containsOnly(
                entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "zyx"),
                entry("consumes", "application/json"), entry("produces", "application/atom+xml"));

        Parameter parameter1 = new Parameter().name("q").in("query").required(true);
        operation.addParametersItem(parameter1);
        assertThat(endpoint.determineEndpointParameters(openapi, operation)).containsOnly(
                entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "zyx"),
                entry("consumes", "application/json"), entry("produces", "application/atom+xml"),
                entry("queryParameters", "q={q}"));

        Parameter parameter2 = new Parameter().name("o").in("query");
        operation.addParametersItem(parameter2);
        assertThat(endpoint.determineEndpointParameters(openapi, operation)).containsOnly(
                entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "zyx"),
                entry("consumes", "application/json"), entry("produces", "application/atom+xml"),
                entry("queryParameters", "q={q}&o={o?}"));
    }

    @Test
    public void shouldDetermineHostFromRestConfiguration() {
        assertThat(RestOpenApiEndpoint.hostFrom(null)).isNull();

        final RestConfiguration configuration = new RestConfiguration();
        assertThat(RestOpenApiEndpoint.hostFrom(configuration)).isNull();

        configuration.setScheme("ftp");
        assertThat(RestOpenApiEndpoint.hostFrom(configuration)).isNull();

        configuration.setScheme("http");
        assertThat(RestOpenApiEndpoint.hostFrom(configuration)).isNull();

        configuration.setHost("petstore.openapi.io");
        assertThat(RestOpenApiEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.openapi.io");

        configuration.setPort(80);
        assertThat(RestOpenApiEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.openapi.io");

        configuration.setPort(8080);
        assertThat(RestOpenApiEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.openapi.io:8080");

        configuration.setScheme("https");
        configuration.setPort(80);
        assertThat(RestOpenApiEndpoint.hostFrom(configuration)).isEqualTo("https://petstore.openapi.io:80");

        configuration.setPort(443);
        assertThat(RestOpenApiEndpoint.hostFrom(configuration)).isEqualTo("https://petstore.openapi.io");
    }

    @Test
    public void shouldDetermineHostFromSpecification() {
        final RestOpenApiComponent component = new RestOpenApiComponent();

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:http://some-uri#getPetById",
                "http://some-uri#getPetById", component, Collections.emptyMap());

        final OpenAPI openapi = new OpenAPI();
        openapi.addServersItem(new Server().url("http://petstore.openapi.io").description("v3 test"));
        openapi.addServersItem(new Server().url("http://anotherpetstore.openapi.io").description("v3 test"));
        Operation dummyOp = new Operation();
        assertThat(endpoint.determineHost(openapi, dummyOp)).isEqualTo("http://petstore.openapi.io");

        openapi.getServers().clear();
        openapi.addServersItem(new Server().url("https://petstore.openapi.io").description("v3 test"));
        assertThat(endpoint.determineHost(openapi, dummyOp)).isEqualTo("https://petstore.openapi.io");
    }

    @Test
    public void shouldDetermineOptions() {
        assertThat(RestOpenApiEndpoint.determineOption(null, null, null, null)).isNull();

        assertThat(RestOpenApiEndpoint.determineOption(Collections.emptyList(), Collections.emptySet(), "", ""))
                .isNull();

        assertThat(RestOpenApiEndpoint.determineOption(Arrays.asList("specification"), null, null, null))
                .isEqualTo("specification");

        assertThat(
                RestOpenApiEndpoint.determineOption(Arrays.asList("specification"), Collections.singleton("operation"), null,
                        null))
                .isEqualTo("operation");

        assertThat(RestOpenApiEndpoint.determineOption(Arrays.asList("specification"), Collections.singleton("operation"),
                "component", null)).isEqualTo("component");

        assertThat(RestOpenApiEndpoint.determineOption(Arrays.asList("specification"), Collections.singleton("operation"),
                "component", "operation")).isEqualTo("operation");
    }

    @Test
    public void shouldHonourComponentSpecificationPathProperty() {
        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setSpecificationUri(componentJsonUri);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:getPetById", "getPetById", component,
                Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(componentJsonUri);
    }

    @Test
    public void shouldHonourEndpointUriPathSpecificationPathProperty() {
        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setSpecificationUri(componentJsonUri);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:endpoint.json#getPetById",
                "endpoint.json#getPetById", component, Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(endpointUri);
    }

    @Test
    public void shouldHonourHostPrecedence() {
        final RestConfiguration globalRestConfiguration = new RestConfiguration();

        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getRestConfiguration()).thenReturn(globalRestConfiguration);

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setCamelContext(camelContext);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "petstore:http://specification-uri#getPetById",
                "http://specification-uri#getPetById", component, Collections.emptyMap());

        final OpenAPI openapi = new OpenAPI();
        Operation dummyOp = new Operation();
        assertThat(endpoint.determineHost(openapi, dummyOp)).isEqualTo("http://specification-uri");

        globalRestConfiguration.setHost("global-rest");
        globalRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(openapi, dummyOp)).isEqualTo("http://global-rest");

        globalRestConfiguration.setHost("component-rest");
        globalRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(openapi, dummyOp)).isEqualTo("http://component-rest");

        component.setHost("http://component");
        assertThat(endpoint.determineHost(openapi, dummyOp)).isEqualTo("http://component");

        endpoint.setHost("http://endpoint");
        assertThat(endpoint.determineHost(openapi, dummyOp)).isEqualTo("http://endpoint");
    }

    @Test
    public void shouldIncludeApiKeysQueryParameters() {
        final CamelContext camelContext = mock(CamelContext.class);

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setCamelContext(camelContext);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "uri", "remaining", component,
                Collections.emptyMap());
        endpoint.setHost("http://petstore.openapi.io");

        final OpenAPI openapi = new OpenAPI();
        final SecurityScheme apiKeys = new SecurityScheme().type(Type.APIKEY).in(In.HEADER).name("key");

        openapi.components(new Components().addSecuritySchemes("apiKeys", apiKeys));

        final Operation operation = new Operation().operationId("get");
        Parameter oas30Parameter = new Parameter().name("q").in("query").required(true);
        operation.addParametersItem(oas30Parameter);

        operation.addSecurityItem(new SecurityRequirement().addList("apiKeys", Collections.emptyList()));

        assertThat(endpoint.determineEndpointParameters(openapi, operation))
                .containsOnly(entry("host", "http://petstore.openapi.io"), entry("queryParameters", "q={q}"));

        apiKeys.setIn(In.QUERY);
        assertThat(endpoint.determineEndpointParameters(openapi, operation))
                .containsOnly(entry("host", "http://petstore.openapi.io"), entry("queryParameters", "key={key}&q={q}"));
    }

    @Test
    public void shouldPickBestScheme() {
        assertThat(RestOpenApiEndpoint.pickBestScheme("http", Arrays.asList("http", "https")))
                .isEqualTo("https");

        assertThat(RestOpenApiEndpoint.pickBestScheme("https", Arrays.asList("http"))).isEqualTo("http");

        assertThat(RestOpenApiEndpoint.pickBestScheme("http", Collections.emptyList())).isEqualTo("http");

        assertThat(RestOpenApiEndpoint.pickBestScheme("http", null)).isEqualTo("http");

        assertThat(RestOpenApiEndpoint.pickBestScheme(null, Collections.emptyList())).isNull();

        assertThat(RestOpenApiEndpoint.pickBestScheme(null, null)).isNull();
    }

    @Test
    public void shouldRaiseExceptionsForMissingSpecifications() {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        final URI uri = URI.create("non-existant.json");
        assertThrows(IllegalArgumentException.class,
                () -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, uri));
    }

    @Test
    public void shouldResolveUris() {
        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("param1", "value1");

        final Map<String, Parameter> pathParameters = new HashMap<>();
        pathParameters.put("param1", new Parameter().name("param1"));
        pathParameters.put("param2", new Parameter().name("param2"));

        assertThat(endpoint.resolveUri("/path", pathParameters)).isEqualTo("/path");
        assertThat(endpoint.resolveUri("/path/{param1}", pathParameters)).isEqualTo("/path/value1");
        assertThat(endpoint.resolveUri("/{param1}/path", pathParameters)).isEqualTo("/value1/path");
        assertThat(endpoint.resolveUri("/{param1}/path/{param2}", pathParameters)).isEqualTo("/value1/path/{param2}");
        assertThat(endpoint.resolveUri("/{param1}/{param2}", pathParameters)).isEqualTo("/value1/{param2}");
        assertThat(endpoint.resolveUri("/path/{param1}/to/{param2}/rest", pathParameters))
                .isEqualTo("/path/value1/to/{param2}/rest");
    }

    @Test
    public void shouldSerializeGivenLiteralValues() {
        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("param", "va lue");

        final Parameter queryParameter = new Parameter().name("param");

        assertThat(endpoint.literalQueryParameterValue(queryParameter)).isEqualTo("param=va%20lue");

        final Parameter pathParameter = new Parameter().name("param");
        assertThat(endpoint.literalPathParameterValue(pathParameter)).isEqualTo("va%20lue");
    }

    @Test
    public void shouldUseDefaultSpecificationUri() {
        final RestOpenApiComponent component = new RestOpenApiComponent();

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:getPetById", "getPetById", component,
                Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(RestOpenApiComponent.DEFAULT_SPECIFICATION_URI);
    }

    @Test
    public void shouldUseDefaultSpecificationUriEvenIfHashIsPresent() {
        final RestOpenApiComponent component = new RestOpenApiComponent();

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:#getPetById", "#getPetById",
                component, Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(RestOpenApiComponent.DEFAULT_SPECIFICATION_URI);
    }

}
