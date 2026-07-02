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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor.tool;

/**
 * Central registry of all shared AI tool descriptors. Tools registered here are available to both the Agent REPL
 * ({@code AskTools}) and the MCP server. CLI-specific and file-system tools that depend on {@code CamelJBangMain} or
 * direct filesystem access remain in {@code AskTools}.
 */
public final class ToolRegistry {

    private static final List<ToolDescriptor> TOOLS = new ArrayList<>();
    private static final Map<String, ToolDescriptor> BY_NAME = new LinkedHashMap<>();

    static {
        registerProcessTools();
        registerRuntimeStatusTools();
        registerRuntimeActionTools();
        registerDevConsoleTools();
        registerAnalysisTools();
        registerCatalogTools();
        registerExampleTools();
    }

    private ToolRegistry() {
    }

    public static List<ToolDescriptor> allTools() {
        return Collections.unmodifiableList(TOOLS);
    }

    public static ToolDescriptor findTool(String name) {
        return BY_NAME.get(name);
    }

    public static Object execute(String name, ToolContext ctx, Map<String, String> args) {
        ToolDescriptor desc = BY_NAME.get(name);
        if (desc == null) {
            throw new ToolExecutionException("Unknown tool: " + name);
        }
        return desc.executor().execute(ctx, args);
    }

    private static void register(ToolDescriptor descriptor) {
        TOOLS.add(descriptor);
        BY_NAME.put(descriptor.name(), descriptor);
    }

    // ---- Process discovery and selection ----

    private static void registerProcessTools() {
        register(tool("list_processes",
                "List all running Camel processes with their PID and name. Use this to discover available processes before selecting one.")
                .executor((ctx, args) -> {
                    List<RuntimeHelper.ProcessInfo> processes = ctx.discoverProcesses();
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
                        entry.put("selected", p.pid() == ctx.pid());
                        list.add(entry);
                    }
                    response.put("processes", list);
                    if (!ctx.hasProcess()) {
                        response.put("hint", "No process selected. Use select_process to connect to one.");
                    }
                    return response.toJson();
                }));

        register(tool("select_process",
                "Select a running Camel process by name or PID to inspect. Required when multiple processes are running.")
                .param("name", "string", "Name or PID of the Camel process to connect to", true)
                .executor((ctx, args) -> {
                    String name = args.get("name");
                    if (name == null || name.isBlank()) {
                        throw new ToolExecutionException("name or PID is required");
                    }
                    RuntimeHelper.ProcessInfo found = ctx.findProcess(name);
                    if (found == null) {
                        List<RuntimeHelper.ProcessInfo> processes = ctx.discoverProcesses();
                        if (processes.isEmpty()) {
                            return "No running Camel processes found.";
                        }
                        StringBuilder sb
                                = new StringBuilder("No process found matching: " + name + ". Available:\n");
                        processes.forEach(
                                p -> sb.append("  ").append(p.name()).append(" (PID ").append(p.pid()).append(")\n"));
                        return sb.toString();
                    }
                    ctx.selectProcess(found.pid());
                    return "Connected to " + found.name() + " (PID " + found.pid()
                           + "). Runtime tools are now active.";
                }));
    }

    // ---- Runtime status (simple read-section) tools ----

    private static void registerRuntimeStatusTools() {
        registerStatusTool("get_context", "context",
                "Get Camel context info: name, version, state, uptime, route count, exchange statistics.");
        registerStatusTool("get_routes", "routes",
                "List all routes with their state, uptime, messages processed, last error, and throughput.");
        registerStatusTool("get_health", "healthChecks",
                "Get health check status for the Camel application.");
        registerStatusTool("get_endpoints", "endpoints",
                "List all endpoints registered in the Camel context with URIs and usage stats.");
        registerStatusTool("get_inflight", "inflight",
                "Show currently in-flight exchanges (messages being processed).");
        registerStatusTool("get_blocked", "blocked",
                "Show blocked exchanges that are stuck or waiting.");
        registerStatusTool("get_consumers", "consumers",
                "Show consumer statistics (polling and event-driven consumers).");
        registerStatusTool("get_properties", "properties",
                "Show configuration properties of the running Camel application.");
        // get_memory combines memory, gc, and threads sections for a complete JVM resource view
        register(tool("get_memory",
                "Show JVM memory usage (heap/non-heap), garbage collection stats, and thread counts.")
                .executor((ctx, args) -> {
                    JsonObject root = ctx.readFullStatus();
                    if (root == null) {
                        return "{}";
                    }
                    JsonObject result = new JsonObject();
                    if (root.containsKey("memory")) {
                        result.put("memory", root.get("memory"));
                    }
                    if (root.containsKey("gc")) {
                        result.put("gc", root.get("gc"));
                    }
                    if (root.containsKey("threads")) {
                        result.put("threads", root.get("threads"));
                    }
                    return result.toJson();
                }));
        registerStatusTool("get_variables", "variables",
                "Show exchange variables in the Camel context.");
        registerStatusTool("get_services", "services",
                "Show services registered in the Camel service registry.");
        registerStatusTool("get_sql_trace", "sqlTrace",
                "Get traced SQL query executions flowing through camel-sql and camel-jdbc components. "
                                                        + "Returns per-query timing, row counts, category (SELECT/INSERT/UPDATE/DELETE), "
                                                        + "route ID, and failure status. Includes summary statistics.");
    }

    private static void registerStatusTool(String name, String section, String description) {
        register(tool(name, description)
                .executor((ctx, args) -> ctx.readStatus(section)));
    }

    // ---- Runtime action tools (with parameters) ----

    private static void registerRuntimeActionTools() {
        register(tool("get_errors",
                "Get captured routing errors from the running Camel application.")
                .executor((ctx, args) -> {
                    JsonObject errors = ctx.readErrorFile();
                    return errors != null ? errors.toJson() : "No errors captured.";
                }));

        register(tool("get_history",
                "Get the message history trace of the last completed exchange.")
                .executor((ctx, args) -> {
                    JsonObject history = ctx.readHistoryFile();
                    return history != null ? history.toJson() : "No message history available.";
                }));

        register(tool("get_route_source",
                "Get the source code of routes. Use filter to limit by filename (supports wildcards).")
                .param("filter", "string",
                        "Filter source files by name (supports wildcards). Use * for all.", false)
                .executor((ctx, args) -> {
                    String filter = args.get("filter");
                    return ctx.executeAction("source",
                            root -> root.put("filter", filter != null ? filter : "*"));
                }));

        register(tool("get_route_dump",
                "Dump route definitions in XML or YAML format.")
                .param("routeId", "string", "Route ID to dump (use * for all routes)", false)
                .param("format", "string", "Output format: xml or yaml (default: yaml)", false)
                .executor((ctx, args) -> {
                    String routeId = args.get("routeId");
                    String format = args.get("format");
                    return ctx.executeAction("route-dump", root -> {
                        root.put("id", routeId != null ? routeId : "*");
                        root.put("format", format != null ? format : "yaml");
                    });
                }));

        register(tool("get_route_structure",
                "Show the route structure as a tree of processors.")
                .param("routeId", "string", "Route ID to inspect (use * for all routes)", false)
                .executor((ctx, args) -> {
                    String routeId = args.get("routeId");
                    return ctx.executeAction("route-structure",
                            root -> root.put("id", routeId != null ? routeId : "*"));
                }));

        register(tool("get_top_processors",
                "Show top processor statistics: which processors are slowest and most active.")
                .executor((ctx, args) -> ctx.executeAction("top-processors", null)));

        register(tool("get_route_topology",
                "Get the inter-route topology showing how routes connect to each other and to external endpoints.")
                .param("metric", "string", "Include live metrics on nodes and edges (default: true)", false)
                .param("external", "string", "Include external systems as nodes (default: true)", false)
                .executor((ctx, args) -> ctx.executeAction("route-topology", root -> {
                    root.put("metric", args.getOrDefault("metric", "true"));
                    root.put("external", args.getOrDefault("external", "true"));
                })));

        register(tool("trace_control",
                "Enable, disable, or dump message tracing.")
                .param("action", "string", "Action: enable, disable, or dump", true)
                .executor((ctx, args) -> {
                    String action = args.get("action");
                    if (action == null) {
                        throw new ToolExecutionException("action is required (enable, disable, dump)");
                    }
                    return ctx.executeAction("trace", root -> {
                        switch (action.toLowerCase()) {
                            case "enable" -> root.put("enabled", "true");
                            case "disable" -> root.put("enabled", "false");
                            case "dump" -> root.put("dump", "true");
                            default -> throw new ToolExecutionException(
                                    "Unknown trace action: " + action + ". Use 'enable', 'disable', or 'dump'.");
                        }
                    });
                }));

        register(tool("send_message",
                "Send a test message to a Camel endpoint in the running application.")
                .param("endpoint", "string",
                        "Endpoint URI to send to (e.g., direct:myRoute, seda:queue)", true)
                .param("body", "string", "Message body to send", false)
                .param("headers", "string",
                        "Message headers as key=value pairs separated by newlines", false)
                .readOnly(false).destructive(false)
                .executor((ctx, args) -> {
                    String endpoint = args.get("endpoint");
                    if (endpoint == null || endpoint.isBlank()) {
                        throw new ToolExecutionException("endpoint is required");
                    }
                    return ctx.sendMessage(endpoint, args.get("body"), args.get("headers")).toJson();
                }));

        register(tool("eval_expression",
                "Evaluate a Camel expression in the given language against the running context.")
                .param("language", "string",
                        "Expression language (e.g., simple, jsonpath, xpath, jq)", true)
                .param("expression", "string", "Expression to evaluate", true)
                .executor((ctx, args) -> {
                    String language = args.get("language");
                    String expression = args.get("expression");
                    if (language == null || language.isBlank()) {
                        throw new ToolExecutionException("language is required");
                    }
                    if (expression == null || expression.isBlank()) {
                        throw new ToolExecutionException("expression is required");
                    }
                    return ctx.executeAction("eval", root -> {
                        root.put("language", language);
                        root.put("predicate", "false");
                        root.put("template", Jsoner.escape(expression));
                    });
                }));

        register(tool("browse_endpoint",
                "Browse messages in a Camel endpoint (e.g., browse messages queued in a SEDA endpoint).")
                .param("endpoint", "string", "Endpoint URI to browse (e.g., seda:queue)", true)
                .param("limit", "string",
                        "Maximum number of messages to return (default: 50)", false)
                .executor((ctx, args) -> {
                    String endpoint = args.get("endpoint");
                    if (endpoint == null || endpoint.isBlank()) {
                        throw new ToolExecutionException("endpoint is required");
                    }
                    int limit = 50;
                    String limitStr = args.get("limit");
                    if (limitStr != null && !limitStr.isBlank()) {
                        try {
                            limit = Integer.parseInt(limitStr);
                        } catch (NumberFormatException e) {
                            // use default
                        }
                    }
                    int browseLimit = limit;
                    return ctx.executeAction("browse", root -> {
                        root.put("filter", endpoint);
                        root.put("limit", browseLimit);
                    });
                }));

        register(tool("get_thread_dump",
                "Get a JVM thread dump showing thread names, states, and stack traces.")
                .executor((ctx, args) -> ctx.executeAction("thread-dump", null)));

        register(tool("get_heap_histogram",
                "Get a class-level heap histogram showing instance counts and byte usage per class. "
                                            + "Useful for diagnosing memory leaks and understanding which classes dominate heap usage.")
                .executor((ctx, args) -> ctx.executeAction("heap-histogram", null)));

        register(tool("execute_sql",
                "Execute a SQL query against a DataSource in the running Camel application. "
                                     + "Returns structured JSON with columns, rows, and metadata for SELECT queries, "
                                     + "or an update count for INSERT/UPDATE/DELETE.")
                .param("query", "string", "The SQL query to execute", true)
                .param("datasource", "string",
                        "Name of the DataSource bean (auto-detected if only one exists)", false)
                .param("maxRows", "string", "Maximum number of rows to return (default: 100)", false)
                .readOnly(false).destructive(true)
                .executor((ctx, args) -> {
                    String sql = args.get("query");
                    if (sql == null || sql.isBlank()) {
                        throw new ToolExecutionException("'query' parameter is required");
                    }
                    String datasource = args.get("datasource");
                    int maxRows = 100;
                    String maxRowsStr = args.get("maxRows");
                    if (maxRowsStr != null && !maxRowsStr.isBlank()) {
                        try {
                            maxRows = Integer.parseInt(maxRowsStr);
                        } catch (NumberFormatException e) {
                            // use default
                        }
                    }
                    JsonObject result = ctx.executeSqlQuery(sql, datasource, maxRows, 30);
                    return result.toJson();
                }));

        // Route control
        registerRouteControlTool("stop_route", "stop",
                "Gracefully stop a route. The route will finish processing in-flight exchanges before stopping.");
        registerRouteControlTool("start_route", "start", "Start a stopped route.");
        registerRouteControlTool("suspend_route", "suspend",
                "Suspend a route (pauses the consumer but keeps the route loaded).");
        registerRouteControlTool("resume_route", "resume", "Resume a suspended route.");

        register(tool("stop_application",
                "Gracefully stop the Camel application. Finishes in-flight exchanges then shuts down cleanly.")
                .readOnly(false).destructive(true)
                .executor((ctx, args) -> ctx.stopApplication()));
    }

    private static void registerRouteControlTool(String name, String command, String description) {
        register(tool(name, description)
                .param("routeId", "string", "The ID of the route", true)
                .readOnly(false).destructive(command.equals("stop"))
                .executor((ctx, args) -> {
                    String routeId = args.get("routeId");
                    if (routeId == null || routeId.isBlank()) {
                        throw new ToolExecutionException("routeId is required");
                    }
                    return ctx.executeAction("route", root -> {
                        root.put("id", routeId);
                        root.put("command", command);
                    });
                }));
    }

    // ---- DevConsole tools (data available in TUI, now surfaced for AI) ----

    private static void registerDevConsoleTools() {
        register(tool("get_circuit_breakers",
                "Get circuit breaker status from the running Camel application. Shows state (CLOSED/OPEN/HALF_OPEN), "
                                              + "call counts, failure rates, and not-permitted calls for each breaker. "
                                              + "Supports Resilience4j and MicroProfile Fault Tolerance implementations.")
                .executor((ctx, args) -> {
                    JsonObject root = ctx.readFullStatus();
                    if (root == null) {
                        return "No status available.";
                    }
                    // Circuit breaker data can be under resilience4j, fault-tolerance, or circuit-breaker sections
                    JsonArray allBreakers = new JsonArray();
                    collectCircuitBreakers(root, "resilience4j", allBreakers);
                    collectCircuitBreakers(root, "fault-tolerance", allBreakers);
                    collectCircuitBreakers(root, "circuit-breaker", allBreakers);

                    JsonObject response = new JsonObject();
                    response.put("count", allBreakers.size());
                    response.put("circuitBreakers", allBreakers);
                    if (allBreakers.isEmpty()) {
                        response.put("hint",
                                "No circuit breakers found. Add camel-resilience4j or camel-microprofile-fault-tolerance to use circuit breakers.");
                    }
                    return response.toJson();
                }));

        register(tool("get_startup_steps",
                "Get startup recorder steps showing component initialization timing. "
                                           + "Shows each startup step with duration, level, and type. "
                                           + "Useful for diagnosing slow application startup. "
                                           + "Requires startup recording to be enabled (camel.main.startup-recorder=true).")
                .executor((ctx, args) -> ctx.executeAction("startup-recorder", null)));

        register(tool("get_datasources",
                "Get datasource connection pool status. Shows active, idle, and total connections, "
                                         + "max pool size, and waiting threads for each datasource. "
                                         + "Supports HikariCP and Agroal connection pools.")
                .executor((ctx, args) -> ctx.readStatus("dataSources")));

        register(tool("get_spans",
                "Get OpenTelemetry trace spans from the running Camel application. "
                                   + "Shows trace IDs, span names, durations, route IDs, and status. "
                                   + "Requires OpenTelemetry tracing to be enabled (run with --observe).")
                .param("limit", "string", "Maximum number of spans to return (default: 100)", false)
                .executor((ctx, args) -> {
                    String limitStr = args.get("limit");
                    int limit = 100;
                    if (limitStr != null && !limitStr.isBlank()) {
                        try {
                            limit = Integer.parseInt(limitStr);
                        } catch (NumberFormatException e) {
                            // use default
                        }
                    }
                    int spanLimit = limit;
                    return ctx.executeAction("span", root -> {
                        root.put("dump", "true");
                        root.put("limit", String.valueOf(spanLimit));
                    });
                }));

        register(tool("get_metrics",
                "Get Micrometer metrics from the running Camel application. "
                                     + "Shows counters, gauges, timers, long-task timers, and distributions. "
                                     + "Useful for monitoring throughput, latency, and resource usage. "
                                     + "Requires micrometer to be enabled (run with --observe or add camel-micrometer).")
                .executor((ctx, args) -> ctx.readStatus("micrometer")));
    }

    @SuppressWarnings("unchecked")
    private static void collectCircuitBreakers(JsonObject root, String sectionKey, JsonArray target) {
        Object section = root.get(sectionKey);
        if (!(section instanceof JsonObject sectionObj)) {
            return;
        }
        Object breakers = sectionObj.get("circuitBreakers");
        if (!(breakers instanceof List<?> breakerList)) {
            return;
        }
        String component = switch (sectionKey) {
            case "resilience4j" -> "resilience4j";
            case "fault-tolerance" -> "fault-tolerance";
            default -> "core";
        };
        for (Object b : breakerList) {
            if (b instanceof JsonObject bj) {
                JsonObject entry = new JsonObject();
                entry.put("component", component);
                entry.put("routeId", bj.get("routeId"));
                entry.put("id", bj.get("id"));
                entry.put("state", bj.get("state"));
                entry.put("bufferedCalls", bj.get("bufferedCalls"));
                entry.put("successfulCalls", bj.get("successfulCalls"));
                entry.put("failedCalls", bj.get("failedCalls"));
                entry.put("notPermittedCalls", bj.get("notPermittedCalls"));
                entry.put("failureRate", bj.get("failureRate"));
                target.add(entry);
            }
        }
    }

    // ---- Analysis tools (higher-level diagnostic tools) ----

    private static void registerAnalysisTools() {
        register(tool("get_route_analysis",
                "Analyze route definitions for common anti-patterns and structural issues. "
                                            + "Checks each route for: error handler configuration, dead letter channel, "
                                            + "route complexity (processor count and nesting depth), component usage, "
                                            + "and potential issues. Returns structured analysis results per route.")
                .param("routeId", "string", "Route ID to analyze (use * for all routes, default: *)", false)
                .executor((ctx, args) -> {
                    String routeId = args.get("routeId");
                    // Get route structure for analysis
                    String structureJson = ctx.executeAction("route-structure",
                            root -> root.put("id", routeId != null ? routeId : "*"));
                    // Get route dump for error handler info
                    String dumpJson = ctx.executeAction("route-dump", root -> {
                        root.put("id", routeId != null ? routeId : "*");
                        root.put("format", "yaml");
                    });
                    JsonObject response = new JsonObject();
                    response.put("routeStructure", structureJson);
                    response.put("routeDump", dumpJson);
                    response.put("analysisHints", buildAnalysisHints());
                    return response.toJson();
                }));

        register(tool("get_eip_stats",
                "Aggregate EIP (Enterprise Integration Pattern) usage statistics across all routes. "
                                       + "Shows which processors are used, how often, and their performance metrics "
                                       + "(total exchanges, failures, mean/max processing time). "
                                       + "Useful for understanding route complexity and identifying bottlenecks.")
                .executor((ctx, args) -> {
                    // Get top processor stats which include per-processor metrics
                    String topJson = ctx.executeAction("top-processors", null);
                    // Get route structure for EIP type counting
                    String structureJson = ctx.executeAction("route-structure",
                            root -> root.put("id", "*"));
                    JsonObject response = new JsonObject();
                    response.put("processorStats", topJson);
                    response.put("routeStructures", structureJson);
                    return response.toJson();
                }));

        register(tool("detect_config_drift",
                "Compare the running route configuration with the original source files. "
                                             + "Dumps the running route definitions and the source files "
                                             + "so they can be compared to detect configuration drift. "
                                             + "Useful for verifying that runtime matches deployment artifacts.")
                .param("format", "string", "Dump format for comparison: yaml or xml (default: yaml)", false)
                .executor((ctx, args) -> {
                    String format = args.get("format");
                    if (format == null || format.isBlank()) {
                        format = "yaml";
                    }
                    // Get running route definitions
                    String dumpFormat = format;
                    String runningRoutes = ctx.executeAction("route-dump", root -> {
                        root.put("id", "*");
                        root.put("format", dumpFormat);
                    });
                    // Get source files
                    String sourceFiles = ctx.executeAction("source",
                            root -> root.put("filter", "*"));
                    JsonObject response = new JsonObject();
                    response.put("runningRoutes", runningRoutes);
                    response.put("sourceFiles", sourceFiles);
                    response.put("format", dumpFormat);
                    response.put("hint",
                            "Compare the running route definitions with the source files to identify any differences. "
                                         + "Common drift causes: dynamic route modification, runtime property resolution, "
                                         + "and hot-reloading changes.");
                    return response.toJson();
                }));
    }

    private static JsonObject buildAnalysisHints() {
        JsonObject hints = new JsonObject();
        hints.put("errorHandling",
                "Check if routes define an errorHandler or onException clause. "
                                   + "Routes without error handling will propagate exceptions to the caller.");
        hints.put("deadLetterChannel",
                "Routes processing messages from queues should have a deadLetterChannel "
                                       + "to avoid losing messages on repeated failures.");
        hints.put("timeout",
                "Routes calling external services (http, rest, soap) should configure "
                             + "connection and read timeouts to avoid hanging threads.");
        hints.put("idempotency",
                "Routes consuming from non-idempotent sources (JMS, Kafka without exactly-once) "
                                 + "should use an idempotentConsumer to handle duplicate messages.");
        hints.put("circuitBreaker",
                "Routes calling unreliable external services should wrap calls in a "
                                    + "circuitBreaker (resilience4j) to prevent cascade failures.");
        hints.put("logging",
                "Routes should include appropriate logging (log EIP) at key points "
                             + "for operational visibility, especially on error paths.");
        return hints;
    }

    // ---- Catalog tools ----

    private static void registerCatalogTools() {
        register(tool("catalog_components",
                "Search the Camel component catalog by name or label.")
                .param("filter", "string",
                        "Filter by name, title, or description (case-insensitive substring)", false)
                .param("label", "string",
                        "Filter by category label (e.g., cloud, messaging, database, file)", false)
                .executor((ctx, args) -> {
                    String filter = args.get("filter");
                    String label = args.get("label");
                    CamelCatalog cat = ctx.catalog();
                    List<JsonObject> results = cat.findComponentNames().stream()
                            .map(cat::componentModel)
                            .filter(Objects::nonNull)
                            .filter(m -> matchesFilter(m.getScheme(), m.getTitle(), m.getDescription(), filter))
                            .filter(m -> label == null || label.isBlank()
                                    || (m.getLabel() != null
                                            && m.getLabel().toLowerCase().contains(label.toLowerCase())))
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
                }));

        register(tool("catalog_component_doc",
                "Get detailed documentation for a Camel component: URI syntax and endpoint options.")
                .param("component", "string",
                        "Component name (e.g., kafka, http, file, timer)", true)
                .executor((ctx, args) -> {
                    String component = args.get("component");
                    if (component == null || component.isBlank()) {
                        throw new ToolExecutionException("component name is required");
                    }
                    CamelCatalog cat = ctx.catalog();
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
                }));

        register(tool("catalog_eips",
                "Search EIPs (Enterprise Integration Patterns) like split, aggregate, filter, choice, multicast.")
                .param("filter", "string",
                        "Filter by name, title, or description (case-insensitive substring)", false)
                .executor((ctx, args) -> {
                    String filter = args.get("filter");
                    CamelCatalog cat = ctx.catalog();
                    List<JsonObject> results = cat.findModelNames().stream()
                            .map(cat::eipModel)
                            .filter(Objects::nonNull)
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
                }));

        // New catalog tools (previously MCP-only)

        register(tool("catalog_dataformats",
                "List available Camel data formats (e.g., json, xml, csv, avro).")
                .param("filter", "string", "Filter by name or description", false)
                .executor((ctx, args) -> {
                    String filter = args.get("filter");
                    CamelCatalog cat = ctx.catalog();
                    List<JsonObject> results = cat.findDataFormatNames().stream()
                            .map(cat::dataFormatModel)
                            .filter(Objects::nonNull)
                            .filter(m -> matchesFilter(m.getName(), m.getTitle(), m.getDescription(), filter))
                            .limit(20)
                            .map(m -> {
                                JsonObject jo = new JsonObject();
                                jo.put("name", m.getName());
                                jo.put("title", m.getTitle());
                                jo.put("description", m.getDescription());
                                return jo;
                            })
                            .collect(Collectors.toList());
                    JsonObject response = new JsonObject();
                    response.put("count", results.size());
                    response.put("dataformats", results);
                    return response.toJson();
                }));

        register(tool("catalog_dataformat_doc",
                "Get detailed documentation for a Camel data format.")
                .param("dataformat", "string",
                        "Data format name (e.g., jackson, jaxb, csv)", true)
                .executor((ctx, args) -> {
                    String name = args.get("dataformat");
                    if (name == null || name.isBlank()) {
                        throw new ToolExecutionException("dataformat name is required");
                    }
                    CamelCatalog cat = ctx.catalog();
                    var model = cat.dataFormatModel(name);
                    if (model == null) {
                        return "Data format not found: " + name;
                    }
                    JsonObject response = new JsonObject();
                    response.put("name", model.getName());
                    response.put("title", model.getTitle());
                    response.put("description", model.getDescription());
                    response.put("modelJavaType", model.getModelJavaType());
                    if (model.getOptions() != null) {
                        List<JsonObject> options = model.getOptions().stream()
                                .filter(opt -> !opt.isDeprecated())
                                .map(opt -> {
                                    JsonObject jo = new JsonObject();
                                    jo.put("name", opt.getName());
                                    jo.put("description", opt.getDescription());
                                    jo.put("type", opt.getType());
                                    jo.put("required", opt.isRequired());
                                    if (opt.getDefaultValue() != null) {
                                        jo.put("defaultValue", opt.getDefaultValue().toString());
                                    }
                                    return jo;
                                })
                                .collect(Collectors.toList());
                        response.put("options", options);
                    }
                    return response.toJson();
                }));

        register(tool("catalog_languages",
                "List available Camel expression languages (e.g., simple, jsonpath, xpath, jq).")
                .param("filter", "string", "Filter by name or description", false)
                .executor((ctx, args) -> {
                    String filter = args.get("filter");
                    CamelCatalog cat = ctx.catalog();
                    List<JsonObject> results = cat.findLanguageNames().stream()
                            .map(cat::languageModel)
                            .filter(Objects::nonNull)
                            .filter(m -> matchesFilter(m.getName(), m.getTitle(), m.getDescription(), filter))
                            .limit(20)
                            .map(m -> {
                                JsonObject jo = new JsonObject();
                                jo.put("name", m.getName());
                                jo.put("title", m.getTitle());
                                jo.put("description", m.getDescription());
                                return jo;
                            })
                            .collect(Collectors.toList());
                    JsonObject response = new JsonObject();
                    response.put("count", results.size());
                    response.put("languages", results);
                    return response.toJson();
                }));

        register(tool("catalog_language_doc",
                "Get detailed documentation for a Camel expression language.")
                .param("language", "string",
                        "Language name (e.g., simple, jsonpath, xpath, jq)", true)
                .executor((ctx, args) -> {
                    String name = args.get("language");
                    if (name == null || name.isBlank()) {
                        throw new ToolExecutionException("language name is required");
                    }
                    CamelCatalog cat = ctx.catalog();
                    var model = cat.languageModel(name);
                    if (model == null) {
                        return "Language not found: " + name;
                    }
                    JsonObject response = new JsonObject();
                    response.put("name", model.getName());
                    response.put("title", model.getTitle());
                    response.put("description", model.getDescription());
                    response.put("modelJavaType", model.getModelJavaType());
                    if (model.getOptions() != null) {
                        List<JsonObject> options = model.getOptions().stream()
                                .filter(opt -> !opt.isDeprecated())
                                .map(opt -> {
                                    JsonObject jo = new JsonObject();
                                    jo.put("name", opt.getName());
                                    jo.put("description", opt.getDescription());
                                    jo.put("type", opt.getType());
                                    jo.put("required", opt.isRequired());
                                    if (opt.getDefaultValue() != null) {
                                        jo.put("defaultValue", opt.getDefaultValue().toString());
                                    }
                                    return jo;
                                })
                                .collect(Collectors.toList());
                        response.put("options", options);
                    }
                    return response.toJson();
                }));

        register(tool("catalog_eip_doc",
                "Get detailed documentation for a Camel EIP (Enterprise Integration Pattern).")
                .param("eip", "string",
                        "EIP name (e.g., split, aggregate, filter, choice)", true)
                .executor((ctx, args) -> {
                    String name = args.get("eip");
                    if (name == null || name.isBlank()) {
                        throw new ToolExecutionException("EIP name is required");
                    }
                    CamelCatalog cat = ctx.catalog();
                    var model = cat.eipModel(name);
                    if (model == null) {
                        return "EIP not found: " + name;
                    }
                    JsonObject response = new JsonObject();
                    response.put("name", model.getName());
                    response.put("title", model.getTitle());
                    response.put("description", model.getDescription());
                    response.put("label", model.getLabel());
                    response.put("input", model.isInput());
                    response.put("output", model.isOutput());
                    if (model.getOptions() != null) {
                        List<JsonObject> options = model.getOptions().stream()
                                .filter(opt -> !opt.isDeprecated())
                                .map(opt -> {
                                    JsonObject jo = new JsonObject();
                                    jo.put("name", opt.getName());
                                    jo.put("description", opt.getDescription());
                                    jo.put("type", opt.getType());
                                    jo.put("required", opt.isRequired());
                                    if (opt.getDefaultValue() != null) {
                                        jo.put("defaultValue", opt.getDefaultValue().toString());
                                    }
                                    return jo;
                                })
                                .collect(Collectors.toList());
                        response.put("options", options);
                    }
                    return response.toJson();
                }));
    }

    // ---- Example tools ----

    private static void registerExampleTools() {
        register(tool("list_examples",
                "List available Camel CLI examples. Returns name, title, description, difficulty level, and tags.")
                .param("filter", "string",
                        "Filter by name, description, or tag (case-insensitive)", false)
                .param("level", "string",
                        "Filter by difficulty: beginner, intermediate, or advanced", false)
                .executor((ctx, args) -> {
                    String filter = args.get("filter");
                    String level = args.get("level");
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
                }));

        register(tool("get_example_file",
                "Get the content of a file from a bundled Camel CLI example.")
                .param("example", "string",
                        "Example name (e.g., timer-log, rest-api, circuit-breaker)", true)
                .param("file", "string",
                        "File name within the example (e.g., route.camel.yaml)", true)
                .executor((ctx, args) -> {
                    String example = args.get("example");
                    String file = args.get("file");
                    if (example == null || example.isBlank()) {
                        throw new ToolExecutionException("example name is required");
                    }
                    if (file == null || file.isBlank()) {
                        throw new ToolExecutionException("file name is required");
                    }
                    List<JsonObject> catalog2 = ExampleHelper.loadCatalog();
                    JsonObject entry = ExampleHelper.findExample(catalog2, example);
                    if (entry == null) {
                        return "Example not found: " + example;
                    }
                    List<String> files = ExampleHelper.getFiles(entry);
                    if (!files.contains(file)) {
                        return "File '" + file + "' not found in example '" + example + "'. Available: " + files;
                    }
                    if (ExampleHelper.isBundled(entry)) {
                        String resourcePath = "examples/" + example + "/" + file;
                        try (InputStream is
                                = ExampleHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
                            if (is != null) {
                                return IOHelper.loadText(is);
                            }
                        } catch (Exception e) {
                            return "Error reading file: " + e.getMessage();
                        }
                        return "Could not read bundled file: " + resourcePath;
                    } else {
                        return "This example is not bundled. View it on GitHub: "
                               + ExampleHelper.getGithubUrl(entry) + "/" + file;
                    }
                }));
    }

    // ---- Utility ----

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
