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

import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.apicurioregistry.ApicurioRegistryConstants;
import org.apache.camel.component.apicurioregistry.ApicurioRegistryEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Artifacts of several types created, updated and validated without a content type header, in a group with a FULL
 * validity rule, so the registry rejects content sent with the wrong content type.
 */
class ApicurioRegistryArtifactTypesIT extends ApicurioRegistryTestSupport {

    private static final String OPENAPI_V1 = """
            openapi: 3.0.3
            info:
              title: Orders
              version: "1"
            paths: {}
            """;

    private static final String OPENAPI_V2 = """
            openapi: 3.0.3
            info:
              title: Orders
              version: "2"
            paths: {}
            """;

    private static final String ASYNCAPI = """
            asyncapi: 2.6.0
            info:
              title: Events
              version: "1"
            channels: {}
            """;

    private static final String PROTOBUF = """
            syntax = "proto3";
            message Order {
              string id = 1;
            }
            """;

    private static final String GRAPHQL = """
            type Query {
              order(id: ID!): String
            }
            """;

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order" type="xs:string"/>
            </xs:schema>
            """;

    private static final Map<String, String[]> CONTENT = Map.of(
            "OPENAPI", new String[] { OPENAPI_V1, OPENAPI_V2 },
            "ASYNCAPI", new String[] { ASYNCAPI, ASYNCAPI.replace("version: \"1\"", "version: \"2\"") },
            "PROTOBUF", new String[] { PROTOBUF, PROTOBUF.replace("string id = 1;", "string id = 1;\n  int32 qty = 2;") },
            "GRAPHQL", new String[] { GRAPHQL, GRAPHQL.replace("String", "Int") },
            "XSD", new String[] { XSD, XSD.replace("xs:string", "xs:int") });

    private final String groupId = "types-group-" + UUID.randomUUID();

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

    @BeforeEach
    void createGroupWithValidityRule() {
        Exchange group = operation(ApicurioRegistryConstants.OPERATION_CREATE_GROUP, null, null, null);
        assertThat(group.getException()).isNull();
        ApicurioRegistryEndpoint endpoint = context.getEndpoint(
                "apicurio-registry:" + groupId + "?registryUrl=" + getRegistryUrl(), ApicurioRegistryEndpoint.class);
        CreateRule rule = new CreateRule();
        rule.setRuleType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        endpoint.getRegistryClient().groups().byGroupId(groupId).rules().post(rule);
    }

    @ParameterizedTest
    @CsvSource({ "OPENAPI", "ASYNCAPI", "PROTOBUF", "GRAPHQL", "XSD" })
    void testCreateUpdateAndValidateWithoutContentTypeHeader(String artifactType) {
        String artifactId = artifactType.toLowerCase() + "-" + UUID.randomUUID();
        String[] content = CONTENT.get(artifactType);

        Exchange created = operation(ApicurioRegistryConstants.OPERATION_CREATE_ARTIFACT, artifactId, artifactType,
                content[0]);
        assertThat(created.getException()).isNull();

        Exchange updated = operation(ApicurioRegistryConstants.OPERATION_UPDATE_ARTIFACT, artifactId, artifactType,
                content[1]);
        assertThat(updated.getException()).isNull();

        Exchange validated = operation(ApicurioRegistryConstants.OPERATION_VALIDATE, artifactId, artifactType,
                content[1]);
        assertThat(validated.getException()).isNull();
        assertThat(validated.getIn().getHeader(ApicurioRegistryConstants.HEADER_VALIDATION_RESULT)).isEqualTo(true);
    }

    private Exchange operation(String operation, String artifactId, String artifactType, String body) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(ApicurioRegistryConstants.HEADER_OPERATION, operation);
        headers.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        if (artifactId != null) {
            headers.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        }
        if (artifactType != null) {
            headers.put(ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, artifactType);
        }
        return template.request("direct:operation", exchange -> {
            exchange.getIn().setHeaders(headers);
            exchange.getIn().setBody(body);
        });
    }
}
