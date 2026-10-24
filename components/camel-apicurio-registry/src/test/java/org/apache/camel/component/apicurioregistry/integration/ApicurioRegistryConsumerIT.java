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
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.apicurioregistry.ApicurioRegistryConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApicurioRegistryConsumerIT extends ApicurioRegistryTestSupport {

    private static final String JSON_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {"type": "string"}
                }
            }
            """;

    private final String groupId = "consumer-test-" + UUID.randomUUID();
    private final String artifactId = "consumer-artifact-" + UUID.randomUUID();

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Producer route to create artifacts
                from("direct:createForConsumer")
                        .toD("apicurio-registry:${header.CamelApicurioRegistryGroupId}/${header.CamelApicurioRegistryArtifactId}"
                             + "?registryUrl=" + getRegistryUrl());

                // Consumer route
                from("apicurio-registry:" + groupId + "/" + artifactId
                     + "?registryUrl=" + getRegistryUrl() + "&delay=1000&fetchContent=true")
                        .to("mock:consumed");
            }
        };
    }

    @Test
    void testConsumerReceivesNewVersions() throws Exception {
        // Create the group first
        Map<String, Object> groupHeaders = new HashMap<>();
        groupHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_CREATE_GROUP);
        groupHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        Exchange group = template.request("direct:createForConsumer", exchange -> exchange.getIn().setHeaders(groupHeaders));
        assertThat(group.getException()).isNull();

        // Create the artifact
        Map<String, Object> createHeaders = new HashMap<>();
        createHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_CREATE_ARTIFACT);
        createHeaders.put(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
        createHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
        createHeaders.put(ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, "JSON");
        createHeaders.put(ApicurioRegistryConstants.HEADER_IF_EXISTS, "FIND_OR_CREATE_VERSION");
        Exchange artifact = template.request("direct:createForConsumer", exchange -> {
            exchange.getIn().setHeaders(createHeaders);
            exchange.getIn().setBody(JSON_SCHEMA);
        });
        assertThat(artifact.getException()).isNull();

        MockEndpoint mock = getMockEndpoint("mock:consumed");
        mock.expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        // Verify headers set by consumer
        Map<String, Object> receivedHeaders = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertThat(receivedHeaders.get(ApicurioRegistryConstants.HEADER_GROUP_ID)).isEqualTo(groupId);
        assertThat(receivedHeaders.get(ApicurioRegistryConstants.HEADER_ARTIFACT_ID)).isEqualTo(artifactId);
        assertThat(mock.getExchanges().get(0).getIn().getBody(String.class)).isEqualTo(JSON_SCHEMA);

        createHeaders.put(ApicurioRegistryConstants.HEADER_OPERATION, ApicurioRegistryConstants.OPERATION_UPDATE_ARTIFACT);
        String secondSchema = "{\"type\":\"string\"}";
        Exchange update = template.request("direct:createForConsumer", exchange -> {
            exchange.getIn().setHeaders(createHeaders);
            exchange.getIn().setBody(secondSchema);
        });
        assertThat(update.getException()).isNull();
        mock.expectedMessageCount(2);
        mock.setAssertPeriod(1500);
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
        assertThat(mock.getExchanges().get(1).getIn().getBody(String.class)).isEqualTo(secondSchema);
        assertThat(mock.getExchanges().get(1).getIn().getHeader(ApicurioRegistryConstants.HEADER_GLOBAL_ID, Long.class))
                .isGreaterThan((Long) receivedHeaders.get(ApicurioRegistryConstants.HEADER_GLOBAL_ID));
    }

}
