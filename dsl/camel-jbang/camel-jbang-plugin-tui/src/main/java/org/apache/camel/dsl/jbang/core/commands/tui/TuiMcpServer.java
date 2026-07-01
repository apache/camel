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
import dev.tamboui.style.Color;
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

    record LogEntry(String timestamp, LogLevel level, String message, String requestBody, String responseBody) {
    }

    private final int port;
    private final McpFacade facade;
    private HttpServer server;
    private volatile String clientName;
    private volatile long lastActivity;
    private volatile long lastToolCallTime;
    private volatile int toolCallCount;
    private final List<LogEntry> activityLog = new ArrayList<>();

    TuiMcpServer(int port, McpFacade facade) {
        this.port = port;
        this.facade = facade;
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
        return toolCallCount;
    }

    String getConnectedClient() {
        if (System.currentTimeMillis() - lastActivity < CLIENT_TIMEOUT_MS) {
            return clientName != null ? clientName : "unknown";
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
                String responseJson = sendResult(exchange, request, result);
                logMethodCall(jsonrpcMethod, request, body, responseJson);
            }
        } catch (Exception e) {
            exchange.sendResponseHeaders(500, -1);
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
        toolList.add(toolDef(
                "tui_get_screen",
                "Returns the current TUI screen content as text. "
                                  + "Shows exactly what the user sees in their terminal. "
                                  + "Use ansi=true to include ANSI color codes for color-related questions. "
                                  + "Also returns a 'selection' field with structured metadata about the active list/table "
                                  + "(type, items, selectedIndex, totalItems, label) when available.",
                Map.of("ansi", propDef("boolean", "Include ANSI color codes in the output (default false)"))));
        toolList.add(toolDef(
                "tui_get_events",
                "Returns recent user input events (key presses, navigation). "
                                  + "Each event has a key, human-readable label, and timestamp.",
                Map.of("limit", propDef("integer", "Maximum number of events to return (default 50)"))));
        toolList.add(toolDef(
                "tui_get_state",
                "Returns the current TUI navigation state: active tab, selected integration, "
                                 + "and integration count. "
                                 + "Includes a 'selection' field with structured metadata about the active list/table. "
                                 + "captionVisible indicates if a caption overlay is on screen. "
                                 + "keystrokesVisible indicates if the keystroke overlay is on; toggle with Ctrl+K.",
                Map.of()));
        toolList.add(toolDef(
                "tui_show_caption",
                "Shows a caption message on the TUI screen with a typewriter animation. "
                                    + "Use this to display messages to the user. "
                                    + "Supports \\n for newlines.",
                Map.of("text", propDef("string", "The caption text to display"),
                        "duration", propDef("integer",
                                "Auto-dismiss after this many seconds. Caption won't block key events. "
                                                       + "If omitted, caption stays until dismissed by a key press.")),
                List.of("text")));
        toolList.add(toolDef(
                "tui_navigate",
                "Navigates the TUI: switch tabs and/or select an integration. "
                                + "All parameters are optional — set whichever you want to change. "
                                + "Tab names: Overview, Log, Diagram, Routes, Endpoints, HTTP, Health, Inspect, "
                                + "Circuit Breaker, Spans, Process. "
                                + "Use 'route' to select a route in the Diagram topology, "
                                + "and 'node' to drill down into a route and select a specific processor/EIP node. "
                                + "Returns screen content and selection metadata after navigating.",
                Map.of("tab", propDef("string", "Tab to switch to (e.g. 'Routes', 'Health', 'Diagram')"),
                        "integration", propDef("string", "Integration name or PID to select"),
                        "route", propDef("string",
                                "Route ID to select in the Diagram tab topology (e.g. 'order-dispatcher')"),
                        "node", propDef("string",
                                "Processor/EIP node ID to select within a drilled-down route (e.g. 'multicast1'). "
                                                  + "If 'route' is also provided, drills into that route first"))));

        toolList.add(toolDef(
                "tui_send_keys",
                "Sends key presses to the TUI. A human is watching the screen, "
                                 + "so keys should be paced naturally like a skilled user would type. "
                                 + "Key names: Enter, Esc, Tab, Backspace, Delete, Up, Down, Left, Right, "
                                 + "Home, End, PgUp, PgDn, Space, F1-F12, or any single character. "
                                 + "Modifiers: Ctrl+x, Shift+x, Ctrl+Shift+x.",
                Map.of("keys", propDef("array", "Array of key name strings to send"),
                        "delay", propDef("integer",
                                "Delay in milliseconds between keys (default 150, minimum 80)"),
                        "wait", propDef("boolean",
                                "Wait for all keys to be processed and return the resulting screen "
                                                   + "with selection metadata (default false)")),
                List.of("keys")));
        toolList.add(toolDef(
                "tui_get_options",
                "Returns all available tabs and integrations. "
                                   + "Use this to discover what tabs exist and which integrations are running "
                                   + "before navigating. Also returns the current selection.",
                Map.of()));
        toolList.add(toolDef(
                "tui_wait_for_idle",
                "Waits for the TUI to render new frames after an action. "
                                     + "Blocks until the specified number of new frames have been rendered, "
                                     + "ensuring the action has been processed. "
                                     + "Returns the screen content with selection metadata after settling. "
                                     + "Use after tui_navigate or tui_send_keys.",
                Map.of("timeout", propDef("integer",
                        "Maximum wait time in milliseconds (default 5000, max 30000)"),
                        "frames", propDef("integer",
                                "Number of new frames to wait for (default 2)"))));
        toolList.add(toolDef(
                "tui_tape_start",
                "Start recording TUI interactions as a .tape file for demo playback. "
                                  + "All subsequent tui_send_keys calls will be captured as tape commands. "
                                  + "Stop recording with tui_tape_stop to get the tape content. "
                                  + "Replay with: camel tui monitor --record=<file>.tape",
                Map.of("title", propDef("string", "Description comment for the tape header"))));
        toolList.add(toolDef(
                "tui_tape_stop",
                "Stop tape recording and return the generated .tape content. "
                                 + "The tape can be replayed with: camel tui monitor --record=<file>.tape",
                Map.of("save", propDef("boolean",
                        "If true, also save the tape to a local file (camel-tui-tape-<timestamp>.tape). Default false."))));
        toolList.add(toolDef(
                "tui_sleep",
                "Pauses for the specified duration. "
                             + "When tape recording is active, inserts a Sleep command into the tape. "
                             + "Use this to pace demos and wait for captions to dismiss.",
                Map.of("seconds", propDef("integer",
                        "Number of seconds to sleep (1-30)")),
                List.of("seconds")));
        toolList.add(toolDef(
                "tui_draw",
                "Draws characters at specific screen coordinates as an overlay on top of the TUI. "
                            + "Use this to highlight areas, annotate the screen for the human, "
                            + "draw shapes, or create fun emoji art. "
                            + "All cells are sent in a single call to avoid chatty networking. "
                            + "Coordinates are 0-based and match the screen grid from tui_get_screen. "
                            + "Characters can be any unicode including emoji. "
                            + "The drawing overlays on top of existing content without modifying it. "
                            + "Use with tui_show_caption to explain what you drew.",
                Map.of("cells", propDef("array",
                        "Array of cell objects to draw. Each cell has: "
                                                 + "x (integer, column), y (integer, row), "
                                                 + "char (string, character to draw), "
                                                 + "fg (string, optional foreground color: red/green/blue/yellow/cyan/magenta/white/gray/black), "
                                                 + "bg (string, optional background color, same values), "
                                                 + "bold (boolean, optional)"),
                        "duration", propDef("integer",
                                "Auto-dismiss drawing after this many seconds. "
                                                       + "If omitted, drawing stays until cleared with tui_draw_clear or replaced by another tui_draw call."),
                        "append", propDef("boolean",
                                "If true, add cells to the existing drawing instead of replacing it. Default false.")),
                List.of("cells")));
        toolList.add(toolDef(
                "tui_draw_clear",
                "Clears the drawing overlay and restores the screen to its normal state. "
                                  + "The underlying content is unchanged since drawing is an overlay.",
                Map.of()));

        toolList.add(toolDef(
                "tui_draw_shape",
                "Draws a predefined shape on the TUI screen overlay. "
                                  + "Much easier than constructing individual cells with tui_draw. "
                                  + "Combine with tui_locate for precise positioning.",
                Map.of("shape", propDef("string",
                        "Shape to draw: box (rectangle border), highlight (background color on existing text like a marker pen), "
                                                  + "underline (horizontal line), arrow-down, arrow-up, arrow-left, arrow-right, "
                                                  + "text (draw text string at position)"),
                        "x", propDef("integer", "X coordinate (column) of the shape origin"),
                        "y", propDef("integer", "Y coordinate (row) of the shape origin"),
                        "width", propDef("integer", "Width of the shape (for box, highlight, underline)"),
                        "height", propDef("integer", "Height of the shape (for box, highlight). Defaults to 1."),
                        "length", propDef("integer", "Length of arrows"),
                        "text", propDef("string", "Text content to draw (for text shape)"),
                        "color", propDef("string",
                                "Color: red, green, blue, yellow, cyan, magenta, white, gray, black. Default: red for box/underline/arrow, yellow for highlight."),
                        "duration",
                        propDef("integer", "Auto-dismiss after this many seconds. If omitted, stays until cleared."),
                        "append", propDef("boolean",
                                "If true, add to existing drawing instead of replacing it. Default false.")),
                List.of("shape", "x", "y")));

        // --- Structured data tools ---

        toolList.add(toolDef(
                "tui_get_table",
                "Returns the currently visible table data as structured JSON with typed values. "
                                 + "Much more reliable than parsing screen text. "
                                 + "Returns tab name, rows array with all fields, totalRows, and selectedIndex.",
                Map.of("tab", propDef("string",
                        "Tab name to get data from (e.g. 'Routes', 'Endpoints', 'Health'). "
                                                + "If omitted, uses the active tab."))));
        toolList.add(toolDef(
                "tui_action",
                "Invokes a TUI action by name, bypassing fragile key sequences. "
                              + "Actions: reset-stats, reset-screen, screenshot, show-keystrokes, "
                              + "tape-recording, doctor, caption, mcp-info, mcp-log.",
                Map.of("action", propDef("string", "Action name in kebab-case (e.g. 'reset-stats', 'screenshot')")),
                List.of("action")));
        toolList.add(toolDef(
                "tui_get_log",
                "Returns recent log lines as structured data with optional filtering. "
                               + "Returns newest entries first.",
                Map.of("limit", propDef("integer", "Maximum lines to return (default 50)"),
                        "filter", propDef("string", "Case-insensitive substring filter on log message"),
                        "level", propDef("string", "Filter by log level (INFO, WARN, ERROR, DEBUG, TRACE)"))));
        toolList.add(toolDef(
                "tui_get_errors",
                "Returns structured error data from the Errors tab. "
                                  + "Includes routeId, exchangeId, exception details, stack trace, body, and headers.",
                Map.of()));
        toolList.add(toolDef(
                "tui_get_diagram",
                "Returns the route topology diagram as text. "
                                   + "Shows the ASCII/Unicode art diagram of routes and their connections.",
                Map.of()));
        toolList.add(toolDef(
                "tui_get_history",
                "Returns rich trace and history data from the History tab as structured JSON. "
                                   + "Includes ALL exchange details: body with type, headers with types, "
                                   + "exchange properties, exchange variables, thread name, source location, "
                                   + "node level, and node labels. Much richer than tui_get_table on History. "
                                   + "Returns different shapes depending on the History tab's current mode: "
                                   + "Traces (exchange ID list), Trace Steps (per-step detail), "
                                   + "or History (message history steps).",
                Map.of("exchangeId", propDef("string",
                        "If provided, returns trace steps for this specific exchange ID. "
                                                       + "Otherwise returns data for the current History tab view."))));
        toolList.add(toolDef(
                "tui_get_topology",
                "Returns the route topology as a structured JSON graph with nodes and edges arrays. "
                                    + "Each node has: routeId, nodeType (route/external-in/external-out/trigger), "
                                    + "layer, description, from (consumer URI), exchangesTotal, exchangesFailed. "
                                    + "Each edge has: from (routeId), to (routeId), endpoint, connectionType, "
                                    + "selfLoop, backEdge. "
                                    + "Use this instead of tui_get_diagram when you need to reason about "
                                    + "route connectivity programmatically.",
                Map.of()));
        toolList.add(toolDef(
                "tui_send_message",
                "Sends a message to a Camel endpoint in the selected integration. "
                                    + "Uses the file-based IPC protocol to deliver the message directly.",
                Map.of("endpoint", propDef("string", "Endpoint URI to send to (e.g. 'direct:myRoute', 'seda:queue')"),
                        "body", propDef("string", "Message body to send"),
                        "headers", propDef("string", "Message headers as key=value pairs separated by newlines")),
                List.of("endpoint")));
        toolList.add(toolDef(
                "tui_execute_sql",
                "Executes a SQL query against a DataSource in the selected integration. "
                                   + "Returns structured JSON with columns, rows, and metadata for SELECT queries, "
                                   + "or an update count for INSERT/UPDATE/DELETE. "
                                   + "Requires dev console to be enabled in the running application.",
                Map.of("query", propDef("string", "The SQL query to execute"),
                        "datasource", propDef("string",
                                "Name of the DataSource bean (auto-detected if only one exists)"),
                        "maxRows", propDef("integer",
                                "Maximum number of rows to return (default 100)"),
                        "queryTimeout", propDef("integer",
                                "Query timeout in seconds (default 30)")),
                List.of("query")));
        toolList.add(toolDef(
                "tui_update_row",
                "Updates a single row in a database table. Use after tui_execute_sql returns "
                                  + "editable=true with tableName and primaryKeys. Builds and executes "
                                  + "an UPDATE statement using PreparedStatement for safety.",
                Map.of("table", propDef("string", "The table name to update"),
                        "primaryKeyValues", propDef("string",
                                "JSON object of primary key column-value pairs, e.g. {\"id\": 1}"),
                        "columnValues", propDef("string",
                                "JSON object of column-value pairs to update, e.g. {\"name\": \"new\"}"),
                        "datasource", propDef("string",
                                "Name of the DataSource bean (auto-detected if only one exists)")),
                List.of("table", "primaryKeyValues", "columnValues")));
        toolList.add(toolDef(
                "tui_set_log_level",
                "Changes the runtime log level of the selected integration. "
                                     + "This sends a command to the running Camel application to change "
                                     + "the root logger level.",
                Map.of("level", propDef("string",
                        "Log level to set: ERROR, WARN, INFO, DEBUG, or TRACE")),
                List.of("level")));
        toolList.add(toolDef(
                "tui_filter",
                "Sets or clears the fuzzy text filter on a tab that supports typing-to-filter. "
                              + "Currently supported on the Classpath tab. "
                              + "Use an empty string to clear the filter.",
                Map.of("filter", propDef("string",
                        "Filter text to apply. Empty string clears the filter."),
                        "tab", propDef("string",
                                "Tab name to filter (e.g. 'Classpath'). If omitted, uses the active tab.")),
                List.of("filter")));
        toolList.add(toolDef(
                "tui_set_input",
                "Sets the value of a text input field on a TUI tab directly, without simulating keystrokes. "
                                 + "The text appears in the TUI input widget so the user can see it. "
                                 + "Supported fields by tab: SQL Query (field='sql'), "
                                 + "HTTP probe (field='path' or 'body'), "
                                 + "Spans (field='filter'), Classpath (field='filter').",
                Map.of("field", propDef("string",
                        "Field name to set: 'sql', 'path', 'body', or 'filter'"),
                        "value", propDef("string",
                                "The text value to set in the input field"),
                        "tab", propDef("string",
                                "Tab name (e.g. 'SQL Query', 'HTTP'). If omitted, uses the active tab.")),
                List.of("field", "value")));
        toolList.add(toolDef(
                "tui_toggle_trace_display",
                "Toggles which sections are visible in the History tab's detail view. "
                                            + "Controls what data is shown when inspecting trace steps or history entries.",
                Map.of("section", propDef("string",
                        "Section to toggle: headers, properties, variables, body, or wrap"),
                        "enabled", propDef("boolean",
                                "If provided, forces the section on (true) or off (false). "
                                                      + "If omitted, toggles the current state.")),
                List.of("section")));
        toolList.add(toolDef(
                "tui_get_readme",
                "Returns the README/documentation content from a running integration. "
                                  + "Useful for understanding what the integration does, its configuration, and usage. "
                                  + "If no name is provided, returns the README for the currently selected integration.",
                Map.of("name", propDef("string",
                        "Integration name. If omitted, uses the currently selected integration."))));
        toolList.add(toolDef(
                "tui_control",
                "Controls the selected integration: stop/start routes, restart, stop, or kill the process. "
                               + "Actions: stop-routes (or pause) — suspend all routes; "
                               + "start-routes (or resume) — resume all routes; "
                               + "restart — gracefully restart the integration; "
                               + "stop — gracefully stop the process; "
                               + "kill — forcefully terminate the process.",
                Map.of("action", propDef("string",
                        "Control action: stop-routes, start-routes, pause, resume, restart, stop, or kill")),
                List.of("action")));
        toolList.add(toolDef(
                "tui_get_files",
                "Returns source files from the selected integration's directory. "
                                 + "Without a file parameter, returns the list of files (name, size, type). "
                                 + "With a file parameter, returns the file's content. "
                                 + "Useful for reading route source code, configuration, and other integration files.",
                Map.of("name", propDef("string",
                        "Integration name. If omitted, uses the currently selected integration."),
                        "file", propDef("string",
                                "Filename to read. If omitted, returns the file list instead."))));
        toolList.add(toolDef(
                "tui_get_spans",
                "Returns raw OpenTelemetry span data as structured JSON from the selected integration. "
                                 + "Each span includes: traceId, spanId, parentSpanId, name, kind, status, "
                                 + "startEpochNanos, endEpochNanos, durationMs, routeId, processorId, and attributes. "
                                 + "Use traceId to filter spans for a specific trace. "
                                 + "The parentSpanId chain shows the span hierarchy for building waterfall views.",
                Map.of("traceId", propDef("string",
                        "Filter to spans matching this trace ID (substring match). "
                                                    + "If omitted, returns all recent spans."),
                        "limit", propDef("integer",
                                "Maximum number of spans to return (default 500)"))));
        toolList.add(toolDef(
                "tui_locate",
                "Locates elements on the TUI screen and returns their exact screen coordinates (x, y, width, height). "
                              + "Use 'text' to find text on screen with proper wide-character handling (emoji, CJK). "
                              + "Use 'node' or 'nodes' to find diagram nodes by ID. "
                              + "Returns coordinates suitable for tui_draw.",
                Map.of("text", propDef("string",
                        "Text to search for on screen. Returns all matches with screen coordinates."),
                        "node", propDef("string",
                                "Single diagram node ID to locate (routeId or nodeId)."),
                        "nodes", propDef("array",
                                "Array of diagram node IDs to locate. Returns individual rects plus combined bounds."))));

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
        toolCallCount++;

        String text;
        boolean isError = false;
        try {
            text = switch (toolName) {
                case "tui_get_screen" -> callGetScreen(args);
                case "tui_get_events" -> callGetEvents(args);
                case "tui_get_state" -> callGetState();
                case "tui_show_caption" -> callShowCaption(args);
                case "tui_navigate" -> callNavigate(args);
                case "tui_send_keys" -> callSendKeys(args);
                case "tui_get_options" -> callGetOptions();
                case "tui_wait_for_idle" -> callWaitForIdle(args);
                case "tui_tape_start" -> callTapeStart(args);
                case "tui_tape_stop" -> callTapeStop(args);
                case "tui_sleep" -> callSleep(args);
                case "tui_draw" -> callDraw(args);
                case "tui_draw_clear" -> callDrawClear();
                case "tui_get_table" -> callGetTable(args);
                case "tui_action" -> callAction(args);
                case "tui_get_log" -> callGetLog(args);
                case "tui_get_errors" -> callGetErrors();
                case "tui_get_diagram" -> callGetDiagram();
                case "tui_get_history" -> callGetHistory(args);
                case "tui_get_topology" -> callGetTopology();
                case "tui_send_message" -> callSendMessage(args);
                case "tui_execute_sql" -> callExecuteSql(args);
                case "tui_update_row" -> callUpdateRow(args);
                case "tui_set_log_level" -> callSetLogLevel(args);
                case "tui_filter" -> callFilter(args);
                case "tui_set_input" -> callSetInput(args);
                case "tui_toggle_trace_display" -> callToggleTraceDisplay(args);
                case "tui_get_readme" -> callGetReadme(args);
                case "tui_control" -> callControl(args);
                case "tui_get_files" -> callGetFiles(args);
                case "tui_get_spans" -> callGetSpans(args);
                case "tui_locate" -> callLocate(args);
                case "tui_draw_shape" -> callDrawShape(args);
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

    private void addSelectionContext(JsonObject result) {
        SelectionContext ctx = facade.getSelectionContext();
        if (ctx != null) {
            JsonObject sel = new JsonObject();
            sel.put("type", ctx.type());
            sel.put("label", ctx.label());
            sel.put("selectedIndex", ctx.selectedIndex());
            sel.put("totalItems", ctx.totalItems());
            JsonArray items = new JsonArray();
            items.addAll(ctx.items());
            sel.put("items", items);
            result.put("selection", sel);
        }
    }

    private void addFooterActions(JsonObject result) {
        JsonArray actions = facade.getFooterActionsAsJson();
        if (actions != null && !actions.isEmpty()) {
            result.put("actions", actions);
        }
    }

    private String callGetScreen(Map<String, Object> args) {
        Buffer buf = facade.getLastBuffer();
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
        addSelectionContext(result);
        return Jsoner.serialize(result);
    }

    private String callGetEvents(Map<String, Object> args) {
        int limit = 50;
        Object limitArg = args.get("limit");
        if (limitArg instanceof Number n) {
            limit = n.intValue();
        }

        TuiEventLog eventLog = facade.getEventLog();
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
        result.put("activeTab", facade.getActiveTabName());
        result.put("tabIndex", facade.getActiveTabIndex());

        String pid = facade.getSelectedPid();
        if (pid != null) {
            result.put("selectedPid", pid);
        }
        String name = facade.getSelectedIntegrationName();
        if (name != null) {
            result.put("selectedIntegration", name);
        }
        result.put("integrationCount", facade.getIntegrationCount());
        result.put("keystrokesVisible", facade.isKeystrokesVisible());
        result.put("captionVisible", facade.isCaptionVisible());
        addSelectionContext(result);
        addFooterActions(result);
        JsonObject diagramState = facade.getDiagramState();
        if (diagramState != null) {
            result.put("diagram", diagramState);
        }
        return Jsoner.serialize(result);
    }

    private String callShowCaption(Map<String, Object> args) {
        String text = (String) args.get("text");
        if (text == null || text.isBlank()) {
            return "Error: text is required";
        }
        Object durationArg = args.get("duration");
        int duration = 0;
        if (durationArg instanceof Number n) {
            duration = n.intValue();
        }

        TapeRecorder recorder = facade.getTapeRecorder();
        if (recorder != null && recorder.isActive()) {
            recorder.resetClock();
            recorder.recordCaption(text, Math.max(duration, 0));
        }

        if (duration > 0) {
            facade.showCaption(text, duration);
            return "Caption displayed (auto-dismiss in " + duration + "s): " + text;
        }
        facade.showCaption(text);
        return "Caption displayed: " + text;
    }

    private String callNavigate(Map<String, Object> args) {
        JsonObject result = new JsonObject();
        String tab = (String) args.get("tab");
        String integration = (String) args.get("integration");
        String route = args.get("route") instanceof String s ? s : null;
        String node = args.get("node") instanceof String s ? s : null;

        if (tab == null && integration == null && route == null && node == null) {
            result.put("error", "Provide at least one of: tab, integration, route, node");
            result.put("availableTabs", toJsonArray(facade.getTabNames()));
            result.put("availableIntegrations", toJsonArray(facade.getIntegrationNames()));
            return Jsoner.serialize(result);
        }

        if (integration != null) {
            String selected = facade.selectIntegration(integration);
            if (selected != null) {
                result.put("selectedIntegration", selected);
            } else {
                result.put("integrationError", "Not found: " + integration);
                result.put("availableIntegrations", toJsonArray(facade.getIntegrationNames()));
            }
        }

        if (tab != null) {
            String switched = facade.navigateToTab(tab);
            if (switched != null) {
                result.put("activeTab", switched);
                TapeRecorder recorder = facade.getTapeRecorder();
                if (recorder != null && recorder.isActive()) {
                    recorder.resetClock();
                    int tabIndex = facade.getTabNames().indexOf(switched);
                    if (tabIndex >= 0 && tabIndex < 9) {
                        recorder.recordKey(String.valueOf(tabIndex + 1));
                    }
                }
            } else {
                result.put("tabError", "Unknown tab: " + tab);
                result.put("availableTabs", toJsonArray(facade.getTabNames()));
            }
        }

        // Diagram route/node navigation (route selection in topology doesn't need render wait)
        if (node == null && route != null) {
            String selected = facade.navigateDiagramToRoute(route);
            if (selected != null) {
                result.put("selectedRoute", route);
            } else {
                result.put("routeError", "Route not found in diagram: " + route);
            }
        }

        // When drilling down with a node, we first drill into the route, then wait
        // for render to populate the EIP node boxes, then select the node
        if (node != null) {
            // Drill into the route first (sets topologyMode=false)
            if (route != null) {
                facade.navigateDiagramToNode(route, null);
            }
        }

        long beforeGen = facade.getRenderGeneration();
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (facade.getRenderGeneration() >= beforeGen + 2) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Now that the render has populated EIP node boxes, select the node
        if (node != null) {
            String selected = facade.navigateDiagramToNode(null, node);
            if (selected != null) {
                result.put("selectedNode", node);
                if (route != null) {
                    result.put("drillDownRoute", route);
                }
            } else {
                result.put("nodeError", "Node not found: " + node
                                        + (route != null ? " in route " + route : ""));
            }
        }
        Buffer buf = facade.getLastBuffer();
        if (buf != null) {
            result.put("screen", ExportRequest.export(buf).text().toString());
        }
        addSelectionContext(result);
        addFooterActions(result);
        return Jsoner.serialize(result);
    }

    @SuppressWarnings("unchecked")
    private String callSendKeys(Map<String, Object> args) {
        Object keysArg = args.get("keys");
        if (!(keysArg instanceof List)) {
            return "Error: keys must be an array of strings";
        }
        List<String> keys = ((List<Object>) keysArg).stream()
                .map(String::valueOf)
                .toList();
        if (keys.isEmpty()) {
            return "Error: keys array is empty";
        }
        int delay = 150;
        Object delayArg = args.get("delay");
        if (delayArg instanceof Number n) {
            delay = Math.max(80, n.intValue());
        }
        TapeRecorder recorder = facade.getTapeRecorder();
        if (recorder != null && recorder.isActive()) {
            recorder.resetClock();
            recorder.recordKeys(keys, delay);
        }

        boolean wait = Boolean.TRUE.equals(args.get("wait"));
        long beforeGen = wait ? facade.getRenderGeneration() : 0;
        int sent = facade.injectKeys(keys, delay);

        if (!wait) {
            return "Queued " + sent + " key(s) with " + delay + "ms delay";
        }

        long lastKeyFireAt = System.currentTimeMillis() + (long) (sent - 1) * delay;
        long deadline = lastKeyFireAt + 5000;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() < deadline) {
            long now = System.currentTimeMillis();
            if (now >= lastKeyFireAt) {
                long gen = facade.getRenderGeneration();
                if (gen >= beforeGen + sent + 2) {
                    break;
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Buffer buf = facade.getLastBuffer();
        JsonObject result = new JsonObject();
        result.put("sent", sent);
        result.put("delay", delay);
        result.put("waitedMs", System.currentTimeMillis() - start);
        if (buf != null) {
            result.put("screen", ExportRequest.export(buf).text().toString());
        }
        addSelectionContext(result);
        addFooterActions(result);
        return Jsoner.serialize(result);
    }

    private String callGetOptions() {
        JsonObject result = new JsonObject();
        JsonArray tabsArray = new JsonArray();
        for (String name : facade.getTabNames()) {
            JsonObject tab = new JsonObject();
            tab.put("name", name);
            String desc = McpFacade.TAB_DESCRIPTIONS.get(name);
            if (desc != null) {
                tab.put("description", desc);
            }
            tabsArray.add(tab);
        }
        result.put("tabs", tabsArray);
        result.put("activeTab", facade.getActiveTabName());
        result.put("activeTabIndex", facade.getActiveTabIndex());
        result.put("integrations", toJsonArray(facade.getIntegrationNames()));
        String selected = facade.getSelectedIntegrationName();
        if (selected != null) {
            result.put("selectedIntegration", selected);
        }
        result.put("integrationCount", facade.getIntegrationCount());

        List<String> actions = facade.getActionLabels();
        JsonArray actionsArray = new JsonArray();
        for (int i = 0; i < actions.size(); i++) {
            JsonObject action = new JsonObject();
            action.put("index", i);
            action.put("label", actions.get(i));
            action.put("keys", actionKeys(i, actions.size()));
            actionsArray.add(action);
        }
        result.put("actions", actionsArray);
        result.put("actionsHint",
                "Press F2 to open the Actions menu, then use Down arrow to reach the item by index, then Enter to select.");

        return Jsoner.serialize(result);
    }

    private String actionKeys(int index, int totalActions) {
        StringBuilder sb = new StringBuilder("F2");
        for (int i = 0; i < index; i++) {
            sb.append(",Down");
        }
        sb.append(",Enter");
        return sb.toString();
    }

    private String callWaitForIdle(Map<String, Object> args) {
        int timeout = 5000;
        Object timeoutArg = args.get("timeout");
        if (timeoutArg instanceof Number n) {
            timeout = Math.min(30_000, Math.max(500, n.intValue()));
        }
        int requiredFrames = 2;
        Object framesArg = args.get("frames");
        if (framesArg instanceof Number n) {
            requiredFrames = Math.max(1, Math.min(10, n.intValue()));
        }

        long startGeneration = facade.getRenderGeneration();
        long start = System.currentTimeMillis();
        long deadline = start + timeout;

        while (System.currentTimeMillis() < deadline) {
            long current = facade.getRenderGeneration();
            if (current >= startGeneration + requiredFrames) {
                Buffer buf = facade.getLastBuffer();
                JsonObject result = new JsonObject();
                result.put("settled", true);
                result.put("waitedMs", System.currentTimeMillis() - start);
                result.put("frames", current - startGeneration);
                if (buf != null) {
                    result.put("screen", ExportRequest.export(buf).text().toString());
                }
                addSelectionContext(result);
                return Jsoner.serialize(result);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        JsonObject result = new JsonObject();
        result.put("settled", false);
        result.put("waitedMs", System.currentTimeMillis() - start);
        result.put("reason", "timeout");
        return Jsoner.serialize(result);
    }

    private String callTapeStart(Map<String, Object> args) {
        if (facade.isTapeRecording()) {
            return "Tape recording is already active. Stop it first with tui_tape_stop.";
        }
        String title = args.get("title") instanceof String s ? s : null;
        facade.startTapeRecording(title);
        return "Tape recording started" + (title != null ? ": " + title : "");
    }

    private String callTapeStop(Map<String, Object> args) {
        if (!facade.isTapeRecording()) {
            return "No tape recording is active. Start one with tui_tape_start.";
        }
        TapeRecorder recorder = facade.getTapeRecorder();
        String tape = recorder.stop();
        int keyCount = recorder.getKeyCount();
        long durationMs = recorder.getDurationMs();
        facade.clearTapeRecorder();

        JsonObject result = new JsonObject();
        result.put("tape", tape);
        result.put("keyCount", keyCount);
        result.put("duration", TapeRecorder.formatSleep(durationMs));

        boolean save = Boolean.TRUE.equals(args.get("save"));
        if (save) {
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = "camel-tui-tape-" + timestamp + ".tape";
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(filename), tape);
                result.put("file", filename);
            } catch (java.io.IOException e) {
                result.put("saveError", e.getMessage());
            }
        }

        return Jsoner.serialize(result);
    }

    private String callSleep(Map<String, Object> args) {
        Object secArg = args.get("seconds");
        int seconds = secArg instanceof Number n ? n.intValue() : 3;
        seconds = Math.max(1, Math.min(30, seconds));

        TapeRecorder recorder = facade.getTapeRecorder();
        if (recorder != null && recorder.isActive()) {
            recorder.resetClock();
            recorder.recordSleep(seconds * 1000L);
        }

        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return "Slept for " + seconds + "s";
    }

    @SuppressWarnings("unchecked")
    private String callDraw(Map<String, Object> args) {
        Object cellsArg = args.get("cells");
        if (!(cellsArg instanceof List)) {
            return "Error: cells must be an array";
        }
        List<Object> cellsList = (List<Object>) cellsArg;
        if (cellsList.isEmpty()) {
            return "Error: cells array is empty";
        }

        List<DrawOverlay.DrawCell> drawCells = new ArrayList<>();
        for (Object item : cellsList) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> cell = (Map<String, Object>) item;

            int x = cell.get("x") instanceof Number n ? n.intValue() : -1;
            int y = cell.get("y") instanceof Number n ? n.intValue() : -1;
            String ch = cell.get("char") instanceof String s ? s : " ";
            if (x < 0 || y < 0) {
                continue;
            }

            dev.tamboui.style.Style style = dev.tamboui.style.Style.EMPTY;
            dev.tamboui.style.Color fg = DrawOverlay.parseColor(
                    cell.get("fg") instanceof String s ? s : null);
            dev.tamboui.style.Color bg = DrawOverlay.parseColor(
                    cell.get("bg") instanceof String s ? s : null);
            if (fg != null) {
                style = style.fg(fg);
            }
            if (bg != null) {
                style = style.bg(bg);
            }
            if (Boolean.TRUE.equals(cell.get("bold"))) {
                style = style.bold();
            }

            drawCells.add(new DrawOverlay.DrawCell(x, y, ch, style));
        }

        if (drawCells.isEmpty()) {
            return "Error: no valid cells in array";
        }

        boolean append = Boolean.TRUE.equals(args.get("append"));
        int duration = 0;
        if (args.get("duration") instanceof Number n) {
            duration = n.intValue();
        }

        if (append) {
            facade.appendDrawing(drawCells);
        } else {
            facade.setDrawing(drawCells, duration);
        }

        return "Drawing " + drawCells.size() + " cell(s)"
               + (append ? " (appended)" : "")
               + (duration > 0 ? ", auto-dismiss in " + duration + "s" : "");
    }

    private String callDrawClear() {
        facade.clearDrawing();
        return "Drawing cleared";
    }

    private String callGetTable(Map<String, Object> args) {
        String tab = args.get("tab") instanceof String s ? s : null;
        JsonObject data = facade.getTableData(tab);
        if (data == null) {
            return "No table data available" + (tab != null ? " for tab: " + tab : "");
        }
        return Jsoner.serialize(data);
    }

    private String callAction(Map<String, Object> args) {
        String action = (String) args.get("action");
        if (action == null || action.isBlank()) {
            return "Error: action is required";
        }
        boolean executed = facade.executeAction(action);
        if (executed) {
            return "Action '" + action + "' executed";
        }
        return "Unknown or unsupported action: " + action
               + ". Available: reset-stats, reset-screen, screenshot, show-keystrokes, "
               + "tape-recording, doctor, caption, mcp-info, mcp-log";
    }

    private String callGetLog(Map<String, Object> args) {
        int limit = 50;
        if (args.get("limit") instanceof Number n) {
            limit = Math.max(1, Math.min(1000, n.intValue()));
        }
        String filter = args.get("filter") instanceof String s ? s : null;
        String level = args.get("level") instanceof String s ? s : null;
        JsonObject data = facade.getLogData(limit, filter, level);
        return Jsoner.serialize(data);
    }

    private String callGetErrors() {
        JsonObject data = facade.getTableData("Errors");
        if (data == null) {
            JsonObject empty = new JsonObject();
            empty.put("tab", "Errors");
            empty.put("rows", new JsonArray());
            empty.put("totalRows", 0);
            return Jsoner.serialize(empty);
        }
        return Jsoner.serialize(data);
    }

    private String callGetDiagram() {
        JsonObject data = facade.getDiagramData();
        if (data == null) {
            return "No diagram available. Navigate to the Diagram tab first.";
        }
        return Jsoner.serialize(data);
    }

    private String callGetHistory(Map<String, Object> args) {
        String exchangeId = args.get("exchangeId") instanceof String s ? s : null;
        if (exchangeId != null && !exchangeId.isBlank()) {
            facade.navigateToTab("History");
            facade.selectTraceExchange(exchangeId);
        }
        JsonObject data = facade.getTableData("History");
        if (data == null) {
            return "No history data available. Ensure the History tab has data.";
        }
        return Jsoner.serialize(data);
    }

    private String callGetTopology() {
        JsonObject data = facade.getTopologyData();
        if (data == null) {
            return "No topology data available. The Diagram tab may not have loaded yet.";
        }
        return Jsoner.serialize(data);
    }

    private String callGetSpans(Map<String, Object> args) {
        String traceId = args.get("traceId") instanceof String s ? s : null;
        int limit = 500;
        if (args.get("limit") instanceof Number n) {
            limit = n.intValue();
        }
        JsonObject data = facade.getSpanData(traceId, limit);
        return Jsoner.serialize(data);
    }

    private String callSendMessage(Map<String, Object> args) {
        String endpoint = (String) args.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return "Error: endpoint is required";
        }
        String body = args.get("body") instanceof String s ? s : null;
        String headers = args.get("headers") instanceof String s ? s : null;
        JsonObject response = facade.sendMessage(endpoint, body, headers);
        if (response == null) {
            return "Error: no integration selected or PID unavailable";
        }
        return Jsoner.serialize(response);
    }

    private String callExecuteSql(Map<String, Object> args) {
        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return "Error: query is required";
        }
        String datasource = args.get("datasource") instanceof String s ? s : null;
        int maxRows = args.get("maxRows") instanceof Number n ? n.intValue() : 100;
        int queryTimeout = args.get("queryTimeout") instanceof Number n ? n.intValue() : 30;
        JsonObject response = facade.executeSql(query, datasource, maxRows, queryTimeout);
        if (response == null) {
            return "Error: no integration selected or PID unavailable";
        }
        return Jsoner.serialize(response);
    }

    private String callUpdateRow(Map<String, Object> args) {
        String table = (String) args.get("table");
        if (table == null || table.isBlank()) {
            return "Error: table is required";
        }
        String pkValues = (String) args.get("primaryKeyValues");
        if (pkValues == null || pkValues.isBlank()) {
            return "Error: primaryKeyValues is required (JSON object)";
        }
        String colValues = (String) args.get("columnValues");
        if (colValues == null || colValues.isBlank()) {
            return "Error: columnValues is required (JSON object)";
        }
        String datasource = args.get("datasource") instanceof String s ? s : null;
        JsonObject response = facade.updateRow(table, datasource, pkValues, colValues);
        if (response == null) {
            return "Error: no integration selected or PID unavailable";
        }
        return Jsoner.serialize(response);
    }

    private String callSetLogLevel(Map<String, Object> args) {
        String level = (String) args.get("level");
        if (level == null || level.isBlank()) {
            return "Error: level is required (ERROR, WARN, INFO, DEBUG, TRACE)";
        }
        level = level.toUpperCase();
        if (!"ERROR".equals(level) && !"WARN".equals(level) && !"INFO".equals(level)
                && !"DEBUG".equals(level) && !"TRACE".equals(level)) {
            return "Error: invalid level '" + level + "'. Must be ERROR, WARN, INFO, DEBUG, or TRACE";
        }
        facade.setLogLevel(level);
        return "Log level set to " + level;
    }

    private String callFilter(Map<String, Object> args) {
        String filter = args.get("filter") instanceof String s ? s : "";
        String tab = args.get("tab") instanceof String s ? s : null;
        boolean applied = facade.setTabFilter(tab, filter);
        if (!applied) {
            return "This tab does not support text filtering";
        }
        return filter.isEmpty() ? "Filter cleared" : "Filter set to: " + filter;
    }

    private String callSetInput(Map<String, Object> args) {
        String field = (String) args.get("field");
        if (field == null || field.isBlank()) {
            return "Error: field is required";
        }
        String value = args.get("value") instanceof String s ? s : "";
        String tab = args.get("tab") instanceof String s ? s : null;
        boolean applied = facade.setTabInputValue(tab, field, value);
        if (!applied) {
            return "Error: field '" + field + "' not found on " + (tab != null ? tab : "active") + " tab";
        }
        return "Input set: " + field + " = " + (value.length() > 80 ? value.substring(0, 80) + "..." : value);
    }

    private String callToggleTraceDisplay(Map<String, Object> args) {
        String section = (String) args.get("section");
        if (section == null || section.isBlank()) {
            return "Error: section is required (headers, properties, variables, body, wrap)";
        }
        Boolean enabled = args.get("enabled") instanceof Boolean b ? b : null;
        String result = facade.toggleTraceDisplay(section, enabled);
        if (result == null) {
            return "Error: unknown section '" + section + "'. Must be headers, properties, variables, body, or wrap";
        }
        return result;
    }

    private String callGetReadme(Map<String, Object> args) {
        String name = args.get("name") instanceof String s ? s : null;
        JsonObject response = facade.getReadme(name);
        if (response == null) {
            return name != null
                    ? "No README found for integration '" + name + "'"
                    : "No README found for the selected integration";
        }
        JsonObject result = new JsonObject();
        String content = response.getString("content");
        String file = response.getStringOrDefault("file", "README");
        result.put("file", file);
        result.put("content", content != null ? content : "");
        return Jsoner.serialize(result);
    }

    private String callControl(Map<String, Object> args) {
        String action = (String) args.get("action");
        if (action == null || action.isBlank()) {
            return "Error: action is required";
        }
        return facade.controlIntegration(action);
    }

    private String callGetFiles(Map<String, Object> args) {
        String name = args.get("name") instanceof String s ? s : null;
        String file = args.get("file") instanceof String s ? s : null;
        JsonObject response = facade.getFiles(name, file);
        if (response == null) {
            return name != null
                    ? "No source files found for integration '" + name + "'"
                    : "No source files found for the selected integration";
        }
        return Jsoner.serialize(response);
    }

    @SuppressWarnings("unchecked")
    private String callLocate(Map<String, Object> args) {
        String text = args.get("text") instanceof String s ? s : null;
        String node = args.get("node") instanceof String s ? s : null;
        List<String> nodes = args.get("nodes") instanceof List<?> list
                ? ((List<Object>) list).stream().map(Object::toString).toList()
                : null;

        JsonObject result = new JsonObject();

        if (text != null) {
            JsonArray matches = facade.locateText(text);
            result.put("matches", matches);
        } else if (node != null || nodes != null) {
            List<String> ids = nodes != null ? nodes : List.of(node);
            JsonObject located = facade.locateNodes(ids);
            if (located != null) {
                result.put("matches", located.get("matches"));
                if (located.containsKey("bounds")) {
                    result.put("bounds", located.get("bounds"));
                }
            } else {
                result.put("matches", new JsonArray());
            }
        } else {
            result.put("error", "Provide 'text', 'node', or 'nodes' parameter");
        }

        return Jsoner.serialize(result);
    }

    private String callDrawShape(Map<String, Object> args) {
        String shape = args.get("shape") instanceof String s ? s : null;
        if (shape == null) {
            return "Error: 'shape' is required";
        }
        int x = args.get("x") instanceof Number n ? n.intValue() : 0;
        int y = args.get("y") instanceof Number n ? n.intValue() : 0;
        int width = args.get("width") instanceof Number n ? n.intValue() : 0;
        int height = args.get("height") instanceof Number n ? n.intValue() : Math.max(1, 0);
        int length = args.get("length") instanceof Number n ? n.intValue() : 5;
        String text = args.get("text") instanceof String s ? s : null;
        String colorName = args.get("color") instanceof String s ? s : null;
        int duration = args.get("duration") instanceof Number n ? n.intValue() : 0;

        Color color = DrawOverlay.parseColor(colorName);
        if (color == null) {
            color = "highlight".equals(shape) ? Color.YELLOW : Color.RED;
        }

        if (height < 1) {
            height = 1;
        }

        List<DrawOverlay.DrawCell> cells;
        if ("text".equals(shape)) {
            cells = DrawOverlay.generateText(x, y, text != null ? text : "", color);
        } else {
            cells = DrawOverlay.generateShape(shape, x, y, width, height, length, color);
        }

        if (cells.isEmpty() && !"text".equals(shape)) {
            return "Unknown shape: " + shape
                   + ". Use: box, highlight, underline, arrow-down, arrow-up, arrow-left, arrow-right, text";
        }

        boolean append = args.get("append") instanceof Boolean b && b;
        if (append) {
            facade.appendDrawing(cells);
        } else {
            facade.setDrawing(cells, duration);
        }
        return "Drew " + shape + " at (" + x + "," + y + ")";
    }

    private static JsonArray toJsonArray(List<String> list) {
        JsonArray arr = new JsonArray();
        arr.addAll(list);
        return arr;
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

    // --- Tool definition helpers ---

    private JsonObject toolDef(String name, String description, Map<String, JsonObject> properties) {
        return toolDef(name, description, properties, List.of());
    }

    private JsonObject toolDef(String name, String description, Map<String, JsonObject> properties, List<String> required) {
        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        if (!properties.isEmpty()) {
            JsonObject props = new JsonObject();
            props.putAll(properties);
            schema.put("properties", props);
        }
        if (!required.isEmpty()) {
            JsonArray req = new JsonArray();
            req.addAll(required);
            schema.put("required", req);
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
