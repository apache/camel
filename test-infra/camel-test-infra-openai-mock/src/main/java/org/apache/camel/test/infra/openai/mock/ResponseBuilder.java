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

package org.apache.camel.test.infra.openai.mock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

/**
 * Builder class for creating different types of OpenAI API mock responses.
 */
public class ResponseBuilder {
    private final ObjectMapper objectMapper;

    public ResponseBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createSimpleTextResponse(String content) throws Exception {
        Map<String, Object> responseMessage = createBaseMessage();
        responseMessage.put("content", content);

        Map<String, Object> choice = createBaseChoice("stop", responseMessage);
        Map<String, Object> chatCompletion = createBaseChatCompletion(choice);

        return objectMapper.writeValueAsString(chatCompletion);
    }

    public String createToolCallResponse(String content, List<ToolCallDefinition> toolCalls) throws Exception {
        Map<String, Object> responseMessage = createBaseMessage();
        responseMessage.put("content", content);
        responseMessage.put("tool_calls", buildToolCallsList(toolCalls));

        Map<String, Object> choice = createBaseChoice("tool_calls", responseMessage);
        Map<String, Object> chatCompletion = createBaseChatCompletion(choice);

        return objectMapper.writeValueAsString(chatCompletion);
    }

    public String createFinalToolResponse(JsonNode messagesNode, String fallbackContent, String toolContentResponse)
            throws Exception {
        Map<String, Object> responseMessage = createBaseMessage();

        String content;
        if (fallbackContent != null) {
            content = fallbackContent;
        } else if (toolContentResponse != null) {
            String toolContent = extractLastToolContent(messagesNode).orElse("");
            content = toolContent + " " + toolContentResponse;
        } else {
            content = extractLastToolContent(messagesNode).orElse("All tools processed");
        }
        responseMessage.put("content", content);

        Map<String, Object> choice = createBaseChoice("stop", responseMessage);
        Map<String, Object> chatCompletion = createBaseChatCompletion(choice);
        chatCompletion.put("history", messagesNode);

        return objectMapper.writeValueAsString(chatCompletion);
    }

    public String createFinalToolResponse(JsonNode messagesNode, String fallbackContent) throws Exception {
        return createFinalToolResponse(messagesNode, fallbackContent, null);
    }

    public String createErrorResponse(int statusCode, String errorMessage, HttpExchange exchange) {
        String jsonErrorMessage = String.format("{\"error\": \"%s\"}", errorMessage);

        try {
            exchange.sendResponseHeaders(statusCode, jsonErrorMessage.length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return String.format("{\"error\": \"%s\"}", errorMessage);
    }

    private Map<String, Object> createBaseMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("refusal", null);
        return message;
    }

    private Map<String, Object> createBaseChoice(String finishReason, Map<String, Object> message) {
        Map<String, Object> choice = new HashMap<>();
        choice.put("finish_reason", finishReason);
        choice.put("index", 0);
        choice.put("message", message);
        return choice;
    }

    private Map<String, Object> createBaseChatCompletion(Map<String, Object> choice) {
        Map<String, Object> chatCompletion = new HashMap<>();
        chatCompletion.put("id", UUID.randomUUID().toString());
        chatCompletion.put("choices", Collections.singletonList(choice));
        chatCompletion.put("created", System.currentTimeMillis() / 1000L);
        chatCompletion.put("model", "openai-mock");
        chatCompletion.put("object", "chat.completion");
        return chatCompletion;
    }

    private List<Map<String, Object>> buildToolCallsList(List<ToolCallDefinition> toolCalls) throws Exception {
        List<Map<String, Object>> toolCallsList = new ArrayList<>();

        for (ToolCallDefinition toolCall : toolCalls) {
            String argumentsJson = objectMapper.writeValueAsString(toolCall.getArguments());

            Map<String, Object> functionObject = new HashMap<>();
            functionObject.put("name", toolCall.getName());
            functionObject.put("arguments", argumentsJson);

            Map<String, Object> toolCallItem = new HashMap<>();
            toolCallItem.put("id", UUID.randomUUID().toString());
            toolCallItem.put("type", "function");
            toolCallItem.put("function", functionObject);

            toolCallsList.add(toolCallItem);
        }

        return toolCallsList;
    }

    private Optional<String> extractLastToolContent(JsonNode messagesNode) {
        return StreamSupport.stream(messagesNode.spliterator(), false)
                .filter(entry ->
                        entry.has("role") && "tool".equals(entry.get("role").asText()))
                .reduce((first, second) -> second) // Get the last one
                .map(entry -> entry.get("content"))
                .map(JsonNode::asText);
    }
}
