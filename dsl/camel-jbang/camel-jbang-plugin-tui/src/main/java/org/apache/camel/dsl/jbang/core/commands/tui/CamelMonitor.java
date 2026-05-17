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
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.barchart.Bar;
import dev.tamboui.widgets.barchart.BarChart;
import dev.tamboui.widgets.barchart.BarGroup;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
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
    private static final int MAX_LOG_LINES = 5000;
    private static final int MAX_TRACES = 200;
    private static final int NUM_TABS = 9;

    // Tab indices
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_LOG = 1;
    private static final int TAB_ROUTES = 2;
    private static final int TAB_CONSUMERS = 3;
    private static final int TAB_ENDPOINTS = 4;
    private static final int TAB_CIRCUIT_BREAKER = 5;
    private static final int TAB_HEALTH = 6;
    private static final int TAB_HISTORY = 7;
    private static final int TAB_TRACE = 8;

    // Overview sort columns
    private static final String[] OVERVIEW_SORT_COLUMNS = { "pid", "name", "status", "total", "fail" };

    // Route sort columns
    private static final String[] ROUTE_SORT_COLUMNS = { "name", "group", "status", "total", "failed" };

    // Consumer sort columns (order matches table column order)
    private static final String[] CONSUMER_SORT_COLUMNS = { "id", "status", "type", "inflight", "total", "uri" };

    // Endpoint sort columns (order matches table column order)
    private static final String[] ENDPOINT_SORT_COLUMNS = { "component", "route", "dir", "total", "uri" };

    // Circuit breaker sort columns (order matches table column order)
    private static final String[] CB_SORT_COLUMNS = { "route", "id", "component", "state" };

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
    private final TableState consumerTableState = new TableState();
    private final TableState healthTableState = new TableState();
    private final TableState endpointTableState = new TableState();
    private final TableState cbTableState = new TableState();
    private final TableState processorTableState = new TableState();
    private final TableState routeHeaderTableState = new TableState();
    private final TabsState tabsState = new TabsState(TAB_OVERVIEW);

    // Sparkline: throughput history per PID (one point per second)
    private final Map<String, LinkedList<Long>> throughputHistory = new ConcurrentHashMap<>();
    // Sparkline: failed throughput history per PID (one point per second)
    private final Map<String, LinkedList<Long>> failedHistory = new ConcurrentHashMap<>();
    // Sliding window of [timestamp, exchangesTotal, exchangesFailed] samples for smoothing
    private final Map<String, LinkedList<long[]>> throughputSamples = new ConcurrentHashMap<>();
    // Track last time a sparkline point was recorded
    private final Map<String, Long> previousExchangesTime = new ConcurrentHashMap<>();

    // Overview sort state
    private String overviewSort = "name";
    private int overviewSortIndex = 1;

    // Route sort state
    private String routeSort = "name";
    private int routeSortIndex = 0;

    // Consumer sort state (default: id = index 0)
    private String consumerSort = "id";
    private int consumerSortIndex = 0;

    // Endpoint sort state (default: route = index 1)
    private String endpointSort = "route";
    private int endpointSortIndex = 1;

    // Endpoint filter state
    private boolean showOnlyRemote;

    // Circuit breaker sort state (default: route = index 0)
    private String cbSort = "route";
    private int cbSortIndex = 0;

    // Health filter state
    private boolean showOnlyDown;

    // Log state
    private volatile List<LogEntry> filteredLogEntries = new ArrayList<>();
    // Incremental log tail state — persisted across refresh cycles
    private long logFilePos = -1;
    private String logFilePid;
    private final StringBuilder logLineBuffer = new StringBuilder();
    private final List<LogEntry> mutableFilteredEntries = new ArrayList<>();
    // Render-side Line cache — rebuilt only when entries or hSkip changes
    private List<LogEntry> cachedLogEntries;
    private int cachedLogHSkip = -1;
    private int cachedLogMaxWidth;
    private List<Line> cachedLogLines = Collections.emptyList();
    private int logScroll;
    private final ScrollbarState logScrollState = new ScrollbarState();
    private boolean logFollowMode = true;
    private boolean logWordWrap = true;
    private int logHScroll;

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
    private boolean traceWordWrap = true;
    private int traceDetailScroll;
    private int traceDetailHScroll;

    // History state
    private volatile List<HistoryEntry> historyEntries = Collections.emptyList();
    private final TableState historyTableState = new TableState();
    private boolean showHistoryProperties;
    private boolean showHistoryVariables;
    private boolean showHistoryHeaders = true;
    private boolean showHistoryBody = true;
    private boolean historyWordWrap = true;
    private int historyDetailScroll;
    private int historyDetailHScroll;

    // Selected integration for detail views
    private String selectedPid;

    // Diagram state
    private boolean chartAllIntegrations = true;
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
    private boolean showSource;
    private List<String> sourceLines = Collections.emptyList();
    private String sourceTitle;
    private int sourceScroll;
    private int sourceScrollX;
    private final ScrollbarState sourceVScrollState = new ScrollbarState();
    private final ScrollbarState sourceHScrollState = new ScrollbarState();
    private final AtomicBoolean sourceLoading = new AtomicBoolean(false);

    private static final String[] LOG_LEVELS = { "ERROR", "WARN", "INFO", "DEBUG", "TRACE" };
    private boolean showLogLevelPopup;
    private final ListState logLevelListState = new ListState();

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
                if (showLogLevelPopup) {
                    showLogLevelPopup = false;
                    return true;
                }
                if (showSource) {
                    showSource = false;
                    return true;
                }
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
                return handleTabKey(TAB_LOG);
            }
            if (ke.isChar('3')) {
                return handleTabKey(TAB_ROUTES);
            }
            if (ke.isChar('4')) {
                return handleTabKey(TAB_CONSUMERS);
            }
            if (ke.isChar('5')) {
                return handleTabKey(TAB_ENDPOINTS);
            }
            if (ke.isChar('6')) {
                return handleTabKey(TAB_CIRCUIT_BREAKER);
            }
            if (ke.isChar('7')) {
                return handleTabKey(TAB_HEALTH);
            }
            if (ke.isChar('8')) {
                return handleTabKey(TAB_HISTORY);
            }
            if (ke.isChar('9')) {
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
                if (tab == TAB_LOG && showLogLevelPopup) {
                    logLevelListState.selectPrevious();
                } else if (showDiagram && tab == TAB_ROUTES) {
                    diagramScroll = Math.max(0, diagramScroll - 1);
                } else {
                    navigateUp();
                }
                return true;
            }
            if (ke.isDown()) {
                if (tab == TAB_LOG && showLogLevelPopup) {
                    logLevelListState.selectNext(LOG_LEVELS.length);
                } else if (showDiagram && tab == TAB_ROUTES) {
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
                    logScroll = Math.max(0, logScroll - 20);
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
                    logScroll += 20;
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
                } else if (tab == TAB_TRACE && traceDetailView && !traceWordWrap) {
                    traceDetailHScroll = Math.max(0, traceDetailHScroll - 4);
                    return true;
                } else if (tab == TAB_HISTORY && !historyWordWrap) {
                    historyDetailHScroll = Math.max(0, historyDetailHScroll - 4);
                    return true;
                }
            }
            if (ke.isRight()) {
                if (showDiagram && tab == TAB_ROUTES) {
                    diagramScrollX++;
                    return true;
                } else if (tab == TAB_TRACE && traceDetailView && !traceWordWrap) {
                    traceDetailHScroll += 4;
                    return true;
                } else if (tab == TAB_HISTORY && !historyWordWrap) {
                    historyDetailHScroll += 4;
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
                    tabsState.select(TAB_LOG);
                }
                return true;
            }

            // Overview tab: sort
            if (tab == TAB_OVERVIEW && ke.isCharIgnoreCase('s')) {
                overviewSortIndex = (overviewSortIndex + 1) % OVERVIEW_SORT_COLUMNS.length;
                overviewSort = OVERVIEW_SORT_COLUMNS[overviewSortIndex];
                return true;
            }
            // Overview tab: toggle chart between all integrations and selected only
            if (tab == TAB_OVERVIEW && ke.isCharIgnoreCase('a')) {
                chartAllIntegrations = !chartAllIntegrations;
                return true;
            }
            // Overview tab: start/stop all routes for selected integration
            if (tab == TAB_OVERVIEW && ke.isChar('p') && selectedPid != null) {
                IntegrationInfo selInfo = findSelectedIntegration();
                if (selInfo != null) {
                    String cmd = selInfo.routeStarted > 0 ? "stop" : "start";
                    sendRouteCommand(selectedPid, "*", cmd);
                }
                return true;
            }

            // Consumers tab: sort
            if (tab == TAB_CONSUMERS && ke.isCharIgnoreCase('s')) {
                consumerSortIndex = (consumerSortIndex + 1) % CONSUMER_SORT_COLUMNS.length;
                consumerSort = CONSUMER_SORT_COLUMNS[consumerSortIndex];
                return true;
            }

            // Circuit breaker tab: sort
            if (tab == TAB_CIRCUIT_BREAKER && ke.isCharIgnoreCase('s')) {
                cbSortIndex = (cbSortIndex + 1) % CB_SORT_COLUMNS.length;
                cbSort = CB_SORT_COLUMNS[cbSortIndex];
                return true;
            }

            // Endpoints tab: sort and filter
            if (tab == TAB_ENDPOINTS && ke.isCharIgnoreCase('s')) {
                endpointSortIndex = (endpointSortIndex + 1) % ENDPOINT_SORT_COLUMNS.length;
                endpointSort = ENDPOINT_SORT_COLUMNS[endpointSortIndex];
                return true;
            }
            if (tab == TAB_ENDPOINTS && ke.isCharIgnoreCase('r')) {
                showOnlyRemote = !showOnlyRemote;
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

            if (tab == TAB_ROUTES && ke.isChar('c')) {
                if (showSource) {
                    showSource = false;
                } else {
                    loadSourceForSelectedRoute();
                }
                return true;
            }
            if (tab == TAB_ROUTES && !showSource && !showDiagram && ke.isChar('p')) {
                toggleRouteStartStop();
                return true;
            }
            if (tab == TAB_ROUTES && !showSource && !showDiagram && ke.isChar('P')) {
                toggleRouteSuspendResume();
                return true;
            }
            if (tab == TAB_ROUTES && showSource) {
                if (ke.isUp()) {
                    sourceScroll = Math.max(0, sourceScroll - 1);
                } else if (ke.isDown()) {
                    sourceScroll++;
                } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                    sourceScroll = Math.max(0, sourceScroll - 20);
                } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                    sourceScroll += 20;
                } else if (ke.isLeft()) {
                    sourceScrollX = Math.max(0, sourceScrollX - 1);
                } else if (ke.isRight()) {
                    sourceScrollX++;
                } else if (ke.isHome()) {
                    sourceScroll = 0;
                    sourceScrollX = 0;
                } else if (ke.isEnd()) {
                    sourceScroll = Integer.MAX_VALUE;
                } else {
                    return false;
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

            // Log tab: log level popup — absorb all keys except Enter to confirm
            if (tab == TAB_LOG && showLogLevelPopup) {
                if (ke.isConfirm()) {
                    Integer sel = logLevelListState.selected();
                    if (sel != null && sel >= 0 && sel < LOG_LEVELS.length && selectedPid != null) {
                        sendLoggerLevelAction(selectedPid, LOG_LEVELS[sel]);
                    }
                    showLogLevelPopup = false;
                }
                return true;
            }

            // Log tab: follow mode, word wrap, horizontal scroll
            if (tab == TAB_LOG) {
                if (ke.isChar('l')) {
                    showLogLevelPopup = true;
                    // pre-select INFO as a sensible default
                    logLevelListState.select(2);
                    return true;
                }
                if (ke.isCharIgnoreCase('f')) {
                    logFollowMode = !logFollowMode;
                    return true;
                }
                if (ke.isCharIgnoreCase('w')) {
                    logWordWrap = !logWordWrap;
                    logHScroll = 0;
                    return true;
                }
                if (!logWordWrap) {
                    if (ke.isLeft()) {
                        logFollowMode = false;
                        logHScroll = Math.max(0, logHScroll - 4);
                        return true;
                    }
                    if (ke.isRight()) {
                        logFollowMode = false;
                        logHScroll += 4;
                        return true;
                    }
                }
                if (ke.isHome()) {
                    logFollowMode = false;
                    logScroll = 0;
                    logHScroll = 0;
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
                        traceDetailHScroll = 0;
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
                    historyDetailHScroll = 0;
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

    private void syncSelectedPidFromOverview() {
        List<IntegrationInfo> infos = data.get().stream().filter(i -> !i.vanishing).toList();
        Integer sel = overviewTableState.selected();
        String newPid = null;
        if (sel != null && sel >= 0 && sel < infos.size()) {
            newPid = infos.get(sel).pid;
        } else if (infos.size() == 1) {
            newPid = infos.get(0).pid;
        }
        if (newPid != null && !newPid.equals(selectedPid)) {
            selectedPid = newPid;
            resetIntegrationTabState();
            List<Long> pids = List.of(Long.parseLong(selectedPid));
            refreshHistoryData(pids);
            traceFilePositions.clear();
            traces.set(Collections.emptyList());
            refreshTraceData(pids);
        }
    }

    // NOTE: When adding a new tab, reset its view state here too so switching integrations
    // on the Overview always shows a clean slate for the newly selected integration.
    private void resetIntegrationTabState() {
        // Source (TAB_ROUTES)
        showSource = false;
        sourceLines = Collections.emptyList();
        sourceTitle = null;
        sourceScroll = 0;
        sourceScrollX = 0;
        // Diagram (TAB_ROUTES)
        showDiagram = false;
        diagramImageData = null;
        diagramFullImageData = null;
        diagramLines = Collections.emptyList();
        diagramScroll = 0;
        diagramScrollX = 0;
        // Routes (TAB_ROUTES)
        routeTableState.select(0);
        // Log (TAB_LOG)
        logScroll = 0;
        logFollowMode = true;
        showLogLevelPopup = false;
        mutableFilteredEntries.clear();
        filteredLogEntries = Collections.emptyList();
        cachedLogEntries = null;
        logFilePos = -1;
        logLineBuffer.setLength(0);
        // Trace (TAB_TRACE)
        traceDetailView = false;
        traceSelectedExchangeId = null;
        traceDetailScroll = 0;
    }

    private void navigateUp() {
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> {
                overviewTableState.selectPrevious();
                syncSelectedPidFromOverview();
            }
            case TAB_ROUTES -> routeTableState.selectPrevious();
            case TAB_CONSUMERS -> consumerTableState.selectPrevious();
            case TAB_HEALTH -> healthTableState.selectPrevious();
            case TAB_ENDPOINTS -> endpointTableState.selectPrevious();
            case TAB_CIRCUIT_BREAKER -> cbTableState.selectPrevious();
            case TAB_LOG -> {
                logFollowMode = false;
                logScroll = Math.max(0, logScroll - 1);
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
            case TAB_OVERVIEW -> {
                overviewTableState.selectNext(infos.size());
                syncSelectedPidFromOverview();
            }
            case TAB_ROUTES -> {
                IntegrationInfo info = findSelectedIntegration();
                routeTableState.selectNext(info != null ? info.routes.size() : 0);
            }
            case TAB_CONSUMERS -> {
                IntegrationInfo info = findSelectedIntegration();
                consumerTableState.selectNext(info != null ? info.consumers.size() : 0);
            }
            case TAB_HEALTH -> {
                IntegrationInfo info = findSelectedIntegration();
                healthTableState.selectNext(info != null ? getFilteredHealthChecks(info).size() : 0);
            }
            case TAB_ENDPOINTS -> {
                IntegrationInfo info = findSelectedIntegration();
                endpointTableState.selectNext(info != null ? info.endpoints.size() : 0);
            }
            case TAB_CIRCUIT_BREAKER -> {
                IntegrationInfo info = findSelectedIntegration();
                cbTableState.selectNext(info != null ? info.circuitBreakers.size() : 0);
            }
            case TAB_LOG -> logScroll++;
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
        int consumerCount = hasSelection ? sel.consumers.size() : 0;
        int endpointCount = hasSelection ? sel.endpoints.size() : 0;
        int cbCount = hasSelection ? sel.circuitBreakers.size() : 0;
        long cbOpenCount = hasSelection
                ? sel.circuitBreakers.stream()
                        .filter(cb -> cb.state != null && (cb.state.equalsIgnoreCase("open")
                                || cb.state.equalsIgnoreCase("forced_open")))
                        .count()
                : 0;
        int healthCount = hasSelection ? sel.healthChecks.size() : 0;
        long healthDownCount = hasSelection
                ? sel.healthChecks.stream().filter(hc -> "DOWN".equals(hc.state)).count() : 0;
        int historyCount = hasSelection ? historyEntries.size() : 0;
        boolean hasTraces = hasSelection && !traces.get().isEmpty();

        Tabs tabs = Tabs.builder()
                .titles(
                        badge(" 1 Overview ", activeCount),
                        Line.from(" 2 Log "),
                        badge(" 3 Routes ", routeCount),
                        badge(" 4 Consumers ", consumerCount),
                        badge(" 5 Endpoints ", endpointCount),
                        badgeCb(" 6 Circuit Breaker ", cbCount, cbOpenCount),
                        badgeHealth(" 7 Health ", healthCount, healthDownCount),
                        badge(" 8 History ", historyCount),
                        hasTraces
                                ? Line.from(Span.raw(" 9 Trace "), Span.styled("(*)", Style.EMPTY.fg(Color.YELLOW).bold()),
                                        Span.raw(" "))
                                : Line.from(" 9 Trace "))
                .highlightStyle(Style.EMPTY.fg(Color.rgb(0xF6, 0x91, 0x23)).bold())
                .divider(Span.styled(" | ", Style.EMPTY.dim()))
                .build();

        frame.renderStatefulWidget(tabs, area, tabsState);
    }

    private void renderContent(Frame frame, Rect area) {
        // Clear the content area before rendering the active tab. Without this, styled cells
        // from the previous tab (e.g. RED error text in the log detail) can bleed through when
        // switching tabs if TamboUI's buffer diff does not reset every cell in the region.
        frame.buffer().clear(area);
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> renderOverview(frame, area);
            case TAB_ROUTES -> renderRoutes(frame, area);
            case TAB_CONSUMERS -> renderConsumers(frame, area);
            case TAB_ENDPOINTS -> renderEndpoints(frame, area);
            case TAB_CIRCUIT_BREAKER -> renderCircuitBreaker(frame, area);
            case TAB_HEALTH -> renderHealth(frame, area);
            case TAB_LOG -> renderLog(frame, area);
            case TAB_TRACE -> renderTrace(frame, area);
            case TAB_HISTORY -> renderHistory(frame, area);
        }
    }

    // ---- Tab 1: Overview ----

    private void renderOverview(Frame frame, Rect area) {
        List<IntegrationInfo> infos = new ArrayList<>(data.get());
        infos.sort(this::sortOverview);

        // Split: table (fill) + chart (14 rows: 13 chart + 1 x-axis) if we have data
        boolean hasSparkline = !throughputHistory.isEmpty();
        List<Rect> chunks;
        if (hasSparkline) {
            chunks = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(14))
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
                        Cell.from(Span.styled("\u2716 Stopped", Style.EMPTY.fg(Color.LIGHT_RED).dim())),
                        Cell.from(Span.styled(info.ago != null ? info.ago : "", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle))));
            } else {
                Style statusStyle = switch (extractState(info.state)) {
                    case "Started", "Running" -> Style.EMPTY.fg(Color.GREEN);
                    case "Stopped" -> Style.EMPTY.fg(Color.LIGHT_RED);
                    default -> Style.EMPTY.fg(Color.YELLOW);
                };

                Style failStyle = info.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY;

                String sinceLastDisplay = formatSinceLast(info);

                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(Span.styled(info.name != null ? info.name : "", Style.EMPTY.fg(Color.CYAN))),
                        Cell.from(info.camelVersion != null ? info.camelVersion : ""),
                        centerCell(info.ready != null ? info.ready : "", 5),
                        Cell.from(Span.styled(extractState(info.state), statusStyle)),
                        Cell.from(info.ago != null ? info.ago : ""),
                        rightCell(info.routeStarted + "/" + info.routeTotal, 7),
                        rightCell(info.throughput != null ? info.throughput : "", 8),
                        rightCell(String.valueOf(info.exchangesTotal), 8),
                        rightCell(String.valueOf(info.failed), 6, failStyle),
                        rightCell(String.valueOf(info.inflight), 8),
                        Cell.from(sinceLastDisplay)));
            }
        }

        Row header = Row.from(
                Cell.from(Span.styled(overviewSortLabel("PID", "pid"), overviewSortStyle("pid"))),
                Cell.from(Span.styled(overviewSortLabel("NAME", "name"), overviewSortStyle("name"))),
                Cell.from(Span.styled("VERSION", Style.EMPTY.bold())),
                centerCell("READY", 5, Style.EMPTY.bold()),
                Cell.from(Span.styled(overviewSortLabel("STATUS", "status"), overviewSortStyle("status"))),
                Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                rightCell("ROUTE", 7, Style.EMPTY.bold()),
                rightCell("MSG/S", 8, Style.EMPTY.bold()),
                rightCell(overviewSortLabel("TOTAL", "total"), 8, overviewSortStyle("total")),
                rightCell(overviewSortLabel("FAIL", "fail"), 6, overviewSortStyle("fail")),
                rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold())));

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
                        Constraint.length(12))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Integrations ").build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), overviewTableState);

        // Split green/red throughput bar chart with Y and X axes
        if (hasSparkline && chunks.size() > 1) {
            Rect chartTotalArea = chunks.get(1);

            // Split chart area horizontally: bar chart (fill) + info panel (30 cols)
            List<Rect> chartHSplit = Layout.horizontal()
                    .constraints(Constraint.fill(), Constraint.length(30))
                    .split(chartTotalArea);
            Rect chartArea = chartHSplit.get(0);
            Rect infoArea = chartHSplit.get(1);

            // Split chart area: chart rows (13) + x-axis label row (1)
            List<Rect> vChunks = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(chartArea);

            // Split chart rows: y-axis labels (4 cols) + bar chart (fill)
            List<Rect> hChunks = Layout.horizontal()
                    .constraints(Constraint.length(4), Constraint.fill())
                    .split(vChunks.get(0));

            Rect barChartArea = hChunks.get(1);

            // Compute how many ticks fit: each tick = 2 bars × barWidth=1 = 2 cols
            int innerBarCols = Math.max(2, barChartArea.width() - 2); // minus block borders
            int renderPoints = Math.min(MAX_SPARKLINE_POINTS, innerBarCols / 2);

            // Merge throughput histories: all PIDs or selected only
            long[] mergedTotal = new long[renderPoints];
            long[] mergedFailed = new long[renderPoints];
            String chartPid = (!chartAllIntegrations && selectedPid != null) ? selectedPid : null;
            for (int i = 0; i < renderPoints; i++) {
                for (Map.Entry<String, LinkedList<Long>> e : throughputHistory.entrySet()) {
                    if (chartPid == null || chartPid.equals(e.getKey())) {
                        int idx = e.getValue().size() - renderPoints + i;
                        if (idx >= 0) {
                            mergedTotal[i] += e.getValue().get(idx);
                        }
                    }
                }
                for (Map.Entry<String, LinkedList<Long>> e : failedHistory.entrySet()) {
                    if (chartPid == null || chartPid.equals(e.getKey())) {
                        int idx = e.getValue().size() - renderPoints + i;
                        if (idx >= 0) {
                            mergedFailed[i] += e.getValue().get(idx);
                        }
                    }
                }
            }

            long maxTp = 0;
            for (long v : mergedTotal) {
                maxTp = Math.max(maxTp, v);
            }
            long curTp = mergedTotal[renderPoints - 1];
            long curFailed = mergedFailed[renderPoints - 1];
            long curOk = Math.max(0, curTp - curFailed);

            // Styled legend in chart title
            Line titleLine;
            if (!chartAllIntegrations && selectedPid != null) {
                IntegrationInfo chartSel = findSelectedIntegration();
                String chartName = chartSel != null ? TuiHelper.truncate(chartSel.name, 12) : selectedPid;
                titleLine = Line.from(
                        Span.raw(" ["),
                        Span.styled(chartName, Style.EMPTY.fg(Color.YELLOW)),
                        Span.raw(String.format("] Throughput: %d msg/s  ", curTp)),
                        Span.styled("■", Style.EMPTY.fg(Color.GREEN)),
                        Span.raw(String.format(" ok:%d  ", curOk)),
                        Span.styled("■", Style.EMPTY.fg(Color.RED)),
                        Span.raw(String.format(" fail:%d ", curFailed)));
            } else {
                titleLine = Line.from(
                        Span.raw(String.format(" [All] Throughput: %d msg/s  ", curTp)),
                        Span.styled("■", Style.EMPTY.fg(Color.GREEN)),
                        Span.raw(String.format(" ok:%d  ", curOk)),
                        Span.styled("■", Style.EMPTY.fg(Color.RED)),
                        Span.raw(String.format(" fail:%d ", curFailed)));
            }

            // Build bar groups (ok=green, failed=red), no bar value labels
            List<BarGroup> groups = new ArrayList<>();
            for (int i = 0; i < renderPoints; i++) {
                long failed = Math.min(mergedFailed[i], mergedTotal[i]);
                long ok = Math.max(0, mergedTotal[i] - failed);
                groups.add(BarGroup.of(
                        Bar.builder().value(ok).textValue("").style(Style.EMPTY.fg(Color.GREEN)).build(),
                        Bar.builder().value(failed).textValue("").style(Style.EMPTY.fg(Color.RED)).build()));
            }

            BarChart barChart = BarChart.builder()
                    .data(groups)
                    .max(maxTp > 0 ? maxTp + 2 : 2)
                    .barWidth(1)
                    .barGap(0)
                    .groupGap(0)
                    .block(Block.builder().borderType(BorderType.ROUNDED)
                            .title(Title.from(titleLine)).build())
                    .build();

            frame.renderWidget(barChart, barChartArea);

            // Y-axis: scale labels aligned with bar chart inner rows
            int barRows = vChunks.get(0).height() - 2; // minus top + bottom border
            List<Line> yLines = new ArrayList<>();
            Style dimStyle = Style.EMPTY.dim();
            for (int row = 0; row < vChunks.get(0).height(); row++) {
                int barRow = row - 1; // bar area starts after top border
                if (barRow == 0) {
                    yLines.add(Line.from(Span.styled(String.format("%3d", maxTp), dimStyle)));
                } else if (barRows > 4 && barRow == barRows / 2) {
                    yLines.add(Line.from(Span.styled(String.format("%3d", maxTp / 2), dimStyle)));
                } else if (barRow == barRows - 1) {
                    yLines.add(Line.from(Span.styled("  0", dimStyle)));
                } else {
                    yLines.add(Line.from(""));
                }
            }
            frame.renderWidget(Paragraph.builder().text(Text.from(yLines)).build(), hChunks.get(0));

            // X-axis: time labels drawn into the bottom row
            if (!vChunks.get(1).isEmpty()) {
                int barInnerStartX = barChartArea.x() + 1; // inside left border
                int xAxisY = vChunks.get(1).y();
                // Markers at: oldest, 1/4, 1/2, 3/4, newest
                int[][] markerIndices = {
                        { 0, renderPoints },
                        { renderPoints / 4, renderPoints - renderPoints / 4 },
                        { renderPoints / 2, renderPoints / 2 },
                        { 3 * renderPoints / 4, renderPoints / 4 },
                        { renderPoints - 1, 0 }
                };
                for (int[] m : markerIndices) {
                    int groupIdx = m[0];
                    int secsAgo = m[1];
                    String label = secsAgo == 0 ? "now" : "-" + secsAgo + "s";
                    int markerX = barInnerStartX + groupIdx * 2;
                    if (markerX + label.length() <= barChartArea.right()) {
                        frame.buffer().setString(markerX, xAxisY, label, dimStyle);
                    }
                }
            }

            // Info panel: heap and threads for the selected integration
            renderOverviewInfoPanel(frame, infoArea);
        }
    }

    private void renderOverviewInfoPanel(Frame frame, Rect area) {
        IntegrationInfo sel = findSelectedIntegration();
        // Fall back to the single active integration when nothing is explicitly selected
        if (sel == null) {
            List<IntegrationInfo> active = data.get().stream().filter(i -> !i.vanishing).toList();
            if (active.size() == 1) {
                sel = active.get(0);
            }
        }
        Block infoBlock = Block.builder().borderType(BorderType.ROUNDED).build();
        frame.renderWidget(infoBlock, area);
        Rect inner = infoBlock.inner(area);
        List<Line> lines = new ArrayList<>();
        Style dim = Style.EMPTY.dim();
        if (sel != null) {
            // Identity
            if (sel.platform != null) {
                String plat = sel.platformVersion != null
                        ? sel.platform + "/" + sel.platformVersion
                        : sel.platform;
                lines.add(Line.from(
                        Span.styled("Runtime: ", dim),
                        Span.raw(TuiHelper.truncate(plat, inner.width() - 9))));
            }
            if (sel.camelVersion != null) {
                lines.add(Line.from(
                        Span.styled("Version: ", dim),
                        Span.raw(TuiHelper.truncate(sel.camelVersion, inner.width() - 9))));
            }
            lines.add(Line.from(Span.raw("")));
            // Resources
            if (sel.javaVersion != null) {
                lines.add(Line.from(
                        Span.styled("JVM:  ", dim),
                        Span.raw(TuiHelper.truncate(sel.javaVersion, inner.width() - 6))));
            }
            if (sel.javaVendor != null) {
                lines.add(Line.from(
                        Span.styled("      ", dim),
                        Span.raw(TuiHelper.truncate(sel.javaVendor, inner.width() - 6))));
            }
            if (sel.javaVmName != null) {
                lines.add(Line.from(
                        Span.styled("      ", dim),
                        Span.raw(TuiHelper.truncate(sel.javaVmName, inner.width() - 6))));
            }
            lines.add(Line.from(
                    Span.styled("Uptime: ", dim),
                    Span.raw(sel.ago != null ? sel.ago : "-")));
            if (sel.heapMemUsed > 0) {
                String heap = formatMemory(sel.heapMemUsed, sel.heapMemMax);
                long pct = sel.heapMemMax > 0 ? sel.heapMemUsed * 100 / sel.heapMemMax : 0;
                lines.add(Line.from(
                        Span.styled("Heap: ", dim),
                        Span.raw(heap + " " + pct + "%")));
            }
            if (sel.nonHeapMemUsed > 0) {
                lines.add(Line.from(
                        Span.styled("Meta: ", dim),
                        Span.raw(formatMemory(sel.nonHeapMemUsed, 0))));
            }
            if (sel.threadCount > 0) {
                lines.add(Line.from(
                        Span.styled("Thds: ", dim),
                        Span.raw(sel.threadCount + " / " + sel.peakThreadCount)));
            }
        } else {
            lines.add(Line.from(Span.raw("-")));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), inner);
    }

    // ---- Tab 2: Routes ----

    private void renderRoutes(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Fullscreen source view
        if (showSource) {
            List<Rect> fullChunks = Layout.vertical()
                    .constraints(Constraint.length(4), Constraint.fill())
                    .split(area);
            renderRouteHeader(frame, fullChunks.get(0), info);
            renderSource(frame, fullChunks.get(1));
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
                    : Style.EMPTY.fg(Color.LIGHT_RED);

            Style failStyle = route.failed > 0
                    ? Style.EMPTY.fg(Color.LIGHT_RED).bold()
                    : Style.EMPTY;

            String sinceLastRoute = formatSinceLastRoute(route);

            routeRows.add(Row.from(
                    Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(route.group != null ? route.group : "", Style.EMPTY.dim())),
                    Cell.from(route.from != null ? route.from : ""),
                    Cell.from(Span.styled(route.state != null ? route.state : "", stateStyle)),
                    Cell.from(route.uptime != null ? route.uptime : ""),
                    rightCell(route.coverage != null ? route.coverage : "", 6),
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
                        Cell.from(Span.styled(routeSortLabel("GROUP", "group"), routeSortStyle("group"))),
                        Cell.from(Span.styled("FROM", Style.EMPTY.bold())),
                        Cell.from(Span.styled(routeSortLabel("STATUS", "status"), routeSortStyle("status"))),
                        Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                        rightCell("COVER", 6, Style.EMPTY.bold()),
                        rightCell("MSG/S", 8, Style.EMPTY.bold()),
                        rightCell(routeSortLabel("TOTAL", "total"), 8, routeSortStyle("total")),
                        rightCell(routeSortLabel("FAIL", "failed"), 6, routeSortStyle("failed")),
                        rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                        rightCell("MIN/MAX/MEAN", 14, Style.EMPTY.bold()),
                        Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(14),
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
            case "group" -> {
                String ga = a.group != null ? a.group : "";
                String gb = b.group != null ? b.group : "";
                yield ga.compareToIgnoreCase(gb);
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

    private String consumerSortLabel(String label, String column) {
        return sortLabel(label, column, consumerSort);
    }

    private Style consumerSortStyle(String column) {
        return sortStyle(column, consumerSort);
    }

    private int sortConsumer(ConsumerInfo a, ConsumerInfo b) {
        return switch (consumerSort) {
            case "status" -> {
                String sa = consumerStatus(a);
                String sb = consumerStatus(b);
                yield sa.compareToIgnoreCase(sb);
            }
            case "type" -> {
                String ta = consumerType(a);
                String tb = consumerType(b);
                yield ta.compareToIgnoreCase(tb);
            }
            case "inflight" -> Integer.compare(b.inflight, a.inflight);
            case "total" -> {
                long la = a.totalCounter != null ? a.totalCounter : 0;
                long lb = b.totalCounter != null ? b.totalCounter : 0;
                yield Long.compare(lb, la);
            }
            case "uri" -> {
                String ua = a.uri != null ? a.uri : "";
                String ub = b.uri != null ? b.uri : "";
                yield ua.compareToIgnoreCase(ub);
            }
            default -> { // "id"
                String ia = a.id != null ? a.id : "";
                String ib = b.id != null ? b.id : "";
                yield ia.compareToIgnoreCase(ib);
            }
        };
    }

    private static String consumerStatus(ConsumerInfo ci) {
        if (ci.polling != null && ci.polling) {
            return "Polling";
        }
        return ci.state != null ? ci.state : "";
    }

    private static String consumerType(ConsumerInfo ci) {
        if (ci.className == null) {
            return "";
        }
        String s = ci.className;
        if (s.endsWith("Consumer")) {
            s = s.substring(0, s.length() - 8);
        }
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private static HealthCheckInfo consumerHealthCheck(IntegrationInfo info, ConsumerInfo ci) {
        if (ci.id == null) {
            return null;
        }
        String hcId = "consumer:" + ci.id;
        for (HealthCheckInfo hc : info.healthChecks) {
            if (hcId.equals(hc.name)) {
                return hc;
            }
        }
        return null;
    }

    private static String consumerPeriod(ConsumerInfo ci) {
        if (ci.period != null) {
            return ci.period + "ms";
        } else if (ci.delay != null) {
            return ci.delay + "ms";
        }
        return "";
    }

    private static String consumerSinceLast(ConsumerInfo ci) {
        String s1 = ci.sinceLastStarted != null ? ci.sinceLastStarted : "-";
        String s2 = ci.sinceLastCompleted != null ? ci.sinceLastCompleted : "-";
        String s3 = ci.sinceLastFailed != null ? ci.sinceLastFailed : "-";
        return s1 + "/" + s2 + "/" + s3;
    }

    private void renderConsumers(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<ConsumerInfo> sorted = new ArrayList<>(info.consumers);
        sorted.sort(this::sortConsumer);

        List<Row> rows = new ArrayList<>();
        for (ConsumerInfo ci : sorted) {
            String status = consumerStatus(ci);
            HealthCheckInfo hc = consumerHealthCheck(info, ci);
            boolean healthDown = hc != null && "DOWN".equals(hc.state);
            Style statusStyle = healthDown
                    ? Style.EMPTY.fg(Color.LIGHT_RED)
                    : ("Started".equals(ci.state) || "Polling".equals(status)
                            ? Style.EMPTY.fg(Color.GREEN)
                            : Style.EMPTY.fg(Color.LIGHT_RED));
            String statusText = healthDown ? "⚠ " + status : status;
            String type = consumerType(ci);
            String period = consumerPeriod(ci);
            String sinceLast = consumerSinceLast(ci);
            String uri = healthDown && hc.message != null
                    ? hc.message
                    : (ci.uri != null ? ci.uri : "");

            rows.add(Row.from(
                    Cell.from(Span.styled(ci.id != null ? ci.id : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(statusText, statusStyle)),
                    Cell.from(type),
                    rightCell(ci.inflight > 0 ? String.valueOf(ci.inflight) : "", 8),
                    rightCell(ci.totalCounter != null ? String.valueOf(ci.totalCounter) : "", 8),
                    rightCell(period, 10),
                    Cell.from(sinceLast),
                    Cell.from(Span.styled(uri, healthDown ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY))));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No consumers", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(consumerSortLabel("ID", "id"), consumerSortStyle("id"))),
                        Cell.from(Span.styled(consumerSortLabel("STATUS", "status"), consumerSortStyle("status"))),
                        Cell.from(Span.styled(consumerSortLabel("TYPE", "type"), consumerSortStyle("type"))),
                        rightCell(consumerSortLabel("INFLIGHT", "inflight"), 8, consumerSortStyle("inflight")),
                        rightCell(consumerSortLabel("TOTAL", "total"), 8, consumerSortStyle("total")),
                        rightCell("PERIOD", 10, Style.EMPTY.bold()),
                        Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold())),
                        Cell.from(Span.styled(consumerSortLabel("URI", "uri"), consumerSortStyle("uri")))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(10),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(22),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Consumers sort:" + consumerSort + " ").build())
                .build();

        frame.renderStatefulWidget(table, area, consumerTableState);
    }

    private String endpointSortLabel(String label, String column) {
        return sortLabel(label, column, endpointSort);
    }

    private Style endpointSortStyle(String column) {
        return sortStyle(column, endpointSort);
    }

    private int sortEndpoint(EndpointInfo a, EndpointInfo b) {
        return switch (endpointSort) {
            case "component" -> {
                String ca = a.component != null ? a.component : "";
                String cb = b.component != null ? b.component : "";
                yield ca.compareToIgnoreCase(cb);
            }
            case "dir" -> {
                String da = a.direction != null ? a.direction : "";
                String db = b.direction != null ? b.direction : "";
                yield da.compareToIgnoreCase(db);
            }
            case "total" -> Long.compare(b.hits, a.hits);
            case "uri" -> {
                String ua = a.uri != null ? a.uri : "";
                String ub = b.uri != null ? b.uri : "";
                yield ua.compareToIgnoreCase(ub);
            }
            default -> { // "route"
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
        };
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

        // Synthetic top row representing the route itself
        Style routeStyle = route.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.CYAN);
        rows.add(Row.from(
                Cell.from("   route"),
                Cell.from(Span.styled(route.from != null ? route.from : route.routeId, routeStyle)),
                Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                rightCell(String.valueOf(route.total), 8),
                rightCell(String.valueOf(route.failed), 6,
                        route.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY),
                Cell.from(""),
                rightCell(route.total > 0
                        ? route.minTime + "/" + route.maxTime + "/" + route.meanTime
                        : "", 14),
                Cell.from("")));

        for (ProcessorInfo proc : route.processors) {
            String indent = "  ".repeat(proc.level);
            Style nameStyle = proc.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.CYAN);

            rows.add(Row.from(
                    Cell.from("   " + (proc.processor != null ? proc.processor : "")),
                    Cell.from(Span.styled(indent + (proc.id != null ? proc.id : ""), nameStyle)),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    rightCell(String.valueOf(proc.total), 8),
                    rightCell(String.valueOf(proc.failed), 6,
                            proc.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY),
                    Cell.from(""),
                    rightCell(proc.total > 0
                            ? proc.minTime + "/" + proc.maxTime + "/" + proc.meanTime
                            : "", 14),
                    Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("   TYPE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                        Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                        rightCell("TOTAL", 8, Style.EMPTY.bold()),
                        rightCell("FAIL", 6, Style.EMPTY.bold()),
                        Cell.from(""),
                        rightCell("MIN/MAX/MEAN", 14, Style.EMPTY.bold()),
                        Cell.from("")))
                .widths(
                        Constraint.length(20),
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
                    : Style.EMPTY.fg(Color.LIGHT_RED);
            Style failStyle = route.failed > 0
                    ? Style.EMPTY.fg(Color.LIGHT_RED).bold()
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
                Color counterColor = cr[2] == 1 ? Color.GREEN : Color.LIGHT_RED;
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

    private void loadSourceForSelectedRoute() {
        if (selectedPid == null || runner == null) {
            return;
        }
        if (!sourceLoading.compareAndSet(false, true)) {
            return;
        }
        IntegrationInfo info = findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            sourceLoading.set(false);
            return;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo selectedRoute = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);

        sourceLines = List.of("(Loading source...)");
        sourceTitle = selectedRoute.routeId;
        sourceScroll = 0;
        sourceScrollX = 0;
        showSource = true;

        String pid = selectedPid;
        String routeId = selectedRoute.routeId;
        runner.scheduler().execute(() -> {
            try {
                loadSourceInBackground(pid, routeId);
            } finally {
                sourceLoading.set(false);
            }
        });
    }

    private void toggleRouteStartStop() {
        if (selectedPid == null) {
            return;
        }
        IntegrationInfo info = findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo route = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        // Started → stop; Stopped or Suspended → start
        String command = "Started".equals(route.state) ? "stop" : "start";
        sendRouteCommand(selectedPid, route.routeId, command);
    }

    private void toggleRouteSuspendResume() {
        if (selectedPid == null) {
            return;
        }
        IntegrationInfo info = findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo route = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        // Started → suspend; Suspended → resume; Stopped → start
        String command = switch (route.state != null ? route.state : "") {
            case "Started" -> "suspend";
            case "Suspended" -> "resume";
            default -> "start";
        };
        sendRouteCommand(selectedPid, route.routeId, command);
    }

    private void sendRouteCommand(String pid, String routeId, String command) {
        JsonObject root = new JsonObject();
        root.put("action", "route");
        root.put("id", routeId);
        root.put("command", command);
        Path actionFile = getActionFile(pid);
        org.apache.camel.dsl.jbang.core.common.PathUtils.writeTextSafely(root.toJson(), actionFile);
    }

    private String selectedRouteState() {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return null;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo route = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        return route.state;
    }

    private void loadSourceInBackground(String pid, String routeId) {
        Path outputFile = getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "source");
        root.put("filter", routeId);

        Path actionFile = getActionFile(pid);
        org.apache.camel.dsl.jbang.core.common.PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            applySourceResult(routeId, null, List.of("(No response from integration)"));
            return;
        }

        JsonArray routes = (JsonArray) jo.get("routes");
        if (routes == null || routes.isEmpty()) {
            applySourceResult(routeId, null, List.of("(No source available for route: " + routeId + ")"));
            return;
        }

        JsonObject routeObj = (JsonObject) routes.get(0);
        String sourceLocation = objToString(routeObj.get("source"));
        List<JsonObject> codeLines = routeObj.getCollection("code");
        if (codeLines == null || codeLines.isEmpty()) {
            applySourceResult(routeId, sourceLocation, List.of("(No source code available)"));
            return;
        }

        List<String> lines = new ArrayList<>();
        int maxLineNum = 0;
        for (JsonObject codeLine : codeLines) {
            Integer lineNum = codeLine.getInteger("line");
            if (lineNum != null && lineNum > maxLineNum) {
                maxLineNum = lineNum;
            }
        }
        int lineNumWidth = String.valueOf(maxLineNum).length();
        int matchLine = -1;
        int idx = 0;
        for (JsonObject codeLine : codeLines) {
            Integer lineNum = codeLine.getInteger("line");
            String code = Jsoner.unescape(objToString(codeLine.get("code")));
            Boolean match = codeLine.getBoolean("match");
            String prefix = lineNum != null
                    ? String.format("%" + lineNumWidth + "d  ", lineNum)
                    : String.format("%" + lineNumWidth + "s  ", "");
            lines.add(prefix + code);
            if (Boolean.TRUE.equals(match) && matchLine < 0) {
                matchLine = idx;
            }
            idx++;
        }

        int scrollTo = matchLine > 0 ? Math.max(0, matchLine - 2) : 0;
        applySourceResult(routeId, sourceLocation, lines, scrollTo);
    }

    private void applySourceResult(String routeId, String location, List<String> lines) {
        applySourceResult(routeId, location, lines, 0);
    }

    private void applySourceResult(String routeId, String location, List<String> lines, int scrollTo) {
        if (runner == null) {
            return;
        }
        runner.runOnRenderThread(() -> {
            if (!showSource) {
                return; // user cancelled via Esc while loading
            }
            sourceTitle = location != null ? routeId + "  " + location : routeId;
            sourceLines = lines;
            sourceScroll = scrollTo;
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
            // Only restore showDiagram if user hasn't cancelled via Esc while loading
            if (wasShowing) {
                showDiagram = true;
            }
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

    // ---- Tab 5: Circuit Breaker ----

    private void renderCircuitBreaker(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<CircuitBreakerInfo> sorted = new ArrayList<>(info.circuitBreakers);
        sorted.sort(this::sortCircuitBreaker);

        List<Row> rows = new ArrayList<>();
        for (CircuitBreakerInfo cb : sorted) {
            Style stateStyle = switch (cb.state != null ? cb.state.toLowerCase() : "") {
                case "closed" -> Style.EMPTY.fg(Color.GREEN);
                case "open", "forced_open" -> Style.EMPTY.fg(Color.LIGHT_RED);
                default -> Style.EMPTY.fg(Color.YELLOW); // half_open / half opened / unknown
            };
            String state = cb.state != null ? cb.state : "";
            String pending = cb.bufferedCalls > 0 ? String.valueOf(cb.bufferedCalls) : "";
            String success = cb.successfulCalls > 0 ? String.valueOf(cb.successfulCalls) : "";
            String failed = cb.failedCalls > 0 ? String.valueOf(cb.failedCalls) : "";
            String reject = cb.notPermittedCalls > 0 ? String.valueOf(cb.notPermittedCalls) : "";
            String rate = cb.failureRate >= 0 ? String.format("%.0f%%", cb.failureRate) : "";

            rows.add(Row.from(
                    Cell.from(Span.styled(cb.routeId != null ? cb.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(cb.id != null ? cb.id : ""),
                    Cell.from(cb.component != null ? cb.component : ""),
                    Cell.from(Span.styled(state, stateStyle)),
                    rightCell(pending, 8),
                    rightCell(success, 8),
                    rightCell(failed, 8),
                    rightCell(rate, 6),
                    rightCell(reject, 8)));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No circuit breakers", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""), Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(cbSortLabel("ROUTE", "route"), cbSortStyle("route"))),
                        Cell.from(Span.styled(cbSortLabel("ID", "id"), cbSortStyle("id"))),
                        Cell.from(Span.styled(cbSortLabel("COMPONENT", "component"), cbSortStyle("component"))),
                        Cell.from(Span.styled(cbSortLabel("STATE", "state"), cbSortStyle("state"))),
                        rightCell("PENDING", 8, Style.EMPTY.bold()),
                        rightCell("SUCCESS", 8, Style.EMPTY.bold()),
                        rightCell("FAIL", 8, Style.EMPTY.bold()),
                        rightCell("RATE%", 6, Style.EMPTY.bold()),
                        rightCell("REJECT", 8, Style.EMPTY.bold())))
                .widths(
                        Constraint.length(20),
                        Constraint.length(20),
                        Constraint.length(16),
                        Constraint.length(12),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Circuit Breaker ").build())
                .build();

        frame.renderStatefulWidget(table, area, cbTableState);
    }

    private String cbSortLabel(String label, String column) {
        return cbSort.equals(column) ? label + " ▴" : label;
    }

    private Style cbSortStyle(String column) {
        return cbSort.equals(column) ? Style.EMPTY.fg(Color.YELLOW).bold() : Style.EMPTY.bold();
    }

    private int sortCircuitBreaker(CircuitBreakerInfo a, CircuitBreakerInfo b) {
        return switch (cbSort) {
            case "id" -> compareStr(a.id, b.id);
            case "component" -> compareStr(a.component, b.component);
            case "state" -> compareStr(a.state, b.state);
            default -> compareStr(a.routeId, b.routeId); // "route"
        };
    }

    private static int compareStr(String a, String b) {
        if (a == null && b == null)
            return 0;
        if (a == null)
            return 1;
        if (b == null)
            return -1;
        return a.compareToIgnoreCase(b);
    }

    private void renderSource(Frame frame, Rect area) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Source [" + (sourceTitle != null ? sourceTitle : "") + "] ")
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (sourceLines.isEmpty()) {
            return;
        }

        int visibleLines = inner.height();
        int maxScroll = Math.max(0, sourceLines.size() - visibleLines);
        sourceScroll = Math.min(sourceScroll, maxScroll);

        int maxLineWidth = sourceLines.stream().mapToInt(String::length).max().orElse(0);
        int maxHScroll = Math.max(0, maxLineWidth - inner.width());
        sourceScrollX = Math.min(sourceScrollX, maxHScroll);

        int end = Math.min(sourceScroll + visibleLines, sourceLines.size());
        List<Line> visible = new ArrayList<>();
        for (int i = sourceScroll; i < end; i++) {
            String raw = sourceLines.get(i);
            visible.add(TuiHelper.ansiToLine(raw, sourceScrollX));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(visible)).build(), inner);

        // Vertical scrollbar
        if (sourceLines.size() > visibleLines) {
            sourceVScrollState.contentLength(sourceLines.size()).viewportContentLength(visibleLines).position(sourceScroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), inner, sourceVScrollState);
        }
        // Horizontal scrollbar
        if (maxHScroll > 0) {
            sourceHScrollState.contentLength(maxLineWidth).viewportContentLength(inner.width()).position(sourceScrollX);
            frame.renderStatefulWidget(Scrollbar.horizontal(), inner, sourceHScrollState);
        }
    }

    // ---- Tab 3: Health ----

    private void renderHealth(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<HealthCheckInfo> healthChecks = getFilteredHealthChecks(info);

        List<Row> rows = new ArrayList<>();
        for (HealthCheckInfo hc : healthChecks) {
            Style stateStyle;
            String icon;
            if ("UP".equals(hc.state)) {
                stateStyle = Style.EMPTY.fg(Color.GREEN);
                icon = "\u2714 ";
            } else if ("DOWN".equals(hc.state)) {
                stateStyle = Style.EMPTY.fg(Color.LIGHT_RED);
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

        frame.renderStatefulWidget(table, area, healthTableState);
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

        List<EndpointInfo> sortedEndpoints = new ArrayList<>(info.endpoints);
        if (showOnlyRemote) {
            sortedEndpoints.removeIf(ep -> !ep.remote);
        }
        sortedEndpoints.sort(this::sortEndpoint);

        List<Row> rows = new ArrayList<>();
        for (EndpointInfo ep : sortedEndpoints) {
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
                    Cell.from(ep.routeId != null ? ep.routeId : ""),
                    Cell.from(Span.styled(arrow + dir, dirStyle)),
                    rightCell(ep.hits > 0 ? String.valueOf(ep.hits) : "", 8),
                    centerCell(ep.stub ? "x" : "", 6),
                    centerCell(ep.remote ? "x" : "", 8),
                    Cell.from(ep.uri != null ? ep.uri : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No endpoints", Style.EMPTY.dim())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(endpointSortLabel("COMPONENT", "component"), endpointSortStyle("component"))),
                        Cell.from(Span.styled(endpointSortLabel("ROUTE", "route"), endpointSortStyle("route"))),
                        Cell.from(Span.styled(endpointSortLabel("DIR", "dir"), endpointSortStyle("dir"))),
                        rightCell(endpointSortLabel("TOTAL", "total"), 8, endpointSortStyle("total")),
                        centerCell("STUB", 6, Style.EMPTY.bold()),
                        centerCell("REMOTE", 8, Style.EMPTY.bold()),
                        Cell.from(Span.styled(endpointSortLabel("URI", "uri"), endpointSortStyle("uri")))))
                .widths(
                        Constraint.length(15),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Endpoints sort:" + endpointSort + (showOnlyRemote ? " remote" : "") + " ").build())
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

        List<LogEntry> entries = filteredLogEntries;
        int contentHeight = entries.size();

        String logTitle = info.rootLogLevel != null
                ? " Log level:" + info.rootLogLevel + " "
                : " Log ";
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(logTitle)
                .build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        int visibleHeight = Math.max(1, inner.height());

        if (logFollowMode) {
            logScroll = Math.max(0, contentHeight - visibleHeight);
        }
        logScroll = Math.min(logScroll, Math.max(0, contentHeight - visibleHeight));

        int hSkip = logWordWrap ? 0 : logHScroll;

        // Rebuild Line cache only when entries or horizontal offset changes
        if (entries != cachedLogEntries || hSkip != cachedLogHSkip) {
            cachedLogEntries = entries;
            cachedLogHSkip = hSkip;
            List<Line> built = new ArrayList<>(entries.size());
            int maxW = 0;
            for (LogEntry entry : entries) {
                String raw = entry.raw != null ? entry.raw : "";
                if (!logWordWrap) {
                    maxW = Math.max(maxW, CharWidth.of(TuiHelper.stripAnsi(raw)));
                }
                built.add(TuiHelper.ansiToLine(raw, hSkip));
            }
            cachedLogMaxWidth = maxW;
            cachedLogLines = built;
        }

        // Cap horizontal scroll using cached max width
        if (!logWordWrap) {
            int visibleWidth = Math.max(1, inner.width() - 1);
            logHScroll = Math.min(logHScroll, Math.max(0, cachedLogMaxWidth - visibleWidth));
        }

        // Slice only the visible window so Paragraph doesn't iterate all entries every frame
        List<Line> allLines = cachedLogLines;
        int start = Math.min(logScroll, Math.max(0, allLines.size() - visibleHeight));
        List<Line> visibleLines = allLines.subList(start, Math.min(allLines.size(), start + visibleHeight));

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        Overflow overflow = logWordWrap ? Overflow.WRAP_WORD : Overflow.CLIP;
        Paragraph para = Paragraph.builder()
                .text(Text.from(visibleLines))
                .overflow(overflow)
                .build();
        frame.renderWidget(para, hChunks.get(0));

        if (contentHeight > visibleHeight) {
            logScrollState.contentLength(contentHeight);
            logScrollState.viewportContentLength(visibleHeight);
            logScrollState.position(logScroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), logScrollState);
        }

        if (showLogLevelPopup) {
            renderLogLevelPopup(frame, area);
        }
    }

    private void renderLogLevelPopup(Frame frame, Rect area) {
        int popupW = 24;
        int popupH = 7; // 2 border rows + 5 level items
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        // Clear the popup area so log content doesn't bleed through the border
        frame.renderWidget(Clear.INSTANCE, popup);

        ListWidget list = ListWidget.builder()
                .items(
                        ListItem.from("  ERROR  ").style(Style.EMPTY.fg(Color.LIGHT_RED)),
                        ListItem.from("  WARN   ").style(Style.EMPTY.fg(Color.YELLOW)),
                        ListItem.from("  INFO   ").style(Style.EMPTY),
                        ListItem.from("  DEBUG  ").style(Style.EMPTY.fg(Color.CYAN)),
                        ListItem.from("  TRACE  ").style(Style.EMPTY.dim()))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" Set Log Level ")
                        .build())
                .build();

        frame.renderStatefulWidget(list, popup, logLevelListState);
    }

    private void sendLoggerLevelAction(String pid, String level) {
        JsonObject root = new JsonObject();
        root.put("action", "logger");
        root.put("command", "set-logging-level");
        root.put("logger-name", "root");
        root.put("logging-level", level);
        Path actionFile = getActionFile(pid);
        org.apache.camel.dsl.jbang.core.common.PathUtils.writeTextSafely(root.toJson(), actionFile);
    }

    private void readNewLogLines(String pid, List<String> newLines) {
        Path logFile = CommandLineHelper.getCamelDir().resolve(pid + ".log");
        if (!Files.exists(logFile)) {
            logFilePid = pid;
            logFilePos = -1;
            return;
        }
        logFilePid = pid;
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = raf.length();
            if (logFilePos < 0 || logFilePos > length) {
                // First read or file truncated/rotated: start 1 MB from end
                logFilePos = Math.max(0, length - 1024 * 1024);
                logLineBuffer.setLength(0);
            }
            if (logFilePos >= length) {
                return; // nothing new
            }
            raf.seek(logFilePos);
            byte[] buf = new byte[(int) Math.min(length - logFilePos, 4 * 1024 * 1024)];
            raf.readFully(buf);
            logFilePos += buf.length;

            // Prepend any unfinished line from previous read, then process line by line
            String chunk = logLineBuffer + new String(buf, StandardCharsets.UTF_8);
            logLineBuffer.setLength(0);
            int start = 0;
            int end;
            while ((end = chunk.indexOf('\n', start)) >= 0) {
                // TODO: remove fixControlChars workaround once TamboUI ships a release that
                // sanitises C0 control chars in Buffer.setString (fix contributed in PR #345).
                String line = TuiHelper.fixControlChars(chunk.substring(start, end));
                if (!line.isEmpty()) {
                    newLines.add(line);
                }
                start = end + 1;
            }
            // Save incomplete trailing line for next call
            if (start < chunk.length()) {
                logLineBuffer.append(chunk, start, chunk.length());
            }
        } catch (IOException e) {
            // ignore
        }
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
            String plain = TuiHelper.stripAnsi(line);
            Matcher m = LOG_PATTERN.matcher(plain);
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
                entry.message = plain;
            }
        } catch (Exception e) {
            entry.time = "";
            entry.level = "INFO";
            entry.message = TuiHelper.stripAnsi(line);
        }
        return entry;
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
                case "Failed" -> Style.EMPTY.fg(Color.LIGHT_RED);
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
        addExceptionLines(lines, entry.exception);

        int[] scroll = { traceDetailScroll };
        int[] hScroll = { traceDetailHScroll };
        renderDetailPanel(frame, area, lines, traceWordWrap, hScroll, scroll, traceDetailScrollState);
        traceDetailScroll = scroll[0];
        traceDetailHScroll = hScroll[0];
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
        addExceptionLines(lines, entry.exception);

        int[] scroll = { historyDetailScroll };
        int[] hScroll = { historyDetailHScroll };
        renderDetailPanel(frame, area, lines, historyWordWrap, hScroll, scroll, historyDetailScrollState);
        historyDetailScroll = scroll[0];
        historyDetailHScroll = hScroll[0];
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
                    Span.styled("Failed", Style.EMPTY.fg(Color.LIGHT_RED).bold())));
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
                t = TuiHelper.truncateStart(t, 20);
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

    private static void addExceptionLines(List<Line> lines, String exception) {
        if (exception == null) {
            return;
        }
        lines.add(Line.from(Span.styled(" Exception:", Style.EMPTY.fg(Color.LIGHT_RED).bold())));
        for (String l : exception.split("\n", -1)) {
            lines.add(Line.from(Span.raw("   " + TuiHelper.fixControlChars(l))));
        }
        lines.add(Line.from(Span.raw("")));
    }

    private void renderDetailPanel(
            Frame frame, Rect area, List<Line> lines,
            boolean wordWrap, int[] hScroll, int[] scroll, ScrollbarState scrollState) {
        Block block = Block.builder().borderType(BorderType.ROUNDED).build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        int visibleHeight = Math.max(1, inner.height());
        int visibleWidth = Math.max(1, inner.width() - 1); // -1 for scrollbar column
        int contentHeight;
        if (wordWrap) {
            contentHeight = 0;
            for (Line l : lines) {
                int w = l.width();
                contentHeight += Math.max(1, (w + visibleWidth - 1) / visibleWidth);
            }
        } else {
            contentHeight = lines.size();
        }
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        if (scroll[0] > maxScroll) {
            scroll[0] = maxScroll;
        }

        // Cap horizontal scroll so it can't go past the longest line
        if (!wordWrap) {
            int maxLineWidth = lines.stream().mapToInt(Line::width).max().orElse(0);
            hScroll[0] = Math.min(hScroll[0], Math.max(0, maxLineWidth - visibleWidth));
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Line> visibleLines = (!wordWrap && hScroll[0] > 0) ? applyHSkip(lines, hScroll[0]) : lines;
        Paragraph detail = Paragraph.builder()
                .text(Text.from(visibleLines))
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

    private static List<Line> applyHSkip(List<Line> lines, int hSkip) {
        List<Line> result = new ArrayList<>(lines.size());
        for (Line line : lines) {
            result.add(hSkipLine(line, hSkip));
        }
        return result;
    }

    private static Line hSkipLine(Line line, int hSkip) {
        List<Span> result = new ArrayList<>();
        int skip = hSkip;
        for (Span span : line.spans()) {
            if (skip <= 0) {
                result.add(span);
                continue;
            }
            String text = span.content();
            int spanWidth = CharWidth.of(text);
            if (spanWidth <= skip) {
                skip -= spanWidth;
            } else {
                // Partial skip: advance char-by-char until skip columns consumed
                int i = 0;
                int consumed = 0;
                while (i < text.length() && consumed < skip) {
                    int cp = text.codePointAt(i);
                    consumed += CharWidth.of(cp);
                    i += Character.charCount(cp);
                }
                skip = 0;
                String remaining = text.substring(i);
                if (!remaining.isEmpty()) {
                    result.add(Span.styled(remaining, span.style()));
                }
            }
        }
        return Line.from(result);
    }

    private static Row buildStepRow(
            String direction, boolean first, boolean last, boolean failed,
            String timestamp, String routeId, String nodeId, String processor, long elapsed) {
        Style dirStyle;
        if (first) {
            dirStyle = Style.EMPTY.fg(Color.GREEN);
        } else if (last) {
            dirStyle = failed ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.GREEN);
        } else {
            dirStyle = failed ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY;
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
                failed ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY.fg(Color.GREEN).bold()));
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
            hint(spans, "a", "chart " + (chartAllIntegrations ? "[all]" : "[single]"));
            hint(spans, "Enter", "details");
            if (selectedPid != null) {
                IntegrationInfo selInfo = findSelectedIntegration();
                if (selInfo != null) {
                    hint(spans, "p", selInfo.routeStarted > 0 ? "stop" : "start");
                }
                hint(spans, "Esc", "unselect");
            }
            hint(spans, "1-9", "tabs");
        } else if (tab == TAB_ROUTES && showSource) {
            hint(spans, "c/Esc", "close");
            hint(spans, "\u2191\u2193\u2190\u2192", "scroll");
            hint(spans, "PgUp/PgDn", "page");
            hintLast(spans, "Home/End", "top/bottom");
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
            hint(spans, "c", "source");
            hint(spans, "d", "diagram");
            hint(spans, "D", "text diagram");
            String routeState = selectedRouteState();
            if ("Started".equals(routeState)) {
                hint(spans, "p", "stop");
                hint(spans, "P", "suspend");
            } else if ("Suspended".equals(routeState)) {
                hint(spans, "p", "start");
                hint(spans, "P", "resume");
            } else if (routeState != null) {
                hint(spans, "p", "start");
            }
            hint(spans, "1-9", "tabs");
        } else if (tab == TAB_CONSUMERS) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "1-9", "tabs");
        } else if (tab == TAB_ENDPOINTS) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "r", "remote" + (showOnlyRemote ? " [on]" : " [off]"));
            hint(spans, "1-9", "tabs");
        } else if (tab == TAB_CIRCUIT_BREAKER) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "1-9", "tabs");
        } else if (tab == TAB_HEALTH) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "d", "toggle DOWN");
            hint(spans, "1-9", "tabs");
        } else if (tab == TAB_LOG && showLogLevelPopup) {
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "Enter", "set level");
            hintLast(spans, "Esc", "cancel");
        } else if (tab == TAB_LOG) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "scroll");
            hint(spans, "PgUp/PgDn", "page");
            hint(spans, "Home/End", "top/end");
            hint(spans, "w", "wrap" + (logWordWrap ? " [on]" : " [off]"));
            if (!logWordWrap) {
                hint(spans, "\u2190\u2192", "h-scroll");
            }
            hint(spans, "l", "level");
            hintLast(spans, "f", "follow" + (logFollowMode ? " [on]" : " [off]"));
        } else if (tab == TAB_TRACE && traceDetailView) {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "PgUp/PgDn", "scroll detail");
            if (!traceWordWrap) {
                hint(spans, "\u2190\u2192", "h-scroll");
            }
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
            if (!historyWordWrap) {
                hint(spans, "\u2190\u2192", "h-scroll");
            }
            hint(spans, "p", "properties" + (showHistoryProperties ? " [on]" : " [off]"));
            hint(spans, "v", "variables" + (showHistoryVariables ? " [on]" : " [off]"));
            hint(spans, "h", "headers" + (showHistoryHeaders ? " [on]" : " [off]"));
            hint(spans, "b", "body" + (showHistoryBody ? " [on]" : " [off]"));
            hint(spans, "w", "wrap" + (historyWordWrap ? " [on]" : " [off]"));
            hintLast(spans, "F5", "refresh");
        } else {
            hint(spans, "Esc", "back");
            hint(spans, "\u2191\u2193", "navigate");
            hint(spans, "1-9", "tabs");
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

    private static Cell centerCell(String text, int width) {
        int len = text.length();
        int padding = Math.max(0, width - len);
        int leftPad = padding / 2;
        return Cell.from(" ".repeat(leftPad) + text);
    }

    private static Cell centerCell(String text, int width, Style style) {
        int len = text.length();
        int padding = Math.max(0, width - len);
        int leftPad = padding / 2;
        return Cell.from(Span.styled(" ".repeat(leftPad) + text, style));
    }

    private static Line badgeCb(String label, long total, long open) {
        if (open > 0) {
            return Line.from(
                    Span.raw(label),
                    Span.styled("(" + open + " OPEN)", Style.EMPTY.fg(Color.LIGHT_RED).bold()),
                    Span.raw(" "));
        }
        return badge(label, total);
    }

    private static Line badgeHealth(String label, long total, long down) {
        if (down > 0) {
            return Line.from(
                    Span.raw(label),
                    Span.styled("(" + down + " DOWN)", Style.EMPTY.fg(Color.LIGHT_RED).bold()),
                    Span.raw(" "));
        }
        return badge(label, total);
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
                    failedHistory.remove(entry.getKey());
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

            // Refresh log data for the selected integration (incremental tail)
            IntegrationInfo selected = findSelectedIntegration();
            if (selected != null) {
                if (!selected.pid.equals(logFilePid)) {
                    // Integration changed: reset all incremental log state
                    mutableFilteredEntries.clear();
                    logFilePos = -1;
                    logLineBuffer.setLength(0);
                }
                List<String> newRawLines = new ArrayList<>();
                readNewLogLines(selected.pid, newRawLines);
                if (!newRawLines.isEmpty()) {
                    for (String line : newRawLines) {
                        mutableFilteredEntries.add(parseLogLine(line));
                    }
                    if (mutableFilteredEntries.size() > MAX_LOG_LINES) {
                        mutableFilteredEntries.subList(0, mutableFilteredEntries.size() - MAX_LOG_LINES).clear();
                    }
                    filteredLogEntries = new ArrayList<>(mutableFilteredEntries);
                }
            }

            // Refresh trace data
            refreshTraceData(pids);
        } catch (Exception e) {
            // ignore refresh errors
        }
    }

    private void updateThroughputHistory(IntegrationInfo info) {
        // Track exchangesTotal and exchangesFailed over a 1-second sliding window
        long currentTotal = info.exchangesTotal;
        long currentFailed = info.failed;
        long now = System.currentTimeMillis();

        String pid = info.pid;
        LinkedList<long[]> samples = throughputSamples.computeIfAbsent(pid, k -> new LinkedList<>());
        samples.add(new long[] { now, currentTotal, currentFailed });

        // Remove samples older than 1 second
        while (!samples.isEmpty() && now - samples.get(0)[0] > 1000) {
            samples.remove(0);
        }

        // Compute throughput over the window
        if (samples.size() >= 2) {
            long[] oldest = samples.get(0);
            long[] newest = samples.get(samples.size() - 1);
            long deltaTotal = newest[1] - oldest[1];
            long deltaFailed = newest[2] - oldest[2];
            long deltaTimeMs = newest[0] - oldest[0];
            long tp = deltaTimeMs > 0 ? (deltaTotal * 1000) / deltaTimeMs : 0;
            long fp = deltaTimeMs > 0 ? (deltaFailed * 1000) / deltaTimeMs : 0;

            // Only add one point per second to keep the sparkline meaningful
            Long lastTime = previousExchangesTime.get(pid);
            if (lastTime == null || now - lastTime >= 1000) {
                previousExchangesTime.put(pid, now);
                LinkedList<Long> hist = throughputHistory.computeIfAbsent(pid, k -> new LinkedList<>());
                hist.add(tp);
                while (hist.size() > MAX_SPARKLINE_POINTS) {
                    hist.remove(0);
                }
                LinkedList<Long> fhist = failedHistory.computeIfAbsent(pid, k -> new LinkedList<>());
                fhist.add(fp);
                while (fhist.size() > MAX_SPARKLINE_POINTS) {
                    fhist.remove(0);
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

        // Exception (message + full stacktrace)
        Object excObj = json.get("exception");
        if (excObj instanceof JsonObject excJson) {
            String msg = excJson.getString("message");
            entry.exception = msg != null ? Jsoner.unescape(msg) : null;
            String st = excJson.getString("stackTrace");
            if (st != null && !st.isEmpty()) {
                entry.exception = entry.exception + "\n" + Jsoner.unescape(st);
            }
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

        // Exception (message + full stacktrace)
        Object excObj = json.get("exception");
        if (excObj instanceof JsonObject excJson) {
            String msg = excJson.getString("message");
            entry.exception = msg != null ? Jsoner.unescape(msg) : null;
            String st = excJson.getString("stackTrace");
            if (st != null && !st.isEmpty()) {
                entry.exception = entry.exception + "\n" + Jsoner.unescape(st);
            }
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
        info.javaVersion = runtime != null ? runtime.getString("javaVersion") : null;
        info.javaVendor = runtime != null ? runtime.getString("javaVendor") : null;
        info.javaVmName = runtime != null ? runtime.getString("javaVmName") : null;

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
            info.nonHeapMemUsed = mem.getLongOrDefault("nonHeapMemoryUsed", 0L);
        }

        JsonObject threads = (JsonObject) root.get("threads");
        if (threads != null) {
            info.threadCount = threads.getIntegerOrDefault("threadCount", 0);
            info.peakThreadCount = threads.getIntegerOrDefault("peakThreadCount", 0);
        }

        JsonObject logger = (JsonObject) root.get("logger");
        if (logger != null) {
            JsonObject levels = (JsonObject) logger.get("levels");
            if (levels != null) {
                info.rootLogLevel = levels.getString("root");
            }
        }

        // Parse routes
        JsonArray routes = (JsonArray) root.get("routes");
        if (routes != null) {
            for (Object r : routes) {
                JsonObject rj = (JsonObject) r;
                RouteInfo ri = new RouteInfo();
                ri.routeId = rj.getString("routeId");
                ri.group = rj.getString("group");
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
                    ri.meanTime = Math.max(0, objToLong(rs.get("meanProcessingTime")));
                    ri.minTime = Math.max(0, objToLong(rs.get("minProcessingTime")));
                    ri.maxTime = Math.max(0, objToLong(rs.get("maxProcessingTime")));
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
                            pi.meanTime = Math.max(0, objToLong(ps.get("meanProcessingTime")));
                            pi.minTime = Math.max(0, objToLong(ps.get("minProcessingTime")));
                            pi.maxTime = Math.max(0, objToLong(ps.get("maxProcessingTime")));
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
                    hc.message = cj.getString("message");
                    if (hc.message == null) {
                        JsonObject details = (JsonObject) cj.get("details");
                        if (details != null && details.containsKey("failure.error.message")) {
                            hc.message = details.getString("failure.error.message");
                        }
                    }
                    info.healthChecks.add(hc);
                }
            }
        }

        // Parse consumers
        JsonObject consumersObj = (JsonObject) root.get("consumers");
        if (consumersObj != null) {
            JsonArray consumerList = (JsonArray) consumersObj.get("consumers");
            if (consumerList != null) {
                for (Object c : consumerList) {
                    JsonObject cj = (JsonObject) c;
                    ConsumerInfo ci = new ConsumerInfo();
                    ci.id = cj.getString("id");
                    ci.uri = cj.getString("uri");
                    ci.state = cj.getString("state");
                    ci.className = cj.getString("class");
                    ci.scheduled = Boolean.TRUE.equals(cj.get("scheduled"));
                    ci.inflight = cj.getIntegerOrDefault("inflight", 0);
                    ci.polling = (Boolean) cj.get("polling");
                    ci.totalCounter = cj.getLong("totalCounter");
                    ci.delay = cj.getLong("delay");
                    ci.period = cj.getLong("period");
                    JsonObject cStats = (JsonObject) cj.get("statistics");
                    if (cStats != null) {
                        Object last = cStats.get("lastCreatedExchangeTimestamp");
                        if (last != null) {
                            ci.sinceLastStarted = TimeUtils.printSince(Long.parseLong(last.toString()));
                        }
                        last = cStats.get("lastCompletedExchangeTimestamp");
                        if (last != null) {
                            ci.sinceLastCompleted = TimeUtils.printSince(Long.parseLong(last.toString()));
                        }
                        last = cStats.get("lastFailedExchangeTimestamp");
                        if (last != null) {
                            ci.sinceLastFailed = TimeUtils.printSince(Long.parseLong(last.toString()));
                        }
                    }
                    info.consumers.add(ci);
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
                    ep.hits = TuiHelper.objToLong(ej.get("hits"));
                    ep.stub = Boolean.TRUE.equals(ej.get("stub"));
                    ep.remote = !Boolean.FALSE.equals(ej.get("remote"));
                    // Extract component from URI (e.g., "timer://tick" -> "timer")
                    if (ep.uri != null) {
                        int idx = ep.uri.indexOf(':');
                        ep.component = idx > 0 ? ep.uri.substring(0, idx) : ep.uri;
                    }
                    info.endpoints.add(ep);
                }
            }
        }

        // Parse circuit breakers: resilience4j, fault-tolerance, core
        parseCbSection(root, "resilience4j", info);
        parseCbSection(root, "fault-tolerance", info);
        parseCbSection(root, "circuit-breaker", info);

        return info;
    }

    private static void parseCbSection(JsonObject root, String key, IntegrationInfo info) {
        JsonObject section = (JsonObject) root.get(key);
        if (section == null) {
            return;
        }
        JsonArray breakers = (JsonArray) section.get("circuitBreakers");
        if (breakers == null) {
            return;
        }
        String component = switch (key) {
            case "resilience4j" -> "resilience4j";
            case "fault-tolerance" -> "fault-tolerance";
            default -> "core";
        };
        for (Object b : breakers) {
            JsonObject bj = (JsonObject) b;
            CircuitBreakerInfo cb = new CircuitBreakerInfo();
            cb.component = component;
            cb.routeId = bj.getString("routeId");
            cb.id = bj.getString("id");
            cb.state = bj.getString("state");
            cb.bufferedCalls = bj.getIntegerOrDefault("bufferedCalls", 0);
            cb.successfulCalls = TuiHelper.objToLong(bj.get("successfulCalls"));
            cb.failedCalls = TuiHelper.objToLong(bj.get("failedCalls"));
            cb.notPermittedCalls = TuiHelper.objToLong(bj.get("notPermittedCalls"));
            Object fr = bj.get("failureRate");
            cb.failureRate = fr instanceof Number n ? n.doubleValue() : -1;
            info.circuitBreakers.add(cb);
        }
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
        String javaVersion;
        String javaVendor;
        String javaVmName;
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
        String rootLogLevel;
        int routeStarted;
        int routeTotal;
        long heapMemUsed;
        long heapMemMax;
        long nonHeapMemUsed;
        int threadCount;
        int peakThreadCount;
        boolean vanishing;
        long vanishStart;
        final List<RouteInfo> routes = new ArrayList<>();
        final List<ConsumerInfo> consumers = new ArrayList<>();
        final List<HealthCheckInfo> healthChecks = new ArrayList<>();
        final List<EndpointInfo> endpoints = new ArrayList<>();
        final List<CircuitBreakerInfo> circuitBreakers = new ArrayList<>();
    }

    static class RouteInfo {
        String routeId;
        String group;
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

    static class ConsumerInfo {
        String id;
        String uri;
        String state;
        String className;
        boolean scheduled;
        int inflight;
        Boolean polling;
        Long totalCounter;
        Long delay;
        Long period;
        String sinceLastStarted;
        String sinceLastCompleted;
        String sinceLastFailed;
    }

    static class EndpointInfo {
        String uri;
        String component;
        String direction;
        String routeId;
        long hits;
        boolean stub;
        boolean remote;
    }

    static class CircuitBreakerInfo {
        String routeId;
        String id;
        String component; // "resilience4j", "fault-tolerance", "core"
        String state;
        int bufferedCalls;
        long successfulCalls;
        long failedCalls;
        long notPermittedCalls;
        double failureRate; // -1 means not available
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
        String exception;
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
