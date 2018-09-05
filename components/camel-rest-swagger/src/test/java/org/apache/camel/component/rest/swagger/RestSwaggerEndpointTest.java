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
package org.apache.camel.component.rest.swagger;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import io.swagger.models.Operation;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.spi.RestConfiguration;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestSwaggerEndpointTest {

    URI componentJsonUri = URI.create("component.json");

    URI endpointUri = URI.create("endpoint.json");

    @Test(expected = IllegalArgumentException.class)
    public void shouldComplainForUnknownOperations() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        final RestSwaggerComponent component = new RestSwaggerComponent(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:unknown", "unknown", component,
            Collections.emptyMap());

        endpoint.createProducer();
    }

    @Test
    public void shouldComputeQueryParameters() {
        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("literal", "value");

        assertThat(endpoint.queryParameter(new QueryParameter())).isEqualTo("");
        assertThat(endpoint.queryParameter(new QueryParameter().name("param"))).isEqualTo("param={param?}");
        assertThat(endpoint.queryParameter(new QueryParameter().name("literal"))).isEqualTo("literal=value");
    }

    @Test
    public void shouldCreateProducers() throws Exception {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());
        final Endpoint endpointDelegate = mock(Endpoint.class);
        when(camelContext.getEndpoint("rest:GET:/v2:/pet/{petId}")).thenReturn(endpointDelegate);
        final Producer delegateProducer = mock(Producer.class);
        when(endpointDelegate.createProducer()).thenReturn(delegateProducer);

        final RestSwaggerComponent component = new RestSwaggerComponent(camelContext);
        component.setHost("http://petstore.swagger.io");

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById", component,
            Collections.emptyMap());

        final Producer producer = endpoint.createProducer();

        assertThat(producer).isSameAs(delegateProducer);
    }

    @Test
    public void shouldCreateQueryParameterExpressions() {
        assertThat(RestSwaggerEndpoint.queryParameterExpression(new QueryParameter().name("q").required(true)))
            .isEqualTo("q={q}");
        assertThat(RestSwaggerEndpoint.queryParameterExpression(new QueryParameter().name("q").required(false)))
            .isEqualTo("q={q?}");
    }

    @Test
    public void shouldDetermineBasePath() {
        final RestConfiguration restConfiguration = new RestConfiguration();

        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getRestConfiguration("rest-swagger", true)).thenReturn(restConfiguration);

        final Swagger swagger = new Swagger();

        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setCamelContext(camelContext);
        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.determineBasePath(swagger))
            .as("When no base path is specified on component, endpoint or rest configuration it should default to `/`")
            .isEqualTo("/");

        restConfiguration.setContextPath("/rest");
        assertThat(endpoint.determineBasePath(swagger))
            .as("When base path is specified in REST configuration and not specified in component the base path should be from the REST configuration")
            .isEqualTo("/rest");

        swagger.basePath("/specification");
        assertThat(endpoint.determineBasePath(swagger))
            .as("When base path is specified in the specification it should take precedence the one specified in the REST configuration")
            .isEqualTo("/specification");

        component.setBasePath("/component");
        assertThat(endpoint.determineBasePath(swagger))
            .as("When base path is specified on the component it should take precedence over Swagger specification and REST configuration")
            .isEqualTo("/component");

        endpoint.setBasePath("/endpoint");
        assertThat(endpoint.determineBasePath(swagger))
            .as("When base path is specified on the endpoint it should take precedence over any other")
            .isEqualTo("/endpoint");
    }

    @Test
    public void shouldDetermineEndpointParameters() {
        final CamelContext camelContext = mock(CamelContext.class);

        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setCamelContext(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("uri", "remaining", component,
            Collections.emptyMap());
        endpoint.setHost("http://petstore.swagger.io");

        final Swagger swagger = new Swagger();
        final Operation operation = new Operation();

        assertThat(endpoint.determineEndpointParameters(swagger, operation))
            .containsOnly(entry("host", "http://petstore.swagger.io"));

        component.setComponentName("xyz");
        assertThat(endpoint.determineEndpointParameters(swagger, operation))
            .containsOnly(entry("host", "http://petstore.swagger.io"), entry("componentName", "xyz"));

        swagger.consumes("application/json").produces("application/xml");
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("componentName", "xyz"),
            entry("consumes", "application/xml"), entry("produces", "application/json"));

        component.setProduces("application/json");
        component.setConsumes("application/atom+xml");
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("componentName", "xyz"),
            entry("consumes", "application/atom+xml"), entry("produces", "application/json"));

        endpoint.setProduces("application/atom+xml");
        endpoint.setConsumes("application/json");
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("componentName", "xyz"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"));

        endpoint.setComponentName("zyx");
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("componentName", "zyx"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"));

        operation.addParameter(new QueryParameter().name("q").required(true));
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("componentName", "zyx"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"),
            entry("queryParameters", "q={q}"));

        operation.addParameter(new QueryParameter().name("o"));
        assertThat(endpoint.determineEndpointParameters(swagger, operation)).containsOnly(
            entry("host", "http://petstore.swagger.io"), entry("componentName", "zyx"),
            entry("consumes", "application/json"), entry("produces", "application/atom+xml"),
            entry("queryParameters", "q={q}&o={o?}"));
    }

    @Test
    public void shouldDetermineHostFromRestConfiguration() {
        assertThat(RestSwaggerEndpoint.hostFrom(null)).isNull();

        final RestConfiguration configuration = new RestConfiguration();
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isNull();

        configuration.setScheme("ftp");
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isNull();

        configuration.setScheme("http");
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isNull();

        configuration.setHost("petstore.swagger.io");
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.swagger.io");

        configuration.setPort(80);
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.swagger.io");

        configuration.setPort(8080);
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("http://petstore.swagger.io:8080");

        configuration.setScheme("https");
        configuration.setPort(80);
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("https://petstore.swagger.io:80");

        configuration.setPort(443);
        assertThat(RestSwaggerEndpoint.hostFrom(configuration)).isEqualTo("https://petstore.swagger.io");
    }

    @Test
    public void shouldDetermineHostFromSpecification() {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:http://some-uri#getPetById",
            "http://some-uri#getPetById", component, Collections.emptyMap());

        final Swagger swagger = new Swagger();
        swagger.host("petstore.swagger.io");

        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://petstore.swagger.io");

        swagger.schemes(Arrays.asList(Scheme.HTTPS));
        assertThat(endpoint.determineHost(swagger)).isEqualTo("https://petstore.swagger.io");
    }

    @Test
    public void shouldDetermineOptions() {
        assertThat(RestSwaggerEndpoint.determineOption(null, null, null, null)).isNull();

        assertThat(RestSwaggerEndpoint.determineOption(Collections.emptyList(), Collections.emptyList(), "", ""))
            .isNull();

        assertThat(RestSwaggerEndpoint.determineOption(Arrays.asList("specification"), null, null, null))
            .isEqualTo("specification");

        assertThat(
            RestSwaggerEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"), null, null))
                .isEqualTo("operation");

        assertThat(RestSwaggerEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"),
            "component", null)).isEqualTo("component");

        assertThat(RestSwaggerEndpoint.determineOption(Arrays.asList("specification"), Arrays.asList("operation"),
            "component", "operation")).isEqualTo("operation");
    }

    @Test
    public void shouldHonourComponentSpecificationPathProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setSpecificationUri(componentJsonUri);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(componentJsonUri);
    }

    @Test
    public void shouldHonourEndpointUriPathSpecificationPathProperty() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setSpecificationUri(componentJsonUri);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:endpoint.json#getPetById",
            "endpoint.json#getPetById", component, Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(endpointUri);
    }

    @Test
    public void shouldHonourHostPrecedence() {
        final RestConfiguration globalRestConfiguration = new RestConfiguration();

        final RestConfiguration componentRestConfiguration = new RestConfiguration();
        final RestConfiguration specificRestConfiguration = new RestConfiguration();

        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getRestConfiguration()).thenReturn(globalRestConfiguration);
        when(camelContext.getRestConfiguration("rest-swagger", false)).thenReturn(componentRestConfiguration);
        when(camelContext.getRestConfiguration("petstore", false)).thenReturn(specificRestConfiguration);

        final RestSwaggerComponent component = new RestSwaggerComponent();
        component.setCamelContext(camelContext);

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("petstore:http://specification-uri#getPetById",
            "http://specification-uri#getPetById", component, Collections.emptyMap());

        final Swagger swagger = new Swagger();
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://specification-uri");

        globalRestConfiguration.setHost("global-rest");
        globalRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://global-rest");

        globalRestConfiguration.setHost("component-rest");
        globalRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://component-rest");

        specificRestConfiguration.setHost("specific-rest");
        specificRestConfiguration.setScheme("http");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://specific-rest");

        swagger.host("specification").scheme(Scheme.HTTP);
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://specification");

        component.setHost("http://component");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://component");

        endpoint.setHost("http://endpoint");
        assertThat(endpoint.determineHost(swagger)).isEqualTo("http://endpoint");
    }

    @Test
    public void shouldLoadSwaggerSpecifications() throws IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        assertThat(
            RestSwaggerEndpoint.loadSpecificationFrom(camelContext, RestSwaggerComponent.DEFAULT_SPECIFICATION_URI))
                .isNotNull();
    }

    @Test
    public void shouldPickBestScheme() {
        assertThat(RestSwaggerEndpoint.pickBestScheme("http", Arrays.asList(Scheme.HTTP, Scheme.HTTPS)))
            .isEqualTo("https");

        assertThat(RestSwaggerEndpoint.pickBestScheme("https", Arrays.asList(Scheme.HTTP))).isEqualTo("http");

        assertThat(RestSwaggerEndpoint.pickBestScheme("http", Collections.emptyList())).isEqualTo("http");

        assertThat(RestSwaggerEndpoint.pickBestScheme("http", null)).isEqualTo("http");

        assertThat(RestSwaggerEndpoint.pickBestScheme(null, Collections.emptyList())).isNull();

        assertThat(RestSwaggerEndpoint.pickBestScheme(null, null)).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRaiseExceptionsForMissingSpecifications() throws IOException {
        final CamelContext camelContext = mock(CamelContext.class);
        when(camelContext.getClassResolver()).thenReturn(new DefaultClassResolver());

        RestSwaggerEndpoint.loadSpecificationFrom(camelContext, URI.create("non-existant.json"));
    }

    @Test
    public void shouldSerializeGivenLiteralValues() {
        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint();
        endpoint.parameters = new HashMap<>();
        endpoint.parameters.put("param", "va lue");

        final QueryParameter queryParameter = new QueryParameter().name("param");

        assertThat(endpoint.literalQueryParameterValue(queryParameter)).isEqualTo("param=va%20lue");

        final PathParameter pathParameter = new PathParameter().name("param");
        assertThat(endpoint.literalPathParameterValue(pathParameter)).isEqualTo("va%20lue");
    }

    @Test
    public void shouldUseDefaultSpecificationUri() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:getPetById", "getPetById", component,
            Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(RestSwaggerComponent.DEFAULT_SPECIFICATION_URI);
    }

    @Test
    public void shouldUseDefaultSpecificationUriEvenIfHashIsPresent() throws Exception {
        final RestSwaggerComponent component = new RestSwaggerComponent();

        final RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:#getPetById", "#getPetById",
            component, Collections.emptyMap());

        assertThat(endpoint.getSpecificationUri()).isEqualTo(RestSwaggerComponent.DEFAULT_SPECIFICATION_URI);
    }

}
