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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.openai.OpenAIConstants;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingService;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the camel-openai component with external MCP (Model Context Protocol) tools.
 *
 * <p>
 * Uses the MCP Everything Server (Streamable HTTP transport) configured via endpoint URI parameters. The LLM backend is
 * Ollama (via {@link OpenAITestSupport}).
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class OpenAIMcpToolsIT extends OpenAITestSupport {

    @RegisterExtension
    static McpEverythingService MCP_EVERYTHING = McpEverythingServiceFactory.createService();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String mcpUrl = MCP_EVERYTHING.url();

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:mcp-chat")
                        .toF("openai:chat-completion"
                             + "?mcpServer.everything.transportType=streamableHttp"
                             + "&mcpServer.everything.url=%s"
                             + "&mcpProtocolVersions=2024-11-05,2025-03-26,2025-06-18",
                                mcpUrl)
                        .to("mock:response");
            }
        };
    }

    /**
     * Tests the echo tool from the MCP Everything Server.
     */
    @Test
    void testMcpEchoTool() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:mcp-chat",
                "Use the echo tool to echo the message 'Hello from Camel'.", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("hello") || response.toLowerCase().contains("camel"),
                "Response should contain echoed message from MCP echo tool but was: " + response);
    }

    /**
     * Tests the add tool from the MCP Everything Server.
     */
    @Test
    void testMcpAddTool() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:mcp-chat",
                "Use the add tool to add 17 and 25. What is the result?", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.contains("42"),
                "Response should contain the sum 42 from MCP add tool but was: " + response);
    }

    /**
     * Tests that agentic loop headers are set after tool execution.
     */
    @Test
    void testAgenticLoopHeaders() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        Exchange result = template.request("direct:mcp-chat",
                e -> e.getIn().setBody("Use the add tool to add 10 and 5. What is the result?"));

        mockEndpoint.assertIsSatisfied();

        Integer iterations = result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class);
        assertNotNull(iterations, "TOOL_ITERATIONS header should be set");
        assertTrue(iterations >= 1, "At least one tool iteration should have occurred");

        @SuppressWarnings("unchecked")
        List<String> toolCalls = result.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class);
        assertNotNull(toolCalls, "MCP_TOOL_CALLS header should be set");
        assertFalse(toolCalls.isEmpty(), "Tool calls list should not be empty");

        Boolean returnDirect = result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class);
        assertNotNull(returnDirect, "MCP_RETURN_DIRECT header should be set");
        assertFalse(returnDirect, "returnDirect should be false for normal tool execution");
    }

    /**
     * Tests that a prompt not requiring tools returns a normal text response without triggering the agentic loop.
     */
    @Test
    void testNoToolCallNeeded() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:mcp-chat",
                "What is Apache Camel? Answer in one sentence.", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertTrue(response.length() > 10, "Response should contain meaningful text but was: " + response);
    }
}
