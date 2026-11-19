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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.springai.chat.mcp.SpringAiChatMcpManager;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Connects the {@code streamableHttp} transport of the {@code mcpServer} option to a real MCP server: the stateless
 * server of the MCP SDK, served over Streamable HTTP by the HTTP server of the JDK.
 */
class SpringAiChatMcpStreamableHttpTest extends CamelTestSupport {

    private static final Set<String> SERVED_PATHS = Set.of("/mcp", "/api/mcp");
    private static final List<RecordedRequest> REQUESTS = new CopyOnWriteArrayList<>();

    private static ExecutorService executor;
    private static HttpServer httpServer;
    private static McpStatelessSyncServer mcpServer;
    private static String serverUrl;

    private final List<String> offeredTools = new CopyOnWriteArrayList<>();
    private ChatModel chatModel;

    private record RecordedRequest(String httpMethod, String path, String query, String authorization, String rpcMethod) {
    }

    @BeforeAll
    static void startMcpServer() throws IOException {
        JdkHttpStatelessServerTransport transport = new JdkHttpStatelessServerTransport();
        McpSchema.Tool echo = McpSchema.Tool.builder("echo",
                Map.of("type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", List.of("message")))
                .description("Echoes the message")
                .build();
        mcpServer = McpServer.sync(transport)
                .serverInfo("streamable-http-test-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(McpStatelessServerFeatures.SyncToolSpecification.builder()
                        .tool(echo)
                        .callHandler((context, request) -> McpSchema.CallToolResult.builder(List.of(
                                McpSchema.TextContent.builder("echo: " + request.arguments().get("message")).build()))
                                .build())
                        .build())
                .build();

        InetAddress loopback = InetAddress.getLoopbackAddress();
        executor = Executors.newFixedThreadPool(4);
        httpServer = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        httpServer.createContext("/", transport);
        httpServer.setExecutor(executor);
        httpServer.start();
        serverUrl = "http://" + loopback.getHostAddress() + ":" + httpServer.getAddress().getPort();
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

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        // Like the chat models of Spring AI, the stub runs the tool call it asks for with a ToolCallingManager, then
        // answers with the result of the tool
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            offeredTools.addAll(toolNames(prompt));
            AssistantMessage toolCall = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            "call-1", "function", "echo", "{\"message\":\"hello\"}")))
                    .build();
            ToolExecutionResult result
                    = toolCallingManager.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(toolCall))));
            List<Message> history = result.conversationHistory();
            ToolResponseMessage toolResponse = (ToolResponseMessage) history.get(history.size() - 1);
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage(toolResponse.getResponses().get(0).responseData()))));
        });
    }

    @Test
    void chatCallsAToolOfTheServerAtThePathOfTheUrl() throws Exception {
        context.getRegistry().bind("chatModel", chatModel);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("spring-ai-chat:chat?chatModel=#chatModel&mcpTimeout=10"
                            + "&mcpServer.local.transportType=streamableHttp"
                            + "&mcpServer.local.url=" + serverUrl + "/api/mcp"
                            + "&mcpServer.local.oauthProfile=local");
            }
        });

        String response = template.requestBody("direct:chat", "Echo hello", String.class);

        assertThat(response).contains("echo: hello");
        assertThat(offeredTools).contains("echo");
        assertThat(rpcMethods()).contains("initialize", "notifications/initialized", "tools/list", "tools/call");
        assertThat(postRequests()).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/api/mcp");
            assertThat(request.authorization())
                    .isEqualTo("Bearer " + StubOAuthClientAuthenticationFactory.tokenFor("local"));
        });
    }

    @Test
    void urlWithoutPathUsesMcp() throws Exception {
        SpringAiChatMcpManager manager = new SpringAiChatMcpManager();
        try {
            manager.initialize(Map.<String, Object> of("local.transportType", "streamableHttp", "local.url", serverUrl), 10,
                    context);

            assertThat(callbackNames(manager)).containsExactly("echo");
            assertThat(postRequests()).isNotEmpty().allSatisfy(request -> {
                assertThat(request.path()).isEqualTo("/mcp");
                assertThat(request.authorization()).isNull();
            });
        } finally {
            manager.close();
        }
    }

    @Test
    void urlKeepsItsQuery() throws Exception {
        SpringAiChatMcpManager manager = new SpringAiChatMcpManager();
        try {
            manager.initialize(Map.<String, Object> of("local.transportType", "streamableHttp",
                    "local.url", serverUrl + "/api/mcp?tenant=acme"), 10, context);

            assertThat(callbackNames(manager)).containsExactly("echo");
            assertThat(postRequests()).isNotEmpty().allSatisfy(request -> {
                assertThat(request.path()).isEqualTo("/api/mcp");
                assertThat(request.query()).isEqualTo("tenant=acme");
            });
        } finally {
            manager.close();
        }
    }

    @Test
    void streamableHttpRequiresAnAbsoluteUrl() {
        SpringAiChatMcpManager manager = new SpringAiChatMcpManager();

        assertThatThrownBy(
                () -> manager.initialize(Map.<String, Object> of("local.transportType", "streamableHttp"), 10, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mcpServer.local.url is required for streamableHttp transport");
        assertThatThrownBy(() -> manager.initialize(
                Map.<String, Object> of("local.transportType", "streamableHttp", "local.url", "mcp-server/mcp"), 10,
                context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mcpServer.local.url must be an absolute URL");
    }

    @Test
    void unknownTransportListsTheSupportedOnes() {
        SpringAiChatMcpManager manager = new SpringAiChatMcpManager();

        assertThatThrownBy(() -> manager.initialize(Map.<String, Object> of("local.transportType", "websocket"), 10, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("Supported: streamableHttp, stdio, sse");
    }

    private static List<String> rpcMethods() {
        return REQUESTS.stream().map(RecordedRequest::rpcMethod).filter(Objects::nonNull).toList();
    }

    /**
     * The POST requests, which carry the JSON-RPC messages and complete before the call that sent them returns. The GET
     * request that opens the optional SSE stream is sent in the background.
     */
    private static List<RecordedRequest> postRequests() {
        return REQUESTS.stream().filter(request -> "POST".equals(request.httpMethod())).toList();
    }

    private static List<String> callbackNames(SpringAiChatMcpManager manager) {
        List<String> names = new ArrayList<>();
        for (ToolCallback callback : manager.getToolCallbackProvider().getToolCallbacks()) {
            names.add(callback.getToolDefinition().name());
        }
        return names;
    }

    private static List<String> toolNames(Prompt prompt) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions options) || options.getToolCallbacks() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        options.getToolCallbacks().forEach(callback -> names.add(callback.getToolDefinition().name()));
        return names;
    }

    /**
     * Serves the stateless Streamable HTTP transport of the MCP SDK with the HTTP server of the JDK, as the
     * {@code HttpServletStatelessServerTransport} of the SDK does in a servlet container: a POST carries one JSON-RPC
     * message, a request is answered with a JSON response and a notification with 202 Accepted, and the GET that opens
     * the optional SSE stream is refused with 405 Method Not Allowed.
     */
    private static final class JdkHttpStatelessServerTransport implements McpStatelessServerTransport, HttpHandler {

        private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
        private volatile McpStatelessServerHandler mcpHandler;

        @Override
        public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
            this.mcpHandler = mcpHandler;
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String httpMethod = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getRawQuery();
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (!SERVED_PATHS.contains(path) || !"POST".equals(httpMethod)) {
                    REQUESTS.add(new RecordedRequest(httpMethod, path, query, authorization, null));
                    exchange.sendResponseHeaders(SERVED_PATHS.contains(path) ? 405 : 404, -1);
                    return;
                }

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body);
                if (message instanceof McpSchema.JSONRPCRequest request) {
                    REQUESTS.add(new RecordedRequest(httpMethod, path, query, authorization, request.method()));
                    McpSchema.JSONRPCResponse response
                            = mcpHandler.handleRequest(McpTransportContext.EMPTY, request).block();
                    byte[] json = jsonMapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, json.length);
                    exchange.getResponseBody().write(json);
                } else if (message instanceof McpSchema.JSONRPCNotification notification) {
                    REQUESTS.add(new RecordedRequest(httpMethod, path, query, authorization, notification.method()));
                    mcpHandler.handleNotification(McpTransportContext.EMPTY, notification).block();
                    exchange.sendResponseHeaders(202, -1);
                } else {
                    REQUESTS.add(new RecordedRequest(httpMethod, path, query, authorization, null));
                    exchange.sendResponseHeaders(400, -1);
                }
            } finally {
                exchange.close();
            }
        }
    }
}
