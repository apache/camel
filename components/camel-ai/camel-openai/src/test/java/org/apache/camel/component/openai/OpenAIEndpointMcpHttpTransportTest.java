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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Connects the {@code streamableHttp} and {@code sse} transports of the {@code mcpServer} option to a real MCP server:
 * the stateless server of the MCP SDK, served over Streamable HTTP and over HTTP+SSE by the HTTP server of the JDK.
 */
class OpenAIEndpointMcpHttpTransportTest {

    private static final Set<String> STREAMABLE_HTTP_PATHS = Set.of("/mcp", "/api/mcp");
    private static final Set<String> SSE_PATHS = Set.of("/sse", "/custom/sse");
    private static final List<RecordedRequest> REQUESTS = new CopyOnWriteArrayList<>();

    private static ExecutorService executor;
    private static HttpServer httpServer;
    private static JdkHttpMcpServerTransport transport;
    private static McpStatelessSyncServer mcpServer;
    private static String serverUrl;

    private record RecordedRequest(String httpMethod, String path, String query) {
    }

    @BeforeAll
    static void startMcpServer() throws Exception {
        transport = new JdkHttpMcpServerTransport();
        McpSchema.Tool echo = McpSchema.Tool.builder("echo",
                Map.of("type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", List.of("message")))
                .description("Echoes the message")
                .build();
        mcpServer = McpServer.sync(transport)
                .serverInfo("http-transport-test-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(McpStatelessServerFeatures.SyncToolSpecification.builder()
                        .tool(echo)
                        .callHandler((context, request) -> McpSchema.CallToolResult.builder(List.of(
                                McpSchema.TextContent.builder("echo: " + request.arguments().get("message")).build()))
                                .build())
                        .build())
                .build();

        InetAddress loopback = InetAddress.getLoopbackAddress();
        // an SSE stream holds its thread until the test ends it, so the pool must not be bounded
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

    @BeforeEach
    void resetRequests() {
        REQUESTS.clear();
    }

    @AfterEach
    void endSseStreams() {
        transport.endSseStreams();
    }

    /**
     * The client sends its requests to the endpoint at the path and query of the URL, and to the default endpoint of
     * the transport when the URL has no path: the POST requests of Streamable HTTP, or the GET request that opens the
     * SSE stream. A path that starts with // stays a path on the server of the URL: mcp.invalid is not a host.
     */
    @ParameterizedTest
    @CsvSource({
            "streamableHttp, /api/mcp?tenant=acme,    POST, /api/mcp,    tenant=acme",
            "streamableHttp, '',                      POST, /mcp,",
            "sse,            /custom/sse?tenant=acme, GET,  /custom/sse, tenant=acme",
            "sse,            /,                       GET,  /sse,",
            "streamableHttp, //mcp.invalid/api/mcp,   POST, //mcp.invalid/api/mcp," })
    void connectsToTheEndpointAtThePathOfTheUrl(
            String transportType, String urlPath, String httpMethod, String expectedPath, String expectedQuery)
            throws Exception {
        OpenAIEndpoint endpoint = createEndpoint();
        McpSyncClient client = endpoint.createMcpClient("local",
                Map.of("transportType", transportType, "url", serverUrl + urlPath));
        try {
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("echo");
        } finally {
            client.closeGracefully();
        }

        assertThat(REQUESTS).filteredOn(request -> httpMethod.equals(request.httpMethod()))
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(request.path()).isEqualTo(expectedPath);
                    assertThat(request.query()).isEqualTo(expectedQuery);
                });
    }

    @Test
    void urlMustBeAbsolute() {
        OpenAIEndpoint endpoint = createEndpoint();

        assertThatThrownBy(() -> endpoint.createMcpClient("local", Map.of("transportType", "sse", "url", "mcp-server/sse")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mcpServer.local.url must be an absolute URL, such as http://localhost:8080/sse");
    }

    private static OpenAIEndpoint createEndpoint() {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        component.setCamelContext(camelContext);
        OpenAIConfiguration configuration = new OpenAIConfiguration();
        configuration.setMcpTimeout(10);
        OpenAIEndpoint endpoint = new OpenAIEndpoint("openai:chat-completion", component, configuration);
        endpoint.setCamelContext(camelContext);
        return endpoint;
    }

    /**
     * Serves the stateless MCP server with the HTTP server of the JDK, over two transports:
     * <ul>
     * <li>Streamable HTTP, as the {@code HttpServletStatelessServerTransport} of the SDK does in a servlet container: a
     * POST carries one JSON-RPC message, a request is answered with a JSON response and a notification with 202
     * Accepted, and the GET that opens the optional SSE stream is refused with 405 Method Not Allowed.</li>
     * <li>HTTP+SSE: a GET opens the SSE stream, whose first event names the message endpoint next to the SSE endpoint,
     * for example {@code /custom/message} for {@code /custom/sse}. A POST to the message endpoint carries one JSON-RPC
     * message, is answered with 202 Accepted, and the response to a request is sent on the SSE stream.</li>
     * </ul>
     */
    private static final class JdkHttpMcpServerTransport implements McpStatelessServerTransport, HttpHandler {

        private static final String END_OF_STREAM = "";

        private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
        private final Map<String, SseSession> sseSessions = new ConcurrentHashMap<>();
        private volatile McpStatelessServerHandler mcpHandler;

        private record SseSession(String messagePath, BlockingQueue<String> events) {
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
            // the sse transport of the client only supports the first version of the protocol
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
                URI requestUri = exchange.getRequestURI();
                String path = requestUri.getPath();
                String query = requestUri.getRawQuery();
                // the request URI reads a path that starts with // as an authority and a path: record it as it was sent
                String sentPath = requestUri.getRawAuthority() != null
                        ? "//" + requestUri.getRawAuthority() + requestUri.getRawPath() : requestUri.getRawPath();
                REQUESTS.add(new RecordedRequest(httpMethod, sentPath, query));
                SseSession sseSession = query != null && query.startsWith("sessionId=")
                        ? sseSessions.get(query.substring("sessionId=".length())) : null;

                if ("GET".equals(httpMethod) && SSE_PATHS.contains(path)) {
                    streamSseEvents(exchange, path);
                } else if ("POST".equals(httpMethod) && STREAMABLE_HTTP_PATHS.contains(path)) {
                    String response = handleMessage(exchange);
                    if (response == null) {
                        exchange.sendResponseHeaders(202, -1);
                    } else {
                        byte[] json = response.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, json.length);
                        exchange.getResponseBody().write(json);
                    }
                } else if ("POST".equals(httpMethod) && sseSession != null && sseSession.messagePath().equals(path)) {
                    String response = handleMessage(exchange);
                    if (response != null) {
                        sseSession.events().add(response);
                    }
                    exchange.sendResponseHeaders(202, -1);
                } else {
                    boolean served = STREAMABLE_HTTP_PATHS.contains(path) || SSE_PATHS.contains(path);
                    exchange.sendResponseHeaders(served ? 405 : 404, -1);
                }
            } finally {
                exchange.close();
            }
        }

        /**
         * Handles the JSON-RPC message in the body of the request, and returns the JSON response to a request, or null
         * for a notification.
         */
        private String handleMessage(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body);
            if (message instanceof McpSchema.JSONRPCRequest request) {
                return jsonMapper.writeValueAsString(mcpHandler.handleRequest(McpTransportContext.EMPTY, request).block());
            }
            if (message instanceof McpSchema.JSONRPCNotification notification) {
                mcpHandler.handleNotification(McpTransportContext.EMPTY, notification).block();
            }
            return null;
        }

        private void streamSseEvents(HttpExchange exchange, String ssePath) throws IOException {
            String sessionId = UUID.randomUUID().toString();
            SseSession session = new SseSession(
                    ssePath.substring(0, ssePath.lastIndexOf('/')) + "/message",
                    new LinkedBlockingQueue<>());
            sseSessions.put(sessionId, session);
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                OutputStream body = exchange.getResponseBody();
                writeEvent(body, "endpoint", session.messagePath() + "?sessionId=" + sessionId);
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
