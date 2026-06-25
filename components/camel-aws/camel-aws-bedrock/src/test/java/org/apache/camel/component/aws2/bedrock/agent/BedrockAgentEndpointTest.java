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
package org.apache.camel.component.aws2.bedrock.agent;

import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class BedrockAgentEndpointTest {

    @Mock
    private BedrockAgentClient bedrockAgentClient;

    private CamelContext camelContext;

    @BeforeEach
    public void setup() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("bedrockAgentClient", bedrockAgentClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void ingestionEndpointResolvesWithoutModelId() {
        // modelId is irrelevant to the ingestion operations served by this component and must no longer be
        // required (CAMEL-23464): the endpoint has to resolve cleanly without it.
        BedrockAgentEndpoint endpoint = (BedrockAgentEndpoint) camelContext.getEndpoint(
                "aws-bedrock-agent://label"
                                                                                        + "?bedrockAgentClient=#bedrockAgentClient"
                                                                                        + "&operation=listIngestionJobs"
                                                                                        + "&knowledgeBaseId=kb-1"
                                                                                        + "&dataSourceId=ds-1"
                                                                                        + "&region=us-east-1"
                                                                                        + "&accessKey=unused"
                                                                                        + "&secretKey=unused");

        BedrockAgentConfiguration config = endpoint.getConfiguration();
        assertEquals(BedrockAgentOperations.listIngestionJobs, config.getOperation());
        assertEquals("kb-1", config.getKnowledgeBaseId());
        assertEquals("ds-1", config.getDataSourceId());
        assertSame(bedrockAgentClient, config.getBedrockAgentClient());
    }

    @Test
    public void modelIdOptionIsNoLongerSupported() {
        // modelId was an unused copy-paste artifact from aws-bedrock-agent-runtime and has been removed
        // (CAMEL-23464). Supplying it must now fail endpoint resolution instead of being silently accepted.
        ResolveEndpointFailedException ex = assertThrows(ResolveEndpointFailedException.class,
                () -> camelContext.getEndpoint(
                        "aws-bedrock-agent://label"
                                               + "?bedrockAgentClient=#bedrockAgentClient"
                                               + "&operation=listIngestionJobs"
                                               + "&modelId=anthropic.claude-v2"
                                               + "&region=us-east-1"
                                               + "&accessKey=unused"
                                               + "&secretKey=unused"));
        assertTrue(ex.getMessage().contains("modelId"), "Failure should mention the removed modelId option");
    }
}
