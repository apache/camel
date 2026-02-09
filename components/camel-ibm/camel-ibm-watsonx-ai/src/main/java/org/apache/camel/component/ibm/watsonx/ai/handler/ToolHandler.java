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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.watsonx.ai.chat.ToolExecutor;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ToolMessage;
import com.ibm.watsonx.ai.tool.ToolRequest;
import com.ibm.watsonx.ai.tool.ToolService;
import com.ibm.watsonx.ai.tool.UtilityTool;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Handler for tool operations.
 */
public class ToolHandler extends AbstractWatsonxAiHandler {

    public ToolHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        switch (operation) {
            case runTool:
                return processRunTool(exchange);
            case listTools:
                return processListTools(exchange);
            case processToolCalls:
                return processToolCalls(exchange);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] {
                WatsonxAiOperations.runTool,
                WatsonxAiOperations.listTools,
                WatsonxAiOperations.processToolCalls
        };
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processRunTool(Exchange exchange) {
        Message in = exchange.getIn();

        // Get tool request from header or body
        ToolRequest toolRequest = in.getHeader(WatsonxAiConstants.TOOL_REQUEST, ToolRequest.class);

        if (toolRequest == null) {
            // Try to build from tool name and input
            String toolName = in.getHeader(WatsonxAiConstants.TOOL_NAME, String.class);
            if (toolName == null || toolName.isEmpty()) {
                throw new IllegalArgumentException(
                        "Tool request must be provided via header '" + WatsonxAiConstants.TOOL_REQUEST
                                                   + "' or tool name via '" + WatsonxAiConstants.TOOL_NAME + "'");
            }

            Object body = in.getBody();
            Map<String, Object> config = in.getHeader(WatsonxAiConstants.TOOL_CONFIG, Map.class);

            if (body instanceof Map) {
                // Structured input
                toolRequest = ToolRequest.structuredInput(toolName, (Map<String, Object>) body, config);
            } else if (body instanceof String bodyString) {
                // Unstructured input
                toolRequest = ToolRequest.unstructuredInput(toolName, bodyString, config);
            } else {
                throw new IllegalArgumentException(
                        "Tool input must be provided as Map (structured) or String (unstructured) in message body");
            }
        }

        // Call the service
        ToolService service = endpoint.getToolService();
        String result = service.run(toolRequest);

        return WatsonxAiOperationResponse.create(result);
    }

    private WatsonxAiOperationResponse processListTools(Exchange exchange) {
        // Call the service
        ToolService service = endpoint.getToolService();
        ToolService.Resources response = service.getAll();

        List<UtilityTool> tools = response.resources();

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.UTILITY_TOOLS, tools);

        return WatsonxAiOperationResponse.create(tools, headers);
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processToolCalls(Exchange exchange) {
        Message in = exchange.getIn();

        // Get the assistant message from header
        AssistantMessage assistantMessage = in.getHeader(WatsonxAiConstants.ASSISTANT_MESSAGE, AssistantMessage.class);
        if (assistantMessage == null) {
            throw new IllegalArgumentException(
                    "Assistant message must be provided via header '" + WatsonxAiConstants.ASSISTANT_MESSAGE + "'");
        }

        // Check if there are tool calls to process
        if (!assistantMessage.hasToolCalls()) {
            // No tool calls, just pass through
            return WatsonxAiOperationResponse.create(in.getBody());
        }

        // Get the tool executor from header (ToolRegistry implements ToolExecutor)
        ToolExecutor toolExecutor = in.getHeader(WatsonxAiConstants.TOOL_REGISTRY, ToolExecutor.class);
        if (toolExecutor == null) {
            throw new IllegalArgumentException(
                    "Tool executor must be provided via header '" + WatsonxAiConstants.TOOL_REGISTRY
                                               + "' (use ToolRegistry)");
        }

        // Get current messages from header
        List<ChatMessage> messages = in.getHeader(WatsonxAiConstants.MESSAGES, List.class);
        if (messages == null) {
            messages = new ArrayList<>();
        } else {
            // Create a mutable copy
            messages = new ArrayList<>(messages);
        }

        // Add the assistant message to the conversation
        messages.add(assistantMessage);

        // Execute tools and get results
        List<ToolMessage> toolMessages = assistantMessage.processTools(toolExecutor);
        messages.addAll(toolMessages);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        // Update messages header with the new messages including tool results
        headers.put(WatsonxAiConstants.MESSAGES, messages);

        // Clear the tool calls flags since we've processed them
        headers.put(WatsonxAiConstants.HAS_TOOL_CALLS, false);
        headers.put(WatsonxAiConstants.TOOL_CALLS, null);
        headers.put(WatsonxAiConstants.ASSISTANT_MESSAGE, null);

        // Preserve tool registry
        headers.put(WatsonxAiConstants.TOOL_REGISTRY, toolExecutor);
        // Preserve tools header if present
        Object tools = in.getHeader(WatsonxAiConstants.TOOLS);
        if (tools != null) {
            headers.put(WatsonxAiConstants.TOOLS, tools);
        }

        return WatsonxAiOperationResponse.create(in.getBody(), headers);
    }
}
