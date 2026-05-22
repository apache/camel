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
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared service for discovering running Camel processes and communicating with them via the file-based IPC protocol.
 * Delegates to {@link RuntimeHelper} and translates errors to {@link ToolCallException}.
 */
@ApplicationScoped
public class RuntimeService {

    public record ProcessInfo(long pid, String name, String contextName) {
    }

    public List<ProcessInfo> discoverProcesses() {
        return RuntimeHelper.discoverProcesses().stream()
                .map(p -> new ProcessInfo(p.pid(), p.name(), p.contextName()))
                .toList();
    }

    public ProcessInfo findSingleProcess(String nameOrPid) {
        List<RuntimeHelper.ProcessInfo> processes = RuntimeHelper.discoverProcesses();

        if (processes.isEmpty()) {
            throw new ToolCallException("No running Camel processes found", null);
        }

        RuntimeHelper.ProcessInfo found = RuntimeHelper.findProcess(nameOrPid);
        if (found != null) {
            return new ProcessInfo(found.pid(), found.name(), found.contextName());
        }

        if (nameOrPid != null && !nameOrPid.isBlank()) {
            throw new ToolCallException(
                    "No unique Camel process found matching '" + nameOrPid + "': "
                                        + processes.stream().map(p -> p.name() + " (PID " + p.pid() + ")").toList()
                                        + ". Specify a more specific name or PID.",
                    null);
        }

        throw new ToolCallException(
                "Multiple Camel processes running: "
                                    + processes.stream().map(p -> p.name() + " (PID " + p.pid() + ")").toList()
                                    + ". Specify nameOrPid to select one.",
                null);
    }

    public JsonObject readStatus(long pid) {
        return RuntimeHelper.readStatus(pid);
    }

    public JsonObject readStatusSection(long pid, String section) {
        JsonObject root = RuntimeHelper.readStatus(pid);
        if (root == null) {
            throw new ToolCallException("No status available for PID " + pid, null);
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

    public JsonObject executeAction(long pid, String action, Consumer<JsonObject> configure) {
        String result = RuntimeHelper.executeAction(pid, action, configure);
        if (result != null && result.startsWith("Timeout")) {
            throw new ToolCallException(result, null);
        }
        try {
            return (JsonObject) Jsoner.deserialize(result);
        } catch (Exception e) {
            JsonObject wrapper = new JsonObject();
            wrapper.put("result", result);
            return wrapper;
        }
    }
}
