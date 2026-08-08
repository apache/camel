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
package org.apache.camel.component.apicurioregistry.integration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.apicurioregistry.ApicurioRegistryConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApicurioRegistryProducerIT extends ApicurioRegistryTestSupport {

    private static final String JSON_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "integer"}
                }
            }
            """;

    private static final String JSON_SCHEMA_V2 = """
            {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "integer"},
                    "email": {"type": "string"}
                }
            }
            """;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:operation")
                        .toD("apicurio-registry:${header.CamelApicurioRegistryGroupId}/${header.CamelApicurioRegistryArtifactId}"
                             + "?registryUrl=" + getRegistryUrl());
            }
        };
    }

    @Test
    void testFullArtifactLifecycle() throws Exception {
        String groupId = "test-group-" + UUID.randomUUID();
        String artifactId = "test-artifact-" + UUID.randomUUID();

        // 1. Create group
        Map<String, Object> headers = new HashMap<>();
        headers.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_CREATE_GROUP);
        headers.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        Exchange groupResult = template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(headers);
        });
        assertNotNull(groupResult.getIn().getBody());
        assertInstanceOf(GroupMetaData.class, groupResult.getIn().getBody());

        // 2. Create artifact
        Map<String, Object> createHeaders = new HashMap<>();
        createHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_CREATE_ARTIFACT);
        createHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        createHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        createHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, "JSON");
        createHeaders.put(ApicurioRegistryConstants.HEADER_IF_EXISTS, "FIND_OR_CREATE_VERSION");
        Exchange createResult = template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(createHeaders);
            exchange.getIn().setBody(JSON_SCHEMA);
        });
        assertNotNull(createResult.getIn().getBody());
        assertInstanceOf(CreateArtifactResponse.class, createResult.getIn().getBody());

        // 3. Get artifact metadata
        Map<String, Object> getMetaHeaders = new HashMap<>();
        getMetaHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION,
                ApicurioRegistryConstants.OPERATION_GET_ARTIFACT_METADATA);
        getMetaHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        getMetaHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        Exchange metaResult = template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(getMetaHeaders);
        });
        assertInstanceOf(ArtifactMetaData.class, metaResult.getIn().getBody());
        ArtifactMetaData metadata = (ArtifactMetaData) metaResult.getIn().getBody();
        assertEquals(artifactId, metadata.getArtifactId());

        // 4. Get artifact content
        Map<String, Object> getContentHeaders = new HashMap<>();
        getContentHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION,
                ApicurioRegistryConstants.OPERATION_GET_ARTIFACT_CONTENT);
        getContentHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        getContentHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        Exchange contentResult = template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(getContentHeaders);
        });
        Object contentBody = contentResult.getIn().getBody();
        assertNotNull(contentBody);
        String contentStr;
        if (contentBody instanceof InputStream) {
            contentStr = new String(((InputStream) contentBody).readAllBytes(), StandardCharsets.UTF_8);
        } else {
            contentStr = contentBody.toString();
        }
        assertTrue(contentStr.contains("name"));

        // 5. Update artifact (new version)
        Map<String, Object> updateHeaders = new HashMap<>();
        updateHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION,
                ApicurioRegistryConstants.OPERATION_UPDATE_ARTIFACT);
        updateHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        updateHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        Exchange updateResult = template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(updateHeaders);
            exchange.getIn().setBody(JSON_SCHEMA_V2);
        });
        assertInstanceOf(VersionMetaData.class, updateResult.getIn().getBody());

        // 6. List versions
        Map<String, Object> listHeaders = new HashMap<>();
        listHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION,
                ApicurioRegistryConstants.OPERATION_LIST_VERSIONS);
        listHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        listHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        Exchange listResult = template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(listHeaders);
        });
        assertInstanceOf(VersionSearchResults.class, listResult.getIn().getBody());
        VersionSearchResults versions = (VersionSearchResults) listResult.getIn().getBody();
        assertTrue(versions.getCount() >= 2);

        // 7. Delete artifact
        Map<String, Object> deleteHeaders = new HashMap<>();
        deleteHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION,
                ApicurioRegistryConstants.OPERATION_DELETE_ARTIFACT);
        deleteHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        deleteHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(deleteHeaders);
        });
    }

    @Test
    void testSearchArtifacts() throws Exception {
        String groupId = "search-group-" + UUID.randomUUID();
        String artifactId = "search-artifact-" + UUID.randomUUID();

        // Create group first
        Map<String, Object> groupHeaders = new HashMap<>();
        groupHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_CREATE_GROUP);
        groupHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        template.request("direct:operation", exchange -> exchange.getIn().setHeaders(groupHeaders));

        // Create artifact
        Map<String, Object> createHeaders = new HashMap<>();
        createHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_CREATE_ARTIFACT);
        createHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        createHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        createHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, "JSON");
        template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(createHeaders);
            exchange.getIn().setBody(JSON_SCHEMA);
        });

        // Search
        Map<String, Object> searchHeaders = new HashMap<>();
        searchHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_SEARCH_ARTIFACTS);
        searchHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        searchHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        Exchange searchResult = template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(searchHeaders);
        });
        assertNotNull(searchResult.getIn().getBody());

        // Cleanup
        Map<String, Object> deleteHeaders = new HashMap<>();
        deleteHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_DELETE_ARTIFACT);
        deleteHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        deleteHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        template.request("direct:operation", exchange -> exchange.getIn().setHeaders(deleteHeaders));
    }
}
