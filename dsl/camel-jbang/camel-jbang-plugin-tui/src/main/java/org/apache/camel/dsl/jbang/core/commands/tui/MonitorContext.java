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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import dev.tamboui.style.Style;
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
    final List<IntegrationInfo> phantomIntegrations = new CopyOnWriteArrayList<>();
    private final AtomicInteger phantomCounter = new AtomicInteger();
    int shellPercent;
    boolean logPinned;
    int logPinPercent;
    boolean logPinVisible;
    boolean ratePerMinute;
    boolean confirmActions;
    boolean validateOnSave = true;
    /** True while the shell (F6) or AI (F8) panel is open and owns keyboard focus. */
    boolean bottomPanelFocused;
    /** Shell/AI panel opens at the top of the content area instead of the bottom. */
    boolean panelTop;
    /** Shell/AI panel is drawn over the tab instead of taking space away from it. */
    boolean panelOverlay;
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

    /**
     * Border style for a focusable pane. The accent color marks the pane that receives keys; while the shell or AI
     * panel is open that panel owns the focus, so every tab pane is drawn muted.
     */
    Style paneBorder(boolean focused) {
        return paneBorder(focused, Theme.muted());
    }

    /**
     * Same as {@link #paneBorder(boolean)} with a custom style for the unfocused state.
     */
    Style paneBorder(boolean focused, Style unfocused) {
        return focused && !bottomPanelFocused ? Style.EMPTY.fg(Theme.accent()) : unfocused;
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

    void addPhantom(IntegrationInfo info) {
        info.phantom = true;
        info.pid = "phantom-" + phantomCounter.incrementAndGet();
        info.state = 9;
        phantomIntegrations.add(info);
    }

    void removePhantom(String pid) {
        phantomIntegrations.removeIf(i -> pid.equals(i.pid));
    }

    IntegrationInfo findPhantomByDirectory(String dir) {
        if (dir == null) {
            return null;
        }
        return phantomIntegrations.stream()
                .filter(i -> dir.equals(i.sourceDir))
                .findFirst().orElse(null);
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
