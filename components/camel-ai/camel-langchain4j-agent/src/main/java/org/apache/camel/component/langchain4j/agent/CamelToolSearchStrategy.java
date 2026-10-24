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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.ai.tool.AiToolSearchArguments;
import org.apache.camel.component.ai.tool.AiToolSearchCandidate;
import org.apache.camel.component.ai.tool.AiToolSearchEmbedder;
import org.apache.camel.component.ai.tool.AiToolSearchIndex;
import org.apache.camel.component.ai.tool.AiToolSearchStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ranks the tools LangChain4j has already combined from route and MCP providers.
 * <p>
 * LangChain4j still executes a found tool through its original executor and restores names found earlier from chat
 * memory. This strategy only replaces the ranking engine. Dynamic tool providers registered outside Camel's composite
 * are refreshed after search and are not part of this catalogue.
 */
final class CamelToolSearchStrategy implements ToolSearchStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(CamelToolSearchStrategy.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final int MIN_CACHED_EMBEDDINGS = 4096;
    private static final int CACHE_HEADROOM = 256;

    private final AiToolSearchStrategy strategy;
    private final int maxResults;

    /*
     * The cache belongs to this strategy instance and therefore to its final embeddingModel.
     * Cache and catalogue-key metadata are protected by embeddingCacheLock. Model calls must
     * run outside that lock so a slow embedding request cannot block other searches.
     */
    private final EmbeddingModel embeddingModel;
    private final Object embeddingCacheLock = new Object();
    private int maxCachedEmbeddings = MIN_CACHED_EMBEDDINGS;

    private final Map<String, float[]> embeddingCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > maxCachedEmbeddings;
        }
    };

    /*
     * Embedding texts belonging to the previously searched tool catalogue.
     * This lets each search discard entries for tool content that has disappeared or changed.
     */
    private Set<String> cachedToolEmbeddingKeys = Set.of();

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

        // Validate search arguments early before performing any embedding work.
        String arguments = request.toolExecutionRequest() == null ? null : request.toolExecutionRequest().arguments();
        String query = null;
        List<String> terms = null;

        if (strategy == AiToolSearchStrategy.VECTOR) {
            query = AiToolSearchArguments.readString(arguments, AiToolSearch.QUERY_ARGUMENT,
                    CamelToolSearchStrategy::readArguments);
            if (query == null) {
                return new ToolSearchResult(
                        List.of(),
                        "Error: missing required tool argument '" + AiToolSearch.QUERY_ARGUMENT + "'");
            }
        } else {
            terms = AiToolSearchArguments.readTerms(arguments, CamelToolSearchStrategy::readArguments);
            if (terms == null) {
                return new ToolSearchResult(
                        List.of(),
                        "Error: missing required tool argument '" + AiToolSearch.TERMS_ARGUMENT + "'");
            }
        }

        if (strategy == AiToolSearchStrategy.VECTOR && embeddingModel != null) {
            List<AiToolSearchCandidate> searchable = searchableCandidates(candidates);
            Set<String> currentToolKeys = new LinkedHashSet<>();
            for (AiToolSearchCandidate candidate : searchable) {
                currentToolKeys.add(embeddingText(candidate));
            }

            List<String> missingTexts;
            synchronized (embeddingCacheLock) {
                /*
                 * Reserve enough capacity for all current tool vectors plus query-cache
                 * headroom. Keep the map and its access order protected by this lock.
                 */
                maxCachedEmbeddings = Math.max(
                        MIN_CACHED_EMBEDDINGS,
                        currentToolKeys.size() + CACHE_HEADROOM);

                for (String previousKey : cachedToolEmbeddingKeys) {
                    if (!currentToolKeys.contains(previousKey)) {
                        embeddingCache.remove(previousKey);
                    }
                }
                cachedToolEmbeddingKeys = Set.copyOf(currentToolKeys);

                missingTexts = new ArrayList<>();
                for (String text : currentToolKeys) {
                    if (!embeddingCache.containsKey(text)) {
                        missingTexts.add(text);
                    }
                }
            }

            /*
             * Do not hold embeddingCacheLock during embedAll(). A concurrent search may
             * compute the same missing vectors; putIfAbsent below safely reconciles the
             * results, while both searches can progress independently.
             */
            if (!missingTexts.isEmpty()) {
                try {
                    List<TextSegment> segments = missingTexts.stream()
                            .map(TextSegment::from)
                            .toList();
                    var response = embeddingModel.embedAll(segments);
                    if (response != null && response.content() != null
                            && response.content().size() == missingTexts.size()) {
                        boolean valid = true;
                        List<float[]> vectors = new ArrayList<>(missingTexts.size());
                        for (int i = 0; i < missingTexts.size(); i++) {
                            var embedding = response.content().get(i);
                            if (embedding == null
                                    || embedding.vector() == null
                                    || embedding.vector().length == 0) {
                                valid = false;
                                break;
                            }
                            vectors.add(embedding.vector());
                        }

                        if (valid) {
                            synchronized (embeddingCacheLock) {
                                for (int i = 0; i < missingTexts.size(); i++) {
                                    embeddingCache.putIfAbsent(
                                            missingTexts.get(i), vectors.get(i));
                                }
                            }
                        } else {
                            LOG.debug(
                                    "Batch embedding returned invalid or empty vectors, falling back to individual embedding");
                        }
                    } else {
                        LOG.debug(
                                "Batch embedding returned null or mismatched size, falling back to individual embedding");
                    }
                } catch (UnsupportedOperationException e) {
                    LOG.debug(
                            "Batch embedding (embedAll) not supported by embedding model, falling back to individual embedding");
                } catch (Exception e) {
                    LOG.warn(
                            "Batch embedding failed unexpectedly, falling back to individual embedding: {}",
                            e.getMessage());
                }
            }
        }

        AiToolSearchEmbedder embedder = embeddingModel == null ? null : text -> {
            float[] cached;
            synchronized (embeddingCacheLock) {
                cached = embeddingCache.get(text);
            }
            if (cached != null) {
                return cached;
            }

            var response = embeddingModel.embed(text);
            if (response == null || response.content() == null || response.content().vector() == null) {
                throw new IllegalStateException("Embedding model returned no vector");
            }
            float[] vector = response.content().vector();
            if (vector.length == 0) {
                throw new IllegalStateException("Embedding model returned an empty vector");
            }
            synchronized (embeddingCacheLock) {
                embeddingCache.put(text, vector);
            }
            return vector;
        };

        AiToolSearchIndex index = AiToolSearchIndex.create(strategy, candidates, maxResults, embedder);
        if (strategy == AiToolSearchStrategy.VECTOR) {
            return result(index.searchVector(query));
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

    private static List<AiToolSearchCandidate> searchableCandidates(List<AiToolSearchCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, AiToolSearchCandidate> byName = new LinkedHashMap<>();
        for (AiToolSearchCandidate candidate : candidates) {
            if (candidate == null || !candidate.searchable() || candidate.name().isBlank()) {
                continue;
            }
            byName.putIfAbsent(candidate.name(), candidate);
        }
        return List.copyOf(byName.values());
    }

    private static String embeddingText(AiToolSearchCandidate candidate) {
        String description = candidate.description();
        if (description == null || description.isBlank()) {
            return candidate.name();
        }
        return candidate.name() + "\n" + description;
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
