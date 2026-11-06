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
package org.apache.camel.component.mcp.server.vertx;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that the Vert.x streamable transport checks the {@code Origin} header on every method of the MCP endpoint
 * (CAMEL-26359).
 */
class VertxMcpOriginTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    private static final String INITIALIZED_NOTIFICATION = """
            {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""";

    private static final String CALL_TOOL_REQUEST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"ping","arguments":{}}}""";

    private CamelContext camelContext;
    private VertxMcpServerEngine engine;
    private int port;

    @AfterEach
    void stop() {
        if (engine != null) {
            engine.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void requestWithoutOriginIsServed() throws Exception {
        start(null);

        HttpResponse<String> response = post(null, null, INITIALIZE_REQUEST);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("mcp-session-id")).isPresent();
        assertThat(engine.sessionCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost", "http://localhost:6274", "https://localhost:8443",
            "http://127.0.0.1:3000", "http://[::1]:8080" })
    void loopbackOriginIsServedByDefault(String origin) throws Exception {
        start(null);

        HttpResponse<String> response = post(origin, null, INITIALIZE_REQUEST);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(engine.sessionCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://app.example.com", "https://localhost.example.com", "http://127.0.0.2:8080", "null" })
    void otherOriginIsRejectedByDefault(String origin) throws Exception {
        start(null);

        HttpResponse<String> response = post(origin, null, INITIALIZE_REQUEST);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        assertThat(response.body())
                .contains("\"jsonrpc\":\"2.0\"")
                .contains("\"code\":-32600")
                .contains("Origin not allowed")
                .doesNotContain("\"id\"");
        assertThat(response.headers().firstValue("mcp-session-id")).isEmpty();
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void configuredOriginsReplaceTheLoopbackDefault() throws Exception {
        start(List.of("https://app.example.com", "http://tools.example.com:*"));

        assertThat(post("https://app.example.com", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
        assertThat(post("http://tools.example.com:9000", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
        assertThat(post("https://app.example.com:8443", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(403);
        assertThat(post("http://app.example.com", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(403);
        assertThat(post("http://localhost:6274", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(403);
        assertThat(post(null, null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
    }

    @Test
    void originsAreComparedIgnoringCaseAndDefaultPort() throws Exception {
        start(List.of("https://App.Example.com:443/", "http://Tools.Example.com:*"));

        assertThat(post("https://app.example.com", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
        assertThat(post("HTTPS://APP.EXAMPLE.COM:443", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
        assertThat(post("http://tools.example.com", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
        assertThat(post("http://TOOLS.example.com:9000", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
        assertThat(post("https://app.example.com:8443", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(403);
        assertThat(post("https://app.example.com.evil.test", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(403);
    }

    @Test
    void loopbackOriginIsComparedIgnoringCase() throws Exception {
        start(null);

        assertThat(post("http://LocalHost:6274", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
    }

    @Test
    void rejectedOriginDoesNotCallTheTool() throws Exception {
        start(null);
        AtomicInteger calls = new AtomicInteger();
        engine.toolAdded(tool("ping", arguments -> {
            calls.incrementAndGet();
            return new McpToolCallResult("pong", false);
        }));
        String sessionId = initializeSession();

        HttpResponse<String> rejected = post("http://app.example.com", sessionId, CALL_TOOL_REQUEST);
        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(calls).hasValue(0);

        HttpResponse<String> served = post("http://localhost:6274", sessionId, CALL_TOOL_REQUEST);
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).contains("pong");
        assertThat(calls).hasValue(1);
    }

    @Test
    void sseChannelChecksTheOrigin() throws Exception {
        start(null);
        String sessionId = initializeSession();

        HttpResponse<InputStream> rejected = get("http://app.example.com", sessionId);
        try (InputStream body = rejected.body()) {
            assertThat(rejected.statusCode()).isEqualTo(403);
        }

        // the SSE response headers go out with the first event, so add a tool to make the server send one
        CompletableFuture<HttpResponse<InputStream>> pending = HTTP.sendAsync(
                getRequest("http://localhost:6274", sessionId), HttpResponse.BodyHandlers.ofInputStream());
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            engine.toolAdded(tool("tool" + System.nanoTime(), arguments -> new McpToolCallResult("ok", false)));
            assertThat(pending).isDone();
        });
        HttpResponse<InputStream> served = pending.get();
        try (InputStream body = served.body()) {
            assertThat(served.statusCode()).isEqualTo(200);
            assertThat(served.headers().firstValue("Content-Type")).hasValue("text/event-stream");
        }
    }

    @Test
    void deleteChecksTheOrigin() throws Exception {
        start(null);
        String sessionId = initializeSession();

        assertThat(delete("http://app.example.com", sessionId)).isEqualTo(403);
        assertThat(engine.sessionCount()).isEqualTo(1);

        assertThat(delete("http://127.0.0.1:6274", sessionId)).isEqualTo(200);
        assertThat(engine.sessionCount()).isZero();
    }

    private void start(List<String> allowedOrigins) throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        camelContext = new DefaultCamelContext();
        MainHttpServer main = new MainHttpServer();
        main.setCamelContext(camelContext);
        main.setHost("0.0.0.0");
        main.setPort(port);
        camelContext.addService(main);
        camelContext.start();

        engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.initialize(new McpServerInfo(
                "origin", "1.0", "/mcp", 0, 0, null, null, null, null, null, allowedOrigins));
        engine.start();
    }

    private String initializeSession() throws Exception {
        HttpResponse<String> response = post(null, null, INITIALIZE_REQUEST);
        assertThat(response.statusCode()).isEqualTo(200);
        String sessionId = response.headers().firstValue("mcp-session-id").orElseThrow();
        assertThat(post(null, sessionId, INITIALIZED_NOTIFICATION).statusCode()).isEqualTo(202);
        return sessionId;
    }

    private HttpResponse<String> post(String origin, String sessionId, String body) throws Exception {
        HttpRequest.Builder builder = request(origin, sessionId)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<InputStream> get(String origin, String sessionId) throws Exception {
        return HTTP.send(getRequest(origin, sessionId), HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpRequest getRequest(String origin, String sessionId) {
        return request(origin, sessionId)
                .header("Accept", "text/event-stream")
                .GET()
                .build();
    }

    private int delete(String origin, String sessionId) throws Exception {
        HttpRequest.Builder builder = request(origin, sessionId).DELETE();
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpRequest.Builder request(String origin, String sessionId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10));
        if (origin != null) {
            builder.header("Origin", origin);
        }
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return builder;
    }

    private static McpServerTool tool(String name, McpToolCallHandler handler) {
        return new McpServerTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "Answers pong";
            }

            @Override
            public String inputSchemaJson() {
                return null;
            }

            @Override
            public Map<String, ParameterDef> parameters() {
                return Map.of();
            }

            @Override
            public McpToolCallHandler handler() {
                return handler;
            }
        };
    }
}
