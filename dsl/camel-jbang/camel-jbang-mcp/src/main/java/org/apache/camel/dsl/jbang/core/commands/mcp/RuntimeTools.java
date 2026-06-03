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

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP tools for inspecting and interacting with running Camel applications.
 * <p>
 * These tools communicate with running Camel processes via the file-based IPC protocol in ~/.camel/. Status data is
 * read from periodic snapshots; interactive commands use the multi-client action file protocol.
 */
@ApplicationScoped
public class RuntimeTools {

    private static final String NAME_OR_PID_DESC
            = "Name or PID of the Camel process. Leave empty to auto-detect (works when exactly one Camel process is running)";

    @Inject
    RuntimeService runtimeService;

    // ---- Process discovery ----

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  List all running Camel processes that can be inspected. \
                  Returns PID, name, and context name for each discovered process.""")
    public List<RuntimeService.ProcessInfo> camel_runtime_processes() {
        return runtimeService.discoverProcesses();
    }

    // ---- Read-only tools (from status file) ----

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get Camel context information: name, version, state, uptime, route count, exchange statistics.")
    public JsonObject camel_runtime_context(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "context");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List Camel routes with their state, uptime, messages processed, last error, and throughput statistics.")
    public JsonObject camel_runtime_routes(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "routes");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get health check status for the Camel application.")
    public JsonObject camel_runtime_health(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "healthChecks");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List all endpoints registered in the Camel context with their URIs and usage statistics.")
    public JsonObject camel_runtime_endpoints(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "endpoints");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show currently in-flight exchanges (messages being processed).")
    public JsonObject camel_runtime_inflight(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "inflight");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show blocked exchanges that are stuck or waiting.")
    public JsonObject camel_runtime_blocked(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "blocked");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show exchange variables in the Camel context.")
    public JsonObject camel_runtime_variables(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "variables");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show consumer statistics (polling consumers, event-driven consumers).")
    public JsonObject camel_runtime_consumers(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "consumers");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show configuration properties of the running Camel application.")
    public JsonObject camel_runtime_properties(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "properties");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show services registered in the Camel service registry.")
    public JsonObject camel_runtime_services(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readStatusSection(p.pid(), "services");
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show JVM memory usage (heap/non-heap), garbage collection stats, and thread counts.")
    public JsonObject camel_runtime_memory(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        JsonObject status = runtimeService.readStatus(p.pid());
        if (status == null) {
            return new JsonObject();
        }
        JsonObject result = new JsonObject();
        if (status.containsKey("memory")) {
            result.put("memory", status.get("memory"));
        }
        if (status.containsKey("gc")) {
            result.put("gc", status.get("gc"));
        }
        if (status.containsKey("threads")) {
            result.put("threads", status.get("threads"));
        }
        return result;
    }

    // ---- Interactive tools (via action file IPC) ----

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get the source code of routes in the running Camel application.")
    public JsonObject camel_runtime_route_source(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Filter source files by name (supports wildcards)") String filter) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "source", root -> {
            root.put("filter", filter != null ? filter : "*");
        });
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Dump route definitions in XML or YAML format.")
    public JsonObject camel_runtime_route_dump(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Route ID to dump (use * for all routes)") String routeId,
            @ToolArg(description = "Output format: xml or yaml (default: yaml)") String format) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "route-dump", root -> {
            root.put("id", routeId != null ? routeId : "*");
            root.put("format", format != null ? format : "yaml");
        });
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show the route structure as a tree of processors.")
    public JsonObject camel_runtime_route_structure(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Route ID to inspect (use * for all routes)") String routeId) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "route-structure", root -> {
            root.put("id", routeId != null ? routeId : "*");
        });
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
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "route", root -> {
            root.put("id", routeId);
            root.put("command", command);
        });
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
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "send", root -> {
            root.put("endpoint", endpoint);
            if (body != null) {
                root.put("body", body);
            }
            if (headers != null) {
                root.put("headers", headers);
            }
        });
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Enable, disable, or dump message tracing for the running Camel application.")
    public JsonObject camel_runtime_trace(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Action: enable, disable, or dump") String action) {
        if (action == null || action.isBlank()) {
            throw new ToolCallException("action is required (enable, disable, dump)", null);
        }
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "trace", root -> {
            switch (action.toLowerCase()) {
                case "enable" -> root.put("enabled", "true");
                case "disable" -> root.put("enabled", "false");
                case "dump" -> root.put("dump", "true");
                default -> throw new ToolCallException(
                        "Unknown trace action: " + action
                                                       + ". Use 'enable', 'disable', or 'dump'.",
                        null);
            }
        });
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Show top processor statistics: which processors are slowest and most active.")
    public JsonObject camel_runtime_top(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "top-processors", null);
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
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "eval", root -> {
            root.put("language", language);
            root.put("expression", expression);
        });
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the inter-route topology showing how routes connect to each other \
                  and to external endpoints. Returns nodes and edges describing the route graph.""")
    public JsonObject camel_runtime_route_topology(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Include live metrics (message counts, throughput) on nodes and edges") Boolean metric,
            @ToolArg(description = "Include external systems (databases, messaging brokers, etc.) as nodes") Boolean external) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "route-topology", root -> {
            root.put("metric", metric != null && metric ? "true" : "false");
            root.put("external", external != null && external ? "true" : "false");
        });
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get captured routing errors from the running Camel application. \
                  Returns error details including exception, exchange context, and route information.""")
    public JsonObject camel_runtime_errors(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readErrorFile(p.pid());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get a JVM thread dump showing thread names, states, and stack traces.")
    public JsonObject camel_runtime_thread_dump(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "thread-dump", null);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = """
                  Get the message history trace of the last completed exchange. \
                  This is always captured (no need to enable tracing) and shows the single most recent exchange \
                  with its route path, processors visited, headers, body, and timing.""")
    public JsonObject camel_runtime_history(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.readHistoryFile(p.pid());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = """
                  Initiate graceful shutdown of a running Camel application. \
                  The application will finish processing in-flight exchanges before stopping.""")
    public JsonObject camel_runtime_stop(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid) {
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        String result = runtimeService.stopApplication(p.pid());
        JsonObject response = new JsonObject();
        response.put("result", result);
        return response;
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
        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        return runtimeService.executeAction(p.pid(), "browse", root -> {
            root.put("endpoint", endpoint);
            root.put("limit", limit != null ? limit : 50);
        });
    }
}
