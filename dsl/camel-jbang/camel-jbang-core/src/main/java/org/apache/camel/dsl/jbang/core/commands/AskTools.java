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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared tool definitions and execution logic for the Camel AI assistant. Used by both the {@code camel ask} CLI
 * command and the TUI AI panel.
 */
public class AskTools {

    private static final String NO_PROCESS
            = "No running Camel process connected. Start one with: camel run <file>";

    private long targetPid;
    private CamelCatalog catalog;
    private volatile List<JsonObject> commandMetadataCache;

    public AskTools(long targetPid) {
        this.targetPid = targetPid;
    }

    public long getTargetPid() {
        return targetPid;
    }

    public void setTargetPid(long targetPid) {
        this.targetPid = targetPid;
    }

    // ---- Tool definitions ----

    public List<LlmClient.ToolDef> buildToolDefinitions() {
        List<LlmClient.ToolDef> tools = new ArrayList<>();

        tools.add(new LlmClient.ToolDef(
                "list_processes",
                "List all running Camel processes with their PID and name. Use this to discover available processes before selecting one.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "select_process",
                "Select a running Camel process by name or PID to inspect. Required when multiple processes are running. After selection, all runtime tools (get_routes, get_context, etc.) will target this process.",
                objectParams(Map.of(
                        "name", stringProp("Name or PID of the Camel process to connect to")))));

        tools.add(new LlmClient.ToolDef(
                "get_context",
                "Get Camel context info: name, version, state, uptime, route count, exchange statistics.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_routes",
                "List all routes with their state, uptime, messages processed, last error, and throughput.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_health",
                "Get health check status for the Camel application.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_endpoints",
                "List all endpoints registered in the Camel context with URIs and usage stats.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_inflight",
                "Show currently in-flight exchanges (messages being processed).",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_blocked",
                "Show blocked exchanges that are stuck or waiting.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_consumers",
                "Show consumer statistics (polling and event-driven consumers).",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_properties",
                "Show configuration properties of the running Camel application.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_memory",
                "Show JVM memory usage (heap/non-heap), garbage collection stats, and thread counts.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_errors",
                "Get captured routing errors from the running Camel application. Returns error details including exception, exchange context, and route information.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_history",
                "Get the message history trace of the last completed exchange. Shows the route path, processors visited, headers, body, and timing.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_variables",
                "Show exchange variables in the Camel context.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_services",
                "Show services registered in the Camel service registry.",
                emptyParams()));

        tools.add(new LlmClient.ToolDef(
                "get_route_source",
                "Get the source code of routes. Use filter to limit by filename (supports wildcards).",
                objectParams(Map.of(
                        "filter", stringProp("Filter source files by name (supports wildcards). Use * for all.")))));
        tools.add(new LlmClient.ToolDef(
                "get_route_dump",
                "Dump route definitions in XML or YAML format.",
                objectParams(Map.of(
                        "routeId", stringProp("Route ID to dump (use * for all routes)"),
                        "format", stringProp("Output format: xml or yaml (default: yaml)")))));
        tools.add(new LlmClient.ToolDef(
                "get_route_structure",
                "Show the route structure as a tree of processors.",
                objectParams(Map.of(
                        "routeId", stringProp("Route ID to inspect (use * for all routes)")))));
        tools.add(new LlmClient.ToolDef(
                "get_top_processors",
                "Show top processor statistics: which processors are slowest and most active.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "get_route_topology",
                "Get the inter-route topology showing how routes connect to each other and to external endpoints.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "trace_control",
                "Enable, disable, or dump message tracing.",
                objectParams(Map.of(
                        "action", stringProp("Action: enable, disable, or dump")))));

        tools.add(new LlmClient.ToolDef(
                "send_message",
                "Send a test message to a Camel endpoint in the running application.",
                objectParams(Map.of(
                        "endpoint", stringProp("Endpoint URI to send to (e.g., direct:myRoute, seda:queue)"),
                        "body", stringProp("Message body to send"),
                        "headers", stringProp("Message headers as key=value pairs separated by newlines")))));
        tools.add(new LlmClient.ToolDef(
                "eval_expression",
                "Evaluate a Camel expression in the given language (e.g., simple, jsonpath, xpath) against the running context.",
                objectParams(Map.of(
                        "language", stringProp("Expression language (e.g., simple, jsonpath, xpath, jq)"),
                        "expression", stringProp("Expression to evaluate")))));
        tools.add(new LlmClient.ToolDef(
                "browse_endpoint",
                "Browse messages in a Camel endpoint (e.g., browse messages queued in a SEDA endpoint).",
                objectParams(Map.of(
                        "endpoint", stringProp("Endpoint URI to browse (e.g., seda:queue)"),
                        "limit", stringProp("Maximum number of messages to return (default: 50)")))));
        tools.add(new LlmClient.ToolDef(
                "get_sql_trace",
                "Get traced SQL query executions flowing through camel-sql and camel-jdbc components. "
                                 + "Returns per-query timing, row counts, category (SELECT/INSERT/UPDATE/DELETE), "
                                 + "route ID, and failure status. Includes summary statistics: total queries, "
                                 + "average time, slowest time, slow count (>=100ms), and failed count. "
                                 + "Use to identify slow queries, fastest queries, most frequent queries, "
                                 + "and failed SQL executions.",
                emptyParams()));
        tools.add(new LlmClient.ToolDef(
                "execute_sql",
                "Execute a SQL query against a DataSource in the running Camel application. "
                               + "Returns structured JSON with columns, rows, and metadata for SELECT queries, "
                               + "or an update count for INSERT/UPDATE/DELETE.",
                objectParams(Map.of(
                        "query", stringProp("The SQL query to execute"),
                        "datasource", stringProp("Name of the DataSource bean (auto-detected if only one exists)"),
                        "maxRows", stringProp("Maximum number of rows to return (default: 100)")))));
        tools.add(new LlmClient.ToolDef(
                "get_thread_dump",
                "Get a JVM thread dump showing thread names, states, and stack traces.",
                emptyParams()));

        tools.add(new LlmClient.ToolDef(
                "stop_route",
                "Gracefully stop a route. The route will finish processing in-flight exchanges before stopping.",
                objectParams(Map.of(
                        "routeId", stringProp("The ID of the route to stop")))));
        tools.add(new LlmClient.ToolDef(
                "start_route",
                "Start a stopped route.",
                objectParams(Map.of(
                        "routeId", stringProp("The ID of the route to start")))));
        tools.add(new LlmClient.ToolDef(
                "suspend_route",
                "Suspend a route (pauses the consumer but keeps the route loaded).",
                objectParams(Map.of(
                        "routeId", stringProp("The ID of the route to suspend")))));
        tools.add(new LlmClient.ToolDef(
                "resume_route",
                "Resume a suspended route.",
                objectParams(Map.of(
                        "routeId", stringProp("The ID of the route to resume")))));

        tools.add(new LlmClient.ToolDef(
                "stop_application",
                "Gracefully stop the Camel application. The application will finish processing in-flight exchanges and shut down cleanly. Use this instead of kill.",
                emptyParams()));

        tools.add(new LlmClient.ToolDef(
                "catalog_components",
                "Search the Camel component catalog by name or label. Returns component name, title, description, and labels.",
                objectParams(Map.of(
                        "filter", stringProp("Filter by name, title, or description (case-insensitive substring)"),
                        "label", stringProp("Filter by category label (e.g., cloud, messaging, database, file)")))));
        tools.add(new LlmClient.ToolDef(
                "catalog_component_doc",
                "Get detailed documentation for a Camel component: URI syntax and endpoint options.",
                objectParams(Map.of(
                        "component", stringProp("Component name (e.g., kafka, http, file, timer)")))));
        tools.add(new LlmClient.ToolDef(
                "catalog_eips",
                "Search EIPs (Enterprise Integration Patterns) like split, aggregate, filter, choice, multicast.",
                objectParams(Map.of(
                        "filter", stringProp("Filter by name, title, or description (case-insensitive substring)")))));

        tools.add(new LlmClient.ToolDef(
                "list_examples",
                "List available Camel CLI examples. Returns name, title, description, difficulty level, and tags.",
                objectParams(Map.of(
                        "filter", stringProp("Filter by name, description, or tag (case-insensitive)"),
                        "level", stringProp("Filter by difficulty: beginner, intermediate, or advanced")))));
        tools.add(new LlmClient.ToolDef(
                "get_example_file",
                "Get the content of a file from a bundled Camel CLI example. Use list_examples first to find available examples.",
                objectParams(Map.of(
                        "example", stringProp("Example name (e.g., timer-log, rest-api, circuit-breaker)"),
                        "file", stringProp("File name within the example (e.g., route.camel.yaml)")))));

        tools.add(new LlmClient.ToolDef(
                "cli_list_commands",
                "List available Camel CLI commands. Returns command names and descriptions. Use filter to narrow results.",
                objectParams(Map.of(
                        "filter", stringProp("Filter by command name or description (case-insensitive substring)")))));
        tools.add(new LlmClient.ToolDef(
                "cli_command_help",
                "Get detailed help for a specific Camel CLI command, including all options with types and defaults.",
                objectParams(Map.of(
                        "command", stringProp("Full command name (e.g., 'get error', 'catalog component', 'run')")))));
        tools.add(new LlmClient.ToolDef(
                "cli_exec",
                "Execute any Camel CLI command and return its output. Use cli_list_commands and cli_command_help first to discover commands and their options. CAUTION: some commands (stop, cmd stop-route, cmd stop-group) are destructive and will affect running integrations. Always confirm with the user before executing destructive commands.",
                objectParams(Map.of(
                        "command", stringProp(
                                "The full command line to execute (e.g., 'get error --diagram', 'catalog component --filter=kafka')")))));

        tools.add(new LlmClient.ToolDef(
                "list_files",
                "List files in a directory (up to 2 levels deep). Defaults to current working directory.",
                objectParams(Map.of(
                        "path", stringProp("Directory path relative to CWD (default: current directory)")))));
        tools.add(new LlmClient.ToolDef(
                "read_file",
                "Read the content of a file. Useful for inspecting route definitions, configuration, and properties files.",
                objectParams(Map.of(
                        "file", stringProp("File path relative to CWD")))));
        tools.add(new LlmClient.ToolDef(
                "write_file",
                "Write content to a file. Creates parent directories if needed. Use for creating or editing route definitions and configuration files.",
                objectParams(Map.of(
                        "file", stringProp("File path relative to CWD"),
                        "content", stringProp("The content to write to the file")))));

        return tools;
    }

    // ---- Tool execution ----

    public String executeTool(String name, JsonObject args) {
        try {
            return switch (name) {
                case "list_processes" -> executeListProcesses();
                case "select_process" -> executeSelectProcess(args);
                case "get_context" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "context");
                case "get_routes" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "routes");
                case "get_health" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "healthChecks");
                case "get_endpoints" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "endpoints");
                case "get_inflight" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "inflight");
                case "get_blocked" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "blocked");
                case "get_consumers" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "consumers");
                case "get_properties" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "properties");
                case "get_memory" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "memory");
                case "get_errors" -> targetPid < 0 ? NO_PROCESS : executeGetErrors();
                case "get_history" -> targetPid < 0 ? NO_PROCESS : executeGetHistory();
                case "get_variables" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "variables");
                case "get_services" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "services");
                case "get_route_source" -> targetPid < 0 ? NO_PROCESS : executeRouteSource(args);
                case "get_route_dump" -> targetPid < 0 ? NO_PROCESS : executeRouteDump(args);
                case "get_route_structure" -> targetPid < 0 ? NO_PROCESS : executeRouteStructure(args);
                case "get_top_processors" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.executeAction(targetPid, "top-processors", null);
                case "get_route_topology" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.executeAction(targetPid, "route-topology", root -> {
                        root.put("metric", "true");
                        root.put("external", "true");
                    });
                case "trace_control" -> targetPid < 0 ? NO_PROCESS : executeTraceControl(args);
                case "send_message" -> targetPid < 0 ? NO_PROCESS : executeSendMessage(args);
                case "eval_expression" -> targetPid < 0 ? NO_PROCESS : executeEvalExpression(args);
                case "browse_endpoint" -> targetPid < 0 ? NO_PROCESS : executeBrowseEndpoint(args);
                case "get_sql_trace" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.readStatusSection(targetPid, "sqlTrace");
                case "execute_sql" -> targetPid < 0 ? NO_PROCESS : executeSQL(args);
                case "get_thread_dump" ->
                    targetPid < 0 ? NO_PROCESS : RuntimeHelper.executeAction(targetPid, "thread-dump", null);
                case "stop_route" -> targetPid < 0 ? NO_PROCESS : executeRouteCommand(args, "stop");
                case "start_route" -> targetPid < 0 ? NO_PROCESS : executeRouteCommand(args, "start");
                case "suspend_route" -> targetPid < 0 ? NO_PROCESS : executeRouteCommand(args, "suspend");
                case "resume_route" -> targetPid < 0 ? NO_PROCESS : executeRouteCommand(args, "resume");
                case "stop_application" -> targetPid < 0 ? NO_PROCESS : RuntimeHelper.stopApplication(targetPid);
                case "catalog_components" -> executeCatalogComponents(args);
                case "catalog_component_doc" -> executeCatalogComponentDoc(args);
                case "catalog_eips" -> executeCatalogEips(args);
                case "list_examples" -> executeListExamples(args);
                case "get_example_file" -> executeGetExampleFile(args);
                case "cli_list_commands" -> executeCliListCommands(args);
                case "cli_command_help" -> executeCliCommandHelp(args);
                case "cli_exec" -> executeCliExec(args);
                case "list_files" -> executeListFiles(args);
                case "read_file" -> executeReadFile(args);
                case "write_file" -> executeWriteFile(args);
                default -> "Unknown tool: " + name;
            };
        } catch (Exception e) {
            return "Error executing " + name + ": " + e.getMessage();
        }
    }

    // ---- System prompt ----

    public static String buildSystemPrompt(long targetPid, String processName) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an Apache Camel assistant. ");
        sb.append("You help users build, understand, and troubleshoot Camel applications.\n\n");

        if (targetPid >= 0 && processName != null) {
            sb.append("You are connected to a running Camel application: ");
            sb.append(processName).append(" (PID ").append(targetPid).append("). ");
            sb.append("Use the runtime inspection tools to gather information about it.\n\n");
        } else {
            List<RuntimeHelper.ProcessInfo> available = RuntimeHelper.discoverProcesses();
            if (!available.isEmpty()) {
                sb.append("No Camel process is currently selected. ");
                sb.append("Use list_processes to see available processes, then select_process to connect to one. ");
                sb.append("Runtime inspection tools will not work until a process is selected.\n\n");
            }
        }

        sb.append("You can search the Camel catalog (components, EIPs), browse examples, ");
        sb.append("read/write files, and execute any Camel CLI command.\n\n");
        sb.append("For CLI commands beyond the built-in tools, use cli_list_commands to discover ");
        sb.append("available commands, cli_command_help to see options, and cli_exec to run them.\n\n");
        sb.append("Guidelines:\n");
        sb.append("- When creating routes, use YAML DSL format (Camel's recommended format for JBang)\n");
        sb.append("- Look at existing files first with list_files/read_file before creating new ones\n");
        sb.append("- Use catalog tools to look up component syntax before writing routes\n");
        sb.append("- Use examples as reference when building new routes\n");
        sb.append("- Be concise and actionable in your answers\n");
        sb.append("- Format output as plain text for terminal display, do not use markdown\n");
        if (targetPid >= 0) {
            sb.append("- Start by gathering relevant information using the available runtime tools\n");
            sb.append("- If something looks wrong, explain what it means and suggest fixes\n");
            sb.append("- To stop routes or the application, always use the provided tools ");
            sb.append("(stop_route, stop_application) for graceful shutdown. Never suggest kill or kill -9.\n");
        }
        return sb.toString();
    }

    // ---- Runtime tool execution ----

    private String executeListProcesses() {
        List<RuntimeHelper.ProcessInfo> processes = RuntimeHelper.discoverProcesses();
        if (processes.isEmpty()) {
            return "No running Camel processes found. Start one with: camel run <file>";
        }
        JsonObject response = new JsonObject();
        response.put("count", processes.size());
        List<JsonObject> list = new ArrayList<>();
        for (RuntimeHelper.ProcessInfo p : processes) {
            JsonObject entry = new JsonObject();
            entry.put("pid", p.pid());
            entry.put("name", p.name());
            entry.put("selected", p.pid() == targetPid);
            list.add(entry);
        }
        response.put("processes", list);
        if (targetPid < 0) {
            response.put("hint", "No process selected. Use select_process to connect to one.");
        }
        return response.toJson();
    }

    private String executeSelectProcess(JsonObject args) {
        String name = args.getString("name");
        if (name == null || name.isBlank()) {
            return "Error: name or PID is required";
        }
        RuntimeHelper.ProcessInfo found = RuntimeHelper.findProcess(name);
        if (found == null) {
            List<RuntimeHelper.ProcessInfo> processes = RuntimeHelper.discoverProcesses();
            if (processes.isEmpty()) {
                return "No running Camel processes found.";
            }
            StringBuilder sb = new StringBuilder("No process found matching: " + name + ". Available:\n");
            processes.forEach(p -> sb.append("  ").append(p.name()).append(" (PID ").append(p.pid()).append(")\n"));
            return sb.toString();
        }
        targetPid = found.pid();
        return "Connected to " + found.name() + " (PID " + found.pid() + "). Runtime tools are now active.";
    }

    private String executeRouteSource(JsonObject args) {
        String filter = args.getString("filter");
        return RuntimeHelper.executeAction(targetPid, "source",
                root -> root.put("filter", filter != null ? filter : "*"));
    }

    private String executeRouteDump(JsonObject args) {
        String routeId = args.getString("routeId");
        String format = args.getString("format");
        return RuntimeHelper.executeAction(targetPid, "route-dump", root -> {
            root.put("id", routeId != null ? routeId : "*");
            root.put("format", format != null ? format : "yaml");
        });
    }

    private String executeRouteStructure(JsonObject args) {
        String routeId = args.getString("routeId");
        return RuntimeHelper.executeAction(targetPid, "route-structure",
                root -> root.put("id", routeId != null ? routeId : "*"));
    }

    private String executeTraceControl(JsonObject args) {
        String action = args.getString("action");
        if (action == null) {
            return "Error: action is required (enable, disable, dump)";
        }
        return RuntimeHelper.executeAction(targetPid, "trace", root -> {
            switch (action.toLowerCase()) {
                case "enable" -> root.put("enabled", "true");
                case "disable" -> root.put("enabled", "false");
                case "dump" -> root.put("dump", "true");
                default -> root.put("enabled", action);
            }
        });
    }

    private String executeRouteCommand(JsonObject args, String command) {
        String routeId = args.getString("routeId");
        if (routeId == null || routeId.isBlank()) {
            return "Error: routeId is required";
        }
        return RuntimeHelper.executeAction(targetPid, "route", root -> {
            root.put("id", routeId);
            root.put("command", command);
        });
    }

    private String executeGetErrors() {
        JsonObject errors = RuntimeHelper.readErrorFile(targetPid);
        if (errors == null) {
            return "No errors captured.";
        }
        return errors.toJson();
    }

    private String executeGetHistory() {
        JsonObject history = RuntimeHelper.readHistoryFile(targetPid);
        if (history == null) {
            return "No message history available.";
        }
        return history.toJson();
    }

    private String executeSendMessage(JsonObject args) {
        String endpoint = args.getString("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return "Error: endpoint is required";
        }
        String body = args.getString("body");
        String headers = args.getString("headers");
        JsonObject result = RuntimeHelper.sendMessage(targetPid, endpoint, body, headers);
        return result.toJson();
    }

    private String executeEvalExpression(JsonObject args) {
        String language = args.getString("language");
        String expression = args.getString("expression");
        if (language == null || language.isBlank()) {
            return "Error: language is required";
        }
        if (expression == null || expression.isBlank()) {
            return "Error: expression is required";
        }
        return RuntimeHelper.executeAction(targetPid, "eval", root -> {
            root.put("language", language);
            root.put("predicate", "false");
            root.put("template", Jsoner.escape(expression));
        });
    }

    private String executeBrowseEndpoint(JsonObject args) {
        String endpoint = args.getString("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return "Error: endpoint is required";
        }
        String limitStr = args.getString("limit");
        int limit = 50;
        if (limitStr != null && !limitStr.isBlank()) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                // use default
            }
        }
        int browseLimit = limit;
        return RuntimeHelper.executeAction(targetPid, "browse", root -> {
            root.put("filter", endpoint);
            root.put("limit", browseLimit);
        });
    }

    private String executeSQL(JsonObject args) {
        String sql = args.getString("query");
        if (sql == null || sql.isBlank()) {
            return "Error: 'query' parameter is required";
        }
        String datasource = args.getString("datasource");
        int maxRows = 100;
        String maxRowsStr = args.getString("maxRows");
        if (maxRowsStr != null) {
            try {
                maxRows = Integer.parseInt(maxRowsStr);
            } catch (NumberFormatException e) {
                // use default
            }
        }
        JsonObject result = RuntimeHelper.executeSqlQuery(targetPid, sql, datasource, maxRows, 30);
        return Jsoner.serialize(result);
    }

    // ---- Catalog tools ----

    private String executeCatalogComponents(JsonObject args) {
        String filter = args.getString("filter");
        String label = args.getString("label");
        CamelCatalog cat = getCatalog();

        List<JsonObject> results = cat.findComponentNames().stream()
                .map(cat::componentModel)
                .filter(m -> m != null)
                .filter(m -> matchesFilter(m.getScheme(), m.getTitle(), m.getDescription(), filter))
                .filter(m -> label == null || label.isBlank()
                        || (m.getLabel() != null && m.getLabel().toLowerCase().contains(label.toLowerCase())))
                .limit(20)
                .map(m -> {
                    JsonObject jo = new JsonObject();
                    jo.put("name", m.getScheme());
                    jo.put("title", m.getTitle());
                    jo.put("description", m.getDescription());
                    jo.put("label", m.getLabel());
                    return jo;
                })
                .collect(Collectors.toList());

        JsonObject response = new JsonObject();
        response.put("count", results.size());
        response.put("components", results);
        return response.toJson();
    }

    private String executeCatalogComponentDoc(JsonObject args) {
        String component = args.getString("component");
        if (component == null || component.isBlank()) {
            return "Error: component name is required";
        }
        CamelCatalog cat = getCatalog();
        ComponentModel model = cat.componentModel(component);
        if (model == null) {
            return "Component not found: " + component;
        }

        JsonObject response = new JsonObject();
        response.put("name", model.getScheme());
        response.put("title", model.getTitle());
        response.put("description", model.getDescription());
        response.put("syntax", model.getSyntax());
        response.put("consumerOnly", model.isConsumerOnly());
        response.put("producerOnly", model.isProducerOnly());

        List<JsonObject> options = new ArrayList<>();
        if (model.getEndpointOptions() != null) {
            model.getEndpointOptions().stream()
                    .filter(opt -> !opt.isDeprecated())
                    .forEach(opt -> {
                        JsonObject jo = new JsonObject();
                        jo.put("name", opt.getName());
                        jo.put("description", opt.getDescription());
                        jo.put("type", opt.getType());
                        jo.put("required", opt.isRequired());
                        if (opt.getDefaultValue() != null) {
                            jo.put("defaultValue", opt.getDefaultValue().toString());
                        }
                        options.add(jo);
                    });
        }
        response.put("options", options);
        return response.toJson();
    }

    private String executeCatalogEips(JsonObject args) {
        String filter = args.getString("filter");
        CamelCatalog cat = getCatalog();

        List<JsonObject> results = cat.findModelNames().stream()
                .map(cat::eipModel)
                .filter(m -> m != null)
                .filter(m -> matchesFilter(m.getName(), m.getTitle(), m.getDescription(), filter))
                .limit(20)
                .map(m -> {
                    JsonObject jo = new JsonObject();
                    jo.put("name", m.getName());
                    jo.put("title", m.getTitle());
                    jo.put("description", m.getDescription());
                    jo.put("label", m.getLabel());
                    return jo;
                })
                .collect(Collectors.toList());

        JsonObject response = new JsonObject();
        response.put("count", results.size());
        response.put("eips", results);
        return response.toJson();
    }

    // ---- Example tools ----

    @SuppressWarnings("unchecked")
    private String executeListExamples(JsonObject args) {
        String filter = args.getString("filter");
        String level = args.getString("level");

        List<JsonObject> catalog2 = ExampleHelper.loadCatalog();
        List<JsonObject> filtered = ExampleHelper.filterExamples(catalog2, filter);

        List<JsonObject> results = new ArrayList<>();
        for (JsonObject entry : filtered) {
            if (level != null && !level.isBlank()) {
                String entryLevel = entry.getString("level");
                if (entryLevel == null || !entryLevel.equalsIgnoreCase(level)) {
                    continue;
                }
            }
            JsonObject jo = new JsonObject();
            jo.put("name", entry.getString("name"));
            jo.put("title", entry.getString("title"));
            jo.put("description", entry.getString("description"));
            jo.put("level", entry.getString("level"));
            jo.put("tags", entry.get("tags"));
            jo.put("bundled", ExampleHelper.isBundled(entry));
            jo.put("files", ExampleHelper.getFiles(entry));
            results.add(jo);

            if (results.size() >= 20) {
                break;
            }
        }

        JsonObject response = new JsonObject();
        response.put("count", results.size());
        response.put("examples", results);
        return response.toJson();
    }

    private String executeGetExampleFile(JsonObject args) {
        String example = args.getString("example");
        String file = args.getString("file");
        if (example == null || example.isBlank()) {
            return "Error: example name is required";
        }
        if (file == null || file.isBlank()) {
            return "Error: file name is required";
        }

        List<JsonObject> catalog2 = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog2, example);
        if (entry == null) {
            return "Example not found: " + example;
        }

        List<String> files = ExampleHelper.getFiles(entry);
        if (!files.contains(file)) {
            return "File '" + file + "' not found in example '" + example + "'. Available files: " + files;
        }

        if (ExampleHelper.isBundled(entry)) {
            String resourcePath = "examples/" + example + "/" + file;
            try (InputStream is = ExampleHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    return IOHelper.loadText(is);
                }
            } catch (Exception e) {
                return "Error reading file: " + e.getMessage();
            }
            return "Could not read bundled file: " + resourcePath;
        } else {
            return "This example is not bundled. View it on GitHub: " + ExampleHelper.getGithubUrl(entry) + "/" + file;
        }
    }

    // ---- CLI tools ----

    @SuppressWarnings("unchecked")
    private List<JsonObject> loadCommandMetadata() {
        if (commandMetadataCache != null) {
            return commandMetadataCache;
        }
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/camel-jbang-commands-metadata.json")) {
            if (is == null) {
                return List.of();
            }
            String json = IOHelper.loadText(is);
            JsonObject root = (JsonObject) Jsoner.deserialize(json);
            Object commands = root.get("commands");
            if (commands instanceof Collection<?>) {
                commandMetadataCache = ((Collection<Object>) commands).stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .toList();
                return commandMetadataCache;
            }
        } catch (Exception e) {
            // ignore
        }
        return List.of();
    }

    private String executeCliListCommands(JsonObject args) {
        String filter = args.getString("filter");
        List<JsonObject> commands = loadCommandMetadata();
        List<JsonObject> result = new ArrayList<>();
        Ask.collectCommands(commands, result, filter);

        JsonObject response = new JsonObject();
        response.put("count", result.size());
        response.put("commands", result);
        return response.toJson();
    }

    @SuppressWarnings("unchecked")
    private String executeCliCommandHelp(JsonObject args) {
        String command = args.getString("command");
        if (command == null || command.isBlank()) {
            return "Error: command name is required";
        }

        List<JsonObject> commands = loadCommandMetadata();
        JsonObject cmd = Ask.findCommand(commands, command);
        if (cmd == null) {
            return "Command not found: " + command + ". Use cli_list_commands to see available commands.";
        }

        JsonObject response = new JsonObject();
        response.put("command", cmd.getString("fullName"));
        response.put("description", cmd.getString("description"));

        Object options = cmd.get("options");
        if (options instanceof Collection<?>) {
            List<JsonObject> opts = ((Collection<Object>) options).stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(opt -> {
                        JsonObject o = new JsonObject();
                        o.put("names", opt.getString("names"));
                        o.put("description", opt.getString("description"));
                        o.put("type", opt.getString("type"));
                        String dv = opt.getString("defaultValue");
                        if (dv != null) {
                            o.put("defaultValue", dv);
                        }
                        return o;
                    })
                    .toList();
            response.put("options", opts);
        }

        Object subs = cmd.get("subcommands");
        if (subs instanceof Collection<?> subList && !subList.isEmpty()) {
            List<JsonObject> subSummaries = ((Collection<Object>) subList).stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(sub -> {
                        JsonObject s = new JsonObject();
                        s.put("command", sub.getString("fullName"));
                        s.put("description", sub.getString("description"));
                        return s;
                    })
                    .toList();
            response.put("subcommands", subSummaries);
        }

        return response.toJson();
    }

    private String executeCliExec(JsonObject args) {
        String command = args.getString("command");
        if (command == null || command.isBlank()) {
            return "Error: command is required";
        }

        picocli.CommandLine commandLine = CamelJBangMain.getCommandLine();
        if (commandLine == null) {
            return "Error: CLI not available";
        }

        String[] cmdArgs = Ask.tokenizeCommand(command.trim());

        StringBuilder captured = new StringBuilder();
        Printer capturingPrinter = new Printer() {
            @Override
            public void println() {
                captured.append('\n');
            }

            @Override
            public void println(String line) {
                captured.append(line).append('\n');
            }

            @Override
            public void print(String output) {
                captured.append(output);
            }

            @Override
            public void printf(String format, Object... fmtArgs) {
                captured.append(String.format(format, fmtArgs));
            }
        };

        CamelJBangMain main = (CamelJBangMain) commandLine.getCommand();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        PrintWriter originalOut = commandLine.getOut();
        PrintWriter originalErr = commandLine.getErr();
        commandLine.setOut(pw);
        commandLine.setErr(pw);

        Printer originalPrinter = main.getOut();
        main.setOut(capturingPrinter);
        try {
            int exitCode = commandLine.execute(cmdArgs);
            pw.flush();
            String output = captured.toString() + sw.toString();
            if (output.isBlank() && exitCode != 0) {
                return "Command exited with code " + exitCode;
            }
            if (output.length() > 32768) {
                output = output.substring(0, 32768) + "\n... (truncated)";
            }
            return output;
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        } finally {
            main.setOut(originalPrinter);
            commandLine.setOut(originalOut);
            commandLine.setErr(originalErr);
        }
    }

    // ---- File tools ----

    private String executeListFiles(JsonObject args) throws IOException {
        String pathStr = args.getString("path");
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path base = cwd.resolve(pathStr != null && !pathStr.isBlank() ? pathStr : ".").normalize();

        if (!base.startsWith(cwd)) {
            return "Error: path must be within the current working directory";
        }
        if (!Files.isDirectory(base)) {
            return "Error: not a directory: " + cwd.relativize(base);
        }

        try (Stream<Path> stream = Files.walk(base, 2)) {
            List<String> files = stream
                    .filter(p -> !p.equals(base))
                    .map(p -> cwd.relativize(p).toString() + (Files.isDirectory(p) ? "/" : ""))
                    .sorted()
                    .toList();

            JsonObject response = new JsonObject();
            response.put("directory", base.equals(cwd) ? "." : cwd.relativize(base).toString());
            response.put("count", files.size());
            response.put("files", files);
            return response.toJson();
        }
    }

    private String executeReadFile(JsonObject args) throws IOException {
        String fileStr = args.getString("file");
        if (fileStr == null || fileStr.isBlank()) {
            return "Error: file path is required";
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path filePath = cwd.resolve(fileStr).normalize();

        if (!filePath.startsWith(cwd)) {
            return "Error: file path must be within the current working directory";
        }
        if (!Files.exists(filePath)) {
            return "File not found: " + cwd.relativize(filePath);
        }

        String content = Files.readString(filePath);
        if (content.length() > 32768) {
            content = content.substring(0, 32768) + "\n... (truncated, file is " + content.length() + " bytes)";
        }
        return content;
    }

    private String executeWriteFile(JsonObject args) throws IOException {
        String fileStr = args.getString("file");
        String content = args.getString("content");
        if (fileStr == null || fileStr.isBlank()) {
            return "Error: file path is required";
        }
        if (content == null) {
            return "Error: content is required";
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path filePath = cwd.resolve(fileStr).normalize();

        if (!filePath.startsWith(cwd)) {
            return "Error: file path must be within the current working directory";
        }

        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
        return "File written: " + cwd.relativize(filePath);
    }

    private CamelCatalog getCatalog() {
        if (catalog == null) {
            catalog = new DefaultCamelCatalog();
        }
        return catalog;
    }

    // ---- JSON schema helpers ----

    public static JsonObject emptyParams() {
        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        schema.put("properties", new JsonObject());
        return schema;
    }

    public static JsonObject objectParams(Map<String, JsonObject> properties) {
        JsonObject props = new JsonObject();
        Map<String, JsonObject> ordered = new LinkedHashMap<>(properties);
        for (Map.Entry<String, JsonObject> entry : ordered.entrySet()) {
            props.put(entry.getKey(), entry.getValue());
        }
        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    public static JsonObject stringProp(String description) {
        JsonObject prop = new JsonObject();
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }

    static boolean matchesFilter(String name, String title, String description, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String lf = filter.toLowerCase();
        return (name != null && name.toLowerCase().contains(lf))
                || (title != null && title.toLowerCase().contains(lf))
                || (description != null && description.toLowerCase().contains(lf));
    }
}
