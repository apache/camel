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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Embedded MCP (Model Context Protocol) server for the Camel TUI.
 * <p>
 * Implements the Streamable HTTP transport (spec 2025-03-26) using JDK's built-in HttpServer. Exposes tools that let AI
 * agents observe the live TUI session: screen content, key events, and navigation state.
 * <p>
 * Tool definitions and execution logic are delegated to {@link TuiToolRegistry}.
 * <p>
 * Binds to 127.0.0.1 only for security.
 */
class TuiMcpServer {

    private static final String SERVER_NAME = "camel-tui-mcp";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final long CLIENT_TIMEOUT_MS = 60_000;

    private static final int MAX_LOG_ENTRIES = 200;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    enum LogLevel {
        INFO,
        CONNECT,
        TOOL,
        ERROR
    }

    record LogEntry(String timestamp, LogLevel level, String message, String requestBody, String responseBody) {
    }

    private final int port;
    private final McpFacade facade;
    private final TuiToolRegistry toolRegistry;
    private HttpServer server;
    private volatile String clientName;
    private volatile long lastActivity;
    private volatile long lastToolCallTime;
    private final AtomicInteger toolCallCount = new AtomicInteger();
    private final List<LogEntry> activityLog = new ArrayList<>();

    TuiMcpServer(int port, McpFacade facade) {
        this.port = port;
        this.facade = facade;
        this.toolRegistry = new TuiToolRegistry(facade);
    }

    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/mcp", this::handleMcp);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-handler");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        log(LogLevel.INFO, "Server started on port " + port);
    }

    void stop() {
        if (server != null) {
            server.stop(1);
            if (server.getExecutor() instanceof java.util.concurrent.ExecutorService es) {
                es.shutdownNow();
            }
        }
    }

    synchronized List<LogEntry> getActivityLog() {
        return new ArrayList<>(activityLog);
    }

    private synchronized void log(LogLevel level, String message) {
        log(level, message, null, null);
    }

    private synchronized void log(LogLevel level, String message, String requestBody, String responseBody) {
        activityLog.add(new LogEntry(TIME_FMT.format(Instant.now()), level, message, requestBody, responseBody));
        if (activityLog.size() > MAX_LOG_ENTRIES) {
            activityLog.remove(0);
        }
    }

    boolean isRecentActivity() {
        return System.currentTimeMillis() - lastToolCallTime < 2000;
    }

    int getToolCallCount() {
        return toolCallCount.get();
    }

    String getConnectedClient() {
        if (System.currentTimeMillis() - lastActivity < CLIENT_TIMEOUT_MS) {
            return clientName != null ? clientName : "connected";
        }
        return null;
    }

    private void handleMcp(HttpExchange exchange) throws IOException {
        try {
            lastActivity = System.currentTimeMillis();
            String method = exchange.getRequestMethod();
            if (!"POST".equals(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("application/json")) {
                exchange.sendResponseHeaders(415, -1);
                return;
            }

            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonObject request = Jsoner.deserialize(body, new JsonObject());
            String jsonrpcMethod = (String) request.get("method");

            if (jsonrpcMethod == null) {
                sendError(exchange, request, -32600, "Invalid Request");
                return;
            }

            // Notifications have no "id" — respond with 202
            if (!request.containsKey("id")) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }

            JsonObject result = switch (jsonrpcMethod) {
                case "initialize" -> handleInitialize(request);
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(request);
                case "ping" -> new JsonObject();
                default -> null;
            };

            if (result == null) {
                sendError(exchange, request, -32601, "Method not found: " + jsonrpcMethod);
            } else {
                String responseJson = sendResult(exchange, request, result);
                logMethodCall(jsonrpcMethod, request, body, responseJson);
            }
        } catch (Exception e) {
            try {
                JsonObject error = new JsonObject();
                error.put("code", -32603);
                error.put("message", "Internal error");
                JsonObject response = new JsonObject();
                response.put("jsonrpc", "2.0");
                response.put("id", null);
                response.put("error", error);
                sendJson(exchange, 200, response);
            } catch (Exception ignored) {
                // headers already sent
            }
        } finally {
            exchange.close();
        }
    }

    private void logMethodCall(String jsonrpcMethod, JsonObject request, String requestBody, String responseBody) {
        switch (jsonrpcMethod) {
            case "initialize" -> {
                JsonObject params = (JsonObject) request.get("params");
                String name = "unknown";
                if (params != null) {
                    JsonObject clientInfo = (JsonObject) params.get("clientInfo");
                    if (clientInfo != null && clientInfo.get("name") != null) {
                        name = (String) clientInfo.get("name");
                    }
                }
                log(LogLevel.CONNECT, "Client connected: " + name, requestBody, responseBody);
            }
            case "tools/call" -> {
                JsonObject params = (JsonObject) request.get("params");
                String toolName = params != null ? (String) params.get("name") : "unknown";
                log(LogLevel.TOOL, "Tool call: " + toolName, requestBody, responseBody);
            }
            case "tools/list" -> log(LogLevel.INFO, "Tools listed", requestBody, responseBody);
            case "ping" -> log(LogLevel.INFO, "Ping", requestBody, responseBody);
            default -> log(LogLevel.INFO, jsonrpcMethod, requestBody, responseBody);
        }
    }

    private JsonObject handleInitialize(JsonObject request) {
        JsonObject params = (JsonObject) request.get("params");
        if (params != null) {
            JsonObject clientInfo = (JsonObject) params.get("clientInfo");
            if (clientInfo != null) {
                clientName = (String) clientInfo.get("name");
            }
        }

        JsonObject result = new JsonObject();
        result.put("protocolVersion", PROTOCOL_VERSION);

        JsonObject serverInfo = new JsonObject();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        tools.put("listChanged", false);
        capabilities.put("tools", tools);
        result.put("capabilities", capabilities);

        return result;
    }

    private JsonObject handleToolsList() {
        JsonArray toolList = new JsonArray();
        for (TuiToolRegistry.ToolDef td : toolRegistry.getToolDefinitions()) {
            JsonObject tool = new JsonObject();
            tool.put("name", td.name());
            tool.put("description", td.description());
            tool.put("inputSchema", td.inputSchema());
            toolList.add(tool);
        }
        JsonObject result = new JsonObject();
        result.put("tools", toolList);
        return result;
    }

    @SuppressWarnings("unchecked")
    private JsonObject handleToolsCall(JsonObject request) {
        JsonObject params = (JsonObject) request.get("params");
        String toolName = params != null ? (String) params.get("name") : null;
        Map<String, Object> args = params != null ? (Map<String, Object>) params.get("arguments") : Map.of();
        if (args == null) {
            args = Map.of();
        }

        lastToolCallTime = System.currentTimeMillis();
        toolCallCount.incrementAndGet();

        String text;
        boolean isError = false;
        try {
            text = toolRegistry.execute(toolName, args);
        } catch (IllegalArgumentException e) {
            text = "Unknown tool: " + toolName;
            isError = true;
            log(LogLevel.ERROR, "Unknown tool: " + toolName);
        } catch (Exception e) {
            text = "Error: " + e.getMessage();
            isError = true;
            log(LogLevel.ERROR, "Tool error: " + toolName + " - " + e.getMessage());
        }

        JsonObject content = new JsonObject();
        content.put("type", "text");
        content.put("text", text);

        JsonArray contentArray = new JsonArray();
        contentArray.add(content);

        JsonObject result = new JsonObject();
        result.put("content", contentArray);
        if (isError) {
            result.put("isError", true);
        }
        return result;
    }

    // --- JSON-RPC helpers ---

    private String sendResult(HttpExchange exchange, JsonObject request, JsonObject result) throws IOException {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", "2.0");
        response.put("id", request.get("id"));
        response.put("result", result);
        return sendJson(exchange, 200, response);
    }

    private void sendError(HttpExchange exchange, JsonObject request, int code, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.put("code", code);
        error.put("message", message);

        JsonObject response = new JsonObject();
        response.put("jsonrpc", "2.0");
        response.put("id", request.get("id"));
        response.put("error", error);
        sendJson(exchange, 200, response);
    }

    private String sendJson(HttpExchange exchange, int status, JsonObject json) throws IOException {
        String serialized = Jsoner.serialize(json);
        byte[] bytes = serialized.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        return serialized;
    }

}
