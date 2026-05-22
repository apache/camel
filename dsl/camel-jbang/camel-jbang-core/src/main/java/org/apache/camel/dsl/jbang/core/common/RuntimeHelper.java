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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared helper for discovering running Camel processes and communicating with them via the file-based IPC protocol.
 * Uses the multi-client action file protocol: each request gets a unique ID, so concurrent callers don't interfere.
 */
public final class RuntimeHelper {

    private static final long ACTION_TIMEOUT_MS = 10_000;
    private static final long POLL_INTERVAL_MS = 100;

    public record ProcessInfo(long pid, String name, String contextName) {
    }

    private RuntimeHelper() {
    }

    public static List<ProcessInfo> discoverProcesses() {
        List<ProcessInfo> result = new ArrayList<>();
        Path camelDir = CommandLineHelper.getCamelDir();
        File dir = camelDir.toFile();
        if (!dir.isDirectory()) {
            return result;
        }

        File[] statusFiles = dir.listFiles((d, name) -> name.matches("\\d+-status\\.json"));
        if (statusFiles == null) {
            return result;
        }

        for (File sf : statusFiles) {
            String fileName = sf.getName();
            long pid = Long.parseLong(fileName.substring(0, fileName.indexOf('-')));
            if (!ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                continue;
            }
            try {
                JsonObject root = readStatusFromFile(sf.toPath());
                if (root != null) {
                    String name = ProcessHelper.extractName(root, ProcessHandle.of(pid).orElse(null));
                    String contextName = null;
                    JsonObject context = (JsonObject) root.get("context");
                    if (context != null) {
                        contextName = context.getString("name");
                    }
                    result.add(new ProcessInfo(pid, name, contextName));
                }
            } catch (Exception e) {
                // skip
            }
        }
        return result;
    }

    public static ProcessInfo findProcess(String nameOrPid) {
        List<ProcessInfo> processes = discoverProcesses();
        if (processes.isEmpty()) {
            return null;
        }

        if (nameOrPid != null && !nameOrPid.isBlank()) {
            if (nameOrPid.matches("\\d+")) {
                long pid = Long.parseLong(nameOrPid);
                return processes.stream()
                        .filter(p -> p.pid == pid)
                        .findFirst()
                        .orElse(null);
            }
            String pattern = nameOrPid.endsWith("*") ? nameOrPid : nameOrPid + "*";
            List<ProcessInfo> matched = processes.stream()
                    .filter(p -> (p.name != null && PatternHelper.matchPattern(FileUtil.onlyName(p.name), pattern))
                            || (p.contextName != null && PatternHelper.matchPattern(p.contextName, pattern)))
                    .toList();
            if (matched.size() == 1) {
                return matched.get(0);
            }
            return null;
        }

        if (processes.size() == 1) {
            return processes.get(0);
        }
        return null;
    }

    public static JsonObject readStatus(long pid) {
        Path statusFile = CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
        return readStatusFromFile(statusFile);
    }

    public static String readStatusSection(long pid, String section) {
        JsonObject root = readStatus(pid);
        if (root == null) {
            return "No status available for PID " + pid;
        }
        Object value = root.get(section);
        if (value instanceof JsonObject jo) {
            return jo.toJson();
        }
        if (value != null) {
            JsonObject wrapper = new JsonObject();
            wrapper.put(section, value);
            return wrapper.toJson();
        }
        return "{}";
    }

    public static String executeAction(long pid, String action, Consumer<JsonObject> configure) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        Path camelDir = CommandLineHelper.getCamelDir();
        Path outputFile = camelDir.resolve(pid + "-output-" + requestId + ".json");
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", action);
        if (configure != null) {
            configure.accept(root);
        }

        Path actionFile = camelDir.resolve(pid + "-action-" + requestId + ".json");
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        try {
            StopWatch watch = new StopWatch();
            while (watch.taken() < ACTION_TIMEOUT_MS) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                    if (Files.exists(outputFile) && outputFile.toFile().length() > 0) {
                        return Files.readString(outputFile);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // retry
                }
            }
            return "Timeout waiting for response from PID " + pid + " for action: " + action;
        } finally {
            PathUtils.deleteFile(outputFile);
            PathUtils.deleteFile(actionFile);
        }
    }

    public static String stopApplication(long pid) {
        Path pidFile = CommandLineHelper.getCamelDir().resolve(Long.toString(pid));
        if (Files.exists(pidFile)) {
            PathUtils.deleteFile(pidFile);
            return "Graceful shutdown initiated for PID " + pid
                   + ". The application will finish processing in-flight exchanges and shut down.";
        } else {
            return "PID file not found for " + pid + ". The process may already be stopping.";
        }
    }

    public static JsonObject readStatusFromFile(Path path) {
        try {
            if (Files.exists(path) && path.toFile().length() > 0) {
                String text = Files.readString(path);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
