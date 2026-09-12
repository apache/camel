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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonObject;

/**
 * Shared execution context for AI tools. Wraps {@link RuntimeHelper} (process IPC) and {@link CamelCatalog} (component
 * metadata). This class intentionally has no dependency on MCP or Quarkus so it can be used by both the Agent REPL and
 * the MCP server.
 */
public class ToolContext {

    private volatile long pid = -1;
    private volatile CamelCatalog catalog;
    private volatile String camelVersion;
    private volatile Path defaultDirectory;
    private volatile Function<String, String> propertyLineValidator;
    private volatile boolean autoSelectSingleProcess = true;

    public long pid() {
        return pid;
    }

    public void selectProcess(long pid) {
        this.pid = pid;
    }

    public boolean hasProcess() {
        return pid >= 0;
    }

    /** Forgets the selected process, so tools fall back to what needs no running integration. */
    public void clearProcess() {
        this.pid = -1;
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
        RuntimeHelper.ProcessInfo only = RuntimeHelper.findProcess(null);
        if (only == null) {
            return false;
        }
        selectProcess(only.pid());
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
     * selected integration, an MCP server without a selection leaves it null and requires the argument.
     */
    public Path defaultDirectory() {
        return defaultDirectory;
    }

    public void setDefaultDirectory(Path defaultDirectory) {
        this.defaultDirectory = defaultDirectory;
    }

    /**
     * Resolves the directory of a file tool call: the {@code directory} argument, else the default directory.
     *
     * @throws ToolExecutionException when neither is given or the directory does not exist
     */
    public Path resolveDirectory(String directory) {
        Path dir = directory != null && !directory.isBlank() ? Path.of(directory) : defaultDirectory;
        if (dir == null) {
            throw new ToolExecutionException("directory is required: the project directory with the source files");
        }
        dir = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new ToolExecutionException("No such directory: " + dir);
        }
        return dir;
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
     * @throws ToolExecutionException when the name matches no running process
     */
    public void selectProcess(String nameOrPid) {
        if (nameOrPid == null || nameOrPid.isBlank()) {
            return;
        }
        RuntimeHelper.ProcessInfo p = RuntimeHelper.findProcess(nameOrPid);
        if (p == null) {
            throw new ToolExecutionException(
                    "No running Camel process matches '" + nameOrPid
                                             + "'; list_processes shows what runs");
        }
        selectProcess(p.pid());
    }

    public String readStatus(String section) {
        requireProcess();
        return RuntimeHelper.readStatusSection(pid, section);
    }

    public JsonObject readFullStatus() {
        requireProcess();
        return RuntimeHelper.readStatus(pid);
    }

    public String executeAction(String action, Consumer<JsonObject> configure) {
        requireProcess();
        return RuntimeHelper.executeAction(pid, action, configure);
    }

    public JsonObject readErrorFile() {
        requireProcess();
        return RuntimeHelper.readErrorFile(pid);
    }

    public JsonObject readHistoryFile() {
        requireProcess();
        return RuntimeHelper.readHistoryFile(pid);
    }

    public String stopApplication() {
        requireProcess();
        return RuntimeHelper.stopApplication(pid);
    }

    public JsonObject sendMessage(String endpoint, String body, String headers) {
        requireProcess();
        return RuntimeHelper.sendMessage(pid, endpoint, body, headers);
    }

    public List<RuntimeHelper.ProcessInfo> discoverProcesses() {
        return RuntimeHelper.discoverProcesses();
    }

    public RuntimeHelper.ProcessInfo findProcess(String name) {
        return RuntimeHelper.findProcess(name);
    }

    public JsonObject executeSqlQuery(String sql, String datasource, int maxRows, int queryTimeout) {
        requireProcess();
        return RuntimeHelper.executeSqlQuery(pid, sql, datasource, maxRows, queryTimeout);
    }

    public void requireProcess() {
        if (pid < 0) {
            throw new ToolExecutionException(
                    "No running Camel process connected. Start one with: camel run <file>");
        }
    }
}
