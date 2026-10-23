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
package org.apache.camel.component.langchain4j.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.ai.tool.AiToolSearchCandidate;
import org.apache.camel.component.ai.tool.AiToolSearchEmbedder;
import org.apache.camel.component.ai.tool.AiToolSearchIndex;
import org.apache.camel.component.ai.tool.AiToolSearchStrategy;

/**
 * Ranks the tools LangChain4j has already combined from route and MCP providers.
 * <p>
 * LangChain4j still executes a found tool through its original executor and restores names found earlier from chat
 * memory. This strategy only replaces the ranking engine. Dynamic tool providers registered outside Camel's composite
 * are refreshed after search and are not part of this catalogue.
 */
final class CamelToolSearchStrategy implements ToolSearchStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AiToolSearchStrategy strategy;
    private final int maxResults;
    private final EmbeddingModel embeddingModel;

    CamelToolSearchStrategy(AiToolSearchStrategy strategy, int maxResults, EmbeddingModel embeddingModel) {
        this.strategy = strategy;
        this.maxResults = maxResults;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext invocationContext) {
        return List.of(synthetic());
    }

    @Override
    public ToolSearchResult search(ToolSearchRequest request) {
        List<AiToolSearchCandidate> candidates = new ArrayList<>();
        for (ToolSpecification specification : request.searchableTools()) {
            if (AiToolSearch.TOOL_NAME.equals(specification.name())) {
                throw new IllegalArgumentException(
                        "Tool name '" + AiToolSearch.TOOL_NAME + "' is reserved while toolSearch=true");
            }
            String schema = specification.parameters() == null ? null : specification.parameters().toString();
            candidates.add(new AiToolSearchCandidate(
                    specification.name(), specification.description(), schema, true, specification));
        }
        AiToolSearchEmbedder embedder = embeddingModel == null ? null : text -> {
            var response = embeddingModel.embed(text);
            if (response == null || response.content() == null || response.content().vector() == null) {
                throw new IllegalStateException("Embedding model returned no vector");
            }
            return response.content().vector();
        };
        AiToolSearchIndex index = AiToolSearchIndex.create(strategy, candidates, maxResults, embedder);
        String arguments = request.toolExecutionRequest() == null ? null : request.toolExecutionRequest().arguments();
        if (strategy == AiToolSearchStrategy.VECTOR) {
            String query = readString(arguments, AiToolSearch.QUERY_ARGUMENT);
            if (query == null) {
                return new ToolSearchResult(
                        List.of(),
                        "Error: missing required tool argument '" + AiToolSearch.QUERY_ARGUMENT + "'");
            }
            return result(index.searchVector(query));
        }
        List<String> terms = readTerms(arguments);
        if (terms == null) {
            return new ToolSearchResult(
                    List.of(),
                    "Error: missing required tool argument '" + AiToolSearch.TERMS_ARGUMENT + "'");
        }
        return result(index.searchKeyword(terms));
    }

    private ToolSpecification synthetic() {
        boolean vector = strategy == AiToolSearchStrategy.VECTOR;
        JsonObjectSchema parameters = vector
                ? JsonObjectSchema.builder()
                        .addProperty(AiToolSearch.QUERY_ARGUMENT, JsonStringSchema.builder()
                                .description("Natural language query describing the desired tool")
                                .build())
                        .required(AiToolSearch.QUERY_ARGUMENT)
                        .build()
                : JsonObjectSchema.builder()
                        .addProperty(AiToolSearch.TERMS_ARGUMENT, JsonArraySchema.builder()
                                .description("A list of individual search terms used to find relevant tools")
                                .items(JsonStringSchema.builder().build())
                                .build())
                        .required(AiToolSearch.TERMS_ARGUMENT)
                        .build();
        return ToolSpecification.builder()
                .name(AiToolSearch.TOOL_NAME)
                .description(vector
                        ? "Finds available tools using semantic vector search. Matching tools become available to call on the next turn."
                        : "Finds available tools whose name or description contains the given search terms. Matching tools become available to call on the next turn.")
                .parameters(parameters)
                .build();
    }

    private static ToolSearchResult result(List<AiToolSearchCandidate> hits) {
        List<String> names = hits.stream().map(AiToolSearchCandidate::name).toList();
        return new ToolSearchResult(names, AiToolSearch.resultMessage(names));
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
        if (arguments == null || arguments.get(name) == null) {
            return null;
        }
        String text = arguments.get(name).toString().trim();
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
