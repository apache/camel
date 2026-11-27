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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpSyncClient;
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
 */
class OpenAIMcpOAuthTokenRefreshTest {

    private static final String PROFILE = "refresh-test";
    private static final Set<String> SERVED_PATHS = Set.of("/mcp");
    private static final List<String> RECORDED_AUTHORIZATIONS = new CopyOnWriteArrayList<>();

    private static ExecutorService executor;
    private static HttpServer httpServer;
    private static McpStatelessSyncServer mcpServer;
    private static String serverUrl;

    @BeforeAll
    static void startMcpServer() throws Exception {
        McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
        RecordingTransport transport = new RecordingTransport(jsonMapper);

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
    }

    /**
     * The stub returns token-1 for the first session and then token-2 for the second session. All requests in the first
     * MCP session carry token-1; all requests in the second session carry token-2, proving per-request resolution.
     */
    @Test
    void tokenChangeBetweenSessionsIsPickedUp() throws Exception {
        StubOAuthClientAuthenticationFactory.registerFixed(PROFILE, "token-1");

        DefaultCamelContext camelContext = new DefaultCamelContext();
        camelContext.start();
        try {
            OpenAIComponent component = new OpenAIComponent();
            component.setCamelContext(camelContext);
            OpenAIConfiguration configuration = new OpenAIConfiguration();
            configuration.setMcpTimeout(10);
            OpenAIEndpoint endpoint = new OpenAIEndpoint("openai:chat-completion", component, configuration);
            endpoint.setCamelContext(camelContext);

            // First session: initializes and lists tools — all requests carry token-1
            McpSyncClient client1 = endpoint.createMcpClient("local",
                    Map.of("transportType", "streamableHttp", "url", serverUrl + "/mcp", "oauthProfile", PROFILE));
            try {
                assertThat(client1.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("echo");
            } finally {
                client1.closeGracefully();
            }
            List<String> firstSessionAuths = List.copyOf(RECORDED_AUTHORIZATIONS);
            assertThat(firstSessionAuths).isNotEmpty()
                    .allSatisfy(auth -> assertThat(auth).isEqualTo("Bearer token-1"));

            RECORDED_AUTHORIZATIONS.clear();

            // Switch the token the stub returns
            StubOAuthClientAuthenticationFactory.registerFixed(PROFILE, "token-2");

            // Second session: the stub now returns token-2
            McpSyncClient client2 = endpoint.createMcpClient("local",
                    Map.of("transportType", "streamableHttp", "url", serverUrl + "/mcp", "oauthProfile", PROFILE));
            try {
                assertThat(client2.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("echo");
            } finally {
                client2.closeGracefully();
            }
            assertThat(RECORDED_AUTHORIZATIONS).isNotEmpty()
                    .allSatisfy(auth -> assertThat(auth).isEqualTo("Bearer token-2"));
        } finally {
            camelContext.stop();
        }
    }

    /**
     * A minimal Streamable HTTP handler that records the Authorization header of every POST request.
     */
    private static final class RecordingTransport implements McpStatelessServerTransport, HttpHandler {

        private final McpJsonMapper jsonMapper;
        private volatile McpStatelessServerHandler mcpHandler;

        RecordingTransport(McpJsonMapper jsonMapper) {
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

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String httpMethod = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization != null && "POST".equals(httpMethod)) {
                    RECORDED_AUTHORIZATIONS.add(authorization);
                }

                if (!SERVED_PATHS.contains(path) || !"POST".equals(httpMethod)) {
                    exchange.sendResponseHeaders(SERVED_PATHS.contains(path) ? 405 : 404, -1);
                    return;
                }

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body);
                if (message instanceof McpSchema.JSONRPCRequest request) {
                    McpSchema.JSONRPCResponse response
                            = mcpHandler.handleRequest(McpTransportContext.EMPTY, request).block();
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
            } finally {
                exchange.close();
            }
        }
    }
}
