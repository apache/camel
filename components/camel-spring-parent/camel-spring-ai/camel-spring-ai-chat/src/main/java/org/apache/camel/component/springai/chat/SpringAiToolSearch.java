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
package org.apache.camel.component.springai.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.ai.tool.AiToolSearchCandidate;
import org.apache.camel.component.ai.tool.AiToolSearchEmbedder;
import org.apache.camel.component.ai.tool.AiToolSearchIndex;
import org.apache.camel.component.ai.tool.AiToolSearchSession;
import org.apache.camel.component.ai.tool.AiToolSearchSessions;
import org.apache.camel.component.ai.tool.AiToolSearchStrategy;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Rebuilds the tool list inside Spring AI's existing tool-calling loop.
 * <p>
 * The advisor does not run its own loop. {@code doBeforeCall} and {@code doBeforeStream} replace the immutable callback
 * snapshot with the directly visible tools, the synthetic search tool, and tools already discovered for this request or
 * conversation. Execution stays on the original callback.
 */
final class SpringAiToolSearch {

    static final String CATALOGUE = "CamelSpringAiToolSearchCatalogue";
    static final String SEARCHABLE = "CamelSpringAiToolSearchSearchableNames";
    static final String CONVERSATION = "CamelSpringAiToolSearchConversationId";
    static final String SESSION = "CamelSpringAiToolSearchSession";

    private static final String KEYWORD_SCHEMA = "{\"type\":\"object\",\"properties\":{\"terms\":{\"type\":\"array\","
                                                 + "\"description\":\"A list of individual search terms used to find relevant tools\","
                                                 + "\"items\":{\"type\":\"string\"}}},\"required\":[\"terms\"]}";
    private static final String VECTOR_SCHEMA = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\","
                                                + "\"description\":\"Natural language query describing the desired tool\"}},"
                                                + "\"required\":[\"query\"]}";

    private final AiToolSearchStrategy strategy;
    private final int maxResults;
    private final EmbeddingModel embeddingModel;
    private final AiToolSearchSessions sessions = new AiToolSearchSessions();
    private final Object indexLock = new Object();
    private String cachedFingerprint;
    private AiToolSearchIndex cachedIndex;

    SpringAiToolSearch(SpringAiChatConfiguration configuration) {
        this.strategy = configuration.getToolSearchStrategy();
        this.maxResults = configuration.getToolSearchMaxResults();
        this.embeddingModel = configuration.getToolSearchEmbeddingModel();
    }

    static void validate(SpringAiChatConfiguration configuration) {
        if (!configuration.isToolSearch()) {
            return;
        }
        if (configuration.getChatClient() != null) {
            throw new IllegalArgumentException(
                    "toolSearch=true requires Camel to build the ChatClient from chatModel. "
                                               + "A prebuilt chatClient cannot install the tool search advisor.");
        }
        if (configuration.getToolSearchMaxResults() < 1) {
            throw new IllegalArgumentException("toolSearchMaxResults must be at least 1");
        }
        if (configuration.getToolSearchStrategy() == null) {
            throw new IllegalArgumentException("toolSearchStrategy is required when toolSearch=true");
        }
        if (configuration.getToolSearchStrategy() == AiToolSearchStrategy.VECTOR
                && configuration.getToolSearchEmbeddingModel() == null) {
            throw new IllegalArgumentException(
                    "toolSearchStrategy=vector requires toolSearchEmbeddingModel and does not fall back to keyword search");
        }
    }

    ChatClientRequest initialize(ChatClientRequest request) {
        if (!request.context().containsKey(CATALOGUE)) {
            return request;
        }
        if (conversationId(request) != null || request.context().get(SESSION) instanceof AiToolSearchSession) {
            return request;
        }
        Map<String, Object> context = new HashMap<>(request.context());
        context.put(SESSION, new AiToolSearchSession());
        return request.mutate().context(context).build();
    }

    ChatClientRequest prepare(ChatClientRequest request) {
        if (!request.context().containsKey(CATALOGUE)) {
            return request;
        }
        List<ToolCallback> catalogue = catalogue(request);
        Set<String> searchable = searchable(request);
        Map<String, ToolCallback> byName = new LinkedHashMap<>();
        for (ToolCallback callback : catalogue) {
            byName.putIfAbsent(callback.getToolDefinition().name(), callback);
        }
        AiToolSearchSession session = session(request);
        session.retainAvailable(byName.keySet());

        List<ToolCallback> selected = new ArrayList<>();
        for (ToolCallback callback : byName.values()) {
            if (!searchable.contains(callback.getToolDefinition().name())) {
                selected.add(callback);
            }
        }
        selected.add(searchCallback(catalogue, searchable, session));
        for (String name : session.discoveredNames()) {
            ToolCallback discovered = byName.get(name);
            if (discovered != null && searchable.contains(name)) {
                selected.add(discovered);
            }
        }
        ToolCallingChatOptions options = ((ToolCallingChatOptions) request.prompt().getOptions()).mutate()
                .toolCallbacks(List.copyOf(selected))
                .build();
        Prompt prompt = request.prompt().mutate().chatOptions(options).build();
        return request.mutate().prompt(prompt).build();
    }

    private ToolCallback searchCallback(
            List<ToolCallback> catalogue, Set<String> searchable, AiToolSearchSession session) {
        boolean vector = strategy == AiToolSearchStrategy.VECTOR;
        ToolDefinition definition = ToolDefinition.builder()
                .name(AiToolSearch.TOOL_NAME)
                .description(vector
                        ? "Finds available tools using semantic vector search. Matching tools become available to call on the next turn."
                        : "Finds available tools whose name or description contains the given search terms. Matching tools become available to call on the next turn.")
                .inputSchema(vector ? VECTOR_SCHEMA : KEYWORD_SCHEMA)
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                try {
                    List<AiToolSearchCandidate> hits = vector
                            ? index(catalogue, searchable).searchVector(requiredQuery(toolInput))
                            : index(catalogue, searchable).searchKeyword(requiredTerms(toolInput));
                    List<String> names = hits.stream().map(AiToolSearchCandidate::name).toList();
                    session.remember(names);
                    return AiToolSearch.resultMessage(names);
                } catch (IllegalArgumentException e) {
                    return "Error: " + e.getMessage();
                }
            }
        };
    }

    private AiToolSearchIndex index(List<ToolCallback> catalogue, Set<String> searchable) {
        List<AiToolSearchCandidate> candidates = new ArrayList<>();
        for (ToolCallback callback : catalogue) {
            String name = callback.getToolDefinition().name();
            if (!searchable.contains(name) || AiToolSearch.TOOL_NAME.equals(name)) {
                continue;
            }
            candidates.add(new AiToolSearchCandidate(
                    name, callback.getToolDefinition().description(), callback.getToolDefinition().inputSchema(), true,
                    callback));
        }
        String fingerprint = fingerprint(candidates);
        synchronized (indexLock) {
            if (fingerprint.equals(cachedFingerprint) && cachedIndex != null) {
                return cachedIndex;
            }
        }
        AiToolSearchEmbedder embedder = strategy == AiToolSearchStrategy.VECTOR ? this::embed : null;
        AiToolSearchIndex built = AiToolSearchIndex.create(strategy, candidates, maxResults, embedder);
        synchronized (indexLock) {
            cachedFingerprint = fingerprint;
            cachedIndex = built;
            return built;
        }
    }

    private float[] embed(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("Embedding model returned no vector");
        }
        return vector;
    }

    private AiToolSearchSession session(ChatClientRequest request) {
        String conversationId = conversationId(request);
        if (conversationId != null) {
            return sessions.session(conversationId);
        }
        Object current = request.context().get(SESSION);
        if (current instanceof AiToolSearchSession session) {
            return session;
        }
        return new AiToolSearchSession();
    }

    private static String conversationId(ChatClientRequest request) {
        Object value = request.context().get(CONVERSATION);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<ToolCallback> catalogue(ChatClientRequest request) {
        Object value = request.context().get(CATALOGUE);
        if (value instanceof List<?> list) {
            return (List<ToolCallback>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> searchable(ChatClientRequest request) {
        Object value = request.context().get(SEARCHABLE);
        if (value instanceof Set<?> names) {
            return (Set<String>) names;
        }
        return Set.of();
    }

    private static String requiredQuery(String argumentsJson) {
        String query = readString(argumentsJson, AiToolSearch.QUERY_ARGUMENT);
        if (query == null) {
            throw new IllegalArgumentException("missing required tool argument '" + AiToolSearch.QUERY_ARGUMENT + "'");
        }
        return query;
    }

    private static List<String> requiredTerms(String argumentsJson) {
        List<String> terms = readTerms(argumentsJson);
        if (terms == null) {
            throw new IllegalArgumentException("missing required tool argument '" + AiToolSearch.TERMS_ARGUMENT + "'");
        }
        return terms;
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
            Object parsed = Jsoner.deserialize(argumentsJson);
            if (parsed instanceof JsonObject object) {
                return object;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String fingerprint(List<AiToolSearchCandidate> candidates) {
        StringBuilder builder = new StringBuilder();
        for (AiToolSearchCandidate candidate : candidates) {
            builder.append(candidate.name()).append('\0')
                    .append(candidate.description()).append('\0')
                    .append(candidate.parametersJsonSchema()).append('\n');
        }
        return builder.toString();
    }

    static final class AdvisorBuilder extends ToolCallingAdvisor.Builder<AdvisorBuilder> {

        private final SpringAiToolSearch search;

        AdvisorBuilder(SpringAiToolSearch search) {
            this.search = search;
        }

        @Override
        protected AdvisorBuilder self() {
            return this;
        }

        @Override
        protected ToolCallingAdvisor.Builder<?> newCopy() {
            return new AdvisorBuilder(search);
        }

        @Override
        public ToolCallingAdvisor build() {
            return new SearchAdvisor(
                    getToolCallingManager(), getToolExecutionEligibilityChecker(), getAdvisorOrder(),
                    isConversationHistoryEnabled(), search);
        }
    }

    private static final class SearchAdvisor extends ToolCallingAdvisor {

        private final SpringAiToolSearch search;

        private SearchAdvisor(
                              ToolCallingManager toolCallingManager,
                              ToolExecutionEligibilityChecker toolExecutionEligibilityChecker, int order,
                              boolean conversationHistoryEnabled, SpringAiToolSearch search) {
            super(toolCallingManager, toolExecutionEligibilityChecker, order, conversationHistoryEnabled);
            this.search = search;
        }

        @Override
        protected ChatClientRequest doInitializeLoop(ChatClientRequest request, CallAdvisorChain chain) {
            return search.initialize(super.doInitializeLoop(request, chain));
        }

        @Override
        protected ChatClientRequest doInitializeLoopStream(ChatClientRequest request, StreamAdvisorChain chain) {
            return search.initialize(super.doInitializeLoopStream(request, chain));
        }

        @Override
        protected ChatClientRequest doBeforeCall(ChatClientRequest request, CallAdvisorChain chain) {
            return search.prepare(request);
        }

        @Override
        protected ChatClientRequest doBeforeStream(ChatClientRequest request, StreamAdvisorChain chain) {
            return search.prepare(request);
        }
    }
}
