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
import java.util.Map;
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
     * Returns all 39 tool definitions. The result is cached since it is immutable.
     */
    List<ToolDef> getToolDefinitions() {
        List<ToolDef> tools = cachedTools;
        if (tools != null) {
            return tools;
        }
        tools = buildToolDefinitions();
        cachedTools = tools;
        return tools;
    }

    /**
     * Executes a tool by name, returns result string.
     */
    String execute(String name, Map<String, Object> args) throws Exception {
        return switch (name) {
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
            case "tui_get_files" -> callGetFiles(args);
            case "tui_get_spans" -> callGetSpans(args);
            case "tui_locate" -> callLocate(args);
            case "tui_draw_shape" -> callDrawShape(args);
            case "tui_canvas_open" -> callCanvasOpen(args);
            case "tui_canvas_close" -> callCanvasClose();
            case "tui_animate" -> callAnimate(args);
            case "tui_animate_status" -> callAnimateStatus(args);
            case "tui_catalog_doc" -> callCatalogDoc(args);
            case "tui_list_examples" -> callListExamples(args);
            case "tui_run_example" -> callRunExample(args);
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    // --- Tool definitions ---

    private List<ToolDef> buildToolDefinitions() {
        List<ToolDef> tools = new ArrayList<>();
        tools.add(toToolDef(toolDef(
                "tui_get_screen",
                "Returns the current TUI screen content as text. "
                                  + "Shows exactly what the user sees in their terminal. "
                                  + "Use ansi=true to include ANSI color codes for color-related questions. "
                                  + "Also returns a 'selection' field with structured metadata about the active list/table "
                                  + "(type, items, selectedIndex, totalItems, label) when available.",
                Map.of("ansi", propDef("boolean", "Include ANSI color codes in the output (default false)")))));
        tools.add(toToolDef(toolDef(
                "tui_get_events",
                "Returns recent user input events (key presses, navigation). "
                                  + "Each event has a key, human-readable label, and timestamp.",
                Map.of("limit", propDef("integer", "Maximum number of events to return (default 50)")))));
        tools.add(toToolDef(toolDef(
                "tui_get_state",
                "Returns the current TUI navigation state: active tab, selected integration, "
                                 + "and integration count. "
                                 + "Includes a 'selection' field with structured metadata about the active list/table. "
                                 + "captionVisible indicates if a caption overlay is on screen. "
                                 + "keystrokesVisible indicates if the keystroke overlay is on; toggle with Ctrl+K.",
                Map.of())));
        tools.add(toToolDef(toolDef(
                "tui_show_caption",
                "Shows a caption message on the TUI screen with a typewriter animation. "
                                    + "Use this to display messages to the user. "
                                    + "Supports \\n for newlines.",
                Map.of("text", propDef("string", "The caption text to display"),
                        "duration", propDef("integer",
                                "Auto-dismiss after this many seconds. Caption won't block key events. "
                                                       + "If omitted, caption stays until dismissed by a key press.")),
                List.of("text"))));
        tools.add(toToolDef(toolDef(
                "tui_navigate",
                "Navigates the TUI: switch tabs and/or select an integration. "
                                + "All parameters are optional — set whichever you want to change. "
                                + "Tab names: Overview, Log, Activity, Diagram, Routes, Endpoints, HTTP, Inspect, "
                                + "Circuit Breaker, Health, Spans, Process. "
                                + "Use 'route' to select a route in the Diagram topology, "
                                + "and 'node' to drill down into a route and select a specific processor/EIP node. "
                                + "Returns screen content and selection metadata after navigating.",
                Map.of("tab", propDef("string", "Tab to switch to (e.g. 'Routes', 'Activity', 'Diagram')"),
                        "integration", propDef("string", "Integration name or PID to select"),
                        "route", propDef("string",
                                "Route ID to select in the Diagram tab topology (e.g. 'order-dispatcher')"),
                        "node", propDef("string",
                                "Processor/EIP node ID to select within a drilled-down route (e.g. 'multicast1'). "
                                                  + "If 'route' is also provided, drills into that route first")))));

        tools.add(toToolDef(toolDef(
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
                List.of("keys"))));
        tools.add(toToolDef(toolDef(
                "tui_get_options",
                "IMPORTANT: Call this FIRST before any other tui_ tool when starting a new task. "
                                   + "Returns all available tabs with descriptions and running integrations. "
                                   + "Each tab description says what data it provides — match the user's question "
                                   + "keywords to tab descriptions to find the right data source in one step "
                                   + "(e.g. 'kafka offset' → find Kafka tab → tui_get_table(tab='Kafka')). "
                                   + "This avoids wasting calls piecing data from logs, spans, and endpoints "
                                   + "when a dedicated tab already has exactly the needed information.",
                Map.of())));
        tools.add(toToolDef(toolDef(
                "tui_wait_for_idle",
                "Waits for the TUI to render new frames after an action. "
                                     + "Blocks until the specified number of new frames have been rendered, "
                                     + "ensuring the action has been processed. "
                                     + "Returns the screen content with selection metadata after settling. "
                                     + "Use after tui_navigate or tui_send_keys.",
                Map.of("timeout", propDef("integer",
                        "Maximum wait time in milliseconds (default 5000, max 30000)"),
                        "frames", propDef("integer",
                                "Number of new frames to wait for (default 2)")))));
        tools.add(toToolDef(toolDef(
                "tui_tape_start",
                "Start recording TUI interactions as a .tape file for demo playback. "
                                  + "All subsequent tui_send_keys calls will be captured as tape commands. "
                                  + "Stop recording with tui_tape_stop to get the tape content. "
                                  + "Replay with: camel tui monitor --record=<file>.tape",
                Map.of("title", propDef("string", "Description comment for the tape header")))));
        tools.add(toToolDef(toolDef(
                "tui_tape_stop",
                "Stop tape recording and return the generated .tape content. "
                                 + "The tape can be replayed with: camel tui monitor --record=<file>.tape",
                Map.of("save", propDef("boolean",
                        "If true, also save the tape to a local file (camel-tui-tape-<timestamp>.tape). Default false.")))));
        tools.add(toToolDef(toolDef(
                "tui_sleep",
                "Pauses for the specified duration. "
                             + "When tape recording is active, inserts a Sleep command into the tape. "
                             + "Use this to pace demos and wait for captions to dismiss.",
                Map.of("seconds", propDef("integer",
                        "Number of seconds to sleep (1-30)")),
                List.of("seconds"))));
        tools.add(toToolDef(toolDef(
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
                        "shapes", propDef("array",
                                "Array of shape objects to draw (batch mode). Each shape has: "
                                                   + "shape (string, required: box/highlight/underline/arrow-down/arrow-up/arrow-left/arrow-right/text), "
                                                   + "x (integer, column), y (integer, row), "
                                                   + "width (integer, for box/highlight/underline), "
                                                   + "height (integer, for box/highlight), "
                                                   + "length (integer, for arrows), "
                                                   + "text (string, for text shape), "
                                                   + "color (string: red/green/blue/yellow/cyan/magenta/white/gray/black). "
                                                   + "Use shapes instead of cells for high-level drawing in a single call."),
                        "duration", propDef("integer",
                                "Auto-dismiss drawing after this many seconds. "
                                                       + "If omitted, drawing stays until cleared with tui_draw_clear or replaced by another tui_draw call."),
                        "append", propDef("boolean",
                                "If true, add cells to the existing drawing instead of replacing it. Default false.")),
                List.of())));
        tools.add(toToolDef(toolDef(
                "tui_draw_clear",
                "Clears the drawing overlay and restores the screen to its normal state. "
                                  + "The underlying content is unchanged since drawing is an overlay.",
                Map.of())));

        tools.add(toToolDef(toolDef(
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
                List.of("shape", "x", "y"))));

        tools.add(toToolDef(toolDef(
                "tui_canvas_open",
                "Opens a full blank canvas screen for free-form drawing. "
                                   + "Use tui_draw / tui_draw_shape to draw on the canvas. "
                                   + "The user can press Esc to dismiss.",
                Map.of("shapes", propDef("array",
                        "Optional array of shapes to draw immediately on the canvas. "
                                                  + "Same format as tui_draw shapes parameter. "
                                                  + "Saves a round-trip vs separate tui_canvas_open + tui_draw calls.")))));
        tools.add(toToolDef(toolDef(
                "tui_canvas_close",
                "Closes the canvas and returns to the normal TUI screen. Also clears any drawing.",
                Map.of())));
        tools.add(toToolDef(toolDef(
                "tui_animate",
                "Run a keyframe animation on the canvas. Auto-opens the canvas, "
                               + "plays frames sequentially with specified delays, then optionally auto-closes. "
                               + "User can press Esc to stop early. Returns immediately; "
                               + "use tui_animate_status to check progress. "
                               + "Use 'name' for built-in animations (instant start, no token cost): "
                               + String.join(", ", BuiltinAnimations.names()) + ".",
                Map.of("frames", propDef("array",
                        "Array of keyframes. Each keyframe is an object with: "
                                                  + "delay (integer, milliseconds to wait before drawing this frame), "
                                                  + "shapes (array of shape objects, same format as tui_draw shapes). "
                                                  + "Not required when 'name' is provided."),
                        "name", propDef("string",
                                "Name of a built-in animation: "
                                                  + String.join(", ", BuiltinAnimations.names())
                                                  + ". When set, 'frames' is ignored."),
                        "autoClose", propDef("boolean",
                                "If true, close the canvas when animation finishes (default: false)")))));
        tools.add(toToolDef(toolDef(
                "tui_animate_status",
                "Check progress of a running animation. Returns animationId, status "
                                      + "(running/completed/cancelled), currentFrame, and totalFrames.",
                Map.of("animationId", propDef("string",
                        "Animation ID to check. If omitted, returns status of the latest animation.")))));

        // --- Structured data tools ---

        tools.add(toToolDef(toolDef(
                "tui_get_table",
                "Returns structured JSON table data for any tab — the primary way to read tab data. "
                                 + "Much more reliable than parsing screen text. "
                                 + "Returns tab name, rows array with all fields, totalRows, and selectedIndex. "
                                 + "Tip: call tui_get_options first to discover all available tab names "
                                 + "and their descriptions, so you pick the right tab in one call.",
                Map.of("tab", propDef("string",
                        "Tab name to get data from (e.g. 'Routes', 'Endpoints', 'Kafka'). "
                                                + "Use tui_get_options to discover available tab names. "
                                                + "If omitted, uses the active tab.")))));
        tools.add(toToolDef(toolDef(
                "tui_action",
                "Invokes a TUI action by name, bypassing fragile key sequences. "
                              + "Actions: reset-stats, reset-screen, screenshot, show-keystrokes, "
                              + "tape-recording, doctor, caption, mcp-info, mcp-log, toggle-theme.",
                Map.of("action", propDef("string", "Action name in kebab-case (e.g. 'reset-stats', 'screenshot')")),
                List.of("action"))));
        tools.add(toToolDef(toolDef(
                "tui_get_themes",
                "Returns available TUI themes grouped by dark and light, "
                                  + "with the currently active theme marked.",
                Map.of())));
        tools.add(toToolDef(toolDef(
                "tui_set_theme",
                "Switches the TUI to a named theme. Use tui_get_themes to list available theme IDs.",
                Map.of("theme", propDef("string", "Theme ID (e.g. 'dracula', 'nord', 'catppuccin-mocha')")),
                List.of("theme"))));
        tools.add(toToolDef(toolDef(
                "tui_get_log",
                "Returns recent log lines as structured data with optional filtering. "
                               + "Returns newest entries first.",
                Map.of("limit", propDef("integer", "Maximum lines to return (default 50)"),
                        "filter", propDef("string", "Case-insensitive substring filter on log message"),
                        "level", propDef("string", "Filter by log level (INFO, WARN, ERROR, DEBUG, TRACE)")))));
        tools.add(toToolDef(toolDef(
                "tui_get_errors",
                "Returns structured error data from the Errors tab. "
                                  + "Includes routeId, exchangeId, exception details, stack trace, body, and headers.",
                Map.of())));
        tools.add(toToolDef(toolDef(
                "tui_get_diagram",
                "Returns the route topology diagram as text. "
                                   + "Shows the ASCII/Unicode art diagram of routes and their connections.",
                Map.of())));
        tools.add(toToolDef(toolDef(
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
                                                       + "Otherwise returns data for the current History tab view.")))));
        tools.add(toToolDef(toolDef(
                "tui_get_topology",
                "Returns the route topology as a structured JSON graph with nodes and edges arrays. "
                                    + "Each node has: routeId, nodeType (route/external-in/external-out/trigger), "
                                    + "layer, description, from (consumer URI), exchangesTotal, exchangesFailed. "
                                    + "Each edge has: from (routeId), to (routeId), endpoint, connectionType, "
                                    + "selfLoop, backEdge. "
                                    + "Use this instead of tui_get_diagram when you need to reason about "
                                    + "route connectivity programmatically.",
                Map.of())));
        tools.add(toToolDef(toolDef(
                "tui_send_message",
                "Sends a message to a Camel endpoint in the selected integration. "
                                    + "Uses the file-based IPC protocol to deliver the message directly.",
                Map.of("endpoint", propDef("string", "Endpoint URI to send to (e.g. 'direct:myRoute', 'seda:queue')"),
                        "body", propDef("string", "Message body to send"),
                        "headers", propDef("string", "Message headers as key=value pairs separated by newlines")),
                List.of("endpoint"))));
        tools.add(toToolDef(toolDef(
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
                List.of("query"))));
        tools.add(toToolDef(toolDef(
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
                List.of("table", "primaryKeyValues", "columnValues"))));
        tools.add(toToolDef(toolDef(
                "tui_set_log_level",
                "Changes the runtime log level of the selected integration. "
                                     + "This sends a command to the running Camel application to change "
                                     + "the root logger level.",
                Map.of("level", propDef("string",
                        "Log level to set: ERROR, WARN, INFO, DEBUG, or TRACE")),
                List.of("level"))));
        tools.add(toToolDef(toolDef(
                "tui_filter",
                "Sets or clears the fuzzy text filter on a tab that supports typing-to-filter. "
                              + "Currently supported on the Classpath tab. "
                              + "Use an empty string to clear the filter.",
                Map.of("filter", propDef("string",
                        "Filter text to apply. Empty string clears the filter."),
                        "tab", propDef("string",
                                "Tab name to filter (e.g. 'Classpath'). If omitted, uses the active tab.")),
                List.of("filter"))));
        tools.add(toToolDef(toolDef(
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
                List.of("field", "value"))));
        tools.add(toToolDef(toolDef(
                "tui_toggle_trace_display",
                "Toggles which sections are visible in the History tab's detail view. "
                                            + "Controls what data is shown when inspecting trace steps or history entries.",
                Map.of("section", propDef("string",
                        "Section to toggle: headers, properties, variables, body, or wrap"),
                        "enabled", propDef("boolean",
                                "If provided, forces the section on (true) or off (false). "
                                                      + "If omitted, toggles the current state.")),
                List.of("section"))));
        tools.add(toToolDef(toolDef(
                "tui_get_readme",
                "Returns the README/documentation content from a running integration. "
                                  + "Useful for understanding what the integration does, its configuration, and usage. "
                                  + "If no name is provided, returns the README for the currently selected integration.",
                Map.of("name", propDef("string",
                        "Integration name. If omitted, uses the currently selected integration.")))));
        tools.add(toToolDef(toolDef(
                "tui_control",
                "Controls the selected integration: stop/start routes, restart, stop, or kill the process. "
                               + "Actions: stop-routes (or pause) — suspend all routes; "
                               + "start-routes (or resume) — resume all routes; "
                               + "restart — gracefully restart the integration; "
                               + "stop — gracefully stop the process; "
                               + "kill — forcefully terminate the process.",
                Map.of("action", propDef("string",
                        "Control action: stop-routes, start-routes, pause, resume, restart, stop, or kill")),
                List.of("action"))));
        tools.add(toToolDef(toolDef(
                "tui_get_files",
                "Returns source files from the selected integration's directory. "
                                 + "Without a file parameter, returns the list of files (name, size, type). "
                                 + "With a file parameter, returns the file's content. "
                                 + "Useful for reading route source code, configuration, and other integration files.",
                Map.of("name", propDef("string",
                        "Integration name. If omitted, uses the currently selected integration."),
                        "file", propDef("string",
                                "Filename to read. If omitted, returns the file list instead.")))));
        tools.add(toToolDef(toolDef(
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
                                "Maximum number of spans to return (default 500)")))));
        tools.add(toToolDef(toolDef(
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
                                "Array of diagram node IDs to locate. Returns individual rects plus combined bounds.")))));

        // --- Catalog tools ---

        tools.add(toToolDef(toolDef(
                "tui_catalog_doc",
                "Get documentation for a Camel catalog artifact (component, data format, language, EIP) "
                                   + "including description, options, and Maven coordinates. "
                                   + "Use optionsFilter to search options by keyword (e.g., 'security', 'ssl', 'timeout'). "
                                   + "This enables queries like 'what options are there on kafka about security'. "
                                   + "Set includeDoc=true to get the full AsciiDoc documentation for deep-dive questions. "
                                   + "Uses the Camel version from the selected integration.",
                Map.of("name", propDef("string",
                        "Artifact name (e.g., kafka, json-jackson, simple, timer, choice, split)"),
                        "kind", propDef("string",
                                "Artifact kind: component, dataformat, language, or eip. "
                                                  + "If omitted, auto-detects by trying component first, then dataformat, then language, then eip."),
                        "includeOptions", propDef("boolean",
                                "Whether to include configuration options in the response (default: true). "
                                                             + "Set to false for a lightweight response with just metadata."),
                        "includeDoc", propDef("boolean",
                                "Whether to include the full AsciiDoc documentation text in the response (default: false). "
                                                         + "Useful for deep-dive questions about usage, examples, and configuration patterns."),
                        "optionsFilter", propDef("string",
                                "Filter options by keyword in name or description (case-insensitive substring match). "
                                                           + "Only used when includeOptions is true.")),
                List.of("name"))));

        // --- Example tools ---

        tools.add(toToolDef(toolDef(
                "tui_list_examples",
                "Returns the list of available bundled Camel examples as structured JSON. "
                                     + "Each example has: name, title, description, level, category, tags, "
                                     + "bundled, requiresDocker, infraServices. "
                                     + "Use the 'name' field with tui_run_example to launch one.",
                Map.of("filter", propDef("string",
                        "Case-insensitive substring filter on name, title, description, level, or tags"),
                        "level", propDef("string",
                                "Filter by difficulty level: beginner, intermediate, or advanced")))));
        tools.add(toToolDef(toolDef(
                "tui_run_example",
                "Launches a named bundled example as a background process. "
                                   + "Bypasses the F2 menu entirely — no UI navigation needed. "
                                   + "Automatically starts required infra services (Docker containers) if needed. "
                                   + "Use tui_list_examples to discover available example names.",
                Map.of("name", propDef("string",
                        "Example name from the catalog (e.g. 'beginner/timer-log', 'ai/ollama')"),
                        "profile", propDef("string",
                                "Camel profile to use (e.g. 'dev'). Optional.")),
                List.of("name"))));

        return List.copyOf(tools);
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
               + "tape-recording, doctor, caption, mcp-info, mcp-log, toggle-theme";
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

        String version = facade.getSelectedCamelVersion();
        try {
            CamelCatalog catalog = CatalogLoader.loadCatalog(null, version, true);
            if (catalog == null) {
                return "{\"error\": \"Could not load catalog" + (version != null ? " for version " + version : "") + "\"}";
            }
            return buildCatalogDocResult(catalog, name, kind, optionsFilter, includeOptions, includeDoc);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.put("error", "Failed to load catalog: " + e.getMessage());
            return Jsoner.serialize(err);
        }
    }

    private String buildCatalogDocResult(
            CamelCatalog catalog, String name, String kind, String optionsFilter,
            boolean includeOptions, boolean includeDoc) {
        String lowerFilter = optionsFilter != null ? optionsFilter.toLowerCase() : null;

        if (kind == null || "component".equals(kind)) {
            ComponentModel cm = catalog.componentModel(name);
            if (cm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-component") : null;
                return buildComponentDocJson(cm, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return "{\"error\": \"Component not found: " + name + "\"}";
            }
        }
        if (kind == null || "dataformat".equals(kind)) {
            DataFormatModel dm = catalog.dataFormatModel(name);
            if (dm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-dataformat") : null;
                return buildDataFormatDocJson(dm, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return "{\"error\": \"Data format not found: " + name + "\"}";
            }
        }
        if (kind == null || "language".equals(kind)) {
            LanguageModel lm = catalog.languageModel(name);
            if (lm != null) {
                String doc = includeDoc ? catalog.asciiDoc(name + "-language") : null;
                return buildLanguageDocJson(lm, lowerFilter, includeOptions, doc);
            }
            if (kind != null) {
                return "{\"error\": \"Language not found: " + name + "\"}";
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
        return "{\"error\": \"Artifact not found: " + name + "\"}";
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

    private String buildLanguageDocJson(LanguageModel model, String filter, boolean includeOptions, String doc) {
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

    private static String actionKeys(int index, int totalActions) {
        StringBuilder sb = new StringBuilder("F2");
        for (int i = 0; i < index; i++) {
            sb.append(",Down");
        }
        sb.append(",Enter");
        return sb.toString();
    }

    private static JsonArray toJsonArray(List<String> list) {
        JsonArray arr = new JsonArray();
        arr.addAll(list);
        return arr;
    }

    // --- Tool definition helpers ---

    private static JsonObject toolDef(String name, String description, Map<String, JsonObject> properties) {
        return toolDef(name, description, properties, List.of());
    }

    private static JsonObject toolDef(
            String name, String description, Map<String, JsonObject> properties, List<String> required) {
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

    private static JsonObject propDef(String type, String description) {
        JsonObject prop = new JsonObject();
        prop.put("type", type);
        prop.put("description", description);
        return prop;
    }

    private static ToolDef toToolDef(JsonObject json) {
        return new ToolDef(
                (String) json.get("name"),
                (String) json.get("description"),
                (JsonObject) json.get("inputSchema"));
    }
}
