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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.export.ExportRequest;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Single source of truth for all TUI MCP tool definitions and execution logic.
 * <p>
 * Both {@link TuiMcpServer} and {@link AiPanel} delegate to this class instead of duplicating tool definitions and
 * implementations.
 */
class TuiToolRegistry {

    record ToolDef(String name, String description, JsonObject inputSchema) {
    }

    static final class AnimationState {
        final String id;
        final int totalFrames;
        final AtomicInteger currentFrame = new AtomicInteger();
        volatile boolean cancelled;
        volatile String status = "running";

        AnimationState(String id, int totalFrames) {
            this.id = id;
            this.totalFrames = totalFrames;
        }
    }

    private final McpFacade facade;
    private final AtomicInteger animCounter = new AtomicInteger();
    private volatile AnimationState currentAnimation;

    private volatile List<ToolDef> cachedTools;
    private volatile LaunchManager launchManager;
    private volatile List<JsonObject> exampleCatalog;

    TuiToolRegistry(McpFacade facade) {
        this.facade = facade;
    }

    void setLaunchManager(LaunchManager launchManager) {
        this.launchManager = launchManager;
    }

    /**
     * The tools needed to answer questions and troubleshoot from the built-in AI panel. The remaining tools drive the
     * screen (drawing, animation, key presses, tape recording, themes) and exist for external MCP agents. Every tool
     * schema is sent on every request, and a local model pays for that in prompt-processing time, so the AI panel sends
     * only this subset to local providers unless configured otherwise.
     */
    static final Set<String> CORE_TOOLS = Set.of(
            "tui_get_state", "tui_get_options", "tui_get_table", "tui_get_log", "tui_get_errors",
            "tui_get_diagram", "tui_get_topology", "tui_get_processor_detail", "tui_catalog_doc",
            "tui_get_history", "tui_get_spans", "tui_control", "tui_send_message", "tui_get_files", "tui_write_file",
            "tui_validate_source", "tui_eval_expression",
            "tui_get_readme", "tui_navigate", "tui_set_log_level", "tui_filter", "tui_get_status",
            "tui_infra");

    /**
     * Returns all tool definitions. The result is cached since it is immutable.
     */
    List<ToolDef> getToolDefinitions() {
        List<ToolDef> tools = cachedTools;
        if (tools != null) {
            return tools;
        }
        tools = TuiToolDefinitions.all();
        cachedTools = tools;
        return tools;
    }

    /**
     * Returns only the {@link #CORE_TOOLS} definitions, in registry order.
     */
    List<ToolDef> getCoreToolDefinitions() {
        return getToolDefinitions().stream().filter(t -> CORE_TOOLS.contains(t.name())).toList();
    }

    /**
     * Executes a tool by name, returns result string.
     */
    String execute(String name, Map<String, Object> args) throws Exception {
        return switch (name) {
            case "tui_get_screen" -> callGetScreen(args);
            case "tui_get_events" -> callGetEvents(args);
            case "tui_get_state" -> callGetState();
            case "tui_get_status" -> callGetStatus(args);
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
            case "tui_get_themes" -> callGetThemes();
            case "tui_set_theme" -> callSetTheme(args);
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
            case "tui_infra" -> callInfra(args);
            case "tui_open_project" -> callOpenProject(args);
            case "tui_get_files" -> callGetFiles(args);
            case "tui_write_file" -> callWriteFile(args);
            case "tui_validate_source" -> callValidateSource(args);
            case "tui_eval_expression" -> callEvalExpression(args);
            case "tui_get_spans" -> callGetSpans(args);
            case "tui_locate" -> callLocate(args);
            case "tui_draw_shape" -> callDrawShape(args);
            case "tui_canvas_open" -> callCanvasOpen(args);
            case "tui_canvas_close" -> callCanvasClose();
            case "tui_animate" -> callAnimate(args);
            case "tui_animate_status" -> callAnimateStatus(args);
            case "tui_catalog_doc" -> callCatalogDoc(args);
            case "tui_get_processor_detail" -> callGetProcessorDetail(args);
            case "tui_get_ai_log" -> callGetAiLog(args);
            case "tui_get_mcp_log" -> callGetMcpLog(args);
            case "tui_list_examples" -> callListExamples(args);
            case "tui_run_example" -> callRunExample(args);
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    // --- Tool execution methods ---

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
        addInfraContext(result);
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
        long waitDeadline = lastKeyFireAt + 5000;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() < waitDeadline) {
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
        for (TabRegistry.TabEntry entry : facade.getTabEntries()) {
            JsonObject tab = new JsonObject();
            tab.put("name", entry.name());
            if (entry.description() != null) {
                tab.put("description", entry.description());
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
        JsonArray infraArray = new JsonArray();
        for (InfraInfo info : facade.liveInfraServices()) {
            infraArray.add(InfraSupport.toJson(info));
        }
        result.put("infraServices", infraArray);
        addInfraContext(result);

        // Menu entries by label only: tui_action takes a label, which is sturdier (and far smaller on the wire) than
        // the F2 + n x Down + Enter key sequences this used to spell out per entry.
        JsonArray actionsArray = new JsonArray();
        for (String label : facade.getActionLabels()) {
            if (!label.startsWith("─")) {
                actionsArray.add(label);
            }
        }
        result.put("actions", actionsArray);

        return Jsoner.serialize(result);
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
        List<DrawOverlay.DrawCell> drawCells = new ArrayList<>();
        int cellCount = 0;
        int shapeCount = 0;

        // Process raw cells
        if (args.get("cells") instanceof List<?> cellsList) {
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

                Style style = Style.EMPTY;
                Color fg = DrawOverlay.parseColor(
                        cell.get("fg") instanceof String s ? s : null);
                Color bg = DrawOverlay.parseColor(
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
                cellCount++;
            }
        }

        // Process shapes
        if (args.get("shapes") instanceof List<?> shapesList) {
            for (Object item : shapesList) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> s = (Map<String, Object>) item;
                drawCells.addAll(parseShape(s));
                shapeCount++;
            }
        }

        if (drawCells.isEmpty()) {
            return "Error: no valid cells or shapes provided";
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

        StringBuilder sb = new StringBuilder("Drawing ");
        if (cellCount > 0) {
            sb.append(cellCount).append(" cell(s)");
        }
        if (shapeCount > 0) {
            if (cellCount > 0) {
                sb.append(" + ");
            }
            sb.append(shapeCount).append(" shape(s)");
        }
        if (append) {
            sb.append(" (appended)");
        }
        if (duration > 0) {
            sb.append(", auto-dismiss in ").append(duration).append("s");
        }
        return sb.toString();
    }

    private String callDrawClear() {
        facade.clearDrawing();
        return "Drawing cleared";
    }

    private String callCanvasOpen(Map<String, Object> args) {
        facade.openCanvas();

        // Draw shapes if provided
        int shapeCount = 0;
        if (args.get("shapes") instanceof List<?> shapesList) {
            List<DrawOverlay.DrawCell> drawCells = new ArrayList<>();
            for (Object item : shapesList) {
                if (item instanceof Map) {
                    drawCells.addAll(parseShape((Map<String, Object>) item));
                    shapeCount++;
                }
            }
            if (!drawCells.isEmpty()) {
                facade.setDrawing(drawCells, 0);
            }
        }

        Buffer buf = facade.getLastBuffer();
        int w = buf != null ? buf.area().width() : 0;
        int h = buf != null ? buf.area().height() - 1 : 0;

        JsonObject result = new JsonObject();
        result.put("status", "Canvas opened");
        result.put("width", w);
        result.put("height", h);
        result.put("theme", Theme.isDark() ? "dark" : "light");
        if (shapeCount > 0) {
            result.put("shapesDrawn", shapeCount);
        }
        return Jsoner.serialize(result);
    }

    private String callCanvasClose() {
        facade.closeCanvas();
        AnimationState anim = currentAnimation;
        if (anim != null && "running".equals(anim.status)) {
            anim.cancelled = true;
            anim.status = "cancelled";
        }
        return "Canvas closed";
    }

    @SuppressWarnings("unchecked")
    private String callAnimate(Map<String, Object> args) {
        // Cancel any running animation
        AnimationState prev = currentAnimation;
        if (prev != null && "running".equals(prev.status)) {
            prev.cancelled = true;
            prev.status = "cancelled";
        }

        boolean requestedAutoClose = args.get("autoClose") instanceof Boolean b && b;

        // Pre-parse all frames
        record ParsedFrame(long delayMs, List<DrawOverlay.DrawCell> cells) {
        }
        List<ParsedFrame> frames = new ArrayList<>();

        // Check for built-in animation by name
        String animName = args.get("name") instanceof String s ? s : null;
        if (animName != null) {
            List<BuiltinAnimations.Frame> builtin = BuiltinAnimations.get(animName);
            if (builtin == null) {
                return "Error: unknown animation '" + animName + "'. Available: "
                       + String.join(", ", BuiltinAnimations.names());
            }
            for (BuiltinAnimations.Frame bf : builtin) {
                frames.add(new ParsedFrame(bf.delayMs(), bf.cells()));
            }
            requestedAutoClose = true;
        } else {
            List<?> framesList = args.get("frames") instanceof List<?> l ? l : List.of();
            if (framesList.isEmpty()) {
                return "Error: frames array or name is required";
            }
            for (Object item : framesList) {
                if (item instanceof Map<?, ?> m) {
                    long delay = m.get("delay") instanceof Number n ? n.longValue() : 0;
                    delay = Math.max(0, Math.min(30_000, delay));
                    List<DrawOverlay.DrawCell> cells = new ArrayList<>();
                    if (m.get("shapes") instanceof List<?> shapesList) {
                        for (Object s : shapesList) {
                            if (s instanceof Map) {
                                cells.addAll(parseShape((Map<String, Object>) s));
                            }
                        }
                    }
                    frames.add(new ParsedFrame(delay, cells));
                }
            }
        }

        String id = "anim-" + animCounter.incrementAndGet();
        AnimationState anim = new AnimationState(id, frames.size());
        currentAnimation = anim;

        boolean autoClose = requestedAutoClose;

        // Open canvas
        facade.openCanvas();

        // Launch animation on a daemon thread
        Thread animThread = new Thread(() -> {
            try {
                for (int i = 0; i < frames.size(); i++) {
                    if (anim.cancelled || !facade.isCanvasVisible()) {
                        anim.cancelled = true;
                        anim.status = "cancelled";
                        return;
                    }
                    ParsedFrame f = frames.get(i);
                    if (f.delayMs > 0) {
                        Thread.sleep(f.delayMs);
                    }
                    if (anim.cancelled || !facade.isCanvasVisible()) {
                        anim.cancelled = true;
                        anim.status = "cancelled";
                        return;
                    }
                    facade.setDrawing(f.cells, 0);
                    anim.currentFrame.set(i + 1);
                }
                anim.status = "completed";
                if (autoClose) {
                    facade.closeCanvas();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                anim.status = "cancelled";
            }
        }, "tui-animate-" + id);
        animThread.setDaemon(true);
        animThread.start();

        JsonObject result = new JsonObject();
        result.put("animationId", id);
        result.put("totalFrames", frames.size());
        result.put("status", "running");
        return Jsoner.serialize(result);
    }

    private String callAnimateStatus(Map<String, Object> args) {
        AnimationState anim = currentAnimation;
        if (anim == null) {
            return "No animation has been started";
        }
        String requestedId = args.get("animationId") instanceof String s ? s : null;
        if (requestedId != null && !requestedId.equals(anim.id)) {
            return "Animation not found: " + requestedId;
        }

        JsonObject result = new JsonObject();
        result.put("animationId", anim.id);
        result.put("status", anim.status);
        result.put("currentFrame", anim.currentFrame.get());
        result.put("totalFrames", anim.totalFrames);
        return Jsoner.serialize(result);
    }

    private String callGetTable(Map<String, Object> args) {
        String tab = args.get("tab") instanceof String s ? s : null;
        JsonObject data = facade.getTableData(tab);
        if (data == null) {
            return facade.tableDataError(tab);
        }
        return Jsoner.serialize(data);
    }

    private String callGetStatus(Map<String, Object> args) {
        String section = args.get("section") instanceof String s ? s.trim() : "";
        if (section.isEmpty()) {
            return "Error: section is required (use 'sections' to list the available names)";
        }
        String pid = args.get("pid") instanceof String s && !s.isBlank() ? s.trim() : facade.getSelectedPid();
        if (pid == null || pid.isBlank()) {
            return "No integration selected";
        }
        StatusFileReader reader = facade.statusFiles();
        List<String> sections = reader.sections(pid);
        if (sections.isEmpty()) {
            return "No status document available for PID " + pid;
        }
        if (StatusFileReader.SECTION_LIST.equals(section)) {
            JsonObject result = new JsonObject();
            result.put("pid", pid);
            result.put("sections", new JsonArray(sections));
            return Jsoner.serialize(result);
        }
        Object value = reader.section(pid, section);
        if (value == null) {
            return "Unknown section '" + section + "' for PID " + pid + ". Available: " + String.join(", ", sections);
        }
        addUptimeText(value);
        JsonObject result = new JsonObject();
        result.put("pid", pid);
        result.put("section", section);
        result.put("data", value);
        return Jsoner.serialize(result);
    }

    /**
     * Adds a human-readable {@code uptimeText} ("3h13m") next to every numeric {@code uptime}, which the status
     * document holds in milliseconds without saying so; a small model otherwise guesses the unit (13,800,803 was read
     * as 13.8 seconds). The route entries already carry their uptime as text and are left alone.
     */
    static void addUptimeText(Object value) {
        if (value instanceof JsonObject jo) {
            Object uptime = jo.get("uptime");
            if (uptime instanceof Number n && !jo.containsKey("uptimeText")) {
                jo.put("uptimeText", TimeUtils.printDuration(n.longValue()));
            }
            for (Object child : jo.values()) {
                addUptimeText(child);
            }
        } else if (value instanceof JsonArray arr) {
            for (Object child : arr) {
                addUptimeText(child);
            }
        }
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
               + ". Use a name (reset-stats, reset-screen, screenshot, show-keystrokes, "
               + "tape-recording, doctor, caption, mcp-info, mcp-log, toggle-theme) "
               + "or a menu label from tui_get_options actions";
    }

    private String callGetThemes() {
        JsonObject result = new JsonObject();
        result.put("current", Theme.mode());
        JsonArray dark = new JsonArray();
        for (ThemeMode m : ThemeMode.darkThemes()) {
            JsonObject t = new JsonObject();
            t.put("id", m.id());
            t.put("label", m.label());
            t.put("active", m.id().equals(Theme.mode()));
            dark.add(t);
        }
        JsonArray light = new JsonArray();
        for (ThemeMode m : ThemeMode.lightThemes()) {
            JsonObject t = new JsonObject();
            t.put("id", m.id());
            t.put("label", m.label());
            t.put("active", m.id().equals(Theme.mode()));
            light.add(t);
        }
        result.put("dark", dark);
        result.put("light", light);
        return Jsoner.serialize(result);
    }

    private String callSetTheme(Map<String, Object> args) {
        String themeId = (String) args.get("theme");
        if (themeId == null || themeId.isBlank()) {
            return "Error: theme is required. Use tui_get_themes to list available IDs.";
        }
        if (!Theme.isValidMode(themeId)) {
            return "Error: unknown theme '" + themeId + "'. Use tui_get_themes to list available IDs.";
        }
        Theme.setTheme(themeId);
        facade.executeAction("reset-screen");
        return "Theme switched to '" + themeId + "'";
    }

    private String callGetLog(Map<String, Object> args) {
        int limit = 50;
        if (args.get("limit") instanceof Number n) {
            limit = Math.max(1, Math.min(1000, n.intValue()));
        }
        String filter = args.get("filter") instanceof String s ? s : null;
        String level = args.get("level") instanceof String s ? s : null;
        if (args.get("infra") instanceof String alias && !alias.isBlank()) {
            return infraLog(alias, limit, filter);
        }
        JsonObject data = facade.getLogData(limit, filter, level);
        return Jsoner.serialize(data);
    }

    private void addInfraContext(JsonObject result) {
        result.put("infraCount", facade.liveInfraServices().size());
        String selectedInfra = facade.getSelectedInfraAlias();
        if (selectedInfra != null) {
            result.put("selectedInfra", selectedInfra);
        }
    }

    private String infraLog(String alias, int limit, String filter) {
        InfraInfo info = facade.findInfra(alias);
        if (info == null) {
            return unknownInfra(alias);
        }
        try {
            return Jsoner.serialize(facade.getInfraLogData(info, limit, filter));
        } catch (Exception e) {
            return "Error: cannot read log of infra service " + info.alias + ": " + e.getMessage();
        }
    }

    private String unknownInfra(String alias) {
        List<String> running = facade.liveInfraServices().stream().map(i -> i.alias).toList();
        return "Error: no running infra service named '" + alias + "'. Running: "
               + (running.isEmpty() ? "none" : String.join(", ", running));
    }

    private String callInfra(Map<String, Object> args) {
        String action = args.get("action") instanceof String s ? s.strip().toLowerCase(Locale.ROOT) : "";
        if (action.isEmpty()) {
            return "Error: action is required (list, log, start, stop, restart)";
        }
        if ("list".equals(action) || "ps".equals(action)) {
            JsonArray arr = new JsonArray();
            for (InfraInfo info : facade.liveInfraServices()) {
                arr.add(InfraSupport.toJson(info));
            }
            JsonObject result = new JsonObject();
            result.put("infraServices", arr);
            result.put("count", arr.size());
            return Jsoner.serialize(result);
        }
        String alias = args.get("alias") instanceof String s ? s.strip() : "";
        if (alias.isEmpty()) {
            return "Error: alias is required for " + action;
        }
        return switch (action) {
            case "log" -> {
                int limit = 50;
                if (args.get("limit") instanceof Number n) {
                    limit = Math.max(1, Math.min(1000, n.intValue()));
                }
                String filter = args.get("filter") instanceof String s ? s : null;
                yield infraLog(alias, limit, filter);
            }
            case "start", "run" -> {
                if (facade.findInfra(alias) != null) {
                    yield "Infra service " + alias + " is already running";
                }
                yield startInfra(alias);
            }
            case "stop" -> {
                InfraInfo info = facade.findInfra(alias);
                if (info == null) {
                    yield unknownInfra(alias);
                }
                yield facade.stopInfra(info)
                        ? "Stopping infra service " + info.alias + " (pid " + info.pid + ")"
                        : "Error: infra service " + info.alias + " (pid " + info.pid + ") is not running";
            }
            case "restart" -> {
                InfraInfo info = facade.findInfra(alias);
                if (info == null) {
                    yield unknownInfra(alias);
                }
                facade.stopInfra(info);
                yield startInfra(info.alias).replace("Starting", "Restarting");
            }
            default -> "Unknown action: " + action + ". Available: list, log, start, stop, restart";
        };
    }

    private String startInfra(String alias) {
        LaunchManager lm = launchManager;
        if (lm == null) {
            return "Error: launching is not available";
        }
        try {
            lm.startInfra(alias);
            return "Starting infra service " + alias + " (Docker/Podman required); "
                   + "it appears in tui_infra list once ready, typically within a few seconds";
        } catch (Exception e) {
            return "Error: failed to start infra service " + alias + ": " + e.getMessage();
        }
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

    private String callEvalExpression(Map<String, Object> args) {
        String expression = args.get("expression") instanceof String s ? s : null;
        if (expression == null || expression.isBlank()) {
            return "Error: expression is required";
        }
        String language = args.get("language") instanceof String s ? s : null;
        String body = args.get("body") instanceof String s ? s : null;
        JsonObject response = facade.evalExpression(language, expression, body);
        if (response == null) {
            return "Error: no integration selected or PID unavailable";
        }
        return Jsoner.serialize(response);
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
        String hint = unknownSchemeHint(endpoint, Jsoner.serialize(response));
        if (hint != null) {
            response.put("hint", hint);
        }
        return Jsoner.serialize(response);
    }

    /**
     * When a send failed because the endpoint scheme is not a Camel component (a model guessing {@code mqt t:} for
     * {@code paho-mqtt5:}), names the catalog components that look like what was meant so the next call can use one.
     */
    private String unknownSchemeHint(String endpoint, String result) {
        int colon = endpoint.indexOf(':');
        if (colon <= 0 || result == null) {
            return null;
        }
        String lower = result.toLowerCase();
        if (!(lower.contains("no component found") || lower.contains("nosuchendpoint")
                || lower.contains("failed to resolve endpoint") || lower.contains("cannot find component"))) {
            return null;
        }
        String scheme = endpoint.substring(0, colon).toLowerCase();
        List<String> similar;
        try {
            CamelCatalog catalog = CatalogLoader.loadCatalog(null, facade.getSelectedCamelVersion(), true);
            if (catalog.findComponentNames().contains(scheme)) {
                return null;
            }
            similar = catalog.suggestComponentNames(scheme, 5);
        } catch (Exception e) {
            return null;
        }
        if (similar.isEmpty()) {
            return "Camel has no component named '" + scheme + "'. Use tui_catalog_doc to find the right component, "
                   + "then send again with its scheme.";
        }
        return "Camel has no component named '" + scheme + "'. Similar components in the catalog: "
               + String.join(", ", similar) + ". Send again with one of those schemes, e.g. '" + similar.get(0)
               + endpoint.substring(colon) + "'.";
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

    private String callOpenProject(Map<String, Object> args) {
        String directory = (String) args.get("directory");
        if (directory == null || directory.isBlank()) {
            return "Error: directory is required";
        }
        return facade.openProject(directory);
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

    /** Time the last tool call spent waiting for the user (a tui_write_file confirmation), see the AI panel. */
    long consumeConfirmWaitMs() {
        return facade != null ? facade.consumeConfirmWaitMs() : 0;
    }

    private String callWriteFile(Map<String, Object> args) {
        String name = args.get("name") instanceof String s ? s : null;
        String file = args.get("file") instanceof String s ? s : null;
        String content = args.get("content") instanceof String s ? s : null;
        boolean confirm = !Boolean.FALSE.equals(args.get("confirm"));
        boolean validate = !Boolean.FALSE.equals(args.get("validate"));
        return Jsoner.serialize(facade.writeFile(name, file, content, confirm, validate));
    }

    private String callValidateSource(Map<String, Object> args) {
        String name = args.get("name") instanceof String s ? s : null;
        String file = args.get("file") instanceof String s ? s : null;
        String content = args.get("content") instanceof String s ? s : null;
        return Jsoner.serialize(facade.validateSource(name, file, content));
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

        List<DrawOverlay.DrawCell> cells = parseShape(args);
        if (cells.isEmpty() && !"text".equals(shape)) {
            return "Unknown shape: " + shape
                   + ". Use: box, highlight, underline, arrow-down, arrow-up, arrow-left, arrow-right, text";
        }

        int x = args.get("x") instanceof Number n ? n.intValue() : 0;
        int y = args.get("y") instanceof Number n ? n.intValue() : 0;
        int duration = args.get("duration") instanceof Number n ? n.intValue() : 0;
        boolean append = args.get("append") instanceof Boolean b && b;
        if (append) {
            facade.appendDrawing(cells);
        } else {
            facade.setDrawing(cells, duration);
        }
        return "Drew " + shape + " at (" + x + "," + y + ")";
    }

    // --- Helper methods ---

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
        Boolean detailFocused = facade.isDetailFocused();
        if (detailFocused != null) {
            result.put("detailFocused", detailFocused);
        }
    }

    private void addFooterActions(JsonObject result) {
        JsonArray actions = facade.getFooterActionsAsJson();
        if (actions != null && !actions.isEmpty()) {
            result.put("actions", actions);
        }
    }

    private List<DrawOverlay.DrawCell> parseShape(Map<String, Object> s) {
        String shape = s.get("shape") instanceof String v ? v : null;
        if (shape == null) {
            return List.of();
        }
        int x = s.get("x") instanceof Number n ? n.intValue() : 0;
        int y = s.get("y") instanceof Number n ? n.intValue() : 0;
        int width = s.get("width") instanceof Number n ? n.intValue() : 0;
        int height = s.get("height") instanceof Number n ? n.intValue() : 1;
        int length = s.get("length") instanceof Number n ? n.intValue() : 5;
        String text = s.get("text") instanceof String v ? v : null;
        String colorName = s.get("color") instanceof String v ? v : null;

        Color color = DrawOverlay.parseColor(colorName);
        if (color == null) {
            color = "highlight".equals(shape) ? Color.YELLOW : Color.RED;
        }
        if (height < 1) {
            height = 1;
        }

        if ("text".equals(shape)) {
            return DrawOverlay.generateText(x, y, text != null ? text : "", color);
        }
        return DrawOverlay.generateShape(shape, x, y, width, height, length, color);
    }

    private String callCatalogDoc(Map<String, Object> args) {
        String name = args.get("name") instanceof String v ? v : null;
        if (name == null || name.isEmpty()) {
            return "{\"error\": \"'name' parameter is required\"}";
        }
        String kind = args.get("kind") instanceof String v ? v : null;
        String optionsFilter = args.get("optionsFilter") instanceof String v ? v : null;
        boolean includeOptions = !Boolean.FALSE.equals(args.get("includeOptions"));
        boolean includeDoc = Boolean.TRUE.equals(args.get("includeDoc"));
        String docPage = args.get("docPage") instanceof String v ? v.trim().toLowerCase(Locale.ROOT) : null;

        String version = facade != null ? facade.getSelectedCamelVersion() : null;
        try {
            CamelCatalog catalog = CatalogLoader.loadCatalog(null, version, true);
            if (catalog == null) {
                return "{\"error\": \"Could not load catalog" + (version != null ? " for version " + version : "") + "\"}";
            }
            return buildCatalogDocResult(catalog, name, kind, optionsFilter, includeOptions, includeDoc, docPage);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.put("error", "Failed to load catalog: " + e.getMessage());
            return Jsoner.serialize(err);
        }
    }

    private String buildCatalogDocResult(
            CamelCatalog catalog, String name, String kind, String optionsFilter,
            boolean includeOptions, boolean includeDoc, String docPage) {
        String lowerFilter = optionsFilter != null ? optionsFilter.toLowerCase() : null;

        if (kind == null || "component".equals(kind)) {
            ComponentModel cm = catalog.componentModel(name);
            if (cm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-component") : null;
                return buildComponentDocJson(cm, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return notFound("Component", name, catalog.suggestComponentNames(name, 5));
            }
        }
        if (kind == null || "dataformat".equals(kind)) {
            DataFormatModel dm = catalog.dataFormatModel(name);
            if (dm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-dataformat") : null;
                return buildDataFormatDocJson(dm, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return notFound("Data format", name, catalog.suggestDataFormatNames(name, 5));
            }
        }
        if (kind == null || "language".equals(kind)) {
            LanguageModel lm = catalog.languageModel(name);
            if (lm != null) {
                String doc = null;
                if (docPage != null && !docPage.isEmpty()) {
                    doc = catalog.asciiDoc(name + "-" + docPage);
                    if (doc == null) {
                        JsonObject error = new JsonObject();
                        error.put("error", "No doc page '" + docPage + "' for language " + name);
                        error.put("docPages", new JsonArray(languageDocPages(catalog, name)));
                        return error.toJson();
                    }
                } else if (includeDoc) {
                    doc = catalog.asciiDoc(name + "-language");
                }
                boolean docPageOnly = docPage != null && !docPage.isEmpty();
                return buildLanguageDocJson(
                        lm, lowerFilter, includeOptions, doc, languageDocPages(catalog, name), docPageOnly);
            }
            if (kind != null) {
                return notFound("Language", name, catalog.suggestLanguageNames(name, 5));
            }
        }
        if (kind == null || "eip".equals(kind)) {
            EipModel em = catalog.eipModel(name);
            if (em != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-eip") : null;
                return buildEipDocJson(em, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return "{\"error\": \"EIP not found: " + name + "\"}";
            }
        }
        List<String> suggestions = new ArrayList<>(catalog.suggestComponentNames(name, 5));
        suggestions.addAll(catalog.suggestDataFormatNames(name, 3));
        suggestions.addAll(catalog.suggestLanguageNames(name, 3));
        return notFound("Artifact", name, suggestions);
    }

    /**
     * Error for a catalog lookup that found nothing, with the names the catalog suggests for the term (a protocol or
     * product name such as mqtt or s3) so the next call can use one of them.
     */
    private static String notFound(String kind, String name, List<String> suggestions) {
        JsonObject error = new JsonObject();
        error.put("error", kind + " not found: " + name);
        if (!suggestions.isEmpty()) {
            error.put("suggestions", new JsonArray(suggestions));
        }
        return error.toJson();
    }

    @SuppressWarnings("unchecked")
    private static void addCommonModelFields(JsonObject result, BaseModel<?> model) {
        if (model.getFirstVersion() != null) {
            result.put("since", model.getFirstVersion());
        }
        if (model.getSupportLevel() != null) {
            result.put("supportLevel", model.getSupportLevel().name());
        }
        if (model.isNativeSupported()) {
            result.put("nativeSupported", true);
        }
        if (model.isDeprecated()) {
            result.put("deprecated", true);
            if (model.getDeprecatedSince() != null) {
                result.put("deprecatedSince", model.getDeprecatedSince());
            }
            if (model.getDeprecationNote() != null) {
                result.put("deprecationNote", model.getDeprecationNote());
            }
        }
    }

    private String buildComponentDocJson(ComponentModel model, String filter, boolean includeOptions, String doc) {
        JsonObject result = new JsonObject();
        result.put("kind", "component");
        result.put("name", model.getScheme());
        result.put("title", model.getTitle());
        result.put("description", model.getDescription());
        if (model.getLabel() != null) {
            result.put("label", model.getLabel());
        }
        if (model.getSyntax() != null) {
            result.put("syntax", model.getSyntax());
        }
        result.put("consumerOnly", model.isConsumerOnly());
        result.put("producerOnly", model.isProducerOnly());
        result.put("remote", model.isRemote());
        result.put("groupId", model.getGroupId());
        result.put("artifactId", model.getArtifactId());
        addCommonModelFields(result, model);

        if (includeOptions) {
            JsonArray options = new JsonArray();
            if (model.getComponentOptions() != null) {
                for (BaseOptionModel opt : model.getComponentOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        options.add(optionToJson(opt, "component"));
                    }
                }
            }
            if (model.getEndpointOptions() != null) {
                for (BaseOptionModel opt : model.getEndpointOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        options.add(optionToJson(opt, "endpoint"));
                    }
                }
            }
            result.put("options", options);
            result.put("matchedOptions", options.size());
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return Jsoner.serialize(result);
    }

    private String buildDataFormatDocJson(DataFormatModel model, String filter, boolean includeOptions, String doc) {
        JsonObject result = new JsonObject();
        result.put("kind", "dataformat");
        result.put("name", model.getName());
        result.put("title", model.getTitle());
        result.put("description", model.getDescription());
        if (model.getLabel() != null) {
            result.put("label", model.getLabel());
        }
        result.put("groupId", model.getGroupId());
        result.put("artifactId", model.getArtifactId());
        addCommonModelFields(result, model);

        if (includeOptions) {
            JsonArray options = new JsonArray();
            if (model.getOptions() != null) {
                for (BaseOptionModel opt : model.getOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        options.add(optionToJson(opt, null));
                    }
                }
            }
            result.put("options", options);
            result.put("matchedOptions", options.size());
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return Jsoner.serialize(result);
    }

    /** The sub-pages of a language's documentation (simple has functions, operators, ognl and advanced). */
    private static List<String> languageDocPages(CamelCatalog catalog, String name) {
        List<String> pages = new ArrayList<>();
        for (String page : LANGUAGE_DOC_PAGES) {
            if (catalog.asciiDoc(name + "-" + page) != null) {
                pages.add(page);
            }
        }
        return pages;
    }

    private static final List<String> LANGUAGE_DOC_PAGES = List.of("functions", "operators", "ognl", "advanced");

    /**
     * The rules a small model gets wrong most: functions live inside the placeholder, operators between placeholders.
     * Sent with the simple language result so an answer's examples follow the same shape as the catalog's.
     */
    static final String SIMPLE_SYNTAX = "Values and functions go inside ${...}: ${body}, ${header.name},"
                                        + " ${exchangeProperty.name}, ${variable.name}, ${random(1,10)},"
                                        + " ${date:now:yyyy-MM-dd}. Operators go BETWEEN placeholders, with spaces,"
                                        + " never inside one: ${header.foo} == 'bar', ${header.user} ?: 'Guest',"
                                        + " ${header.n} > 5 && ${body} != null, ${header.a} == 'x' ? 'yes' : 'no'."
                                        + " Text literals are in single quotes; text outside ${...} is kept as is:"
                                        + " Hello ${header.name}. Nesting works: ${header.${header.key}}.";

    private String buildLanguageDocJson(
            LanguageModel model, String filter, boolean includeOptions, String doc, List<String> docPages,
            boolean docPageOnly) {
        JsonObject result = new JsonObject();
        result.put("kind", "language");
        result.put("name", model.getName());
        result.put("title", model.getTitle());
        result.put("description", model.getDescription());
        if (model.getLabel() != null) {
            result.put("label", model.getLabel());
        }
        result.put("groupId", model.getGroupId());
        result.put("artifactId", model.getArtifactId());
        addCommonModelFields(result, model);

        // a requested doc page is the answer; the options, functions and operators would only add tokens around it
        if (includeOptions && !docPageOnly) {
            JsonArray options = new JsonArray();
            if (model.getOptions() != null) {
                for (BaseOptionModel opt : model.getOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        options.add(optionToJson(opt, null));
                    }
                }
            }
            result.put("options", options);
            result.put("matchedOptions", options.size());
        }
        if (!docPageOnly) {
            addLanguageFunctions(result, model, filter);
        }
        if ("simple".equals(model.getName()) || "csimple".equals(model.getName())) {
            result.put("syntax", SIMPLE_SYNTAX);
        }
        if (!docPages.isEmpty()) {
            result.put("docPages", new JsonArray(docPages));
            result.put("docPagesHint", "docPage=<name> returns that documentation page as text");
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return Jsoner.serialize(result);
    }

    /**
     * The functions and operators of a language that has them (simple): without a filter their count and names by
     * group, which answers "what is there" in a few hundred tokens; with a filter the matching ones in full, with
     * parameters and examples, the way the options are filtered.
     */
    private static void addLanguageFunctions(JsonObject result, LanguageModel model, String filter) {
        List<LanguageModel.LanguageFunctionModel> functions = model.getFunctions();
        if (functions != null && !functions.isEmpty()) {
            result.put("functionCount", functions.size());
            if (filter != null) {
                JsonArray arr = new JsonArray();
                for (LanguageModel.LanguageFunctionModel fn : functions) {
                    if (matchesOptionFilter(fn, filter)
                            || (fn.getDisplayName() != null && fn.getDisplayName().toLowerCase().contains(filter))) {
                        arr.add(functionToJson(fn));
                    }
                }
                result.put("functions", arr);
                result.put("matchedFunctions", arr.size());
            } else {
                Map<String, JsonArray> groups = new TreeMap<>();
                for (LanguageModel.LanguageFunctionModel fn : functions) {
                    String group = fn.getGroup() != null ? fn.getGroup() : "other";
                    groups.computeIfAbsent(group, g -> new JsonArray()).add(fn.getName());
                }
                result.put("functionGroups", new JsonObject(groups));
                result.put("functionsHint", "optionsFilter with a function name, a group above or a word from its"
                                            + " description returns the matching functions with their parameters"
                                            + " and examples");
            }
        }
        List<LanguageModel.LanguageOperatorModel> operators = model.getOperators();
        if (operators != null && !operators.isEmpty()) {
            result.put("operatorCount", operators.size());
            if (filter != null) {
                JsonArray arr = new JsonArray();
                for (LanguageModel.LanguageOperatorModel op : operators) {
                    if (matchesOptionFilter(op, filter)
                            || (op.getOperatorKind() != null && op.getOperatorKind().toLowerCase().contains(filter))) {
                        arr.add(operatorToJson(op));
                    }
                }
                result.put("operators", arr);
                result.put("matchedOperators", arr.size());
            } else {
                JsonArray syntaxes = new JsonArray();
                for (LanguageModel.LanguageOperatorModel op : operators) {
                    syntaxes.add(op.getOperatorSyntax() != null ? op.getOperatorSyntax() : op.getName());
                }
                result.put("operatorSyntax", syntaxes);
            }
        }
    }

    private static JsonObject functionToJson(LanguageModel.LanguageFunctionModel fn) {
        JsonObject o = new JsonObject();
        o.put("name", fn.getName());
        if (fn.getDisplayName() != null) {
            o.put("displayName", fn.getDisplayName());
        }
        if (fn.getGroup() != null) {
            o.put("group", fn.getGroup());
        }
        if (fn.getJavaType() != null) {
            o.put("javaType", fn.getJavaType());
        }
        if (fn.getDescription() != null) {
            o.put("description", fn.getDescription());
        }
        if (fn.getParams() != null && !fn.getParams().isEmpty()) {
            JsonArray params = new JsonArray();
            for (LanguageModel.FunctionParamModel param : fn.getParams()) {
                JsonObject p = new JsonObject();
                p.put("name", param.getName());
                if (param.getJavaType() != null) {
                    p.put("javaType", param.getJavaType());
                }
                p.put("required", param.isRequired());
                if (param.getDescription() != null) {
                    p.put("description", param.getDescription());
                }
                params.add(p);
            }
            o.put("params", params);
        }
        if (fn.getExamples() != null && !fn.getExamples().isEmpty()) {
            o.put("examples", new JsonArray(fn.getExamples()));
        }
        if (fn.isOgnl()) {
            o.put("ognl", true);
        }
        if (fn.isDeprecated()) {
            o.put("deprecated", true);
        }
        return o;
    }

    private static JsonObject operatorToJson(LanguageModel.LanguageOperatorModel op) {
        JsonObject o = new JsonObject();
        o.put("name", op.getName());
        if (op.getDisplayName() != null) {
            o.put("displayName", op.getDisplayName());
        }
        if (op.getOperatorKind() != null) {
            o.put("kind", op.getOperatorKind());
        }
        if (op.getOperatorSyntax() != null) {
            o.put("syntax", op.getOperatorSyntax());
        }
        if (op.getDescription() != null) {
            o.put("description", op.getDescription());
        }
        if (op.getExamples() != null && !op.getExamples().isEmpty()) {
            o.put("examples", new JsonArray(op.getExamples()));
        }
        return o;
    }

    private String buildEipDocJson(EipModel model, String filter, boolean includeOptions, String doc) {
        JsonObject result = new JsonObject();
        result.put("kind", "eip");
        result.put("name", model.getName());
        result.put("title", model.getTitle());
        result.put("description", model.getDescription());
        if (model.getLabel() != null) {
            result.put("label", model.getLabel());
        }
        result.put("input", model.isInput());
        result.put("output", model.isOutput());
        addCommonModelFields(result, model);

        if (includeOptions) {
            JsonArray options = new JsonArray();
            if (model.getOptions() != null) {
                for (BaseOptionModel opt : model.getOptions()) {
                    if (matchesOptionFilter(opt, filter)) {
                        options.add(optionToJson(opt, null));
                    }
                }
            }
            result.put("options", options);
            result.put("matchedOptions", options.size());
        }
        if (doc != null) {
            result.put("doc", doc);
        }
        return Jsoner.serialize(result);
    }

    private static boolean matchesOptionFilter(BaseOptionModel opt, String filter) {
        if (filter == null) {
            return true;
        }
        return (opt.getName() != null && opt.getName().toLowerCase().contains(filter))
                || (opt.getDescription() != null && opt.getDescription().toLowerCase().contains(filter))
                || (opt.getGroup() != null && opt.getGroup().toLowerCase().contains(filter))
                || (opt.getLabel() != null && opt.getLabel().toLowerCase().contains(filter));
    }

    private static JsonObject optionToJson(BaseOptionModel opt, String scope) {
        JsonObject o = new JsonObject();
        o.put("name", opt.getName());
        o.put("description", opt.getDescription());
        o.put("type", opt.getType());
        o.put("required", opt.isRequired());
        if (opt.getDefaultValue() != null) {
            o.put("defaultValue", opt.getDefaultValue().toString());
        }
        if (opt.getGroup() != null) {
            o.put("group", opt.getGroup());
        }
        if (scope != null) {
            o.put("scope", scope);
        }
        if (opt.isDeprecated()) {
            o.put("deprecated", true);
        }
        if (opt.isSecret()) {
            o.put("secret", true);
        }
        if (opt.getEnums() != null && !opt.getEnums().isEmpty()) {
            o.put("enumValues", toJsonArray(opt.getEnums()));
        }
        return o;
    }

    private String callGetProcessorDetail(Map<String, Object> args) {
        String routeId = args.get("routeId") instanceof String v ? v : "*";
        boolean includeDocs = Boolean.TRUE.equals(args.get("includeDocs"));

        JsonObject response = facade.getProcessorDetail(routeId);
        if (response == null) {
            return "{\"error\": \"No response from integration\"}";
        }
        if (response.containsKey("error")) {
            return Jsoner.serialize(response);
        }

        if (includeDocs) {
            enrichProcessorDetailWithDocs(response);
        }
        return Jsoner.serialize(response);
    }

    private void enrichProcessorDetailWithDocs(JsonObject json) {
        String version = facade.getSelectedCamelVersion();
        CamelCatalog catalog;
        try {
            catalog = CatalogLoader.loadCatalog(null, version, true);
        } catch (Exception e) {
            return;
        }
        if (catalog == null) {
            return;
        }

        JsonArray routes = (JsonArray) json.get("routes");
        if (routes != null) {
            for (Object routeObj : routes) {
                if (routeObj instanceof JsonObject routeJson) {
                    enrichRouteProcessors(routeJson, catalog);
                }
            }
        } else {
            enrichRouteProcessors(json, catalog);
        }
    }

    private static void enrichRouteProcessors(JsonObject routeJson, CamelCatalog catalog) {
        JsonArray processors = (JsonArray) routeJson.get("processors");
        if (processors == null) {
            return;
        }
        for (Object obj : processors) {
            if (!(obj instanceof JsonObject processor)) {
                continue;
            }
            String type = processor.getString("type");
            if (type == null) {
                continue;
            }
            JsonObject opts = processor.getMap("options");
            if ("from".equals(type) || "to".equals(type) || "toD".equals(type) || "wireTap".equals(type)
                    || "enrich".equals(type) || "pollEnrich".equals(type)) {
                enrichComponentProcessorOptions(processor, opts, catalog);
            } else {
                enrichEipProcessorOptions(processor, type, opts, catalog);
            }
        }
    }

    private static void enrichComponentProcessorOptions(JsonObject processor, JsonObject opts, CamelCatalog catalog) {
        String uri = processor.getString("endpointUri");
        if (uri == null) {
            uri = opts != null ? opts.getString("uri") : null;
        }
        if (uri == null) {
            return;
        }
        String scheme = uri.contains(":") ? uri.substring(0, uri.indexOf(':')) : uri;
        ComponentModel model = catalog.componentModel(scheme);
        if (model == null) {
            return;
        }
        processor.put("componentDescription", model.getDescription());

        if (opts != null && model.getEndpointOptions() != null) {
            JsonObject optDocs = new JsonObject();
            for (BaseOptionModel opt : model.getEndpointOptions()) {
                if (opts.containsKey(opt.getName())) {
                    optDocs.put(opt.getName(), buildProcessorOptionDoc(opt));
                }
            }
            if (!optDocs.isEmpty()) {
                processor.put("optionDocs", optDocs);
            }
        }
    }

    private static void enrichEipProcessorOptions(JsonObject processor, String type, JsonObject opts, CamelCatalog catalog) {
        EipModel model = catalog.eipModel(type);
        if (model == null) {
            return;
        }
        processor.put("eipDescription", model.getDescription());

        if (opts != null && model.getOptions() != null) {
            JsonObject optDocs = new JsonObject();
            for (BaseOptionModel opt : model.getOptions()) {
                if (opts.containsKey(opt.getName())) {
                    optDocs.put(opt.getName(), buildProcessorOptionDoc(opt));
                }
            }
            if (!optDocs.isEmpty()) {
                processor.put("optionDocs", optDocs);
            }
        }
    }

    private static JsonObject buildProcessorOptionDoc(BaseOptionModel opt) {
        JsonObject doc = new JsonObject();
        doc.put("description", opt.getDescription());
        doc.put("type", opt.getType());
        if (opt.getGroup() != null && !opt.getGroup().isEmpty()) {
            doc.put("group", opt.getGroup());
        }
        if (opt.getDefaultValue() != null) {
            doc.put("defaultValue", opt.getDefaultValue().toString());
        }
        if (opt.isRequired()) {
            doc.put("required", true);
        }
        if (opt.isDeprecated()) {
            doc.put("deprecated", true);
        }
        if (opt.getEnums() != null && !opt.getEnums().isEmpty()) {
            doc.put("enum", toJsonArray(opt.getEnums()));
        }
        return doc;
    }

    private String callGetAiLog(Map<String, Object> args) {
        int limit = args.get("limit") instanceof Number n ? n.intValue() : 50;
        List<AiPanel.LogEntry> entries = facade.getAiActivityLog();
        if (entries.size() > limit) {
            entries = entries.subList(entries.size() - limit, entries.size());
        }
        JsonArray arr = new JsonArray();
        for (AiPanel.LogEntry e : entries) {
            JsonObject obj = new JsonObject();
            obj.put("timestamp", e.timestamp());
            obj.put("level", e.level().name());
            obj.put("message", e.message());
            if (e.detail() != null) {
                obj.put("detail", e.detail());
            }
            arr.add(obj);
        }
        JsonObject result = new JsonObject();
        result.put("entries", arr);
        result.put("count", arr.size());
        return result.toJson();
    }

    private String callGetMcpLog(Map<String, Object> args) {
        int limit = args.get("limit") instanceof Number n ? n.intValue() : 50;
        List<TuiMcpServer.LogEntry> entries = facade.getMcpActivityLog();
        if (entries.size() > limit) {
            entries = entries.subList(entries.size() - limit, entries.size());
        }
        JsonArray arr = new JsonArray();
        for (TuiMcpServer.LogEntry e : entries) {
            JsonObject obj = new JsonObject();
            obj.put("timestamp", e.timestamp());
            obj.put("level", e.level().name());
            obj.put("message", e.message());
            if (e.requestBody() != null) {
                obj.put("requestBody", e.requestBody());
            }
            if (e.responseBody() != null) {
                obj.put("responseBody", e.responseBody());
            }
            arr.add(obj);
        }
        JsonObject result = new JsonObject();
        result.put("entries", arr);
        result.put("count", arr.size());
        result.put("toolCallCount", facade.getMcpToolCallCount());
        return result.toJson();
    }

    @SuppressWarnings("unchecked")
    private String callListExamples(Map<String, Object> args) {
        List<JsonObject> catalog = exampleCatalog;
        if (catalog == null) {
            catalog = ExampleHelper.loadCatalog();
            exampleCatalog = catalog;
        }

        String filter = args.get("filter") instanceof String v ? v : null;
        String level = args.get("level") instanceof String v ? v : null;

        List<JsonObject> filtered = catalog;
        if (filter != null && !filter.isEmpty()) {
            filtered = ExampleHelper.filterExamples(filtered, filter);
        }
        if (level != null && !level.isEmpty()) {
            String lowerLevel = level.toLowerCase();
            filtered = filtered.stream()
                    .filter(e -> lowerLevel.equals(e.getStringOrDefault("level", "")))
                    .toList();
        }

        JsonArray examples = new JsonArray();
        for (JsonObject entry : filtered) {
            JsonObject ex = new JsonObject();
            ex.put("name", entry.getStringOrDefault("name", ""));
            ex.put("title", entry.getStringOrDefault("title", ""));
            ex.put("description", entry.getStringOrDefault("description", ""));
            ex.put("level", entry.getStringOrDefault("level", ""));
            ex.put("category", ExampleHelper.getCategory(entry));
            ex.put("tags", toJsonArray(
                    entry.get("tags") instanceof java.util.Collection<?> c
                            ? c.stream().map(Object::toString).toList()
                            : List.of()));
            ex.put("bundled", ExampleHelper.isBundled(entry));
            ex.put("requiresDocker", ExampleHelper.requiresDocker(entry));
            ex.put("infraServices", toJsonArray(ExampleHelper.getInfraServices(entry)));
            examples.add(ex);
        }

        JsonObject result = new JsonObject();
        result.put("examples", examples);
        result.put("totalCount", examples.size());
        return Jsoner.serialize(result);
    }

    private String callRunExample(Map<String, Object> args) throws Exception {
        String name = args.get("name") instanceof String v ? v : null;
        if (name == null || name.isEmpty()) {
            return "{\"error\": \"'name' parameter is required\"}";
        }

        LaunchManager lm = launchManager;
        if (lm == null) {
            return "{\"error\": \"Launching examples is not available in this session\"}";
        }

        List<JsonObject> catalog = exampleCatalog;
        if (catalog == null) {
            catalog = ExampleHelper.loadCatalog();
            exampleCatalog = catalog;
        }

        JsonObject example = ExampleHelper.findExample(catalog, name);
        if (example == null) {
            return "{\"error\": \"Unknown example: " + name + ". Use tui_list_examples to see available names.\"}";
        }

        List<String> missing = lm.findMissingInfraServices(example);
        if (!missing.isEmpty()) {
            if (!LaunchManager.isContainerRuntimeAvailable()) {
                JsonObject err = new JsonObject();
                err.put("error", "Docker/Podman required for infra services: " + String.join(", ", missing));
                return Jsoner.serialize(err);
            }
            String displayName = name;
            List<String> camelArgs = buildExampleArgs(name, args);
            lm.startMissingInfraAndDefer(
                    missing, displayName, () -> {
                        try {
                            lm.launchDetached(displayName, camelArgs);
                        } catch (Exception e) {
                            // silently swallow — same as ExampleBrowserPopup's deferred path
                        }
                    });
            JsonObject result = new JsonObject();
            result.put("status", "starting_infra");
            result.put("message", "Starting infra: " + String.join(", ", missing) + " → then: " + displayName);
            result.put("infraServices", toJsonArray(missing));
            return Jsoner.serialize(result);
        }

        List<String> camelArgs = buildExampleArgs(name, args);
        lm.launchDetached(name, camelArgs);

        JsonObject result = new JsonObject();
        result.put("status", "started");
        result.put("message", "Started: " + name);
        result.put("name", name);
        return Jsoner.serialize(result);
    }

    private static List<String> buildExampleArgs(String name, Map<String, Object> args) {
        List<String> camelArgs = new ArrayList<>();
        camelArgs.add("run");
        camelArgs.add("--example=" + name);
        camelArgs.add("--logging-color=true");
        if (name.contains("/")) {
            camelArgs.add("--name=" + TuiHelper.stripCategory(name));
        }
        String profile = args.get("profile") instanceof String v ? v : null;
        if (profile != null && !profile.isEmpty()) {
            camelArgs.add("--profile=" + profile);
        }
        return camelArgs;
    }

    private static JsonArray toJsonArray(List<String> list) {
        JsonArray arr = new JsonArray();
        arr.addAll(list);
        return arr;
    }

    // --- Tool definition helpers ---

}
