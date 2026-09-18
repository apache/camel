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
package org.apache.camel.component.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.ResponsesModel;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * Helpers for the OpenAI Responses API producer.
 */
final class OpenAIResponsesSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OpenAIResponsesSupport() {
    }

    static void applyBuiltinTools(
            ResponseCreateParams.Builder paramsBuilder, String builtinTools,
            String fileSearchVectorStoreIds) {
        if (ObjectHelper.isEmpty(builtinTools)) {
            return;
        }
        for (String token : builtinTools.split(",")) {
            String name = token.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            switch (name) {
                case "web_search" -> paramsBuilder.addTool(WebSearchTool.builder()
                        .type(WebSearchTool.Type.WEB_SEARCH)
                        .build());
                case "file_search" -> {
                    FileSearchTool.Builder fsBuilder = FileSearchTool.builder()
                            .type(JsonValue.from("file_search"));
                    if (ObjectHelper.isEmpty(fileSearchVectorStoreIds)) {
                        throw new IllegalArgumentException(
                                "fileSearchVectorStoreIds must be set when builtinTools includes file_search");
                    }
                    int ids = 0;
                    for (String id : fileSearchVectorStoreIds.split(",")) {
                        String trimmed = id.trim();
                        if (!trimmed.isEmpty()) {
                            fsBuilder.addVectorStoreId(trimmed);
                            ids++;
                        }
                    }
                    if (ids == 0) {
                        throw new IllegalArgumentException(
                                "fileSearchVectorStoreIds must contain at least one vector store id for file_search");
                    }
                    paramsBuilder.addTool(fsBuilder.build());
                }
                case "code_interpreter" -> paramsBuilder.addTool(Tool.CodeInterpreter.builder()
                        .type(JsonValue.from("code_interpreter"))
                        .container("auto")
                        .build());
                default -> throw new IllegalArgumentException(
                        "Unknown builtin tool '" + token.trim()
                                                              + "'. Supported: web_search, file_search, code_interpreter");
            }
        }
    }

    static void applyHostedMcpTools(ResponseCreateParams.Builder paramsBuilder, String hostedMcpToolsJson)
            throws Exception {
        if (ObjectHelper.isEmpty(hostedMcpToolsJson)) {
            return;
        }
        JsonNode root = OBJECT_MAPPER.readTree(hostedMcpToolsJson);
        if (!root.isArray()) {
            throw new IllegalArgumentException("hostedMcpTools must be a JSON array of MCP tool objects");
        }
        for (JsonNode node : root) {
            if (!(node instanceof ObjectNode tool)) {
                throw new IllegalArgumentException("hostedMcpTools must be a JSON array of MCP tool objects");
            }
            ObjectNode mcpTool = tool.deepCopy();
            mcpTool.put("type", "mcp");
            // camelCase names were accepted for these fields before every API field was passed through
            renameField(mcpTool, "serverLabel", "server_label");
            renameField(mcpTool, "serverUrl", "server_url");
            renameField(mcpTool, "serverDescription", "server_description");
            paramsBuilder.addTool(ObjectMappers.jsonMapper().treeToValue(mcpTool, Tool.Mcp.class));
        }
    }

    private static void renameField(ObjectNode node, String from, String to) {
        if (node.has(from) && !node.has(to)) {
            node.set(to, node.remove(from));
        }
    }

    /**
     * Converts a tool of the endpoint tool state, which holds the MCP and route tools as chat completion tools, into a
     * Responses API function tool.
     */
    static FunctionTool toFunctionTool(ChatCompletionFunctionTool tool) {
        FunctionDefinition function = tool.function();
        FunctionTool.Parameters.Builder parameters = FunctionTool.Parameters.builder();
        function.parameters().ifPresent(schema -> parameters.putAllAdditionalProperties(schema._additionalProperties()));
        FunctionTool.Builder builder = FunctionTool.builder()
                .name(function.name())
                .parameters(parameters.build())
                .strict(false);
        function.description().ifPresent(builder::description);
        return builder.build();
    }

    static void applyJsonSchemaTextFormat(ResponseCreateParams.Builder paramsBuilder, String jsonSchema)
            throws Exception {
        Map<String, Object> root = OBJECT_MAPPER.readValue(jsonSchema, Map.class);
        if (root == null) {
            throw new IllegalArgumentException("JSON schema string parsed to null");
        }
        ResponseFormatTextJsonSchemaConfig.Schema.Builder schemaBuilder
                = ResponseFormatTextJsonSchemaConfig.Schema.builder();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            schemaBuilder.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
        }
        ResponseFormatTextJsonSchemaConfig jsonSchemaConfig = ResponseFormatTextJsonSchemaConfig.builder()
                .name("camel_schema")
                .schema(schemaBuilder.build())
                .build();
        paramsBuilder.text(
                ResponseTextConfig.builder()
                        .format(ResponseFormatTextConfig.ofJsonSchema(jsonSchemaConfig))
                        .build());
    }

    static void applyAdditionalBodyProperties(ResponseCreateParams.Builder paramsBuilder, Map<String, Object> additional) {
        if (additional == null || additional.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : additional.entrySet()) {
            Object valueToUse = e.getValue();
            if (valueToUse instanceof String s) {
                valueToUse = parseJsonOrString(s);
            }
            paramsBuilder.putAdditionalBodyProperty(e.getKey(), JsonValue.from(valueToUse));
        }
    }

    private static Object parseJsonOrString(String value) {
        try {
            return OBJECT_MAPPER.readValue(value, Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    static String extractAssistantText(Response response) {
        StringBuilder text = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            ResponseOutputMessage message = item.asMessage();
            for (ResponseOutputMessage.Content content : message.content()) {
                if (content.isOutputText()) {
                    ResponseOutputText outputText = content.asOutputText();
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(outputText.text());
                }
            }
        }
        return text.toString();
    }

    static List<ResponseFunctionToolCall> extractFunctionCalls(Response response) {
        return response.output().stream()
                .filter(ResponseOutputItem::isFunctionCall)
                .map(ResponseOutputItem::asFunctionCall)
                .toList();
    }

    /**
     * Returns the output items to send back as input before the function call results: the function calls themselves,
     * and the reasoning and messages that came with them.
     */
    static List<ResponseInputItem> toInputItems(Response response) {
        List<ResponseInputItem> items = new ArrayList<>();
        for (ResponseOutputItem item : response.output()) {
            if (item.isFunctionCall()) {
                items.add(ResponseInputItem.ofFunctionCall(item.asFunctionCall()));
            } else if (item.isReasoning()) {
                items.add(ResponseInputItem.ofReasoning(item.asReasoning()));
            } else if (item.isMessage()) {
                items.add(ResponseInputItem.ofResponseOutputMessage(item.asMessage()));
            }
        }
        return items;
    }

    /**
     * Converts function calls into the chat completion tool calls executed by {@link McpToolCallExecutor}. The call id
     * becomes the tool call id, so that each result pairs back with its call.
     */
    static List<ChatCompletionMessageToolCall> toChatToolCalls(List<ResponseFunctionToolCall> functionCalls) {
        return functionCalls.stream()
                .map(call -> ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
                        .id(call.callId())
                        .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                .name(call.name())
                                .arguments(call.arguments())
                                .build())
                        .build()))
                .toList();
    }

    /**
     * Fails when the model waits for the approval of hosted MCP tool calls. The operation cannot grant approvals, so
     * the exchange would otherwise complete with an empty body.
     */
    static void requireNoPendingMcpApprovals(Exchange exchange, Response response) throws CamelExchangeException {
        List<String> pending = response.output().stream()
                .filter(ResponseOutputItem::isMcpApprovalRequest)
                .map(ResponseOutputItem::asMcpApprovalRequest)
                .map(request -> request.serverLabel() + "/" + request.name())
                .toList();
        if (!pending.isEmpty()) {
            throw new CamelExchangeException(
                    "The model requested approval for the hosted MCP tool calls " + pending
                                             + ", which the responses operation cannot grant. Set require_approval "
                                             + "to never in hostedMcpTools",
                    exchange);
        }
    }

    /**
     * Returns the model id. {@code ResponsesModel} is a union type whose {@code toString()} includes the variant name.
     */
    static String modelName(ResponsesModel model) {
        return model.string()
                .or(() -> model.chat().map(ChatModel::asString))
                .or(() -> model.only().map(ResponsesModel.ResponsesOnlyModel::asString))
                .orElseGet(model::toString);
    }

    /**
     * Returns the annotations attached to the output text of the answer, such as citations, each converted to a map of
     * the API fields.
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> extractAnnotations(Response response) {
        return response.output().stream()
                .filter(ResponseOutputItem::isMessage)
                .flatMap(item -> item.asMessage().content().stream())
                .filter(ResponseOutputMessage.Content::isOutputText)
                // OpenAI-compatible servers may omit the field, which the annotations() accessor rejects
                .flatMap(content -> content.asOutputText()._annotations().asKnown().orElse(List.of()).stream())
                .map(annotation -> (Map<String, Object>) ObjectMappers.jsonMapper().convertValue(annotation, Map.class))
                .toList();
    }

    static Optional<String> extractFinishStatus(Response response) {
        for (ResponseOutputItem item : response.output()) {
            if (item.isMessage()) {
                return Optional.of(item.asMessage().status().toString());
            }
        }
        return response.status().map(Object::toString);
    }
}
