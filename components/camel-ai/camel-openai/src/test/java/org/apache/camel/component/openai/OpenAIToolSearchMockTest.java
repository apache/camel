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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.JsonNode;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.infra.openai.mock.RecordedRequest;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAIToolSearchMockTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("search off")
            .replyWith("off")
            .end()
            .when("search on")
            .replyWith("on")
            .end()
            .when("call invoice directly")
            .invokeTool("invoice")
            .replyWith("invoiced-direct")
            .end()
            .when("find invoice")
            .invokeTool(AiToolSearch.TOOL_NAME)
            .withParam("terms", List.of("invoice"))
            .invokeTool("invoice")
            .replyWith("invoiced")
            .end()
            .when("mixed batch")
            .invokeTool(AiToolSearch.TOOL_NAME)
            .withParam("terms", List.of("invoice"))
            .andInvokeTool("ping")
            .replyWith("mixed-done")
            .end()
            .when("missing tool")
            .invokeTool("not_a_tool")
            .replyWith("should-not-arrive")
            .end()
            .when("keep searching")
            .invokeTool(AiToolSearch.TOOL_NAME)
            .withParam("terms", List.of("invoice"))
            .invokeTool(AiToolSearch.TOOL_NAME)
            .withParam("terms", List.of("invoice"))
            .replyWith("too many")
            .end()
            .when("direct only")
            .invokeTool("lookup")
            .replyWith("should-not-arrive")
            .end()
            .when("need invoice")
            .invokeTool(AiToolSearch.TOOL_NAME)
            .withParam("terms", List.of("invoice"))
            .replyWith("invoice-done")
            .end()
            .when("need weather")
            .invokeTool(AiToolSearch.TOOL_NAME)
            .withParam("terms", List.of("weather"))
            .replyWith("weather-done")
            .end()
            .when("find weather semantically")
            .invokeTool(AiToolSearch.TOOL_NAME)
            .withParam("query", "weather")
            .invokeTool("get_weather")
            .replyWith("semantic-weather")
            .end()
            .whenEmbedding("invoice\nbilling-document-invoice")
            .replyWithEmbedding(new float[] { 0.0f, 1.0f })
            .end()
            .whenEmbedding("get_weather\nweather forecast")
            .replyWithEmbedding(new float[] { 1.0f, 0.0f })
            .end()
            .whenEmbedding("weather")
            .replyWithEmbedding(new float[] { 1.0f, 0.0f })
            .end()
            .build();

    @Test
    void toolSearchOffSendsSearchableTools() {
        template.sendBody("direct:off", "search off");

        List<String> names = toolNamesFor("search off").get(0);
        assertTrue(names.contains("invoice"));
        assertTrue(names.contains("ping"));
        assertFalse(names.contains(AiToolSearch.TOOL_NAME));
    }

    @Test
    void toolSearchOnOmitsSearchableToolsUntilTheyAreFound() {
        template.sendBody("direct:on", "search on");

        List<String> names = toolNamesFor("search on").get(0);
        assertTrue(names.contains("ping"));
        assertTrue(names.contains(AiToolSearch.TOOL_NAME));
        assertFalse(names.contains("invoice"));
        assertFalse(names.contains("get_weather"));
    }

    @Test
    void searchThenCallsTheOriginalHandler() {
        injectWeather("openai:chat-completion?model=gpt-4o-mini&apiKey=dummy&autoToolExecution=true&toolSearch=true"
                      + "&tags=search&baseUrl=" + openAIMock.getBaseUrl() + "/v1");

        String body = template.requestBody("direct:on", "find invoice", String.class);

        assertEquals("invoiced", body);
        List<List<String>> turns = toolNamesFor("find invoice");
        assertFalse(turns.get(0).contains("invoice"));
        assertTrue(turns.get(1).contains("invoice"));
        assertFalse(turns.get(1).contains("get_weather"));
    }

    @Test
    void vectorSearchFindsAndExecutesTheSemanticallyMatchingMcpTool() {
        String endpointUri = "openai:chat-completion?model=gpt-4o-mini&apiKey=dummy"
                             + "&autoToolExecution=true&toolSearch=true"
                             + "&toolSearchStrategy=vector&toolSearchEmbeddingModel=text-embedding-ada-002"
                             + "&toolSearchMaxResults=1&tags=search&baseUrl="
                             + openAIMock.getBaseUrl() + "/v1";
        injectWeather(endpointUri);

        String body = template.requestBody(
                "direct:vector", "find weather semantically", String.class);

        assertEquals("semantic-weather", body);
        List<List<String>> turns = toolNamesFor("find weather semantically");
        assertTrue(turns.size() >= 2);
        assertTrue(turns.get(0).contains(AiToolSearch.TOOL_NAME));
        assertFalse(turns.get(0).contains("invoice"));
        assertTrue(turns.get(1).contains("get_weather"));
        assertFalse(turns.get(1).contains("invoice"));
    }

    @Test
    void mixedSearchAndNormalCallKeepsOrderAndIsNotReturnDirect() {
        Exchange result = template.request("direct:on", e -> e.getIn().setBody("mixed batch"));

        assertEquals("mixed-done", result.getMessage().getBody(String.class));
        assertEquals(List.of(AiToolSearch.TOOL_NAME, "ping"),
                result.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class));
        assertFalse(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));
    }

    @Test
    void hallucinatedNameStillFailsTheExchange() {
        Exchange result = template.request("direct:on", e -> e.getIn().setBody("missing tool"));

        assertInstanceOf(IllegalStateException.class, result.getException());
        assertTrue(result.getException().getMessage().contains("not_a_tool"));
    }

    @Test
    void iterationLimitStillFailsTheExchange() {
        Exchange result = template.request("direct:limited", e -> e.getIn().setBody("keep searching"));

        assertInstanceOf(IllegalStateException.class, result.getException());
        assertTrue(result.getException().getMessage().contains("Max tool iterations"));
    }

    @Test
    void returnDirectStillShortCircuitsAVisibleTool() {
        Exchange result = template.request("direct:direct", e -> e.getIn().setBody("direct only"));

        assertEquals("looked-up", result.getMessage().getBody(String.class));
        assertTrue(result.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class));
    }

    @Test
    void concurrentExchangesDoNotShareDiscoveries() throws Exception {
        injectWeather("openai:chat-completion?model=gpt-4o-mini&apiKey=dummy&autoToolExecution=true&toolSearch=true"
                      + "&tags=search&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> invoice = pool.submit(() -> template.requestBody("direct:on", "need invoice", String.class));
            Future<String> weather = pool.submit(() -> template.requestBody("direct:on", "need weather", String.class));
            assertEquals("invoice-done", invoice.get());
            assertEquals("weather-done", weather.get());
        } finally {
            pool.shutdownNow();
        }

        List<String> invoiceSecond = toolNamesFor("need invoice").get(1);
        List<String> weatherSecond = toolNamesFor("need weather").get(1);
        assertTrue(invoiceSecond.contains("invoice"));
        assertFalse(invoiceSecond.contains("get_weather"));
        assertTrue(weatherSecond.contains("get_weather"));
        assertFalse(weatherSecond.contains("invoice"));
    }

    @Test
    void conversationMemoryReusesDiscoveriesAcrossExchanges() {
        String endpointUri = "openai:chat-completion?model=gpt-4o-mini&apiKey=dummy"
                             + "&autoToolExecution=true&toolSearch=true&conversationMemory=true"
                             + "&tags=search&baseUrl=" + openAIMock.getBaseUrl() + "/v1";
        injectWeather(endpointUri);

        Exchange first = template.request(endpointUri, e -> {
            e.getIn().setBody("find invoice");
            e.getIn().setHeader(OpenAIConstants.CONVERSATION_ID, "conv-shared");
        });
        assertNull(first.getException());

        List<RecordedRequest> afterFirst = openAIMock.getReceivedRequests();
        int secondExchangeStart = afterFirst.size();
        assertTrue(secondExchangeStart > 0);
        assertFalse(toolNames(afterFirst.get(0).bodyAsJson()).contains("invoice"));

        Exchange second = template.request(endpointUri, e -> {
            e.getIn().setBody("call invoice directly");
            e.getIn().setHeader(OpenAIConstants.CONVERSATION_ID, "conv-shared");
        });
        assertNull(second.getException());
        assertEquals("invoiced-direct", second.getMessage().getBody(String.class));
        assertEquals(List.of("invoice"),
                second.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class));

        List<RecordedRequest> afterSecond = openAIMock.getReceivedRequests();
        assertTrue(afterSecond.size() > secondExchangeStart);
        assertTrue(
                toolNames(afterSecond.get(secondExchangeStart).bodyAsJson()).contains("invoice"));

        int thirdExchangeStart = afterSecond.size();
        Exchange third = template.request(endpointUri, e -> {
            e.getIn().setBody("find invoice");
            e.getIn().setHeader(OpenAIConstants.CONVERSATION_ID, "conv-other");
        });
        assertEquals(null, third.getException());

        List<RecordedRequest> afterThird = openAIMock.getReceivedRequests();
        assertTrue(afterThird.size() > thirdExchangeStart);
        assertFalse(
                toolNames(afterThird.get(thirdExchangeStart).bodyAsJson()).contains("invoice"));
    }

    private static List<String> toolNames(JsonNode root) {
        List<String> names = new ArrayList<>();
        for (JsonNode tool : root.path("tools")) {
            names.add(tool.path("function").path("name").asText());
        }
        return names;
    }

    private void injectWeather(String endpointUri) {
        OpenAIEndpoint endpoint = context.getEndpoint(endpointUri, OpenAIEndpoint.class);
        McpToolState current = endpoint.getMcpToolState();
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(null, "sunny", null)))
                .isError(false)
                .build());
        List<ChatCompletionFunctionTool> tools = new ArrayList<>(current.tools());
        tools.add(AiToolSpecToOpenAI.function("get_weather", "weather forecast", "{\"type\":\"object\"}"));
        endpoint.setMcpToolState(new McpToolState(
                tools, Map.of("get_weather", client), Map.of("get_weather", "stub"), Set.of(), current.routeTools()));
    }

    private List<List<String>> toolNamesFor(String prompt) {
        List<List<String>> turns = new ArrayList<>();
        for (RecordedRequest request : openAIMock.getReceivedRequests()) {
            JsonNode root = request.bodyAsJson();
            if (!containsUserText(root, prompt)) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (JsonNode tool : root.path("tools")) {
                names.add(tool.path("function").path("name").asText());
            }
            turns.add(names);
        }
        return turns;
    }

    private static boolean containsUserText(JsonNode root, String prompt) {
        for (JsonNode message : root.path("messages")) {
            if (prompt.equals(message.path("content").asText())) {
                return true;
            }
            JsonNode content = message.path("content");
            if (content.isArray()) {
                for (JsonNode part : content) {
                    if (prompt.equals(part.path("text").asText())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String base = openAIMock.getBaseUrl() + "/v1";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:invoice?tags=search&searchable=true&description=billing-document-invoice")
                        .setBody(constant("invoice-body"));
                from("ai-tool:ping?tags=search&description=health-check")
                        .setBody(constant("pong"));
                from("ai-tool:lookup?tags=direct&description=order-lookup&returnDirect=true")
                        .setBody(constant("looked-up"));

                from("direct:off")
                        .to("openai:chat-completion?model=gpt-4o-mini&apiKey=dummy&autoToolExecution=true&tags=search&baseUrl="
                            + base);
                from("direct:on")
                        .to("openai:chat-completion?model=gpt-4o-mini&apiKey=dummy&autoToolExecution=true&toolSearch=true&tags=search&baseUrl="
                            + base);
                from("direct:vector")
                        .to("openai:chat-completion?model=gpt-4o-mini&apiKey=dummy&autoToolExecution=true"
                            + "&toolSearch=true&toolSearchStrategy=vector"
                            + "&toolSearchEmbeddingModel=text-embedding-ada-002&toolSearchMaxResults=1"
                            + "&tags=search&baseUrl=" + base);
                from("direct:limited")
                        .to("openai:chat-completion?model=gpt-4o-mini&apiKey=dummy&autoToolExecution=true&toolSearch=true"
                            + "&maxToolIterations=1&tags=search&baseUrl=" + base);
                from("direct:direct")
                        .to("openai:chat-completion?model=gpt-4o-mini&apiKey=dummy&autoToolExecution=true&toolSearch=true&tags=direct&baseUrl="
                            + base);
            }
        };
    }
}
