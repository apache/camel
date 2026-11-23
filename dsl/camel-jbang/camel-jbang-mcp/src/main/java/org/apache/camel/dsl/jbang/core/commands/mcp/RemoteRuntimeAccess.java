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
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.apache.camel.cli.connector.RepeatedSend;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.LocalRuntimeAccess;
import org.apache.camel.dsl.jbang.core.common.RuntimeAccess;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeTarget;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Runtime access used by the MCP server. It combines the existing local file-IPC runtimes with remote WebSocket
 * runtimes authenticated for this server's configured owner. Remote identity is always the connector executionId; a
 * remote operating-system PID is deliberately not used as an identity or exposed as a local PID.
 */
final class RemoteRuntimeAccess implements RuntimeAccess {

    private static final String REMOTE_PREFIX = "websocket:";

    private final RuntimeAccess local;
    private final RemoteRuntimeRegistry registry;
    private final RemoteRuntimeConfig config;

    RemoteRuntimeAccess(RemoteRuntimeRegistry registry, RemoteRuntimeConfig config) {
        this(LocalRuntimeAccess.INSTANCE, registry, config);
    }

    RemoteRuntimeAccess(RuntimeAccess local, RemoteRuntimeRegistry registry, RemoteRuntimeConfig config) {
        this.local = local;
        this.registry = registry;
        this.config = config;
    }

    @Override
    public List<RuntimeTarget> discoverProcesses() {
        List<RuntimeTarget> targets = new ArrayList<>(local.discoverProcesses());
        if (remoteEnabled()) {
            for (RemoteRuntimeEntry entry : registry.list(config.ownerId())) {
                // Do not offer an integration until its hello has completed, or after its socket has gone away.
                if (entry.isReady()) {
                    targets.add(toTarget(entry));
                }
            }
        }
        targets.sort(Comparator.comparing(RuntimeTarget::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RuntimeTarget::id));
        return List.copyOf(targets);
    }

    @Override
    public RuntimeTarget findProcess(String selector) {
        List<RuntimeTarget> targets = discoverProcesses();
        if (selector == null || selector.isBlank()) {
            return targets.size() == 1 ? targets.get(0) : null;
        }

        String value = selector.trim();
        List<RuntimeTarget> exact = targets.stream().filter(target -> matchesExact(target, value)).toList();
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (!exact.isEmpty()) {
            return null;
        }
        // Numeric selectors are PIDs; a remote executionId is selected through its explicit runtime ID.
        if (value.matches("\\d+")) {
            return null;
        }

        List<RuntimeTarget> partial = targets.stream().filter(target -> matchesName(target.name(), value)
                || !"CamelJBang".equals(target.contextName()) && matchesName(target.contextName(), value)
                || matchesName(target.id(), value)).toList();
        return partial.size() == 1 ? partial.get(0) : null;
    }

    private static boolean matchesExact(RuntimeTarget target, String value) {
        if (target.id().equals(value) || !value.matches("\\d+") && target.id().equals(REMOTE_PREFIX + value)) {
            return true;
        }
        if (target.pid() != null && Long.toString(target.pid()).equals(value)) {
            return true;
        }
        String exactName = value.endsWith("!") ? value.substring(0, value.length() - 1) : value;
        return exactName(target.name(), exactName)
                || !"CamelJBang".equals(target.contextName()) && exactName(target.contextName(), exactName);
    }

    private static boolean exactName(String candidate, String selector) {
        if (candidate == null) {
            return false;
        }
        String fileName = FileUtil.onlyName(candidate);
        return candidate.equalsIgnoreCase(selector) || fileName != null && fileName.equalsIgnoreCase(selector);
    }

    private static boolean matchesName(String candidate, String selector) {
        if (candidate == null || selector.endsWith("!")) {
            return false;
        }
        // Match ProcessFinder's name semantics: match the file name too; a plain name is a prefix.
        String fileName = FileUtil.onlyName(candidate);
        return matchesPattern(candidate, selector) || fileName != null && matchesPattern(fileName, selector);
    }

    private static boolean matchesPattern(String candidate, String selector) {
        String pattern = selector.endsWith("*") ? selector : selector + "*";
        StringBuilder regex = new StringBuilder("(?i)^");
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*') {
                regex.append(".*");
            } else if (ch == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        regex.append('$');
        return candidate.matches(regex.toString());
    }

    @Override
    public RuntimeTarget findChaosProcess() {
        List<RuntimeTarget> targets = discoverProcesses();
        if (targets.size() == 1) {
            return targets.get(0);
        }
        if (targets.isEmpty()) {
            throw CliException.usage(
                    "No running Camel integrations: start one with chaos switched on (camel run --chaos)");
        }
        List<RuntimeTarget> chaosTargets = new ArrayList<>();
        for (RuntimeTarget target : targets) {
            if (hasChaos(readStatus(target))) {
                chaosTargets.add(target);
            }
        }
        if (chaosTargets.size() == 1) {
            return chaosTargets.get(0);
        }
        StringJoiner available = new StringJoiner(", ");
        for (RuntimeTarget target : targets) {
            available.add(target.displayName() + " (" + target.identityLabel() + ", "
                          + (chaosTargets.contains(target) ? "chaos on" : "chaos off") + ")");
        }
        throw CliException.usage(targets.size() + " Camel integrations are running and "
                                 + (chaosTargets.isEmpty() ? "none has" : chaosTargets.size() + " have")
                                 + " chaos switched on: " + available + ". Give the name, PID, or runtime ID of one.");
    }

    private static boolean hasChaos(JsonObject status) {
        return status != null && status.get("chaos") instanceof JsonObject chaos
                && chaos.getBooleanOrDefault("enabled", false);
    }

    @Override
    public RuntimeTarget targetForPid(long pid) {
        return local.targetForPid(pid);
    }

    @Override
    public JsonObject readStatus(RuntimeTarget target) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        return entry != null ? copySnapshot(entry, "status") : local.readStatus(target);
    }

    @Override
    public ActionResult sendAction(RuntimeTarget target, JsonObject request, long timeoutMs) throws IOException {
        RemoteRuntimeEntry entry = remoteEntry(target);
        if (entry == null) {
            return local.sendAction(target, request, timeoutMs);
        }
        try {
            JsonObject frame = entry.executeAction(request, timeoutMs);
            if (!Boolean.TRUE.equals(frame.get("ok"))) {
                return new ActionResult(ActionStatus.ERROR, frame.getString("error"), frame.getString("reason"));
            }
            return new ActionResult(ActionStatus.OK, null, frame.getString("reason"));
        } catch (SocketTimeoutException e) {
            return new ActionResult(ActionStatus.TIMEOUT, e.getMessage(), null);
        }
    }

    @Override
    public String executeActionText(RuntimeTarget target, JsonObject request, long timeoutMs) throws IOException {
        RemoteRuntimeEntry entry = remoteEntry(target);
        if (entry == null) {
            return local.executeActionText(target, request, timeoutMs);
        }
        final JsonObject frame;
        try {
            frame = entry.executeAction(request, timeoutMs);
        } catch (SocketTimeoutException e) {
            return null;
        }
        if (!Boolean.TRUE.equals(frame.get("ok"))) {
            JsonObject failed = new JsonObject();
            failed.put("status", "failed");
            failed.put("error", frame.getString("error") != null ? frame.getString("error") : "Remote action failed");
            return failed.toJson();
        }
        Object result = frame.get("result");
        if (result instanceof JsonObject json) {
            return json.toJson();
        }
        return result != null ? result.toString() : "{}";
    }

    @Override
    public JsonObject readErrorFile(RuntimeTarget target) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        return entry != null ? copySnapshot(entry, "error") : local.readErrorFile(target);
    }

    @Override
    public JsonObject readHistoryFile(RuntimeTarget target) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        return entry != null ? copySnapshot(entry, "history") : local.readHistoryFile(target);
    }

    @Override
    public String receiveCursor(RuntimeTarget target) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        if (entry == null) {
            return local.receiveCursor(target);
        }
        JsonObject receive = copySnapshot(entry, "receive");
        long highest = 0;
        if (receive != null) {
            List<JsonObject> messages = receive.getCollection("messages");
            if (messages != null) {
                for (JsonObject message : messages) {
                    Object uid = message.get("uid");
                    if (uid instanceof Number number) {
                        highest = Math.max(highest, number.longValue());
                    }
                }
            }
        }
        return Long.toString(highest);
    }

    @Override
    public List<JsonObject> readReceivedMessages(RuntimeTarget target, String cursor) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        if (entry == null) {
            return local.readReceivedMessages(target, cursor);
        }
        final long after;
        try {
            after = cursor == null || cursor.isBlank() ? 0 : Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid remote receive cursor: " + cursor, e);
        }
        JsonObject receive = copySnapshot(entry, "receive");
        if (receive == null) {
            return List.of();
        }
        List<JsonObject> messages = receive.getCollection("messages");
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject message : messages) {
            Object uid = message.get("uid");
            if (uid instanceof Number number && number.longValue() > after) {
                result.add(new JsonObject(message));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public String stopApplication(RuntimeTarget target) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        if (entry == null) {
            return local.stopApplication(target);
        }
        JsonObject request = new JsonObject();
        request.put("action", "stop");
        try {
            JsonObject frame = entry.executeAction(request, 10_000L);
            if (!Boolean.TRUE.equals(frame.get("ok"))) {
                return frame.getString("error");
            }
            Object result = frame.get("result");
            return result != null ? result.toString() : "Application stopping";
        } catch (IOException e) {
            throw new ToolExecutionException(
                    "Cannot stop remote Camel integration " + target.id() + ": " + e.getMessage());
        }
    }

    @Override
    public JsonObject sendMessage(
            RuntimeTarget target, String endpoint, String body, String headers, int repeat, String interval) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        if (entry == null) {
            return local.sendMessage(target, endpoint, body, headers, repeat, interval);
        }
        JsonObject request = new JsonObject();
        request.put("action", "send");
        request.put("endpoint", endpoint);
        if (body != null) {
            request.put("body", body);
        }
        if (headers != null) {
            request.put("headers", RuntimeHelper.parseKeyValues(headers, "Header"));
        }
        if (repeat > 0) {
            request.put("repeat", repeat);
        }
        long[] pause = RepeatedSend.interval(interval);
        if (pause[1] > 0) {
            request.put("interval", pause[0]);
            if (pause[1] > pause[0]) {
                request.put("intervalMax", pause[1]);
            }
        }
        return executeJsonAction(entry, request, 30_000L);
    }

    @Override
    public JsonObject executeSqlQuery(
            RuntimeTarget target, String sql, String datasource, int maxRows, int queryTimeout) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        if (entry == null) {
            return local.executeSqlQuery(target, sql, datasource, maxRows, queryTimeout);
        }
        JsonObject request = new JsonObject();
        request.put("action", "sql-query");
        request.put("sql", sql);
        if (datasource != null) {
            request.put("datasource", datasource);
        }
        request.put("maxRows", maxRows);
        request.put("queryTimeout", queryTimeout);
        return executeJsonAction(entry, request, (queryTimeout + 10L) * 1000L);
    }

    private JsonObject executeJsonAction(RemoteRuntimeEntry entry, JsonObject request, long timeoutMs) {
        try {
            JsonObject frame = entry.executeAction(request, timeoutMs);
            if (!Boolean.TRUE.equals(frame.get("ok"))) {
                JsonObject failed = new JsonObject();
                failed.put("status", "failed");
                failed.put("error", frame.getString("error") != null
                        ? frame.getString("error") : "Remote action failed");
                return failed;
            }
            Object result = frame.get("result");
            if (result instanceof JsonObject json) {
                return json;
            }
            if (result != null) {
                try {
                    Object parsed = Jsoner.deserialize(result.toString());
                    if (parsed instanceof JsonObject json) {
                        return json;
                    }
                } catch (Exception e) {
                    // Preserve plain-text action results in the same shape as local file IPC.
                }
                JsonObject wrapper = new JsonObject();
                wrapper.put("result", result);
                return wrapper;
            }
            return new JsonObject();
        } catch (IOException e) {
            throw new ToolExecutionException("Remote Camel integration is unavailable: " + e.getMessage());
        }
    }

    @Override
    public String runtimeVersion(RuntimeTarget target) {
        RemoteRuntimeEntry entry = remoteEntry(target);
        if (entry == null) {
            return local.runtimeVersion(target);
        }
        JsonObject hello = entry.hello();
        return hello != null ? hello.getString("camelVersion") : null;
    }

    private static JsonObject copySnapshot(RemoteRuntimeEntry entry, String kind) {
        JsonObject snapshot = entry.snapshot(kind);
        if (snapshot == null) {
            return null;
        }
        return Jsoner.deserialize(snapshot.toJson(), (JsonObject) null);
    }

    private boolean remoteEnabled() {
        return config != null && config.isEnabled() && config.isTokenConfigured();
    }

    private RemoteRuntimeEntry remoteEntry(RuntimeTarget target) {
        if (target == null || !"websocket".equals(target.transport()) || !target.id().startsWith(REMOTE_PREFIX)) {
            return null;
        }
        if (!remoteEnabled()) {
            throw new ToolExecutionException("Remote Camel runtime access is disabled or not authenticated");
        }
        RemoteRuntimeEntry entry = registry.find(config.ownerId(), target.id().substring(REMOTE_PREFIX.length()));
        if (entry == null) {
            throw new ToolExecutionException("Remote Camel integration is no longer available: " + target.id());
        }
        return entry;
    }

    /** Package-visible for focused identity tests; it never turns the remote PID into the target identity. */
    static RuntimeTarget toTarget(RemoteRuntimeEntry entry) {
        JsonObject hello = entry.hello();
        String name = hello != null ? hello.getString("name") : null;
        JsonObject status = entry.snapshot("status");
        String contextName = null;
        if (status != null && status.get("context") instanceof JsonObject context) {
            contextName = context.getString("name");
        }
        return new RuntimeTarget(
                REMOTE_PREFIX + entry.executionId(), null,
                name != null && !name.isBlank() ? name : entry.executionId(), contextName, "websocket");
    }
}
