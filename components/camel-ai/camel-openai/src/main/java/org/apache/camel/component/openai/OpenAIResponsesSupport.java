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

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
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
            throw new IllegalArgumentException("hostedMcpTools must be a JSON array of Tool.Mcp objects");
        }
        for (JsonNode node : root) {
            Tool.Mcp.Builder builder = Tool.Mcp.builder();
            if (node.hasNonNull("server_label")) {
                builder.serverLabel(node.get("server_label").asText());
            } else if (node.hasNonNull("serverLabel")) {
                builder.serverLabel(node.get("serverLabel").asText());
            }
            if (node.hasNonNull("server_url")) {
                builder.serverUrl(node.get("server_url").asText());
            } else if (node.hasNonNull("serverUrl")) {
                builder.serverUrl(node.get("serverUrl").asText());
            }
            if (node.hasNonNull("server_description")) {
                builder.serverDescription(node.get("server_description").asText());
            } else if (node.hasNonNull("serverDescription")) {
                builder.serverDescription(node.get("serverDescription").asText());
            }
            paramsBuilder.addTool(builder.build());
        }
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

    static Optional<String> extractFinishStatus(Response response) {
        for (ResponseOutputItem item : response.output()) {
            if (item.isMessage()) {
                return Optional.of(item.asMessage().status().toString());
            }
        }
        return response.status().map(Object::toString);
    }
}
