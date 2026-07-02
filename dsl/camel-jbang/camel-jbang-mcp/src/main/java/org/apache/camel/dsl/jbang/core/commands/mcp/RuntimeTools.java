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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.json.JsonArray;
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
@ApplicationScoped
public class RuntimeTools {

    private static final String NAME_OR_PID_DESC
            = "Name or PID of the Camel process. Leave empty to auto-detect (works when exactly one Camel process is running)";

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
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
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

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  List all running Camel processes that can be inspected. \
                  Returns PID, name, and context name for each discovered process.""")
    public List<RuntimeService.ProcessInfo> camel_runtime_processes() {
        return runtimeService.discoverProcesses();
    }

    // ---- Read-only status tools ----

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get Camel context information: name, version, state, uptime, route count, exchange statistics.")
    public JsonObject camel_runtime_context(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_context", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List Camel routes with their state, uptime, messages processed, last error, and throughput statistics.")
    public JsonObject camel_runtime_routes(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_routes", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get health check status for the Camel application.")
    public JsonObject camel_runtime_health(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_health", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List all endpoints registered in the Camel context with their URIs and usage statistics.")
    public JsonObject camel_runtime_endpoints(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_endpoints", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show currently in-flight exchanges (messages being processed).")
    public JsonObject camel_runtime_inflight(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_inflight", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show blocked exchanges that are stuck or waiting.")
    public JsonObject camel_runtime_blocked(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_blocked", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show exchange variables in the Camel context.")
    public JsonObject camel_runtime_variables(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_variables", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show consumer statistics (polling consumers, event-driven consumers).")
    public JsonObject camel_runtime_consumers(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_consumers", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show configuration properties of the running Camel application.")
    public JsonObject camel_runtime_properties(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_properties", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show services registered in the Camel service registry.")
    public JsonObject camel_runtime_services(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_services", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show JVM memory usage (heap/non-heap), garbage collection stats, and thread counts.")
    public JsonObject camel_runtime_memory(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_memory", nameOrPid, Map.of());
    }

    // ---- Interactive action tools ----

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get the source code of routes in the running Camel application.")
    public JsonObject camel_runtime_route_source(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Filter source files by name (supports wildcards)") String filter) {
        return delegateToRegistry("get_route_source", nameOrPid,
                Map.of("filter", filter != null ? filter : "*"));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Dump route definitions in XML, YAML, or Java DSL format.")
    public JsonObject camel_runtime_route_dump(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Route ID to dump (use * for all routes)") String routeId,
            @ToolArg(description = "Output format: xml, yaml, or java (default: yaml)") String format) {
        Map<String, String> args = new HashMap<>();
        args.put("routeId", routeId != null ? routeId : "*");
        args.put("format", format != null ? format : "yaml");
        return delegateToRegistry("get_route_dump", nameOrPid, args);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show the route structure as a tree of processors.")
    public JsonObject camel_runtime_route_structure(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Route ID to inspect (use * for all routes)") String routeId) {
        return delegateToRegistry("get_route_structure", nameOrPid,
                Map.of("routeId", routeId != null ? routeId : "*"));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = "Control a route: start, stop, suspend, or resume it.")
    public JsonObject camel_runtime_route_control(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
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

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = "Send a test message to a Camel endpoint in the running application.")
    public JsonObject camel_runtime_send(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Endpoint URI to send to (e.g., direct:myRoute, seda:queue)") String endpoint,
            @ToolArg(description = "Message body to send") String body,
            @ToolArg(description = "Message headers as key=value pairs separated by newlines") String headers) {
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
        return delegateToRegistry("send_message", nameOrPid, args);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Enable, disable, or dump message tracing for the running Camel application.")
    public JsonObject camel_runtime_trace(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Action: enable, disable, or dump") String action) {
        if (action == null || action.isBlank()) {
            throw new ToolCallException("action is required (enable, disable, dump)", null);
        }
        return delegateToRegistry("trace_control", nameOrPid, Map.of("action", action));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show top processor statistics: which processors are slowest and most active.")
    public JsonObject camel_runtime_top(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_top_processors", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Evaluate an expression in the given language (e.g., simple, jsonpath, xpath) \
                  against the Camel context.""")
    public JsonObject camel_runtime_eval(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Expression language (e.g., simple, jsonpath, xpath, jq)") String language,
            @ToolArg(description = "Expression to evaluate") String expression) {
        if (language == null || language.isBlank()) {
            throw new ToolCallException("language is required", null);
        }
        if (expression == null || expression.isBlank()) {
            throw new ToolCallException("expression is required", null);
        }
        Map<String, String> args = new HashMap<>();
        args.put("language", language);
        args.put("expression", expression);
        return delegateToRegistry("eval_expression", nameOrPid, args);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the inter-route topology showing how routes connect to each other \
                  and to external endpoints. Returns nodes and edges describing the route graph.""")
    public JsonObject camel_runtime_route_topology(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Include live metrics (message counts, throughput) on nodes and edges") Boolean metric,
            @ToolArg(description = "Include external systems (databases, messaging brokers, etc.) as nodes") Boolean external) {
        Map<String, String> args = new HashMap<>();
        args.put("metric", metric == null || metric ? "true" : "false");
        args.put("external", external == null || external ? "true" : "false");
        return delegateToRegistry("get_route_topology", nameOrPid, args);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get captured routing errors from the running Camel application. \
                  Returns error details including exception, exchange context, and route information.""")
    public JsonObject camel_runtime_errors(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_errors", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get a JVM thread dump showing thread names, states, and stack traces.")
    public JsonObject camel_runtime_thread_dump(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_thread_dump", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get a class-level heap histogram showing instance counts and byte usage per class. \
                  Useful for diagnosing memory leaks and understanding which classes dominate heap usage.""")
    public JsonObject camel_runtime_heap_histogram(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_heap_histogram", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Diagnose memory leaks in a running Camel integration using Java Flight Recorder (JFR). \
                  JFR samples objects that survive multiple GC cycles and captures their reference chains back to GC roots. \
                  This is lightweight and safe for production, but shows sampled sizes not total heap — \
                  use values to compare classes relative to each other and spot trends, not as absolute numbers. \
                  For deep analysis, use a full heap dump (jmap -dump:live) with tools like Eclipse MAT or jhat. \
                  Use command 'start' to begin recording, 'stop' to stop and get results, \
                  'status' to check recording state, and 'query' to retrieve cached results from the last recording. \
                  Use mode 'dual' with 'start' to run two sequential recordings (Xs then 2Xs) and automatically \
                  compare trends — returns growth ratios and trend classifications (growing, stable, shrinking, new, gone).""")
    public JsonObject camel_runtime_memory_leak(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Command: start, stop, status, or query") String command,
            @ToolArg(description = "Recording duration in seconds (only for start command, default 60, use 0 for manual stop)") String duration,
            @ToolArg(description = "Recording mode: dual (default, two recordings at Xs and 2Xs with trend comparison) or single (one recording)") String mode,
            @ToolArg(description = "Include allocation stack traces in results (default false, set true for detailed analysis)") String stacktrace,
            @ToolArg(description = "Minimum total size in bytes to include a sample (e.g. 1024 for 1KB). Filters out small allocations to reduce noise. Default 1024 (1KB) in dual mode") String minSize) {
        if (command == null || command.isBlank()) {
            throw new ToolCallException("command is required (start, stop, status, or query)", null);
        }
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);

        if ("start".equals(command) && "dual".equalsIgnoreCase(mode)) {
            return doDualJfrRecording(p.pid(), duration, stacktrace, minSize);
        }

        return runtimeService.executeAction(p.pid(), "jfr-memory-leak", root -> {
            root.put("command", command);
            if ("start".equals(command) && duration != null && !duration.isBlank()) {
                root.put("duration", duration);
            }
            if (stacktrace != null) {
                root.put("stacktrace", stacktrace);
            }
            if (minSize != null && !minSize.isBlank()) {
                root.put("minSize", minSize);
            }
        });
    }

    private JsonObject doDualJfrRecording(long pid, String duration, String stacktrace, String minSize) {
        int dur = 30;
        if (duration != null && !duration.isBlank()) {
            dur = Integer.parseInt(duration);
        }
        if (dur <= 0) {
            dur = 30;
        }
        int dur1 = dur;
        int dur2 = dur * 2;

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

        try {
            Thread.sleep((dur1 + 3) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolCallException("Interrupted during recording 1", null);
        }

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

        try {
            Thread.sleep((dur2 + 3) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolCallException("Interrupted during recording 2", null);
        }

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

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the message history trace of the last completed exchange. \
                  This is always captured (no need to enable tracing) and shows the single most recent exchange \
                  with its route path, processors visited, headers, body, and timing.""")
    public JsonObject camel_runtime_history(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("get_history", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Initiate graceful shutdown of a running Camel application. \
                  The application will finish processing in-flight exchanges before stopping.""")
    public JsonObject camel_runtime_stop(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        return delegateToRegistry("stop_application", nameOrPid, Map.of());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = """
                  Receive (poll) a message from a Camel endpoint in the running application. \
                  This is the complement to camel_runtime_send — it consumes one message from the endpoint.""")
    public JsonObject camel_runtime_receive(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Endpoint URI to receive from") String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new ToolCallException("endpoint is required", null);
        }
        // receive is MCP-only (no ToolRegistry equivalent) — call RuntimeService directly
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "receive", root -> {
            root.put("enabled", "true");
            root.put("endpoint", endpoint);
        });
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Browse messages in a Camel endpoint (e.g., browse messages queued in a SEDA endpoint).")
    public JsonObject camel_runtime_browse(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Endpoint URI to browse") String endpoint,
            @ToolArg(description = "Maximum number of messages to return (default: 50)") Integer limit) {
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
}
