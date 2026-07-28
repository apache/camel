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
    void normalizeGeminiBaseUrlStripsGenerateContentSuffix() {
        assertThat(LlmClient.normalizeGeminiBaseUrl(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta");
        assertThat(LlmClient.normalizeGeminiBaseUrl(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash%3AgenerateContent"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta");
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
    void geminiGenerateContentUrlUsesColonActionSegment() {
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.gemini)
                .withUrl("https://generativelanguage.googleapis.com/v1beta")
                .withModel("gemini-2.0-flash")
                .withApiKey("gemini-key");

        assertThat(client.geminiGenerateContentUrl())
                .isEqualTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent");
    }

    @Test
    void listsGeminiModelsWithApiKeyHeader() throws IOException {
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        String baseUrl = startGeminiModelsServer(apiKeyHeader);
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.gemini)
                .withUrl(baseUrl)
                .withApiKey("gemini-key");

        assertThat(client.listModels()).containsExactly("gemini-2.0-flash", "gemini-1.5-flash");
        assertThat(apiKeyHeader.get()).isEqualTo("gemini-key");
    }

    @Test
    void parseGeminiChatResponseExtractsFunctionCallIdArgsAndThoughtSignature() throws Exception {
        String json
                = """
                        {"candidates":[{"content":{"parts":[{"functionCall":{"id":"call-42","name":"list_routes","args":{"q":"x"},"thoughtSignature":"sig-abc"}}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3}}
                        """;
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.gemini);
        LlmClient.ChatResponse response = client.parseGeminiChatResponse((JsonObject) Jsoner.deserialize(json));

        assertThat(response.toolCalls()).hasSize(1);
        LlmClient.ToolCall call = response.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call-42");
        assertThat(call.name()).isEqualTo("list_routes");
        assertThat(call.arguments().getString("q")).isEqualTo("x");
        assertThat(call.thoughtSignature()).isEqualTo("sig-abc");
        assertThat(response.stopReason()).isEqualTo("tool_calls");
    }

    @Test
    void buildGeminiGenerateRequestPreservesThoughtSignatureOnModelTurn() {
        JsonObject args = new JsonObject();
        args.put("q", "x");
        LlmClient.ToolCall call = new LlmClient.ToolCall("call-42", "list_routes", args, "sig-abc");
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.gemini);
        JsonObject request = client.buildGeminiGenerateRequestForTest(
                "system",
                List.of(LlmClient.Message.assistantWithToolCalls(null, List.of(call))),
                null);

        assertThat(request.toJson()).contains("thoughtSignature").contains("sig-abc").contains("call-42");
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

    @Test
    void detectEndpointAcceptsConfiguredGeminiApiKeyWithoutEnv() {
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.gemini)
                .withApiKey("configured-key");

        assertThat(client.detectEndpoint()).isTrue();
        assertThat(client.apiType()).isEqualTo(LlmClient.ApiType.gemini);
    }

    private String startGeminiModelsServer(AtomicReference<String> capturedApiKeyHeader) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models", exchange -> {
            capturedApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
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
