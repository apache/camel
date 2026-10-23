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
import io.modelcontextprotocol.client.McpSyncClient;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.ai.tool.AiToolSearchCandidate;
import org.apache.camel.component.ai.tool.AiToolSearchIndex;
import org.apache.camel.component.ai.tool.AiToolSearchSession;
import org.apache.camel.component.ai.tool.AiToolSearchStrategy;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
