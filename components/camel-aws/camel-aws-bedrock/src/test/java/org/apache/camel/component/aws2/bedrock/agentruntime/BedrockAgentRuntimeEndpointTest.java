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
package org.apache.camel.component.aws2.bedrock.agentruntime;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class BedrockAgentRuntimeEndpointTest {

    @Mock
    private BedrockAgentRuntimeClient syncClient;

    @Mock
    private BedrockAgentRuntimeAsyncClient asyncClient;

    private CamelContext camelContext;

    @BeforeEach
    public void setup() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("syncClient", syncClient);
        registry.bind("asyncClient", asyncClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void invokeFlowEndpointParsesFlowOptions() {
        BedrockAgentRuntimeEndpoint endpoint = (BedrockAgentRuntimeEndpoint) camelContext.getEndpoint(
                "aws-bedrock-agent-runtime://label"
                                                                                                      + "?bedrockAgentRuntimeAsyncClient=#asyncClient"
                                                                                                      + "&operation=invokeFlow"
                                                                                                      + "&flowIdentifier=flow-abc"
                                                                                                      + "&flowAliasIdentifier=alias-xyz"
                                                                                                      + "&enableTrace=true"
                                                                                                      + "&region=us-east-1"
                                                                                                      + "&accessKey=unused"
                                                                                                      + "&secretKey=unused");

        BedrockAgentRuntimeConfiguration config = endpoint.getConfiguration();
        assertEquals(BedrockAgentRuntimeOperations.invokeFlow, config.getOperation());
        assertEquals("flow-abc", config.getFlowIdentifier());
        assertEquals("alias-xyz", config.getFlowAliasIdentifier());
        assertTrue(config.isEnableTrace());
        assertSame(asyncClient, config.getBedrockAgentRuntimeAsyncClient());
    }

    @Test
    public void invokeFlowEndpointBuildsAsyncClientOnStart() throws Exception {
        BedrockAgentRuntimeEndpoint endpoint = (BedrockAgentRuntimeEndpoint) camelContext.getEndpoint(
                "aws-bedrock-agent-runtime://label"
                                                                                                      + "?bedrockAgentRuntimeAsyncClient=#asyncClient"
                                                                                                      + "&operation=invokeFlow"
                                                                                                      + "&flowIdentifier=flow-abc"
                                                                                                      + "&flowAliasIdentifier=alias-xyz"
                                                                                                      + "&region=us-east-1"
                                                                                                      + "&accessKey=unused"
                                                                                                      + "&secretKey=unused");

        endpoint.start();
        try {
            assertNotNull(endpoint.getBedrockAgentRuntimeAsyncClient(), "Async client should be wired for invokeFlow");
            assertSame(asyncClient, endpoint.getBedrockAgentRuntimeAsyncClient());
        } finally {
            endpoint.stop();
        }
    }

    @Test
    public void retrieveAndGenerateEndpointDoesNotBuildAsyncClient() throws Exception {
        // Build a context that does NOT have an async client autowired so we can prove the endpoint refrains from
        // creating one (creating one would allocate a Netty HTTP client and event-loop thread pool — the cost we
        // want to avoid for users that only ever call retrieveAndGenerate).
        SimpleRegistry localRegistry = new SimpleRegistry();
        localRegistry.bind("syncClient", syncClient);
        CamelContext localContext = new DefaultCamelContext(localRegistry);

        BedrockAgentRuntimeEndpoint endpoint = (BedrockAgentRuntimeEndpoint) localContext.getEndpoint(
                "aws-bedrock-agent-runtime://label"
                                                                                                      + "?bedrockAgentRuntimeClient=#syncClient"
                                                                                                      + "&operation=retrieveAndGenerate"
                                                                                                      + "&modelId=anthropic.claude-instant-v1"
                                                                                                      + "&knowledgeBaseId=kb-1"
                                                                                                      + "&region=us-east-1"
                                                                                                      + "&accessKey=unused"
                                                                                                      + "&secretKey=unused");

        endpoint.start();
        try {
            assertNull(endpoint.getBedrockAgentRuntimeAsyncClient(),
                    "Async client must not be built when operation does not require event streaming");
        } finally {
            endpoint.stop();
            localContext.stop();
        }
    }
}
