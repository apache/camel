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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The existing local runtime implementation, using the Camel JBang file-based IPC protocol.
 * <p>
 * This adapter deliberately preserves the existing {@link RuntimeHelper} and {@link ActionClient} behaviour. It is
 * stateless and safe to share between tool contexts.
 *
 */
public final class LocalRuntimeAccess implements RuntimeAccess {

    public static final LocalRuntimeAccess INSTANCE = new LocalRuntimeAccess();

    private LocalRuntimeAccess() {
    }

    @Override
    public List<RuntimeTarget> discoverProcesses() {
        return RuntimeHelper.discoverProcesses().stream().map(RuntimeTarget::local).toList();
    }

    @Override
    public RuntimeTarget findProcess(String selector) {
        String localSelector = selector;
        if (localSelector != null && localSelector.startsWith("file:")) {
            localSelector = localSelector.substring("file:".length());
        }
        RuntimeHelper.ProcessInfo process = RuntimeHelper.findProcess(localSelector);
        return process != null ? RuntimeTarget.local(process) : null;
    }

    @Override
    public RuntimeTarget findChaosProcess() {
        ProcessFinder.CamelProcess process = ProcessFinder.requireOneForChaos(null);
        return RuntimeTarget.local(new RuntimeHelper.ProcessInfo(
                process.pid(), process.name(), process.contextName()));
    }

    @Override
    public RuntimeTarget targetForPid(long pid) {
        return RuntimeTarget.local(pid);
    }

    @Override
    public JsonObject readStatus(RuntimeTarget target) {
        return RuntimeHelper.readStatus(pid(target));
    }

    @Override
    public ActionResult sendAction(RuntimeTarget target, JsonObject request, long timeoutMs) throws IOException {
        ActionClient.ActionResult result = ActionClient.send(pid(target), request, timeoutMs);
        return new ActionResult(ActionStatus.valueOf(result.status().name()), result.error(), result.reason());
    }

    @Override
    public String executeActionText(RuntimeTarget target, JsonObject request, long timeoutMs) throws IOException {
        try {
            return ActionClient.executeText(pid(target), request, timeoutMs);
        } catch (ActionClient.StoppedException e) {
            throw new RuntimeStoppedException(e.getMessage(), e);
        }
    }

    @Override
    public JsonObject readErrorFile(RuntimeTarget target) {
        return RuntimeHelper.readErrorFile(pid(target));
    }

    @Override
    public JsonObject readHistoryFile(RuntimeTarget target) {
        return RuntimeHelper.readHistoryFile(pid(target));
    }

    @Override
    public String receiveCursor(RuntimeTarget target) {
        try {
            Path file = receiveFile(pid(target));
            return Long.toString(Files.exists(file) ? Files.size(file) : 0);
        } catch (IOException e) {
            return "0";
        }
    }

    @Override
    public List<JsonObject> readReceivedMessages(RuntimeTarget target, String cursor) {
        List<JsonObject> messages = new ArrayList<>();
        Path file = receiveFile(pid(target));
        try {
            long offset = Long.parseLong(cursor);
            if (!Files.exists(file)) {
                return messages;
            }
            String text;
            try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
                long size = channel.size();
                // The CLI can clear the receive console by rewriting its file: start over when it shrank.
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
                    for (Object message : dump.getCollection("messages")) {
                        if (message instanceof JsonObject json) {
                            messages.add(json);
                        }
                    }
                }
            }
        } catch (

        IOException e) {
            // A partially published file is read on the next poll.
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid local receive cursor: " + cursor, e);
        }
        return messages;
    }

    private static Path receiveFile(long pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-receive.json");
    }

    @Override
    public String stopApplication(RuntimeTarget target) {
        return RuntimeHelper.stopApplication(pid(target));
    }

    @Override
    public JsonObject sendMessage(
            RuntimeTarget target, String endpoint, String body, String headers, int repeat, String interval) {
        return RuntimeHelper.sendMessage(pid(target), endpoint, body, headers, repeat, interval);
    }

    @Override
    public JsonObject executeSqlQuery(
            RuntimeTarget target, String sql, String datasource, int maxRows, int queryTimeout) {
        return RuntimeHelper.executeSqlQuery(pid(target), sql, datasource, maxRows, queryTimeout);
    }

    @Override
    public String runtimeVersion(RuntimeTarget target) {
        return ActionClient.versionOf(pid(target));
    }

    private static long pid(RuntimeTarget target) {
        if (target == null || target.pid() == null) {
            throw new IllegalArgumentException("Local file IPC requires a runtime target with a PID");
        }
        return target.pid();
    }
}
