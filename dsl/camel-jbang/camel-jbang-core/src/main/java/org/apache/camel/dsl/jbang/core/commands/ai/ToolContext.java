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

import java.util.List;
import java.util.function.Consumer;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
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

    public long pid() {
        return pid;
    }

    public void selectProcess(long pid) {
        this.pid = pid;
    }

    public boolean hasProcess() {
        return pid >= 0;
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
                    result = new DefaultCamelCatalog();
                    catalog = result;
                }
            }
        }
        return result;
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
