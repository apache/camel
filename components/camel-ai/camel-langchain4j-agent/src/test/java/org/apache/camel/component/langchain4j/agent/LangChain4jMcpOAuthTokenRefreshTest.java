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
package org.apache.camel.component.langchain4j.agent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.McpClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a changed OAuth token is picked up on the next MCP request, proving that the {@code customHeaders}
 * supplier is called per-request rather than once at startup.
 */
class LangChain4jMcpOAuthTokenRefreshTest {

    private static final String PROFILE = "refresh-test";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> RECORDED_AUTHORIZATIONS = new CopyOnWriteArrayList<>();

    private static ExecutorService executor;
    private static HttpServer httpServer;
    private static String serverUrl;

    @BeforeAll
    static void startMcpServer() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        executor = Executors.newCachedThreadPool();
        httpServer = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        httpServer.createContext("/mcp", LangChain4jMcpOAuthTokenRefreshTest::handleMcpRequest);
        httpServer.setExecutor(executor);
        httpServer.start();
        serverUrl = new URI("http", null, loopback.getHostAddress(), httpServer.getAddress().getPort(), "/mcp", null, null)
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

        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            camelContext.start();

            // First session: initializes and lists tools — all requests carry token-1
            LangChain4jMcpServerDefinition def1 = createDefinition();
            McpClient client1 = def1.buildClient(camelContext);
            try {
                assertThat(client1.listTools()).isNotNull();
            } finally {
                client1.close();
            }
            List<String> firstSessionAuths = List.copyOf(RECORDED_AUTHORIZATIONS);
            assertThat(firstSessionAuths).isNotEmpty()
                    .allSatisfy(auth -> assertThat(auth).isEqualTo("Bearer token-1"));

            RECORDED_AUTHORIZATIONS.clear();

            // Switch the token the stub returns
            StubOAuthClientAuthenticationFactory.registerFixed(PROFILE, "token-2");

            // Second session: the stub now returns token-2
            LangChain4jMcpServerDefinition def2 = createDefinition();
            McpClient client2 = def2.buildClient(camelContext);
            try {
                assertThat(client2.listTools()).isNotNull();
            } finally {
                client2.close();
            }
            assertThat(RECORDED_AUTHORIZATIONS).isNotEmpty()
                    .allSatisfy(auth -> assertThat(auth).isEqualTo("Bearer token-2"));
        }
    }

    private LangChain4jMcpServerDefinition createDefinition() {
        LangChain4jMcpServerDefinition def = new LangChain4jMcpServerDefinition();
        def.setServerName("token-refresh-test");
        def.setTransportType("http");
        def.setUrl(serverUrl);
        def.setOauthProfile(PROFILE);
        return def;
    }

    /**
     * A minimal MCP Streamable HTTP handler that records the Authorization header and responds to initialize and
     * tools/list JSON-RPC requests.
     */
    private static void handleMcpRequest(HttpExchange exchange) throws IOException {
        try {
            String httpMethod = exchange.getRequestMethod();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && "POST".equals(httpMethod)) {
                RECORDED_AUTHORIZATIONS.add(authorization);
            }

            if (!"POST".equals(httpMethod)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode message = MAPPER.readTree(body);
            String method = message.path("method").asText("");
            Object id = message.has("id") ? message.get("id") : null;

            if (id == null) {
                // notification
                exchange.sendResponseHeaders(202, -1);
                return;
            }

            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", message.get("id"));

            switch (method) {
                case "initialize" -> {
                    ObjectNode result = MAPPER.createObjectNode();
                    result.put("protocolVersion", "2024-11-05");
                    ObjectNode capabilities = MAPPER.createObjectNode();
                    ObjectNode tools = MAPPER.createObjectNode();
                    tools.put("listChanged", false);
                    capabilities.set("tools", tools);
                    result.set("capabilities", capabilities);
                    ObjectNode serverInfo = MAPPER.createObjectNode();
                    serverInfo.put("name", "token-refresh-test-server");
                    serverInfo.put("version", "1.0.0");
                    result.set("serverInfo", serverInfo);
                    response.set("result", result);
                }
                case "tools/list" -> {
                    ObjectNode result = MAPPER.createObjectNode();
                    ArrayNode toolsArray = MAPPER.createArrayNode();
                    ObjectNode echoTool = MAPPER.createObjectNode();
                    echoTool.put("name", "echo");
                    echoTool.put("description", "Echoes the message");
                    ObjectNode inputSchema = MAPPER.createObjectNode();
                    inputSchema.put("type", "object");
                    echoTool.set("inputSchema", inputSchema);
                    toolsArray.add(echoTool);
                    result.set("tools", toolsArray);
                    response.set("result", result);
                }
                default -> {
                    ObjectNode error = MAPPER.createObjectNode();
                    error.put("code", -32601);
                    error.put("message", "Method not found: " + method);
                    response.set("error", error);
                }
            }

            byte[] json = MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json);
            }
        } finally {
            exchange.close();
        }
    }
}
