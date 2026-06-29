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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.tamboui.tui.TuiRunner;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Manages all data refresh logic for the TUI monitor: integration scanning, infra services, traces, spans, errors, and
 * history data.
 * <p>
 * Extracted from {@link CamelMonitor} to reduce class size and isolate data-loading concerns from event handling and
 * rendering.
 */
class DataRefreshService {

    private static final long VANISH_DURATION_MS = 6000;
    private static final int MAX_TRACES = 200;

    /**
     * Callback interface for operations that remain in {@link CamelMonitor} (UI state queries needed during refresh).
     */
    interface RefreshContext {
        int selectedTab();

        boolean isSwitchPopupVisible();

        String getPendingAutoSelect();

        void clearPendingAutoSelect();

        void onInfraAutoSelected(int tableIndex, String pid);

        boolean isInfraSelected();
    }

    // State
    private final AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(Collections.emptyList());
    private final Map<String, VanishingInfo> vanishing = new ConcurrentHashMap<>();
    private final Map<String, VanishingInfraInfo> vanishingInfra = new ConcurrentHashMap<>();

    // Sparkline/chart history for all metric families
    private final MetricsCollector metrics = new MetricsCollector();

    // Cached PID list -- full process scan throttled to every 2 seconds (1 second in burst mode)
    private volatile List<Long> cachedPids = Collections.emptyList();
    private volatile long lastFullScanTime;
    private volatile long burstModeUntil;
    final Set<String> stoppingPids = ConcurrentHashMap.newKeySet();

    // Trace/history data -- shared between CamelMonitor and tabs
    private final AtomicReference<List<TraceEntry>> traces = new AtomicReference<>(Collections.emptyList());
    private final Map<String, Long> traceFilePositions = new ConcurrentHashMap<>();
    // OTel span data -- shared between CamelMonitor and SpansTab
    private final AtomicReference<List<SpanEntry>> otelSpans = new AtomicReference<>(List.of());

    private volatile long lastRefresh;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private MonitorContext ctx;
    private final String name;
    private final RefreshContext refreshCtx;
    private final Function<String, Path> statusFileResolver;
    private final Function<String, Path> errorFileResolver;

    // Tab index constants (mirrored from CamelMonitor to avoid coupling)
    private static final int TAB_OVERVIEW = 0;

    DataRefreshService(
                       String name,
                       RefreshContext refreshCtx,
                       Function<String, Path> statusFileResolver,
                       Function<String, Path> errorFileResolver) {
        this.name = name;
        this.refreshCtx = refreshCtx;
        this.statusFileResolver = statusFileResolver;
        this.errorFileResolver = errorFileResolver;
    }

    /**
     * Set the monitor context. Called after construction to break the circular dependency between DataRefreshService
     * (owns the AtomicReferences) and MonitorContext (needs those references).
     */
    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    // ---- Public accessors ----

    AtomicReference<List<IntegrationInfo>> data() {
        return data;
    }

    AtomicReference<List<InfraInfo>> infraData() {
        return infraData;
    }

    AtomicReference<List<TraceEntry>> traces() {
        return traces;
    }

    Map<String, Long> traceFilePositions() {
        return traceFilePositions;
    }

    AtomicReference<List<SpanEntry>> otelSpans() {
        return otelSpans;
    }

    MetricsCollector metrics() {
        return metrics;
    }

    Set<String> stoppingPids() {
        return stoppingPids;
    }

    long lastRefresh() {
        return lastRefresh;
    }

    // ---- Burst mode ----

    void enableBurstMode() {
        burstModeUntil = System.currentTimeMillis() + 20_000;
    }

    boolean isBurstMode() {
        return System.currentTimeMillis() < burstModeUntil;
    }

    // ---- Refresh orchestration ----

    /**
     * Trigger an asynchronous data refresh. If a refresh is already in progress, the call is a no-op. Falls back to
     * synchronous refresh when no TUI runner is available.
     *
     * @param runner               the TUI runner for scheduling background work
     * @param logRefresher         callback to refresh log data (stays on CamelMonitor)
     * @param conditionalRefresher callback to refresh tab-conditional data (stays on CamelMonitor)
     */
    void refresh(TuiRunner runner, Runnable logRefresher, Runnable conditionalRefresher) {
        if (runner == null) {
            refreshSync(logRefresher, conditionalRefresher);
            return;
        }
        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }
        lastRefresh = System.currentTimeMillis();
        runner.scheduler().execute(() -> {
            try {
                refreshSync(logRefresher, conditionalRefresher);
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    /**
     * Perform a synchronous data refresh cycle: log data, integration scan, auto-select, and conditional tab data.
     */
    void refreshSync(Runnable logRefresher, Runnable conditionalRefresher) {
        lastRefresh = System.currentTimeMillis();
        try {
            logRefresher.run();
            boolean fullScan = scanIntegrations();
            List<IntegrationInfo> infos = data.get();
            handleAutoSelect(infos, fullScan);
            conditionalRefresher.run();
        } catch (Exception e) {
            // ignore refresh errors
        }
    }

    // ---- PID helpers ----

    List<Long> selectedPidAsList() {
        if (ctx.selectedPid == null) {
            return Collections.emptyList();
        }
        try {
            return List.of(Long.parseLong(ctx.selectedPid));
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
    }

    // ---- Integration scanning ----

    private boolean scanIntegrations() {
        List<IntegrationInfo> infos = new ArrayList<>();
        long now = System.currentTimeMillis();
        boolean wantFullScan = refreshCtx.selectedTab() == TAB_OVERVIEW || refreshCtx.isSwitchPopupVisible()
                || cachedPids.isEmpty();
        long scanInterval = isBurstMode() ? 1000 : 2000;
        boolean fullScan = wantFullScan && (now - lastFullScanTime >= scanInterval);
        List<Long> pids;
        if (fullScan) {
            pids = findPids(name);
            cachedPids = pids;
            lastFullScanTime = now;
        } else {
            pids = cachedPids;
        }

        List<Long> refreshPids;
        if (!fullScan && ctx.selectedPid != null) {
            try {
                refreshPids = List.of(Long.parseLong(ctx.selectedPid));
            } catch (NumberFormatException e) {
                refreshPids = pids;
            }
        } else {
            refreshPids = pids;
        }
        for (Long pid : refreshPids) {
            JsonObject root = loadStatus(pid);
            if (root != null) {
                ProcessHandle ph = ProcessHandle.of(pid).orElse(null);
                if (ph == null) {
                    continue;
                }
                IntegrationInfo info = StatusParser.parseIntegration(ph, root);
                if (info != null) {
                    infos.add(info);
                    metrics.updateThroughputHistory(info);
                    metrics.updateEndpointHistory(info);
                    metrics.updateCbHistory(info);
                    metrics.updateHeapHistory(info);
                    metrics.updateLoadMetrics(ph, info);
                }
            }
        }
        if (!fullScan && ctx.selectedPid != null) {
            List<IntegrationInfo> previous = data.get();
            for (IntegrationInfo prev : previous) {
                if (!prev.vanishing && !ctx.selectedPid.equals(prev.pid)) {
                    infos.add(prev);
                }
            }
        }

        handleVanishing(infos, now);
        data.set(infos);
        return fullScan;
    }

    private void handleVanishing(List<IntegrationInfo> infos, long now) {
        Set<String> livePids = infos.stream().map(i -> i.pid).collect(Collectors.toSet());
        List<IntegrationInfo> previous = data.get();
        for (IntegrationInfo prev : previous) {
            if (!prev.vanishing && !livePids.contains(prev.pid) && !vanishing.containsKey(prev.pid)) {
                boolean wasExplicitStop = stoppingPids.remove(prev.pid);
                if (wasExplicitStop) {
                    metrics.removeVanished(prev.pid);
                } else {
                    vanishing.put(prev.pid, new VanishingInfo(prev, System.currentTimeMillis()));
                }
            }
        }

        Iterator<Map.Entry<String, VanishingInfo>> it = vanishing.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, VanishingInfo> entry = it.next();
            if (now - entry.getValue().startTime > VANISH_DURATION_MS) {
                it.remove();
                metrics.removeVanished(entry.getKey());
            } else if (!livePids.contains(entry.getKey())) {
                IntegrationInfo ghost = entry.getValue().info;
                ghost.vanishing = true;
                ghost.vanishStart = entry.getValue().startTime;
                infos.add(ghost);
            } else {
                it.remove();
            }
        }
    }

    private void handleAutoSelect(List<IntegrationInfo> infos, boolean fullScan) {
        if (ctx.selectedPid != null && !refreshCtx.isInfraSelected()) {
            boolean stillAlive = infos.stream()
                    .anyMatch(i -> ctx.selectedPid.equals(i.pid) && !i.vanishing);
            if (!stillAlive) {
                IntegrationInfo gone = infos.stream()
                        .filter(i -> ctx.selectedPid.equals(i.pid))
                        .findFirst().orElse(null);
                if (gone != null) {
                    ctx.lastSelectedName = gone.name;
                }
                ctx.selectedPid = null;
            }
        }

        String autoSelect = refreshCtx.getPendingAutoSelect();
        if (autoSelect != null) {
            for (IntegrationInfo info : infos) {
                if (!info.vanishing && autoSelect.equalsIgnoreCase(info.name)) {
                    ctx.selectedPid = info.pid;
                    ctx.lastSelectedName = null;
                    refreshCtx.clearPendingAutoSelect();
                    break;
                }
            }
        }

        if (ctx.selectedPid == null && ctx.lastSelectedName != null && !refreshCtx.isInfraSelected()) {
            for (IntegrationInfo info : infos) {
                if (!info.vanishing && ctx.lastSelectedName.equalsIgnoreCase(info.name)) {
                    ctx.selectedPid = info.pid;
                    ctx.lastSelectedName = null;
                    break;
                }
            }
        }

        if (fullScan) {
            refreshInfraData();
        }

        if (ctx.selectedPid == null && !infraData.get().isEmpty()
                && infos.stream().noneMatch(i -> !i.vanishing)) {
            List<InfraInfo> infras = infraData.get();
            if (!infras.isEmpty()) {
                int firstInfraIndex = infos.size() + (infras.size() > 0 ? 1 : 0);
                refreshCtx.onInfraAutoSelected(firstInfraIndex, infras.get(0).pid);
            }
        }
    }

    // ---- Infra data ----

    @SuppressWarnings("unchecked")
    private void refreshInfraData() {
        List<InfraInfo> infraInfos = new ArrayList<>();
        try {
            Path camelDir = CommandLineHelper.getCamelDir();
            if (Files.isDirectory(camelDir)) {
                try (var files = Files.list(camelDir)) {
                    List<Path> jsonFiles = files
                            .filter(p -> {
                                String n = p.getFileName().toString();
                                return n.startsWith("infra-") && n.endsWith(".json");
                            })
                            .toList();
                    for (Path jsonFile : jsonFiles) {
                        String fn = jsonFile.getFileName().toString();
                        // Format: infra-{alias}-{pid}.json
                        String withoutExt = fn.substring(0, fn.lastIndexOf('.'));
                        int lastDash = withoutExt.lastIndexOf('-');
                        if (lastDash <= 6) {
                            continue;
                        }
                        String alias = withoutExt.substring(6, lastDash);
                        String pidStr = withoutExt.substring(lastDash + 1);
                        long pid;
                        try {
                            pid = Long.parseLong(pidStr);
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);

                        InfraInfo info = new InfraInfo();
                        info.pid = pidStr;
                        info.alias = alias;
                        info.alive = alive;
                        try {
                            String json = Files.readString(jsonFile);
                            Object parsed = Jsoner.deserialize(json);
                            if (parsed instanceof Map<?, ?> map) {
                                for (Map.Entry<?, ?> e : map.entrySet()) {
                                    info.properties.put(String.valueOf(e.getKey()), e.getValue());
                                }
                            }
                        } catch (Exception e) {
                            // ignore parse errors
                        }
                        info.serviceVersion = StatusParser.objToString(info.properties.get("serviceVersion"));
                        infraInfos.add(info);
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }

        // Handle vanishing infra services
        Set<String> liveInfraPids = infraInfos.stream().map(i -> i.pid).collect(Collectors.toSet());
        List<InfraInfo> previousInfra = infraData.get();
        for (InfraInfo prev : previousInfra) {
            if (!prev.vanishing && !liveInfraPids.contains(prev.pid) && !vanishingInfra.containsKey(prev.pid)) {
                vanishingInfra.put(prev.pid, new VanishingInfraInfo(prev, System.currentTimeMillis()));
            }
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, VanishingInfraInfo>> infraIt = vanishingInfra.entrySet().iterator();
        while (infraIt.hasNext()) {
            Map.Entry<String, VanishingInfraInfo> entry = infraIt.next();
            if (now - entry.getValue().startTime > VANISH_DURATION_MS) {
                infraIt.remove();
            } else if (!liveInfraPids.contains(entry.getKey())) {
                InfraInfo ghost = entry.getValue().info;
                ghost.vanishing = true;
                ghost.vanishStart = entry.getValue().startTime;
                infraInfos.add(ghost);
            } else {
                infraIt.remove();
            }
        }

        infraInfos.sort((a, b) -> a.alias.compareToIgnoreCase(b.alias));
        infraData.set(infraInfos);
    }

    // ---- Trace data ----

    void refreshTraceData(List<Long> pids) {
        List<TraceEntry> allTraces = new ArrayList<>(traces.get());

        for (Long pid : pids) {
            readTraceFile(Long.toString(pid), allTraces);
        }

        // Sort by timestamp
        allTraces.sort((a, b) -> {
            if (a.timestamp == null && b.timestamp == null) {
                return 0;
            }
            if (a.timestamp == null) {
                return -1;
            }
            if (b.timestamp == null) {
                return 1;
            }
            return a.timestamp.compareTo(b.timestamp);
        });

        // Keep only last MAX_TRACES
        if (allTraces.size() > MAX_TRACES) {
            allTraces = new ArrayList<>(allTraces.subList(allTraces.size() - MAX_TRACES, allTraces.size()));
        }

        traces.set(allTraces);
    }

    @SuppressWarnings("unchecked")
    private void readTraceFile(String pid, List<TraceEntry> allTraces) {
        Path traceFile = CommandLineHelper.getCamelDir().resolve(pid + "-trace.json");
        if (!Files.exists(traceFile)) {
            return;
        }

        long lastPos = traceFilePositions.getOrDefault(pid, 0L);

        try (RandomAccessFile raf = new RandomAccessFile(traceFile.toFile(), "r")) {
            long length = raf.length();
            if (length <= lastPos) {
                return; // no new data
            }

            raf.seek(lastPos);
            // If we're resuming mid-file, skip any partial line
            if (lastPos > 0) {
                raf.readLine();
            }

            // Read remaining bytes
            long startPos = raf.getFilePointer();
            byte[] remaining = new byte[(int) (length - startPos)];
            raf.readFully(remaining);
            String content = new String(remaining, StandardCharsets.UTF_8);

            traceFilePositions.put(pid, length);

            // Each line is a JSON object: {"enabled":true,"traces":[...]}
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    JsonObject json = (JsonObject) Jsoner.deserialize(line);
                    Object tracesArray = json.get("traces");
                    if (tracesArray instanceof List<?> traceList) {
                        for (Object traceObj : traceList) {
                            if (traceObj instanceof JsonObject traceJson) {
                                TraceEntry entry = StatusParser.parseTraceEntry(traceJson, pid);
                                if (entry != null) {
                                    allTraces.add(entry);
                                }
                            }
                        }
                    } else {
                        // Fallback: try parsing the line itself as a trace entry
                        TraceEntry entry = StatusParser.parseTraceEntry(json, pid);
                        if (entry != null) {
                            allTraces.add(entry);
                        }
                    }
                } catch (Exception e) {
                    // skip malformed lines
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }

    // ---- Span data ----

    @SuppressWarnings("unchecked")
    void refreshSpanData() {
        String pid = ctx.selectedPid;
        if (pid == null) {
            return;
        }
        try {
            // Send action to request span data
            Path outputFile = ctx.getOutputFile(pid);
            PathUtils.deleteFile(outputFile);

            JsonObject action = new JsonObject();
            action.put("action", "span");
            action.put("dump", "true");
            action.put("limit", "500");
            Path actionFile = ctx.getActionFile(pid);
            PathUtils.writeTextSafely(action.toJson(), actionFile);

            // Poll for response
            JsonObject response = MonitorContext.pollJsonResponse(outputFile, 3000);
            if (response != null) {
                Boolean enabled = response.getBoolean("enabled");
                if (enabled != null && enabled) {
                    JsonArray arr = response.getCollection("spans");
                    if (arr != null) {
                        List<SpanEntry> entries = new ArrayList<>();
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject spanObj = (JsonObject) arr.get(i);
                            entries.add(SpanEntry.fromJson(spanObj));
                        }
                        otelSpans.set(entries);
                    }
                }
                PathUtils.deleteFile(outputFile);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // ---- Error data ----

    void refreshErrorData(List<Long> pids) {
        IntegrationInfo sel = ctx.findSelectedIntegration();
        if (sel == null) {
            return;
        }
        try {
            long pid = Long.parseLong(sel.pid);
            JsonObject root = loadErrorFile(pid);
            if (root != null) {
                List<ErrorInfo> parsed = StatusParser.parseErrors(root);
                sel.errors.clear();
                sel.errors.addAll(parsed);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // ---- History data ----

    /**
     * Load history data for the given PIDs and return the parsed entries. The caller is responsible for storing the
     * entries (e.g. on HistoryTab).
     */
    @SuppressWarnings("unchecked")
    List<HistoryEntry> loadHistoryData(List<Long> pids) {
        List<HistoryEntry> allEntries = new ArrayList<>();
        for (Long pid : pids) {
            Path historyFile = CommandLineHelper.getCamelDir().resolve(pid + "-history.json");
            if (!Files.exists(historyFile)) {
                continue;
            }
            try {
                String content = Files.readString(historyFile);
                if (content == null || content.isBlank()) {
                    continue;
                }
                JsonObject json = (JsonObject) Jsoner.deserialize(content);
                Object tracesArray = json.get("traces");
                if (tracesArray instanceof List<?> traceList) {
                    for (Object traceObj : traceList) {
                        if (traceObj instanceof JsonObject traceJson) {
                            HistoryEntry entry = StatusParser.parseHistoryEntry(traceJson, Long.toString(pid));
                            if (entry != null) {
                                allEntries.add(entry);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return allEntries;
    }

    // ---- Helpers ----

    private List<Long> findPids(String name) {
        return TuiHelper.findPids(name, statusFileResolver);
    }

    private JsonObject loadStatus(long pid) {
        return TuiHelper.loadStatus(pid, statusFileResolver);
    }

    private JsonObject loadErrorFile(long pid) {
        return TuiHelper.loadStatus(pid, errorFileResolver);
    }

    record VanishingInfo(IntegrationInfo info, long startTime) {
    }

    record VanishingInfraInfo(InfraInfo info, long startTime) {
    }
}
