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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.style.Style;
import dev.tamboui.tui.TuiRunner;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonObject;

/**
 * Shared state accessible to all {@link MonitorTab} implementations.
 */
class MonitorContext {

    final AtomicReference<List<IntegrationInfo>> data;
    final AtomicReference<List<InfraInfo>> infraData;
    /**
     * Reads the integrations and the data tabs show on demand (errors, activity, history, traces, spans); set by
     * CamelMonitor at startup, before the tabs are created. Null in a test that builds a context by hand.
     */
    DataRefreshService dataService;
    TuiRunner runner;
    final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "camel-tui-bg");
        t.setDaemon(true);
        return t;
    });
    /** The structure of the routes the diagrams draw, asked of an integration again only when its routes change. */
    final DiagramTopology diagramTopology = new DiagramTopology();

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
    /**
     * Whether a tape drives the monitor: a model records one (tui_tape_start), or {@code --record} plays one. Nothing
     * asks the user to confirm then (see {@link Confirmations}).
     */
    BooleanSupplier tapeDriven = () -> false;
    boolean validateOnSave = true;
    // whether the source editor shows the route tree at first (the Route Tree setting)
    boolean routeTree;
    /** True while the shell (F6) or AI (F8) panel is open and owns keyboard focus. */
    boolean bottomPanelFocused;
    /** Shell/AI panel opens at the top of the content area instead of the bottom. */
    boolean panelTop;
    /** Shell/AI panel is drawn over the tab instead of taking space away from it. */
    boolean panelOverlay;
    /** Collector behind the Ollama tab and the tui_get_ollama tool; set by CamelMonitor at startup. */
    OllamaMonitor ollamaMonitor;
    BiConsumer<String, Boolean> notificationCallback;
    BiConsumer<String, String> openMarkdownCallback;
    /** Opens markdown in the doc viewer scrolled to a heading: title, markdown, heading (may be null). */
    MarkdownOpener openMarkdownAtCallback;

    interface MarkdownOpener {
        void open(String title, String markdown, String heading);
    }

    /** Starts the AI project overview (CAMEL-25143) in the AI panel, which opens to show how it goes. */
    Runnable projectOverviewCallback;
    /** Opens the AI panel with a question in its input (the fix of a problem of the Source editor, Shift+F8). */
    AskAi askAiCallback;
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

    /**
     * Whether to ask the user before an action such as quitting or stopping an integration (the Confirm actions
     * setting). While a tape drives the monitor nothing asks: the model or the tape does everything itself, and the
     * keys it sends would not answer the dialog anyway (CAMEL-25444).
     */
    boolean confirmFirst() {
        return confirmActions && !tapeDriven.getAsBoolean();
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
        Runnable opened = onProjectOpened;
        if (opened != null) {
            opened.run();
        }
    }

    // called when a project is opened, so what was shown of an earlier run of it (its failure log) is put away
    volatile Runnable onProjectOpened;

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

    /**
     * Sends a request no one waits for an answer to: a route command, reset stats, a logging level. The connector of
     * Camel 4.21 or later gets a file of its own for it (see {@link #takesRequestFiles(String)}), which the next
     * request of a tab cannot overwrite before the connector read it.
     */
    void fireAction(String pid, JsonObject request) {
        if (takesRequestFiles(pid)) {
            fireRequest(pid, request);
            return;
        }
        Object lock = actionLocks.computeIfAbsent(pid, k -> new Object());
        synchronized (lock) {
            Path actionFile = getActionFile(pid);
            PathUtils.writeTextSafely(request.toJson(), actionFile);
        }
    }

    /** How long a request sent with fireAction may wait for the connector, which runs it within a second or so. */
    static final long FIRED_REQUEST_TIMEOUT_MS = 60_000;

    /**
     * Writes the request in a file of its own, which the connector deletes once it ran it. An answer it writes for it
     * (one that acknowledges the action) is removed, and so is a request still there after a minute (the integration
     * stopped).
     */
    private void fireRequest(String pid, JsonObject request) {
        String requestId = UUID.randomUUID().toString();
        Path dir = CommandLineHelper.getCamelDir();
        Path actionFile = dir.resolve(pid + "-action-" + requestId + ".json");
        Path outputFile = dir.resolve(pid + "-output-" + requestId + ".json");
        Path temporary = dir.resolve(pid + "-" + requestId + ".tmp");
        try {
            Files.writeString(temporary, request.toJson());
            // the connector must never read half a request
            Files.move(temporary, actionFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            PathUtils.deleteFile(temporary);
            return;
        }
        backgroundExecutor.execute(() -> {
            long deadline = MonotonicClock.millis() + FIRED_REQUEST_TIMEOUT_MS;
            try {
                while (Files.exists(actionFile) && MonotonicClock.millis() < deadline) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                PathUtils.deleteFile(actionFile);
                PathUtils.deleteFile(outputFile);
            }
        });
    }

    /**
     * Whether the connector of the integration takes a file per request ({@code <pid>-action-<id>.json}, answered in
     * {@code <pid>-output-<id>.json}), which it does since Camel 4.21 (CAMEL-25448). The tabs, and other clients such
     * as camel ask, then do not overwrite or take each other's requests and answers. An older connector only reads the
     * shared {@code <pid>-action.json}, one request at a time.
     */
    boolean takesRequestFiles(String pid) {
        List<IntegrationInfo> infos = data.get();
        if (pid == null || infos == null || pid.isEmpty() || !pid.chars().allMatch(Character::isDigit)) {
            // a phantom project has no process to ask
            return false;
        }
        for (IntegrationInfo info : infos) {
            if (pid.equals(info.pid)) {
                return isCamelVersionAtLeast(info.camelVersion, 4, 21);
            }
        }
        return false;
    }

    /** Whether a Camel version such as 4.24.0-SNAPSHOT is the given major.minor or later; false when unknown. */
    static boolean isCamelVersionAtLeast(String version, int major, int minor) {
        Matcher m = version != null ? VERSION.matcher(version) : null;
        if (m == null || !m.find()) {
            return false;
        }
        int actualMajor = Integer.parseInt(m.group(1));
        int actualMinor = Integer.parseInt(m.group(2));
        return actualMajor > major || actualMajor == major && actualMinor >= minor;
    }

    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)");

    /** How long the jvm answer of an integration is kept for the tabs that need its classpath. */
    static final long JVM_INFO_TTL_MS = 30_000;
    private final ConcurrentHashMap<String, CachedAnswer> jvmInfo = new ConcurrentHashMap<>();

    private record CachedAnswer(JsonObject answer, long time) {
    }

    /**
     * The jvm answer of an integration (its classpath and JVM details), shared by the tabs that need it (Classpath, CVE
     * Audit, Maven Dependencies, Heap Histogram): an action takes a second of the integration, so it is asked once, not
     * once per tab.
     */
    JsonObject jvmInfo(String pid, long timeoutMs) {
        Object lock = actionLocks.computeIfAbsent(pid, k -> new Object());
        synchronized (lock) {
            CachedAnswer cached = jvmInfo.get(pid);
            long now = MonotonicClock.millis();
            if (cached != null && now - cached.time() < JVM_INFO_TTL_MS) {
                return cached.answer();
            }
            JsonObject request = new JsonObject();
            request.put("action", "jvm");
            JsonObject answer = executeAction(pid, request, timeoutMs);
            if (answer != null) {
                jvmInfo.put(pid, new CachedAnswer(answer, now));
            }
            return answer;
        }
    }

    /**
     * Sends a request and waits for its answer; null when none came in time. The connector of Camel 4.21 or later gets
     * files of its own for it, so the requests of several tabs are answered together, and no lock is needed.
     */
    JsonObject executeAction(String pid, JsonObject request, long timeoutMs) {
        if (takesRequestFiles(pid)) {
            return executeIndependentAction(pid, request, timeoutMs);
        }
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

    /** Request-specific files keep a late response from being consumed by another action. */
    JsonObject executeIndependentAction(String pid, JsonObject request, long timeoutMs) {
        try {
            return RuntimeHelper.executeAction(Long.parseLong(pid), request, timeoutMs);
        } catch (IOException e) {
            return null;
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

    Path getErrorFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-error.json");
    }

    /** Asks the AI to fix a problem of a source file, or a line that fails at runtime. */
    @FunctionalInterface
    interface AskAi {
        void fixProblem(Path file, int line, String problem, String lineText);

        /**
         * Asks the AI to fix a line whose processors fail at runtime (Shift+F8 on a line with failures in the live run
         * data): the failure says how many exchanges failed and the exception of the last one.
         */
        default void fixFailure(Path file, int line, String failure, String lineText) {
            fixProblem(file, line, failure, lineText);
        }

        /**
         * Asks the AI about an ERROR of the log whose source line is not known (Shift+F8 in the Log tab): the error
         * line, the exception and its causes.
         */
        default void explainLogError(String error) {
        }
    }
}
