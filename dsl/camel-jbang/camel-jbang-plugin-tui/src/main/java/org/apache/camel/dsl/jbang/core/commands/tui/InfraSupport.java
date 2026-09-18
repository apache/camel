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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonObject;

/**
 * Infra service (broker, database, ...) operations shared by the MCP tools and the AI panel. The TUI discovers infra
 * services from the {@code infra-{alias}-{pid}.json} files that {@code camel infra run} writes in the Camel home
 * directory; the matching {@code .log} file next to it holds the service output.
 */
final class InfraSupport {

    private InfraSupport() {
    }

    /**
     * Finds a live infra service by alias (case-insensitive) or pid.
     */
    static InfraInfo find(List<InfraInfo> services, String aliasOrPid) {
        if (services == null || aliasOrPid == null || aliasOrPid.isBlank()) {
            return null;
        }
        String key = aliasOrPid.strip();
        for (InfraInfo info : services) {
            if (info.vanishing) {
                continue;
            }
            if (key.equalsIgnoreCase(info.alias) || key.equals(info.pid)) {
                return info;
            }
        }
        return null;
    }

    static JsonObject toJson(InfraInfo info) {
        JsonObject json = new JsonObject();
        json.put("alias", info.alias);
        json.put("pid", info.pid);
        json.put("alive", info.alive);
        if (info.description != null) {
            json.put("description", info.description);
        }
        if (info.serviceVersion != null) {
            json.put("version", info.serviceVersion);
        }
        // connection details (brokerUrl, host, port, ...) are what a caller needs to reach the service
        JsonObject props = new JsonObject();
        for (Map.Entry<String, Object> e : info.properties.entrySet()) {
            String k = e.getKey();
            if ("description".equals(k) || "serviceVersion".equals(k) || k.startsWith("get")) {
                continue;
            }
            props.put(k, e.getValue());
        }
        if (!props.isEmpty()) {
            json.put("properties", props);
        }
        return json;
    }

    static Path logFile(Path camelDir, InfraInfo info) {
        return camelDir.resolve("infra-" + info.alias + "-" + info.pid + ".log");
    }

    /**
     * Returns the newest {@code limit} lines of the service log, newest first, optionally filtered by a
     * case-insensitive substring. Returns an empty list when the service has written no log yet.
     */
    static List<String> readLogTail(Path camelDir, InfraInfo info, int limit, String filter) throws IOException {
        Path logFile = logFile(camelDir, info);
        if (!Files.isRegularFile(logFile)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // service output is not guaranteed to be UTF-8
            lines = Files.readAllLines(logFile, StandardCharsets.ISO_8859_1);
        }
        String needle = filter == null || filter.isBlank() ? null : filter.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            if (needle == null || line.toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(line);
            }
        }
        return result;
    }

    /**
     * Stops a service the same way the Overview stop-all does: remove its pid file so the TUI stops listing it, then
     * ask the process to terminate. Returns false when no live process was found for it.
     */
    static boolean stop(Path camelDir, InfraInfo info) {
        PathUtils.deleteFile(camelDir.resolve("infra-" + info.alias + "-" + info.pid + ".json"));
        try {
            long pid = Long.parseLong(info.pid);
            return ProcessHandle.of(pid).map(ProcessHandle::destroy).orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
