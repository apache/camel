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
}
