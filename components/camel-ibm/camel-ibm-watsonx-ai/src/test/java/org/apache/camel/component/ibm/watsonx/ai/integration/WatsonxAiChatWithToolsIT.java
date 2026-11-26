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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ibm.watsonx.ai.chat.ToolRegistry;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.TextContent;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.chat.model.ToolMessage;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import com.ibm.watsonx.ai.tool.ToolService;
import com.ibm.watsonx.ai.tool.builtin.WikipediaTool;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for chat with tool calling capabilities.
 * <p>
 * This test demonstrates how to:
 * <ul>
 * <li>Configure a chat with available tools</li>
 * <li>Detect when the LLM wants to call a tool</li>
 * <li>Execute the tool and continue the conversation</li>
 * </ul>
 *
 * Flow Summary: 1. Tool Registration (line 1): Wikipedia tool registered successfully 2. User Question: "What is Apache
 * Camel? Use the wikipedia tool to find out." 3. LLM Tool Call (line 9): The LLM recognized it needed the Wikipedia
 * tool 4. Tool Execution (lines 10-12): - Tool: wikipedia - Args: {"query":"Apache Camel"} - Completed successfully 5.
 * Conversation Continued (line 12): 1 tool executed, conversation resumed 6. Final Response (lines 13-17): LLM
 * synthesized the Wikipedia content into a comprehensive answer about Apache Camel
 *
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.wxUrl", matches = ".+",
                                 disabledReason = "IBM watsonx.ai WX URL not provided")
})
public class WatsonxAiChatWithToolsIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiChatWithToolsIT.class);

    private static String wxUrl;
    private static ToolService toolService;
    private static ToolRegistry toolRegistry;
    private static List<Tool> tools;
    private static final List<String> EXECUTED_TOOLS = new CopyOnWriteArrayList<>();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeAll
    public static void setupToolService() {
        wxUrl = System.getProperty("camel.ibm.watsonx.ai.wxUrl");

        // Create ToolService for the utility tools
        toolService = ToolService.builder()
                .apiKey(apiKey)
                .baseUrl(wxUrl)
                .build();

        // Create ToolRegistry with Wikipedia tool
        toolRegistry = ToolRegistry.builder()
                .register(new WikipediaTool(toolService))
                .beforeExecution((name, args) -> LOG.info("Executing tool: {} with args: {}", name, args))
                .afterExecution((name, args, result) -> {
                    LOG.info("Tool {} completed", name);
                    EXECUTED_TOOLS.add(name);
                })
                .build();

        // Get tool schemas for chat
        tools = toolRegistry.tools();
        LOG.info("Registered {} tools for chat", tools.size());
    }

    @BindToRegistry("toolRegistry")
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    @BindToRegistry("tools")
    public List<Tool> getTools() {
        return tools;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testChatWithToolCalling() throws Exception {
        mockResult.expectedMinimumMessageCount(1);

        // Start the conversation asking about something that requires Wikipedia lookup
        String question = "What is Apache Camel? Use the wikipedia tool to find out.";

        Exchange result = template.send("direct:chatWithTools", exchange -> {
            exchange.getIn().setBody(question);
        });

        mockResult.assertIsSatisfied();

        // Get the final response
        Exchange finalExchange = mockResult.getExchanges().get(0);
        String response = finalExchange.getIn().getBody(String.class);

        LOG.info("Question: {}", question);
        LOG.info("Final response: {}", response);

        assertNotNull(response, "Response should not be null");
        assertFalse(response.isEmpty(), "Response should not be empty");

        // the tool ran, and the last request sent the whole conversation: the system message and the question of the
        // body, then the tool calls and their results
        assertTrue(EXECUTED_TOOLS.contains("wikipedia"), "The wikipedia tool should have run: " + EXECUTED_TOOLS);
        List<ChatMessage> conversation = finalExchange.getIn().getHeader(WatsonxAiConstants.MESSAGES, List.class);
        LOG.info("Conversation sent with the tool results: {}", conversation);
        assertTrue(conversation.size() >= 4, "The conversation should go on from the question: " + conversation);
        assertInstanceOf(SystemMessage.class, conversation.get(0));
        UserMessage user = assertInstanceOf(UserMessage.class, conversation.get(1));
        assertEquals(question, ((TextContent) user.content().get(0)).text());
        assertTrue(assertInstanceOf(AssistantMessage.class, conversation.get(2)).hasToolCalls());
        assertInstanceOf(ToolMessage.class, conversation.get(3));
        assertNull(finalExchange.getIn().getHeader(WatsonxAiConstants.TOOL_CALLS));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Main chat route with tools - fully declarative, no processors needed
                from("direct:chatWithTools")
                        // Set system message, tools, and tool registry via headers
                        // The body contains the user question
                        .setHeader(WatsonxAiConstants.SYSTEM_MESSAGE,
                                constant("You are a helpful assistant. Use the available tools when needed to answer questions accurately."))
                        .setHeader(WatsonxAiConstants.TOOLS, constant(tools))
                        .setHeader(WatsonxAiConstants.TOOL_REGISTRY, constant(toolRegistry))
                        // Send to chat endpoint
                        .to(buildChatEndpointUri())
                        // Check if we need to process tool calls
                        .loopDoWhile(simple("${header." + WatsonxAiConstants.HAS_TOOL_CALLS + "} == true"))
                            .log("LLM requested tool calls, processing...")
                            // Process tool calls using the processToolCalls operation
                            .to("ibm-watsonx-ai://tools?operation=processToolCalls")
                            // Call chat again with updated messages
                            .to(buildChatEndpointUri())
                        .end()
                        .log("Chat completed. Response: ${body}")
                        .to("mock:result");
            }
        };
    }

    private String buildChatEndpointUri() {
        StringBuilder uri = new StringBuilder("ibm-watsonx-ai://chat");
        uri.append("?apiKey=RAW(").append(apiKey).append(")");
        uri.append("&baseUrl=").append(baseUrl);
        uri.append("&projectId=").append(projectId);
        uri.append("&modelId=ibm/granite-4-h-small");
        uri.append("&operation=chat");
        return uri.toString();
    }
}
