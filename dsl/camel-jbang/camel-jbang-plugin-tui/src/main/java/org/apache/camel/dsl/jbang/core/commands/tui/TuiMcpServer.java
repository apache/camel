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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.export.ExportRequest;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Embedded MCP (Model Context Protocol) server for the Camel TUI.
 * <p>
 * Implements the Streamable HTTP transport (spec 2025-03-26) using JDK's built-in HttpServer. Exposes tools that let AI
 * agents observe the live TUI session: screen content, key events, and navigation state.
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

    record LogEntry(String timestamp, LogLevel level, String message) {
    }

    private final int port;
    private final CamelMonitor monitor;
    private HttpServer server;
    private volatile String clientName;
    private volatile long lastActivity;
    private volatile long lastToolCallTime;
    private final List<LogEntry> activityLog = new ArrayList<>();

    TuiMcpServer(int port, CamelMonitor monitor) {
        this.port = port;
        this.monitor = monitor;
    }

    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/mcp", this::handleMcp);
        server.start();
        log(LogLevel.INFO, "Server started on port " + port);
    }

    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    synchronized List<LogEntry> getActivityLog() {
        return new ArrayList<>(activityLog);
    }

    private synchronized void log(LogLevel level, String message) {
        activityLog.add(new LogEntry(TIME_FMT.format(Instant.now()), level, message));
        if (activityLog.size() > MAX_LOG_ENTRIES) {
            activityLog.remove(0);
        }
    }

    boolean isRecentActivity() {
        return System.currentTimeMillis() - lastToolCallTime < 2000;
    }

    String getConnectedClient() {
        if (clientName != null && System.currentTimeMillis() - lastActivity < CLIENT_TIMEOUT_MS) {
            return clientName;
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
                sendResult(exchange, request, result);
            }
        } catch (Exception e) {
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
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
        log(LogLevel.CONNECT, "Client connected: " + (clientName != null ? clientName : "unknown"));

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
        toolList.add(toolDef(
                "tui_get_screen",
                "Returns the current TUI screen content as text. "
                                  + "Shows exactly what the user sees in their terminal. "
                                  + "Use ansi=true to include ANSI color codes for color-related questions.",
                Map.of("ansi", propDef("boolean", "Include ANSI color codes in the output (default false)"))));
        toolList.add(toolDef(
                "tui_get_events",
                "Returns recent user input events (key presses, navigation). "
                                  + "Each event has a key, human-readable label, and timestamp.",
                Map.of("limit", propDef("integer", "Maximum number of events to return (default 50)"))));
        toolList.add(toolDef(
                "tui_get_state",
                "Returns the current TUI navigation state: active tab, selected integration, "
                                 + "and integration count.",
                Map.of()));

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
        log(LogLevel.TOOL, "Tool call: " + toolName);

        String text;
        boolean isError = false;
        try {
            text = switch (toolName) {
                case "tui_get_screen" -> callGetScreen(args);
                case "tui_get_events" -> callGetEvents(args);
                case "tui_get_state" -> callGetState();
                default -> {
                    isError = true;
                    yield "Unknown tool: " + toolName;
                }
            };
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

    private String callGetScreen(Map<String, Object> args) {
        Buffer buf = monitor.getLastBuffer();
        if (buf == null) {
            return "Screen not yet available";
        }
        boolean ansi = Boolean.TRUE.equals(args.get("ansi"));
        String screen = ansi
                ? ExportRequest.export(buf).text().options(o -> o.styles(true)).toString()
                : ExportRequest.export(buf).text().toString();

        JsonObject result = new JsonObject();
        result.put("screen", screen);
        result.put("width", buf.area().width());
        result.put("height", buf.area().height());
        return Jsoner.serialize(result);
    }

    private String callGetEvents(Map<String, Object> args) {
        int limit = 50;
        Object limitArg = args.get("limit");
        if (limitArg instanceof Number n) {
            limit = n.intValue();
        }

        TuiEventLog eventLog = monitor.getEventLog();
        List<TuiEventLog.Event> events = eventLog.getRecent(limit);

        JsonArray eventsArray = new JsonArray();
        for (TuiEventLog.Event event : events) {
            JsonObject obj = new JsonObject();
            obj.put("key", event.key());
            obj.put("label", event.label());
            obj.put("timestamp", event.timestamp().toString());
            eventsArray.add(obj);
        }

        JsonObject result = new JsonObject();
        result.put("events", eventsArray);
        result.put("count", events.size());
        return Jsoner.serialize(result);
    }

    private String callGetState() {
        JsonObject result = new JsonObject();
        result.put("activeTab", monitor.getActiveTabName());
        result.put("tabIndex", monitor.getActiveTabIndex());

        String pid = monitor.getSelectedPid();
        if (pid != null) {
            result.put("selectedPid", pid);
        }
        String name = monitor.getSelectedIntegrationName();
        if (name != null) {
            result.put("selectedIntegration", name);
        }
        result.put("integrationCount", monitor.getIntegrationCount());
        return Jsoner.serialize(result);
    }

    // --- JSON-RPC helpers ---

    private void sendResult(HttpExchange exchange, JsonObject request, JsonObject result) throws IOException {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", "2.0");
        response.put("id", request.get("id"));
        response.put("result", result);
        sendJson(exchange, 200, response);
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

    private void sendJson(HttpExchange exchange, int status, JsonObject json) throws IOException {
        byte[] bytes = Jsoner.serialize(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // --- Tool definition helpers ---

    private JsonObject toolDef(String name, String description, Map<String, JsonObject> properties) {
        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        if (!properties.isEmpty()) {
            JsonObject props = new JsonObject();
            props.putAll(properties);
            schema.put("properties", props);
        }

        JsonObject tool = new JsonObject();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", schema);
        return tool;
    }

    private JsonObject propDef(String type, String description) {
        JsonObject prop = new JsonObject();
        prop.put("type", type);
        prop.put("description", description);
        return prop;
    }
}
