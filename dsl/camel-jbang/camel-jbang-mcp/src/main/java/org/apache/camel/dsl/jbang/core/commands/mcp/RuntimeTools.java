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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * MCP tools for inspecting and interacting with running Camel applications.
 * <p>
 * These tools are thin MCP wrappers that delegate to the shared {@link ToolRegistry}. Each method resolves the
 * {@code nameOrPid} parameter (an MCP-specific concern for multi-process discovery) and then delegates to the
 * corresponding {@link ToolRegistry} tool for the actual logic.
 * <p>
 * The {@code @Tool} and {@code @ToolArg} annotations are required by the Quarkus MCP server for protocol discovery but
 * all tool logic lives in {@link ToolRegistry}, ensuring a single source of truth shared with the Agent REPL.
 */
@McpSecured
@ApplicationScoped
public class RuntimeTools {

    private static final String NAME_OR_PID_DESC
            = "Name or PID of the Camel process. Leave empty to auto-detect (works when exactly one Camel process is running)";

    private static final String CHAOS_NAME_OR_PID_DESC
            = "Name or PID of the Camel process. Leave empty to auto-detect: the only Camel process running, else the"
              + " only one with chaos switched on";

    private static final Set<String> MEMORY_LEAK_COMMANDS = Set.of("start", "stop", "status", "query", "compare");
    private static final int SINGLE_DEFAULT_DURATION = 60;
    private static final int DUAL_DEFAULT_DURATION = 30;

    private static final int RECEIVE_DEFAULT_TIMEOUT = 10;
    private static final int RECEIVE_MAX_TIMEOUT = 60;
    private static final long RECEIVE_POLL_MS = 200;
    // the connector applies the stop and writes the status in one tick, and dumps the console every second tick
    // (one tick is a second by default)
    private static final long RECEIVE_SETTLE_DEFAULT_MS = 3000;

    private final ConcurrentMap<Long, ReentrantLock> receiveLocks = new ConcurrentHashMap<>();
    /** How long to wait after the stop for the process to confirm it and to write the last messages. */
    long receiveSettleMs = RECEIVE_SETTLE_DEFAULT_MS;

    @Inject
    RuntimeService runtimeService;

    // ---- Delegation helpers ----

    /**
     * Resolve nameOrPid via {@link RuntimeService}, create a {@link ToolContext} with the resolved PID, and delegate to
     * the shared {@link ToolRegistry}.
     *
     * @param  registryToolName the tool name in ToolRegistry (e.g., "get_context")
     * @param  nameOrPid        MCP process selector (name, PID, or empty for auto-detect)
     * @param  args             tool arguments as a String map
     * @return                  the tool result as a JsonObject suitable for MCP responses
     */
    private JsonObject delegateToRegistry(String registryToolName, String nameOrPid, Map<String, String> args) {
        return delegateToRegistry(registryToolName, runtimeService.findSingleProcess(nameOrPid), args);
    }

    private static JsonObject delegateToRegistry(
            String registryToolName, RuntimeService.ProcessInfo p, Map<String, String> args) {
        ToolContext ctx = new ToolContext();
        ctx.selectProcess(p.pid());
        try {
            Object result = ToolRegistry.execute(registryToolName, ctx, args);
            return toJsonObject(result);
        } catch (ToolExecutionException e) {
            throw new ToolCallException(e.getMessage(), e);
        }
    }

    /**
     * Convert a {@link ToolRegistry} result (typically a JSON String or JsonObject) into a {@link JsonObject} for MCP
     * serialization.
     */
    static JsonObject toJsonObject(Object result) {
        if (result instanceof JsonObject jo) {
            return jo;
        }
        if (result == null) {
            return new JsonObject();
        }
        String str = result.toString();
        try {
            Object parsed = Jsoner.deserialize(str);
            if (parsed instanceof JsonObject jo) {
                return jo;
            }
        } catch (Exception e) {
            // not a valid JSON object — wrap as plain text
        }
        JsonObject wrapper = new JsonObject();
        wrapper.put("result", str);
        return wrapper;
    }

    // ---- Process discovery (MCP-specific, not in ToolRegistry) ----

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  List all running Camel processes that can be inspected. \
                  Returns PID, name, and context name for each discovered process.""")
    public List<RuntimeService.ProcessInfo> camel_runtime_processes() {
        return runtimeService.discoverProcesses();
    }

    // ---- Read-only status tools ----

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get Camel context information: name, version, state, uptime, route count, exchange statistics.")
    public JsonObject camel_runtime_context(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_context", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List Camel routes with their state, uptime, messages processed, last error, and throughput statistics."
                        + " A route with mixedBodyKinds has steps reached with bodies of more than one kind, which need"
                        + " attention: camel_runtime_route_structure with varied=true shows them. The checksum of a route"
                        + " is the same in every run while the route is not changed: compare it with an earlier run to"
                        + " tell whether a route changed, and match routes with generated ids by it; revision counts the"
                        + " times a reload changed the route in this run.")
    public JsonObject camel_runtime_routes(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_routes", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get health check status for the Camel application.")
    public JsonObject camel_runtime_health(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_health", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List all endpoints registered in the Camel context with their URIs and usage statistics.")
    public JsonObject camel_runtime_endpoints(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_endpoints", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show currently in-flight exchanges (messages being processed).")
    public JsonObject camel_runtime_inflight(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_inflight", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show blocked exchanges that are stuck or waiting.")
    public JsonObject camel_runtime_blocked(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_blocked", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show exchange variables in the Camel context.")
    public JsonObject camel_runtime_variables(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_variables", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show consumer statistics (polling consumers, event-driven consumers).")
    public JsonObject camel_runtime_consumers(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_consumers", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show configuration properties of the running Camel application.")
    public JsonObject camel_runtime_properties(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_properties", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show services registered in the Camel service registry.")
    public JsonObject camel_runtime_services(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_services", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show JVM memory usage (heap/non-heap), garbage collection stats, and thread counts.")
    public JsonObject camel_runtime_memory(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_memory", nameOrPid, Map.of());
    }

    // ---- Interactive action tools ----

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get the source code of routes in the running Camel application.")
    public JsonObject camel_runtime_route_source(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Filter source files by name (supports wildcards)", required = false) String filter) {
        return delegateToRegistry("get_route_source", nameOrPid,
                Map.of("filter", filter != null ? filter : "*"));
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Dump route definitions in XML, YAML, or Java DSL format.")
    public JsonObject camel_runtime_route_dump(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Route ID or pattern such as orders* to dump (default * for all routes)",
                     required = false) String routeId,
            @ToolArg(description = "Output format: xml, yaml, or java (default: yaml)", required = false) String format) {
        Map<String, String> args = new HashMap<>();
        args.put("routeId", routeId != null ? routeId : "*");
        args.put("format", format != null ? format : "yaml");
        return delegateToRegistry("get_route_dump", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show the route structure as a tree of processors. With the message history on (camel run --dev),"
                        + " a processor has bodyTypes: the body types it has been reached with, across the messages, and"
                        + " bodyKinds: null, payload, data or collection. More than one kind needs attention: a step that"
                        + " writes fails for data, one that reads fields fails for a payload. The routes tool counts them"
                        + " per route (mixedBodyKinds). The checksum of each route says which version of the route the"
                        + " facts of its lines are of.")
    public JsonObject camel_runtime_route_structure(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Route ID or pattern such as orders* to inspect (default * for all routes)",
                     required = false) String routeId,
            @ToolArg(description = "Only the overall structure, without the options and expressions (default false)",
                     required = false) Boolean brief,
            @ToolArg(description = "Only the steps reached with more than one body type (default false)",
                     required = false) Boolean varied) {
        Map<String, String> args = new HashMap<>();
        args.put("routeId", routeId != null ? routeId : "*");
        if (Boolean.TRUE.equals(brief)) {
            args.put("brief", "true");
        }
        if (Boolean.TRUE.equals(varied)) {
            args.put("varied", "true");
        }
        return delegateToRegistry("get_route_structure", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Show configured options for all processors in a route. \
                  Returns each processor's type, id, and configured options (attributes, expressions). \
                  Use includeDocs=true to enrich the response with documentation from the Camel catalog \
                  for each EIP option and component endpoint option.""")
    public JsonObject camel_runtime_processor_detail(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Route ID to inspect (use * for all routes)", required = false) String routeId,
            @ToolArg(description = "If true, enrich each processor's options with documentation from the Camel catalog",
                     required = false) Boolean includeDocs) {
        Map<String, String> args = new HashMap<>();
        args.put("routeId", routeId != null ? routeId : "*");
        if (includeDocs != null && includeDocs) {
            args.put("includeDocs", "true");
        }
        return delegateToRegistry("get_processor_detail", nameOrPid, args);
    }

    // one tool for the four commands, and the hint is per tool: destructive because stop and suspend take the route's
    // consumer offline (the shared registry, with one tool per command, marks only stop_route destructive)
    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = "Control a route: start, stop, suspend, or resume it.")
    public JsonObject camel_runtime_route_control(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Route ID to control") String routeId,
            @ToolArg(description = "Command: start, stop, suspend, or resume") String command) {
        if (routeId == null || routeId.isBlank()) {
            throw new ToolCallException("routeId is required", null);
        }
        if (command == null || command.isBlank()) {
            throw new ToolCallException("command is required (start, stop, suspend, resume)", null);
        }
        // Map the MCP command to the corresponding ToolRegistry tool
        String toolName = switch (command.toLowerCase()) {
            case "start" -> "start_route";
            case "stop" -> "stop_route";
            case "suspend" -> "suspend_route";
            case "resume" -> "resume_route";
            default -> throw new ToolCallException(
                    "Unknown command: " + command + ". Use start, stop, suspend, or resume.", null);
        };
        return delegateToRegistry(toolName, nameOrPid, Map.of("routeId", routeId));
    }

    // ---- Data, resilience, metrics and analysis (CAMEL-24867: the rest of the shared runtime registry) ----

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Execute a SQL statement against a DataSource of the running Camel application. \
                  Returns columns, rows and metadata for a SELECT, or the update count for INSERT/UPDATE/DELETE. \
                  Use it to look at the data a route reads or writes, or to try a statement before putting it in a route.""")
    public JsonObject camel_runtime_sql(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "The SQL statement to execute") String query,
            @ToolArg(description = "Name of the DataSource bean (auto-detected if only one exists)",
                     required = false) String datasource,
            @ToolArg(description = "Maximum number of rows to return (default 100)", required = false) String maxRows) {
        if (query == null || query.isBlank()) {
            throw new ToolCallException("query is required", null);
        }
        Map<String, String> args = new HashMap<>();
        args.put("query", query);
        putIfNotBlank(args, "datasource", datasource);
        putIfNotBlank(args, "maxRows", maxRows);
        return delegateToRegistry("execute_sql", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Which runtime tool groups the Camel application needs, from what it has: sql (datasources, \
                  SQL endpoints), tracing (OpenTelemetry, message tracing, Micrometer), resilience (circuit \
                  breakers), http (the endpoints it serves) and chaos (camel-chaos). Returns the core tools, each \
                  group's tools with one line of guidance, and a fingerprint that changes only when the groups do. \
                  A client for a small model offers the core tools plus these.""")
    public JsonObject camel_runtime_tool_groups(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_tool_groups", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  List the HTTP endpoints the running Camel application serves (Rest DSL and platform-http): \
                  method, path, consumes/produces, route and OpenAPI operation, the server's base URL and the \
                  contract. includeSpec=true adds the OpenAPI contract itself.""")
    public JsonObject camel_runtime_http_endpoints(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Add the OpenAPI contract of a contract-first service (default false)",
                     required = false) Boolean includeSpec) {
        Map<String, String> args = new HashMap<>();
        if (includeSpec != null && includeSpec) {
            args.put("includeSpec", "true");
        }
        return delegateToRegistry("get_http_endpoints", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Send an HTTP request to the running Camel application's own server (localhost and its port) and \
                  return the status, headers and body (cut at 6000 characters). Pass the path, e.g. /api/orders/1; \
                  other hosts are refused. Use camel_runtime_http_endpoints for the paths it serves.""")
    public JsonObject camel_runtime_http_request(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "GET (default), POST, PUT, PATCH, DELETE, HEAD or OPTIONS",
                     required = false) String method,
            @ToolArg(description = "The path with query, e.g. /api/orders?status=open") String path,
            @ToolArg(description = "Headers as a JSON object or one 'name: value' per line",
                     required = false) String headers,
            @ToolArg(description = "The request body", required = false) String body,
            @ToolArg(description = "How long to wait for the answer in seconds (default 30, at most 120)",
                     required = false) Integer timeoutSeconds) {
        if (path == null || path.isBlank()) {
            throw new ToolCallException("path is required, e.g. /api/orders/1", null);
        }
        Map<String, String> args = new HashMap<>();
        args.put("path", path);
        putIfNotBlank(args, "method", method);
        putIfNotBlank(args, "headers", headers);
        if (body != null) {
            args.put("body", body);
        }
        if (timeoutSeconds != null) {
            args.put("timeoutSeconds", timeoutSeconds.toString());
        }
        return delegateToRegistry("http_request", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the datasources of the running Camel application with their connection pool status: \
                  active, idle and total connections, max pool size and waiting threads (HikariCP and Agroal).""")
    public JsonObject camel_runtime_datasources(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_datasources", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the traced SQL executions of the camel-sql and camel-jdbc components: per statement timing, \
                  row counts, category (SELECT/INSERT/UPDATE/DELETE), route ID and failure status, with summary statistics.""")
    public JsonObject camel_runtime_sql_trace(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_sql_trace", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the circuit breakers of the running Camel application: state (CLOSED/OPEN/HALF_OPEN), \
                  call counts, failure rates and not-permitted calls (Resilience4j and MicroProfile Fault Tolerance).""")
    public JsonObject camel_runtime_circuit_breakers(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_circuit_breakers", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the Micrometer metrics of the running Camel application: counters, gauges, timers and \
                  distributions. Requires micrometer (run with --observe or add camel-micrometer).""")
    public JsonObject camel_runtime_metrics(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_metrics", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Aggregate EIP usage across all routes: which processors are used, how often, and their \
                  performance (total exchanges, failures, mean and max processing time).""")
    public JsonObject camel_runtime_eip_stats(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_eip_stats", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the OpenTelemetry spans of the running Camel application: trace IDs, span names, durations, \
                  route IDs and status. Requires tracing (run with --observe).""")
    public JsonObject camel_runtime_spans(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Maximum number of spans to return (default 100)", required = false) String limit) {
        Map<String, String> args = new HashMap<>();
        putIfNotBlank(args, "limit", limit);
        return delegateToRegistry("get_spans", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the startup recorder steps of the running Camel application: each step with duration, level \
                  and type, for diagnosing a slow startup. Requires camel.main.startup-recorder=backlog.""")
    public JsonObject camel_runtime_startup_steps(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_startup_steps", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Analyze the routes of the running Camel application for anti-patterns and structural issues: \
                  error handler and dead letter channel configuration, complexity (processor count and nesting depth), \
                  component usage and potential issues, per route.""")
    public JsonObject camel_runtime_route_analysis(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Route ID to analyze (* for all routes, the default)", required = false) String routeId) {
        Map<String, String> args = new HashMap<>();
        putIfNotBlank(args, "routeId", routeId);
        return delegateToRegistry("get_route_analysis", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Compare the running route definitions with the original source files, to detect configuration \
                  drift between what runs and what is deployed.""")
    public JsonObject camel_runtime_config_drift(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Dump format for the comparison: yaml (default) or xml", required = false) String format) {
        Map<String, String> args = new HashMap<>();
        putIfNotBlank(args, "format", format);
        return delegateToRegistry("detect_config_drift", nameOrPid, args);
    }

    private static void putIfNotBlank(Map<String, String> args, String key, String value) {
        if (value != null && !value.isBlank()) {
            args.put(key, value);
        }
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Send a test message to a Camel endpoint in the running application. With repeat, or a body of \
                  file:<folder> (one message per file, in name order; 001.properties has the headers of 001.json), it \
                  sends several messages with interval millis between them, and answers with a summary: sent, failed \
                  and the errors. A broker consumer stubbed with camel run --stub=remote is fed this way.""")
    public JsonObject camel_runtime_send(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Endpoint URI to send to (e.g., direct:myRoute, seda:queue)") String endpoint,
            @ToolArg(description = "Message body to send (file:<path> for a file, file:<folder> for its files)",
                     required = false) String body,
            @ToolArg(description = "Message headers as key=value pairs separated by newlines",
                     required = false) String headers,
            @ToolArg(description = "How many messages to send in all (at most 10000), going round the files of a folder",
                     required = false) Integer repeat,
            @ToolArg(description = "The pause between two messages in millis, or a range such as 100..500 to draw"
                                   + " each pause at random (at most 60000)",
                     required = false) String interval) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new ToolCallException("endpoint is required", null);
        }
        Map<String, String> args = new HashMap<>();
        args.put("endpoint", endpoint);
        if (body != null) {
            args.put("body", body);
        }
        if (headers != null) {
            args.put("headers", headers);
        }
        if (repeat != null) {
            args.put("repeat", String.valueOf(repeat));
        }
        if (interval != null) {
            args.put("interval", interval);
        }
        return delegateToRegistry("send_message", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Enable, disable, or dump message tracing for the running Camel application. \
                  camel_runtime_trace_dump returns the traced messages too, and is offered to read-only clients.""")
    public JsonObject camel_runtime_trace(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Action: enable, disable, or dump") String action) {
        if (action == null || action.isBlank()) {
            throw new ToolCallException("action is required (enable, disable, dump)", null);
        }
        return delegateToRegistry("trace_control", nameOrPid, Map.of("action", action));
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Dump the messages traced in the running Camel application (tracing must be enabled, \
                  see camel_runtime_trace). Same answer as camel_runtime_trace with action dump.""")
    public JsonObject camel_runtime_trace_dump(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("trace_control", nameOrPid, Map.of("action", "dump"));
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  What happened per second in the last minutes (up to 5) of the running application: per route the \
                  exchanges completed, recovered by a redelivery, handled by the error handler and failed, the mean, \
                  p95 and max time, the exchanges in flight and the redelivery attempts; the errors per second by \
                  route and exception (chaosRule names the camel-chaos rule that made one); with camel-chaos the faults \
                  per second of each chaos rule (chaos) and the seconds a rule had a route down (down of the route); \
                  the seconds a circuit breaker was open (2) or half open (1) (circuitBreakers); \
                  and per second the failed, handled and recovered exchanges (a recovered one with the failure it was \
                  redelivered for) and the 3 slowest with the remote calls they made. Each series is oldest first and ends at \
                  the epoch second end. Use it to tell when something went wrong and what it did to which route; \
                  camel_runtime_chaos_report has the chaos rules.""")
    public JsonObject camel_runtime_timeline(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "The number of seconds (default 60, at most what the application records)",
                     required = false) Integer seconds,
            @ToolArg(description = "Route ID or pattern such as orders* (default all routes)",
                     required = false) String routeId,
            @ToolArg(description = "Include the failed, handled, recovered and slowest exchanges (default true)",
                     required = false) Boolean exchanges) {
        Map<String, String> args = new HashMap<>();
        if (seconds != null) {
            args.put("seconds", seconds.toString());
        }
        if (routeId != null && !routeId.isBlank()) {
            args.put("routeId", routeId);
        }
        if (exchanges != null) {
            args.put("exchanges", exchanges.toString());
        }
        return delegateToRegistry("get_timeline", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show top processor statistics: which processors are slowest and most active.")
    public JsonObject camel_runtime_top(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_top_processors", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the inter-route topology showing how routes connect to each other \
                  and to external endpoints. Returns nodes and edges describing the route graph.""")
    public JsonObject camel_runtime_route_topology(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Include live metrics (message counts, throughput) on nodes and edges",
                     required = false) Boolean metric,
            @ToolArg(description = "Include external systems (databases, messaging brokers, etc.) as nodes",
                     required = false) Boolean external) {
        Map<String, String> args = new HashMap<>();
        args.put("metric", metric == null || metric ? "true" : "false");
        args.put("external", external == null || external ? "true" : "false");
        return delegateToRegistry("get_route_topology", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get a JVM thread dump showing thread names, states, and stack traces.")
    public JsonObject camel_runtime_thread_dump(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_thread_dump", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get a class-level heap histogram showing instance counts and byte usage per class. \
                  Useful for diagnosing memory leaks and understanding which classes dominate heap usage.""")
    public JsonObject camel_runtime_heap_histogram(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_heap_histogram", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Write a heap dump (.hprof) file from a running Camel integration for deep memory analysis \
                  with tools like Eclipse MAT, VisualVM, or jhat. The dump is written to the process working directory.""")
    public JsonObject camel_runtime_heap_dump(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "File name for the heap dump, without a directory and without the .hprof extension. "
                                   + "Defaults to heap-dump-<timestamp>",
                     required = false) String name,
            @ToolArg(description = "Whether to dump only live objects (default true). Live dumps trigger a GC first",
                     required = false) String live) {
        Map<String, String> args = new HashMap<>();
        putIfNotBlank(args, "name", name);
        putIfNotBlank(args, "live", live);
        return delegateToRegistry("write_heap_dump", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Diagnose memory leaks in a running Camel integration using Java Flight Recorder (JFR). \
                  JFR samples objects that survive multiple GC cycles and captures their reference chains back to GC roots. \
                  This is lightweight and safe for production, but shows sampled sizes not total heap — \
                  use values to compare classes relative to each other and spot trends, not as absolute numbers. \
                  For deep analysis, use a full heap dump (camel_runtime_heap_dump) with tools like Eclipse MAT. \
                  Use command 'start' to begin a recording (it stops by itself after the duration), 'status' to check \
                  it, 'query' (or 'stop' to end it early) to get the results, and 'compare' to compare the last two \
                  recordings: growth ratios and trends (growing, stable, shrinking, new, gone). \
                  For a trend, start a recording of X seconds, then one of 2X seconds, then compare. \
                  mode 'dual' with 'start' does all of this in one call, which blocks for about three times the \
                  duration (90 s with the default 30): use it only when the client waits that long. \
                  Entries with lowConfidence=true have unreliable growth percentages due to low sample counts or \
                  sample counts that diverge significantly between runs — recommend a longer recording duration.""")
    public JsonObject camel_runtime_memory_leak(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Command: start, stop, status, query, or compare") String command,
            @ToolArg(description = "Recording duration in seconds, for start (default 60, use 0 to stop it manually; "
                                   + "in dual mode default 30, the second recording takes twice as long)",
                     required = false) String duration,
            @ToolArg(description = "Recording mode: single (default, start returns at once) or dual "
                                   + "(two recordings at Xs and 2Xs with trend comparison, the call blocks until done)",
                     required = false) String mode,
            @ToolArg(description = "Include allocation stack traces in results (default false, set true for detailed analysis)",
                     required = false) String stacktrace,
            @ToolArg(description = "Minimum total size in bytes to include a sample (e.g. 1024 for 1KB). Filters out small allocations to reduce noise. Default 1024 (1KB) in dual mode",
                     required = false) String minSize) {
        String cmd = command != null ? command.trim().toLowerCase(Locale.ROOT) : "";
        if (!MEMORY_LEAK_COMMANDS.contains(cmd)) {
            throw new ToolCallException("command is required: start, stop, status, query, or compare", null);
        }
        boolean dual = mode != null && "dual".equalsIgnoreCase(mode.trim());
        if (!dual && mode != null && !mode.isBlank() && !"single".equalsIgnoreCase(mode.trim())) {
            throw new ToolCallException("mode must be single or dual, got: " + mode, null);
        }
        Integer seconds = parseDuration(duration);
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);

        if ("start".equals(cmd) && dual) {
            return doDualJfrRecording(p.pid(), seconds == null || seconds <= 0 ? DUAL_DEFAULT_DURATION : seconds,
                    stacktrace, minSize);
        }

        return runtimeService.executeAction(p.pid(), "jfr-memory-leak", root -> {
            root.put("command", cmd);
            if ("start".equals(cmd)) {
                root.put("duration", String.valueOf(seconds != null ? seconds : SINGLE_DEFAULT_DURATION));
            }
            if (stacktrace != null) {
                root.put("stacktrace", stacktrace);
            }
            if (minSize != null && !minSize.isBlank()) {
                root.put("minSize", minSize);
            }
        });
    }

    static Integer parseDuration(String duration) {
        if (duration == null || duration.isBlank()) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(duration.trim());
            if (seconds < 0) {
                throw new NumberFormatException();
            }
            return seconds;
        } catch (NumberFormatException e) {
            throw new ToolCallException(
                    "duration must be a whole number of seconds (0 or more), got: " + duration,
                    null);
        }
    }

    private JsonObject doDualJfrRecording(long pid, int dur1, String stacktrace, String minSize) {
        int dur2 = dur1 * 2;

        // run 1
        JsonObject r1 = runtimeService.executeAction(pid, "jfr-memory-leak", root -> {
            root.put("command", "start");
            root.put("duration", String.valueOf(dur1));
            if (stacktrace != null) {
                root.put("stacktrace", stacktrace);
            }
            if (minSize != null && !minSize.isBlank()) {
                root.put("minSize", minSize);
            }
        });
        if (r1 == null || !"recording".equals(r1.getString("status"))) {
            throw new ToolCallException("Failed to start recording 1", null);
        }

        waitForJfrRecordingComplete(pid, dur1);

        JsonObject s1 = runtimeService.executeAction(pid, "jfr-memory-leak", root -> {
            root.put("command", "stop");
            if (stacktrace != null) {
                root.put("stacktrace", stacktrace);
            }
            if (minSize != null && !minSize.isBlank()) {
                root.put("minSize", minSize);
            }
        });
        if (s1 == null || !"completed".equals(s1.getString("status"))) {
            throw new ToolCallException("Recording 1 did not complete", null);
        }

        // run 2
        JsonObject r2 = runtimeService.executeAction(pid, "jfr-memory-leak", root -> {
            root.put("command", "start");
            root.put("duration", String.valueOf(dur2));
            if (stacktrace != null) {
                root.put("stacktrace", stacktrace);
            }
            if (minSize != null && !minSize.isBlank()) {
                root.put("minSize", minSize);
            }
        });
        if (r2 == null || !"recording".equals(r2.getString("status"))) {
            throw new ToolCallException("Failed to start recording 2", null);
        }

        waitForJfrRecordingComplete(pid, dur2);

        JsonObject s2 = runtimeService.executeAction(pid, "jfr-memory-leak", root -> {
            root.put("command", "stop");
            if (stacktrace != null) {
                root.put("stacktrace", stacktrace);
            }
            if (minSize != null && !minSize.isBlank()) {
                root.put("minSize", minSize);
            }
        });
        if (s2 == null || !"completed".equals(s2.getString("status"))) {
            throw new ToolCallException("Recording 2 did not complete", null);
        }

        // compare
        return runtimeService.executeAction(pid, "jfr-memory-leak", root -> {
            root.put("command", "compare");
            if (stacktrace != null) {
                root.put("stacktrace", stacktrace);
            }
            if (minSize != null && !minSize.isBlank()) {
                root.put("minSize", minSize);
            }
        });
    }

    /**
     * Waits until the recording stopped by itself: sleeps for the time the console says is left, then checks again (a
     * recording ends a little after its duration, as it is parsed).
     */
    private void waitForJfrRecordingComplete(long pid, int dur) {
        long deadline = System.currentTimeMillis() + (dur + 30) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            JsonObject status = runtimeService.executeAction(pid, "jfr-memory-leak", root -> {
                root.put("command", "status");
            });
            if (status == null || !"recording".equals(status.getString("status"))) {
                return;
            }
            long remaining = status.getLongOrDefault("remainingMs", 1000L);
            long wait = Math.min(Math.max(remaining, 500L), Math.max(1L, deadline - System.currentTimeMillis()));
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ToolCallException("Interrupted while waiting for recording", null);
            }
        }
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the message history trace of the last completed exchange, with its route path, processors \
                  visited, headers, body, and timing. Recorded when message history is on and the tracer is \
                  enabled or on standby: the default of camel run (dev profile); otherwise set \
                  camel.main.message-history=true and camel.trace.standby=true.""")
    public JsonObject camel_runtime_history(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateToRegistry("get_history", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Receive (consume) a message from an endpoint of the running application, the complement of \
                  camel_runtime_send: starts a consumer on the endpoint, waits for a message, returns it and stops \
                  the consumer again. Returns one message by default; limit returns up to that many, and received \
                  counts every message the consumer took, returned or not. The consumer competes with the routes \
                  that consume the endpoint, so the messages it takes do not reach them (a queue or topic message \
                  is gone); one call at a time per process. To look at queued messages without taking them use \
                  camel_runtime_browse.""")
    public JsonObject camel_runtime_receive(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Endpoint URI to receive from, or a pattern matching an endpoint the application "
                                   + "sends to, e.g. seda:orders or kafka*") String endpoint,
            @ToolArg(description = "How long to wait for a message in seconds (default 10, at most 60)",
                     required = false) Integer timeoutSeconds,
            @ToolArg(description = "How many of the messages the consumer takes to return (default 1, at least 1)",
                     required = false) Integer limit) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new ToolCallException("endpoint is required", null);
        }
        if (limit != null && limit < 1) {
            throw new ToolCallException("limit must be at least 1: " + limit, null);
        }
        int timeout = timeoutSeconds == null || timeoutSeconds <= 0
                ? RECEIVE_DEFAULT_TIMEOUT : Math.min(timeoutSeconds, RECEIVE_MAX_TIMEOUT);
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return receive(p.pid(), endpoint, timeout * 1000L, limit != null ? limit : 1);
    }

    /**
     * Enables the receive console of the process for the endpoint, waits for the messages the CLI connector writes to
     * the receive file, and disables it again whatever happens. One call at a time per process: the console is shared,
     * and disabling it stops every consumer it runs.
     */
    JsonObject receive(long pid, String endpoint, long timeoutMs, int limit) {
        ReentrantLock lock = receiveLocks.computeIfAbsent(pid, k -> new ReentrantLock());
        boolean locked;
        try {
            locked = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolCallException("Interrupted while waiting for a message", null);
        }
        if (!locked) {
            throw new ToolCallException(
                    "Another camel_runtime_receive call is receiving from this process (the receive console is shared,"
                                        + " and stopping it would stop that call's consumer): call again when it has finished",
                    null);
        }
        try {
            return receiveExclusively(pid, endpoint, timeoutMs, limit);
        } finally {
            lock.unlock();
        }
    }

    private JsonObject receiveExclusively(long pid, String endpoint, long timeoutMs, int limit) {
        JsonObject state = runtimeService.readStatusSection(pid, "receive");
        Collection<?> active = state.getCollection("endpoints");
        if (Boolean.TRUE.equals(state.getBoolean("enabled")) && active != null && !active.isEmpty()) {
            // disabling would stop those consumers too
            throw new ToolCallException(
                    "The process is already receiving from " + active
                                        + " (camel cmd receive); stop that first with: camel cmd receive --action=stop",
                    null);
        }
        // the console numbers every message it takes (uid) from one counter, which the status reports as total:
        // the messages above the count before this call are the ones its consumer takes
        long startTotal = longValue(state, "total", 0);
        long offset = runtimeService.receiveFileSize(pid);
        JsonObject started = runtimeService.executeAction(pid, "receive", root -> {
            root.put("enabled", "true");
            root.put("endpoint", endpoint);
        });
        List<JsonObject> messages;
        try {
            messages = awaitMessages(pid, endpoint, started, offset, startTotal, timeoutMs);
        } catch (RuntimeException e) {
            try {
                stopReceiving(pid);
            } catch (RuntimeException stop) {
                throw new ToolCallException(
                        message(e) + ". The consumer could not be stopped (" + message(stop)
                                            + "): stop it with: camel cmd receive --action=stop",
                        e);
            }
            throw e;
        }
        JsonObject answer = new JsonObject();
        answer.put("endpoint", started.getString("url"));
        Long taken = null;
        try {
            stopReceiving(pid);
            // the connector writes the queue of the console every second tick, so the messages taken between the dump
            // read above and the stop come later: the status after the stop says how many there are
            taken = settle(pid, offset, startTotal, messages);
        } catch (RuntimeException e) {
            // the messages are taken from the endpoint already: return them, and say the consumer may still run
            answer.put("warning", "The consumer could not be stopped (" + message(e)
                                  + ") and may still take messages from the endpoint: stop it with: "
                                  + "camel cmd receive --action=stop");
        }
        long received = taken != null ? taken : messages.size();
        int returned = (int) Math.min(limit, messages.size());
        answer.put("received", received);
        answer.put("messages", messages.subList(0, returned));
        if (received == 0) {
            answer.put("note", "No message arrived within " + (timeoutMs / 1000) + " seconds");
            return answer;
        }
        long notReturned = received - returned;
        StringBuilder note = new StringBuilder();
        if (notReturned > 0) {
            answer.put("notReturned", notReturned);
            note.append(notReturned).append(" more message(s) were taken from the endpoint by this call and are not")
                    .append(" returned");
            if (messages.size() > returned) {
                note.append(" (raise limit to see them)");
            }
            if (received > messages.size()) {
                note.append("; ").append(received - messages.size())
                        .append(" of them were not written by the process within the wait after the stop");
            }
            note.append('.');
        }
        if (taken == null) {
            note.append(" The process did not confirm the stop, so received may be short of what the consumer took.");
        }
        if (!note.isEmpty()) {
            answer.put("note", note.toString().trim());
        }
        return answer;
    }

    /**
     * Waits for the first dump of the receive file that holds a message of this call, or for the timeout; returns the
     * messages found (none on timeout).
     */
    private List<JsonObject> awaitMessages(
            long pid, String endpoint, JsonObject started, long offset, long startTotal, long timeoutMs) {
        if (started.getString("error") != null) {
            throw new ToolCallException("Cannot receive from " + endpoint + ": " + started.getString("error"), null);
        }
        if (started.getString("url") == null) {
            throw new ToolCallException(
                    "No endpoint matches " + endpoint
                                        + " (a pattern matches the endpoints the application sends to; "
                                        + "camel_runtime_endpoints lists them)",
                    null);
        }
        StopWatch watch = new StopWatch();
        while (true) {
            List<JsonObject> messages = ownMessages(pid, offset, startTotal, null);
            if (!messages.isEmpty() || watch.taken() >= timeoutMs) {
                return messages;
            }
            sleep();
        }
    }

    /**
     * After the stop: waits until the status of the process shows the console disabled, so its total is the number of
     * messages this call took, and until the receive file holds them (the connector dumps the queue every second tick).
     * Returns the number taken, or null when the process did not confirm the stop within the wait; the list of messages
     * is completed with the ones dumped after the stop.
     */
    private Long settle(long pid, long offset, long startTotal, List<JsonObject> messages) {
        StopWatch watch = new StopWatch();
        Long taken = null;
        while (true) {
            JsonObject state = runtimeService.readStatusSection(pid, "receive");
            if (Boolean.FALSE.equals(state.getBoolean("enabled")) && state.get("total") != null) {
                long endTotal = longValue(state, "total", startTotal);
                taken = Math.max(endTotal - startTotal, messages.size());
                List<JsonObject> all = ownMessages(pid, offset, startTotal, endTotal);
                if (all.size() >= messages.size()) {
                    messages.clear();
                    messages.addAll(all);
                }
                if (messages.size() >= taken) {
                    return taken;
                }
            }
            if (watch.taken() >= receiveSettleMs) {
                return taken;
            }
            sleep();
        }
    }

    /** The messages of the receive file after the offset that this call's consumer took: uid above the start count. */
    private List<JsonObject> ownMessages(long pid, long offset, long startTotal, Long endTotal) {
        List<JsonObject> messages = new ArrayList<>(runtimeService.readReceivedMessages(pid, offset));
        messages.removeIf(m -> {
            Object uid = m.get("uid");
            if (!(uid instanceof Number n)) {
                return false;
            }
            return n.longValue() <= startTotal || endTotal != null && n.longValue() > endTotal;
        });
        return messages;
    }

    /** An action without an endpoint stops the consumers of the receive console. */
    private void stopReceiving(long pid) {
        runtimeService.executeAction(pid, "receive", null);
    }

    private static void sleep() {
        try {
            TimeUnit.MILLISECONDS.sleep(RECEIVE_POLL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolCallException("Interrupted while waiting for a message", null);
        }
    }

    private static long longValue(JsonObject json, String key, long defaultValue) {
        return json.get(key) instanceof Number n ? n.longValue() : defaultValue;
    }

    /** The message of an exception, or its name when it has none. */
    private static String message(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Browse messages in a Camel endpoint (e.g., browse messages queued in a SEDA endpoint).")
    public JsonObject camel_runtime_browse(
            @ToolArg(description = NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Endpoint URI to browse") String endpoint,
            @ToolArg(description = "Maximum number of messages to return (default: 50)", required = false) Integer limit) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new ToolCallException("endpoint is required", null);
        }
        Map<String, String> args = new HashMap<>();
        args.put("endpoint", endpoint);
        if (limit != null) {
            args.put("limit", limit.toString());
        }
        return delegateToRegistry("browse_endpoint", nameOrPid, args);
    }

    // ---- camel-chaos (the chaos tool group): the rules and the report, and the chaos action of the CLI connector ----
    // add, change and load are destructive (a rule makes messages fail, wait or get lost) and the clients ask first
    // (ToolConfirms.chaos and chaosLoad); suggest only reads the routes; remove and clear change the integration only back towards normal, and the report keeps
    // the removed rules, so they are not destructive, as trace start and stop are not

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  List the chaos rules of the Camel application (camel-chaos, development only): id, version, the \
                  rule and the rule in plain words (description), state (active, waiting for its start, or ended), \
                  where it can fire in the application now (hooks), matches, faults fired, time left, the \
                  expectation, and the live state: observed rate next to the rate of the rule, when it last fired, \
                  faults per second over the last minute, and the effect of its faults (exchanges completed, \
                  recovered by redelivery, handled by the error handler, failed, in flight); or why chaos is off. The rules come from the status of the application, written every second.""")
    public JsonObject camel_runtime_chaos_list(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateChaos("chaos_list", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the chaos report of the Camel application: the verdict (PASS or FAIL) and whether it is final, \
                  the seed, every version of every rule with what it fired and its live state, the expectations (a \
                  pending one is not decided yet), the counts per route and the last 20 faults (rule, fault, route, \
                  endpoint or step, exchange id); or why chaos is off.""")
    public JsonObject camel_runtime_chaos_report(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateChaos("chaos_report", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Check chaos rules without adding them (as camel cmd chaos-add --dry-run): each rule in plain words, \
                  where it can fire, warnings, and what it would hit now (hits: the sends, polls, routes or steps, \
                  each with its route). A rule that is not valid is refused with the reason; nothing changes.""")
    public JsonObject camel_runtime_chaos_check(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "The rule in its one-line text form, as an entry of camel.chaos.rules; several"
                                   + " rules separated by ;") String rule) {
        requireArg("rule", rule);
        return delegateChaos("chaos_check", nameOrPid, Map.of("rule", rule));
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  What to try (as camel cmd chaos-suggest): chaos rules suggested per route of the Camel application \
                  from what the routes do, the most worth trying first, such as an HTTP call without a circuit \
                  breaker, a dead letter channel or a hand-off to another route over seda. Each has the route, the \
                  kind of fault, the rule, why to try it, tried=true when a rule of that kind has already faulted \
                  the route, and feed: how to send messages into the route (kind self, http, file or send, a hint \
                  such as the command, and real=true when that goes to the real system rather than a stub). Two \
                  faults at once (kind combined, after the single ones) have rules: all the rules to add together \
                  (camel_runtime_chaos_add with them separated by ;). \
                  Nothing is added: check a rule with camel_runtime_chaos_check and add it with \
                  camel_runtime_chaos_add.""")
    public JsonObject camel_runtime_chaos_suggest(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateChaos("chaos_suggest", nameOrPid, Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Load a chaos scenario into the Camel application (as camel cmd chaos-load): the rules there are \
                  now are removed and the report starts again, then the rules of the scenario are added, all or \
                  none (a line that is not valid is refused with the reason, and nothing changes). Returns the ids \
                  and each rule in plain words, and warnings for a rule that matches nothing in the application \
                  now.""")
    public JsonObject camel_runtime_chaos_load(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "The scenario, the text of a .chaos file: one rule per line, # comments, and"
                                   + " optionally a seed=<number> line for the same sequence of faults each run") String scenario,
            @ToolArg(description = "The name of the scenario in the report, such as outage (default scenario)",
                     required = false) String scenarioName) {
        requireArg("scenario", scenario);
        Map<String, String> args = new HashMap<>();
        args.put("scenario", scenario);
        if (scenarioName != null) {
            args.put("scenarioName", scenarioName);
        }
        return delegateChaos("chaos_load", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Add a chaos rule to the Camel application, in the one-line form of camel.chaos.rules, e.g. \
                  'endpoint=kafka:orders* error rate=0.1 until=5m'; several rules separated by ; are added together \
                  (all or none). Returns the ids of the rules, each rule in plain words (description for one \
                  rule, descriptions for several) to check it does what was meant, and warnings for a rule that \
                  matches nothing in the application now (often a typo: fix it with camel_runtime_chaos_change). \
                  Chaos must be on in the \
                  application; a rule that is not valid is refused with the reason.""")
    public JsonObject camel_runtime_chaos_add(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "The rule in its one-line text form, as an entry of camel.chaos.rules; several"
                                   + " rules separated by ;") String rule) {
        requireArg("rule", rule);
        return delegateChaos("chaos_add", nameOrPid, Map.of("rule", rule));
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Change a chaos rule of the Camel application: the rule with the id gets the new one-line rule and \
                  one more version; the report keeps the version it replaces. Returns the id, the new version and \
                  the new rule in plain words (description), and warnings when the new rule matches nothing in the \
                  application now.""")
    public JsonObject camel_runtime_chaos_change(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "The id of the rule, such as r1 (camel_runtime_chaos_list shows them)") String id,
            @ToolArg(description = "The new rule in its one-line text form") String rule) {
        requireArg("id", id);
        requireArg("rule", rule);
        return delegateChaos("chaos_change", nameOrPid, Map.of("id", id, "rule", rule));
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Remove a chaos rule of the Camel application: its faults stop, and the report keeps what it did.")
    public JsonObject camel_runtime_chaos_remove(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "The id of the rule, such as r1 (camel_runtime_chaos_list shows them)") String id) {
        requireArg("id", id);
        return delegateChaos("chaos_remove", nameOrPid, Map.of("id", id));
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Remove all chaos rules of the Camel application: no more faults, and the report keeps what they \
                  did (with reset=true the report starts again, for a new experiment). Returns how many were \
                  removed.""")
    public JsonObject camel_runtime_chaos_clear(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "Also start a new report: the removed rules, the counts per route and the verdict"
                                   + " are gone (default false)",
                     required = false) Boolean reset) {
        return delegateChaos("chaos_clear", nameOrPid,
                Boolean.TRUE.equals(reset) ? Map.of("reset", "true") : Map.of());
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Start a chaos exploration in the Camel application (as camel cmd chaos-explore-start): a time to \
                  try chaos rules in, that camel-chaos enforces. While it runs, a rule ends at the deadline at the \
                  latest, and at the deadline the rules are removed, whatever happens. Its journal (the baseline, \
                  the rules tried and the experiments) is in camel_runtime_chaos_report. Run experiments in it with \
                  camel_runtime_chaos_experiment, and finish it with camel_runtime_chaos_explore_finish. One runs \
                  at a time.""")
    public JsonObject camel_runtime_chaos_explore_start(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "How long it runs, between 10s and 2h (default 10m)", required = false) String budget,
            @ToolArg(description = "What it is about, such as a route", required = false) String focus) {
        Map<String, String> args = new HashMap<>();
        putIfSet(args, "budget", budget);
        putIfSet(args, "focus", focus);
        return delegateChaos("chaos_explore_start", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Run one chaos experiment in the running exploration of the Camel application (as camel cmd \
                  chaos-experiment), in one call: it adds the rules (one of them with an expectation, expect=, that \
                  decides the hypothesis), sends the messages of feed when given (as camel_runtime_send, with body, \
                  headers, repeat and interval), waits until the expectations are decided or wait is up, and ends \
                  the experiment, which removes its rules. Returns the verdict (FAIL: an expectation is unmet; PASS: \
                  all are met; INCONCLUSIVE: the wait was up before one was decided, so wait longer than its \
                  within=), the expectations with their actual values, the faults per rule, what the routes did \
                  meanwhile, the traffic sent, the time left of the exploration (timeLeft, millis), and warnings \
                  for a rule that matched nothing (fix it and run again). camel_runtime_chaos_suggest has rules to \
                  try and the feed of each route.""")
    public JsonObject camel_runtime_chaos_experiment(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid,
            @ToolArg(description = "The rules in their one-line text form, separated by ;, one of them with expect=,"
                                   + " such as 'route=refunds error rate=every:5 expect=refunds:failed<=0 within=30s'") String rule,
            @ToolArg(description = "What the experiment tests, in a few words", required = false) String hypothesis,
            @ToolArg(description = "The endpoint, or the id of the route, to send the messages to (as the feed of"
                                   + " camel_runtime_chaos_suggest); a route that serves HTTP gets HTTP requests, or"
                                   + " give the path to call (POST /orders/42); leave it out when the route feeds itself",
                     required = false) String feed,
            @ToolArg(description = "The body of the messages (file:<path>, or file:<folder> for a message per file)",
                     required = false) String body,
            @ToolArg(description = "The headers of the messages as key=value pairs separated by newlines",
                     required = false) String headers,
            @ToolArg(description = "How many messages to send in all (at most 10000)", required = false) Integer repeat,
            @ToolArg(description = "The pause between two messages in millis, or a range such as 100..500",
                     required = false) String interval,
            @ToolArg(description = "The longest wait for the verdict after the messages were sent, such as 90s"
                                   + " (default 2m, at most 10m); it is at least the within= of the expectations",
                     required = false) String wait,
            @ToolArg(description = "What to note with the experiment in the journal", required = false) String note) {
        requireArg("rule", rule);
        Map<String, String> args = new HashMap<>();
        args.put("rule", rule);
        putIfSet(args, "hypothesis", hypothesis);
        putIfSet(args, "feed", feed);
        putIfSet(args, "body", body);
        putIfSet(args, "headers", headers);
        if (repeat != null) {
            args.put("repeat", repeat.toString());
        }
        putIfSet(args, "interval", interval);
        putIfSet(args, "wait", wait);
        putIfSet(args, "note", note);
        return delegateChaos("chaos_experiment", nameOrPid, args);
    }

    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Finish the chaos exploration of the Camel application before its deadline (as camel cmd \
                  chaos-explore-finish): the rules are removed (an open experiment is ended), and the journal stays \
                  in camel_runtime_chaos_report. Returns the journal: the rules tried, the experiments with their \
                  verdicts, and the baseline.""")
    public JsonObject camel_runtime_chaos_explore_finish(
            @ToolArg(description = CHAOS_NAME_OR_PID_DESC, required = false) String nameOrPid) {
        return delegateChaos("chaos_explore_finish", nameOrPid, Map.of());
    }

    private static void putIfSet(Map<String, String> args, String key, String value) {
        if (value != null && !value.isBlank()) {
            args.put(key, value);
        }
    }

    /**
     * As {@link #delegateToRegistry(String, String, Map)}, for a chaos tool: without a name, the only integration
     * running, else the only one with chaos switched on ({@link RuntimeService#findChaosProcess(String)}).
     */
    private JsonObject delegateChaos(String registryToolName, String nameOrPid, Map<String, String> args) {
        return delegateToRegistry(registryToolName, runtimeService.findChaosProcess(nameOrPid), args);
    }

    private static void requireArg(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new ToolCallException(name + " is required", null);
        }
    }
}
