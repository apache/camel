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
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.tamboui.image.Image;
import dev.tamboui.image.ImageData;
import dev.tamboui.image.ImageScaling;
import dev.tamboui.image.capability.TerminalImageCapabilities;
import dev.tamboui.image.protocol.ImageProtocol;
import dev.tamboui.layout.Alignment;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.barchart.Bar;
import dev.tamboui.widgets.barchart.BarChart;
import dev.tamboui.widgets.barchart.BarGroup;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.gauge.Gauge;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import dev.tamboui.widgets.tabs.Tabs;
import dev.tamboui.widgets.tabs.TabsState;
import org.apache.camel.diagram.RouteDiagramAsciiRenderer;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramRenderer;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import sun.misc.Signal;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

@Command(name = "monitor",
         description = "Live dashboard for monitoring Camel integrations",
         sortOptions = false)
public class CamelMonitor extends CamelCommand {

    private static final long VANISH_DURATION_MS = 6000;
    private static final long DEFAULT_REFRESH_MS = 100;
    private static final int MAX_SPARKLINE_POINTS = 60;
    private static final int MAX_LOG_LINES = 200;
    private static final int MAX_TRACES = 200;
    private static final int NUM_TABS = 7;

    // Tab indices
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_ROUTES = 1;
    private static final int TAB_LOG = 2;
    private static final int TAB_ENDPOINTS = 3;
    private static final int TAB_HEALTH = 4;
    private static final int TAB_HISTORY = 5;
    private static final int TAB_TRACE = 6;

    // Overview sort columns
    private static final String[] OVERVIEW_SORT_COLUMNS = { "pid", "name", "status", "total", "fail" };

    // Route sort columns
    private static final String[] ROUTE_SORT_COLUMNS = { "total", "failed", "name", "status" };

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--refresh" },
                        description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
                        defaultValue = "100")
    long refreshInterval = DEFAULT_REFRESH_MS;

    // State
    private final AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(Collections.emptyList());
    private final Map<String, VanishingInfo> vanishing = new ConcurrentHashMap<>();
    private final TableState overviewTableState = new TableState();
    private final TableState routeTableState = new TableState();
    private final TableState healthTableState = new TableState();
    private final TableState endpointTableState = new TableState();
    private final TableState processorTableState = new TableState();
    private final TableState routeHeaderTableState = new TableState();
    private final TabsState tabsState = new TabsState(TAB_OVERVIEW);

    // Sparkline: throughput history per PID (one point per second)
    private final Map<String, LinkedList<Long>> throughputHistory = new ConcurrentHashMap<>();
    // Sliding window of [timestamp, exchangesTotal] samples for smoothing
    private final Map<String, LinkedList<long[]>> throughputSamples = new ConcurrentHashMap<>();
    // Track last time a sparkline point was recorded
    private final Map<String, Long> previousExchangesTime = new ConcurrentHashMap<>();

    // Overview sort state
    private String overviewSort = "name";
    private int overviewSortIndex = 1;

    // Route sort state
    private String routeSort = "name";
    private int routeSortIndex = 2;

    // Health filter state
    private boolean showOnlyDown;

    // Log state
    private List<String> logLines = new ArrayList<>();
    private volatile List<LogEntry> filteredLogEntries = new ArrayList<>();
    private final TableState logTableState = new TableState();
    private boolean logFollowMode = true;
    private boolean showLogTrace = true;
    private boolean showLogDebug = true;
    private boolean showLogInfo = true;
    private boolean showLogWarn = true;
    private boolean showLogError = true;

    // Trace state
    private final AtomicReference<List<TraceEntry>> traces = new AtomicReference<>(Collections.emptyList());
    private final TableState traceTableState = new TableState();
    private final TableState traceStepTableState = new TableState();
    private final Map<String, Long> traceFilePositions = new ConcurrentHashMap<>();
    private static final String[] TRACE_SORT_COLUMNS = { "time", "route", "elapsed", "exchange" };
    private String traceSort = "time";
    private int traceSortIndex;
    private boolean traceDetailView;
    private volatile List<String> traceSortedExchangeIds = Collections.emptyList();
    private String traceSelectedExchangeId;
    private boolean showTraceProperties;
    private boolean showTraceVariables;
    private boolean showTraceHeaders = true;
    private boolean showTraceBody = true;
    private boolean traceWordWrap;
    private int traceDetailScroll;

    // History state
    private volatile List<HistoryEntry> historyEntries = Collections.emptyList();
    private final TableState historyTableState = new TableState();
    private boolean showHistoryProperties;
    private boolean showHistoryVariables;
    private boolean showHistoryHeaders = true;
    private boolean showHistoryBody = true;
    private boolean historyWordWrap;
    private int historyDetailScroll;

    // Selected integration for detail views
    private String selectedPid;

    // Diagram state
    private boolean showDiagram;
    private boolean diagramTextMode;
    private boolean diagramMetrics = true;
    private List<RouteDiagramAsciiRenderer.CounterPos> diagramCounterPositions = Collections.emptyList();
    private List<String> diagramLines = Collections.emptyList();
    private int diagramScroll;
    private int diagramScrollX;
    private final ScrollbarState diagramVScrollState = new ScrollbarState();
    private final ScrollbarState diagramHScrollState = new ScrollbarState();
    private final ScrollbarState traceDetailScrollState = new ScrollbarState();
    private final ScrollbarState historyDetailScrollState = new ScrollbarState();
    private String diagramRouteId;
    private ImageData diagramImageData;
    private ImageData diagramFullImageData;
    private ImageProtocol diagramProtocol;
    private int diagramCropX = -1;
    private int diagramCropY = -1;
    private int diagramCropW = -1;
    private int diagramCropH = -1;

    private volatile long lastRefresh;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicBoolean diagramLoading = new AtomicBoolean(false);
    private TuiRunner runner;

    private ClassLoader classLoader;

    public CamelMonitor(CamelJBangMain main, ClassLoader classLoader) {
        super(main);
        this.classLoader = classLoader;
    }

    @Override
    public Integer doCall() throws Exception {
        System.setProperty("java.awt.headless", "true");

        // to make ServiceLoader work with tamboui for downloaded JARs
        Thread.currentThread().setContextClassLoader(classLoader);
        TuiHelper.preloadClasses(classLoader);

        // Initial data load (synchronous before TUI starts)
        refreshDataSync();

        try (var tui = TuiRunner.create()) {
            this.runner = tui;
            // Intercept Ctrl+C: quit the TUI cleanly instead of letting
            // the JVM tear down the classloader while we're still running
            Signal.handle(new Signal("INT"), sig -> tui.quit());
            tui.run(
                    this::handleEvent,
                    this::render);
        } finally {
            this.runner = null;
        }
        return 0;
    }

    // ---- Event Handling ----

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent ke) {
            // Escape: navigate back
            if (ke.isCancel()) {
                if (showDiagram) {
                    showDiagram = false;
                    diagramImageData = null;
                    diagramFullImageData = null;
                    return true;
                }
                if (traceDetailView) {
                    traceDetailView = false;
                    traceSelectedExchangeId = null;
                    traceDetailScroll = 0;
                    return true;
                }
                if (tabsState.selected() != TAB_OVERVIEW) {
                    tabsState.select(TAB_OVERVIEW);
                    selectedPid = null;
                    return true;
                }
                if (selectedPid != null) {
                    selectedPid = null;
                    return true;
                }
                return true;
            }
            // Quit: q or Ctrl+c
            if (ke.isCharIgnoreCase('q') || ke.isCtrlC()) {
                runner.quit();
                return true;
            }
            // Tab switching with number keys
            if (ke.isChar('1')) {
                return handleTabKey(TAB_OVERVIEW);
            }
            if (ke.isChar('2')) {
                return handleTabKey(TAB_ROUTES);
            }
            if (ke.isChar('3')) {
                return handleTabKey(TAB_LOG);
            }
            if (ke.isChar('4')) {
                return handleTabKey(TAB_ENDPOINTS);
            }
            if (ke.isChar('5')) {
                return handleTabKey(TAB_HEALTH);
            }
            if (ke.isChar('6')) {
                return handleTabKey(TAB_HISTORY);
            }
            if (ke.isChar('7')) {
                return handleTabKey(TAB_TRACE);
            }

            // Tab cycling
            if (ke.isFocusNext()) {
                int next = (tabsState.selected() + 1) % NUM_TABS;
                if (next != TAB_OVERVIEW) {
                    selectCurrentIntegration();
                }
                tabsState.select(next);
                return true;
            }

            // Tab-specific keys
            int tab = tabsState.selected();

            // Navigation (all tabs)
            if (ke.isUp()) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScroll = Math.max(0, diagramScroll - 1);
                } else {
                    navigateUp();
                }
                return true;
            }
            if (ke.isDown()) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScroll++;
                } else {
                    navigateDown();
                }
                return true;
            }
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScroll = Math.max(0, diagramScroll - 20);
                } else if (tab == TAB_LOG) {
                    logFollowMode = false;
                    for (int i = 0; i < 20; i++) {
                        logTableState.selectPrevious();
                    }
                } else if (tab == TAB_HISTORY) {
                    historyDetailScroll = Math.max(0, historyDetailScroll - 5);
                } else if (tab == TAB_TRACE && traceDetailView) {
                    traceDetailScroll = Math.max(0, traceDetailScroll - 5);
                }
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScroll += 20;
                } else if (tab == TAB_LOG) {
                    for (int i = 0; i < 20; i++) {
                        logTableState.selectNext(filteredLogEntries.size());
                    }
                } else if (tab == TAB_HISTORY) {
                    historyDetailScroll += 5;
                } else if (tab == TAB_TRACE && traceDetailView) {
                    traceDetailScroll += 5;
                }
                return true;
            }
            if (ke.isLeft()) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScrollX = Math.max(0, diagramScrollX - 1);
                    return true;
                }
            }
            if (ke.isRight()) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScrollX++;
                    return true;
                }
            }
            if (ke.isHome()) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScroll = 0;
                    diagramScrollX = 0;
                    return true;
                }
            }
            if (ke.isEnd()) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScroll = Integer.MAX_VALUE;
                    return true;
                }
            }

            // Enter to drill into selected integration
            if (ke.isConfirm() && tab == TAB_OVERVIEW) {
                selectCurrentIntegration();
                if (selectedPid != null) {
                    tabsState.select(TAB_ROUTES);
                }
                return true;
            }

            // Overview tab: sort
            if (tab == TAB_OVERVIEW && ke.isCharIgnoreCase('s')) {
                overviewSortIndex = (overviewSortIndex + 1) % OVERVIEW_SORT_COLUMNS.length;
                overviewSort = OVERVIEW_SORT_COLUMNS[overviewSortIndex];
                return true;
            }

            // Routes tab: sort and diagram
            if (tab == TAB_ROUTES && ke.isCharIgnoreCase('s')) {
                routeSortIndex = (routeSortIndex + 1) % ROUTE_SORT_COLUMNS.length;
                routeSort = ROUTE_SORT_COLUMNS[routeSortIndex];
                return true;
            }
            if (tab == TAB_ROUTES && ke.isChar('d')) {
                if (showDiagram) {
                    showDiagram = false;
                    diagramImageData = null;
                    diagramFullImageData = null;
                } else {
                    diagramTextMode = false;
                    loadDiagramForSelectedRoute();
                }
                return true;
            }
            if (tab == TAB_ROUTES && ke.isChar('D')) {
                if (showDiagram) {
                    showDiagram = false;
                    diagramImageData = null;
                    diagramFullImageData = null;
                } else {
                    diagramTextMode = true;
                    loadDiagramForSelectedRoute();
                }
                return true;
            }

            if (tab == TAB_ROUTES && showDiagram && ke.isCharIgnoreCase('m')) {
                diagramMetrics = !diagramMetrics;
                diagramLoading.set(false);
                loadDiagramForSelectedRoute();
                return true;
            }
            if (tab == TAB_ROUTES && showDiagram && !diagramTextMode && ke.isKey(KeyCode.F5)) {
                diagramLoading.set(false);
                loadDiagramForSelectedRoute();
                return true;
            }

            // Health tab: DOWN filter
            if (tab == TAB_HEALTH && ke.isCharIgnoreCase('d')) {
                showOnlyDown = !showOnlyDown;
                return true;
            }

            // Log tab: level filters and follow mode
            if (tab == TAB_LOG) {
                if (ke.isCharIgnoreCase('t')) {
                    showLogTrace = !showLogTrace;
                    filteredLogEntries = applyLogFilters(logLines);
                    return true;
                }
                if (ke.isCharIgnoreCase('d')) {
                    showLogDebug = !showLogDebug;
                    filteredLogEntries = applyLogFilters(logLines);
                    return true;
                }
                if (ke.isCharIgnoreCase('i')) {
                    showLogInfo = !showLogInfo;
                    filteredLogEntries = applyLogFilters(logLines);
                    return true;
                }
                if (ke.isCharIgnoreCase('w')) {
                    showLogWarn = !showLogWarn;
                    filteredLogEntries = applyLogFilters(logLines);
                    return true;
                }
                if (ke.isCharIgnoreCase('e')) {
                    showLogError = !showLogError;
                    filteredLogEntries = applyLogFilters(logLines);
                    return true;
                }
                if (ke.isCharIgnoreCase('f')) {
                    logFollowMode = !logFollowMode;
                    return true;
                }
                if (ke.isHome()) {
                    logFollowMode = false;
                    logTableState.select(0);
                    return true;
                }
                if (ke.isEnd()) {
                    logFollowMode = true;
                    return true;
                }
            }

            // Trace tab
            if (tab == TAB_TRACE) {
                if (traceDetailView) {
                    if (ke.isCancel()) {
                        traceDetailView = false;
                        traceSelectedExchangeId = null;
                        traceDetailScroll = 0;
                        return true;
                    }
                    if (ke.isCharIgnoreCase('p')) {
                        showTraceProperties = !showTraceProperties;
                        return true;
                    }
                    if (ke.isCharIgnoreCase('v')) {
                        showTraceVariables = !showTraceVariables;
                        return true;
                    }
                    if (ke.isCharIgnoreCase('h')) {
                        showTraceHeaders = !showTraceHeaders;
                        return true;
                    }
                    if (ke.isCharIgnoreCase('b')) {
                        showTraceBody = !showTraceBody;
                        return true;
                    }
                    if (ke.isCharIgnoreCase('w')) {
                        traceWordWrap = !traceWordWrap;
                        traceDetailScroll = 0;
                        return true;
                    }
                } else {
                    if (ke.isCharIgnoreCase('s')) {
                        traceSortIndex = (traceSortIndex + 1) % TRACE_SORT_COLUMNS.length;
                        traceSort = TRACE_SORT_COLUMNS[traceSortIndex];
                        return true;
                    }
                    if (ke.isConfirm()) {
                        Integer sel = traceTableState.selected();
                        if (sel != null && sel >= 0 && sel < traceSortedExchangeIds.size()) {
                            traceSelectedExchangeId = traceSortedExchangeIds.get(sel);
                            traceDetailView = true;
                            traceStepTableState.select(0);
                            traceDetailScroll = 0;
                        }
                        return true;
                    }
                    if (ke.isKey(KeyCode.F5)) {
                        if (selectedPid != null) {
                            traceFilePositions.clear();
                            traces.set(Collections.emptyList());
                            refreshTraceData(List.of(Long.parseLong(selectedPid)));
                        }
                        return true;
                    }
                }
            }

            // History tab: properties/headers/body toggle and refresh
            if (tab == TAB_HISTORY) {
                if (ke.isCharIgnoreCase('p')) {
                    showHistoryProperties = !showHistoryProperties;
                    return true;
                }
                if (ke.isCharIgnoreCase('v')) {
                    showHistoryVariables = !showHistoryVariables;
                    return true;
                }
                if (ke.isCharIgnoreCase('h')) {
                    showHistoryHeaders = !showHistoryHeaders;
                    return true;
                }
                if (ke.isCharIgnoreCase('b')) {
                    showHistoryBody = !showHistoryBody;
                    return true;
                }
                if (ke.isCharIgnoreCase('w')) {
                    historyWordWrap = !historyWordWrap;
                    historyDetailScroll = 0;
                    return true;
                }
                if (ke.isKey(KeyCode.F5)) {
                    if (selectedPid != null) {
                        refreshHistoryData(List.of(Long.parseLong(selectedPid)));
                    }
                    return true;
                }
            }
        }
        if (event instanceof TickEvent) {
            long now = System.currentTimeMillis();
            long interval = showDiagram ? Math.max(refreshInterval, 1000) : refreshInterval;
            if (now - lastRefresh >= interval) {
                refreshData();
                if (showDiagram && diagramTextMode && diagramMetrics) {
                    loadDiagramForSelectedRoute();
                }
                return true;
            }
            // Skip re-render when showing image diagram to prevent flicker
            return diagramFullImageData == null;
        }
        return false;
    }

    private boolean handleTabKey(int tab) {
        if (tab != TAB_OVERVIEW) {
            selectCurrentIntegration();
        } else {
            selectedPid = null;
        }
        if (tab == TAB_HISTORY && selectedPid != null) {
            refreshHistoryData(List.of(Long.parseLong(selectedPid)));
            if (!historyEntries.isEmpty()) {
                historyTableState.select(0);
            }
        }
        if (tab == TAB_TRACE && selectedPid != null) {
            traceFilePositions.clear();
            traces.set(Collections.emptyList());
            refreshTraceData(List.of(Long.parseLong(selectedPid)));
            traceDetailView = false;
            traceSelectedExchangeId = null;
            List<String> ids = getTraceExchangeIds();
            if (!ids.isEmpty()) {
                traceTableState.select(0);
            }
        }
        tabsState.select(tab);
        return true;
    }

    private void selectCurrentIntegration() {
        if (selectedPid != null) {
            return;
        }
        List<IntegrationInfo> infos = data.get().stream().filter(i -> !i.vanishing).toList();
        Integer sel = overviewTableState.selected();
        if (sel != null && sel >= 0 && sel < infos.size()) {
            selectedPid = infos.get(sel).pid;
        } else if (infos.size() == 1) {
            selectedPid = infos.get(0).pid;
        }
        if (selectedPid != null) {
            List<Long> pids = List.of(Long.parseLong(selectedPid));
            refreshHistoryData(pids);
            traceFilePositions.clear();
            traces.set(Collections.emptyList());
            refreshTraceData(pids);
        }
    }

    private void navigateUp() {
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> overviewTableState.selectPrevious();
            case TAB_ROUTES -> routeTableState.selectPrevious();
            case TAB_HEALTH -> healthTableState.selectPrevious();
            case TAB_ENDPOINTS -> endpointTableState.selectPrevious();
            case TAB_LOG -> {
                logFollowMode = false;
                logTableState.selectPrevious();
            }
            case TAB_TRACE -> {
                if (traceDetailView) {
                    traceStepTableState.selectPrevious();
                    traceDetailScroll = 0;
                } else {
                    traceTableState.selectPrevious();
                }
            }
            case TAB_HISTORY -> {
                historyTableState.selectPrevious();
                historyDetailScroll = 0;
            }
        }
    }

    private void navigateDown() {
        List<IntegrationInfo> infos = data.get().stream().filter(i -> !i.vanishing).toList();
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> overviewTableState.selectNext(infos.size());
            case TAB_ROUTES -> {
                IntegrationInfo info = findSelectedIntegration();
                routeTableState.selectNext(info != null ? info.routes.size() : 0);
            }
            case TAB_HEALTH -> {
                IntegrationInfo info = findSelectedIntegration();
                healthTableState.selectNext(info != null ? getFilteredHealthChecks(info).size() : 0);
            }
            case TAB_ENDPOINTS -> {
                IntegrationInfo info = findSelectedIntegration();
                endpointTableState.selectNext(info != null ? info.endpoints.size() : 0);
            }
            case TAB_LOG -> logTableState.selectNext(filteredLogEntries.size());
            case TAB_TRACE -> {
                if (traceDetailView) {
                    List<TraceEntry> steps = getTraceSteps(traceSelectedExchangeId);
                    traceStepTableState.selectNext(steps.size());
                    traceDetailScroll = 0;
                } else {
                    List<String> exchangeIds = getTraceExchangeIds();
                    traceTableState.selectNext(exchangeIds.size());
                }
            }
            case TAB_HISTORY -> {
                historyTableState.selectNext(historyEntries.size());
                historyDetailScroll = 0;
            }
        }
    }

    // ---- Rendering ----

    private void render(Frame frame) {
        Rect area = frame.area();

        // Layout: header (3 rows) + tabs (2 rows) + content (fill) + footer (1 row)
        List<Rect> mainChunks = Layout.vertical()
                .constraints(
                        Constraint.length(3),
                        Constraint.length(2),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, mainChunks.get(0));
        renderTabs(frame, mainChunks.get(1));
        renderContent(frame, mainChunks.get(2));
        renderFooter(frame, mainChunks.get(3));
    }

    private void renderHeader(Frame frame, Rect area) {
        List<IntegrationInfo> infos = data.get();
        String camelVersion = VersionHelper.extractCamelVersion();
        long activeCount = infos.stream().filter(i -> !i.vanishing).count();

        List<Span> titleSpans = new ArrayList<>();
        titleSpans.add(Span.styled(" Camel Monitor", Style.EMPTY.fg(Color.rgb(0xF6, 0x91, 0x23)).bold()));
        titleSpans.add(Span.raw("  "));
        titleSpans.add(Span.styled(camelVersion != null ? "v" + camelVersion : "", Style.EMPTY.fg(Color.GREEN)));
        titleSpans.add(Span.raw("  "));
        titleSpans.add(Span.styled(activeCount + " integration(s)", Style.EMPTY.fg(Color.CYAN)));
        if (selectedPid != null) {
            titleSpans.add(Span.raw("  "));
            titleSpans.add(Span.styled("selected: " + selectedName(), Style.EMPTY.fg(Color.YELLOW)));
        }
        Line titleLine = Line.from(titleSpans);

        Block headerBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Apache Camel ")
                .build();

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).block(headerBlock).build(),
                area);
    }

    private void renderTabs(Frame frame, Rect area) {
        // Compute notification counts (0 if no integration selected)
        List<IntegrationInfo> infos = data.get();
        long activeCount = infos.stream().filter(i -> !i.vanishing).count();
        IntegrationInfo sel = findSelectedIntegration();
        boolean hasSelection = selectedPid != null && sel != null;
        int routeCount = hasSelection ? sel.routes.size() : 0;
        int logCount = hasSelection ? filteredLogEntries.size() : 0;
        int endpointCount = hasSelection ? sel.endpoints.size() : 0;
        int healthCount = hasSelection ? sel.healthChecks.size() : 0;
        int historyCount = hasSelection ? historyEntries.size() : 0;
        boolean hasTraces = hasSelection && !traces.get().isEmpty();

        Tabs tabs = Tabs.builder()
                .titles(
                        badge(" 1 Overview ", activeCount),
                        badge(" 2 Routes ", routeCount),
                        badge(" 3 Log ", logCount),
                        badge(" 4 Endpoints ", endpointCount),
                        badge(" 5 Health ", healthCount),
                        badge(" 6 History ", historyCount),
                        hasTraces
                                ? Line.from(Span.raw(" 7 Trace "), Span.styled("(*)", Style.EMPTY.fg(Color.YELLOW).bold()),
                                        Span.raw(" "))
                                : Line.from(" 7 Trace "))
                .highlightStyle(Style.EMPTY.fg(Color.rgb(0xF6, 0x91, 0x23)).bold())
                .divider(Span.styled(" | ", Style.EMPTY.dim()))
                .build();

        frame.renderStatefulWidget(tabs, area, tabsState);
    }

    private void renderContent(Frame frame, Rect area) {
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> renderOverview(frame, area);
            case TAB_ROUTES -> renderRoutes(frame, area);
            case TAB_HEALTH -> renderHealth(frame, area);
            case TAB_ENDPOINTS -> renderEndpoints(frame, area);
            case TAB_LOG -> renderLog(frame, area);
            case TAB_TRACE -> renderTrace(frame, area);
            case TAB_HISTORY -> renderHistory(frame, area);
        }
    }

    // ---- Tab 1: Overview ----

    private void renderOverview(Frame frame, Rect area) {
        List<IntegrationInfo> infos = new ArrayList<>(data.get());
        infos.sort(this::sortOverview);

        // Split: table (fill) + sparkline (height 8) if we have data
        boolean hasSparkline = !throughputHistory.isEmpty();
        List<Rect> chunks;
        if (hasSparkline) {
            chunks = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(13))
                    .split(area);
        } else {
            chunks = List.of(area);
        }

        // Integration table
        List<Row> rows = new ArrayList<>();
        for (IntegrationInfo info : infos) {
            if (info.vanishing) {
                long elapsed = System.currentTimeMillis() - info.vanishStart;
                float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                int gray = (int) (100 * fade);
                Style dimStyle = Style.EMPTY.fg(Color.indexed(232 + Math.min(gray / 4, 23)));

                rows.add(Row.from(
                        Cell.from(Span.styled(info.pid, dimStyle)),
                        Cell.from(Span.styled(info.name != null ? info.name : "", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("\u2716 Stopped", Style.EMPTY.fg(Color.RED).dim())),
                        Cell.from(Span.styled(info.ago != null ? info.ago : "", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle))));
            } else {
                Style statusStyle = switch (extractState(info.state)) {
                    case "Started", "Running" -> Style.EMPTY.fg(Color.GREEN);
                    case "Stopped" -> Style.EMPTY.fg(Color.RED);
                    default -> Style.EMPTY.fg(Color.YELLOW);
                };

                Style failStyle = info.failed > 0 ? Style.EMPTY.fg(Color.RED).bold() : Style.EMPTY;

                String sinceLastDisplay = formatSinceLast(info);

                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(Span.styled(info.name != null ? info.name : "", Style.EMPTY.fg(Color.CYAN))),
                        Cell.from(info.camelVersion != null ? info.camelVersion : ""),
                        Cell.from(info.ready != null ? info.ready : ""),
                        Cell.from(Span.styled(extractState(info.state), statusStyle)),
                        Cell.from(info.ago != null ? info.ago : ""),
                        Cell.from(info.routeStarted + "/" + info.routeTotal),
                        rightCell(info.throughput != null ? info.throughput : "", 8),
                        rightCell(String.valueOf(info.exchangesTotal), 8),
                        rightCell(String.valueOf(info.failed), 6, failStyle),
                        rightCell(String.valueOf(info.inflight), 8),
                        Cell.from(sinceLastDisplay),
                        Cell.from(formatMemory(info.heapMemUsed, info.heapMemMax))));
            }
        }

        Row header = Row.from(
                Cell.from(Span.styled(overviewSortLabel("PID", "pid"), overviewSortStyle("pid"))),
                Cell.from(Span.styled(overviewSortLabel("NAME", "name"), overviewSortStyle("name"))),
                Cell.from(Span.styled("VERSION", Style.EMPTY.bold())),
                Cell.from(Span.styled("READY", Style.EMPTY.bold())),
                Cell.from(Span.styled(overviewSortLabel("STATUS", "status"), overviewSortStyle("status"))),
                Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                rightCell("MSG/S", 8, Style.EMPTY.bold()),
                rightCell(overviewSortLabel("TOTAL", "total"), 8, overviewSortStyle("total")),
                rightCell(overviewSortLabel("FAIL", "fail"), 6, overviewSortStyle("fail")),
                rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold())),
                Cell.from(Span.styled("HEAP", Style.EMPTY.bold())));

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(16),
                        Constraint.length(5),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(7),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(12),
                        Constraint.length(14))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Integrations ").build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), overviewTableState);

        // Sparkline for throughput
        if (hasSparkline && chunks.size() > 1) {
            // Merge all throughput histories for overview chart
            LinkedList<Long> merged = new LinkedList<>();
            for (int i = 0; i < MAX_SPARKLINE_POINTS; i++) {
                long sum = 0;
                for (LinkedList<Long> hist : throughputHistory.values()) {
                    if (i < hist.size()) {
                        sum += hist.get(hist.size() - 1 - i);
                    }
                }
                merged.addFirst(sum);
            }

            // Compute stats for title display
            long maxTp = merged.stream().mapToLong(Long::longValue).max().orElse(0);
            long curTp = merged.isEmpty() ? 0 : merged.get(merged.size() - 1);
            String chartTitle = String.format(" Throughput: %d msg/s (peak: %d) ", curTp, maxTp);

            List<BarGroup> groups = new ArrayList<>();
            for (Long value : merged) {
                groups.add(BarGroup.of(Bar.of(value)));
            }

            BarChart barChart = BarChart.builder()
                    .data(groups)
                    .max(maxTp > 0 ? maxTp + 2 : 2)
                    .barWidth(1)
                    .barGap(0)
                    .barStyle(Style.EMPTY.fg(Color.GREEN))
                    .block(Block.builder().borderType(BorderType.ROUNDED).title(chartTitle).build())
                    .build();

            frame.renderWidget(barChart, chunks.get(1));
        }
    }

    // ---- Tab 2: Routes ----

    private void renderRoutes(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Fullscreen diagram mode
        if (showDiagram && (diagramTextMode ? !diagramLines.isEmpty() : diagramFullImageData != null)) {
            // Split: route info header (4 rows) + diagram (fill)
            List<Rect> fullChunks = Layout.vertical()
                    .constraints(Constraint.length(4), Constraint.fill())
                    .split(area);
            renderRouteHeader(frame, fullChunks.get(0), info);
            renderDiagram(frame, fullChunks.get(1));
            return;
        }

        // Sort routes
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);

        // Split: routes table (top half) + processors table (bottom half)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(45), Constraint.percentage(55))
                .split(area);

        // Routes table
        List<Row> routeRows = new ArrayList<>();
        for (RouteInfo route : sortedRoutes) {
            Style stateStyle = "Started".equals(route.state)
                    ? Style.EMPTY.fg(Color.GREEN)
                    : Style.EMPTY.fg(Color.RED);

            Style failStyle = route.failed > 0
                    ? Style.EMPTY.fg(Color.RED).bold()
                    : Style.EMPTY;

            String sinceLastRoute = formatSinceLastRoute(route);

            routeRows.add(Row.from(
                    Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(route.from != null ? route.from : ""),
                    Cell.from(Span.styled(route.state != null ? route.state : "", stateStyle)),
                    Cell.from(route.uptime != null ? route.uptime : ""),
                    Cell.from(route.coverage != null ? route.coverage : ""),
                    rightCell(route.throughput != null ? route.throughput : "", 8),
                    rightCell(String.valueOf(route.total), 8),
                    rightCell(String.valueOf(route.failed), 6, failStyle),
                    rightCell(String.valueOf(route.inflight), 8),
                    rightCell(route.total > 0
                            ? route.minTime + "/" + route.maxTime + "/" + route.meanTime
                            : "", 14),
                    Cell.from(sinceLastRoute)));
        }

        Table routeTable = Table.builder()
                .rows(routeRows)
                .header(Row.from(
                        Cell.from(Span.styled(routeSortLabel("ROUTE", "name"), routeSortStyle("name"))),
                        Cell.from(Span.styled("FROM", Style.EMPTY.bold())),
                        Cell.from(Span.styled(routeSortLabel("STATUS", "status"), routeSortStyle("status"))),
                        Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("COVER", Style.EMPTY.bold())),
                        rightCell("MSG/S", 8, Style.EMPTY.bold()),
                        rightCell(routeSortLabel("TOTAL", "total"), 8, routeSortStyle("total")),
                        rightCell(routeSortLabel("FAIL", "failed"), 6, routeSortStyle("failed")),
                        rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                        rightCell("MIN/MAX/MEAN", 14, Style.EMPTY.bold()),
                        Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.fill(),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(14),
                        Constraint.length(12))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Routes sort:" + routeSort + " ").build())
                .build();

        frame.renderStatefulWidget(routeTable, chunks.get(0), routeTableState);

        // Bottom panel: diagram or processors
        if (showDiagram && (diagramImageData != null || !diagramLines.isEmpty())) {
            renderDiagram(frame, chunks.get(1));
        } else {
            Integer selectedRoute = routeTableState.selected();
            if (selectedRoute != null && selectedRoute >= 0 && selectedRoute < sortedRoutes.size()) {
                RouteInfo route = sortedRoutes.get(selectedRoute);
                renderProcessors(frame, chunks.get(1), route);
            } else if (!sortedRoutes.isEmpty()) {
                renderProcessors(frame, chunks.get(1), sortedRoutes.get(0));
            } else {
                frame.renderWidget(
                        Paragraph.builder()
                                .text(Text.from(Line.from(Span.styled("No routes", Style.EMPTY.dim()))))
                                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Processors ").build())
                                .build(),
                        chunks.get(1));
            }
        }
    }

    private int sortOverview(IntegrationInfo a, IntegrationInfo b) {
        if (a.vanishing != b.vanishing) {
            return a.vanishing ? 1 : -1;
        }
        return switch (overviewSort) {
            case "pid" -> {
                String pa = a.pid != null ? a.pid : "";
                String pb = b.pid != null ? b.pid : "";
                yield pa.compareTo(pb);
            }
            case "name" -> {
                String na = a.name != null ? a.name : "";
                String nb = b.name != null ? b.name : "";
                yield na.compareToIgnoreCase(nb);
            }
            case "status" -> Integer.compare(a.state, b.state);
            case "total" -> Long.compare(b.exchangesTotal, a.exchangesTotal);
            case "fail" -> Long.compare(b.failed, a.failed);
            default -> 0;
        };
    }

    private String overviewSortLabel(String label, String column) {
        return sortLabel(label, column, overviewSort);
    }

    private Style overviewSortStyle(String column) {
        return sortStyle(column, overviewSort);
    }

    private int sortRoute(RouteInfo a, RouteInfo b) {
        return switch (routeSort) {
            case "total" -> Long.compare(b.total, a.total);
            case "failed" -> Long.compare(b.failed, a.failed);
            case "name" -> {
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
            case "status" -> {
                String sa = a.state != null ? a.state : "";
                String sb2 = b.state != null ? b.state : "";
                yield sa.compareToIgnoreCase(sb2);
            }
            default -> 0;
        };
    }

    private String traceSortLabel(String label, String column) {
        return sortLabel(label, column, traceSort);
    }

    private Style traceSortStyle(String column) {
        return sortStyle(column, traceSort);
    }

    private String routeSortLabel(String label, String column) {
        return sortLabel(label, column, routeSort);
    }

    private Style routeSortStyle(String column) {
        return sortStyle(column, routeSort);
    }

    private static String sortLabel(String label, String column, String currentSort) {
        return currentSort.equals(column) ? label + "▼" : label;
    }

    private static Style sortStyle(String column, String currentSort) {
        return currentSort.equals(column)
                ? Style.EMPTY.fg(Color.YELLOW).bold()
                : Style.EMPTY.bold();
    }

    private void renderProcessors(Frame frame, Rect area, RouteInfo route) {
        List<Row> rows = new ArrayList<>();
        for (ProcessorInfo proc : route.processors) {
            String indent = "  ".repeat(proc.level);
            Style nameStyle = proc.failed > 0 ? Style.EMPTY.fg(Color.RED) : Style.EMPTY.fg(Color.CYAN);

            rows.add(Row.from(
                    Cell.from(Span.styled(indent + (proc.id != null ? proc.id : ""), nameStyle)),
                    Cell.from(proc.processor != null ? proc.processor : ""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    rightCell(String.valueOf(proc.total), 8),
                    rightCell(String.valueOf(proc.failed), 6,
                            proc.failed > 0 ? Style.EMPTY.fg(Color.RED) : Style.EMPTY),
                    Cell.from(""),
                    rightCell(proc.total > 0
                            ? proc.minTime + "/" + proc.maxTime + "/" + proc.meanTime
                            : "", 14),
                    Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                        Cell.from(Span.styled("TYPE", Style.EMPTY.bold())),
                        Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                        rightCell("TOTAL", 8, Style.EMPTY.bold()),
                        rightCell("FAIL", 6, Style.EMPTY.bold()),
                        Cell.from(""),
                        rightCell("MIN/MAX/MEAN", 14, Style.EMPTY.bold()),
                        Cell.from("")))
                .widths(
                        Constraint.length(12),
                        Constraint.fill(),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(14),
                        Constraint.length(12))
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Processors [" + route.routeId + "] ").build())
                .build();

        frame.renderStatefulWidget(table, area, processorTableState);
    }

    private void renderRouteHeader(Frame frame, Rect area, IntegrationInfo info) {
        RouteInfo route = null;
        if (diagramRouteId != null) {
            for (RouteInfo r : info.routes) {
                if (diagramRouteId.equals(r.routeId)) {
                    route = r;
                    break;
                }
            }
        }

        List<Row> rows = new ArrayList<>();
        if (route != null) {
            Style stateStyle = "Started".equals(route.state)
                    ? Style.EMPTY.fg(Color.GREEN)
                    : Style.EMPTY.fg(Color.RED);
            Style failStyle = route.failed > 0
                    ? Style.EMPTY.fg(Color.RED).bold()
                    : Style.EMPTY;
            rows.add(Row.from(
                    Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(route.from != null ? route.from : ""),
                    Cell.from(Span.styled(route.state != null ? route.state : "", stateStyle)),
                    Cell.from(route.uptime != null ? route.uptime : ""),
                    rightCell(route.throughput != null ? route.throughput : "", 8),
                    rightCell(String.valueOf(route.total), 8),
                    rightCell(String.valueOf(route.failed), 6, failStyle),
                    rightCell(String.valueOf(route.inflight), 8)));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("FROM", Style.EMPTY.bold())),
                        Cell.from(Span.styled("STATUS", Style.EMPTY.bold())),
                        Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                        rightCell("MSG/S", 8, Style.EMPTY.bold()),
                        rightCell("TOTAL", 8, Style.EMPTY.bold()),
                        rightCell("FAIL", 6, Style.EMPTY.bold()),
                        rightCell("INFLIGHT", 8, Style.EMPTY.bold())))
                .widths(
                        Constraint.length(12),
                        Constraint.fill(),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Route ").build())
                .build();

        frame.renderStatefulWidget(table, area, routeHeaderTableState);
    }

    private void renderDiagram(Frame frame, Rect area) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(diagramTextMode ? "" : " Diagram [" + diagramRouteId + "] ")
                .build();

        if (diagramFullImageData != null) {
            renderImageDiagram(frame, area, block);
            return;
        }

        // Compute max width for horizontal scrolling
        int maxWidth = 0;
        for (String line : diagramLines) {
            maxWidth = Math.max(maxWidth, CharWidth.of(line));
        }

        Rect inner = block.inner(area);
        // Reserve 1 col for vertical scrollbar, 1 row for horizontal scrollbar
        int visibleLines = Math.max(1, inner.height() - 1);
        int visibleCols = Math.max(1, inner.width() - 1);

        int maxVScroll = Math.max(0, diagramLines.size() - visibleLines);
        int maxHScroll = Math.max(0, maxWidth - visibleCols);
        diagramScroll = Math.min(diagramScroll, maxVScroll);
        diagramScrollX = Math.min(diagramScrollX, maxHScroll);

        // Build visible lines with horizontal offset applied
        List<Line> lines = new ArrayList<>();
        int end = Math.min(diagramScroll + visibleLines, diagramLines.size());
        for (int i = diagramScroll; i < end; i++) {
            String line = diagramLines.get(i);
            if (diagramScrollX > 0) {
                line = diagramScrollX < line.length() ? line.substring(diagramScrollX) : "";
            }
            lines.add(styleDiagramLine(line, i, diagramScrollX));
        }

        // Layout: outer block wraps everything, inner splits content + scrollbars
        frame.renderWidget(block, area);

        // Vertical layout inside the block: [content row (fill), horizontal scrollbar (1 row)]
        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        // Horizontal layout for content row: [text (fill), vertical scrollbar (1 col)]
        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        // Render diagram text
        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .build();
        frame.renderWidget(paragraph, hChunks.get(0));

        // Render vertical scrollbar
        diagramVScrollState.contentLength(diagramLines.size());
        diagramVScrollState.viewportContentLength(visibleLines);
        diagramVScrollState.position(diagramScroll);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), diagramVScrollState);

        // Render horizontal scrollbar
        if (maxWidth > visibleCols) {
            diagramHScrollState.contentLength(maxWidth);
            diagramHScrollState.viewportContentLength(visibleCols);
            diagramHScrollState.position(diagramScrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), diagramHScrollState);
        }
    }

    private void renderImageDiagram(Frame frame, Rect area, Block block) {
        int imgW = diagramFullImageData.width();
        int imgH = diagramFullImageData.height();

        Rect inner = block.inner(area);
        // Convert cell area to pixel viewport using protocol resolution
        int pxPerCol = diagramProtocol.resolution().widthMultiplier();
        int pxPerRow = diagramProtocol.resolution().heightMultiplier();
        // Reserve 1 col for vertical scrollbar, 1 row for horizontal scrollbar
        int viewCols = Math.max(1, inner.width() - 1);
        int viewRows = Math.max(1, inner.height() - 1);
        int viewW = viewCols * pxPerCol;
        int viewH = viewRows * pxPerRow;

        // Scroll units are in cells; convert to pixels for clamping
        int maxScrollY = Math.max(0, (imgH - viewH + pxPerRow - 1) / pxPerRow);
        int maxScrollX = Math.max(0, (imgW - viewW + pxPerCol - 1) / pxPerCol);
        diagramScroll = Math.min(diagramScroll, maxScrollY);
        diagramScrollX = Math.min(diagramScrollX, maxScrollX);

        int cropX = Math.min(diagramScrollX * pxPerCol, imgW);
        int cropY = Math.min(diagramScroll * pxPerRow, imgH);
        int cropW = Math.min(viewW, imgW - cropX);
        int cropH = Math.min(viewH, imgH - cropY);

        if (cropW > 0 && cropH > 0) {
            if (cropX != diagramCropX || cropY != diagramCropY
                    || cropW != diagramCropW || cropH != diagramCropH) {
                diagramImageData = diagramFullImageData.crop(cropX, cropY, cropW, cropH);
                diagramCropX = cropX;
                diagramCropY = cropY;
                diagramCropW = cropW;
                diagramCropH = cropH;
            }
        } else if (diagramImageData != diagramFullImageData) {
            diagramImageData = diagramFullImageData;
        }

        // Render the outer block border
        frame.renderWidget(block, area);

        // Vertical layout inside the block: [image+vscrollbar (fill), hscrollbar (1 row)]
        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        // Horizontal layout: [image (fill), vertical scrollbar (1 col)]
        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        // Render cropped image
        Image img = Image.builder()
                .data(diagramImageData)
                .protocol(diagramProtocol)
                .scaling(ImageScaling.FIT)
                .build();
        frame.renderWidget(img, hChunks.get(0));

        // Render vertical scrollbar
        int totalRows = (imgH + pxPerRow - 1) / pxPerRow;
        diagramVScrollState.contentLength(totalRows);
        diagramVScrollState.viewportContentLength(viewRows);
        diagramVScrollState.position(diagramScroll);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), diagramVScrollState);

        // Render horizontal scrollbar
        if (imgW > viewW) {
            int totalCols = (imgW + pxPerCol - 1) / pxPerCol;
            diagramHScrollState.contentLength(totalCols);
            diagramHScrollState.viewportContentLength(viewCols);
            diagramHScrollState.position(diagramScrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), diagramHScrollState);
        }
    }

    private Line styleDiagramLine(String text, int row, int scrollX) {
        // Build counter color ranges for this row
        List<int[]> counterRanges = new ArrayList<>();
        for (RouteDiagramAsciiRenderer.CounterPos cp : diagramCounterPositions) {
            if (cp.row() == row) {
                int start = cp.col() - scrollX;
                int end = start + cp.length();
                if (end > 0 && start < text.length()) {
                    start = Math.max(0, start);
                    end = Math.min(end, text.length());
                    int colorFlag = cp.type() == RouteDiagramAsciiRenderer.CounterType.OK ? 1 : 2;
                    counterRanges.add(new int[] { start, end, colorFlag });
                }
            }
        }

        List<Span> spans = new ArrayList<>();
        int idx = 0;
        while (idx < text.length()) {
            int open = text.indexOf('[', idx);
            if (open < 0) {
                addStyledSegment(spans, text, idx, text.length(), counterRanges, Color.WHITE);
                break;
            }
            int close = text.indexOf(']', open);
            if (close < 0) {
                addStyledSegment(spans, text, idx, text.length(), counterRanges, Color.WHITE);
                break;
            }
            if (open > idx) {
                addStyledSegment(spans, text, idx, open, counterRanges, Color.GRAY);
            }
            String tag = text.substring(open + 1, close);
            Color tagColor = getDiagramNodeColor(tag);
            spans.add(Span.styled("[" + tag + "]", Style.EMPTY.fg(tagColor).bold()));

            int afterTag = close + 1;
            int nextOpen = text.indexOf('[', afterTag);
            int labelEnd = nextOpen >= 0 ? nextOpen : text.length();
            if (afterTag < labelEnd) {
                addStyledSegment(spans, text, afterTag, labelEnd, counterRanges, Color.WHITE);
            }
            idx = labelEnd;
        }
        return Line.from(spans);
    }

    private void addStyledSegment(
            List<Span> spans, String text, int from, int to, List<int[]> counterRanges, Color defaultColor) {
        int pos = from;
        while (pos < to) {
            int[] cr = findNextCounterRange(counterRanges, pos, to);
            if (cr != null) {
                if (pos < cr[0]) {
                    spans.add(Span.styled(text.substring(pos, cr[0]), Style.EMPTY.fg(defaultColor)));
                }
                int counterEnd = Math.min(cr[1], to);
                Color counterColor = cr[2] == 1 ? Color.GREEN : Color.RED;
                spans.add(Span.styled(text.substring(cr[0], counterEnd), Style.EMPTY.fg(counterColor).bold()));
                pos = counterEnd;
            } else {
                spans.add(Span.styled(text.substring(pos, to), Style.EMPTY.fg(defaultColor)));
                pos = to;
            }
        }
    }

    private static int[] findNextCounterRange(List<int[]> ranges, int pos, int limit) {
        int[] best = null;
        for (int[] range : ranges) {
            if (range[1] > pos && range[0] < limit) {
                int start = Math.max(range[0], pos);
                if (best == null || start < best[0]) {
                    best = new int[] { start, range[1], range[2] };
                }
            }
        }
        return best;
    }

    private Color getDiagramNodeColor(String type) {
        if (type == null) {
            return Color.GRAY;
        }
        return switch (type) {
            case "from" -> Color.GREEN;
            case "to", "toD", "wireTap", "enrich", "pollEnrich" -> Color.BLUE;
            case "choice", "when", "otherwise" -> Color.YELLOW;
            case "marshal", "unmarshal", "transform", "setBody", "setHeader", "setProperty",
                    "convertBodyTo", "removeHeader", "removeHeaders", "removeProperty", "removeProperties" ->
                Color.CYAN;
            case "bean", "process", "log", "script", "delay" -> Color.MAGENTA;
            case "filter", "split", "aggregate", "multicast", "recipientList",
                    "routingSlip", "dynamicRouter", "loadBalance",
                    "circuitBreaker", "saga", "doTry", "doCatch", "doFinally",
                    "onException", "onCompletion", "intercept",
                    "loop", "resequence", "throttle", "kamelet", "pipeline", "threads" ->
                Color.rgb(0x89, 0x57, 0xE5);
            default -> Color.GRAY;
        };
    }

    private void loadDiagramForSelectedRoute() {
        if (selectedPid == null || runner == null) {
            return;
        }
        if (!diagramLoading.compareAndSet(false, true)) {
            return;
        }

        IntegrationInfo info = findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            diagramLoading.set(false);
            return;
        }

        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);

        Integer sel = routeTableState.selected();
        RouteInfo selectedRoute;
        if (sel != null && sel >= 0 && sel < sortedRoutes.size()) {
            selectedRoute = sortedRoutes.get(sel);
        } else {
            selectedRoute = sortedRoutes.get(0);
        }

        // Capture state needed by the background thread
        String pid = selectedPid;
        boolean textMode = diagramTextMode;
        boolean showMetrics = diagramMetrics;
        String routeId = selectedRoute.routeId;

        boolean initialLoad = !showDiagram;
        if (initialLoad) {
            diagramRouteId = routeId;
            diagramLines = List.of("(Loading diagram...)");
            diagramImageData = null;
            diagramFullImageData = null;
            showDiagram = true;
            diagramScroll = 0;
            diagramScrollX = 0;
        }

        runner.scheduler().execute(() -> {
            try {
                loadDiagramInBackground(pid, textMode, routeId, showMetrics);
            } finally {
                diagramLoading.set(false);
            }
        });
    }

    private void loadDiagramInBackground(String pid, boolean textMode, String routeId, boolean metrics) {
        Path outputFile = getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-structure");
        root.put("filter", "*");
        root.put("brief", false);
        root.put("metric", true);

        Path actionFile = getActionFile(pid);
        org.apache.camel.dsl.jbang.core.common.PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            applyDiagramResult(routeId, List.of("(No response from integration)"), null, null, null);
            return;
        }

        JsonArray arr = (JsonArray) jo.get("routes");
        if (arr == null) {
            applyDiagramResult(routeId, List.of("(No routes in response)"), null, null, null);
            return;
        }

        List<RouteDiagramLayoutEngine.RouteInfo> diagramRoutes = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            String rid = objToString(o.get("routeId"));
            if (routeId != null && !routeId.equals(rid)) {
                continue;
            }
            RouteDiagramLayoutEngine.RouteInfo route = new RouteDiagramLayoutEngine.RouteInfo();
            route.routeId = rid;
            List<JsonObject> lines = o.getCollection("code");
            if (lines != null) {
                for (JsonObject line : lines) {
                    RouteDiagramLayoutEngine.NodeInfo node = new RouteDiagramLayoutEngine.NodeInfo();
                    node.type = objToString(line.get("type"));
                    node.code = Jsoner.unescape(objToString(line.get("code")));
                    Integer level = line.getInteger("level");
                    node.level = level != null ? level : 0;
                    JsonObject stats = (JsonObject) line.get("statistics");
                    if (stats != null) {
                        RouteDiagramLayoutEngine.StatInfo stat = new RouteDiagramLayoutEngine.StatInfo();
                        stat.exchangesTotal = stats.getLongOrDefault("exchangesTotal", 0);
                        stat.exchangesFailed = stats.getLongOrDefault("exchangesFailed", 0);
                        node.stat = stat;
                    }
                    route.nodes.add(node);
                }
            }
            diagramRoutes.add(route);
        }

        if (textMode) {
            RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                    RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE,
                    RouteDiagramLayoutEngine.NodeLabelMode.CODE);
            List<RouteDiagramLayoutEngine.LayoutRoute> layoutRoutes = new ArrayList<>();
            int currentY = RouteDiagramLayoutEngine.PADDING;
            for (RouteDiagramLayoutEngine.RouteInfo r : diagramRoutes) {
                RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, currentY);
                layoutRoutes.add(lr);
                currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;
            }
            RouteDiagramAsciiRenderer asciiRenderer = new RouteDiagramAsciiRenderer(
                    RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE, true, metrics);
            String ascii = asciiRenderer.renderDiagram(layoutRoutes, currentY);
            List<RouteDiagramAsciiRenderer.CounterPos> origPositions = asciiRenderer.getCounterPositions();

            // Build result lines, remapping counter positions to account for removed empty lines
            String[] rawLines = ascii.split("\n", -1);
            List<String> result = new ArrayList<>();
            int[] rowMapping = new int[rawLines.length];
            int newRow = 0;
            for (int i = 0; i < rawLines.length; i++) {
                if (!rawLines[i].isEmpty()) {
                    rowMapping[i] = newRow++;
                    result.add(rawLines[i]);
                } else {
                    rowMapping[i] = -1;
                }
            }
            List<RouteDiagramAsciiRenderer.CounterPos> positions = new ArrayList<>();
            for (RouteDiagramAsciiRenderer.CounterPos cp : origPositions) {
                if (cp.row() >= 0 && cp.row() < rowMapping.length && rowMapping[cp.row()] >= 0) {
                    positions.add(new RouteDiagramAsciiRenderer.CounterPos(
                            rowMapping[cp.row()], cp.col(), cp.length(), cp.type()));
                }
            }
            applyDiagramResult(routeId, result, null, null, null, positions);
        } else {
            TerminalImageCapabilities caps = TerminalImageCapabilities.detect();
            if (caps.supportsNativeImages()) {
                RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
                List<RouteDiagramLayoutEngine.LayoutRoute> layoutRoutes = new ArrayList<>();
                int totalHeight = 0;
                for (RouteDiagramLayoutEngine.RouteInfo r : diagramRoutes) {
                    RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, totalHeight);
                    layoutRoutes.add(lr);
                    totalHeight = lr.maxY;
                }
                RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                        engine.getNodeWidth(), RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE * RouteDiagramLayoutEngine.SCALE,
                        metrics);
                RouteDiagramRenderer.DiagramColors colors = RouteDiagramRenderer.DiagramColors.parse("transparent");
                java.awt.image.BufferedImage image = renderer.renderDiagram(layoutRoutes, totalHeight, colors);
                ImageData fullImage = ImageData.fromBufferedImage(image);
                ImageData resized = fullImage.resize(fullImage.width() / 2, fullImage.height() / 2);
                ImageProtocol protocol = caps.bestProtocol();
                applyDiagramResult(routeId, Collections.emptyList(), resized, resized, protocol);
            } else {
                applyDiagramResult(routeId, List.of(
                        "(Terminal does not support image rendering)",
                        "(Press Shift+D for text diagram)"), null, null, null);
            }
        }
    }

    private void applyDiagramResult(
            String routeId, List<String> lines, ImageData imageData, ImageData fullImageData, ImageProtocol protocol) {
        applyDiagramResult(routeId, lines, imageData, fullImageData, protocol, Collections.emptyList());
    }

    private void applyDiagramResult(
            String routeId, List<String> lines, ImageData imageData, ImageData fullImageData, ImageProtocol protocol,
            List<RouteDiagramAsciiRenderer.CounterPos> positions) {
        if (runner == null) {
            return;
        }
        runner.runOnRenderThread(() -> {
            boolean wasShowing = showDiagram;
            diagramRouteId = routeId;
            diagramLines = lines;
            diagramCounterPositions = positions;
            diagramImageData = imageData;
            diagramFullImageData = fullImageData;
            diagramProtocol = protocol;
            if (!wasShowing) {
                diagramScroll = 0;
                diagramScrollX = 0;
                diagramCropX = -1;
                diagramCropY = -1;
                diagramCropW = -1;
                diagramCropH = -1;
            }
            showDiagram = true;
        });
    }

    private static JsonObject pollJsonResponse(Path outputFile, long timeout) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout) {
            try {
                Thread.sleep(100);
                if (Files.exists(outputFile) && outputFile.toFile().length() > 0) {
                    String text = Files.readString(outputFile);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    // ---- Tab 3: Health ----

    private void renderHealth(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Split: health table (fill) + memory gauge (3 rows)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(3))
                .split(area);

        List<HealthCheckInfo> healthChecks = getFilteredHealthChecks(info);

        List<Row> rows = new ArrayList<>();
        for (HealthCheckInfo hc : healthChecks) {
            Style stateStyle;
            String icon;
            if ("UP".equals(hc.state)) {
                stateStyle = Style.EMPTY.fg(Color.GREEN);
                icon = "\u2714 ";
            } else if ("DOWN".equals(hc.state)) {
                stateStyle = Style.EMPTY.fg(Color.RED);
                icon = "\u2716 ";
            } else {
                stateStyle = Style.EMPTY.fg(Color.YELLOW);
                icon = "\u26A0 ";
            }

            String kind = "";
            if (hc.readiness) {
                kind += "R";
            }
            if (hc.liveness) {
                kind += kind.isEmpty() ? "L" : "/L";
            }

            rows.add(Row.from(
                    Cell.from(Span.styled(hc.group != null ? hc.group : "", Style.EMPTY.dim())),
                    Cell.from(Span.styled(hc.name != null ? hc.name : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(icon + hc.state, stateStyle)),
                    Cell.from(kind),
                    Cell.from(hc.message != null ? hc.message : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled(showOnlyDown ? "No DOWN checks" : "No health checks registered",
                            Style.EMPTY.dim())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
        }

        String title = showOnlyDown
                ? " Health [DOWN only] "
                : " Health ";

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("GROUP", Style.EMPTY.bold())),
                        Cell.from(Span.styled("NAME", Style.EMPTY.bold())),
                        Cell.from(Span.styled("STATUS", Style.EMPTY.bold())),
                        Cell.from(Span.styled("KIND", Style.EMPTY.bold())),
                        Cell.from(Span.styled("MESSAGE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(25),
                        Constraint.length(12),
                        Constraint.length(6),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), healthTableState);

        // Memory gauge
        if (info.heapMemMax > 0) {
            int pct = (int) (100.0 * info.heapMemUsed / info.heapMemMax);
            Style gaugeStyle = pct > 80 ? Style.EMPTY.fg(Color.RED)
                    : pct > 60 ? Style.EMPTY.fg(Color.YELLOW) : Style.EMPTY.fg(Color.GREEN);
            Gauge gauge = Gauge.builder()
                    .percent(pct)
                    .label(String.format("Heap: %s / %s (%d%%)", formatBytes(info.heapMemUsed),
                            formatBytes(info.heapMemMax), pct))
                    .gaugeStyle(gaugeStyle)
                    .block(Block.builder().borderType(BorderType.ROUNDED).build())
                    .build();

            frame.renderWidget(gauge, chunks.get(1));
        }
    }

    private List<HealthCheckInfo> getFilteredHealthChecks(IntegrationInfo info) {
        if (showOnlyDown) {
            return info.healthChecks.stream().filter(hc -> "DOWN".equals(hc.state)).toList();
        }
        return info.healthChecks;
    }

    // ---- Tab 4: Endpoints ----

    private void renderEndpoints(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<Row> rows = new ArrayList<>();
        for (EndpointInfo ep : info.endpoints) {
            String dir = ep.direction != null ? ep.direction : "";
            Style dirStyle = switch (dir) {
                case "in" -> Style.EMPTY.fg(Color.GREEN);
                case "out" -> Style.EMPTY.fg(Color.BLUE);
                default -> Style.EMPTY.fg(Color.YELLOW);
            };
            String arrow = switch (dir) {
                case "in" -> "\u2192 ";
                case "out" -> "\u2190 ";
                default -> "\u2194 ";
            };

            rows.add(Row.from(
                    Cell.from(Span.styled(ep.component != null ? ep.component : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(arrow + dir, dirStyle)),
                    Cell.from(ep.uri != null ? ep.uri : ""),
                    Cell.from(ep.routeId != null ? ep.routeId : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled("No endpoints", Style.EMPTY.dim())),
                    Cell.from(""),
                    Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("COMPONENT", Style.EMPTY.bold())),
                        Cell.from(Span.styled("DIR", Style.EMPTY.bold())),
                        Cell.from(Span.styled("URI", Style.EMPTY.bold())),
                        Cell.from(Span.styled("ROUTE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(15),
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(20))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Endpoints ").build())
                .build();

        frame.renderStatefulWidget(table, area, endpointTableState);
    }

    // ---- Tab 5: Log ----

    private void renderLog(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Log data is refreshed in refreshData() tick handler

        // Auto-follow: select last entry
        if (logFollowMode && !filteredLogEntries.isEmpty()) {
            logTableState.select(filteredLogEntries.size() - 1);
        }

        // Split: log table (60%) + detail (40%)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(60), Constraint.fill())
                .split(area);

        // Log table
        List<Row> rows = new ArrayList<>();
        for (LogEntry entry : filteredLogEntries) {
            Style levelStyle = colorStyleForLevel(entry.level);
            rows.add(Row.from(
                    Cell.from(Span.styled(entry.time, Style.EMPTY.dim())),
                    Cell.from(Span.styled(entry.level, levelStyle)),
                    Cell.from(Span.styled(entry.logger != null ? entry.logger : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(entry.message, levelStyle))));
        }

        String levelTitle = buildLevelFilterTitle();
        Table logTable = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("TIME", Style.EMPTY.bold())),
                        Cell.from(Span.styled("LEVEL", Style.EMPTY.bold())),
                        Cell.from(Span.styled("LOGGER", Style.EMPTY.bold())),
                        Cell.from(Span.styled("MESSAGE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(6),
                        Constraint.length(20),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Log " + levelTitle).build())
                .build();

        frame.renderStatefulWidget(logTable, chunks.get(0), logTableState);

        // Detail panel for selected log entry
        renderLogDetail(frame, chunks.get(1));
    }

    private void renderLogDetail(Frame frame, Rect area) {
        Integer sel = logTableState.selected();
        if (sel == null || sel < 0 || sel >= filteredLogEntries.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a log entry", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        LogEntry entry = filteredLogEntries.get(sel);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(Line.from(Span.styled(entry.raw, colorStyleForLevel(entry.level)))))
                        .overflow(Overflow.WRAP_WORD)
                        .block(Block.builder().borderType(BorderType.ROUNDED)
                                .title(" " + entry.time + " " + entry.level + " ").build())
                        .build(),
                area);
    }

    private Style colorStyleForLevel(String level) {
        return switch (level) {
            case "ERROR", "FATAL" -> Style.EMPTY.fg(Color.RED);
            case "WARN" -> Style.EMPTY.fg(Color.YELLOW);
            case "DEBUG", "TRACE" -> Style.EMPTY.dim();
            default -> Style.EMPTY;
        };
    }

    private String buildLevelFilterTitle() {
        StringBuilder sb = new StringBuilder();
        sb.append(showLogTrace ? "[T] " : "[t] ");
        sb.append(showLogDebug ? "[D] " : "[d] ");
        sb.append(showLogInfo ? "[I] " : "[i] ");
        sb.append(showLogWarn ? "[W] " : "[w] ");
        sb.append(showLogError ? "[E] " : "[e] ");
        if (logFollowMode) {
            sb.append("[FOLLOW]");
        }
        return sb.toString();
    }

    private void readLogFile(String pid, List<String> target) {
        target.clear();
        Path logFile = CommandLineHelper.getCamelDir().resolve(pid + ".log");
        if (!Files.exists(logFile)) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = raf.length();
            // Read last ~64KB to get recent lines
            long startPos = Math.max(0, length - 64 * 1024);
            raf.seek(startPos);
            if (startPos > 0) {
                raf.readLine(); // skip partial line
            }
            // Read remaining bytes and split into lines using proper encoding
            byte[] remaining = new byte[(int) (length - raf.getFilePointer())];
            raf.readFully(remaining);
            String content = new String(remaining, StandardCharsets.UTF_8);
            String[] rawLines = content.split("\n", -1);
            int start = Math.max(0, rawLines.length - MAX_LOG_LINES);
            for (int i = start; i < rawLines.length; i++) {
                String line = TuiHelper.stripAnsi(rawLines[i]);
                if (!line.isEmpty()) {
                    target.add(line);
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private List<LogEntry> applyLogFilters(List<String> lines) {
        List<LogEntry> result = new ArrayList<>();
        for (String line : lines) {
            LogEntry entry = parseLogLine(line);
            if (!matchesLogLevelFilter(entry.level)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    // Regex for Spring Boot / Camel log format:
    // "2026-03-23T21:24:11.705+01:00  WARN 11283 --- [thread] logger : message"
    // "2026-03-23 21:24:11.705  WARN 11283 --- [thread] logger : message"
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\S*\\s+"
                                                               + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                                                               + "\\d+\\s+---\\s+"
                                                               + "\\[([^]]*)]\\s+"
                                                               + "(\\S+)\\s*:\\s*(.*)$");

    private static LogEntry parseLogLine(String line) {
        LogEntry entry = new LogEntry();
        entry.raw = line;
        try {
            Matcher m = LOG_PATTERN.matcher(line);
            if (m.matches()) {
                entry.time = m.group(2); // HH:mm:ss.SSS...
                // Truncate time to 12 chars (HH:mm:ss.SSS)
                if (entry.time.length() > 12) {
                    entry.time = entry.time.substring(0, 12);
                }
                entry.level = m.group(3);
                entry.logger = m.group(5);
                // Shorten logger to simple name
                int lastDot = entry.logger.lastIndexOf('.');
                if (lastDot > 0) {
                    entry.logger = entry.logger.substring(lastDot + 1);
                }
                entry.message = m.group(6);
            } else {
                entry.time = "";
                entry.level = "INFO";
                entry.message = line;
            }
        } catch (Exception e) {
            entry.time = "";
            entry.level = "INFO";
            entry.message = line;
        }
        return entry;
    }

    private boolean matchesLogLevelFilter(String level) {
        return switch (level) {
            case "ERROR", "FATAL" -> showLogError;
            case "WARN" -> showLogWarn;
            case "DEBUG" -> showLogDebug;
            case "TRACE" -> showLogTrace;
            default -> showLogInfo;
        };
    }

    // ---- Tab 6: Trace ----

    private void renderTrace(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (traceDetailView && traceSelectedExchangeId != null) {
            renderTraceExchangeDetail(frame, area);
        } else {
            renderTraceExchangeList(frame, area);
        }
    }

    private void renderTraceExchangeList(Frame frame, Rect area) {
        List<String> exchangeIds = getTraceExchangeIds();

        // Build exchange summaries for sorting
        List<TraceEntry> current = traces.get();
        record ExchangeSummary(String exchangeId, String timestamp, long epochMs, String routeId,
                String status, long elapsed, int steps) {
        }
        List<ExchangeSummary> summaries = new ArrayList<>();
        for (String exchangeId : exchangeIds) {
            TraceEntry first = null;
            TraceEntry lastEntry = null;
            TraceEntry latestEntry = null;
            int stepCount = 0;
            for (TraceEntry e : current) {
                if (exchangeId.equals(e.exchangeId)) {
                    if (first == null) {
                        first = e;
                    }
                    latestEntry = e;
                    if (e.last) {
                        lastEntry = e;
                    }
                    stepCount++;
                }
            }
            if (first != null) {
                TraceEntry doneEntry = lastEntry != null ? lastEntry : latestEntry;
                String status = doneEntry.status != null ? doneEntry.status : "Processing";
                long elapsed = doneEntry.elapsed;
                summaries.add(new ExchangeSummary(
                        exchangeId, first.timestamp, first.epochMs,
                        first.routeId, status, elapsed, stepCount));
            }
        }

        // Sort
        summaries.sort((a, b) -> switch (traceSort) {
            case "time" -> Long.compare(b.epochMs, a.epochMs);
            case "route" -> {
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
            case "elapsed" -> Long.compare(b.elapsed, a.elapsed);
            case "exchange" -> {
                yield a.exchangeId.compareTo(b.exchangeId);
            }
            default -> 0;
        });

        traceSortedExchangeIds = summaries.stream().map(ExchangeSummary::exchangeId).toList();

        List<Row> rows = new ArrayList<>();
        for (ExchangeSummary s : summaries) {
            Style statusStyle = switch (s.status) {
                case "Done" -> Style.EMPTY.fg(Color.GREEN);
                case "Failed" -> Style.EMPTY.fg(Color.RED);
                case "Processing" -> Style.EMPTY.fg(Color.YELLOW);
                default -> Style.EMPTY;
            };
            rows.add(Row.from(
                    Cell.from(s.timestamp != null ? truncate(s.timestamp, 12) : ""),
                    Cell.from(Span.styled(
                            s.routeId != null ? truncate(s.routeId, 15) : "",
                            Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(s.status, statusStyle)),
                    Cell.from(String.format("%7s", s.elapsed + "ms")),
                    Cell.from(String.format("%6s", s.steps)),
                    Cell.from(s.exchangeId)));
        }

        Row header = Row.from(
                Cell.from(Span.styled(traceSortLabel("TIME", "time"), traceSortStyle("time"))),
                Cell.from(Span.styled(traceSortLabel("ROUTE", "route"), traceSortStyle("route"))),
                Cell.from(Span.styled("STATUS", Style.EMPTY.bold())),
                Cell.from(Span.styled(traceSortLabel("ELAPSED", "elapsed"), traceSortStyle("elapsed"))),
                Cell.from(Span.styled("STEPS", Style.EMPTY.bold())),
                Cell.from(Span.styled(traceSortLabel("EXCHANGE", "exchange"), traceSortStyle("exchange"))));

        String traceTitle = String.format(" Traces [%d] sort:%s ", summaries.size(), traceSort);

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(12),
                        Constraint.length(15),
                        Constraint.length(12),
                        Constraint.length(10),
                        Constraint.length(6),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(traceTitle).build())
                .build();

        frame.renderStatefulWidget(table, area, traceTableState);
    }

    private void renderTraceExchangeDetail(Frame frame, Rect area) {
        List<TraceEntry> steps = getTraceSteps(traceSelectedExchangeId);

        // Layout: step table (fixed 10 rows) + detail panel (fill)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(10), Constraint.fill())
                .split(area);

        List<Row> rows = new ArrayList<>();
        for (TraceEntry entry : steps) {
            rows.add(buildStepRow(
                    entry.direction, entry.first, entry.last, entry.failed,
                    entry.timestamp, entry.routeId, entry.nodeId, entry.processor, entry.elapsed));
        }

        String stepTitle = String.format(" Trace [%s] ", truncate(traceSelectedExchangeId, 30));
        frame.renderStatefulWidget(
                buildStepTable(rows, stepTitle), chunks.get(0), traceStepTableState);

        // Detail panel for selected step
        renderTraceStepDetail(frame, chunks.get(1), steps);
    }

    private void renderTraceStepDetail(Frame frame, Rect area, List<TraceEntry> steps) {
        Integer sel = traceStepTableState.selected();

        if (sel == null || sel < 0 || sel >= steps.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a trace step to view details",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).build())
                            .build(),
                    area);
            return;
        }

        TraceEntry entry = steps.get(sel);
        List<Line> lines = new ArrayList<>();

        addExchangeInfoLines(lines, entry.exchangeId, entry.routeId, entry.nodeId, entry.nodeLabel,
                entry.location, entry.elapsed, entry.threadName, entry.failed);
        if (showTraceProperties) {
            addKvLines(lines, " Exchange Properties:", entry.exchangeProperties, entry.exchangePropertyTypes);
        }
        if (showTraceVariables) {
            addKvLines(lines, " Exchange Variables:", entry.exchangeVariables, entry.exchangeVariableTypes);
        }
        if (showTraceHeaders) {
            addKvLines(lines, " Headers:", entry.headers, entry.headerTypes);
        }
        if (showTraceBody) {
            addBodyLines(lines, entry.body, entry.bodyType);
        }

        int[] scroll = { traceDetailScroll };
        renderDetailPanel(frame, area, lines, traceWordWrap, scroll, traceDetailScrollState);
        traceDetailScroll = scroll[0];
    }

    private List<String> getTraceExchangeIds() {
        List<TraceEntry> current = traces.get();
        // LinkedHashSet: O(1) contains, preserves first-seen insertion order
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (TraceEntry e : current) {
            if (e.exchangeId != null) {
                seen.add(e.exchangeId);
            }
        }
        return new ArrayList<>(seen);
    }

    private List<TraceEntry> getTraceSteps(String exchangeId) {
        List<TraceEntry> current = traces.get();
        List<TraceEntry> steps = new ArrayList<>();
        for (TraceEntry e : current) {
            if (exchangeId != null && exchangeId.equals(e.exchangeId)) {
                steps.add(e);
            }
        }
        steps.sort((a, b) -> {
            String ua = a.uid != null ? a.uid : "";
            String ub = b.uid != null ? b.uid : "";
            try {
                return Long.compare(Long.parseLong(ua), Long.parseLong(ub));
            } catch (NumberFormatException e) {
                return ua.compareTo(ub);
            }
        });
        return steps;
    }

    // ---- Tab 7: History ----

    private void renderHistory(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<HistoryEntry> current = historyEntries;

        // Layout: history list (fixed 6 rows + header + borders) + detail panel (fill)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(10), Constraint.fill())
                .split(area);

        // History list
        List<Row> rows = new ArrayList<>();
        for (HistoryEntry entry : current) {
            rows.add(buildStepRow(
                    entry.direction, entry.first, entry.last, entry.failed,
                    entry.timestamp, entry.routeId, entry.nodeId, entry.processor, entry.elapsed));
        }

        Title historyTitle = buildHistoryTitle(current);
        frame.renderStatefulWidget(
                buildStepTable(rows, historyTitle), chunks.get(0), historyTableState);

        // Detail panel
        renderHistoryDetail(frame, chunks.get(1), current);
    }

    private void renderHistoryDetail(Frame frame, Rect area, List<HistoryEntry> current) {
        Integer sel = historyTableState.selected();

        if (sel == null || sel < 0 || sel >= current.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a history entry to view details",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        HistoryEntry entry = current.get(sel);
        List<Line> lines = new ArrayList<>();

        addExchangeInfoLines(lines, entry.exchangeId, entry.routeId, entry.nodeId, entry.nodeLabel,
                entry.location, entry.elapsed, entry.threadName, entry.failed);
        if (showHistoryProperties) {
            addKvLines(lines, " Exchange Properties:", entry.exchangeProperties, entry.exchangePropertyTypes);
        }
        if (showHistoryVariables) {
            addKvLines(lines, " Exchange Variables:", entry.exchangeVariables, entry.exchangeVariableTypes);
        }
        if (showHistoryHeaders) {
            addKvLines(lines, " Headers:", entry.headers, entry.headerTypes);
        }
        if (showHistoryBody) {
            addBodyLines(lines, entry.body, entry.bodyType);
        }
        if (entry.exception != null) {
            lines.add(Line.from(Span.styled(" Exception:", Style.EMPTY.fg(Color.RED).bold())));
            lines.add(Line.from(Span.raw("   " + entry.exception)));
        }

        int[] scroll = { historyDetailScroll };
        renderDetailPanel(frame, area, lines, historyWordWrap, scroll, historyDetailScrollState);
        historyDetailScroll = scroll[0];
    }

    private static void addExchangeInfoLines(
            List<Line> lines, String exchangeId, String routeId,
            String nodeId, String nodeLabel, String location, long elapsed, String threadName, boolean failed) {
        lines.add(Line.from(
                Span.styled(" Exchange: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(exchangeId != null ? exchangeId : "")));
        lines.add(Line.from(
                Span.styled(" Route:    ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(routeId != null ? routeId : ""),
                Span.styled("  Node: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(nodeId != null ? nodeId : ""),
                Span.raw(nodeLabel != null ? " (" + nodeLabel + ")" : "")));
        lines.add(Line.from(
                Span.styled(" Location: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(location != null ? location : "")));
        lines.add(Line.from(
                Span.styled(" Elapsed:  ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(elapsed >= 0 ? elapsed + "ms" : ""),
                Span.styled("  Thread: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(threadName != null ? threadName : "")));
        if (failed) {
            lines.add(Line.from(
                    Span.styled(" Status:   ", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled("Failed", Style.EMPTY.fg(Color.RED).bold())));
        }
        lines.add(Line.from(Span.raw("")));
    }

    private static void addKvLines(
            List<Line> lines, String section,
            Map<String, Object> map, Map<String, String> types) {
        if (map == null || map.isEmpty()) {
            return;
        }
        lines.add(Line.from(Span.styled(section, Style.EMPTY.fg(Color.GREEN).bold())));
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String type = types != null ? types.get(entry.getKey()) : null;
            String typeLabel;
            if (type != null) {
                String t = "(" + type + ")";
                t = truncate(t, 20);
                typeLabel = String.format("%-20s ", t);
            } else {
                typeLabel = String.format("%-21s", "");
            }
            lines.add(Line.from(
                    Span.styled("   " + typeLabel, Style.EMPTY.dim()),
                    Span.styled(entry.getKey(), Style.EMPTY.fg(Color.CYAN)),
                    Span.raw(" = "),
                    Span.raw(entry.getValue() != null ? entry.getValue().toString() : "null")));
        }
        lines.add(Line.from(Span.raw("")));
    }

    private static void addBodyLines(List<Line> lines, String body, String bodyType) {
        if (body != null) {
            if (bodyType != null) {
                lines.add(Line.from(
                        Span.styled(" Body: ", Style.EMPTY.fg(Color.GREEN).bold()),
                        Span.styled("(" + bodyType + ")", Style.EMPTY.dim())));
            } else {
                lines.add(Line.from(Span.styled(" Body:", Style.EMPTY.fg(Color.GREEN).bold())));
            }
            String[] bodyParts = body.split("\n");
            for (String bl : bodyParts) {
                lines.add(Line.from(Span.raw("   " + bl)));
            }
        } else {
            lines.add(Line.from(Span.styled(" Body is null", Style.EMPTY.fg(Color.GREEN).bold())));
        }
        lines.add(Line.from(Span.raw("")));
    }

    private void renderDetailPanel(
            Frame frame, Rect area, List<Line> lines,
            boolean wordWrap, int[] scroll, ScrollbarState scrollState) {
        Block block = Block.builder().borderType(BorderType.ROUNDED).build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        int visibleHeight = Math.max(1, inner.height());
        int contentHeight;
        if (wordWrap) {
            int width = Math.max(1, inner.width() - 1);
            contentHeight = 0;
            for (Line l : lines) {
                int w = l.width();
                contentHeight += Math.max(1, (w + width - 1) / width);
            }
        } else {
            contentHeight = lines.size();
        }
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        if (scroll[0] > maxScroll) {
            scroll[0] = maxScroll;
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        Paragraph detail = Paragraph.builder()
                .text(Text.from(lines))
                .overflow(wordWrap ? Overflow.WRAP_WORD : Overflow.CLIP)
                .scroll(scroll[0])
                .build();
        frame.renderWidget(detail, hChunks.get(0));

        if (contentHeight > visibleHeight) {
            scrollState.contentLength(contentHeight);
            scrollState.viewportContentLength(visibleHeight);
            scrollState.position(scroll[0]);
            frame.renderStatefulWidget(
                    Scrollbar.builder().build(),
                    hChunks.get(1), scrollState);
        }
    }

    private static Row buildStepRow(
            String direction, boolean first, boolean last, boolean failed,
            String timestamp, String routeId, String nodeId, String processor, long elapsed) {
        Style dirStyle;
        if (first) {
            dirStyle = Style.EMPTY.fg(Color.GREEN);
        } else if (last) {
            dirStyle = failed ? Style.EMPTY.fg(Color.RED) : Style.EMPTY.fg(Color.GREEN);
        } else {
            dirStyle = Style.EMPTY;
        }
        String elapsedStr = elapsed >= 0 ? elapsed + "ms" : "";
        return Row.from(
                Cell.from(Span.styled(direction, dirStyle)),
                Cell.from(timestamp != null ? truncate(timestamp, 12) : ""),
                Cell.from(Span.styled(routeId != null ? truncate(routeId, 15) : "", Style.EMPTY.fg(Color.CYAN))),
                Cell.from(nodeId != null ? truncate(nodeId, 15) : ""),
                Cell.from(processor != null ? processor : ""),
                Cell.from(elapsedStr));
    }

    private static Table buildStepTable(List<Row> rows, Object title) {
        Row header = Row.from(
                Cell.from(Span.styled("", Style.EMPTY.bold())),
                Cell.from(Span.styled("TIME", Style.EMPTY.bold())),
                Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                Cell.from(Span.styled("ID", Style.EMPTY.bold())),
                Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                Cell.from(Span.styled("ELAPSED", Style.EMPTY.bold())));
        Block block = title instanceof Title t
                ? Block.builder().borderType(BorderType.ROUNDED).title(t).build()
                : Block.builder().borderType(BorderType.ROUNDED).title(title.toString()).build();
        return Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(4),
                        Constraint.length(12),
                        Constraint.length(15),
                        Constraint.length(15),
                        Constraint.fill(),
                        Constraint.length(10))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(block)
                .build();
    }

    private Title buildHistoryTitle(List<HistoryEntry> entries) {
        if (entries.isEmpty()) {
            return Title.from(" History of last completed ");
        }
        HistoryEntry first = entries.get(0);
        HistoryEntry last = null;
        for (HistoryEntry e : entries) {
            if (e.last) {
                last = e;
                break;
            }
        }
        if (last == null) {
            last = entries.get(entries.size() - 1);
        }

        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" History of last completed ("));
        boolean failed = last.failed;
        spans.add(Span.styled("status:" + (failed ? "failed" : "success"),
                failed ? Style.EMPTY.fg(Color.RED).bold() : Style.EMPTY.fg(Color.GREEN).bold()));
        if (last.elapsed >= 0) {
            spans.add(Span.raw(" elapsed:" + TimeUtils.printDuration(last.elapsed, true)));
        }
        if (first.epochMs > 0) {
            String ago = TimeUtils.printSince(first.epochMs);
            spans.add(Span.raw(" ago:" + ago));
        }
        spans.add(Span.raw(") "));
        return new Title(Line.from(spans), Alignment.LEFT, Overflow.CLIP);
    }

    // ---- Shared rendering ----

    private void renderNoSelection(Frame frame, Rect area) {
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(Line.from(
                                Span.styled(" Select an integration from the Overview tab (press 1)",
                                        Style.EMPTY.dim()))))
                        .block(Block.builder().borderType(BorderType.ROUNDED)
                                .title(" No integration selected ").build())
                        .build(),
                area);
    }

    private void renderFooter(Frame frame, Rect area) {
        List<Span> spans = new ArrayList<>();
        int tab = tabsState.selected();

        if (tab == TAB_OVERVIEW) {
            hint(spans, "q", "quit");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "Enter", "details");
            hint(spans, "1-7", "tabs");
        } else if (tab == TAB_ROUTES && showDiagram) {
            String closeKey = diagramTextMode ? "D" : "d";
            hint(spans, closeKey + "/Esc", "close");
            hint(spans, "\u2191\u2193\u2190\u2192", "scroll");
            hint(spans, "PgUp/PgDn", "page");
            hint(spans, "Home/End", "top/bottom");
            if (diagramMetrics && !diagramTextMode) {
                hint(spans, "m", "metrics [on]");
                hintLast(spans, "F5", "refresh counters");
            } else {
                hintLast(spans, "m", "metrics" + (diagramMetrics ? " [on]" : " [off]"));
            }
        } else if (tab == TAB_ROUTES) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "d", "diagram");
            hint(spans, "D", "text diagram");
            hint(spans, "1-7", "tabs");
        } else if (tab == TAB_HEALTH) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "d", "toggle DOWN");
            hint(spans, "1-7", "tabs");
        } else if (tab == TAB_LOG) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "scroll");
            hint(spans, "PgUp/PgDn", "page");
            hint(spans, "Home/End", "top/end");
            hint(spans, "t/d/i/w/e", "levels");
            hintLast(spans, "f", "follow");
        } else if (tab == TAB_TRACE && traceDetailView) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "PgUp/PgDn", "scroll detail");
            hint(spans, "p", "properties" + (showTraceProperties ? " [on]" : " [off]"));
            hint(spans, "v", "variables" + (showTraceVariables ? " [on]" : " [off]"));
            hint(spans, "h", "headers" + (showTraceHeaders ? " [on]" : " [off]"));
            hint(spans, "b", "body" + (showTraceBody ? " [on]" : " [off]"));
            hintLast(spans, "w", "wrap" + (traceWordWrap ? " [on]" : " [off]"));
        } else if (tab == TAB_TRACE) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "Enter", "details");
            hintLast(spans, "F5", "refresh");
        } else if (tab == TAB_HISTORY) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "PgUp/PgDn", "scroll detail");
            hint(spans, "p", "properties" + (showHistoryProperties ? " [on]" : " [off]"));
            hint(spans, "v", "variables" + (showHistoryVariables ? " [on]" : " [off]"));
            hint(spans, "h", "headers" + (showHistoryHeaders ? " [on]" : " [off]"));
            hint(spans, "b", "body" + (showHistoryBody ? " [on]" : " [off]"));
            hint(spans, "w", "wrap" + (historyWordWrap ? " [on]" : " [off]"));
            hintLast(spans, "F5", "refresh");
        } else {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "1-7", "tabs");
        }

        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }

    private static String formatSinceLastRoute(RouteInfo route) {
        return formatSinceLast(route.sinceLastStarted, route.sinceLastCompleted, route.sinceLastFailed);
    }

    private static Cell rightCell(String text, int width) {
        return Cell.from(String.format("%" + width + "s", text));
    }

    private static Cell rightCell(String text, int width, Style style) {
        return Cell.from(Span.styled(String.format("%" + width + "s", text), style));
    }

    private static Line badge(String label, long count) {
        if (count > 0) {
            return Line.from(
                    Span.raw(label),
                    Span.styled("(" + count + ")", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.raw(" "));
        }
        return Line.from(label);
    }

    private static final Style HINT_KEY_STYLE = Style.EMPTY.fg(Color.YELLOW).bold();

    private static void hint(List<Span> spans, String key, String label) {
        spans.add(Span.styled(" " + key, HINT_KEY_STYLE));
        spans.add(Span.raw(" " + label + "  "));
    }

    private static void hintLast(List<Span> spans, String key, String label) {
        spans.add(Span.styled(" " + key, HINT_KEY_STYLE));
        spans.add(Span.raw(" " + label));
    }

    // ---- Data Loading ----

    private void refreshData() {
        if (runner == null) {
            refreshDataSync();
            return;
        }
        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }
        lastRefresh = System.currentTimeMillis();
        String currentSelectedPid = selectedPid;
        runner.scheduler().execute(() -> {
            try {
                refreshDataSync();
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    private void refreshDataSync() {
        lastRefresh = System.currentTimeMillis();
        try {
            List<IntegrationInfo> infos = new ArrayList<>();
            List<Long> pids = findPids(name);
            ProcessHandle.allProcesses()
                    .filter(ph -> pids.contains(ph.pid()))
                    .forEach(ph -> {
                        JsonObject root = loadStatus(ph.pid());
                        if (root != null) {
                            IntegrationInfo info = parseIntegration(ph, root);
                            if (info != null) {
                                infos.add(info);
                                updateThroughputHistory(info);
                            }
                        }
                    });

            // Detect disappeared integrations and start vanishing
            Set<String> livePids = infos.stream().map(i -> i.pid).collect(Collectors.toSet());
            List<IntegrationInfo> previous = data.get();
            for (IntegrationInfo prev : previous) {
                if (!prev.vanishing && !livePids.contains(prev.pid) && !vanishing.containsKey(prev.pid)) {
                    vanishing.put(prev.pid, new VanishingInfo(prev, System.currentTimeMillis()));
                }
            }

            // Expire old vanishing entries
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, VanishingInfo>> it = vanishing.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, VanishingInfo> entry = it.next();
                if (now - entry.getValue().startTime > VANISH_DURATION_MS) {
                    it.remove();
                    throughputHistory.remove(entry.getKey());
                } else if (!livePids.contains(entry.getKey())) {
                    IntegrationInfo ghost = entry.getValue().info;
                    ghost.vanishing = true;
                    ghost.vanishStart = entry.getValue().startTime;
                    infos.add(ghost);
                } else {
                    it.remove();
                }
            }

            data.set(infos);

            // Refresh log data for the selected integration
            IntegrationInfo selected = findSelectedIntegration();
            if (selected != null) {
                List<String> newLogLines = new ArrayList<>();
                readLogFile(selected.pid, newLogLines);
                List<LogEntry> newFilteredEntries = applyLogFilters(newLogLines);
                logLines = newLogLines;
                filteredLogEntries = newFilteredEntries;
            }

            // Refresh trace data
            refreshTraceData(pids);
        } catch (Exception e) {
            // ignore refresh errors
        }
    }

    private void updateThroughputHistory(IntegrationInfo info) {
        // Track exchangesTotal over a 1-second sliding window for stable throughput
        long currentTotal = info.exchangesTotal;
        long now = System.currentTimeMillis();

        String pid = info.pid;
        LinkedList<long[]> samples = throughputSamples.computeIfAbsent(pid, k -> new LinkedList<>());
        samples.add(new long[] { now, currentTotal });

        // Remove samples older than 1 second
        while (!samples.isEmpty() && now - samples.get(0)[0] > 1000) {
            samples.remove(0);
        }

        // Compute throughput over the window
        if (samples.size() >= 2) {
            long[] oldest = samples.get(0);
            long[] newest = samples.get(samples.size() - 1);
            long deltaExchanges = newest[1] - oldest[1];
            long deltaTimeMs = newest[0] - oldest[0];
            long tp = deltaTimeMs > 0 ? (deltaExchanges * 1000) / deltaTimeMs : 0;

            LinkedList<Long> hist = throughputHistory.computeIfAbsent(pid, k -> new LinkedList<>());
            // Only add one point per second to keep the sparkline meaningful
            Long lastTime = previousExchangesTime.get(pid);
            if (lastTime == null || now - lastTime >= 1000) {
                previousExchangesTime.put(pid, now);
                hist.add(tp);
                while (hist.size() > MAX_SPARKLINE_POINTS) {
                    hist.remove(0);
                }
            }
        }
    }

    // ---- Trace Data Loading ----

    private void refreshTraceData(List<Long> pids) {
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
                                TraceEntry entry = parseTraceEntry(traceJson, pid);
                                if (entry != null) {
                                    allTraces.add(entry);
                                }
                            }
                        }
                    } else {
                        // Fallback: try parsing the line itself as a trace entry
                        TraceEntry entry = parseTraceEntry(json, pid);
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

    @SuppressWarnings("unchecked")
    private TraceEntry parseTraceEntry(JsonObject json, String pid) {
        TraceEntry entry = new TraceEntry();
        entry.pid = pid;
        entry.uid = stringValue(json.get("uid"));
        entry.exchangeId = json.getString("exchangeId");
        entry.routeId = json.getString("routeId");
        entry.nodeId = json.getString("nodeId");
        entry.nodeShortName = json.getString("nodeShortName");
        entry.location = json.getString("location");
        entry.nodeLabel = json.getString("nodeLabel");
        entry.threadName = json.getString("threadName");
        entry.first = json.getBooleanOrDefault("first", false);
        entry.last = json.getBooleanOrDefault("last", false);
        entry.nodeLevel = json.getIntegerOrDefault("nodeLevel", 0);

        // timestamp is epoch millis (number)
        Object tsObj = json.get("timestamp");
        if (tsObj instanceof Number n) {
            long epochMs = n.longValue();
            entry.epochMs = epochMs;
            entry.timestamp = Instant.ofEpochMilli(epochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime().toString();
            if (entry.timestamp.length() > 12) {
                entry.timestamp = entry.timestamp.substring(0, 12);
            }
        } else if (tsObj != null) {
            entry.timestamp = tsObj.toString();
        }

        // Derive status from done/failed booleans
        Boolean done = (Boolean) json.get("done");
        Boolean failed = (Boolean) json.get("failed");
        entry.failed = Boolean.TRUE.equals(failed);
        if (entry.failed) {
            entry.status = "Failed";
        } else if (Boolean.TRUE.equals(done)) {
            entry.status = "Done";
        } else {
            entry.status = "Processing";
        }

        Object elapsedObj = json.get("elapsed");
        if (elapsedObj instanceof Number n) {
            entry.elapsed = n.longValue();
        } else if (elapsedObj != null) {
            try {
                entry.elapsed = Long.parseLong(elapsedObj.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // Compute direction and processor label
        if (entry.first || entry.last) {
            entry.nodeLevel = Math.max(0, entry.nodeLevel - 1);
        }
        String indent = "  ".repeat(entry.nodeLevel);
        if (entry.first) {
            entry.direction = "-->";
            String uri = json.getString("endpointUri");
            entry.processor = indent + "from[" + (uri != null ? uri : "") + "]";
        } else if (entry.last) {
            entry.direction = "<--";
            entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
        } else {
            entry.direction = "  >";
            entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
        }

        // Parse message object
        Object msgObj = json.get("message");
        if (msgObj instanceof JsonObject message) {
            MessageData md = parseMessage(message);
            entry.headers = md.headers();
            entry.headerTypes = md.headerTypes();
            entry.body = md.body();
            entry.bodyType = md.bodyType();
            if (entry.body != null) {
                entry.bodyPreview = entry.body.replace("\n", " ").replace("\r", "");
            }
            entry.exchangeProperties = md.exchangeProperties();
            entry.exchangePropertyTypes = md.exchangePropertyTypes();
            entry.exchangeVariables = md.exchangeVariables();
            entry.exchangeVariableTypes = md.exchangeVariableTypes();
        }

        return entry;
    }

    @SuppressWarnings("unchecked")
    private void refreshHistoryData(List<Long> pids) {
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
                            HistoryEntry entry = parseHistoryEntry(traceJson, Long.toString(pid));
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
        historyEntries = allEntries;
    }

    @SuppressWarnings("unchecked")
    private HistoryEntry parseHistoryEntry(JsonObject json, String pid) {
        HistoryEntry entry = new HistoryEntry();
        entry.pid = pid;
        entry.exchangeId = json.getString("exchangeId");
        entry.routeId = json.getString("routeId");
        entry.fromRouteId = json.getString("fromRouteId");
        entry.nodeId = json.getString("nodeId");
        entry.nodeShortName = json.getString("nodeShortName");
        entry.nodeLabel = json.getString("nodeLabel");
        entry.location = json.getString("location");
        entry.threadName = json.getString("threadName");
        entry.first = json.getBooleanOrDefault("first", false);
        entry.last = json.getBooleanOrDefault("last", false);
        entry.failed = json.getBooleanOrDefault("failed", false);
        entry.nodeLevel = json.getIntegerOrDefault("nodeLevel", 0);

        Object elapsedObj = json.get("elapsed");
        if (elapsedObj instanceof Number n) {
            entry.elapsed = n.longValue();
        } else {
            entry.elapsed = -1;
        }

        // Compute direction arrow
        if (entry.first) {
            entry.direction = "-->";
        } else if (entry.last) {
            entry.direction = "<--";
        } else {
            entry.direction = "  >";
        }

        // Compute processor label with tree indentation
        if (entry.first || entry.last) {
            entry.nodeLevel = Math.max(0, entry.nodeLevel - 1);
        }
        String indent = "  ".repeat(entry.nodeLevel);
        if (entry.first) {
            String uri = json.getString("endpointUri");
            entry.processor = indent + "from[" + (uri != null ? uri : "") + "]";
        } else {
            entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
        }

        // Timestamp
        Object tsObj = json.get("timestamp");
        if (tsObj instanceof Number n) {
            long epochMs = n.longValue();
            entry.epochMs = epochMs;
            entry.timestamp = Instant.ofEpochMilli(epochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime().toString();
            if (entry.timestamp.length() > 12) {
                entry.timestamp = entry.timestamp.substring(0, 12);
            }
        }

        // Parse message
        Object msgObj = json.get("message");
        if (msgObj instanceof JsonObject message) {
            MessageData md = parseMessage(message);
            entry.headers = md.headers();
            entry.headerTypes = md.headerTypes();
            entry.body = md.body();
            entry.bodyType = md.bodyType();
            entry.exchangeProperties = md.exchangeProperties();
            entry.exchangePropertyTypes = md.exchangePropertyTypes();
            entry.exchangeVariables = md.exchangeVariables();
            entry.exchangeVariableTypes = md.exchangeVariableTypes();
        }

        // Exception
        Object excObj = json.get("exception");
        if (excObj instanceof JsonObject excJson) {
            entry.exception = excJson.getString("message");
        }

        return entry;
    }

    private static String stringValue(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    record MessageData(
            Map<String, Object> headers,
            Map<String, String> headerTypes,
            String body,
            String bodyType,
            Map<String, Object> exchangeProperties,
            Map<String, String> exchangePropertyTypes,
            Map<String, Object> exchangeVariables,
            Map<String, String> exchangeVariableTypes) {
    }

    @SuppressWarnings("unchecked")
    private static MessageData parseMessage(JsonObject message) {
        Map<String, Object> headers = null;
        Map<String, String> headerTypes = null;
        String body = null;
        String bodyType = null;
        Map<String, Object> exchangeProperties = null;
        Map<String, String> exchangePropertyTypes = null;
        Map<String, Object> exchangeVariables = null;
        Map<String, String> exchangeVariableTypes = null;

        // Headers
        Object headersObj = message.get("headers");
        if (headersObj instanceof List<?> headerList) {
            headers = new LinkedHashMap<>();
            headerTypes = new LinkedHashMap<>();
            for (Object h : headerList) {
                if (h instanceof JsonObject hObj) {
                    String key = String.valueOf(hObj.get("key"));
                    headers.put(key, hObj.get("value"));
                    Object type = hObj.get("type");
                    if (type != null) {
                        headerTypes.put(key, TuiHelper.shortTypeName(type.toString()));
                    }
                }
            }
        } else if (headersObj instanceof Map) {
            headers = new LinkedHashMap<>((Map<String, Object>) headersObj);
        }

        // Body
        Object bodyObj = message.get("body");
        if (bodyObj instanceof JsonObject bodyJson) {
            Object val = bodyJson.get("value");
            body = val != null ? val.toString() : null;
            bodyType = TuiHelper.shortTypeName(bodyJson.getString("type"));
        } else if (bodyObj != null) {
            body = bodyObj.toString();
        }

        // Exchange properties
        Object propsObj = message.get("exchangeProperties");
        if (propsObj instanceof List<?> propList) {
            exchangeProperties = new LinkedHashMap<>();
            exchangePropertyTypes = new LinkedHashMap<>();
            for (Object p : propList) {
                if (p instanceof JsonObject pObj) {
                    String key = String.valueOf(pObj.get("key"));
                    exchangeProperties.put(key, pObj.get("value"));
                    Object type = pObj.get("type");
                    if (type != null) {
                        exchangePropertyTypes.put(key, TuiHelper.shortTypeName(type.toString()));
                    }
                }
            }
        } else if (propsObj instanceof Map) {
            exchangeProperties = new LinkedHashMap<>((Map<String, Object>) propsObj);
        }

        // Exchange variables
        Object varsObj = message.get("exchangeVariables");
        if (varsObj instanceof List<?> varList) {
            exchangeVariables = new LinkedHashMap<>();
            exchangeVariableTypes = new LinkedHashMap<>();
            for (Object v : varList) {
                if (v instanceof JsonObject vObj) {
                    String key = String.valueOf(vObj.get("key"));
                    exchangeVariables.put(key, vObj.get("value"));
                    Object type = vObj.get("type");
                    if (type != null) {
                        exchangeVariableTypes.put(key, TuiHelper.shortTypeName(type.toString()));
                    }
                }
            }
        } else if (varsObj instanceof Map) {
            exchangeVariables = new LinkedHashMap<>((Map<String, Object>) varsObj);
        }

        return new MessageData(
                headers, headerTypes, body, bodyType,
                exchangeProperties, exchangePropertyTypes, exchangeVariables, exchangeVariableTypes);
    }

    // ---- Integration Parsing ----

    @SuppressWarnings("unchecked")
    private IntegrationInfo parseIntegration(ProcessHandle ph, JsonObject root) {
        JsonObject context = (JsonObject) root.get("context");
        if (context == null) {
            return null;
        }

        IntegrationInfo info = new IntegrationInfo();
        info.name = context.getString("name");
        if ("CamelJBang".equals(info.name)) {
            info.name = ProcessHelper.extractName(root, ph);
        }
        info.pid = Long.toString(ph.pid());
        info.uptime = extractSince(ph);
        info.ago = TimeUtils.printSince(info.uptime);
        info.state = context.getIntegerOrDefault("phase", 0);
        info.camelVersion = context.getString("version");
        info.profile = context.getString("profile");

        JsonObject runtime = (JsonObject) root.get("runtime");
        info.platform = runtime != null ? runtime.getString("platform") : null;
        info.platformVersion = runtime != null ? runtime.getString("platformVersion") : null;

        Map<String, ?> stats = context.getMap("statistics");
        if (stats != null) {
            Object thp = stats.get("exchangesThroughput");
            if (thp != null) {
                info.throughput = thp.toString();
            }
            info.exchangesTotal = objToLong(stats.get("exchangesTotal"));
            info.failed = objToLong(stats.get("exchangesFailed"));
            info.inflight = objToLong(stats.get("exchangesInflight"));
            info.last = objToString(stats.get("lastProcessingTime"));
            info.delta = objToString(stats.get("deltaProcessingTime"));
            long tsStarted = objToLong(stats.get("lastCreatedExchangeTimestamp"));
            if (tsStarted > 0) {
                info.sinceLastStarted = TimeUtils.printSince(tsStarted);
            }
            long tsCompleted = objToLong(stats.get("lastCompletedExchangeTimestamp"));
            if (tsCompleted > 0) {
                info.sinceLastCompleted = TimeUtils.printSince(tsCompleted);
            }
            long tsFailed = objToLong(stats.get("lastFailedExchangeTimestamp"));
            if (tsFailed > 0) {
                info.sinceLastFailed = TimeUtils.printSince(tsFailed);
            }
            Object reloaded = stats.get("reloaded");
            if (reloaded != null) {
                info.reloaded = reloaded.toString();
            }
        }

        JsonObject mem = (JsonObject) root.get("memory");
        if (mem != null) {
            info.heapMemUsed = mem.getLongOrDefault("heapMemoryUsed", 0L);
            info.heapMemMax = mem.getLongOrDefault("heapMemoryMax", 0L);
        }

        JsonObject threads = (JsonObject) root.get("threads");
        if (threads != null) {
            info.threadCount = threads.getIntegerOrDefault("threadCount", 0);
            info.peakThreadCount = threads.getIntegerOrDefault("peakThreadCount", 0);
        }

        // Parse routes
        JsonArray routes = (JsonArray) root.get("routes");
        if (routes != null) {
            for (Object r : routes) {
                JsonObject rj = (JsonObject) r;
                RouteInfo ri = new RouteInfo();
                ri.routeId = rj.getString("routeId");
                ri.from = rj.getString("from");
                ri.state = rj.getString("state");
                ri.uptime = rj.getString("uptime");

                Map<String, ?> rs = rj.getMap("statistics");
                if (rs != null) {
                    ri.coverage = objToString(rs.get("coverage"));
                    ri.throughput = objToString(rs.get("exchangesThroughput"));
                    ri.total = objToLong(rs.get("exchangesTotal"));
                    ri.failed = objToLong(rs.get("exchangesFailed"));
                    ri.inflight = objToLong(rs.get("exchangesInflight"));
                    ri.meanTime = objToLong(rs.get("meanProcessingTime"));
                    ri.minTime = objToLong(rs.get("minProcessingTime"));
                    ri.maxTime = objToLong(rs.get("maxProcessingTime"));
                    long tsStarted = objToLong(rs.get("lastCreatedExchangeTimestamp"));
                    if (tsStarted > 0) {
                        ri.sinceLastStarted = TimeUtils.printSince(tsStarted);
                    }
                    long tsCompleted = objToLong(rs.get("lastCompletedExchangeTimestamp"));
                    if (tsCompleted > 0) {
                        ri.sinceLastCompleted = TimeUtils.printSince(tsCompleted);
                    }
                    long tsFailed = objToLong(rs.get("lastFailedExchangeTimestamp"));
                    if (tsFailed > 0) {
                        ri.sinceLastFailed = TimeUtils.printSince(tsFailed);
                    }
                }

                // Parse processors
                JsonArray procs = (JsonArray) rj.get("processors");
                if (procs != null) {
                    for (Object p : procs) {
                        JsonObject pj = (JsonObject) p;
                        ProcessorInfo pi = new ProcessorInfo();
                        pi.id = pj.getString("id");
                        pi.processor = pj.getString("processor");
                        pi.level = pj.getIntegerOrDefault("level", 0);

                        Map<String, ?> ps = pj.getMap("statistics");
                        if (ps != null) {
                            pi.total = objToLong(ps.get("exchangesTotal"));
                            pi.failed = objToLong(ps.get("exchangesFailed"));
                            pi.meanTime = objToLong(ps.get("meanProcessingTime"));
                            pi.minTime = objToLong(ps.get("minProcessingTime"));
                            pi.maxTime = objToLong(ps.get("maxProcessingTime"));
                            pi.lastTime = objToLong(ps.get("lastProcessingTime"));
                        }

                        ri.processors.add(pi);
                    }
                }

                info.routes.add(ri);
            }
            info.routeTotal = info.routes.size();
            info.routeStarted = (int) info.routes.stream().filter(r -> "Started".equals(r.state)).count();
        }

        // Parse health checks and ready status
        JsonObject healthChecks = (JsonObject) root.get("healthChecks");
        if (healthChecks != null) {
            Boolean rdy = (Boolean) healthChecks.get("ready");
            info.ready = Boolean.TRUE.equals(rdy) ? "1/1" : "0/1";
            JsonArray checks = (JsonArray) healthChecks.get("checks");
            if (checks != null) {
                for (Object c : checks) {
                    JsonObject cj = (JsonObject) c;
                    HealthCheckInfo hc = new HealthCheckInfo();
                    hc.group = cj.getString("group");
                    hc.name = cj.getString("id");
                    hc.state = cj.getString("state");
                    hc.readiness = cj.getBooleanOrDefault("readiness", false);
                    hc.liveness = cj.getBooleanOrDefault("liveness", false);
                    // Extract message from details if available
                    JsonObject details = (JsonObject) cj.get("details");
                    if (details != null && details.containsKey("failure.error.message")) {
                        hc.message = details.getString("failure.error.message");
                    }
                    info.healthChecks.add(hc);
                }
            }
        }

        // Parse endpoints (top-level "endpoints" is a JsonObject with nested "endpoints" array)
        JsonObject endpointsObj = (JsonObject) root.get("endpoints");
        if (endpointsObj != null) {
            JsonArray endpointList = (JsonArray) endpointsObj.get("endpoints");
            if (endpointList != null) {
                for (Object e : endpointList) {
                    JsonObject ej = (JsonObject) e;
                    EndpointInfo ep = new EndpointInfo();
                    ep.uri = ej.getString("uri");
                    ep.direction = ej.getString("direction");
                    ep.routeId = ej.getString("routeId");
                    // Extract component from URI (e.g., "timer://tick" -> "timer")
                    if (ep.uri != null) {
                        int idx = ep.uri.indexOf(':');
                        ep.component = idx > 0 ? ep.uri.substring(0, idx) : ep.uri;
                    }
                    info.endpoints.add(ep);
                }
            }
        }

        return info;
    }

    // ---- Helpers ----

    private IntegrationInfo findSelectedIntegration() {
        if (selectedPid == null) {
            return null;
        }
        return data.get().stream()
                .filter(i -> selectedPid.equals(i.pid) && !i.vanishing)
                .findFirst().orElse(null);
    }

    private String selectedName() {
        IntegrationInfo info = findSelectedIntegration();
        return info != null ? truncate(info.name, 20) : "?";
    }

    private List<Long> findPids(String name) {
        return TuiHelper.findPids(name, this::getStatusFile);
    }

    private JsonObject loadStatus(long pid) {
        return TuiHelper.loadStatus(pid, this::getStatusFile);
    }

    private static long extractSince(ProcessHandle ph) {
        return ph.info().startInstant().map(Instant::toEpochMilli).orElse(0L);
    }

    private static String truncate(String s, int max) {
        return TuiHelper.truncate(s, max);
    }

    private static String formatMemory(long used, long max) {
        if (used <= 0) {
            return "";
        }
        String u = formatBytes(used);
        if (max > 0) {
            return u + "/" + formatBytes(max);
        }
        return u;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "K";
        }
        return (bytes / (1024 * 1024)) + "M";
    }

    private static String formatSinceLast(IntegrationInfo info) {
        return formatSinceLast(info.sinceLastStarted, info.sinceLastCompleted, info.sinceLastFailed);
    }

    private static String formatSinceLast(String started, String completed, String failed) {
        StringBuilder sb = new StringBuilder();
        if (started != null) {
            sb.append(started);
        }
        if (completed != null) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(completed);
        }
        if (failed != null) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(failed);
        }
        return sb.toString();
    }

    private static String formatThreads(int count, int peak) {
        if (count <= 0) {
            return "";
        }
        return count + "/" + peak;
    }

    private static String objToString(Object o) {
        return o != null ? o.toString() : "";
    }

    private static long objToLong(Object o) {
        return TuiHelper.objToLong(o);
    }

    // ---- Data Classes ----

    static class IntegrationInfo {
        String pid;
        String name;
        String camelVersion;
        String platform;
        String platformVersion;
        String profile;
        String ready;
        int state;
        long uptime;
        String ago;
        String throughput;
        long exchangesTotal;
        long failed;
        long inflight;
        String last;
        String delta;
        String sinceLastStarted;
        String sinceLastCompleted;
        String sinceLastFailed;
        String reloaded;
        int routeStarted;
        int routeTotal;
        long heapMemUsed;
        long heapMemMax;
        int threadCount;
        int peakThreadCount;
        boolean vanishing;
        long vanishStart;
        final List<RouteInfo> routes = new ArrayList<>();
        final List<HealthCheckInfo> healthChecks = new ArrayList<>();
        final List<EndpointInfo> endpoints = new ArrayList<>();
    }

    static class RouteInfo {
        String routeId;
        String from;
        String state;
        String uptime;
        String throughput;
        String coverage;
        long total;
        long failed;
        long inflight;
        long meanTime;
        long minTime;
        long maxTime;
        String sinceLastStarted;
        String sinceLastCompleted;
        String sinceLastFailed;
        final List<ProcessorInfo> processors = new ArrayList<>();
    }

    static class ProcessorInfo {
        String id;
        String processor;
        int level;
        long total;
        long failed;
        long meanTime;
        long minTime;
        long maxTime;
        long lastTime;
    }

    static class HealthCheckInfo {
        String group;
        String name;
        String state;
        boolean readiness;
        boolean liveness;
        String message;
    }

    static class EndpointInfo {
        String uri;
        String component;
        String direction;
        String routeId;
    }

    static class TraceEntry {
        String pid;
        String uid;
        String exchangeId;
        String timestamp;
        String routeId;
        String nodeId;
        String nodeShortName;
        String nodeLabel;
        String location;
        String processor;
        String direction;
        String status;
        String threadName;
        boolean first;
        boolean last;
        boolean failed;
        int nodeLevel;
        long elapsed;
        long epochMs;
        String body;
        String bodyType;
        String bodyPreview;
        Map<String, Object> headers;
        Map<String, String> headerTypes;
        Map<String, Object> exchangeProperties;
        Map<String, String> exchangePropertyTypes;
        Map<String, Object> exchangeVariables;
        Map<String, String> exchangeVariableTypes;
    }

    static class LogEntry {
        String raw;
        String time = "";
        String level = "INFO";
        String logger;
        String message = "";
    }

    static class HistoryEntry {
        String pid;
        String exchangeId;
        String timestamp;
        String routeId;
        String fromRouteId;
        String nodeId;
        String nodeShortName;
        String nodeLabel;
        String location;
        String processor;
        String direction;
        String threadName;
        boolean first;
        boolean last;
        boolean failed;
        int nodeLevel;
        long elapsed;
        long epochMs;
        String body;
        String bodyType;
        String exception;
        Map<String, Object> headers;
        Map<String, String> headerTypes;
        Map<String, Object> exchangeProperties;
        Map<String, String> exchangePropertyTypes;
        Map<String, Object> exchangeVariables;
        Map<String, String> exchangeVariableTypes;
    }

    record VanishingInfo(IntegrationInfo info, long startTime) {
    }
}
