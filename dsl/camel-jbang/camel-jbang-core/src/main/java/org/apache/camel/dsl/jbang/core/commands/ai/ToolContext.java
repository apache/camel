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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.LocalRuntimeAccess;
import org.apache.camel.dsl.jbang.core.common.RuntimeAccess;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeTarget;
import org.apache.camel.dsl.jbang.run.common.CatalogLoader;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared execution context for AI tools. Wraps {@link RuntimeAccess} (local file IPC by default) and
 * {@link CamelCatalog} (component metadata). This class intentionally has no dependency on MCP or Quarkus so it can be
 * used by both the Agent REPL and the MCP server.
 */
public class ToolContext {

    /** How long an action on the integration may take, in millis. */
    private static final long ACTION_TIMEOUT = 10000;

    private final RuntimeAccess runtimeAccess;
    private volatile RuntimeTarget selectedTarget;
    private volatile CamelCatalog catalog;
    private volatile String camelVersion;
    private volatile Path defaultDirectory;
    // guarded by this: the root is asked for once per context
    private Supplier<Path> rootDirectory;
    private Path rootLookedUp;
    private boolean rootKnown;
    private volatile Function<String, String> propertyLineValidator;
    private volatile boolean autoSelectSingleProcess = true;
    private volatile Predicate<ToolConfirms.Call> confirmer;

    public ToolContext() {
        this(LocalRuntimeAccess.INSTANCE);
    }

    /** Creates a context backed by the supplied runtime access implementation. */
    public ToolContext(RuntimeAccess runtimeAccess) {
        this.runtimeAccess = Objects.requireNonNull(runtimeAccess, "runtimeAccess");
    }

    /**
     * The selected PID for legacy local callers, or {@code -1} when no target is selected or the target has no PID. New
     * runtime code should use {@link #selectedTarget()} instead.
     */
    public long pid() {
        RuntimeTarget target = selectedTarget;
        return target != null && target.pid() != null ? target.pid() : -1;
    }

    /** The opaque selected runtime target, or {@code null} when none is selected. */
    public RuntimeTarget selectedTarget() {
        return selectedTarget;
    }

    /** A diagnostic identity that does not assume every runtime has a PID. */
    public String processDescription() {
        RuntimeTarget target = selectedTarget;
        if (target == null) {
            return "no selected runtime";
        }
        return target.pid() != null ? "pid " + target.pid() : "runtime target " + target.id();
    }

    /** Selects a runtime by its opaque reference. */
    public void selectProcess(RuntimeTarget target) {
        this.selectedTarget = Objects.requireNonNull(target, "target");
    }

    /** Legacy convenience for selecting a local runtime by PID. */
    public void selectProcess(long pid) {
        if (pid < 0) {
            clearProcess();
        } else {
            selectProcess(runtimeAccess.targetForPid(pid));
        }
    }

    public boolean hasProcess() {
        return selectedTarget != null;
    }

    /** Forgets the selected process, so tools fall back to what needs no running integration. */
    public void clearProcess() {
        this.selectedTarget = null;
    }

    /**
     * Whether a runtime tool called without a name may pick the only running Camel process when none is selected: the
     * default for a server without a selection of its own; the TUI turns it off, since there what the user selected is
     * the integration.
     */
    public boolean isAutoSelectSingleProcess() {
        return autoSelectSingleProcess;
    }

    public void setAutoSelectSingleProcess(boolean autoSelectSingleProcess) {
        this.autoSelectSingleProcess = autoSelectSingleProcess;
    }

    /**
     * Selects the only running Camel process when nothing is selected yet and auto-selection is on.
     *
     * @return whether a process is selected afterwards
     */
    public boolean selectSingleProcessIfNone() {
        if (hasProcess()) {
            return true;
        }
        if (!autoSelectSingleProcess) {
            return false;
        }
        RuntimeTarget only = runtimeAccess.findProcess(null);
        if (only == null) {
            return false;
        }
        selectProcess(only);
        return true;
    }

    /**
     * The Camel version the catalog tools answer for: null for the version of the running CLI, otherwise the version of
     * the integration being worked on (the TUI passes the selected integration's version), downloaded on demand.
     * <p>
     * Not synchronized with {@link #catalog()}: a context serves one tool call at a time (the MCP servers and the TUI
     * build a fresh one per call, {@code camel ask} runs its calls one after the other), and the version is set before
     * the catalog is first asked for, so a change of version simply drops the cached catalog for the next call.
     */
    public String camelVersion() {
        return camelVersion;
    }

    public void setCamelVersion(String camelVersion) {
        if (!Objects.equals(this.camelVersion, camelVersion)) {
            this.camelVersion = camelVersion;
            this.catalog = null;
        }
    }

    /**
     * The project directory the file tools use when a call names none: the TUI sets it to the source directory of the
     * selected integration, {@code camel ask} to the project directory; an MCP server leaves it null.
     */
    public Path defaultDirectory() {
        return defaultDirectory;
    }

    public void setDefaultDirectory(Path defaultDirectory) {
        this.defaultDirectory = defaultDirectory;
    }

    /**
     * The project directory the client declares, such as the only {@code file:} root of an MCP client: the file tools
     * read and write there when a call names no directory and there is no default directory, and a write creates a
     * directory the call names inside it. Asking the client is a round trip, so the root is looked up when a call first
     * needs it, and once per context; null when there is none.
     */
    public synchronized Path rootDirectory() {
        if (rootDirectory != null && !rootKnown) {
            rootLookedUp = rootDirectory.get();
            rootKnown = true;
        }
        return rootLookedUp;
    }

    public synchronized void setRootDirectory(Supplier<Path> rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.rootLookedUp = null;
        this.rootKnown = false;
    }

    /**
     * Who the shared tools ask before a call that the user confirms first ({@link ToolConfirms}), such as a file write:
     * {@code camel ask} asks on the terminal (CAMEL-25676). Null, the default, asks nobody: an MCP server leaves that
     * to its client, and the TUI confirms in its own tools.
     */
    public Predicate<ToolConfirms.Call> confirmer() {
        return confirmer;
    }

    public void setConfirmer(Predicate<ToolConfirms.Call> confirmer) {
        this.confirmer = confirmer;
    }

    /** Whether a call goes ahead: there is nothing to confirm, nobody to ask, or the user confirmed it. */
    public boolean confirm(ToolConfirms.Call call) {
        Predicate<ToolConfirms.Call> c = confirmer;
        return call == null || c == null || c.test(call);
    }

    /**
     * Resolves the directory of a file tool call: the {@code directory} argument, else the default directory, else the
     * root directory.
     *
     * @throws ToolExecutionException when none is given or the directory does not exist
     */
    public Path resolveDirectory(String directory) {
        Path dir = directory != null && !directory.isBlank() ? Path.of(directory) : projectDirectory();
        if (dir == null) {
            throw new ToolExecutionException(
                    "directory is required: pass directory, the absolute project directory with the source files");
        }
        dir = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new ToolExecutionException("No such directory: " + dir);
        }
        return dir;
    }

    /** The directory a call that names none works in: the default directory, else the root directory; may be null. */
    Path projectDirectory() {
        Path dir = defaultDirectory;
        return dir != null ? dir : rootDirectory();
    }

    /**
     * An extra check for a line of a .properties file that the catalog does not know (the TUI validates Spring Boot
     * properties from the project's metadata); returns the message, or null when the line is fine.
     */
    public Function<String, String> propertyLineValidator() {
        return propertyLineValidator;
    }

    public void setPropertyLineValidator(Function<String, String> propertyLineValidator) {
        this.propertyLineValidator = propertyLineValidator;
    }

    /**
     * Returns the shared {@link CamelCatalog} instance, creating it lazily on first access. Thread-safe: concurrent
     * callers may race but will all see a fully constructed catalog.
     */
    public CamelCatalog catalog() {
        CamelCatalog result = catalog;
        if (result == null) {
            synchronized (this) {
                result = catalog;
                if (result == null) {
                    result = loadCatalog();
                    catalog = result;
                }
            }
        }
        return result;
    }

    /**
     * Sets the catalog to use for this context, bypassing the lazy-load. Use this to inject a pre-built, shared catalog
     * instance (e.g. from {@code CatalogService}) so the tool does not create or download one per call.
     */
    public synchronized void setCatalog(CamelCatalog catalog) {
        this.catalog = catalog;
    }

    private CamelCatalog loadCatalog() {
        String version = camelVersion;
        if (version == null || version.isBlank()) {
            return new DefaultCamelCatalog();
        }
        try {
            return CatalogLoader.loadCatalog(null, version, true);
        } catch (Exception e) {
            throw new ToolExecutionException("Cannot load the Camel catalog for version " + version + ": " + e.getMessage());
        }
    }

    /**
     * Selects the process a tool call names ({@code name} is an integration name or pid), or keeps the current
     * selection when the call names none.
     *
     * @throws ToolExecutionException when the name matches no running process, or is the pid of this process
     */
    public void selectProcess(String nameOrPid) {
        if (nameOrPid == null || nameOrPid.isBlank()) {
            return;
        }
        refuseThisProcess(nameOrPid);
        RuntimeTarget target = runtimeAccess.findProcess(nameOrPid);
        if (target == null) {
            throw new ToolExecutionException(
                    "No running Camel process matches '" + nameOrPid
                                             + "'; list_processes shows what runs");
        }
        selectProcess(target);
    }

    /**
     * Refuses the pid of this process, which runs the tools: a status file of its own comes from a CamelContext it ran
     * for itself, never from an integration, and it would wait for itself to answer an action (CAMEL-26396).
     *
     * @throws ToolExecutionException when the name or pid is the pid of this process
     */
    static void refuseThisProcess(String nameOrPid) {
        if (RuntimeHelper.isThisProcess(nameOrPid)) {
            throw thisProcess(ProcessHandle.current().pid());
        }
    }

    /**
     * The error for a tool call on the pid of this process (see {@link #refuseThisProcess(String)}).
     */
    static ToolExecutionException thisProcess(long pid) {
        return new ToolExecutionException(
                "pid " + pid + " is the process that runs these tools (the MCP server, camel ask or the TUI), not a"
                                          + " Camel integration; list_processes shows the integrations");
    }

    public String readStatus(String section) {
        JsonObject root = readFullStatus();
        if (root == null) {
            RuntimeTarget target = requireTarget();
            return "No status available for "
                   + (target.pid() != null ? "PID " + target.pid() : processDescription());
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

    public JsonObject readFullStatus() {
        return runtimeAccess.readStatus(requireTarget());
    }

    /**
     * Sends an action without a result, such as stopping the routes, to the selected integration, and waits until it
     * confirms the action (an integration before Camel 4.24 takes the action without confirming it).
     *
     * @throws ToolExecutionException when the integration answers that the action failed, or does not take it in time
     */
    public void sendAction(String action, Consumer<JsonObject> configure) {
        JsonObject request = new JsonObject();
        request.put("action", action);
        if (configure != null) {
            configure.accept(request);
        }
        RuntimeAccess.ActionResult result;
        try {
            result = runtimeAccess.sendAction(requireTarget(), request, ACTION_TIMEOUT);
        } catch (IOException e) {
            throw new ToolExecutionException(
                    "Cannot send the action " + action + " to the integration with " + processDescription() + ": "
                                             + e.getMessage());
        }
        if (!result.isOk()) {
            throw new ToolExecutionException(result.error());
        }
    }

    /**
     * Runs an action on the selected integration, and returns the answer as the integration wrote it.
     *
     * @throws ToolExecutionException when the integration answers that the action failed, or does not answer in time
     */
    public String executeAction(String action, Consumer<JsonObject> configure) {
        JsonObject request = new JsonObject();
        request.put("action", action);
        if (configure != null) {
            configure.accept(request);
        }
        String answer;
        try {
            answer = runtimeAccess.executeActionText(requireTarget(), request, ACTION_TIMEOUT);
        } catch (RuntimeAccess.RuntimeStoppedException e) {
            throw new ToolExecutionException(e.getMessage());
        } catch (IOException e) {
            throw new ToolExecutionException(
                    "Cannot send the action " + action + " to the integration with " + processDescription() + ": "
                                             + e.getMessage());
        }
        if (answer == null) {
            throw new ToolExecutionException(
                    "No answer from the integration with " + processDescription() + " to the action " + action + " within "
                                             + ACTION_TIMEOUT / 1000 + " seconds");
        }
        // an integration of Camel 4.24 or later answers a failed action with the error
        JsonObject json = Jsoner.deserialize(answer, (JsonObject) null);
        if (json != null) {
            String status = json.getString("status");
            if ("failed".equals(status) || "error".equals(status)) {
                throw new ToolExecutionException(actionError(json));
            }
            if (unescapeCodeLines(json)) {
                return json.toJson();
            }
        }
        return answer;
    }

    private static String actionError(JsonObject reply) {
        if (reply.get("exception") instanceof JsonObject exception) {
            String message = exception.getString("message") != null
                    ? exception.getString("message") : exception.getString("type");
            if (message != null) {
                return message;
            }
        }
        String error = reply.getString("error");
        if (error != null && !error.isBlank()) {
            return error;
        }
        String message = reply.getString("message");
        return message != null && !message.isBlank() ? message : "Action failed";
    }

    public JsonObject readErrorFile() {
        JsonObject errors = runtimeAccess.readErrorFile(requireTarget());
        unescapeCodeLines(errors);
        return errors;
    }

    public JsonObject readHistoryFile() {
        JsonObject history = runtimeAccess.readHistoryFile(requireTarget());
        unescapeCodeLines(history);
        return history;
    }

    /**
     * Unescapes the lines of source code, so they are as they are in the file. The dev consoles escape each line of a
     * {@code code} list as JSON text in JSON. The CLI commands undo this when they print the lines, but the AI tools
     * gave the lines as they came, so the agent saw {@code \"} that is not in the file (CAMEL-26932).
     *
     * @return whether a line was changed
     */
    static boolean unescapeCodeLines(Object node) {
        boolean changed = false;
        if (node instanceof JsonObject jo) {
            for (Map.Entry<String, Object> e : jo.entrySet()) {
                if ("code".equals(e.getKey()) && e.getValue() instanceof JsonArray lines) {
                    for (Object line : lines) {
                        if (line instanceof JsonObject l && l.get("code") instanceof String code) {
                            String text = Jsoner.unescape(code);
                            if (!text.equals(code)) {
                                l.put("code", text);
                                changed = true;
                            }
                        }
                    }
                } else {
                    changed |= unescapeCodeLines(e.getValue());
                }
            }
        } else if (node instanceof Collection<?> list) {
            for (Object o : list) {
                changed |= unescapeCodeLines(o);
            }
        }
        return changed;
    }

    public String stopApplication() {
        return runtimeAccess.stopApplication(requireTarget());
    }

    public JsonObject sendMessage(String endpoint, String body, String headers) {
        return sendMessage(endpoint, body, headers, 0, null);
    }

    public JsonObject sendMessage(String endpoint, String body, String headers, int repeat, String interval) {
        return runtimeAccess.sendMessage(requireTarget(), endpoint, body, headers, repeat, interval);
    }

    /** Discovers opaque runtime targets. */
    public List<RuntimeTarget> discoverRuntimeTargets() {
        return runtimeAccess.discoverProcesses();
    }

    /** Finds an opaque runtime target by name, PID string, or stable target identifier. */
    public RuntimeTarget findRuntimeTarget(String selector) {
        return runtimeAccess.findProcess(selector);
    }

    /** Resolves the target chosen by the runtime access implementation's chaos-selection rules. */
    public RuntimeTarget findChaosRuntimeTarget() {
        return runtimeAccess.findChaosProcess();
    }

    /**
     * Legacy local-process view retained for callers that work with PID-based log files and process handles. Remote
     * targets are intentionally omitted rather than assigned a synthetic PID.
     */
    public List<RuntimeHelper.ProcessInfo> discoverProcesses() {
        return discoverRuntimeTargets().stream()
                .filter(target -> target.pid() != null)
                .map(ToolContext::legacyProcessInfo)
                .toList();
    }

    /** Legacy local-process lookup retained for callers that need PID-based process metadata. */
    public RuntimeHelper.ProcessInfo findProcess(String name) {
        return legacyProcessInfo(findRuntimeTarget(name));
    }

    private static RuntimeHelper.ProcessInfo legacyProcessInfo(RuntimeTarget target) {
        return target == null || target.pid() == null
                ? null
                : new RuntimeHelper.ProcessInfo(target.pid(), target.name(), target.contextName());
    }

    public JsonObject executeSqlQuery(String sql, String datasource, int maxRows, int queryTimeout) {
        return runtimeAccess.executeSqlQuery(requireTarget(), sql, datasource, maxRows, queryTimeout);
    }

    /** Camel version of the selected runtime, or {@code null} when unknown. */
    public String runtimeVersion() {
        return runtimeAccess.runtimeVersion(requireTarget());
    }

    private RuntimeTarget requireTarget() {
        requireProcess();
        return selectedTarget;
    }

    public void requireProcess() {
        if (selectedTarget == null) {
            throw new ToolExecutionException(
                    "No running Camel process connected. Start one with: camel run <file>");
        }
    }
}
