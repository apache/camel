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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.export.ExportRequest;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.widgets.tabs.TabsState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;

/**
 * Facade that exposes monitor state and actions to the MCP server.
 * <p>
 * Extracts the MCP-facing accessor and control methods from {@link CamelMonitor} to reduce coupling and class size.
 * {@link TuiMcpServer} depends only on this facade, not on the full monitor.
 */
class McpFacade {

    /**
     * Pending key event queued for injection into the TUI event loop.
     */
    record PendingKey(KeyEvent event, long fireAt) {
    }

    /**
     * Callback interface for operations that remain in {@link CamelMonitor}.
     */
    interface MonitorBridge {

        MonitorTab activeTab();

        void handleTabKey(int tabIndex);

        void selectMoreTab(int moreIndex);

        boolean isSwitchPopupVisible();

        boolean isMorePopupVisible();

        void renderOverviewFooter(List<Span> spans);

        void insertFKeyHints(List<Span> spans);

        void sendRouteCommand(String pid, String routeId, String command);

        void restartProcess();

        void stopProcess(boolean forceKill);
    }

    // Tab name constants
    static final String[] TAB_NAMES = {
            "Overview", "Log", "Diagram", "Routes", "Endpoints",
            "HTTP", "Health", "Inspect", "Errors", "More"
    };

    static final String[] MORE_TAB_NAMES = {
            "Beans", "Browse", "Circuit Breaker", "Classpath", "Configuration",
            "Consumers", "DataSource", "Heap Histogram", "Inflight", "Memory", "Metrics", "SQL Query", "SQL Trace",
            "Spans", "Process", "Startup", "Threads"
    };

    static final Map<String, String> TAB_DESCRIPTIONS = Map.ofEntries(
            Map.entry("Overview", "Running integrations with PID, uptime, and exchange statistics"),
            Map.entry("Log", "Live application log with ANSI color support and filtering"),
            Map.entry("Diagram", "Route topology diagram showing how routes connect to each other and external systems"),
            Map.entry("Routes", "Route list with state, message counts, throughput, and failure statistics"),
            Map.entry("Endpoints", "Registered endpoints with usage statistics (hits, direction)"),
            Map.entry("HTTP", "HTTP endpoint probe — send requests and inspect responses"),
            Map.entry("Health", "Health check status for readiness and liveness probes"),
            Map.entry("Inspect", "Message history trace showing route path, headers, body, and timing"),
            Map.entry("Errors", "Routing errors with exception details, stack traces, and exchange context"),
            Map.entry("Beans", "Registered beans in the Camel registry"),
            Map.entry("Browse", "Browse messages queued in browsable endpoints (e.g. SEDA)"),
            Map.entry("Circuit Breaker", "Circuit breaker state and statistics (Resilience4j)"),
            Map.entry("Classpath", "JVM classpath entries with filtering"),
            Map.entry("Configuration", "Application configuration properties"),
            Map.entry("Consumers", "Consumer statistics (polling and event-driven consumers)"),
            Map.entry("DataSource", "JDBC DataSource pool statistics (active, idle, max connections)"),
            Map.entry("Heap Histogram",
                    "Class-level heap memory analysis showing instance counts and byte usage per class"),
            Map.entry("Inflight", "Currently in-flight exchanges being processed"),
            Map.entry("Memory", "JVM memory usage (heap/non-heap), GC stats, and thread counts"),
            Map.entry("Metrics", "Micrometer metrics (counters, gauges, timers, distribution summaries)"),
            Map.entry("SQL Query",
                    "Execute SQL queries against DataSources in the running application and browse results"),
            Map.entry("SQL Trace",
                    "Trace SQL query executions through camel-sql and camel-jdbc components. "
                                   + "Shows per-query timing, row counts, and failure status. "
                                   + "Use to identify slow queries, fastest queries, most frequent queries, "
                                   + "and failed SQL executions. Sortable by time, type, duration, and rows."),
            Map.entry("Spans", "OpenTelemetry spans with trace/span IDs, timing, and attributes"),
            Map.entry("Process", "OS process information (PID, CPU, memory, file descriptors)"),
            Map.entry("Startup", "Startup step recorder showing initialization timing"),
            Map.entry("Threads", "JVM thread dump with thread names, states, and stack traces"));

    private final MonitorContext ctx;
    private final AtomicReference<List<IntegrationInfo>> data;
    private final TabsState tabsState;
    private final RecordingManager recordingManager;
    private final CaptionOverlay captionOverlay;
    private final DrawOverlay drawOverlay;
    private final HelpOverlay helpOverlay;
    private final ActionsPopup actionsPopup;
    private final FilesBrowser filesBrowser;
    private final TabRegistry tabRegistry;
    private final Queue<PendingKey> pendingKeys;
    private final MonitorBridge bridge;

    McpFacade(
              MonitorContext ctx,
              AtomicReference<List<IntegrationInfo>> data,
              TabsState tabsState,
              RecordingManager recordingManager,
              CaptionOverlay captionOverlay,
              DrawOverlay drawOverlay,
              HelpOverlay helpOverlay,
              ActionsPopup actionsPopup,
              FilesBrowser filesBrowser,
              TabRegistry tabRegistry,
              Queue<PendingKey> pendingKeys,
              MonitorBridge bridge) {
        this.ctx = ctx;
        this.data = data;
        this.tabsState = tabsState;
        this.recordingManager = recordingManager;
        this.captionOverlay = captionOverlay;
        this.drawOverlay = drawOverlay;
        this.helpOverlay = helpOverlay;
        this.actionsPopup = actionsPopup;
        this.filesBrowser = filesBrowser;
        this.tabRegistry = tabRegistry;
        this.pendingKeys = pendingKeys;
        this.bridge = bridge;
    }

    // ---- Screen state ----

    Buffer getLastBuffer() {
        return recordingManager.getLastBuffer();
    }

    long getRenderGeneration() {
        return recordingManager.getRenderGeneration();
    }

    // ---- Recording / events ----

    boolean isKeystrokesVisible() {
        return recordingManager.isRecording();
    }

    TapeRecorder getTapeRecorder() {
        return recordingManager.getTapeRecorder();
    }

    boolean isTapeRecording() {
        return recordingManager.isTapeRecording();
    }

    void startTapeRecording(String title) {
        recordingManager.startTapeRecording(title);
    }

    void clearTapeRecorder() {
        recordingManager.clearTapeRecorder();
    }

    TuiEventLog getEventLog() {
        return recordingManager.getEventLog();
    }

    // ---- Navigation state ----

    int getActiveTabIndex() {
        return tabsState.selected();
    }

    String getActiveTabName() {
        int idx = tabsState.selected();
        return idx >= 0 && idx < TAB_NAMES.length ? TAB_NAMES[idx] : "Unknown";
    }

    String getSelectedPid() {
        return ctx != null ? ctx.selectedPid : null;
    }

    String getSelectedIntegrationName() {
        if (ctx == null) {
            return null;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.name : null;
    }

    int getIntegrationCount() {
        List<IntegrationInfo> list = data.get();
        return (int) list.stream().filter(i -> !i.vanishing).count();
    }

    // ---- Caption / draw overlays ----

    boolean isCaptionVisible() {
        return captionOverlay.isCaptionVisible();
    }

    void showCaption(String text) {
        captionOverlay.showCaption(text);
    }

    void showCaption(String text, int durationSeconds) {
        captionOverlay.showCaption(text, durationSeconds);
    }

    void setDrawing(List<DrawOverlay.DrawCell> cells, int durationSeconds) {
        drawOverlay.setDrawing(cells, durationSeconds);
    }

    void appendDrawing(List<DrawOverlay.DrawCell> cells) {
        drawOverlay.appendDrawing(cells);
    }

    void clearDrawing() {
        drawOverlay.clear();
    }

    // ---- Tab navigation ----

    String navigateToTab(String tabName) {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (TAB_NAMES[i].equalsIgnoreCase(tabName)) {
                bridge.handleTabKey(i);
                return TAB_NAMES[i];
            }
        }
        // Check More submenu tabs
        for (int i = 0; i < MORE_TAB_NAMES.length; i++) {
            if (MORE_TAB_NAMES[i].equalsIgnoreCase(tabName)) {
                bridge.selectMoreTab(i);
                return MORE_TAB_NAMES[i];
            }
        }
        return null;
    }

    String selectIntegration(String nameOrPid) {
        List<IntegrationInfo> infos = data.get();
        for (IntegrationInfo info : infos) {
            if (info.vanishing) {
                continue;
            }
            if (nameOrPid.equals(info.pid)
                    || (info.name != null && info.name.equalsIgnoreCase(nameOrPid))) {
                ctx.selectedPid = info.pid;
                return info.name != null ? info.name : info.pid;
            }
        }
        return null;
    }

    List<String> getTabNames() {
        List<String> names = new ArrayList<>();
        names.addAll(List.of(TAB_NAMES));
        names.addAll(List.of(MORE_TAB_NAMES));
        return names;
    }

    List<String> getActionLabels() {
        return actionsPopup.getActionLabels();
    }

    SelectionContext getSelectionContext() {
        SelectionContext popup = actionsPopup.getSelectionContext();
        if (popup != null) {
            return popup;
        }
        MonitorTab tab = bridge.activeTab();
        return tab != null ? tab.getSelectionContext() : null;
    }

    List<String> getIntegrationNames() {
        return data.get().stream()
                .filter(i -> !i.vanishing)
                .map(i -> i.name != null ? i.name : i.pid)
                .toList();
    }

    // ---- Key injection ----

    int injectKeys(List<String> keys, int delayMs) {
        long fireAt = System.currentTimeMillis();
        int count = 0;
        for (String key : keys) {
            KeyEvent ke = parseKey(key);
            if (ke != null) {
                pendingKeys.add(new PendingKey(ke, fireAt));
                fireAt += delayMs;
                count++;
            }
        }
        return count;
    }

    static KeyEvent parseKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        boolean ctrl = false;
        boolean shift = false;
        String remainder = key;
        while (remainder.contains("+")) {
            int plus = remainder.indexOf('+');
            String mod = remainder.substring(0, plus).trim();
            remainder = remainder.substring(plus + 1).trim();
            if (mod.equalsIgnoreCase("Ctrl")) {
                ctrl = true;
            } else if (mod.equalsIgnoreCase("Shift")) {
                shift = true;
            }
        }

        KeyModifiers mods = KeyModifiers.of(ctrl, false, shift);

        KeyCode code = switch (remainder.toLowerCase(Locale.US)) {
            case "enter", "return" -> KeyCode.ENTER;
            case "esc", "escape" -> KeyCode.ESCAPE;
            case "tab" -> KeyCode.TAB;
            case "backspace" -> KeyCode.BACKSPACE;
            case "delete", "del" -> KeyCode.DELETE;
            case "up" -> KeyCode.UP;
            case "down" -> KeyCode.DOWN;
            case "left" -> KeyCode.LEFT;
            case "right" -> KeyCode.RIGHT;
            case "home" -> KeyCode.HOME;
            case "end" -> KeyCode.END;
            case "pageup", "pgup" -> KeyCode.PAGE_UP;
            case "pagedown", "pgdn" -> KeyCode.PAGE_DOWN;
            case "f1" -> KeyCode.F1;
            case "f2" -> KeyCode.F2;
            case "f3" -> KeyCode.F3;
            case "f4" -> KeyCode.F4;
            case "f5" -> KeyCode.F5;
            case "f6" -> KeyCode.F6;
            case "f7" -> KeyCode.F7;
            case "f8" -> KeyCode.F8;
            case "f9" -> KeyCode.F9;
            case "f10" -> KeyCode.F10;
            case "f11" -> KeyCode.F11;
            case "f12" -> KeyCode.F12;
            case "space" -> null;
            default -> null;
        };

        if (code != null) {
            return KeyEvent.ofKey(code, mods);
        }
        if ("space".equalsIgnoreCase(remainder)) {
            return KeyEvent.ofChar(' ', mods);
        }
        if (remainder.length() == 1) {
            return KeyEvent.ofChar(remainder.charAt(0), mods);
        }
        return null;
    }

    // ---- Data access ----

    JsonObject getTableData(String tabName) {
        if (tabName != null && !tabName.isBlank()) {
            String switched = navigateToTab(tabName);
            if (switched == null) {
                return null;
            }
        }
        MonitorTab tab = bridge.activeTab();
        return tab != null ? tab.getTableDataAsJson() : null;
    }

    boolean executeAction(String actionName) {
        return actionsPopup.executeActionByName(actionName);
    }

    JsonObject getLogData(int limit, String filter, String level) {
        return tabRegistry.logTab().getLogDataAsJson(limit, filter, level);
    }

    JsonObject getDiagramData() {
        MonitorTab tab = bridge.activeTab();
        if (tab instanceof DiagramTab dt) {
            return dt.getTableDataAsJson();
        }
        return tabRegistry.diagramTab().getTableDataAsJson();
    }

    void selectTraceExchange(String exchangeId) {
        tabRegistry.historyTab().selectTraceExchange(exchangeId);
    }

    JsonObject getTopologyData() {
        return tabRegistry.diagramTab().getTopologyDataAsJson();
    }

    JsonObject getSpanData(String traceId, int limit) {
        String pid = ctx.selectedPid;
        if (pid == null) {
            JsonObject err = new JsonObject();
            err.put("error", "No integration selected");
            return err;
        }
        try {
            Path outputFile = ctx.getOutputFile(pid);
            PathUtils.deleteFile(outputFile);

            JsonObject action = new JsonObject();
            action.put("action", "span");
            action.put("dump", "true");
            action.put("limit", String.valueOf(limit));
            Path actionFile = ctx.getActionFile(pid);
            PathUtils.writeTextSafely(action.toJson(), actionFile);

            JsonObject response = MonitorContext.pollJsonResponse(outputFile, 3000);
            if (response != null) {
                PathUtils.deleteFile(outputFile);
                Boolean enabled = response.getBoolean("enabled");
                if (enabled == null || !enabled) {
                    JsonObject err = new JsonObject();
                    err.put("error", "OpenTelemetry not enabled (requires --observe flag)");
                    return err;
                }
                if (traceId != null && !traceId.isBlank()) {
                    JsonArray all = response.getCollection("spans");
                    if (all != null) {
                        JsonArray filtered = new JsonArray();
                        for (Object o : all) {
                            JsonObject span = (JsonObject) o;
                            String tid = span.getString("traceId");
                            if (tid != null && tid.contains(traceId)) {
                                filtered.add(span);
                            }
                        }
                        response.put("spans", filtered);
                    }
                }
                return response;
            }
            JsonObject err = new JsonObject();
            err.put("error", "Timeout waiting for span data");
            return err;
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.put("error", e.getMessage());
            return err;
        }
    }

    // ---- Diagram navigation ----

    String navigateDiagramToRoute(String routeId) {
        navigateToTab("Diagram");
        if (tabRegistry.diagramTab().selectRoute(routeId)) {
            return routeId;
        }
        return null;
    }

    String navigateDiagramToNode(String routeId, String nodeId) {
        navigateToTab("Diagram");
        if (tabRegistry.diagramTab().selectNode(routeId, nodeId)) {
            return nodeId;
        }
        return null;
    }

    JsonObject getDiagramState() {
        return tabRegistry.diagramTab().getDiagramStateAsJson();
    }

    // ---- Screen location ----

    JsonArray locateText(String search) {
        Buffer buf = recordingManager.getLastBuffer();
        if (buf == null || search == null || search.isEmpty()) {
            return new JsonArray();
        }
        String screen = ExportRequest.export(buf).text().toString();
        String[] lines = screen.split("\n", -1);
        int searchWidth = 0;
        for (int i = 0; i < search.length();) {
            int cp = search.codePointAt(i);
            searchWidth += Math.max(1, CharWidth.of(cp));
            i += Character.charCount(cp);
        }
        JsonArray matches = new JsonArray();
        for (int y = 0; y < lines.length; y++) {
            String line = lines[y];
            int idx = line.indexOf(search);
            while (idx >= 0) {
                int visualCol = 0;
                for (int i = 0; i < idx;) {
                    int cp = line.codePointAt(i);
                    visualCol += Math.max(1, CharWidth.of(cp));
                    i += Character.charCount(cp);
                }
                JsonObject match = new JsonObject();
                match.put("x", visualCol);
                match.put("y", y);
                match.put("width", searchWidth);
                match.put("height", 1);
                match.put("text", search);
                matches.add(match);
                idx = line.indexOf(search, idx + 1);
            }
            if (matches.size() >= 20) {
                break;
            }
        }
        return matches;
    }

    JsonObject locateNodes(List<String> nodeIds) {
        return tabRegistry.diagramTab().locateNodes(nodeIds);
    }

    // ---- Footer actions ----

    JsonArray getFooterActionsAsJson() {
        List<Span> spans = new ArrayList<>();
        if (helpOverlay.isVisible()) {
            helpOverlay.renderFooter(spans);
        } else if (captionOverlay.isCaptionVisible()) {
            captionOverlay.renderFooter(spans);
        } else if (filesBrowser.isVisible()) {
            filesBrowser.renderFooter(spans);
        } else if (bridge.isSwitchPopupVisible() || bridge.isMorePopupVisible()) {
            if (bridge.isSwitchPopupVisible()) {
                hint(spans, "Up/Down", "select");
                hint(spans, "Enter", "switch");
                hint(spans, "Esc", "close");
            } else {
                hint(spans, "Up/Down", "select");
                hint(spans, "Enter", "open");
                hint(spans, "Esc", "close");
            }
        } else {
            MonitorTab tab = bridge.activeTab();
            if (tabsState.selected() == TabRegistry.TAB_OVERVIEW) {
                bridge.renderOverviewFooter(spans);
            } else if (tab != null) {
                tab.renderFooter(spans);
                bridge.insertFKeyHints(spans);
            }
        }
        JsonArray actions = new JsonArray();
        for (int i = 0; i + 1 < spans.size(); i += 2) {
            String key = spans.get(i).content().trim();
            String rawLabel = spans.get(i + 1).content().trim();
            // compact "show BHPV" pattern: key="show", then space, then 4 single-letter spans, then trailing space
            if ("show".equals(key) && i + 6 < spans.size()) {
                for (int j = 0; j < 4; j++) {
                    Span letter = spans.get(i + 2 + j);
                    String ch = letter.content();
                    boolean on = ch.equals(ch.toUpperCase());
                    JsonObject toggle = new JsonObject();
                    toggle.put("key", ch.toLowerCase());
                    String label = switch (ch.toLowerCase()) {
                        case "b" -> "body";
                        case "h" -> "headers";
                        case "p" -> "properties";
                        case "v" -> "variables";
                        default -> ch;
                    };
                    toggle.put("label", label);
                    toggle.put("state", on ? "on" : "off");
                    actions.add(toggle);
                }
                i += 5; // skip the 7-span group (loop adds 2, we consumed 5 more)
                continue;
            }
            JsonObject action = new JsonObject();
            action.put("key", key);
            int bracket = rawLabel.indexOf('[');
            if (bracket > 0 && rawLabel.endsWith("]")) {
                action.put("label", rawLabel.substring(0, bracket).trim());
                action.put("state", rawLabel.substring(bracket + 1, rawLabel.length() - 1));
            } else {
                action.put("label", rawLabel);
            }
            actions.add(action);
        }
        return actions;
    }

    // ---- Tab filter / input ----

    void setLogLevel(String level) {
        tabRegistry.logTab().setLogLevel(level);
    }

    boolean setTabFilter(String tabName, String filter) {
        if (tabName != null) {
            navigateToTab(tabName);
        }
        MonitorTab tab = bridge.activeTab();
        return tab != null && tab.setFilter(filter);
    }

    boolean setTabInputValue(String tabName, String field, String value) {
        if (tabName != null) {
            navigateToTab(tabName);
        }
        MonitorTab tab = bridge.activeTab();
        return tab != null && tab.setInputValue(field, value);
    }

    String toggleTraceDisplay(String section, Boolean enabled) {
        return tabRegistry.historyTab().toggleDisplaySection(section, enabled);
    }

    // ---- Integration data ----

    JsonObject getReadme(String name) {
        List<IntegrationInfo> integrations = data.get();
        IntegrationInfo target = null;
        if (name != null && !name.isEmpty()) {
            for (IntegrationInfo info : integrations) {
                if (!info.vanishing && name.equals(info.name)) {
                    target = info;
                    break;
                }
            }
        } else {
            target = ctx != null ? ctx.findSelectedIntegration() : null;
        }
        if (target == null) {
            return null;
        }
        if (target.readmeFiles == null || target.readmeFiles.isEmpty()) {
            return null;
        }
        try {
            Path outputFile = ctx.getOutputFile(target.pid);
            Files.deleteIfExists(outputFile);
            JsonObject action = new JsonObject();
            action.put("action", "readme");
            PathUtils.writeTextSafely(action.toJson(), ctx.getActionFile(target.pid));
            return MonitorContext.pollJsonResponse(outputFile, 5000);
        } catch (Exception e) {
            return null;
        }
    }

    JsonObject getFiles(String name, String file) {
        List<IntegrationInfo> integrations = data.get();
        IntegrationInfo target = null;
        if (name != null && !name.isEmpty()) {
            for (IntegrationInfo info : integrations) {
                if (!info.vanishing && name.equals(info.name)) {
                    target = info;
                    break;
                }
            }
        } else {
            target = ctx != null ? ctx.findSelectedIntegration() : null;
        }
        if (target == null) {
            return null;
        }
        Path dir = FilesBrowser.resolveSourceDirectory(target);
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        if (file != null && !file.isEmpty()) {
            Path filePath = dir.resolve(file);
            if (!Files.isRegularFile(filePath)) {
                return null;
            }
            try {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                JsonObject result = new JsonObject();
                result.put("file", file);
                result.put("directory", dir.toString());
                result.put("size", FilesBrowser.formatFileSize(Files.size(filePath)));
                result.put("type", FilesBrowser.fileType(filePath));
                result.put("content", content);
                return result;
            } catch (IOException e) {
                return null;
            }
        }
        JsonArray files = new JsonArray();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .limit(99)
                    .forEach(p -> {
                        JsonObject entry = new JsonObject();
                        entry.put("name", p.getFileName().toString());
                        try {
                            entry.put("size", FilesBrowser.formatFileSize(Files.size(p)));
                        } catch (IOException e) {
                            entry.put("size", "0 B");
                        }
                        entry.put("type", FilesBrowser.fileType(p));
                        files.add(entry);
                    });
        } catch (IOException e) {
            return null;
        }
        if (files.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("directory", dir.toString());
        result.put("files", files);
        result.put("totalFiles", files.size());
        return result;
    }

    // ---- Integration messaging / control ----

    JsonObject sendMessage(String endpoint, String body, String headers) {
        if (ctx.selectedPid == null) {
            return null;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            return null;
        }
        return RuntimeHelper.sendMessage(pid, endpoint, body, headers);
    }

    JsonObject executeSql(String sql, String datasource, int maxRows, int queryTimeout) {
        if (ctx.selectedPid == null) {
            return null;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            return null;
        }
        return RuntimeHelper.executeSqlQuery(pid, sql, datasource, maxRows, queryTimeout);
    }

    JsonObject updateRow(String table, String datasource, String pkValuesJson, String colValuesJson) {
        if (ctx.selectedPid == null) {
            return null;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            return null;
        }
        return RuntimeHelper.executeRowUpdate(pid, table, datasource, pkValuesJson, colValuesJson);
    }

    String controlIntegration(String action) {
        if (action == null || action.isBlank()) {
            return "Error: action is required";
        }
        if (ctx.selectedPid == null) {
            return "Error: no integration selected";
        }
        String name = ctx.selectedName();
        return switch (action) {
            case "stop-routes", "pause" -> {
                if (ctx.isInfraSelected()) {
                    yield "Error: cannot stop routes on infra service";
                }
                bridge.sendRouteCommand(ctx.selectedPid, "*", "stop");
                yield "Routes stopped for " + name;
            }
            case "start-routes", "resume" -> {
                if (ctx.isInfraSelected()) {
                    yield "Error: cannot start routes on infra service";
                }
                bridge.sendRouteCommand(ctx.selectedPid, "*", "start");
                yield "Routes started for " + name;
            }
            case "restart" -> {
                if (ctx.isInfraSelected()) {
                    yield "Error: cannot restart infra service";
                }
                bridge.restartProcess();
                yield "Restarting " + name;
            }
            case "stop" -> {
                bridge.stopProcess(false);
                yield "Stopping " + name;
            }
            case "kill" -> {
                bridge.stopProcess(true);
                yield "Killed " + name;
            }
            default -> "Unknown action: " + action
                       + ". Available: stop-routes, start-routes, restart, stop, kill";
        };
    }

}
