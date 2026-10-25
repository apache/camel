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

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.embeddings.Embedding;
import io.modelcontextprotocol.client.McpSyncClient;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.ai.tool.AiToolSearchCandidate;
import org.apache.camel.component.ai.tool.AiToolSearchIndex;
import org.apache.camel.component.ai.tool.AiToolSearchSession;
import org.apache.camel.component.ai.tool.AiToolSearchSessions;
import org.apache.camel.component.ai.tool.AiToolSearchStrategy;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpenAIToolSearchTest {

    @Test
    void searchableRouteAndMcpToolsAreHiddenUntilSearchFindsThem() {
        McpToolState state = state();
        AiToolSearchSession session = new AiToolSearchSession();
        OpenAIConfiguration configuration = configuration(true);

        List<String> initial = names(OpenAIToolSearch.select(configuration, state, session));
        assertEquals(List.of("ping", AiToolSearch.TOOL_NAME), initial);

        AiToolSearchIndex index = AiToolSearchIndex.create(
                AiToolSearchStrategy.KEYWORD, OpenAIToolSearch.candidates(state), 5, null);
        String message = OpenAIToolSearch.search(
                configuration, state, session, "{\"terms\":[\"invoice\"]}", index);
        assertEquals("Tools found: invoice", message);
        assertEquals(List.of("ping", AiToolSearch.TOOL_NAME, "invoice"),
                names(OpenAIToolSearch.select(configuration, state, session)));
    }

    @Test
    void routeAndMcpToolsShareOneRanking() {
        McpToolState state = state();
        AiToolSearchIndex index = AiToolSearchIndex.create(
                AiToolSearchStrategy.KEYWORD, OpenAIToolSearch.candidates(state), 1, null);

        assertEquals(List.of("get_weather"),
                index.searchKeyword(List.of("weather")).stream().map(AiToolSearchCandidate::name).toList());
        assertTrue(index.searchKeyword(List.of("invoice")).get(0).parametersJsonSchema().contains("id"));
    }

    @Test
    void removedToolIsDroppedFromTheSession() {
        McpToolState state = state();
        AiToolSearchSession session = new AiToolSearchSession();
        session.remember(List.of("invoice", "get_weather"));
        OpenAIConfiguration configuration = configuration(true);

        McpToolState withoutInvoice = new McpToolState(
                state.tools().stream().filter(tool -> !"invoice".equals(tool.function().name())).toList(),
                state.toolClientMap(),
                state.toolToServerName(),
                state.returnDirectTools(),
                Map.of("ping", state.routeTools().get("ping")));

        assertEquals(List.of("ping", AiToolSearch.TOOL_NAME, "get_weather"),
                names(OpenAIToolSearch.select(configuration, withoutInvoice, session)));
        assertEquals(List.of("get_weather"), session.discoveredNames());
    }

    @Test
    void vectorWithoutAnEmbeddingModelFailsAndDoesNotFallBack() {
        OpenAIConfiguration configuration = configuration(true);
        configuration.setToolSearchStrategy(AiToolSearchStrategy.VECTOR);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> OpenAIToolSearch.validateConfiguration(configuration));
        assertTrue(error.getMessage().contains("does not fall back"));
    }

    @Test
    void manualToolExecutionReusesRetainedDiscoveryWhenItsMemoryOptionIsDisabled() {
        AiToolSearchSessions sessions = new AiToolSearchSessions();
        OpenAIToolSearch chatSearch = new OpenAIToolSearch(sessions);
        OpenAIToolSearch executionSearch = new OpenAIToolSearch(sessions);

        OpenAIConfiguration chatConfiguration = configuration(true);
        chatConfiguration.setConversationMemory(true);

        OpenAIConfiguration executionConfiguration = configuration(true);
        executionConfiguration.setConversationMemory(false);

        String sessionId = "manual-loop-tool-search-session";

        Exchange chatExchange = new DefaultExchange(new DefaultCamelContext());
        chatExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, sessionId);
        AiToolSearchSession discovered = chatSearch.openSession(chatExchange, chatConfiguration);
        discovered.remember(List.of("invoice"));
        chatSearch.closeSession(chatExchange, chatConfiguration);

        Exchange executionExchange = new DefaultExchange(new DefaultCamelContext());
        executionExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, sessionId);
        AiToolSearchSession reused = executionSearch.openSessionForExecution(
                executionExchange, executionConfiguration);

        assertEquals(List.of("invoice"), reused.discoveredNames());
        executionSearch.closeSession(executionExchange, executionConfiguration);

        Exchange nextChatExchange = new DefaultExchange(new DefaultCamelContext());
        nextChatExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, sessionId);
        AiToolSearchSession nextTurn = chatSearch.openSession(nextChatExchange, chatConfiguration);
        assertEquals(List.of("invoice"), nextTurn.discoveredNames());
    }

    @Test
    void conversationMemoryRetainsExchangeScopedSessionWithoutConversationId() {
        OpenAIToolSearch toolSearch = new OpenAIToolSearch();
        OpenAIConfiguration configuration = configuration(true);
        configuration.setConversationMemory(true);
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());

        AiToolSearchSession session = toolSearch.openSession(exchange, configuration);
        session.remember(List.of("invoice"));
        toolSearch.closeSession(exchange, configuration);

        assertSame(session, exchange.getProperty(OpenAIToolSearch.SESSION_PROPERTY));
        AiToolSearchSession nextStep = toolSearch.openSession(exchange, configuration);
        assertSame(session, nextStep);
        assertEquals(List.of("invoice"), nextStep.discoveredNames());
    }

    @Test
    void closeSessionRemovesExchangeScopedSessionWhenConversationMemoryIsDisabled() {
        OpenAIToolSearch toolSearch = new OpenAIToolSearch();
        OpenAIConfiguration configuration = configuration(true);
        configuration.setConversationMemory(false);
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());

        toolSearch.openSession(exchange, configuration);
        toolSearch.closeSession(exchange, configuration);

        assertNull(exchange.getProperty(OpenAIToolSearch.SESSION_PROPERTY));
    }

    @Test
    void toolSearchSessionIdIsIndependentFromResponsesConversationId() {
        OpenAIToolSearch toolSearch = new OpenAIToolSearch();
        OpenAIConfiguration configuration = configuration(true);
        configuration.setConversationMemory(true);
        configuration.setConversationId("responses-conversation");

        Exchange firstExchange = new DefaultExchange(new DefaultCamelContext());
        firstExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-a");
        AiToolSearchSession first = toolSearch.openSession(firstExchange, configuration);
        first.remember(List.of("invoice"));
        toolSearch.closeSession(firstExchange, configuration);

        Exchange secondExchange = new DefaultExchange(new DefaultCamelContext());
        secondExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-b");
        AiToolSearchSession second = toolSearch.openSession(secondExchange, configuration);

        assertNotSame(first, second);
        assertEquals(List.of(), second.discoveredNames());
    }

    @Test
    void batchEmbeddingsAreReorderedByInputIndex() {
        List<Embedding> response = List.of(
                Embedding.builder().index(1).embedding(List.of(0.0f, 1.0f)).build(),
                Embedding.builder().index(0).embedding(List.of(1.0f, 0.0f)).build());

        List<float[]> vectors = OpenAIToolSearch.embeddingsInInputOrder(response, 2, "test-model");

        assertArrayEquals(new float[] { 1.0f, 0.0f }, vectors.get(0));
        assertArrayEquals(new float[] { 0.0f, 1.0f }, vectors.get(1));
    }

    @Test
    void batchEmbeddingsRejectDuplicateInputIndices() {
        List<Embedding> response = List.of(
                Embedding.builder().index(0).embedding(List.of(1.0f, 0.0f)).build(),
                Embedding.builder().index(0).embedding(List.of(0.0f, 1.0f)).build());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> OpenAIToolSearch.embeddingsInInputOrder(response, 2, "test-model"));

        assertTrue(error.getMessage().contains("duplicate embedding index 0"));
    }

    @Test
    void batchEmbeddingsRejectOutOfRangeInputIndices() {
        List<Embedding> response = List.of(
                Embedding.builder().index(0).embedding(List.of(1.0f, 0.0f)).build(),
                Embedding.builder().index(2).embedding(List.of(0.0f, 1.0f)).build());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> OpenAIToolSearch.embeddingsInInputOrder(response, 2, "test-model"));

        assertTrue(error.getMessage().contains("out-of-range embedding index 2"));
    }

    @Test
    void responsesToolSearchRequiresAutomaticToolExecution() {
        OpenAIConfiguration configuration = configuration(true);
        configuration.setAutoToolExecution(false);

        assertDoesNotThrow(() -> OpenAIToolSearch.validateConfiguration(configuration, false));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> OpenAIToolSearch.validateConfiguration(configuration, true));
        assertTrue(error.getMessage().contains("autoToolExecution=true"));
    }

    @Test
    void reservedNameFailsWhenSearchIsEnabled() {
        AiToolSpec reservedSpec = spec(AiToolSearch.TOOL_NAME, "collision", false);
        ChatCompletionFunctionTool reserved = AiToolSpecToOpenAI.convert(reservedSpec);
        McpToolState state = new McpToolState(
                List.of(reserved), Map.of(), Map.of(), Set.of(), Map.of(AiToolSearch.TOOL_NAME, reservedSpec));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> OpenAIToolSearch.rejectReservedName(state));
        assertTrue(error.getMessage().contains(AiToolSearch.TOOL_NAME));
    }

    @Test
    void sessionsDoNotShareDiscoveredNames() {
        AiToolSearchSession first = new AiToolSearchSession();
        AiToolSearchSession second = new AiToolSearchSession();
        first.remember(List.of("invoice"));

        assertEquals(List.of(), second.discoveredNames());
    }

    @Test
    void toolSearchSessionIsReusedAcrossExchanges() {
        OpenAIToolSearch toolSearch = new OpenAIToolSearch();
        OpenAIConfiguration configuration = configuration(true);
        configuration.setConversationMemory(true);

        Exchange firstExchange = new DefaultExchange(new DefaultCamelContext());
        firstExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-1");
        AiToolSearchSession firstSession = toolSearch.openSession(firstExchange, configuration);
        firstSession.remember(List.of("invoice"));

        toolSearch.closeSession(firstExchange, configuration);
        assertNull(firstExchange.getProperty(OpenAIToolSearch.SESSION_PROPERTY));

        Exchange secondExchange = new DefaultExchange(new DefaultCamelContext());
        secondExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-1");
        AiToolSearchSession secondSession = toolSearch.openSession(secondExchange, configuration);

        assertEquals(List.of("invoice"), secondSession.discoveredNames());
    }

    @Test
    void differentToolSearchSessionIdsDoNotShareSessions() {
        OpenAIToolSearch toolSearch = new OpenAIToolSearch();
        OpenAIConfiguration configuration = configuration(true);
        configuration.setConversationMemory(true);

        Exchange firstExchange = new DefaultExchange(new DefaultCamelContext());
        firstExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-1");
        AiToolSearchSession firstSession = toolSearch.openSession(firstExchange, configuration);
        firstSession.remember(List.of("invoice"));

        Exchange secondExchange = new DefaultExchange(new DefaultCamelContext());
        secondExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-2");
        AiToolSearchSession secondSession = toolSearch.openSession(secondExchange, configuration);

        assertNotSame(firstSession, secondSession);
        assertEquals(List.of(), secondSession.discoveredNames());
    }

    @Test
    void clearingSessionsStartsANewToolSearchSession() {
        OpenAIToolSearch toolSearch = new OpenAIToolSearch();
        OpenAIConfiguration configuration = configuration(true);
        configuration.setConversationMemory(true);

        Exchange firstExchange = new DefaultExchange(new DefaultCamelContext());
        firstExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-1");
        AiToolSearchSession firstSession = toolSearch.openSession(firstExchange, configuration);
        firstSession.remember(List.of("invoice"));

        toolSearch.clearSessions();

        Exchange secondExchange = new DefaultExchange(new DefaultCamelContext());
        secondExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-1");
        AiToolSearchSession secondSession = toolSearch.openSession(secondExchange, configuration);

        assertNotSame(firstSession, secondSession);
        assertEquals(List.of(), secondSession.discoveredNames());
    }

    @Test
    void sessionsAreNotSharedWhenConversationMemoryIsDisabled() {
        OpenAIToolSearch toolSearch = new OpenAIToolSearch();
        OpenAIConfiguration configuration = configuration(true);
        configuration.setConversationMemory(false);

        Exchange firstExchange = new DefaultExchange(new DefaultCamelContext());
        firstExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-1");
        AiToolSearchSession firstSession = toolSearch.openSession(firstExchange, configuration);
        firstSession.remember(List.of("invoice"));

        toolSearch.closeSession(firstExchange, configuration);
        assertNull(firstExchange.getProperty(OpenAIToolSearch.SESSION_PROPERTY));

        Exchange secondExchange = new DefaultExchange(new DefaultCamelContext());
        secondExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-session-1");
        AiToolSearchSession secondSession = toolSearch.openSession(secondExchange, configuration);

        assertNotSame(firstSession, secondSession);
        assertEquals(List.of(), secondSession.discoveredNames());
    }

    @Test
    void toolSearchSessionHeaderIsIndependentFromConfiguredResponsesConversationId() {
        OpenAIToolSearch toolSearch = new OpenAIToolSearch();
        OpenAIConfiguration configuration = configuration(true);
        configuration.setConversationMemory(true);
        configuration.setConversationId("responses-configured-id");

        Exchange firstExchange = new DefaultExchange(new DefaultCamelContext());
        firstExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-header-id");

        AiToolSearchSession headerSession = toolSearch.openSession(firstExchange, configuration);
        headerSession.remember(List.of("invoice"));
        toolSearch.closeSession(firstExchange, configuration);

        Exchange secondExchange = new DefaultExchange(new DefaultCamelContext());
        secondExchange.getIn().setHeader(OpenAIConstants.TOOL_SEARCH_SESSION_ID, "tool-search-header-id");
        AiToolSearchSession reusedHeaderSession = toolSearch.openSession(secondExchange, configuration);
        assertEquals(List.of("invoice"), reusedHeaderSession.discoveredNames());

        Exchange noToolSearchIdExchange = new DefaultExchange(new DefaultCamelContext());
        // This is the Responses API server-side conversation identifier, not a tool-search session identifier.
        noToolSearchIdExchange.getIn().setHeader(OpenAIConstants.CONVERSATION_ID, "responses-header-id");
        AiToolSearchSession exchangeScopedSession = toolSearch.openSession(noToolSearchIdExchange, configuration);
        assertNotSame(headerSession, exchangeScopedSession);
        assertEquals(List.of(), exchangeScopedSession.discoveredNames());
    }

    @Test
    void searchFailuresRespectConfiguredErrorStrategy() throws Exception {
        OpenAIConfiguration configuration = configuration(true);
        IllegalStateException failure = new IllegalStateException("embedding request failed");

        // Default FAIL_EXCHANGE policy propagates the original failure.
        assertThrows(IllegalStateException.class,
                () -> OpenAIToolSearch.handleSearchError(configuration, failure));

        // REPROMPT_MODEL turns the failure into an unsuccessful tool result.
        configuration.setToolExecutionErrorStrategy(ToolExecutionErrorStrategy.REPROMPT_MODEL);
        assertEquals(
                "Error: Tool search failed: embedding request failed",
                OpenAIToolSearch.handleSearchError(configuration, failure));
    }

    private static OpenAIConfiguration configuration(boolean toolSearch) {
        OpenAIConfiguration configuration = new OpenAIConfiguration();
        configuration.setToolSearch(toolSearch);
        configuration.setToolSearchStrategy(AiToolSearchStrategy.KEYWORD);
        configuration.setToolSearchMaxResults(5);
        return configuration;
    }

    private static McpToolState state() {
        AiToolSpec invoice = spec("invoice", "billing document", true);
        AiToolSpec ping = spec("ping", "health check", false);
        ChatCompletionFunctionTool invoiceTool = AiToolSpecToOpenAI.convert(invoice);
        ChatCompletionFunctionTool pingTool = AiToolSpecToOpenAI.convert(ping);
        ChatCompletionFunctionTool weather = AiToolSpecToOpenAI.function(
                "get_weather", "weather forecast", "{\"type\":\"object\"}");
        McpSyncClient client = mock(McpSyncClient.class);
        return new McpToolState(
                List.of(invoiceTool, pingTool, weather),
                Map.of("get_weather", client),
                Map.of("get_weather", "stub"),
                Set.of(),
                Map.of("invoice", invoice, "ping", ping));
    }

    private static AiToolSpec spec(String name, String description, boolean searchable) {
        return new AiToolSpec(
                name, description, Map.of(), "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}",
                null, null, null, null, searchable);
    }

    private static List<String> names(List<ChatCompletionFunctionTool> tools) {
        return tools.stream().map(tool -> tool.function().name()).toList();
    }
}
