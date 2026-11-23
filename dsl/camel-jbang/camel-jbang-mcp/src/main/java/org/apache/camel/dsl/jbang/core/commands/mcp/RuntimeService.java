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

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.LocalRuntimeAccess;
import org.apache.camel.dsl.jbang.core.common.RuntimeAccess;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeTarget;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * CDI facade for discovering and operating on local and authenticated remote Camel runtimes. The selected identity is
 * an opaque {@link RuntimeTarget}; PID-based methods remain as compatibility overloads for local callers.
 */
@ApplicationScoped
public class RuntimeService {

    private static final long ACTION_TIMEOUT_MILLIS = 10_000L;

    @Inject
    RuntimeAccess runtimeAccess;

    /** Creates a tool context backed by the same local/remote runtime access as this MCP service. */
    public ToolContext newToolContext() {
        return new ToolContext(runtimeAccess());
    }

    private RuntimeAccess runtimeAccess() {
        return runtimeAccess != null ? runtimeAccess : LocalRuntimeAccess.INSTANCE;
    }

    /** Process information exposed by MCP, including a stable ID for runtimes that have no local PID. */
    public record ProcessInfo(String id, Long pid, String name, String contextName, String transport) {

        /** Compatibility constructor for existing local tests and callers. */
        public ProcessInfo(long pid, String name, String contextName) {
            this("file:" + pid, pid, name, contextName, "file");
        }

        public RuntimeTarget target() {
            return new RuntimeTarget(
                    id != null ? id : "file:" + pid, pid, name, contextName,
                    transport != null ? transport : "file");
        }

        static ProcessInfo from(RuntimeTarget target) {
            return new ProcessInfo(target.id(), target.pid(), target.name(), target.contextName(), target.transport());
        }
    }

    public List<ProcessInfo> discoverProcesses() {
        return runtimeAccess().discoverProcesses().stream().map(ProcessInfo::from).toList();
    }

    /** Finds the one runtime selected by name, local PID, or stable runtime ID. */
    public ProcessInfo findSingleProcess(String nameOrPid) {
        if (RuntimeHelper.isThisProcess(nameOrPid)) {
            throw new ToolCallException(
                    "pid " + nameOrPid + " is this MCP server (the process that runs the tools), not a Camel "
                                        + "integration; camel_runtime_processes shows the integrations",
                    null);
        }
        List<RuntimeTarget> processes = runtimeAccess().discoverProcesses();
        if (processes.isEmpty()) {
            throw new ToolCallException("No running Camel processes found", null);
        }
        if (nameOrPid == null || nameOrPid.isBlank()) {
            if (processes.size() == 1) {
                return ProcessInfo.from(processes.get(0));
            }
            throw new ToolCallException(
                    "Multiple Camel processes running: " + processes.stream().map(RuntimeService::describe).toList()
                                        + ". Specify nameOrPid to select one.",
                    null);
        }
        RuntimeTarget found = runtimeAccess().findProcess(nameOrPid);
        if (found != null) {
            return ProcessInfo.from(found);
        }
        throw new ToolCallException(
                "No unique Camel process found matching '" + nameOrPid + "': "
                                    + processes.stream().map(RuntimeService::describe).toList()
                                    + ". Specify a more specific name, PID, or runtime ID.",
                null);
    }

    private static String describe(RuntimeTarget target) {
        return target.displayName() + " (" + target.identityLabel() + ")";
    }

    /**
     * The process a chaos tool should use: explicit selector, sole runtime, or the unique runtime with chaos enabled.
     */
    public ProcessInfo findChaosProcess(String nameOrPid) {
        if (nameOrPid != null && !nameOrPid.isBlank()) {
            return findSingleProcess(nameOrPid);
        }
        try {
            RuntimeTarget target = runtimeAccess().findChaosProcess();
            if (target != null) {
                return ProcessInfo.from(target);
            }
        } catch (CliException e) {
            throw new ToolCallException(e.getMessage(), null);
        }
        List<RuntimeTarget> processes = runtimeAccess().discoverProcesses();
        if (processes.isEmpty()) {
            throw new ToolCallException("No running Camel processes found", null);
        }
        throw new ToolCallException(
                "Cannot select one Camel process for chaos tools: "
                                    + processes.stream().map(RuntimeService::describe).toList()
                                    + ". Specify nameOrPid.",
                null);
    }

    public JsonObject readStatus(long pid) {
        return runtimeAccess().readStatus(runtimeAccess().targetForPid(pid));
    }

    public JsonObject readStatus(RuntimeTarget target) {
        return runtimeAccess().readStatus(target);
    }

    public JsonObject readStatusSection(long pid, String section) {
        JsonObject root = readStatus(pid);
        return statusSection(root, "PID " + pid, section);
    }

    public JsonObject readStatusSection(RuntimeTarget target, String section) {
        if (isLocal(target)) {
            return readStatusSection(target.pid(), section);
        }
        JsonObject root = readStatus(target);
        return statusSection(root, target.identityLabel(), section);
    }

    private static JsonObject statusSection(JsonObject root, String runtime, String section) {
        if (root == null) {
            throw new ToolCallException("No status available for " + runtime, null);
        }
        Object value = root.get(section);
        if (value instanceof JsonObject jo) {
            return jo;
        }
        if (value != null) {
            JsonObject wrapper = new JsonObject();
            wrapper.put(section, value);
            return wrapper;
        }
        return new JsonObject();
    }

    public JsonObject readErrorFile(long pid) {
        return runtimeAccess().readErrorFile(runtimeAccess().targetForPid(pid));
    }

    public JsonObject readErrorFile(RuntimeTarget target) {
        return runtimeAccess().readErrorFile(target);
    }

    public JsonObject readHistoryFile(long pid) {
        return runtimeAccess().readHistoryFile(runtimeAccess().targetForPid(pid));
    }

    public JsonObject readHistoryFile(RuntimeTarget target) {
        return runtimeAccess().readHistoryFile(target);
    }

    public String stopApplication(long pid) {
        return runtimeAccess().stopApplication(runtimeAccess().targetForPid(pid));
    }

    public String stopApplication(RuntimeTarget target) {
        return runtimeAccess().stopApplication(target);
    }

    /** Legacy byte-offset view retained for local callers. */
    public long receiveFileSize(long pid) {
        try {
            return Long.parseLong(runtimeAccess().receiveCursor(runtimeAccess().targetForPid(pid)));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Legacy local receive API retained for callers that hold a PID and byte offset. */
    public List<JsonObject> readReceivedMessages(long pid, long offset) {
        return runtimeAccess().readReceivedMessages(runtimeAccess().targetForPid(pid), Long.toString(offset));
    }

    public String receiveCursor(RuntimeTarget target) {
        if (isLocal(target)) {
            return Long.toString(receiveFileSize(target.pid()));
        }
        return runtimeAccess().receiveCursor(target);
    }

    public List<JsonObject> readReceivedMessages(RuntimeTarget target, String cursor) {
        if (isLocal(target)) {
            try {
                return readReceivedMessages(target.pid(), Long.parseLong(cursor));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid local receive cursor: " + cursor, e);
            }
        }
        return runtimeAccess().readReceivedMessages(target, cursor);
    }

    public JsonObject executeAction(long pid, String action, Consumer<JsonObject> configure) {
        JsonObject request = actionRequest(action, configure);
        String result;
        try {
            result = runtimeAccess().executeActionText(
                    runtimeAccess().targetForPid(pid), request, ACTION_TIMEOUT_MILLIS);
        } catch (IOException e) {
            throw new ToolCallException(e.getMessage(), e);
        }
        return parseActionResult(result, action, "PID " + pid);
    }

    public JsonObject executeAction(RuntimeTarget target, String action, Consumer<JsonObject> configure) {
        if (isLocal(target)) {
            return executeAction(target.pid(), action, configure);
        }
        JsonObject request = actionRequest(action, configure);
        String result;
        try {
            result = runtimeAccess().executeActionText(target, request, ACTION_TIMEOUT_MILLIS);
        } catch (IOException e) {
            throw new ToolCallException(
                    "Cannot send the action " + action + " to " + target.identityLabel()
                                        + ": " + e.getMessage(),
                    e);
        }
        return parseActionResult(result, action, target.identityLabel());
    }

    private static JsonObject actionRequest(String action, Consumer<JsonObject> configure) {
        JsonObject request = new JsonObject();
        request.put("action", action);
        if (configure != null) {
            configure.accept(request);
        }
        return request;
    }

    private static JsonObject parseActionResult(String result, String action, String runtime) {
        if (result == null) {
            throw new ToolCallException(
                    "No answer from the integration " + runtime + " to the action " + action
                                        + " within " + ACTION_TIMEOUT_MILLIS / 1000 + " seconds",
                    null);
        }
        if (result.startsWith("Timeout")) {
            throw new ToolCallException(result, null);
        }
        try {
            Object parsed = Jsoner.deserialize(result);
            if (parsed instanceof JsonObject json) {
                return json;
            }
        } catch (Exception e) {
            // An action may return plain text; preserve it for the caller.
        }
        JsonObject wrapper = new JsonObject();
        wrapper.put("result", result);
        return wrapper;
    }

    public JsonObject sendMessage(
            RuntimeTarget target, String endpoint, String body, String headers, int repeat,
            String interval) {
        return runtimeAccess().sendMessage(target, endpoint, body, headers, repeat, interval);
    }

    public JsonObject executeSqlQuery(
            RuntimeTarget target, String sql, String datasource, int maxRows, int queryTimeout) {
        return runtimeAccess().executeSqlQuery(target, sql, datasource, maxRows, queryTimeout);
    }

    public String runtimeVersion(RuntimeTarget target) {
        return runtimeAccess().runtimeVersion(target);
    }

    private static boolean isLocal(RuntimeTarget target) {
        return target != null && target.pid() != null
                && (target.transport() == null || "file".equals(target.transport()));
    }
}
