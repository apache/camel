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
 * <p>
 * Camel JBang applications write status snapshots to {@code ~/.camel/{pid}-status.json}. Actions are requested by
 * writing to {@code {pid}-action-{requestId}.json} and reading the response from {@code {pid}-output-{requestId}.json}.
 * Each request gets a unique ID so concurrent callers (CLI, MCP server, etc.) don't interfere with each other.
 * <p>
 * This class is used by both the {@code camel ask} CLI command and the MCP server's {@code RuntimeService}.
 *
 * @since 4.21
 */
public final class RuntimeHelper {

    private static final long ACTION_TIMEOUT_MS = 10_000;
    private static final long POLL_INTERVAL_MS = 100;

    /**
     * Information about a discovered running Camel process.
     *
     * @param pid         the OS process ID
     * @param name        the application name (extracted from status or process info)
     * @param contextName the Camel context name, or {@code null} if not available
     *
     * @since             4.21
     */
    public record ProcessInfo(long pid, String name, String contextName) {
    }

    private RuntimeHelper() {
    }

    /**
     * Discovers all running Camel processes by scanning {@code ~/.camel/} for {@code {pid}-status.json} files and
     * verifying each PID is still alive.
     */
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

    /**
     * Finds a single Camel process matching the given name or PID. Returns {@code null} if no match is found or if
     * multiple processes match. When {@code nameOrPid} is {@code null} and exactly one process is running, returns that
     * process.
     *
     * @param nameOrPid a process name (with optional wildcard), a numeric PID string, or {@code null} for auto-detect
     */
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

    /**
     * Reads the full status JSON for the given process.
     *
     * @return the parsed status object, or {@code null} if the status file does not exist or is unreadable
     */
    public static JsonObject readStatus(long pid) {
        Path statusFile = CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
        return readStatusFromFile(statusFile);
    }

    /**
     * Reads a specific section from the status JSON.
     *
     * @param  section the top-level key to extract (e.g., "context", "routes", "health")
     * @return         the section value as a JSON string, or {@code "{}"} if absent
     */
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

    /**
     * Executes an action against a running Camel process using the file-based IPC protocol. Writes an action request
     * file and polls for the output file within a timeout.
     *
     * @param  pid       the target process ID
     * @param  action    the action name (e.g., "route", "top", "source")
     * @param  configure optional callback to add extra fields to the request JSON
     * @return           the raw response string, or a timeout message if no response was received
     */
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

    /**
     * Initiates a graceful shutdown of a running Camel application by deleting its PID file.
     */
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

    /**
     * Reads the error file for the given process.
     *
     * @return the parsed error data, or {@code null} if the file does not exist or is unreadable
     */
    public static JsonObject readErrorFile(long pid) {
        Path errorFile = CommandLineHelper.getCamelDir().resolve(pid + "-error.json");
        return readStatusFromFile(errorFile);
    }

    /**
     * Reads the message history file for the given process (trace of the last completed exchange).
     *
     * @return the parsed history data, or {@code null} if the file does not exist or is unreadable
     */
    public static JsonObject readHistoryFile(long pid) {
        Path historyFile = CommandLineHelper.getCamelDir().resolve(pid + "-history.json");
        return readStatusFromFile(historyFile);
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
