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

    record LogEntry(String timestamp, LogLevel level, String message, String requestBody, String responseBody) {
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
                                + "Both parameters are optional — set whichever you want to change. "
                                + "Tab names: Overview, Log, Routes, Consumers, Endpoints, HTTP, Health, Inspect, Circuit Breaker. "
                                + "Returns screen content and selection metadata after navigating.",
                Map.of("tab", propDef("string", "Tab to switch to (e.g. 'Routes', 'Health')"),
                        "integration", propDef("string", "Integration name or PID to select"))));

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
                "Start recording TUI interactions as a VHS .tape file for demo playback. "
                                  + "All subsequent tui_send_keys calls will be captured as tape commands. "
                                  + "Stop recording with tui_tape_stop to get the tape content.",
                Map.of("title", propDef("string", "Description comment for the tape header"))));
        toolList.add(toolDef(
                "tui_tape_stop",
                "Stop tape recording and return the generated VHS .tape content. "
                                 + "The tape can be replayed with camel monitor --record or charmbracelet/vhs.",
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
        SelectionContext ctx = monitor.getSelectionContext();
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
        addSelectionContext(result);
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
        result.put("keystrokesVisible", monitor.isKeystrokesVisible());
        result.put("captionVisible", monitor.isCaptionVisible());
        addSelectionContext(result);
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

        TapeRecorder recorder = monitor.getTapeRecorder();
        if (recorder != null && recorder.isActive()) {
            recorder.resetClock();
            recorder.recordCaption(text, Math.max(duration, 0));
        }

        if (duration > 0) {
            monitor.showCaption(text, duration);
            return "Caption displayed (auto-dismiss in " + duration + "s): " + text;
        }
        monitor.showCaption(text);
        return "Caption displayed: " + text;
    }

    private String callNavigate(Map<String, Object> args) {
        JsonObject result = new JsonObject();
        String tab = (String) args.get("tab");
        String integration = (String) args.get("integration");

        if (tab == null && integration == null) {
            result.put("error", "Provide at least one of: tab, integration");
            result.put("availableTabs", toJsonArray(monitor.getTabNames()));
            result.put("availableIntegrations", toJsonArray(monitor.getIntegrationNames()));
            return Jsoner.serialize(result);
        }

        if (integration != null) {
            String selected = monitor.selectIntegration(integration);
            if (selected != null) {
                result.put("selectedIntegration", selected);
            } else {
                result.put("integrationError", "Not found: " + integration);
                result.put("availableIntegrations", toJsonArray(monitor.getIntegrationNames()));
            }
        }

        if (tab != null) {
            String switched = monitor.navigateToTab(tab);
            if (switched != null) {
                result.put("activeTab", switched);
                TapeRecorder recorder = monitor.getTapeRecorder();
                if (recorder != null && recorder.isActive()) {
                    recorder.resetClock();
                    int tabIndex = monitor.getTabNames().indexOf(switched);
                    if (tabIndex >= 0 && tabIndex < 9) {
                        recorder.recordKey(String.valueOf(tabIndex + 1));
                    }
                }
            } else {
                result.put("tabError", "Unknown tab: " + tab);
                result.put("availableTabs", toJsonArray(monitor.getTabNames()));
            }
        }

        long beforeGen = monitor.getRenderGeneration();
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (monitor.getRenderGeneration() >= beforeGen + 2) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Buffer buf = monitor.getLastBuffer();
        if (buf != null) {
            result.put("screen", ExportRequest.export(buf).text().toString());
        }
        addSelectionContext(result);
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
        TapeRecorder recorder = monitor.getTapeRecorder();
        if (recorder != null && recorder.isActive()) {
            recorder.resetClock();
            recorder.recordKeys(keys, delay);
        }

        boolean wait = Boolean.TRUE.equals(args.get("wait"));
        long beforeGen = wait ? monitor.getRenderGeneration() : 0;
        int sent = monitor.injectKeys(keys, delay);

        if (!wait) {
            return "Queued " + sent + " key(s) with " + delay + "ms delay";
        }

        long lastKeyFireAt = System.currentTimeMillis() + (long) (sent - 1) * delay;
        long deadline = lastKeyFireAt + 5000;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() < deadline) {
            long now = System.currentTimeMillis();
            if (now >= lastKeyFireAt) {
                long gen = monitor.getRenderGeneration();
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

        Buffer buf = monitor.getLastBuffer();
        JsonObject result = new JsonObject();
        result.put("sent", sent);
        result.put("delay", delay);
        result.put("waitedMs", System.currentTimeMillis() - start);
        if (buf != null) {
            result.put("screen", ExportRequest.export(buf).text().toString());
        }
        addSelectionContext(result);
        return Jsoner.serialize(result);
    }

    private String callGetOptions() {
        JsonObject result = new JsonObject();
        result.put("tabs", toJsonArray(monitor.getTabNames()));
        result.put("activeTab", monitor.getActiveTabName());
        result.put("activeTabIndex", monitor.getActiveTabIndex());
        result.put("integrations", toJsonArray(monitor.getIntegrationNames()));
        String selected = monitor.getSelectedIntegrationName();
        if (selected != null) {
            result.put("selectedIntegration", selected);
        }
        result.put("integrationCount", monitor.getIntegrationCount());

        List<String> actions = monitor.getActionLabels();
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

        long startGeneration = monitor.getRenderGeneration();
        long start = System.currentTimeMillis();
        long deadline = start + timeout;

        while (System.currentTimeMillis() < deadline) {
            long current = monitor.getRenderGeneration();
            if (current >= startGeneration + requiredFrames) {
                Buffer buf = monitor.getLastBuffer();
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
        if (monitor.isTapeRecording()) {
            return "Tape recording is already active. Stop it first with tui_tape_stop.";
        }
        String title = args.get("title") instanceof String s ? s : null;
        monitor.startTapeRecording(title);
        return "Tape recording started" + (title != null ? ": " + title : "");
    }

    private String callTapeStop(Map<String, Object> args) {
        if (!monitor.isTapeRecording()) {
            return "No tape recording is active. Start one with tui_tape_start.";
        }
        TapeRecorder recorder = monitor.getTapeRecorder();
        String tape = recorder.stop();
        int keyCount = recorder.getKeyCount();
        long durationMs = recorder.getDurationMs();
        monitor.clearTapeRecorder();

        JsonObject result = new JsonObject();
        result.put("tape", tape);
        result.put("keyCount", keyCount);
        result.put("duration", TapeRecorder.formatSleep(durationMs));

        boolean save = Boolean.TRUE.equals(args.get("save"));
        if (save) {
            String filename = "camel-tui-tape-" + System.currentTimeMillis() + ".tape";
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

        TapeRecorder recorder = monitor.getTapeRecorder();
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
