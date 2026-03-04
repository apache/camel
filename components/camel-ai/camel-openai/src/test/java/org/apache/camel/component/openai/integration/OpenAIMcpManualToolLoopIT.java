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
package org.apache.camel.component.openai.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.openai.OpenAIConstants;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingService;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test demonstrating how to implement a manual tool execution loop using pure Camel DSL with the
 * {@code openai:tool-execution} operation.
 *
 * <p>
 * This test shows the pattern for users who want full control over tool execution — for example, to add logging,
 * filtering, retry logic, or custom routing between tool calls — without writing any Java Processor.
 *
 * <p>
 * The route uses:
 * <ul>
 * <li>{@code openai:chat-completion?autoToolExecution=false} — returns raw tool calls instead of auto-executing</li>
 * <li>{@code loopDoWhile} — repeats while the model keeps requesting tools</li>
 * <li>{@code openai:tool-execution} — executes MCP tool calls and rebuilds conversation history</li>
 * <li>{@code conversationMemory=true} + {@code storeFullResponse=true} — state management across loop iterations</li>
 * </ul>
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class OpenAIMcpManualToolLoopIT extends OpenAITestSupport {

    private static final String MCP_PROTOCOL_VERSIONS = "2024-11-05,2025-03-26,2025-06-18";

    @RegisterExtension
    static McpEverythingService MCP_EVERYTHING = McpEverythingServiceFactory.createService();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String mcpUrl = MCP_EVERYTHING.url();
        String mcpConfig = "mcpServer.everything.transportType=streamableHttp"
                           + "&mcpServer.everything.url=" + mcpUrl
                           + "&mcpProtocolVersions=" + MCP_PROTOCOL_VERSIONS;

        String chatEndpointUri = "openai:chat-completion"
                                 + "?autoToolExecution=false"
                                 + "&conversationMemory=true"
                                 + "&storeFullResponse=true"
                                 + "&" + mcpConfig;

        String toolExecutionUri = "openai:tool-execution?" + mcpConfig;

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:manual-tool-loop")
                        // Save the original prompt for the tool-execution operation
                        .setProperty("originalPrompt", body())

                        // Step 1: Initial call to OpenAI (tools are listed but not auto-executed)
                        .to(chatEndpointUri)

                        // Step 2: Loop while the model requests tool calls
                        .loopDoWhile(header(OpenAIConstants.FINISH_REASON).isEqualTo("tool_calls"))
                            // Execute tool calls via MCP and rebuild conversation history
                            .to(toolExecutionUri)
                            // Send the updated conversation back to the model
                            .to(chatEndpointUri)
                        .end()

                        // Step 3: Final text response is in the body
                        .to("mock:result");
            }
        };
    }

    /**
     * Tests the manual tool loop: the model calls the "add" tool, the tool-execution operation executes it via MCP, and
     * the model produces the final text answer.
     */
    @Test
    void testManualToolLoopWithAddTool() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:manual-tool-loop",
                "Use the add tool to add 17 and 25. What is the result?", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.contains("42"),
                "Response should contain the sum 42 but was: " + response);
    }

    /**
     * Tests the manual tool loop with the echo tool.
     */
    @Test
    void testManualToolLoopWithEchoTool() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:manual-tool-loop",
                "Use the echo tool to echo 'Hello from Camel'.", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("hello") || response.toLowerCase().contains("camel"),
                "Response should contain echoed content but was: " + response);
    }
}
