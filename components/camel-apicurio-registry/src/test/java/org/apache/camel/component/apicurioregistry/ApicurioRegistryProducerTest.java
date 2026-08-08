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

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApicurioRegistryProducerTest extends CamelTestSupport {

    private final RegistryClient mockClient = mock(RegistryClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

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
        assertNotNull(result);
        verify(mockClient.groups().byGroupId("testGroup").artifacts()).post(any(CreateArtifact.class), any());
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
        assertEquals(mockMetadata, result);
    }

    @Test
    void testGetArtifactContent() throws Exception {
        String endpointUri
                = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&operation=getArtifactContent";
        injectMockClient(endpointUri);

        ByteArrayInputStream mockContent = new ByteArrayInputStream("{\"test\":true}".getBytes(StandardCharsets.UTF_8));
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().byVersionExpression("branch=latest").content().get())
                .thenReturn(mockContent);

        Object result = template.requestBody("direct:getArtifactContent", (Object) null);
        assertNotNull(result);
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
        assertEquals(mockResults, result);
    }

    @Test
    void testCreateGroup() throws Exception {
        String endpointUri
                = "apicurio-registry:newGroup?registryUrl=http://localhost:8080/apis/registry/v3&operation=createGroup";
        injectMockClient(endpointUri);

        GroupMetaData mockGroupMeta = new GroupMetaData();
        when(mockClient.groups().post(any(CreateGroup.class))).thenReturn(mockGroupMeta);

        Object result = template.requestBody("direct:createGroup", (Object) null);
        assertEquals(mockGroupMeta, result);
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
        assertEquals(mockVersionMeta, result);
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
        assertEquals(mockMetadata, result);
    }
}
