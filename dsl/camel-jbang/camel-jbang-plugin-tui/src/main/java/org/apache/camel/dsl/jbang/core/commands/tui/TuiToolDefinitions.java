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

import org.apache.camel.dsl.jbang.core.commands.tui.TuiToolRegistry.ToolDef;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The MCP tool definitions (name, description and JSON input schema) exposed by the TUI. This class is pure data; the
 * matching implementations live in {@link TuiToolRegistry} and are dispatched by tool name, so a new tool needs an
 * entry here and a {@code call*} method there.
 */
final class TuiToolDefinitions {

    private TuiToolDefinitions() {
    }

    /**
     * Returns all tool definitions in registry order.
     */
    static List<ToolDef> all() {
        List<ToolDef> tools = new ArrayList<>();
        addInteractionTools(tools);
        addStructuredDataTools(tools);
        addCatalogTools(tools);
        addLogTools(tools);
        addExampleTools(tools);
        return List.copyOf(tools);
    }

    /**
     * Tools that read or drive the screen: screen text, events, state, captions, navigation, key presses, waiting, tape
     * recording, drawing, themes and actions.
     */
    private static void addInteractionTools(List<ToolDef> tools) {
        tools.add(toToolDef(toolDef(
                "tui_get_screen",
                "Returns the current TUI screen content as text. "
                                  + "Shows exactly what the user sees in their terminal. "
                                  + "Use ansi=true to include ANSI color codes for color-related questions. "
                                  + "Also returns a 'selection' field with structured metadata about the active list/table "
                                  + "(type, items, selectedIndex, totalItems, label) when available. "
                                  + "On tabs with master/detail panels, includes 'detailFocused' (true=detail, false=table).",
                Map.of("ansi", propDef("boolean", "Include ANSI color codes in the output (default false)")))));
        tools.add(toToolDef(toolDef(
                "tui_get_events",
                "Returns recent user input events (key presses, navigation). "
                                  + "Each event has a key, human-readable label, and timestamp.",
                Map.of("limit", propDef("integer", "Maximum number of events to return (default 50)")))));
        tools.add(toToolDef(toolDef(
                "tui_get_state",
                "Current TUI navigation state: active tab, selected integration and PID, integration count, the "
                                 + "active list/table selection, overlay flags and (on master/detail tabs) which "
                                 + "panel has focus.",
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
                "Changes what the user sees: switch tab, select an integration, select a route in the Diagram "
                                + "tab, or drill into a processor node. Every parameter is optional. Returns the screen "
                                + "and selection afterwards. Do not use it just to read data; the tui_get_* tools do that.",
                Map.of("tab", propDef("string", "Tab to switch to, e.g. Routes, Log, Diagram (see tui_get_options)"),
                        "integration", propDef("string", "Integration name or PID to select"),
                        "route", propDef("string", "Route ID to select in the Diagram tab"),
                        "node", propDef("string",
                                "Processor/EIP node ID to select inside the route (drills into 'route' first when given)")))));

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
                "Lists every tab with a description of the data it provides, plus the running integrations. "
                                   + "Use it when unsure which tab holds the data for a question (e.g. 'kafka offset' "
                                   + "-> Kafka tab), then read that tab with tui_get_table.",
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
                "Draws an overlay on top of the TUI screen in one call: many shapes (batch of the tui_draw_shape "
                            + "parameters) and/or individual characters, including emoji. Coordinates are 0-based and "
                            + "match tui_get_screen. Use with tui_show_caption to explain what you drew.",
                Map.of("cells", propDef("array",
                        "Cell objects: x, y, char, optional fg/bg color name, optional bold"),
                        "shapes", propDef("array",
                                "Shape objects with the same fields as tui_draw_shape: shape, x, y, width, height, "
                                                   + "length, text, color"),
                        "duration", propDef("integer",
                                "Auto-dismiss after this many seconds; otherwise stays until tui_draw_clear or the next tui_draw"),
                        "append", propDef("boolean",
                                "Add to the existing drawing instead of replacing it (default false)")),
                List.of())));
        tools.add(toToolDef(toolDef(
                "tui_draw_clear",
                "Clears the drawing overlay and restores the screen to its normal state. "
                                  + "The underlying content is unchanged since drawing is an overlay.",
                Map.of())));

        tools.add(toToolDef(toolDef(
                "tui_draw_shape",
                "Draws one shape on the TUI screen overlay. Combine with tui_locate for precise positioning.",
                Map.of("shape", propDef("string",
                        "box (border), highlight (marker-pen background), underline, arrow-down, arrow-up, "
                                                  + "arrow-left, arrow-right, or text"),
                        "x", propDef("integer", "X coordinate (column) of the shape origin"),
                        "y", propDef("integer", "Y coordinate (row) of the shape origin"),
                        "width", propDef("integer", "Width of the shape (for box, highlight, underline)"),
                        "height", propDef("integer", "Height of the shape (for box, highlight). Defaults to 1."),
                        "length", propDef("integer", "Length of arrows"),
                        "text", propDef("string", "Text content to draw (for text shape)"),
                        "color", propDef("string",
                                "red, green, blue, yellow, cyan, magenta, white, gray or black (default red, "
                                                   + "yellow for highlight)"),
                        "duration", propDef("integer", "Auto-dismiss after this many seconds; otherwise stays until cleared"),
                        "append", propDef("boolean", "Add to the existing drawing instead of replacing it (default false)")),
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
    }

    /**
     * Tools that return structured data (tables, logs, errors, diagrams, history, spans, processors) and act on it.
     */
    private static void addStructuredDataTools(List<ToolDef> tools) {
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
                "tui_get_status",
                "One top-level section of the integration's full status document (~/.camel/<pid>-status.json). "
                                  + "Use it for data no tab shows: context (name, version, state, uptime in millis "
                                  + "with uptimeText human readable, startTimestamp, statistics), runtime (pid, "
                                  + "directory, java), healthChecks, "
                                  + "properties, main-configuration, routeController, services, transformers, rests, "
                                  + "consumers, producers, endpoints, dataSources, memory, threads, gc, classLoading, "
                                  + "trace, events. section='sections' lists them. Request only the section you need.",
                Map.of("section", propDef("string",
                        "Top-level section name, or 'sections' to list the available names"),
                        "pid", propDef("string",
                                "Process id of the integration; defaults to the selected integration")),
                List.of("section"))));
        tools.add(toToolDef(toolDef(
                "tui_action",
                "Invokes a TUI action by name or by its F2 menu label (as listed in tui_get_options actions), "
                              + "bypassing fragile key sequences. "
                              + "Names: reset-stats, reset-screen, screenshot, show-keystrokes, "
                              + "tape-recording, doctor, caption, mcp-info, mcp-log, toggle-theme.",
                Map.of("action", propDef("string",
                        "Action name in kebab-case (e.g. 'reset-stats') or menu label (e.g. 'Run Doctor')")),
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
                               + "Returns newest entries first. Reads the selected integration's log, or an infra "
                               + "service's log when infra=<alias> is given.",
                Map.of("limit", propDef("integer", "Maximum lines to return (default 50)"),
                        "filter", propDef("string", "Case-insensitive substring filter on log message"),
                        "level", propDef("string", "Filter by log level (INFO, WARN, ERROR, DEBUG, TRACE)"),
                        "infra", propDef("string", "Infra service alias whose log to read instead")))));
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
                "Sends a message to any Camel endpoint URI from inside the selected integration: direct:/seda: "
                                    + "to feed a route, or a producer such as paho-mqtt5:, kafka:, jms:, http:, file: "
                                    + "to publish to the system a route consumes from. A route that only consumes from "
                                    + "a broker has no direct: endpoint; publish to the broker with the same component "
                                    + "and options the route uses.",
                Map.of("endpoint", propDef("string",
                        "Endpoint URI, e.g. 'direct:myRoute', 'paho-mqtt5:temperature?brokerUrl=tcp://localhost:1883'"),
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
                                 + "HTTP probe (field='path', 'body', 'method', 'content-type', or 'accept'), "
                                 + "Spans (field='filter'), Classpath (field='filter').",
                Map.of("field", propDef("string",
                        "Field name to set: 'sql', 'path', 'body', 'method', 'content-type', 'accept', or 'filter'"),
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
                "Controls the selected integration: reset statistics, stop/start routes, restart, stop, or kill "
                               + "the process. Actions: reset-stats (or clear-stats) — clear exchange statistics, "
                               + "activity, errors and traces without touching the routes; "
                               + "stop-routes (or pause) — suspend all routes; "
                               + "start-routes (or resume) — resume all routes; "
                               + "restart — gracefully restart the integration; "
                               + "stop — gracefully stop the process; "
                               + "kill — forcefully terminate the process; "
                               + "stop-all — stop all running processes; "
                               + "close — close a phantom (opened but not running) project.",
                Map.of("action", propDef("string",
                        "Control action: reset-stats, stop-routes, start-routes, pause, resume, restart, stop, kill, "
                                                   + "stop-all, or close")),
                List.of("action"))));
        tools.add(toToolDef(toolDef(
                "tui_infra",
                "Lists and controls infra services (brokers, databases started with camel infra run, e.g. "
                             + "mosquitto, kafka, postgres). Actions: list — running services with alias, pid, "
                             + "version and connection properties; log — newest lines of a service's log; "
                             + "start, stop, restart — change state, only when the user asked.",
                Map.of("action", propDef("string", "list, log, start, stop or restart"),
                        "alias", propDef("string", "Service alias (required except for list)"),
                        "limit", propDef("integer", "log: maximum lines to return (default 50)"),
                        "filter", propDef("string", "log: case-insensitive substring filter")),
                List.of("action"))));
        tools.add(toToolDef(toolDef(
                "tui_open_project",
                "Opens a project directory as a phantom integration (shown as Stopped in Overview). "
                                    + "The project can then be browsed in the Source tab and run via tui_control. "
                                    + "Supports Maven projects (Spring Boot, Quarkus, Camel Main detected via pom.xml) "
                                    + "and flat directories with Camel route files.",
                Map.of("directory", propDef("string",
                        "Absolute path to the project directory to open")),
                List.of("directory"))));
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
    }

    /**
     * Camel catalog documentation tools.
     */
    private static void addCatalogTools(List<ToolDef> tools) {
        tools.add(toToolDef(toolDef(
                "tui_catalog_doc",
                "Camel catalog documentation for a component, data format, language or EIP: description, options "
                                   + "and Maven coordinates, for the Camel version of the selected integration. "
                                   + "Use optionsFilter for questions like 'which kafka options are about security'.",
                Map.of("name", propDef("string", "Artifact name, e.g. kafka, json-jackson, simple, timer, choice, split"),
                        "kind", propDef("string",
                                "component, dataformat, language or eip; auto-detected in that order when omitted"),
                        "includeOptions", propDef("boolean", "Include the configuration options (default true)"),
                        "includeDoc", propDef("boolean",
                                "Include the full AsciiDoc page for usage examples and patterns (default false)"),
                        "optionsFilter", propDef("string",
                                "Case-insensitive keyword to match in option names or descriptions")),
                List.of("name"))));

        tools.add(toToolDef(toolDef(
                "tui_get_processor_detail",
                "Returns configured options for all processors in a route as structured JSON. "
                                            + "Each processor entry includes type, id, endpointUri (for from/to), "
                                            + "and the configured options (attributes, expressions). "
                                            + "Use includeDocs=true to enrich the response with documentation from "
                                            + "the Camel catalog for each EIP option and component endpoint option. "
                                            + "This is the programmatic equivalent of the Diagram tab's detail panel.",
                Map.of("routeId", propDef("string",
                        "Route ID to inspect (use * for all routes). Defaults to * if omitted."),
                        "includeDocs", propDef("boolean",
                                "If true, enrich each processor's options with documentation from the Camel catalog "
                                                          + "(description, type, group, defaultValue, required, deprecated, enum values)")))));
    }

    /**
     * AI panel and MCP server activity log tools.
     */
    private static void addLogTools(List<ToolDef> tools) {
        tools.add(toToolDef(toolDef(
                "tui_get_ai_log",
                "Returns the TUI's built-in AI panel activity log as structured JSON. "
                                  + "Shows the AI panel's questions, tool calls, tool results, responses, and errors. "
                                  + "Useful to see what the built-in AI has already investigated "
                                  + "before repeating the same work.",
                Map.of("limit", propDef("integer",
                        "Maximum number of entries to return (default 50)")))));

        tools.add(toToolDef(toolDef(
                "tui_get_mcp_log",
                "Returns the TUI MCP server's tool call log as structured JSON. "
                                   + "Shows external MCP client connections and tool call requests/responses. "
                                   + "Useful for debugging and auditing MCP interactions.",
                Map.of("limit", propDef("integer",
                        "Maximum number of entries to return (default 50)")))));
    }

    /**
     * Bundled example discovery and launch tools.
     */
    private static void addExampleTools(List<ToolDef> tools) {
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
    }

    // --- Schema helpers ---

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
