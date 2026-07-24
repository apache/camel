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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import dev.tamboui.tui.TuiRunner;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonObject;

/**
 * Shared state accessible to all {@link MonitorTab} implementations.
 */
class MonitorContext {

    final AtomicReference<List<IntegrationInfo>> data;
    final AtomicReference<List<InfraInfo>> infraData;
    TuiRunner runner;
    final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "camel-tui-bg");
        t.setDaemon(true);
        return t;
    });

    volatile String selectedPid;
    volatile String lastSelectedName;
    int shellPercent;
    boolean logPinned;
    int logPinPercent;
    boolean logPinVisible;
    boolean ratePerMinute;
    BiConsumer<String, Boolean> notificationCallback;
    BiConsumer<String, String> openMarkdownCallback;
    OpenOptionsCallback openOptionsCallback;
    OpenOptionsCallback openCatalogDocCallback;

    @FunctionalInterface
    interface OpenOptionsCallback {
        void accept(String name, String kind, CamelCatalog catalog);
    }

    MonitorContext(
                   AtomicReference<List<IntegrationInfo>> data,
                   AtomicReference<List<InfraInfo>> infraData) {
        this.data = data;
        this.infraData = infraData;
    }

    IntegrationInfo findSelectedIntegration() {
        String pid = selectedPid;
        if (pid == null) {
            return null;
        }
        return data.get().stream()
                .filter(i -> pid.equals(i.pid) && !i.vanishing)
                .findFirst().orElse(null);
    }

    InfraInfo findSelectedInfra() {
        String pid = selectedPid;
        if (pid == null) {
            return null;
        }
        return infraData.get().stream()
                .filter(i -> pid.equals(i.pid) && !i.vanishing)
                .findFirst().orElse(null);
    }

    boolean isInfraSelected() {
        return findSelectedInfra() != null;
    }

    String selectedName() {
        IntegrationInfo info = findSelectedIntegration();
        if (info != null) {
            return TuiHelper.truncate(info.name, 20);
        }
        InfraInfo infra = findSelectedInfra();
        if (infra != null) {
            return TuiHelper.truncate(infra.alias, 20);
        }
        return "?";
    }

    private final ConcurrentHashMap<String, Object> actionLocks = new ConcurrentHashMap<>();

    void fireAction(String pid, JsonObject request) {
        Object lock = actionLocks.computeIfAbsent(pid, k -> new Object());
        synchronized (lock) {
            Path actionFile = getActionFile(pid);
            PathUtils.writeTextSafely(request.toJson(), actionFile);
        }
    }

    JsonObject executeAction(String pid, JsonObject request, long timeoutMs) {
        Object lock = actionLocks.computeIfAbsent(pid, k -> new Object());
        synchronized (lock) {
            Path outputFile = getOutputFile(pid);
            PathUtils.deleteFile(outputFile);
            Path actionFile = getActionFile(pid);
            PathUtils.writeTextSafely(request.toJson(), actionFile);
            try {
                return TuiHelper.pollJsonResponse(outputFile, timeoutMs);
            } finally {
                PathUtils.deleteFile(outputFile);
            }
        }
    }

    Path getActionFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-action.json");
    }

    Path getOutputFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-output.json");
    }

    Path getTraceFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-trace.json");
    }

}
