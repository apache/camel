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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientGeminiTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void normalizeGeminiModelIdStripsModelsPrefix() {
        assertThat(LlmClient.normalizeGeminiModelId("models/gemini-2.0-flash")).isEqualTo("gemini-2.0-flash");
        assertThat(LlmClient.normalizeGeminiModelId("gemini-2.0-flash")).isEqualTo("gemini-2.0-flash");
    }

    @Test
    void extractGeminiModelIdsKeepsGenerateContentModelsOnly() throws Exception {
        String json = """
                {
                  "models": [
                    {"name": "models/gemini-2.0-flash", "supportedGenerationMethods": ["generateContent"]},
                    {"name": "models/embedding-001", "supportedGenerationMethods": ["embedContent"]}
                  ]
                }
                """;
        assertThat(LlmClient.extractGeminiModelIds((JsonObject) Jsoner.deserialize(json)))
                .containsExactly("gemini-2.0-flash");
    }

    @Test
    void geminiGenerateContentUrlAppendsApiKeyQueryParameter() {
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.gemini)
                .withUrl("https://generativelanguage.googleapis.com/v1beta")
                .withModel("gemini-2.0-flash")
                .withApiKey("gemini-key");

        assertThat(client.geminiGenerateContentUrl("gemini-key"))
                .isEqualTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash%3AgenerateContent?key=gemini-key");
    }

    @Test
    void listsGeminiModelsWithKeyQueryParameter() throws IOException {
        AtomicReference<String> query = new AtomicReference<>();
        String baseUrl = startGeminiModelsServer(query);
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.gemini)
                .withUrl(baseUrl)
                .withApiKey("gemini-key");

        assertThat(client.listModels()).containsExactly("gemini-2.0-flash", "gemini-1.5-flash");
        assertThat(query.get()).contains("key=gemini-key");
    }

    @Test
    void parseGeminiChatResponseExtractsFunctionCall() throws Exception {
        String json
                = """
                        {"candidates":[{"content":{"parts":[{"functionCall":{"name":"list_routes","args":{"q":"x"}}}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3}}
                        """;
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.gemini);
        LlmClient.ChatResponse response = client.parseGeminiChatResponse((JsonObject) Jsoner.deserialize(json));

        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("list_routes");
        assertThat(response.stopReason()).isEqualTo("tool_calls");
    }

    @Test
    void extractGeminiTextFromResponseReadsCandidateParts() throws Exception {
        String json = """
                {"candidates":[{"content":{"parts":[{"text":"Gemini says hi"}]},"finishReason":"STOP"}]}
                """;
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.gemini);

        assertThat(client.extractGeminiTextFromResponse((JsonObject) Jsoner.deserialize(json))).isEqualTo("Gemini says hi");
    }

    @Test
    void buildGeminiGenerateRequestIncludesFunctionDeclarations() {
        JsonObject parameters = new JsonObject();
        parameters.put("type", "object");
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.gemini);
        JsonObject request = client.buildGeminiGenerateRequestForTest(
                "system",
                List.of(LlmClient.Message.user("hello")),
                List.of(new LlmClient.ToolDef("list_routes", "List routes", parameters)));

        assertThat(request.get("tools")).isNotNull();
        assertThat(request.toJson()).contains("functionDeclarations").contains("list_routes");
    }

    private String startGeminiModelsServer(AtomicReference<String> capturedQuery) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models", exchange -> {
            capturedQuery.set(exchange.getRequestURI().getQuery());
            byte[] bytes = """
                    {"models":[
                      {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]},
                      {"name":"models/gemini-1.5-flash","supportedGenerationMethods":["generateContent"]}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
    }
}
