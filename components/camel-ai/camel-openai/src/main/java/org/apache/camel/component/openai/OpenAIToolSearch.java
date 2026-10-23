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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.ai.tool.AiToolSearchCandidate;
import org.apache.camel.component.ai.tool.AiToolSearchEmbedder;
import org.apache.camel.component.ai.tool.AiToolSearchIndex;
import org.apache.camel.component.ai.tool.AiToolSearchSession;
import org.apache.camel.component.ai.tool.AiToolSearchStrategy;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.util.ObjectHelper;

/**
 * Selects the OpenAI tool definitions for one model call when tool search is enabled.
 * <p>
 * The shared {@link McpToolState} snapshot is not modified. Discovered names live on the exchange, and only when
 * conversation memory is enabled, so concurrent requests do not share them. Full definitions are read again from the
 * latest snapshot on every call. MCP {@code tools/list} is unchanged: search only changes which definitions are sent to
 * the model.
 */
final class OpenAIToolSearch {

    static final String SESSION_PROPERTY = "CamelOpenAIToolSearchSession";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String KEYWORD_SCHEMA = "{\"type\":\"object\",\"properties\":{\"terms\":{\"type\":\"array\","
                                                 + "\"description\":\"A list of individual search terms used to find relevant tools\","
                                                 + "\"items\":{\"type\":\"string\"}}},\"required\":[\"terms\"]}";
    private static final String VECTOR_SCHEMA = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\","
                                                + "\"description\":\"Natural language query describing the desired tool\"}},"
                                                + "\"required\":[\"query\"]}";

    private final Object indexLock = new Object();
    private String cachedFingerprint;
    private AiToolSearchIndex cachedIndex;

    static void validateConfiguration(OpenAIConfiguration configuration) {
        if (!configuration.isToolSearch()) {
            return;
        }
        if (configuration.getToolSearchMaxResults() < 1) {
            throw new IllegalArgumentException("toolSearchMaxResults must be at least 1");
        }
        if (configuration.getToolSearchStrategy() == null) {
            throw new IllegalArgumentException("toolSearchStrategy is required when toolSearch=true");
        }
        if (configuration.getToolSearchStrategy() == AiToolSearchStrategy.VECTOR
                && ObjectHelper.isEmpty(configuration.getToolSearchEmbeddingModel())) {
            throw new IllegalArgumentException(
                    "toolSearchStrategy=vector requires toolSearchEmbeddingModel and does not fall back to keyword search");
        }
    }

    static void rejectReservedName(McpToolState state) {
        if (state.knownToolNames().contains(AiToolSearch.TOOL_NAME)) {
            throw new IllegalArgumentException(
                    "Tool name '" + AiToolSearch.TOOL_NAME + "' is reserved while toolSearch=true");
        }
    }

    AiToolSearchSession openSession(Exchange exchange, OpenAIConfiguration configuration) {
        if (configuration.isConversationMemory()) {
            AiToolSearchSession existing = exchange.getProperty(SESSION_PROPERTY, AiToolSearchSession.class);
            if (existing != null) {
                return existing;
            }
            AiToolSearchSession created = new AiToolSearchSession();
            exchange.setProperty(SESSION_PROPERTY, created);
            return created;
        }
        AiToolSearchSession created = new AiToolSearchSession();
        exchange.setProperty(SESSION_PROPERTY, created);
        return created;
    }

    void closeSession(Exchange exchange, OpenAIConfiguration configuration) {
        if (!configuration.isConversationMemory()) {
            exchange.removeProperty(SESSION_PROPERTY);
        }
    }

    List<ChatCompletionTool> toolsFor(OpenAIEndpoint endpoint, AiToolSearchSession session) {
        McpToolState state = endpoint.getMcpToolState();
        rejectReservedName(state);
        return select(endpoint.getConfiguration(), state, session).stream().map(ChatCompletionTool::ofFunction).toList();
    }

    McpToolCallExecutor.ToolResult execute(OpenAIEndpoint endpoint, ChatCompletionMessageToolCall toolCall, Exchange exchange) {
        OpenAIConfiguration configuration = endpoint.getConfiguration();
        AiToolSearchSession session = exchange.getProperty(SESSION_PROPERTY, AiToolSearchSession.class);
        if (session == null) {
            session = openSession(exchange, configuration);
        }
        McpToolState state = endpoint.getMcpToolState();
        String arguments = toolCall.asFunction().function().arguments();
        String content;
        try {
            content = search(configuration, state, session, arguments, index(endpoint, state));
        } catch (IllegalArgumentException e) {
            content = "Error: " + e.getMessage();
        }
        return new McpToolCallExecutor.ToolResult(
                toolCall.asFunction().id(), AiToolSearch.TOOL_NAME, content, false, 0, !content.startsWith("Error:"));
    }

    static String search(
            OpenAIConfiguration configuration, McpToolState state, AiToolSearchSession session, String argumentsJson,
            AiToolSearchIndex index) {
        List<AiToolSearchCandidate> hits;
        if (configuration.getToolSearchStrategy() == AiToolSearchStrategy.VECTOR) {
            String query = readString(argumentsJson, AiToolSearch.QUERY_ARGUMENT);
            if (query == null) {
                return "Error: missing required tool argument '" + AiToolSearch.QUERY_ARGUMENT + "'";
            }
            hits = index.searchVector(query);
        } else {
            List<String> terms = readTerms(argumentsJson);
            if (terms == null) {
                return "Error: missing required tool argument '" + AiToolSearch.TERMS_ARGUMENT + "'";
            }
            hits = index.searchKeyword(terms);
        }
        List<String> names = hits.stream().map(AiToolSearchCandidate::name).toList();
        session.remember(names);
        return AiToolSearch.resultMessage(names);
    }

    static List<AiToolSearchCandidate> candidates(McpToolState state) {
        List<AiToolSearchCandidate> candidates = new ArrayList<>();
        for (ChatCompletionFunctionTool tool : toolsByName(state).values()) {
            String name = tool.function().name();
            if (!isSearchable(state, name)) {
                continue;
            }
            AiToolSpec route = state.routeTools().get(name);
            String description = route != null ? route.getDescription() : tool.function().description().orElse(null);
            String schema = route != null
                    ? route.getParametersJsonSchema()
                    : tool.function().parameters().map(Object::toString).orElse(null);
            Object source = route != null ? route : name;
            candidates.add(new AiToolSearchCandidate(name, description, schema, true, source));
        }
        return List.copyOf(candidates);
    }

    static boolean isSearchable(McpToolState state, String name) {
        AiToolSpec route = state.routeTools().get(name);
        if (route != null) {
            return route.isSearchable();
        }
        return state.toolClientMap().containsKey(name);
    }

    static ChatCompletionFunctionTool synthetic(AiToolSearchStrategy strategy) {
        boolean vector = strategy == AiToolSearchStrategy.VECTOR;
        return AiToolSpecToOpenAI.function(
                AiToolSearch.TOOL_NAME,
                vector
                        ? "Finds available tools using semantic vector search. Matching tools become available to call on the next turn."
                        : "Finds available tools whose name or description contains the given search terms. Matching tools become available to call on the next turn.",
                vector ? VECTOR_SCHEMA : KEYWORD_SCHEMA);
    }

    private AiToolSearchIndex index(OpenAIEndpoint endpoint, McpToolState state) {
        OpenAIConfiguration configuration = endpoint.getConfiguration();
        String fingerprint = fingerprint(configuration, state);
        synchronized (indexLock) {
            if (fingerprint.equals(cachedFingerprint) && cachedIndex != null) {
                return cachedIndex;
            }
        }
        AiToolSearchEmbedder embedder = configuration.getToolSearchStrategy() == AiToolSearchStrategy.VECTOR
                ? embedder(endpoint)
                : null;
        AiToolSearchIndex built = AiToolSearchIndex.create(
                configuration.getToolSearchStrategy(), candidates(state), configuration.getToolSearchMaxResults(),
                embedder);
        synchronized (indexLock) {
            cachedFingerprint = fingerprint;
            cachedIndex = built;
            return built;
        }
    }

    private static AiToolSearchEmbedder embedder(OpenAIEndpoint endpoint) {
        String model = endpoint.getConfiguration().getToolSearchEmbeddingModel();
        return text -> {
            EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                    .model(model)
                    .input(text)
                    .build();
            CreateEmbeddingResponse response = endpoint.getClient().embeddings().create(params);
            if (response.data().isEmpty() || response.data().get(0).embedding() == null) {
                throw new IllegalStateException("Embedding model '" + model + "' returned no vector");
            }
            List<Float> values = response.data().get(0).embedding();
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                Float value = values.get(i);
                vector[i] = value == null ? 0f : value;
            }
            return vector;
        };
    }

    private static String fingerprint(OpenAIConfiguration configuration, McpToolState state) {
        StringBuilder builder = new StringBuilder();
        builder.append(configuration.getToolSearchStrategy())
                .append('|')
                .append(configuration.getToolSearchMaxResults())
                .append('|')
                .append(configuration.getToolSearchEmbeddingModel())
                .append('\n');
        for (AiToolSearchCandidate candidate : candidates(state)) {
            builder.append(candidate.name()).append('\0')
                    .append(candidate.description()).append('\0')
                    .append(candidate.parametersJsonSchema()).append('\n');
        }
        return builder.toString();
    }

    private static Map<String, ChatCompletionFunctionTool> toolsByName(McpToolState state) {
        Map<String, ChatCompletionFunctionTool> byName = new LinkedHashMap<>();
        for (ChatCompletionFunctionTool tool : state.tools()) {
            byName.putIfAbsent(tool.function().name(), tool);
        }
        return byName;
    }

    static List<ChatCompletionFunctionTool> select(
            OpenAIConfiguration configuration, McpToolState state, AiToolSearchSession session) {
        Map<String, ChatCompletionFunctionTool> byName = toolsByName(state);
        session.retainAvailable(byName.keySet());

        List<ChatCompletionFunctionTool> selected = new ArrayList<>();
        for (ChatCompletionFunctionTool tool : byName.values()) {
            if (!isSearchable(state, tool.function().name())) {
                selected.add(tool);
            }
        }
        AiToolSearchStrategy strategy = configuration.getToolSearchStrategy() == null
                ? AiToolSearchStrategy.KEYWORD
                : configuration.getToolSearchStrategy();
        selected.add(synthetic(strategy));
        for (String name : session.discoveredNames()) {
            ChatCompletionFunctionTool discovered = byName.get(name);
            if (discovered != null && isSearchable(state, name)) {
                selected.add(discovered);
            }
        }
        return List.copyOf(selected);
    }

    private static List<String> readTerms(String argumentsJson) {
        Map<String, Object> arguments = readArguments(argumentsJson);
        if (arguments == null || !arguments.containsKey(AiToolSearch.TERMS_ARGUMENT)) {
            return null;
        }
        Object value = arguments.get(AiToolSearch.TERMS_ARGUMENT);
        if (value instanceof List<?> list) {
            List<String> terms = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    terms.add(item.toString());
                }
            }
            return terms;
        }
        if (value instanceof String text) {
            return List.of(text);
        }
        return null;
    }

    private static String readString(String argumentsJson, String name) {
        Map<String, Object> arguments = readArguments(argumentsJson);
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Map<String, Object> readArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(argumentsJson, MAP_TYPE);
        } catch (Exception e) {
            return null;
        }
    }
}
