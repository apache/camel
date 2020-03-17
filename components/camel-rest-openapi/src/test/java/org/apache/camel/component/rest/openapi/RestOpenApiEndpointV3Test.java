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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.apicurio.datamodels.core.models.common.SecurityRequirement;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasResponse;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Operation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Parameter;
import io.apicurio.datamodels.openapi.v3.models.Oas30Response;
import io.apicurio.datamodels.openapi.v3.models.Oas30SecurityScheme;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.spi.RestConfiguration;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestOpenApiEndpointV3Test {

    URI componentJsonUri = URI.create("component.json");

    URI endpointUri = URI.create("endpoint.json");

    @Test(expected = IllegalArgumentException.class)
    public void shouldComplainForUnknownOperations() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        final RestOpenApiComponent component = new RestOpenApiComponent(camelContext);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("rest-openapi:unknown", "unknown", component,
            Collections.emptyMap());

        endpoint.createProducer();
    }

    @Test
    public void shouldComputeQueryParameters() {
        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("literal", "value");
        assertThat(endpoint.queryParameter(new Oas30Parameter())).isEqualTo("");
        assertThat(endpoint.queryParameter(new Oas30Parameter("param"))).isEqualTo("param={param?}");
        assertThat(endpoint.queryParameter(new Oas30Parameter("literal"))).isEqualTo("literal=value");
    }

    @Test
    public void shouldCreateQueryParameterExpressions() {
        Oas30Parameter oas30Parameter = new Oas30Parameter("q");
        oas30Parameter.required = true;
        assertThat(RestOpenApiEndpoint.queryParameterExpression(oas30Parameter))
            .isEqualTo("q={q}");
        oas30Parameter.required = false;
        assertThat(RestOpenApiEndpoint.queryParameterExpression(oas30Parameter))
            .isEqualTo("q={q?}");
    }

    @Test
    public void shouldDetermineBasePath() {
        final RestConfiguration restConfiguration = new RestConfiguration();

        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getRestConfiguration()).thenReturn(restConfiguration);

        final Oas30Document openapi = new Oas30Document();

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setCamelContext(camelContext);
        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("rest-openapi:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.determineBasePath(openapi))
            .as("When no base path is specified on component, endpoint or rest configuration it should default to `/`")
            .isEqualTo("/");

        restConfiguration.setContextPath("/rest");
        assertThat(endpoint.determineBasePath(openapi)).as(
            "When base path is specified in REST configuration and not specified in component the base path should be from the REST configuration")
            .isEqualTo("/rest");

        openapi.addServer("http://petstore.openapi.io", "v3 test");


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

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("uri", "remaining", component,
            Collections.emptyMap());
        endpoint.setHost("http://petstore.openapi.io");

        final Oas30Document openapi = new Oas30Document();
        final Oas30Operation operation = new Oas30Operation("get");
        operation.createParameter();
        assertThat(endpoint.determineEndpointParameters(openapi, operation))
            .containsOnly(entry("host", "http://petstore.openapi.io"));


        component.setComponentName("xyz");
        assertThat(endpoint.determineEndpointParameters(openapi, operation))
            .containsOnly(entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "xyz"));

        List<String> consumers = new ArrayList<String>();
        consumers.add("application/json");
        List<String> produces = new ArrayList<String>();
        produces.add("application/xml");
        operation.requestBody = operation.createRequestBody();
        operation.responses = operation.createResponses();
        operation.responses.addResponse("200", operation.responses.createResponse("200"));
        for (String consumer : consumers) {
            operation.requestBody.content.put(consumer, operation.requestBody.createMediaType(consumer));

        }
        for (String produce : produces) {
            for (OasResponse response : operation.responses.getResponses()) {
                Oas30Response oas30Response = (Oas30Response)response;
                oas30Response.content.put(produce, oas30Response.createMediaType(produce));
            }
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

        Oas30Parameter oas30Parameter = new Oas30Parameter("q");
        oas30Parameter.in = "query";
        oas30Parameter.required = true;
        operation.addParameter(oas30Parameter);
        assertThat(endpoint.determineEndpointParameters(openapi, operation)).containsOnly(
            entry("host", "http://petstore.openapi.io"), entry("producerComponentName", "zyx"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"),
            entry("queryParameters", "q={q}"));

        oas30Parameter = new Oas30Parameter("o");
        oas30Parameter.in = "query";
        operation.addParameter(oas30Parameter);
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

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("rest-openapi:http://some-uri#getPetById",
            "http://some-uri#getPetById", component, Collections.emptyMap());

        final Oas30Document openapi = new Oas30Document();
        openapi.addServer("http://petstore.openapi.io", "v3 test");
        openapi.addServer("http://anotherpetstore.openapi.io", "v3 test");

        assertThat(endpoint.determineHost(openapi)).isEqualTo("http://petstore.openapi.io");

        openapi.getServers().clear();
        openapi.addServer("https://petstore.openapi.io", "v3 test");
        assertThat(endpoint.determineHost(openapi)).isEqualTo("https://petstore.openapi.io");
    }

    @Test
    public void shouldDetermineOptions() {
        assertThat(RestOpenApiEndpoint.determineOption(null, null, null, null)).isNull();

        assertThat(RestOpenApiEndpoint.determineOption(Collections.emptyList(), Collections.emptyList(), "", ""))
            .isNull();

        assertThat(RestOpenApiEndpoint.determineOption(Arrays.asList("specification"), null, null, null))
            .isEqualTo("specification");

        assertThat(
            RestOpenApiEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"), null, null))
                .isEqualTo("operation");

        assertThat(RestOpenApiEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"),
            "component", null)).isEqualTo("component");

        assertThat(RestOpenApiEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"),
            "component", "operation")).isEqualTo("operation");
    }

    @Test
    public void shouldHonourComponentSpecificationPathProperty() throws Exception {
        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setSpecificationUri(componentJsonUri);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("rest-openapi:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(componentJsonUri);
    }

    @Test
    public void shouldHonourEndpointUriPathSpecificationPathProperty() throws Exception {
        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setSpecificationUri(componentJsonUri);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("rest-openapi:endpoint.json#getPetById",
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

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("petstore:http://specification-uri#getPetById",
            "http://specification-uri#getPetById", component, Collections.emptyMap());

        final Oas30Document openapi = new Oas30Document();

        assertThat(endpoint.determineHost(openapi)).isEqualTo("http://specification-uri");

        globalRestConfiguration.setHost("global-rest");
        globalRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(openapi)).isEqualTo("http://global-rest");

        globalRestConfiguration.setHost("component-rest");
        globalRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(openapi)).isEqualTo("http://component-rest");

        component.setHost("http://component");
        assertThat(endpoint.determineHost(openapi)).isEqualTo("http://component");

        endpoint.setHost("http://endpoint");
        assertThat(endpoint.determineHost(openapi)).isEqualTo("http://endpoint");
    }

    @Test
    public void shouldIncludeApiKeysQueryParameters() {
        final CamelContext camelContext = mock(CamelContext.class);

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setCamelContext(camelContext);

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("uri", "remaining", component,
            Collections.emptyMap());
        endpoint.setHost("http://petstore.openapi.io");

        final Oas30Document openapi = new Oas30Document();
        final Oas30SecurityScheme apiKeys = new Oas30SecurityScheme("key");
        apiKeys.name = "key";
        apiKeys.in = "header";
        openapi.components = openapi.createComponents();

        openapi.components.addSecurityScheme("apiKeys", apiKeys);

        final Oas30Operation operation = new Oas30Operation("get");
        Oas30Parameter oas30Parameter = new Oas30Parameter("q");
        oas30Parameter.in = "query";
        oas30Parameter.required = true;
        operation.addParameter(oas30Parameter);
        SecurityRequirement securityRequirement =  operation.createSecurityRequirement();
        securityRequirement.addSecurityRequirementItem("apiKeys", Collections.emptyList());
        operation.addSecurityRequirement(securityRequirement);


        assertThat(endpoint.determineEndpointParameters(openapi, operation))
            .containsOnly(entry("host", "http://petstore.openapi.io"), entry("queryParameters", "q={q}"));

        apiKeys.in = "query";
        assertThat(endpoint.determineEndpointParameters(openapi, operation))
            .containsOnly(entry("host", "http://petstore.openapi.io"), entry("queryParameters", "key={key}&q={q}"));
    }

    @Test
    public void shouldLoadOpenApiSpecifications() throws IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        assertThat(
            RestOpenApiEndpoint.loadSpecificationFrom(camelContext, RestOpenApiComponent.DEFAULT_SPECIFICATION_URI))
                .isNotNull();
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

    @Test(expected = IllegalArgumentException.class)
    public void shouldRaiseExceptionsForMissingSpecifications() throws IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, URI.create("non-existant.json"));
    }

    @Test
    public void shouldResolveUris() {
        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("param1", "value1");

        final Map<String, OasParameter> pathParameters = new HashMap<>();
        pathParameters.put("param1", new Oas30Parameter("param1"));
        pathParameters.put("param2", new Oas30Parameter("param2"));

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

        final OasParameter queryParameter = new Oas30Parameter("param");

        assertThat(endpoint.literalQueryParameterValue(queryParameter)).isEqualTo("param=va%20lue");

        final Oas30Parameter pathParameter = new Oas30Parameter("param");
        assertThat(endpoint.literalPathParameterValue(pathParameter)).isEqualTo("va%20lue");
    }

    @Test
    public void shouldUseDefaultSpecificationUri() throws Exception {
        final RestOpenApiComponent component = new RestOpenApiComponent();

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("rest-openapi:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(RestOpenApiComponent.DEFAULT_SPECIFICATION_URI);
    }

    @Test
    public void shouldUseDefaultSpecificationUriEvenIfHashIsPresent() throws Exception {
        final RestOpenApiComponent component = new RestOpenApiComponent();

        final RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint("rest-openapi:#getPetById", "#getPetById",
            component, Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(RestOpenApiComponent.DEFAULT_SPECIFICATION_URI);
    }

}
