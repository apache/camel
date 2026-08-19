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
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApicurioRegistryConsumerTest {

    private static final String ENDPOINT_URI
            = "apicurio-registry:testGroup/testArtifact?registryUrl=http://localhost:8080/apis/registry/v3&delay=500";

    private final RegistryClient mockClient = mock(RegistryClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        ApicurioRegistryComponent component = new ApicurioRegistryComponent(context);
        component.getConfiguration().setRegistryUrl("http://localhost:8080/apis/registry/v3");
        context.addComponent("apicurio-registry", component);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    private void setupAndStartRoute(VersionSearchResults results) throws Exception {
        when(mockClient.groups().byGroupId("testGroup").artifacts().byArtifactId("testArtifact")
                .versions().get())
                .thenReturn(results);

        ApicurioRegistryEndpoint endpoint = (ApicurioRegistryEndpoint) context.getEndpoint(ENDPOINT_URI);
        endpoint.setRegistryClient(mockClient);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(ENDPOINT_URI).to("mock:result");
            }
        });

        context.start();
    }

    @Test
    void testPollNewVersions() throws Exception {
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

        setupAndStartRoute(results);

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMinimumMessageCount(2);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    void testPollOutOfOrderGlobalIds() throws Exception {
        SearchedVersion v1 = new SearchedVersion();
        v1.setGlobalId(5L);
        v1.setVersion("1.0");
        v1.setContentId(100L);
        v1.setArtifactType("JSON");

        SearchedVersion v2 = new SearchedVersion();
        v2.setGlobalId(2L);
        v2.setVersion("0.1");
        v2.setContentId(99L);
        v2.setArtifactType("JSON");

        SearchedVersion v3 = new SearchedVersion();
        v3.setGlobalId(8L);
        v3.setVersion("2.0");
        v3.setContentId(102L);
        v3.setArtifactType("JSON");

        VersionSearchResults results = new VersionSearchResults();
        results.setVersions(List.of(v1, v2, v3));

        setupAndStartRoute(results);

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMinimumMessageCount(3);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        List<Long> receivedIds = mock.getReceivedExchanges().stream()
                .map(e -> e.getIn().getHeader(ApicurioRegistryConstants.HEADER_GLOBAL_ID, Long.class))
                .toList();
        assertEquals(List.of(2L, 5L, 8L), receivedIds);
    }

    @Test
    void testPollNoNewVersionsAfterInitial() throws Exception {
        SearchedVersion v1 = new SearchedVersion();
        v1.setGlobalId(1L);
        v1.setVersion("1.0");
        v1.setContentId(100L);
        v1.setArtifactType("JSON");

        VersionSearchResults results = new VersionSearchResults();
        results.setVersions(List.of(v1));

        setupAndStartRoute(results);

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }
}
