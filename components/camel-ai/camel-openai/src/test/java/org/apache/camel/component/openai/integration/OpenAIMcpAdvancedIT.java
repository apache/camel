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
import java.util.Optional;

import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.openai.OpenAIConstants;
import org.apache.camel.component.openai.OpenAIEndpoint;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingService;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced integration tests for camel-openai MCP support: manual tool loop, maxToolIterations, returnDirect, and
 * conversation memory with tool calls.
 *
 * <p>
 * Uses the MCP Everything Server (Streamable HTTP) and Ollama as the LLM backend.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class OpenAIMcpAdvancedIT extends OpenAITestSupport {

    private static final String MCP_PROTOCOL_VERSIONS = "2024-11-05,2025-03-26,2025-06-18";

    @RegisterExtension
    static McpEverythingService MCP_EVERYTHING = McpEverythingServiceFactory.createService();

    private String returnDirectEndpointUri;

    @Override
    protected RouteBuilder createRouteBuilder() {
        String mcpUrl = MCP_EVERYTHING.url();
        String mcpConfig = "&mcpServer.everything.transportType=streamableHttp"
                           + "&mcpServer.everything.url=" + mcpUrl
                           + "&mcpProtocolVersions=" + MCP_PROTOCOL_VERSIONS;

        returnDirectEndpointUri = "openai:chat-completion?" + mcpConfig.substring(1);

        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route 1: autoToolExecution=false for manual tool handling
                from("direct:manual-loop")
                        .toF("openai:chat-completion?autoToolExecution=false%s", mcpConfig)
                        .to("mock:manual-result");

                // Route 2: maxToolIterations=1 to test iteration limit
                from("direct:max-iterations")
                        .toF("openai:chat-completion?maxToolIterations=1%s", mcpConfig)
                        .to("mock:max-result");

                // Route 3: returnDirect (returnDirectTools injected in test method)
                from("direct:return-direct")
                        .toF("openai:chat-completion?%s", mcpConfig.substring(1))
                        .to("mock:return-direct-result");

                // Route 4: conversationMemory=true for multi-turn with tool calls
                from("direct:memory")
                        .toF("openai:chat-completion?conversationMemory=true%s", mcpConfig)
                        .to("mock:memory-result");
            }
        };
    }

    /**
     * Tests that with autoToolExecution=false, the raw tool calls are returned in the body. This allows users to
     * implement their own tool execution logic using Camel EIPs.
     */
    @Test
    void testAutoToolExecutionFalseReturnsRawToolCalls() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:manual-result");
        mockEndpoint.expectedMessageCount(1);

        Exchange result = template.request("direct:manual-loop",
                e -> e.getIn().setBody("Use the add tool to add 3 and 4. What is the result?"));

        mockEndpoint.assertIsSatisfied();

        // The finish reason should indicate tool_calls
        String finishReason = result.getMessage().getHeader(OpenAIConstants.FINISH_REASON, String.class);
        assertEquals("tool_calls", finishReason, "Finish reason should be 'tool_calls'");

        // Body should be the raw Optional<List<ChatCompletionMessageToolCall>>
        Object body = result.getMessage().getBody();
        assertNotNull(body, "Body should not be null");
        assertInstanceOf(Optional.class, body, "Body should be Optional (from SDK toolCalls())");

        @SuppressWarnings("unchecked")
        Optional<List<ChatCompletionMessageToolCall>> toolCallsOpt = (Optional<List<ChatCompletionMessageToolCall>>) body;
        assertTrue(toolCallsOpt.isPresent(), "Tool calls should be present");

        List<ChatCompletionMessageToolCall> toolCalls = toolCallsOpt.get();
        assertFalse(toolCalls.isEmpty(), "Should have at least one tool call");

        // Verify the tool call structure is accessible for manual processing
        ChatCompletionMessageToolCall toolCall = toolCalls.get(0);
        assertNotNull(toolCall.asFunction().id(), "Tool call ID should be set");
        assertNotNull(toolCall.asFunction().function().name(), "Tool function name should be set");
        assertNotNull(toolCall.asFunction().function().arguments(), "Tool function arguments should be set");
    }

    /**
     * Tests that maxToolIterations limits the agentic loop. When the model requires more iterations than allowed, an
     * IllegalStateException is thrown.
     */
    @Test
    void testMaxToolIterationsExceeded() {
        // With maxToolIterations=1, the first tool call succeeds but if the model
        // requests a second tool call in a subsequent iteration, the limit is hit.
        // The system message instructs the model to always use tools one at a time.
        try {
            Exchange result = template.request("direct:max-iterations", e -> {
                e.getIn().setHeader(OpenAIConstants.SYSTEM_MESSAGE,
                        "You are a tool-calling assistant. You MUST use the available tools to answer. "
                                                                    + "You MUST call tools one at a time, sequentially. "
                                                                    + "NEVER answer without calling a tool first. "
                                                                    + "NEVER call multiple tools in a single response. "
                                                                    + "For each step, call exactly one tool and wait for the result.");
                e.getIn().setBody(
                        "Follow these steps exactly: "
                                  + "Step 1: Use the echo tool to echo 'step1'. "
                                  + "Step 2: After getting the echo result, use the add tool to add 10 and 20. "
                                  + "Report both results.");
            });

            // If the model resolved everything in 1 iteration (called tools in parallel
            // or answered without tools), verify the response is still valid
            String body = result.getMessage().getBody(String.class);
            assertNotNull(body, "Response body should not be null");
        } catch (CamelExecutionException e) {
            // Expected: maxToolIterations exceeded
            Throwable cause = e.getCause();
            assertInstanceOf(IllegalStateException.class, cause,
                    "Should throw IllegalStateException for max iterations");
            assertTrue(cause.getMessage().contains("Max tool iterations"),
                    "Exception message should mention max iterations but was: " + cause.getMessage());
        }
    }

    /**
     * Tests that when a tool has returnDirect=true, the agentic loop short-circuits and returns the tool result
     * directly without sending it back to the LLM.
     */
    @Test
    void testReturnDirect() throws InterruptedException {
        // Inject "echo" as a returnDirect tool via the public getter (returns mutable HashSet)
        OpenAIEndpoint endpoint = context.getEndpoint(returnDirectEndpointUri, OpenAIEndpoint.class);
        endpoint.getReturnDirectTools().add("echo");

        try {
            MockEndpoint mockEndpoint = getMockEndpoint("mock:return-direct-result");
            mockEndpoint.expectedMessageCount(1);

            Exchange result = template.request("direct:return-direct",
                    e -> e.getIn().setBody("Use the echo tool to echo 'direct response test'"));

            mockEndpoint.assertIsSatisfied();

            Boolean returnDirect = result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class);
            assertNotNull(returnDirect, "MCP_RETURN_DIRECT header should be set");
            assertTrue(returnDirect, "returnDirect should be true when tool has returnDirect annotation");

            Integer iterations = result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class);
            assertNotNull(iterations);
            assertEquals(1, iterations, "Should have completed in 1 iteration (short-circuited)");

            // Body should contain the raw tool result, not an LLM-generated summary
            String body = result.getMessage().getBody(String.class);
            assertNotNull(body);
            assertTrue(body.toLowerCase().equals("echo: direct response test"),
                    "Body should contain the raw tool result but was: " + body);
        } finally {
            // Cleanup: remove to avoid affecting other tests
            endpoint.getReturnDirectTools().remove("echo");
        }
    }

    /**
     * Tests that conversation memory preserves the full tool call chain across turns, enabling multi-turn agentic
     * conversations where the model references previous tool interactions.
     */
    @Test
    void testConversationMemoryWithMcpTools() throws InterruptedException {
        // Turn 1: Use a tool and get a result
        MockEndpoint mockEndpoint = getMockEndpoint("mock:memory-result");
        mockEndpoint.expectedMessageCount(1);

        Exchange turn1 = template.request("direct:memory",
                e -> e.getIn().setBody("Use the add tool to add 15 and 27. What is the result?"));

        mockEndpoint.assertIsSatisfied();

        String turn1Response = turn1.getMessage().getBody(String.class);
        assertNotNull(turn1Response);
        assertTrue(turn1Response.contains("42"),
                "Turn 1 response should contain 42 but was: " + turn1Response);

        // Verify conversation history was stored
        List<?> history = turn1.getProperty("CamelOpenAIConversationHistory", List.class);
        assertNotNull(history, "Conversation history should be stored");
        // History should contain: assistant+toolCalls, tool result, final assistant (at least 3)
        assertTrue(history.size() >= 3,
                "History should contain at least 3 entries (tool call chain + final) but had: " + history.size());

        // Turn 2: Send follow-up with the conversation history from turn 1
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(1);

        Exchange turn2 = template.request("direct:memory", e -> {
            e.getIn().setBody("What was the result of the calculation you just did?");
            e.setProperty("CamelOpenAIConversationHistory", history);
        });

        mockEndpoint.assertIsSatisfied();

        String turn2Response = turn2.getMessage().getBody(String.class);
        assertNotNull(turn2Response);
        assertTrue(turn2Response.contains("42"),
                "Turn 2 should reference the previous result 42 but was: " + turn2Response);
    }
}
