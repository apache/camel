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

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApicurioRegistryConsumerTest extends CamelTestSupport {

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
                from("apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&delay=500")
                        .to("mock:result");
            }
        };
    }

    private void injectMockClient() {
        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(
                "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&delay=500");
        endpoint.setRegistryClient(mockClient);
    }

    @Test
    void testPollNewVersions() throws Exception {
        injectMockClient();

        SearchedVersion v1 = new SearchedVersion();
        v1.setGlobalId(1L);
        v1.setVersion("1.0");
        v1.setContentId(100L);
        v1.setArtifactType("JSON");

        SearchedVersion v2 = new SearchedVersion();
        v2.setGlobalId(2L);
        v2.setVersion("2.0");
        v2.setContentId(101L);
        v2.setArtifactType("JSON");

        VersionSearchResults results = new VersionSearchResults();
        results.setVersions(List.of(v1, v2));

        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().get())
                .thenReturn(results);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    void testPollNoNewVersionsAfterInitial() throws Exception {
        injectMockClient();

        SearchedVersion v1 = new SearchedVersion();
        v1.setGlobalId(1L);
        v1.setVersion("1.0");
        v1.setContentId(100L);
        v1.setArtifactType("JSON");

        VersionSearchResults results = new VersionSearchResults();
        results.setVersions(List.of(v1));

        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().get())
                .thenReturn(results);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }
}
