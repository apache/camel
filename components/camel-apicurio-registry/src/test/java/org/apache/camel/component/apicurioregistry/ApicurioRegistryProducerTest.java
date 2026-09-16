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
package org.apache.camel.component.apicurioregistry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.groups.item.artifacts.ArtifactsRequestBuilder;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApicurioRegistryProducerTest extends CamelTestSupport {

    private final RegistryClient mockClient = mock(RegistryClient.class, RETURNS_DEEP_STUBS);

    @BindToRegistry("apicurio-registry")
    public ApicurioRegistryComponent getComponent() {
        ApicurioRegistryComponent component = new ApicurioRegistryComponent(context);
        component.getConfiguration().setRegistryUrl("http://localhost:8080/apis/registry/v3");
        return component;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createArtifact")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=createArtifact");

                from("direct:deleteArtifact")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=deleteArtifact");

                from("direct:getArtifactMetadata")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=getArtifactMetadata");

                from("direct:getArtifactContent")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=getArtifactContent");

                from("direct:listVersions")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=listVersions");

                from("direct:createGroup")
                        .to("apicurio-registry:newGroup?registryUrl=http://localhost:8080/apis/registry/v3&operation=createGroup");

                from("direct:updateArtifact")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=updateArtifact");

                from("direct:searchArtifacts")
                        .to("apicurio-registry:testGroup?registryUrl=http://localhost:8080/apis/registry/v3&operation=searchArtifacts");

                from("direct:testCompatibility")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=testCompatibility");

                from("direct:validate")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=validate&failOnValidation=false");

                from("direct:validateFail")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=validate&failOnValidation=true");

                from("direct:operationFromHeader")
                        .to("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3");
            }
        };
    }

    private void injectMockClient(String endpointUri) throws Exception {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(endpointUri);
        endpoint.setRegistryClient(mockClient);
    }

    @Test
    void testCreateArtifact() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=createArtifact";
        injectMockClient(endpointUri);

        CreateArtifactResponse mockResponse = new CreateArtifactResponse();
        when(mockClient.groups().byGroupId(anyString()).artifacts().post(any(CreateArtifact.class), any()))
                .thenReturn(mockResponse);

        Object result = template.requestBody("direct:createArtifact", "{\"test\":true}");
        assertThat(result).isSameAs(mockResponse);
        verify(mockClient.groups().byGroupId("testGroup").artifacts()).post(any(CreateArtifact.class), any());
    }

    @Test
    void testCreateHeaderOverrides() throws Exception {
        injectMockClient("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3"
                         + "&operation=createArtifact");
        when(mockClient.groups().byGroupId("other-group").artifacts().post(any(CreateArtifact.class), any()))
                .thenAnswer(call -> {
                    CreateArtifact request = call.getArgument(0);
                    assertThat(request.getArtifactId()).isEqualTo("other-artifact");
                    assertThat(request.getArtifactType()).isEqualTo("AVRO");
                    assertThat(request.getFirstVersion().getContent().getContent()).isEqualTo("{\"type\":\"string\"}");
                    ArtifactsRequestBuilder builder = mockClient.groups().byGroupId("other-group").artifacts();
                    var config = builder.new PostRequestConfiguration();
                    Consumer<ArtifactsRequestBuilder.PostRequestConfiguration> configure = call.getArgument(1);
                    configure.accept(config);
                    assertThat(config.queryParameters.ifExists).isEqualTo(IfArtifactExists.CREATE_VERSION);
                    return new CreateArtifactResponse();
                });
        Object response = template.requestBodyAndHeaders("direct:createArtifact", "{\"type\":\"string\"}", Map.of(
                ApicurioRegistryConstants.HEADER_GROUP_ID, "other-group",
                ApicurioRegistryConstants.HEADER_ARTIFACT_ID, "other-artifact",
                ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, "AVRO",
                ApicurioRegistryConstants.HEADER_IF_EXISTS, "CREATE_VERSION"));
        assertThat(response).isInstanceOf(CreateArtifactResponse.class);
    }

    @Test
    void testDeleteArtifact() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=deleteArtifact";
        injectMockClient(endpointUri);

        template.sendBody("direct:deleteArtifact", null);
        verify(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")).delete();
    }

    @Test
    void testGetArtifactMetadata() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=getArtifactMetadata";
        injectMockClient(endpointUri);

        ArtifactMetaData mockMetadata = new ArtifactMetaData();
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact").get())
                .thenReturn(mockMetadata);

        Object result = template.requestBody("direct:getArtifactMetadata", (Object) null);
        assertThat(result).isSameAs(mockMetadata);
    }

    @Test
    void testGetArtifactContent() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=getArtifactContent";
        injectMockClient(endpointUri);

        ByteArrayInputStream mockContent = spy(new ByteArrayInputStream("{\"test\":true}".getBytes(StandardCharsets.UTF_8)));
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().byVersionExpression("branch=latest").content().get())
                .thenReturn(mockContent);

        Object result = template.requestBody("direct:getArtifactContent", (Object) null);
        assertThat(result).isEqualTo("{\"test\":true}".getBytes(StandardCharsets.UTF_8));
        verify(mockContent).close();
    }

    @Test
    void testListVersions() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=listVersions";
        injectMockClient(endpointUri);

        VersionSearchResults mockResults = new VersionSearchResults();
        mockResults.setVersions(List.of());
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().get())
                .thenReturn(mockResults);

        Object result = template.requestBody("direct:listVersions", (Object) null);
        assertThat(result).isSameAs(mockResults);
    }

    @Test
    void testCreateGroup() throws Exception {
        String endpointUri
                = "apicurio-registry:newGroup?registryUrl=http://localhost:8080/apis/registry/v3&operation=createGroup";
        injectMockClient(endpointUri);

        GroupMetaData mockGroupMeta = new GroupMetaData();
        when(mockClient.groups().post(any(CreateGroup.class))).thenReturn(mockGroupMeta);

        Object result = template.requestBody("direct:createGroup", (Object) null);
        assertThat(result).isSameAs(mockGroupMeta);
    }

    @Test
    void testUpdateArtifact() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=updateArtifact";
        injectMockClient(endpointUri);

        VersionMetaData mockVersionMeta = new VersionMetaData();
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().post(any(CreateVersion.class)))
                .thenReturn(mockVersionMeta);

        Object result = template.requestBody("direct:updateArtifact", "{\"updated\":true}");
        assertThat(result).isSameAs(mockVersionMeta);
    }

    @Test
    void testOperationFromHeader() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3";
        injectMockClient(endpointUri);

        ArtifactMetaData mockMetadata = new ArtifactMetaData();
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact").get())
                .thenReturn(mockMetadata);

        Object result = template.requestBodyAndHeader("direct:operationFromHeader", null,
                ApicurioRegistryConstants.HEADER_OPERATION, "getArtifactMetadata");
        assertThat(result).isSameAs(mockMetadata);
    }

    @Test
    void testSearchArtifacts() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup?registryUrl=http://localhost:8080/apis/registry/v3&operation=searchArtifacts";
        injectMockClient(endpointUri);

        ArtifactSearchResults mockResults = new ArtifactSearchResults();
        when(mockClient.search().artifacts().get(any())).thenReturn(mockResults);

        Object result = template.requestBody("direct:searchArtifacts", (Object) null);
        assertThat(result).isSameAs(mockResults);
    }

    @Test
    void testTestCompatibilitySuccess() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=testCompatibility";
        injectMockClient(endpointUri);

        VersionMetaData mockVersion = new VersionMetaData();
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().post(any(CreateVersion.class), any()))
                .thenReturn(mockVersion);

        var result = template.request("direct:testCompatibility", exchange -> {
            exchange.getIn().setBody("{\"test\":true}");
            exchange.getIn().setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS, "stale error");
        });
        assertThat(result.getException()).isNull();
        assertThat(result.getIn().getBody()).isEqualTo(true);
        assertThat(result.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS)).isNull();
    }

    @Test
    void testTestCompatibilityFailure() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=testCompatibility";
        injectMockClient(endpointUri);

        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().post(any(CreateVersion.class), any()))
                .thenThrow(ruleViolation("incompatible"));

        var result = template.request("direct:testCompatibility", exchange -> exchange.getIn().setBody("{\"bad\":true}"));
        assertThat(result.getException()).isNull();
        assertThat(result.getIn().getBody()).isEqualTo(false);
        assertThat(result.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS)).isEqualTo("incompatible");
    }

    @Test
    void testValidateSuccess() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=validate&failOnValidation=false";
        injectMockClient(endpointUri);

        VersionMetaData mockVersion = new VersionMetaData();
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().post(any(CreateVersion.class), any()))
                .thenReturn(mockVersion);

        var exchange = template.request("direct:validate", ex -> {
            ex.getIn().setBody("{\"test\":true}");
            ex.getIn().setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS, "stale error");
        });
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_RESULT, Boolean.class)).isTrue();
        assertThat(exchange.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS)).isNull();
        assertThat(exchange.getIn().getBody()).isEqualTo("{\"test\":true}");
    }

    @Test
    void testValidateFailureNoThrow() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=validate&failOnValidation=false";
        injectMockClient(endpointUri);

        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().post(any(CreateVersion.class), any()))
                .thenThrow(ruleViolation("validation error"));

        var exchange = template.request("direct:validate", ex -> ex.getIn().setBody("{\"bad\":true}"));
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_RESULT, Boolean.class)).isFalse();
        assertThat(exchange.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS))
                .isEqualTo("validation error");
    }

    @Test
    void testValidateFailureThrows() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=validate&failOnValidation=true";
        injectMockClient(endpointUri);

        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().post(any(CreateVersion.class), any()))
                .thenThrow(ruleViolation("validation error"));

        assertThatThrownBy(() -> template.requestBody("direct:validateFail", "{\"bad\":true}"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(ApicurioRegistryValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "testCompatibility", "validate" })
    void testNonValidationFailuresPropagate(String operation) throws Exception {
        String uri = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3"
                     + "&operation=" + operation + ("validate".equals(operation) ? "&failOnValidation=false" : "");
        injectMockClient(uri);
        ProblemDetails unauthorized = new ProblemDetails();
        unauthorized.setName("NotAuthorizedException");
        unauthorized.setStatus(401);
        ApiException serverError = new ApiException("server error");
        IllegalStateException connectionError = new IllegalStateException("connection failed");
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().post(any(CreateVersion.class), any()))
                .thenThrow(unauthorized, serverError, connectionError);

        for (Exception failure : List.of(unauthorized, serverError, connectionError)) {
            var exchange = template.request("direct:" + operation, ex -> ex.getIn().setBody("{}"));
            assertThat(exchange.getException()).isSameAs(failure);
            assertThat(exchange.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS)).isNull();
        }
    }

    @Test
    void testUpdateHeaderOverrides() throws Exception {
        injectMockClient("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3"
                         + "&operation=updateArtifact");
        template.requestBodyAndHeaders("direct:updateArtifact", "syntax = \"proto3\";", Map.of(
                ApicurioRegistryConstants.HEADER_GROUP_ID, "other/group",
                ApicurioRegistryConstants.HEADER_ARTIFACT_ID, "other-artifact",
                ApicurioRegistryConstants.HEADER_VERSION, "2",
                ApicurioRegistryConstants.HEADER_CONTENT_TYPE, "application/x-protobuf"));

        ArgumentCaptor<CreateVersion> request = ArgumentCaptor.forClass(CreateVersion.class);
        verify(mockClient.groups().byGroupId("other/group").artifacts().byArtifactId("other-artifact").versions())
                .post(request.capture());
        assertThat(request.getValue().getVersion()).isEqualTo("2");
        assertThat(request.getValue().getContent().getContentType()).isEqualTo("application/x-protobuf");
        assertThat(request.getValue().getContent().getContent()).isEqualTo("syntax = \"proto3\";");
    }

    private static RuleViolationProblemDetails ruleViolation(String message) {
        RuleViolationProblemDetails error = new RuleViolationProblemDetails();
        error.setName("RuleViolationException");
        error.setStatus(409);
        error.setTitle(message);
        return error;
    }
}
