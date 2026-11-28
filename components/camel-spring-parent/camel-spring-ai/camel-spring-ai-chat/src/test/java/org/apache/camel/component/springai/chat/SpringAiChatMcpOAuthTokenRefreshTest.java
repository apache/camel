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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.apache.camel.component.springai.chat.mcp.SpringAiChatMcpManager;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a changed OAuth token is picked up on the next MCP request, proving that the token supplier is called
 * per-request rather than once at startup.
 * <p>
 * Tests cover both the {@code streamableHttp} transport (POST-based) and the {@code sse} transport (GET SSE stream +
 * POST message endpoint) to prove that {@code addHttpRequestCustomizer} is wired correctly on both
 * {@link io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.Builder} and
 * {@link io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.Builder}.
 */
class SpringAiChatMcpOAuthTokenRefreshTest {

    private static final String PROFILE = "refresh-test";
    private static final Set<String> STREAMABLE_PATHS = Set.of("/mcp");
    private static final Set<String> SSE_PATHS = Set.of("/sse");
    private static final List<String> RECORDED_AUTHORIZATIONS = new CopyOnWriteArrayList<>();

    private static ExecutorService executor;
    private static HttpServer httpServer;
    private static McpStatelessSyncServer mcpServer;
    private static DualTransport transport;
    private static String serverUrl;

    @BeforeAll
    static void startMcpServer() throws Exception {
        McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
        transport = new DualTransport(jsonMapper);

        McpSchema.Tool echo = McpSchema.Tool.builder("echo",
                Map.of("type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", List.of("message")))
                .description("Echoes the message")
                .build();
        mcpServer = McpServer.sync(transport)
                .serverInfo("token-refresh-test-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(McpStatelessServerFeatures.SyncToolSpecification.builder()
                        .tool(echo)
                        .callHandler((context, request) -> McpSchema.CallToolResult.builder(List.of(
                                McpSchema.TextContent.builder("echo: " + request.arguments().get("message")).build()))
                                .build())
                        .build())
                .build();

        InetAddress loopback = InetAddress.getLoopbackAddress();
        // SSE streams hold a thread until they are closed — pool must be unbounded
        executor = Executors.newCachedThreadPool();
        httpServer = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        httpServer.createContext("/", transport);
        httpServer.setExecutor(executor);
        httpServer.start();
        serverUrl = new URI("http", null, loopback.getHostAddress(), httpServer.getAddress().getPort(), null, null, null)
                .toString();
    }

    @AfterAll
    static void stopMcpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (mcpServer != null) {
            mcpServer.close();
        }
    }

    @AfterEach
    void resetStub() {
        StubOAuthClientAuthenticationFactory.reset(PROFILE);
        RECORDED_AUTHORIZATIONS.clear();
        transport.endSseStreams();
    }

    /**
     * The supplier returns token-1 for the first request (initialize) and token-2 for the second request (tools/list),
     * both within the same MCP session. This proves that the token supplier is called on every HTTP request rather than
     * once at client creation: the per-request customizer causes each POST to re-invoke the supplier, so a rotated
     * token appears immediately on the very next request on the same open connection.
     */
    @Test
    void tokenSupplierIsCalledPerRequestWithinSameSession() throws Exception {
        // Rotating supplier: first call → token-1 (initialize), second call → token-2 (tools/list)
        StubOAuthClientAuthenticationFactory.registerRotating(PROFILE, "token-1", "token-2");

        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            camelContext.start();

            SpringAiChatMcpManager manager = new SpringAiChatMcpManager();
            try {
                manager.initialize(
                        Map.<String, Object> of(
                                "local.transportType", "streamableHttp",
                                "local.url", serverUrl + "/mcp",
                                "local.oauthProfile", PROFILE),
                        10, camelContext);
                assertThat(manager.getToolCallbackProvider()).isNotNull();
            } finally {
                manager.close();
            }
            // The session sends at least two POSTs (initialize + tools/list). With per-request resolution the tokens
            // rotate across requests; without it every request would carry the same value baked at client creation.
            assertThat(RECORDED_AUTHORIZATIONS).hasSizeGreaterThanOrEqualTo(2);
            assertThat(RECORDED_AUTHORIZATIONS.get(0)).isEqualTo("Bearer token-1");
            assertThat(RECORDED_AUTHORIZATIONS.get(1)).isEqualTo("Bearer token-2");
        }
    }

    /**
     * The stub returns token-1 for the first session and then token-2 for the second session. All requests in the first
     * MCP session carry token-1; all requests in the second session carry token-2, proving per-request resolution via
     * the {@code streamableHttp} transport.
     */
    @Test
    void tokenChangeBetweenSessionsIsPickedUp() throws Exception {
        StubOAuthClientAuthenticationFactory.registerFixed(PROFILE, "token-1");

        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            camelContext.start();

            // First session: initializes and lists tools — all requests carry token-1
            SpringAiChatMcpManager manager1 = new SpringAiChatMcpManager();
            try {
                manager1.initialize(
                        Map.<String, Object> of(
                                "local.transportType", "streamableHttp",
                                "local.url", serverUrl + "/mcp",
                                "local.oauthProfile", PROFILE),
                        10, camelContext);
                assertThat(manager1.getToolCallbackProvider()).isNotNull();
            } finally {
                manager1.close();
            }
            List<String> firstSessionAuths = List.copyOf(RECORDED_AUTHORIZATIONS);
            assertThat(firstSessionAuths).isNotEmpty()
                    .allSatisfy(auth -> assertThat(auth).isEqualTo("Bearer token-1"));

            RECORDED_AUTHORIZATIONS.clear();

            // Switch the token the stub returns
            StubOAuthClientAuthenticationFactory.registerFixed(PROFILE, "token-2");

            // Second session: the stub now returns token-2
            SpringAiChatMcpManager manager2 = new SpringAiChatMcpManager();
            try {
                manager2.initialize(
                        Map.<String, Object> of(
                                "local.transportType", "streamableHttp",
                                "local.url", serverUrl + "/mcp",
                                "local.oauthProfile", PROFILE),
                        10, camelContext);
                assertThat(manager2.getToolCallbackProvider()).isNotNull();
            } finally {
                manager2.close();
            }
            assertThat(RECORDED_AUTHORIZATIONS).isNotEmpty()
                    .allSatisfy(auth -> assertThat(auth).isEqualTo("Bearer token-2"));
        }
    }

    /**
     * Same as {@link #tokenChangeBetweenSessionsIsPickedUp()} but over the {@code sse} transport — exercising the
     * {@link io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.Builder#addHttpRequestCustomizer}
     * overload rather than the streamableHttp builder, confirming both builders accept the same customizer API.
     */
    @Test
    void sseTransportSendsTokenOnEveryRequest() throws Exception {
        StubOAuthClientAuthenticationFactory.registerFixed(PROFILE, "token-sse");

        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            camelContext.start();

            SpringAiChatMcpManager manager = new SpringAiChatMcpManager();
            try {
                manager.initialize(
                        Map.<String, Object> of(
                                "local.transportType", "sse",
                                "local.url", serverUrl,
                                "local.oauthProfile", PROFILE),
                        10, camelContext);
                assertThat(manager.getToolCallbackProvider()).isNotNull();
            } finally {
                manager.close();
            }
            assertThat(RECORDED_AUTHORIZATIONS).as("SSE transport requests must carry the Authorization header")
                    .isNotEmpty()
                    .allSatisfy(auth -> assertThat(auth).isEqualTo("Bearer token-sse"));
        }
    }

    /**
     * Serves the stateless MCP server over both Streamable HTTP and HTTP+SSE, recording every Authorization header it
     * sees. The SSE case implements the legacy HTTP+SSE transport protocol: a GET to {@code /sse} opens the SSE event
     * stream whose first event names the message endpoint ({@code /message}); subsequent POSTs to that endpoint carry
     * JSON-RPC messages and responses are returned on the SSE stream.
     */
    private static final class DualTransport implements McpStatelessServerTransport, HttpHandler {

        private static final String END_OF_STREAM = "";

        private final McpJsonMapper jsonMapper;
        private final Map<String, SseSession> sseSessions = new ConcurrentHashMap<>();
        private volatile McpStatelessServerHandler mcpHandler;

        private record SseSession(BlockingQueue<String> events) {
        }

        DualTransport(McpJsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
            this.mcpHandler = mcpHandler;
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }

        @Override
        public List<String> protocolVersions() {
            return List.of(ProtocolVersions.MCP_2024_11_05, ProtocolVersions.MCP_2025_03_26,
                    ProtocolVersions.MCP_2025_06_18, ProtocolVersions.MCP_2025_11_25);
        }

        void endSseStreams() {
            sseSessions.values().forEach(session -> session.events().add(END_OF_STREAM));
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String httpMethod = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getRawQuery();
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");

                // SSE open: GET /sse → emit endpoint event, then stream responses
                if ("GET".equals(httpMethod) && SSE_PATHS.contains(path)) {
                    if (authorization != null) {
                        RECORDED_AUTHORIZATIONS.add(authorization);
                    }
                    streamSseEvents(exchange);
                    return;
                }

                // SSE message: POST /message?sessionId=<id>
                if ("POST".equals(httpMethod) && "/message".equals(path) && query != null
                        && query.startsWith("sessionId=")) {
                    String sessionId = query.substring("sessionId=".length());
                    SseSession sseSession = sseSessions.get(sessionId);
                    if (sseSession != null) {
                        if (authorization != null) {
                            RECORDED_AUTHORIZATIONS.add(authorization);
                        }
                        handleSseMessage(exchange, sseSession);
                        return;
                    }
                }

                // Streamable HTTP: POST /mcp
                if ("POST".equals(httpMethod) && STREAMABLE_PATHS.contains(path)) {
                    if (authorization != null) {
                        RECORDED_AUTHORIZATIONS.add(authorization);
                    }
                    handleStreamableHttpMessage(exchange);
                    return;
                }

                exchange.sendResponseHeaders(STREAMABLE_PATHS.contains(path) ? 405 : 404, -1);
            } finally {
                exchange.close();
            }
        }

        private void handleStreamableHttpMessage(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body);
            if (message instanceof McpSchema.JSONRPCRequest request) {
                McpSchema.JSONRPCResponse response = mcpHandler.handleRequest(McpTransportContext.EMPTY, request).block();
                byte[] json = jsonMapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(json);
                }
            } else if (message instanceof McpSchema.JSONRPCNotification notification) {
                mcpHandler.handleNotification(McpTransportContext.EMPTY, notification).block();
                exchange.sendResponseHeaders(202, -1);
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }

        private void handleSseMessage(HttpExchange exchange, SseSession session) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body);
            if (message instanceof McpSchema.JSONRPCRequest request) {
                McpSchema.JSONRPCResponse response = mcpHandler.handleRequest(McpTransportContext.EMPTY, request).block();
                session.events().add(jsonMapper.writeValueAsString(response));
            } else if (message instanceof McpSchema.JSONRPCNotification notification) {
                mcpHandler.handleNotification(McpTransportContext.EMPTY, notification).block();
            }
            exchange.sendResponseHeaders(202, -1);
        }

        private void streamSseEvents(HttpExchange exchange) throws IOException {
            String sessionId = UUID.randomUUID().toString();
            SseSession session = new SseSession(new LinkedBlockingQueue<>());
            sseSessions.put(sessionId, session);
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                OutputStream body = exchange.getResponseBody();
                writeEvent(body, "endpoint", "/message?sessionId=" + sessionId);
                for (String event = session.events().take();
                     !END_OF_STREAM.equals(event);
                     event = session.events().take()) {
                    writeEvent(body, "message", event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                sseSessions.remove(sessionId);
            }
        }

        private static void writeEvent(OutputStream body, String event, String data) throws IOException {
            body.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
            body.flush();
        }
    }
}
