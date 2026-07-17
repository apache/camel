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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.apicurioregistry.ApicurioRegistryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApicurioRegistryValidateIT extends ApicurioRegistryTestSupport {

    private static final String JSON_SCHEMA = """
            {
                "$schema": "http://json-schema.org/draft-07/schema#",
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "integer"}
                },
                "required": ["name"]
            }
            """;

    private final String groupId = "validate-group-" + UUID.randomUUID();
    private final String artifactId = "validate-artifact-" + UUID.randomUUID();

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:setup")
                        .toD("apicurio-registry:${header.CamelApicurioRegistryGroupId}/${header.CamelApicurioRegistryArtifactId}"
                             + "?registryUrl=" + getRegistryUrl());

                from("direct:validate")
                        .to("apicurio-registry:" + groupId + "/" + artifactId
                            + "?registryUrl=" + getRegistryUrl()
                            + "&operation=validate&failOnValidation=false");

                from("direct:testCompatibility")
                        .to("apicurio-registry:" + groupId + "/" + artifactId
                            + "?registryUrl=" + getRegistryUrl()
                            + "&operation=testCompatibility");
            }
        };
    }

    @BeforeEach
    void setupArtifact() {
        // Create group
        Map<String, Object> groupHeaders = new HashMap<>();
        groupHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_CREATE_GROUP);
        groupHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        template.request("direct:setup", exchange -> exchange.getIn().setHeaders(groupHeaders));

        // Create artifact with JSON Schema
        Map<String, Object> createHeaders = new HashMap<>();
        createHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_CREATE_ARTIFACT);
        createHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        createHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        createHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, "JSON");
        createHeaders.put(ApicurioRegistryConstants.HEADER_IF_EXISTS, "FIND_OR_CREATE_VERSION");
        template.request("direct:setup", exchange -> {
            exchange.getIn().setHeaders(createHeaders);
            exchange.getIn().setBody(JSON_SCHEMA);
        });
    }

    @Test
    void testValidateCompatibleContent() {
        String compatibleSchema = """
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "age": {"type": "integer"},
                        "email": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """;

        Exchange result = template.request("direct:validate", exchange -> {
            exchange.getIn().setBody(compatibleSchema);
        });

        assertNotNull(result.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_RESULT));
    }

    @Test
    void testTestCompatibility() {
        String compatibleSchema = """
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "age": {"type": "integer"}
                    }
                }
                """;

        Exchange result = template.request("direct:testCompatibility", exchange -> {
            exchange.getIn().setBody(compatibleSchema);
        });

        assertNotNull(result.getIn().getBody());
    }
}
