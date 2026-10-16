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
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * CDI service for discovering running Camel processes and communicating with them via the file-based IPC protocol.
 * <p>
 * This is a thin wrapper around {@link RuntimeHelper} that translates null returns and error conditions into
 * {@link ToolCallException} instances suitable for MCP tool responses.
 *
 * @since 4.21
 */
@ApplicationScoped
public class RuntimeService {

    /**
     * Process information exposed to MCP tools.
     *
     * @since 4.21
     */
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

    public JsonObject readErrorFile(long pid) {
        JsonObject result = RuntimeHelper.readErrorFile(pid);
        if (result == null) {
            JsonObject empty = new JsonObject();
            empty.put("errors", new org.apache.camel.util.json.JsonArray());
            return empty;
        }
        return result;
    }

    public JsonObject readHistoryFile(long pid) {
        JsonObject result = RuntimeHelper.readHistoryFile(pid);
        if (result == null) {
            JsonObject empty = new JsonObject();
            empty.put("message", "No message history available for PID " + pid);
            return empty;
        }
        return result;
    }

    public String stopApplication(long pid) {
        return RuntimeHelper.stopApplication(pid);
    }

    /**
     * The size of the file the CLI connector appends the messages of the receive console to
     * ({@code {pid}-receive.json}, read by {@code camel cmd receive} too), to read only what arrives after this point.
     */
    public long receiveFileSize(long pid) {
        try {
            Path file = receiveFile(pid);
            return Files.exists(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * The messages the CLI connector appended to the receive file after the given offset: each complete line is a
     * {@code {"messages": [...]}} dump of the receive console. Only what was appended after the offset is read, as the
     * file is append-only and kept across sessions.
     */
    public List<JsonObject> readReceivedMessages(long pid, long offset) {
        List<JsonObject> messages = new ArrayList<>();
        Path file = receiveFile(pid);
        try {
            if (!Files.exists(file)) {
                return messages;
            }
            String text;
            try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
                long size = channel.size();
                // camel cmd receive --action=clear rewrites the file: read it from the start then
                long from = offset <= size ? offset : 0;
                ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(size - from));
                channel.position(from);
                int read = 0;
                while (buffer.hasRemaining() && read >= 0) {
                    read = channel.read(buffer);
                }
                text = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
            }
            int end = text.lastIndexOf('\n');
            if (end < 0) {
                return messages;
            }
            for (String line : text.substring(0, end).split("\n")) {
                Object parsed = Jsoner.deserialize(line.trim(), (JsonObject) null);
                if (parsed instanceof JsonObject dump && dump.getCollection("messages") != null) {
                    for (Object m : dump.getCollection("messages")) {
                        if (m instanceof JsonObject jo) {
                            messages.add(jo);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // not readable yet: the caller polls again
        }
        return messages;
    }

    private static Path receiveFile(long pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-receive.json");
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
