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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link LlmClient#listModels()} against each provider's real response shape (Ollama's {@code /api/tags},
 * OpenAI/Anthropic's {@code /v1/models}) using a local {@link HttpServer} instead of mocking the HTTP client, so the
 * request path, auth headers, and JSON parsing are all exercised together.
 */
class LlmClientListModelsTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(String path, int status, String body, AtomicReference<String> capturedHeader, String headerName)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            if (capturedHeader != null) {
                capturedHeader.set(exchange.getRequestHeaders().getFirst(headerName));
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String startRootServer(String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            int status = "/".equals(exchange.getRequestURI().getPath()) ? 200 : 404;
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void detectsOllamaFromRootResponseOnNonDefaultPort() throws IOException {
        String baseUrl = startRootServer("Ollama is running");
        LlmClient client = LlmClient.create().withUrl(baseUrl);

        assertTrue(client.detectEndpoint());
        assertEquals(LlmClient.ApiType.ollama, client.apiType());
    }

    @Test
    void listsOllamaModelsFromApiTags() throws IOException {
        String baseUrl = startServer("/api/tags", 200,
                "{\"models\":[{\"name\":\"llama3.2:latest\"},{\"name\":\"qwen3\"}]}", null, null);
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl(baseUrl);

        assertEquals(List.of("llama3.2:latest", "qwen3"), client.listModels());
    }

    @Test
    void listsOpenAiModelsFromV1ModelsWithBearerAuth() throws IOException {
        AtomicReference<String> authHeader = new AtomicReference<>();
        String baseUrl = startServer("/v1/models", 200,
                "{\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"gpt-4o-mini\"}]}", authHeader, "Authorization");
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.openai).withUrl(baseUrl).withApiKey("sk-test");

        assertEquals(List.of("gpt-4o", "gpt-4o-mini"), client.listModels());
        assertEquals("Bearer sk-test", authHeader.get());
    }

    @Test
    void listsAnthropicModelsFromV1ModelsWithApiKeyHeader() throws IOException {
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        String baseUrl = startServer("/v1/models", 200,
                "{\"data\":[{\"id\":\"claude-sonnet-4-6\"}]}", apiKeyHeader, "x-api-key");
        LlmClient client
                = LlmClient.create().withApiType(LlmClient.ApiType.anthropic).withUrl(baseUrl).withApiKey("sk-ant-test");

        assertEquals(List.of("claude-sonnet-4-6"), client.listModels());
        assertEquals("sk-ant-test", apiKeyHeader.get());
    }

    @Test
    void returnsEmptyListOnNonOkStatus() throws IOException {
        String baseUrl = startServer("/api/tags", 500, "{\"error\":\"boom\"}", null, null);
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl(baseUrl);

        assertTrue(client.listModels().isEmpty());
    }

    @Test
    void returnsEmptyListWhenModelListHasUnexpectedShape() throws IOException {
        String baseUrl = startServer("/api/tags", 200, "{\"models\":{}}", null, null);
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl(baseUrl);

        assertTrue(client.listModels().isEmpty());
    }

    @Test
    void returnsEmptyListWhenEndpointUnreachable() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl("http://127.0.0.1:1");

        assertTrue(client.listModels().isEmpty());
    }

    @Test
    void returnsEmptyListWhenApiTypeNotYetDetected() {
        LlmClient client = LlmClient.create();

        assertTrue(client.listModels().isEmpty());
    }

    @Test
    void listsOpenAiModelsWhenConfiguredWithFullChatCompletionsUrl() throws IOException {
        // A user may configure the full chat endpoint; model discovery must derive /v1/models from the same /v1 base
        // instead of appending onto /v1/chat/completions.
        String baseUrl = startServer("/v1/models", 200, "{\"data\":[{\"id\":\"gpt-4o\"}]}", null, null);
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.openai)
                .withUrl(baseUrl + "/v1/chat/completions")
                .withApiKey("sk-test");

        assertEquals(List.of("gpt-4o"), client.listModels());
    }

    @Test
    void assignsDefaultOpenAiModelWhenDetectionResolvesNoModel() throws IOException {
        String baseUrl = startServer("/v1/models", 200, "{\"data\":[]}", null, null);
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.openai).withUrl(baseUrl);

        assertTrue(client.detectEndpoint());
        assertEquals(LlmClient.ApiType.openai, client.apiType());
        assertEquals("gpt-4o-mini", client.model(), "OpenAI detection must leave a usable default model, not null");
    }

    @Test
    void fallsBackToDefaultOllamaModelWhenModelsCannotBeListed() throws IOException {
        // Root responds so the endpoint is detected, but /api/tags is unavailable, so the installed models cannot be
        // listed; detection must still leave a usable (non-null) model.
        String baseUrl = startRootServer("Ollama is running");
        LlmClient client = LlmClient.create().withUrl(baseUrl);

        assertTrue(client.detectEndpoint());
        assertEquals(LlmClient.ApiType.ollama, client.apiType());
        assertEquals("llama3.2", client.model(), "Ollama detection must leave a usable default model, not null");
    }

    @Test
    void picksFirstInstalledOllamaModelWhenNoneConfiguredAndLlama32Absent() throws IOException {
        String baseUrl = startOllamaServer("{\"models\":[{\"name\":\"qwen3\"},{\"name\":\"mistral\"}]}");
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl(baseUrl);

        assertTrue(client.detectEndpoint());
        assertEquals("qwen3", client.model(), "must pick the first installed model when llama3.2 is not installed");
    }

    @Test
    void prefersInstalledLlama32OllamaModelWhenNoneConfigured() throws IOException {
        String baseUrl = startOllamaServer("{\"models\":[{\"name\":\"qwen3\"},{\"name\":\"llama3.2:latest\"}]}");
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl(baseUrl);

        assertTrue(client.detectEndpoint());
        assertEquals("llama3.2:latest", client.model(), "must prefer an installed llama3.2 over the first entry");
    }

    /**
     * Starts a server that answers the Ollama root probe ({@code "Ollama is running"}) and serves the given
     * {@code /api/tags} body, so endpoint detection and model resolution both succeed against it.
     */
    private String startOllamaServer(String apiTagsBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = "/api/tags".equals(path) ? apiTagsBody : "Ollama is running";
            int status = "/".equals(path) || "/api/tags".equals(path) ? 200 : 404;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
