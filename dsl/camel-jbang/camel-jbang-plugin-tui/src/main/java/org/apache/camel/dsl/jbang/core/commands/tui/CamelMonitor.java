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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.export.ExportRequest;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.AnsiColor;
import dev.tamboui.style.Color;
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
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.PasteEvent;
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
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import dev.tamboui.widgets.tabs.Tabs;
import dev.tamboui.widgets.tabs.TabsState;
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

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;
import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

@Command(name = "monitor",
         description = "Live dashboard for monitoring Camel integrations",
         sortOptions = false)
public class CamelMonitor extends CamelCommand {

    private static final long VANISH_DURATION_MS = 6000;
    private static final long DEFAULT_REFRESH_MS = 100;
    private static final int MAX_SPARKLINE_POINTS = 60;
    private static final int MAX_ENDPOINT_CHART_POINTS = 60;
    private static final int MAX_LOG_LINES = 3000;
    private static final int MAX_TRACES = 200;
    private static final int NUM_TABS = 10;

    // Tab indices
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_LOG = 1;
    private static final int TAB_ROUTES = 2;
    private static final int TAB_ENDPOINTS = 3;
    private static final int TAB_HTTP = 4;
    private static final int TAB_HEALTH = 5;
    private static final int TAB_HISTORY = 6;
    private static final int TAB_ERRORS = 7;
    private static final int TAB_METRICS = 8;
    private static final int TAB_MORE = 9;

    // Overview sort columns
    private static final String[] OVERVIEW_SORT_COLUMNS = { "pid", "name", "version", "status", "total", "fail" };

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--refresh" },
                        description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
                        defaultValue = "100")
    long refreshInterval = DEFAULT_REFRESH_MS;

    @CommandLine.Option(names = { "--record" },
                        description = "Replay a .tape file inside the TUI and record to an Asciinema .cast file",
                        arity = "0..1")
    String record;

    @CommandLine.Option(names = { "--mcp" },
                        description = "Enable embedded MCP server for AI agent access to the TUI")
    boolean mcp;

    @CommandLine.Option(names = { "--mcp-port" },
                        description = "MCP server port (default: ${DEFAULT-VALUE})",
                        defaultValue = "8123")
    int mcpPort = 8123;

    // State
    private final AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(Collections.emptyList());
    private final Map<String, VanishingInfo> vanishing = new ConcurrentHashMap<>();
    private final Map<String, VanishingInfraInfo> vanishingInfra = new ConcurrentHashMap<>();
    private final TableState overviewTableState = new TableState();
    private int overviewDividerIndex = -1;
    private final TabsState tabsState = new TabsState(TAB_OVERVIEW);

    // Sparkline: throughput history per PID (one point per second)
    private final Map<String, LinkedList<Long>> throughputHistory = new ConcurrentHashMap<>();
    // Sparkline: failed throughput history per PID (one point per second)
    private final Map<String, LinkedList<Long>> failedHistory = new ConcurrentHashMap<>();
    // Sliding window of [timestamp, exchangesTotal, exchangesFailed] samples for smoothing
    private final Map<String, LinkedList<long[]>> throughputSamples = new ConcurrentHashMap<>();
    // Track last time a sparkline point was recorded
    private final Map<String, Long> previousExchangesTime = new ConcurrentHashMap<>();

    // Endpoint in/out sliding window history per PID — all endpoints
    private final Map<String, LinkedList<Long>> endpointInHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> endpointOutHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> endpointSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousEndpointTime = new ConcurrentHashMap<>();

    // Endpoint in/out sliding window history per PID — remote endpoints only
    private final Map<String, LinkedList<Long>> endpointRemoteInHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> endpointRemoteOutHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> endpointRemoteSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousEndpointRemoteTime = new ConcurrentHashMap<>();

    // Endpoint in/out sliding window history per PID — remote+stub endpoints
    private final Map<String, LinkedList<Long>> endpointRemoteStubInHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> endpointRemoteStubOutHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> endpointRemoteStubSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousEndpointRemoteStubTime = new ConcurrentHashMap<>();

    // Endpoint payload size (mean body size) history per PID — for sparkline
    private final Map<String, LinkedList<Long>> endpointInSizeHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> endpointOutSizeHistory = new ConcurrentHashMap<>();
    private final Map<String, Long> previousEndpointSizeTime = new ConcurrentHashMap<>();

    // Per-endpoint in/out rate history — keyed by pid + "|" + uri
    private final Map<String, LinkedList<Long>> perEndpointInHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> perEndpointOutHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> perEndpointSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousPerEndpointTime = new ConcurrentHashMap<>();

    // Circuit breaker throughput history per PID/cbId (success + fail, one point per second)
    private final Map<String, LinkedList<Long>> cbSuccessHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> cbFailHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> cbThroughputSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousCbTime = new ConcurrentHashMap<>();

    // Load averages (EWMA) — CPU%, per PID (inflight EWMA is read from the management JSON)
    private final Map<String, LoadAvg> cpuLoadAvg = new ConcurrentHashMap<>();
    private final Map<String, long[]> prevCpuSample = new ConcurrentHashMap<>();

    // Overview sort state
    private String overviewSort = "name";
    private int overviewSortIndex = 1;
    private boolean overviewSortReversed;

    // Trace/history data — shared between CamelMonitor and tabs
    private final AtomicReference<List<TraceEntry>> traces = new AtomicReference<>(Collections.emptyList());
    private final Map<String, Long> traceFilePositions = new ConcurrentHashMap<>();

    // selectedPid is stored on ctx (MonitorContext) so tabs can access it

    // Overview chart mode
    private static final int CHART_ALL = 0;
    private static final int CHART_SINGLE = 1;
    private static final int CHART_OFF = 2;
    private int chartMode = CHART_SINGLE;

    private volatile long lastRefresh;
    private boolean showKillConfirm;
    private String monitorNotification;
    private boolean monitorNotificationError;
    private long monitorNotificationExpiry;
    private volatile Buffer lastBuffer;
    private volatile long renderGeneration;
    private volatile String screenshotMessage;
    private volatile long screenshotMessageTime;
    private volatile boolean pendingScreenshot;
    private boolean recording;
    private TapeRecorder tapeRecorder;
    private boolean mcpInjectedKey;
    private TuiEventLog eventLog;
    private TuiMcpServer mcpServer;
    private final Queue<PendingKey> pendingKeys = new ConcurrentLinkedQueue<>();
    private final List<KeyRecord> recentKeys = new ArrayList<>();
    private final CaptionOverlay captionOverlay = new CaptionOverlay();

    private final ActionsPopup actionsPopup = new ActionsPopup(
            () -> data.get().stream()
                    .filter(i -> !i.vanishing && i.name != null)
                    .map(i -> i.name)
                    .collect(Collectors.toSet()),
            () -> data.get().stream()
                    .filter(i -> !i.vanishing)
                    .collect(Collectors.toList()),
            () -> infraData.get().stream()
                    .filter(i -> !i.vanishing)
                    .collect(Collectors.toList()),
            captionOverlay,
            () -> pendingScreenshot = true,
            () -> recording = !recording,
            () -> recording,
            this::toggleTapeRecording,
            () -> tapeRecorder != null && tapeRecorder.isActive());

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private TuiRunner runner;

    private MonitorContext ctx;
    private LogTab logTab;
    private RoutesTab routesTab;
    private ConsumersTab consumersTab;
    private EndpointsTab endpointsTab;
    private HttpTab httpTab;
    private HealthTab healthTab;
    private HistoryTab historyTab;
    private CircuitBreakerTab circuitBreakerTab;
    private ErrorsTab errorsTab;
    private MetricsTab metricsTab;
    private StartupTab startupTab;
    private ConfigurationTab configurationTab;

    // "More" dropdown state
    private boolean showMorePopup;
    private final ListState morePopupState = new ListState();
    private MonitorTab activeMoreTab;
    private int lastMoreSelection;
    private Line[] currentTabLabels;

    private ClassLoader classLoader;

    public CamelMonitor(CamelJBangMain main, ClassLoader classLoader) {
        super(main);
        this.classLoader = classLoader;
    }

    @Override
    public Integer doCall() throws Exception {
        System.setProperty("java.awt.headless", "true");

        // Configure TamboUI recording if --record is specified
        if (record != null) {
            Path tapeFile = Path.of(record);
            Path castFile = Path.of(record.replaceAll("\\.tape$", "") + ".cast");
            System.setProperty("tamboui.record", castFile.toAbsolutePath().toString());
            System.setProperty("tamboui.record.config", tapeFile.toAbsolutePath().toString());
            System.setProperty("tamboui.record.width", "200");
            System.setProperty("tamboui.record.height", "50");
            System.setProperty("tamboui.record.duration", "120000");
            System.setProperty("tamboui.record.fps", "10");
        }

        recording = record != null;

        // to make ServiceLoader work with tamboui for downloaded JARs
        Thread.currentThread().setContextClassLoader(classLoader);
        TuiHelper.preloadClasses(classLoader);

        // Create shared context and tab instances
        ctx = new MonitorContext(data, infraData);
        actionsPopup.setContext(ctx);
        actionsPopup.setResetStatsAction(this::resetStats);
        logTab = new LogTab(ctx);
        routesTab = new RoutesTab(ctx);
        consumersTab = new ConsumersTab(ctx);
        endpointsTab = new EndpointsTab(
                ctx,
                endpointInHistory, endpointOutHistory,
                endpointRemoteInHistory, endpointRemoteOutHistory,
                endpointRemoteStubInHistory, endpointRemoteStubOutHistory,
                endpointInSizeHistory, endpointOutSizeHistory,
                perEndpointInHistory, perEndpointOutHistory);
        httpTab = new HttpTab(ctx);
        healthTab = new HealthTab(ctx);
        historyTab = new HistoryTab(ctx, traces, traceFilePositions);
        circuitBreakerTab = new CircuitBreakerTab(ctx, cbSuccessHistory, cbFailHistory);
        errorsTab = new ErrorsTab(ctx);
        metricsTab = new MetricsTab(ctx);
        startupTab = new StartupTab(ctx);
        configurationTab = new ConfigurationTab(ctx);

        // Initial data load (synchronous before TUI starts)
        refreshDataSync();

        eventLog = new TuiEventLog(500);
        Path mcpJsonFile = null;
        if (mcp) {
            mcpServer = new TuiMcpServer(mcpPort, this);
            try {
                mcpServer.start();
                actionsPopup.setMcpEnabled(true, mcpPort, mcpServer::getConnectedClient, mcpServer::getActivityLog);
                mcpJsonFile = writeMcpJson(mcpPort);
            } catch (java.net.BindException e) {
                System.err.println("MCP server failed to start: port " + mcpPort + " is already in use.");
                System.err.println("Use --mcp-port to specify a different port, e.g.: camel tui --mcp --mcp-port 8124");
                mcpServer = null;
                mcp = false;
            }
        }

        try (var tui = TuiBackendHelper.createTuiRunner()) {
            this.runner = tui;
            ctx.runner = tui;
            actionsPopup.setScheduler(tui.scheduler());
            // Intercept Ctrl+C: quit the TUI cleanly instead of letting
            // the JVM tear down the classloader while we're still running
            Signal.handle(new Signal("INT"), sig -> tui.quit());
            tui.run(
                    this::handleEvent,
                    this::render);
        } finally {
            if (mcpServer != null) {
                mcpServer.stop();
            }
            deleteMcpJson(mcpJsonFile);
            this.runner = null;
        }
        return 0;
    }

    // ---- Event Handling ----

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent ke) {
            if (eventLog != null) {
                String elabel = keyLabel(ke);
                if (elabel != null) {
                    eventLog.record(elabel, elabel);
                }
            }
            if (recording) {
                String label = keyLabel(ke);
                if (label != null) {
                    recentKeys.add(new KeyRecord(label, System.currentTimeMillis()));
                }
            }
            if (ke.hasCtrl() && ke.isChar('r')) {
                toggleTapeRecording();
                return true;
            }
            if (tapeRecorder != null && tapeRecorder.isActive() && !mcpInjectedKey) {
                String label = keyLabel(ke);
                if (label != null) {
                    tapeRecorder.recordKey(label);
                }
            }
            if (captionOverlay.isVisible()) {
                if (captionOverlay.handleKeyEvent(ke)) {
                    return true;
                }
            }
            if (ke.hasCtrl() && ke.isChar('k')) {
                recording = !recording;
                return true;
            }
            if (ke.hasCtrl() && ke.isChar('t')) {
                captionOverlay.openInline();
                return true;
            }
            if (actionsPopup.isVisible()) {
                return actionsPopup.handleKeyEvent(ke);
            }
            // "More" tab popup
            if (showMorePopup) {
                if (ke.isCancel()) {
                    showMorePopup = false;
                    return true;
                }
                if (ke.isUp()) {
                    morePopupState.selectPrevious();
                    return true;
                }
                if (ke.isDown()) {
                    morePopupState.selectNext(4);
                    return true;
                }
                if (ke.isConfirm()) {
                    showMorePopup = false;
                    Integer sel = morePopupState.selected();
                    if (sel != null) {
                        lastMoreSelection = sel;
                        activeMoreTab = switch (sel) {
                            case 0 -> circuitBreakerTab;
                            case 1 -> configurationTab;
                            case 2 -> consumersTab;
                            case 3 -> startupTab;
                            default -> null;
                        };
                        if (activeMoreTab != null) {
                            selectCurrentIntegration();
                            tabsState.select(TAB_MORE);
                            activeMoreTab.onTabSelected();
                        }
                    }
                    return true;
                }
                return true;
            }
            // Kill confirm dialog: Enter to confirm, Esc/any other key to cancel
            if (showKillConfirm) {
                if (ke.isConfirm()) {
                    showKillConfirm = false;
                    stopSelectedProcess(true);
                } else {
                    showKillConfirm = false;
                }
                return true;
            }

            // Escape: navigate back — delegate to active tab first
            if (ke.isCancel()) {
                MonitorTab tab = activeTab();
                if (tab != null && tab.handleEscape()) {
                    return true;
                }
                if (tabsState.selected() != TAB_OVERVIEW) {
                    tabsState.select(TAB_OVERVIEW);
                    return true;
                }
                if (ctx.selectedPid != null) {
                    ctx.selectedPid = null;
                    ctx.lastSelectedName = null;
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
            // When infra is selected, only Overview (1) and Log (2) are available
            if (ke.isChar('1')) {
                return handleTabKey(TAB_OVERVIEW);
            }
            if (ke.isChar('2')) {
                return handleTabKey(TAB_LOG);
            }
            if (!isInfraSelected()) {
                if (ke.isChar('3')) {
                    return handleTabKey(TAB_ROUTES);
                }
                if (ke.isChar('4')) {
                    return handleTabKey(TAB_ENDPOINTS);
                }
                if (ke.isChar('5')) {
                    return handleTabKey(TAB_HTTP);
                }
                if (ke.isChar('6')) {
                    return handleTabKey(TAB_HEALTH);
                }
                if (ke.isChar('7')) {
                    return handleTabKey(TAB_HISTORY);
                }
                if (ke.isChar('8')) {
                    return handleTabKey(TAB_ERRORS);
                }
                if (ke.isChar('9')) {
                    return handleTabKey(TAB_METRICS);
                }
                if (ke.isChar('0')) {
                    return handleTabKey(TAB_MORE);
                }
            }

            // Tab cycling (check Shift+Tab before Tab since Tab binding also matches Shift+Tab)
            if (ke.isFocusPrevious()) {
                if (isInfraSelected()) {
                    // Cycle between Overview and Log only
                    int prev = tabsState.selected() == TAB_OVERVIEW ? TAB_LOG : TAB_OVERVIEW;
                    tabsState.select(prev);
                } else {
                    int prev = (tabsState.selected() - 1 + NUM_TABS) % NUM_TABS;
                    if (prev != TAB_OVERVIEW) {
                        selectCurrentIntegration();
                    }
                    tabsState.select(prev);
                }
                return true;
            }
            if (ke.isFocusNext()) {
                if (isInfraSelected()) {
                    int next = tabsState.selected() == TAB_OVERVIEW ? TAB_LOG : TAB_OVERVIEW;
                    tabsState.select(next);
                } else {
                    int next = (tabsState.selected() + 1) % NUM_TABS;
                    if (next != TAB_OVERVIEW) {
                        selectCurrentIntegration();
                    }
                    tabsState.select(next);
                }
                return true;
            }

            // Screenshot: Shift+F5
            if (ke.isKey(KeyCode.F5) && ke.hasShift()) {
                takeScreenshot();
                return true;
            }

            // F2 opens actions menu (global)
            if (ke.isKey(KeyCode.F2)) {
                if (tabsState.selected() == TAB_ROUTES && routesTab != null) {
                    actionsPopup.setPreSelectedRouteId(routesTab.selectedRouteId());
                }
                actionsPopup.open();
                return true;
            }

            // Tab-specific keys — delegate to active tab first
            int tab = tabsState.selected();
            MonitorTab activeTab = activeTab();

            // Navigation (all tabs)
            if (ke.isUp()) {
                if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                    return true;
                }
                navigateUp();
                return true;
            }
            if (ke.isDown()) {
                if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                    return true;
                }
                navigateDown();
                return true;
            }
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                    return true;
                }
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                    return true;
                }
                return true;
            }
            if (ke.isLeft()) {
                if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                    return true;
                }
            }
            if (ke.isRight()) {
                if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                    return true;
                }
            }
            if (ke.isHome()) {
                if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                    return true;
                }
            }
            if (ke.isEnd()) {
                if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                    return true;
                }
            }

            // Enter to drill into selected integration
            if (ke.isConfirm() && tab == TAB_OVERVIEW) {
                selectCurrentIntegration();
                if (ctx.selectedPid != null) {
                    tabsState.select(TAB_LOG);
                }
                return true;
            }

            // Overview tab: sort
            if (tab == TAB_OVERVIEW && ke.isChar('s')) {
                overviewSortIndex = (overviewSortIndex + 1) % OVERVIEW_SORT_COLUMNS.length;
                overviewSort = OVERVIEW_SORT_COLUMNS[overviewSortIndex];
                overviewSortReversed = false;
                return true;
            }
            if (tab == TAB_OVERVIEW && ke.isChar('S')) {
                overviewSortReversed = !overviewSortReversed;
                return true;
            }
            // Overview tab: cycle chart between all integrations, selected only, and off
            if (tab == TAB_OVERVIEW && ke.isCharIgnoreCase('a')) {
                chartMode = (chartMode + 1) % 3;
                return true;
            }
            // Overview tab: start/stop all routes for selected integration (not infra)
            if (tab == TAB_OVERVIEW && ke.isChar('p') && ctx.selectedPid != null && !isInfraSelected()) {
                IntegrationInfo selInfo = findSelectedIntegration();
                if (selInfo != null) {
                    String cmd = selInfo.routeStarted > 0 ? "stop" : "start";
                    sendRouteCommand(ctx.selectedPid, "*", cmd);
                }
                return true;
            }
            // Overview tab: stop process (SIGTERM) for selected integration or infra
            if (tab == TAB_OVERVIEW && ke.isChar('x') && ctx.selectedPid != null) {
                stopSelectedProcess(false);
                return true;
            }
            // Overview tab: kill process (SIGKILL) — show confirm dialog first
            if (tab == TAB_OVERVIEW && ke.isChar('X') && ctx.selectedPid != null) {
                showKillConfirm = true;
                return true;
            }
            // Overview tab: cold restart (stop + re-launch) for selected integration
            if (tab == TAB_OVERVIEW && ke.isChar('r') && ctx.selectedPid != null && !isInfraSelected()) {
                restartSelectedProcess();
                return true;
            }
            // Delegate remaining keys to active tab
            if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                return true;
            }
        }
        if (event instanceof PasteEvent pe) {
            if (actionsPopup.isVisible()) {
                actionsPopup.handlePaste(pe.text());
                return true;
            }
        }
        if (event instanceof TickEvent) {
            long now = System.currentTimeMillis();
            boolean keyProcessed = false;
            PendingKey pk;
            while ((pk = pendingKeys.peek()) != null && now >= pk.fireAt()) {
                pendingKeys.poll();
                mcpInjectedKey = true;
                handleEvent(pk.event(), runner);
                mcpInjectedKey = false;
                keyProcessed = true;
            }
            if (keyProcessed) {
                return true;
            }
            actionsPopup.tick(now);
            captionOverlay.tick(now);
            if (recording && !recentKeys.isEmpty()) {
                long cutoff = now - 2000;
                recentKeys.removeIf(k -> k.timestamp() < cutoff);
            }
            long interval = routesTab.isShowDiagram() ? Math.max(refreshInterval, 1000) : refreshInterval;
            if (now - lastRefresh >= interval) {
                refreshData();
                routesTab.refreshDiagramIfNeeded();
                return true;
            }
            return !routesTab.hasImageDiagram();
        }
        return false;
    }

    private String keyLabel(KeyEvent ke) {
        if (ke.isKey(KeyCode.ENTER)) {
            return "Enter";
        }
        if (ke.isKey(KeyCode.ESCAPE)) {
            return "Esc";
        }
        if (ke.isKey(KeyCode.TAB)) {
            return ke.hasShift() ? "⇧Tab" : "Tab";
        }
        if (ke.isKey(KeyCode.UP)) {
            return "↑";
        }
        if (ke.isKey(KeyCode.DOWN)) {
            return "↓";
        }
        if (ke.isKey(KeyCode.LEFT)) {
            return "←";
        }
        if (ke.isKey(KeyCode.RIGHT)) {
            return "→";
        }
        if (ke.isKey(KeyCode.PAGE_UP)) {
            return "PgUp";
        }
        if (ke.isKey(KeyCode.PAGE_DOWN)) {
            return "PgDn";
        }
        if (ke.isKey(KeyCode.HOME)) {
            return "Home";
        }
        if (ke.isKey(KeyCode.END)) {
            return "End";
        }
        if (ke.isKey(KeyCode.BACKSPACE)) {
            return "⌫";
        }
        for (int i = 1; i <= 12; i++) {
            try {
                KeyCode fKey = KeyCode.valueOf("F" + i);
                if (ke.isKey(fKey)) {
                    return "F" + i;
                }
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        if (ke.code() == KeyCode.CHAR) {
            String s = ke.string();
            if (" ".equals(s)) {
                return "Space";
            }
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private boolean handleTabKey(int tab) {
        if (tab != TAB_OVERVIEW) {
            selectCurrentIntegration();
        }
        if (tab == TAB_LOG) {
            logTab.onTabSelected();
        }
        if (tab == TAB_HISTORY && ctx.selectedPid != null) {
            try {
                long pid = Long.parseLong(ctx.selectedPid);
                refreshHistoryData(List.of(pid));
                refreshTraceData(List.of(pid));
            } catch (NumberFormatException e) {
                // ignore
            }
            historyTab.onTabSelected();
        }
        if (tab == TAB_ERRORS && ctx.selectedPid != null) {
            try {
                long pid = Long.parseLong(ctx.selectedPid);
                refreshErrorData(List.of(pid));
            } catch (NumberFormatException e) {
                // ignore
            }
            errorsTab.onTabSelected();
        }
        if (tab == TAB_MORE) {
            showMorePopup = !showMorePopup;
            if (showMorePopup) {
                morePopupState.select(lastMoreSelection);
            }
            return true;
        }
        showMorePopup = false;
        tabsState.select(tab);
        return true;
    }

    // Returns integrations in the same order the overview table renders them.
    // Must be used anywhere that translates a table row index to a PID.
    private List<IntegrationInfo> sortedOverviewInfos() {
        List<IntegrationInfo> infos = new ArrayList<>(data.get());
        infos.sort(this::sortOverview);
        return infos;
    }

    private void selectCurrentIntegration() {
        if (ctx.selectedPid != null) {
            if (findSelectedIntegration() != null || findSelectedInfra() != null) {
                return;
            }
            ctx.selectedPid = null;
        }
        List<IntegrationInfo> infos = sortedOverviewInfos();
        List<InfraInfo> infras = infraData.get();
        Integer sel = overviewTableState.selected();
        if (sel != null && sel >= 0) {
            String pid = overviewIndexToPid(infos, infras, sel);
            if (pid != null) {
                ctx.selectedPid = pid;
            }
        } else if (infos.size() == 1) {
            ctx.selectedPid = infos.get(0).pid;
        }
    }

    private void syncSelectedPid() {
        List<IntegrationInfo> infos = sortedOverviewInfos();
        List<InfraInfo> infras = infraData.get();
        Integer sel = overviewTableState.selected();
        String newPid = null;
        if (sel != null && sel >= 0) {
            newPid = overviewIndexToPid(infos, infras, sel);
        }
        if (newPid == null && infos.size() == 1) {
            newPid = infos.get(0).pid;
        }
        if (newPid != null && !newPid.equals(ctx.selectedPid)) {
            ctx.selectedPid = newPid;
            ctx.lastSelectedName = null;
            resetIntegrationTabState();
        }
    }

    private String overviewIndexToPid(List<IntegrationInfo> infos, List<InfraInfo> infras, int index) {
        if (index < infos.size()) {
            return infos.get(index).pid;
        }
        int infraIndex = index - infos.size() - (overviewDividerIndex >= 0 ? 1 : 0);
        if (infraIndex >= 0 && infraIndex < infras.size()) {
            return infras.get(infraIndex).pid;
        }
        return null;
    }

    private void resetIntegrationTabState() {
        routesTab.onIntegrationChanged();
        httpTab.onIntegrationChanged();
        logTab.onIntegrationChanged();
        historyTab.onIntegrationChanged();
    }

    private void navigateUp() {
        MonitorTab tab = activeTab();
        if (tab != null) {
            tab.navigateUp();
        } else {
            overviewTableState.selectPrevious();
            // Skip the divider row
            Integer sel = overviewTableState.selected();
            if (sel != null && overviewDividerIndex >= 0 && sel == overviewDividerIndex) {
                overviewTableState.selectPrevious();
            }
            syncSelectedPid();
        }
    }

    private void navigateDown() {
        MonitorTab tab = activeTab();
        if (tab != null) {
            tab.navigateDown();
        } else {
            int totalRows = overviewTotalRows();
            overviewTableState.selectNext(totalRows);
            // Skip the divider row
            Integer sel = overviewTableState.selected();
            if (sel != null && overviewDividerIndex >= 0 && sel == overviewDividerIndex) {
                overviewTableState.selectNext(totalRows);
            }
            syncSelectedPid();
        }
    }

    private int overviewTotalRows() {
        int integrations = sortedOverviewInfos().size();
        int infra = infraData.get().size();
        return integrations + (infra > 0 ? 1 : 0) + infra;
    }

    // ---- Rendering ----

    private void render(Frame frame) {
        Rect area = frame.area();

        // Layout: header (1 row) + spacer (1 row) + tabs (2 rows) + spacer (1 row) + content (fill) + footer (1 row)
        List<Rect> mainChunks = Layout.vertical()
                .constraints(
                        Constraint.length(1),
                        Constraint.length(1),
                        Constraint.length(2),
                        Constraint.length(1),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, mainChunks.get(0));
        // mainChunks.get(1) is the empty spacer row
        renderTabs(frame, mainChunks.get(2));
        // mainChunks.get(3) is the empty spacer row between tabs and content
        renderContent(frame, mainChunks.get(4));
        if (showKillConfirm) {
            renderKillConfirm(frame, mainChunks.get(4));
        }
        actionsPopup.render(frame, mainChunks.get(4));
        if (captionOverlay.isCaptionVisible()) {
            captionOverlay.render(frame, mainChunks.get(4));
        }
        renderFooter(frame, mainChunks.get(5));

        lastBuffer = frame.buffer();
        renderGeneration++;

        if (pendingScreenshot) {
            pendingScreenshot = false;
            takeScreenshot();
        }
    }

    private void renderHeader(Frame frame, Rect area) {
        List<IntegrationInfo> infos = data.get();
        String camelVersion = VersionHelper.extractCamelVersion();
        long activeCount = infos.stream().filter(i -> !i.vanishing).count();

        List<Span> titleSpans = new ArrayList<>();
        titleSpans.add(Span.styled(" Camel TUI", Style.EMPTY.fg(Color.rgb(0xF6, 0x91, 0x23)).bold()));
        titleSpans.add(Span.raw("  "));
        titleSpans.add(Span.styled(camelVersion != null ? "v" + camelVersion : "", Style.EMPTY.fg(Color.GREEN)));
        titleSpans.add(Span.raw("  "));
        titleSpans.add(Span.styled(activeCount + " integration(s)", Style.EMPTY.fg(Color.CYAN)));
        long activeInfra = infraData.get().stream().filter(i -> !i.vanishing).count();
        if (activeInfra > 0) {
            titleSpans.add(Span.raw("  "));
            titleSpans.add(Span.styled(activeInfra + " infra(s)", Style.EMPTY.fg(Color.MAGENTA)));
        }
        if (ctx.selectedPid != null) {
            titleSpans.add(Span.raw("  "));
            InfraInfo selInfra = findSelectedInfra();
            if (selInfra != null) {
                titleSpans.add(Span.styled("selected: " + selectedName(), Style.EMPTY.fg(Color.MAGENTA)));
            } else {
                titleSpans.add(Span.styled("selected: " + selectedName(), Style.EMPTY.fg(Color.YELLOW)));
            }
        }
        if (actionsPopup.notification() != null) {
            titleSpans.add(Span.raw("  "));
            Style style = actionsPopup.notificationError() ? Style.EMPTY.fg(Color.RED) : Style.EMPTY.fg(Color.GREEN);
            titleSpans.add(Span.styled(actionsPopup.notification(), style));
        }
        if (monitorNotification != null) {
            if (System.currentTimeMillis() > monitorNotificationExpiry) {
                monitorNotification = null;
            } else {
                titleSpans.add(Span.raw("  "));
                Style style = monitorNotificationError ? Style.EMPTY.fg(Color.RED) : Style.EMPTY.fg(Color.GREEN);
                titleSpans.add(Span.styled(monitorNotification, style));
            }
        }
        Line titleLine = Line.from(titleSpans);

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).build(),
                area);
    }

    private void renderTabs(Frame frame, Rect area) {
        boolean infraSelected = isInfraSelected();

        if (infraSelected) {
            // Infra mode: only Overview and Log tabs
            Line[] labels = {
                    Line.from(" 1 Overview "),
                    Line.from(" 2 Log "),
            };

            // Map real tab index to infra tab index for highlight
            int infraTabIdx = tabsState.selected() == TAB_LOG ? 1 : 0;
            TabsState infraTabsState = new TabsState(infraTabIdx);

            Tabs tabs = Tabs.builder()
                    .titles(labels)
                    .highlightStyle(Style.EMPTY.fg(Color.rgb(0xF6, 0x91, 0x23)).bold())
                    .divider(Span.styled(" | ", Style.EMPTY.dim()))
                    .build();

            Rect labelsArea = area.height() >= 2
                    ? new Rect(area.x(), area.y() + 1, area.width(), 1)
                    : area;
            frame.renderStatefulWidget(tabs, labelsArea, infraTabsState);
            return;
        }

        // Compute notification counts (0 if no integration selected)
        List<IntegrationInfo> infos = data.get();
        long activeCount = infos.stream().filter(i -> !i.vanishing).count();
        IntegrationInfo sel = findSelectedIntegration();
        boolean hasSelection = ctx.selectedPid != null && sel != null;
        int routeCount = hasSelection ? sel.routes.size() : 0;
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
        int historyCount = hasSelection ? historyTab.historyEntries.size() : 0;
        boolean hasTraces = hasSelection && !traces.get().isEmpty();
        int httpCount = hasSelection ? sel.httpEndpoints.size() : 0;

        int metricsCount = hasSelection ? sel.meters.size() : 0;

        // Row 0: label-only titles — fixed width so the tab bar never shifts when badges appear
        Line[] labels = {
                Line.from(" 1 Overview "),
                Line.from(" 2 Log "),
                Line.from(routesTab.isTopMode() ? " 3  Top  " : " 3 Route "),
                Line.from(" 4 Endpoint "),
                Line.from(" 5 HTTP "),
                Line.from(" 6 Health "),
                Line.from(" 7 Inspect "),
                Line.from(" 8 Errors "),
                Line.from(" 9 Metrics "),
                Line.from(" 0 More▾ "),
        };
        currentTabLabels = labels;

        Tabs tabs = Tabs.builder()
                .titles(labels)
                .highlightStyle(Style.EMPTY.fg(Color.rgb(0xF6, 0x91, 0x23)).bold())
                .divider(Span.styled(" | ", Style.EMPTY.dim()))
                .build();

        // Row 1: labels (Tabs widget renders at the top of whatever rect it receives)
        Rect labelsArea = area.height() >= 2
                ? new Rect(area.x(), area.y() + 1, area.width(), 1)
                : area;
        frame.renderStatefulWidget(tabs, labelsArea, tabsState);

        // Row 0: badge counters centered above each tab label
        if (area.height() >= 2) {
            int badgeY = area.y();
            int dividerW = CharWidth.of(" | ");

            String[] badgeTexts = { "", "", "", "", "", "", "", "", "", "" };
            Style[] badgeStyles = new Style[labels.length];
            Style yellow = Style.EMPTY.fg(Color.YELLOW).bold();
            Style cyan = Style.EMPTY.fg(Color.CYAN).bold();
            Style red = Style.EMPTY.fg(Color.LIGHT_RED).bold();
            for (int j = 0; j < badgeStyles.length; j++) {
                badgeStyles[j] = yellow;
            }

            if (activeCount > 0) {
                badgeTexts[TAB_OVERVIEW] = "(" + activeCount + ")";
            }
            if (routeCount > 0) {
                badgeTexts[TAB_ROUTES] = "(" + routeCount + ")";
            }
            if (endpointCount > 0) {
                badgeTexts[TAB_ENDPOINTS] = "(" + endpointCount + ")";
            }
            if (httpCount > 0) {
                badgeTexts[TAB_HTTP] = "(" + httpCount + ")";
            }
            if (healthDownCount > 0) {
                badgeTexts[TAB_HEALTH] = "(" + healthDownCount + " DOWN)";
                badgeStyles[TAB_HEALTH] = red;
            } else if (healthCount > 0) {
                badgeTexts[TAB_HEALTH] = "(" + healthCount + ")";
            }
            if (hasTraces) {
                badgeTexts[TAB_HISTORY] = "(*)";
                badgeStyles[TAB_HISTORY] = cyan;
            } else if (historyCount > 0) {
                badgeTexts[TAB_HISTORY] = "(" + historyCount + ")";
            }
            if (metricsCount > 0) {
                badgeTexts[TAB_METRICS] = "(" + metricsCount + ")";
            }
            if (cbOpenCount > 0) {
                badgeTexts[TAB_MORE] = "(" + cbOpenCount + " OPEN)";
                badgeStyles[TAB_MORE] = red;
            }
            int errorCount = hasSelection ? sel.errorCount : 0;
            if (errorCount > 0) {
                badgeTexts[TAB_ERRORS] = "(" + errorCount + ")";
                badgeStyles[TAB_ERRORS] = red;
            }

            int tabX = 0;
            for (int i = 0; i < labels.length; i++) {
                if (i > 0) {
                    tabX += dividerW;
                }
                int tabW = labels[i].width();
                if (!badgeTexts[i].isEmpty()) {
                    int badgeW = CharWidth.of(badgeTexts[i]);
                    int startX = area.x() + tabX + Math.max(0, (tabW - badgeW) / 2);
                    frame.buffer().setString(startX, badgeY, badgeTexts[i], badgeStyles[i]);
                }
                tabX += tabW;
            }
        }
    }

    private void renderContent(Frame frame, Rect area) {
        // Clear the content area before rendering the active tab. Without this, styled cells
        // from the previous tab (e.g. RED error text in the log detail) can bleed through when
        // switching tabs if TamboUI's buffer diff does not reset every cell in the region.
        frame.buffer().clear(area);
        MonitorTab tab = activeTab();
        if (tab != null) {
            tab.render(frame, area);
        } else {
            renderOverview(frame, area);
        }
        // Render "More" popup overlay when visible
        if (showMorePopup) {
            renderMorePopup(frame, area);
        }
    }

    private void renderMorePopup(Frame frame, Rect area) {
        int popupW = 22;
        int popupH = 6;
        // Position just below the "0 More▾" tab label
        int dividerW = CharWidth.of(" | ");
        int tabBarX = 0;
        Line[] tabLabels = currentTabLabels;
        if (tabLabels != null) {
            for (int i = 0; i < tabLabels.length - 1; i++) {
                tabBarX += tabLabels[i].width();
                tabBarX += dividerW;
            }
        }
        int x = area.left() + tabBarX;
        int y = area.top();
        if (x + popupW > area.right()) {
            x = Math.max(area.left(), area.right() - popupW);
        }
        Rect popup = new Rect(x, y, Math.min(popupW, area.width() - (x - area.left())), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        ListItem[] items = {
                ListItem.from("  Circuit Breaker"),
                ListItem.from("  Configuration"),
                ListItem.from("  Consumers"),
                ListItem.from("  Startup"),
        };
        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" More Tabs ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, morePopupState);
    }

    private MonitorTab activeTab() {
        return switch (tabsState.selected()) {
            case TAB_LOG -> logTab;
            case TAB_ROUTES -> routesTab;
            case TAB_ENDPOINTS -> endpointsTab;
            case TAB_HEALTH -> healthTab;
            case TAB_HISTORY -> historyTab;
            case TAB_HTTP -> httpTab;
            case TAB_ERRORS -> errorsTab;
            case TAB_METRICS -> metricsTab;
            case TAB_MORE -> activeMoreTab;
            default -> null;
        };
    }

    // ---- Tab 1: Overview ----

    private void renderOverview(Frame frame, Rect area) {
        List<IntegrationInfo> infos = sortedOverviewInfos();
        List<InfraInfo> infraInfos = infraData.get();

        // Build unified row list: integrations, then divider, then infra
        int integrationCount = infos.size();
        int infraCount = infraInfos.size();
        overviewDividerIndex = infraCount > 0 ? integrationCount : -1;

        // Keep the table selection index tracking the same PID across sort changes and data refreshes
        if (ctx.selectedPid != null) {
            for (int i = 0; i < infos.size(); i++) {
                if (ctx.selectedPid.equals(infos.get(i).pid)) {
                    overviewTableState.select(i);
                    break;
                }
            }
            for (int i = 0; i < infraInfos.size(); i++) {
                if (ctx.selectedPid.equals(infraInfos.get(i).pid)) {
                    int tableIndex = integrationCount + (overviewDividerIndex >= 0 ? 1 : 0) + i;
                    overviewTableState.select(tableIndex);
                    break;
                }
            }
        }

        // Split: table + chart or info panel
        boolean hasSparkline = chartMode != CHART_OFF && !throughputHistory.isEmpty() && !isInfraSelected();
        boolean showInfoPanel = isInfraSelected() && findSelectedInfra() != null && !hasSparkline;
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(Constraint.fill());
        if (hasSparkline) {
            constraints.add(Constraint.length(14));
        } else if (showInfoPanel) {
            constraints.add(Constraint.length(10));
        }
        List<Rect> chunks = Layout.vertical()
                .constraints(constraints)
                .split(area);

        // Integration table
        List<Row> rows = new ArrayList<>();
        for (IntegrationInfo info : infos) {
            if (info.vanishing) {
                long elapsed = System.currentTimeMillis() - info.vanishStart;
                float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                int gray = (int) (100 * fade);
                Style dimStyle = Style.EMPTY.fg(Color.indexed(232 + Math.min(gray / 4, 23)));

                String vanishName = "\ud83d\udc2b " + (info.name != null ? info.name : "");
                rows.add(Row.from(
                        Cell.from(Span.styled(info.pid, dimStyle)),
                        Cell.from(Span.styled(vanishName, dimStyle)),
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

                String nameText = "🐫 " + (info.name != null ? info.name : "");
                Line nameLine = info.devMode
                        ? Line.from(
                                Span.styled(nameText, Style.EMPTY.fg(Color.CYAN)),
                                Span.styled(" [dev]", Style.EMPTY.fg(Color.YELLOW).dim()))
                        : Line.from(Span.styled(nameText, Style.EMPTY.fg(Color.CYAN)));
                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(nameLine),
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
                Cell.from(Span.styled(overviewSortLabel("VERSION", "version"), overviewSortStyle("version"))),
                centerCell("READY", 5, Style.EMPTY.bold()),
                Cell.from(Span.styled(overviewSortLabel("STATUS", "status"), overviewSortStyle("status"))),
                Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                rightCell("ROUTE", 7, Style.EMPTY.bold()),
                rightCell("MSG/S", 8, Style.EMPTY.bold()),
                rightCell(overviewSortLabel("TOTAL", "total"), 8, overviewSortStyle("total")),
                rightCell(overviewSortLabel("FAIL", "fail"), 6, overviewSortStyle("fail")),
                rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold())));

        // Divider row between integrations and infra services
        if (overviewDividerIndex >= 0) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled("─── Infra Services ───", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from("")));
        }

        // Infra rows adapted to 12-column layout
        for (InfraInfo info : infraInfos) {
            if (info.vanishing) {
                long elapsed = System.currentTimeMillis() - info.vanishStart;
                float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                int gray = (int) (100 * fade);
                Style dimStyle = Style.EMPTY.fg(Color.indexed(232 + Math.min(gray / 4, 23)));
                String vanishAlias = "🔧  " + info.alias;
                rows.add(Row.from(
                        Cell.from(Span.styled(info.pid, dimStyle)),
                        Cell.from(Span.styled(vanishAlias, dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("✖ Stopped", Style.EMPTY.fg(Color.LIGHT_RED).dim())),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle))));
            } else {
                Style statusStyle = info.alive ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.fg(Color.LIGHT_RED);
                String statusText = info.alive ? "Running" : "Stopped";
                String infraAlias = "🔧  " + info.alias;
                String version = info.serviceVersion != null ? info.serviceVersion : "";
                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(Span.styled(infraAlias, Style.EMPTY.fg(Color.MAGENTA))),
                        Cell.from(version),
                        Cell.from(""),
                        Cell.from(Span.styled(statusText, statusStyle)),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from("")));
            }
        }

        Style overviewHighlight = Style.EMPTY.fg(Color.WHITE).bold().onBlue();
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
                .highlightStyle(overviewHighlight)
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Overview ").build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), overviewTableState);

        // Split green/red throughput bar chart with Y and X axes
        if (hasSparkline && chunks.size() > 1) {
            Rect chartTotalArea = chunks.get(chunks.size() - 1);

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
            String chartPid = (chartMode == CHART_SINGLE && ctx.selectedPid != null) ? ctx.selectedPid : null;
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
            if (chartMode == CHART_SINGLE && ctx.selectedPid != null) {
                IntegrationInfo chartSel = findSelectedIntegration();
                String chartName = chartSel != null ? TuiHelper.truncate(chartSel.name, 12) : ctx.selectedPid;
                titleLine = Line.from(
                        Span.raw(" ["),
                        Span.styled(chartName, Style.EMPTY.fg(Color.YELLOW)),
                        Span.raw(String.format("] Throughput: %d msg/s  ", curTp)),
                        Span.styled("■", Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN))),
                        Span.raw(String.format(" ok:%d  ", curOk)),
                        Span.styled("■", Style.EMPTY.fg(Color.RED)),
                        Span.raw(String.format(" fail:%d ", curFailed)));
            } else {
                titleLine = Line.from(
                        Span.raw(String.format(" [All] Throughput: %d msg/s  ", curTp)),
                        Span.styled("■", Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN))),
                        Span.raw(String.format(" ok:%d  ", curOk)),
                        Span.styled("■", Style.EMPTY.fg(Color.RED)),
                        Span.raw(String.format(" fail:%d ", curFailed)));
            }

            // Build bar groups (ok=bright green, failed=red), no bar value labels
            List<BarGroup> groups = new ArrayList<>();
            for (int i = 0; i < renderPoints; i++) {
                long failed = Math.min(mergedFailed[i], mergedTotal[i]);
                long ok = Math.max(0, mergedTotal[i] - failed);
                groups.add(BarGroup.of(
                        Bar.builder().value(ok).textValue("").style(Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN))).build(),
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
        } else if (showInfoPanel) {
            renderOverviewInfoPanel(frame, chunks.get(chunks.size() - 1));
        }
    }

    private void renderOverviewInfoPanel(Frame frame, Rect area) {
        // Check if an infra service is selected — show connection details instead
        InfraInfo infraSel = findSelectedInfra();
        if (infraSel != null) {
            renderInfraInfoPanel(frame, area, infraSel);
            return;
        }

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
                        ? sel.platform + " v" + sel.platformVersion
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
            if (sel.profile != null || sel.reloaded > 0) {
                List<Span> profileSpans = new ArrayList<>();
                if (sel.profile != null) {
                    profileSpans.add(Span.styled("Profile: ", dim));
                    profileSpans.add(Span.raw(sel.profile));
                }
                if (sel.reloaded > 0) {
                    if (!profileSpans.isEmpty()) {
                        profileSpans.add(Span.raw("    "));
                    }
                    profileSpans.add(Span.styled("Reload: ", dim));
                    profileSpans.add(Span.raw(String.valueOf(sel.reloaded)));
                }
                lines.add(Line.from(profileSpans));
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
            LoadAvg cpu = cpuLoadAvg.get(sel.pid);
            boolean hasInfl = sel.inflightLoad01 != null && !sel.inflightLoad01.isEmpty();
            if (cpu != null || hasInfl) {
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(Span.styled("Load (1m/5m/15m):", dim)));
                if (cpu != null) {
                    lines.add(Line.from(
                            Span.styled("CPU:  ", dim),
                            Span.raw(cpu.format("%.1f / %.1f / %.1f %%"))));
                }
                if (hasInfl) {
                    lines.add(Line.from(
                            Span.styled("Infl: ", dim),
                            Span.raw(sel.inflightLoad01 + " / " + sel.inflightLoad05 + " / " + sel.inflightLoad15)));
                }
            }
        } else {
            lines.add(Line.from(Span.raw("-")));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), inner);
    }

    private void renderInfraInfoPanel(Frame frame, Rect area, InfraInfo infra) {
        Block infoBlock = Block.builder().borderType(BorderType.ROUNDED).build();
        frame.renderWidget(infoBlock, area);
        Rect inner = infoBlock.inner(area);
        List<Line> lines = new ArrayList<>();
        Style dim = Style.EMPTY.dim();
        lines.add(Line.from(
                Span.styled("Service: ", dim),
                Span.styled(infra.alias, Style.EMPTY.fg(Color.MAGENTA))));
        lines.add(Line.from(Span.raw("")));
        // Show connection properties with cleaned-up key names
        for (Map.Entry<String, Object> e : infra.properties.entrySet()) {
            String key = e.getKey();
            // Strip "get" prefix and capitalize
            if (key.startsWith("get") && key.length() > 3) {
                key = key.substring(3);
            }
            String value = String.valueOf(e.getValue());
            lines.add(Line.from(
                    Span.styled(key + ": ", dim),
                    Span.raw(TuiHelper.truncate(value, inner.width() - key.length() - 2))));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), inner);
    }

    // ---- Overview helpers ----

    private int sortOverview(IntegrationInfo a, IntegrationInfo b) {
        if (a.vanishing != b.vanishing) {
            return a.vanishing ? 1 : -1;
        }
        int result = switch (overviewSort) {
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
            case "version" -> {
                String va = a.camelVersion != null ? a.camelVersion : "";
                String vb = b.camelVersion != null ? b.camelVersion : "";
                yield va.compareToIgnoreCase(vb);
            }
            case "status" -> Integer.compare(a.state, b.state);
            case "total" -> Long.compare(b.exchangesTotal, a.exchangesTotal);
            case "fail" -> Long.compare(b.failed, a.failed);
            default -> 0;
        };
        return overviewSortReversed ? -result : result;
    }

    private String overviewSortLabel(String label, String column) {
        return sortLabel(label, column, overviewSort, overviewSortReversed);
    }

    private Style overviewSortStyle(String column) {
        return sortStyle(column, overviewSort);
    }

    private void stopSelectedProcess(boolean forceKill) {
        if (ctx.selectedPid == null) {
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            return;
        }
        if (isInfraSelected()) {
            InfraInfo infra = findSelectedInfra();
            if (infra != null) {
                Path camelDir = CommandLineHelper.getCamelDir();
                PathUtils.deleteFile(camelDir.resolve("infra-" + infra.alias + "-" + infra.pid + ".json"));
                PathUtils.deleteFile(camelDir.resolve("infra-" + infra.alias + "-" + infra.pid + ".log"));
            }
            if (forceKill) {
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
            }
        } else {
            ProcessHandle.of(pid).ifPresent(ph -> {
                if (forceKill) {
                    ph.destroyForcibly();
                    Path camelDir = CommandLineHelper.getCamelDir();
                    PathUtils.deleteFile(camelDir.resolve(ctx.selectedPid + ".log"));
                    PathUtils.deleteFile(camelDir.resolve(ctx.selectedPid + "-status.json"));
                    PathUtils.deleteFile(camelDir.resolve(ctx.selectedPid + "-action.json"));
                    PathUtils.deleteFile(camelDir.resolve(ctx.selectedPid + "-output.json"));
                    PathUtils.deleteFile(camelDir.resolve(ctx.selectedPid + "-trace.json"));
                    PathUtils.deleteFile(camelDir.resolve(ctx.selectedPid + "-history.json"));
                    PathUtils.deleteFile(camelDir.resolve(ctx.selectedPid + "-debug.json"));
                    PathUtils.deleteFile(camelDir.resolve(ctx.selectedPid + "-receive.json"));
                } else {
                    ph.destroy();
                }
            });
        }
    }

    private void restartSelectedProcess() {
        if (ctx.selectedPid == null || isInfraSelected()) {
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            return;
        }
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            return;
        }
        ProcessHandle ph = ProcessHandle.of(pid).orElse(null);
        if (ph == null) {
            return;
        }

        // capture command line before stopping
        Optional<String> cmdOpt = ph.info().command();
        Optional<String[]> argsOpt = ph.info().arguments();
        Optional<String> cmdLineOpt = ph.info().commandLine();

        String name = info.name;
        String directory = info.directory;

        // remember name so the restarted process gets auto-selected
        ctx.lastSelectedName = name;

        // stop gracefully
        ph.destroy();
        setNotification("Restarting: " + name, false);

        // re-launch in background after process terminates
        if (runner != null) {
            runner.scheduler().execute(() -> {
                try {
                    // wait for termination (max 10 seconds, then force kill)
                    CompletableFuture<ProcessHandle> exitFuture = ph.onExit().toCompletableFuture();
                    try {
                        exitFuture.get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        ph.destroyForcibly();
                        Thread.sleep(500);
                    }

                    // build command
                    List<String> cmd = new ArrayList<>();
                    if (cmdOpt.isPresent() && argsOpt.isPresent() && argsOpt.get().length > 0) {
                        cmd.add(cmdOpt.get());
                        Collections.addAll(cmd, argsOpt.get());
                    } else if (cmdLineOpt.isPresent()) {
                        cmd.addAll(parseCommandLine(cmdLineOpt.get()));
                    }

                    if (cmd.isEmpty()) {
                        runner.runOnRenderThread(
                                () -> setNotification("Cannot restart: command line not available", true));
                        return;
                    }

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    if (directory != null) {
                        pb.directory(new File(directory));
                    }
                    pb.redirectErrorStream(true);
                    Path outputFile = Files.createTempFile("camel-restart-", ".log");
                    outputFile.toFile().deleteOnExit();
                    pb.redirectOutput(outputFile.toFile());
                    pb.start();

                    runner.runOnRenderThread(() -> setNotification("Restarted: " + name, false));
                } catch (Exception e) {
                    runner.runOnRenderThread(
                            () -> setNotification("Restart failed: " + e.getMessage(), true));
                }
            });
        }
    }

    private void setNotification(String message, boolean error) {
        monitorNotification = message;
        monitorNotificationError = error;
        monitorNotificationExpiry = System.currentTimeMillis() + 5000;
    }

    static List<String> parseCommandLine(String commandLine) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private void resetStats() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return;
        }
        String pid = info.pid;
        JsonObject root = new JsonObject();
        root.put("action", "reset-stats");
        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);
        // Clear local sparkline history — overview
        throughputHistory.remove(pid);
        failedHistory.remove(pid);
        throughputSamples.remove(pid);
        previousExchangesTime.remove(pid);
        // Clear local sparkline history — endpoints
        endpointInHistory.remove(pid);
        endpointOutHistory.remove(pid);
        endpointSamples.remove(pid);
        previousEndpointTime.remove(pid);
        endpointRemoteInHistory.remove(pid);
        endpointRemoteOutHistory.remove(pid);
        endpointRemoteSamples.remove(pid);
        previousEndpointRemoteTime.remove(pid);
        endpointRemoteStubInHistory.remove(pid);
        endpointRemoteStubOutHistory.remove(pid);
        endpointRemoteStubSamples.remove(pid);
        previousEndpointRemoteStubTime.remove(pid);
        endpointInSizeHistory.remove(pid);
        endpointOutSizeHistory.remove(pid);
        previousEndpointSizeTime.remove(pid);
        String perEpPrefix = pid + "|";
        perEndpointInHistory.keySet().removeIf(k -> k.startsWith(perEpPrefix));
        perEndpointOutHistory.keySet().removeIf(k -> k.startsWith(perEpPrefix));
        perEndpointSamples.keySet().removeIf(k -> k.startsWith(perEpPrefix));
        previousPerEndpointTime.keySet().removeIf(k -> k.startsWith(perEpPrefix));
    }

    private void sendRouteCommand(String pid, String routeId, String command) {
        JsonObject root = new JsonObject();
        root.put("action", "route");
        root.put("id", routeId);
        root.put("command", command);
        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);
    }

    private void renderKillConfirm(Frame frame, Rect area) {
        String name = selectedName();
        String msg = " Kill " + name + " (PID: " + ctx.selectedPid + ")? ";
        int popupW = Math.max(34, msg.length() + 4);
        int popupH = 6;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.LIGHT_RED))
                .title(" Confirm Kill ")
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(
                                Line.from(Span.raw("")),
                                Line.from(Span.styled(msg, Style.EMPTY.fg(Color.LIGHT_RED).bold())),
                                Line.from(Span.raw("")),
                                Line.from(
                                        Span.raw("  "),
                                        Span.styled("Enter", Style.EMPTY.bold()),
                                        Span.raw(" confirm  "),
                                        Span.styled("Esc", Style.EMPTY.bold()),
                                        Span.raw(" cancel"))))
                        .build(),
                inner);
    }

    private void renderFooter(Frame frame, Rect area) {
        // Show screenshot flash message briefly
        String msg = screenshotMessage;
        if (msg != null && System.currentTimeMillis() - screenshotMessageTime < 3000) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled(" " + msg, Style.EMPTY.fg(Color.GREEN)))),
                    area);
            return;
        }
        screenshotMessage = null;

        List<Span> spans = new ArrayList<>();

        if (captionOverlay.isCaptionVisible()) {
            captionOverlay.renderFooter(spans);
            frame.renderWidget(Paragraph.from(Line.from(spans)), area);
            return;
        }

        if (showMorePopup) {
            hint(spans, "Up/Down", "select");
            hint(spans, "Enter", "open");
            hint(spans, "Esc", "close");
        } else {
            MonitorTab tab = activeTab();

            if (tab != null) {
                tab.renderFooter(spans);
                // Insert F2 after the first hint (Esc) — each hint is 2 spans (key + label)
                int insertPos = Math.min(2, spans.size());
                List<Span> f2Spans = new ArrayList<>();
                hint(f2Spans, "F2", "actions");
                spans.addAll(insertPos, f2Spans);
            } else {
                renderOverviewFooter(spans);
            }
        }

        List<Span> rightSpans = new ArrayList<>();

        if (recording && !recentKeys.isEmpty()) {
            long now = System.currentTimeMillis();
            int maxKeys = Math.min(recentKeys.size(), 8);
            List<KeyRecord> visible = recentKeys.subList(recentKeys.size() - maxKeys, recentKeys.size());
            for (KeyRecord kr : visible) {
                long age = now - kr.timestamp();
                Style style = age < 1000
                        ? Style.EMPTY.fg(Color.WHITE).bold().onBlue()
                        : Style.EMPTY.dim();
                rightSpans.add(Span.styled(" " + kr.label() + " ", style));
            }
        }

        if (mcp) {
            if (!rightSpans.isEmpty()) {
                rightSpans.add(Span.raw("  "));
            }
            String client = mcpServer != null ? mcpServer.getConnectedClient() : null;
            boolean active = mcpServer != null && mcpServer.isRecentActivity();
            String mcpLabel = "MCP :" + mcpPort;
            String suffix;
            Style labelStyle;
            Style suffixStyle;
            if (client != null) {
                suffix = active ? " ●" : " ○";
                mcpLabel += " (" + client + ")";
                labelStyle = Style.EMPTY.fg(Color.GREEN);
                suffixStyle = Style.EMPTY.fg(active ? Color.GREEN : Color.DARK_GRAY);
            } else {
                suffix = " ✗";
                labelStyle = Style.EMPTY.dim();
                suffixStyle = Style.EMPTY.fg(Color.RED);
            }
            rightSpans.add(Span.styled(mcpLabel, labelStyle));
            rightSpans.add(Span.styled(suffix, suffixStyle));
        }

        if (!rightSpans.isEmpty()) {
            int hintsWidth = spans.stream().mapToInt(s -> s.width()).sum();
            int rightWidth = rightSpans.stream().mapToInt(s -> s.width()).sum();
            int gap = Math.max(1, area.width() - hintsWidth - rightWidth);
            spans.add(Span.raw(" ".repeat(gap)));
            spans.addAll(rightSpans);
        }

        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }

    private void renderOverviewFooter(List<Span> spans) {
        if (actionsPopup.isVisible()) {
            actionsPopup.renderFooter(spans);
            return;
        }
        hint(spans, "q", "quit");
        hint(spans, "F2", "actions");
        if (ctx.selectedPid != null) {
            hint(spans, "Esc", "unselect");
        }
        hint(spans, "↑↓", "navigate");
        if (!isInfraSelected()) {
            hint(spans, "s", "sort");
            hint(spans, "a", "chart " + switch (chartMode) {
                case CHART_ALL -> "[all]";
                case CHART_SINGLE -> "[single]";
                default -> "[off]";
            });
        }
        if (ctx.selectedPid != null && !isInfraSelected()) {
            IntegrationInfo selInfo = findSelectedIntegration();
            if (selInfo != null) {
                hint(spans, "p", selInfo.routeStarted > 0 ? "stop routes" : "start routes");
            }
        }
        if (ctx.selectedPid != null) {
            if (!isInfraSelected()) {
                hint(spans, "r", "restart");
            }
            hint(spans, "x", "stop");
            hint(spans, "X", "kill");
        }
        hint(spans, isInfraSelected() ? "1-2" : "1-9", "tabs");
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
        String currentSelectedPid = ctx.selectedPid;
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
                                updateEndpointHistory(info);
                                updateCbHistory(info);
                                updateLoadMetrics(ph, info);
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
                    endpointInHistory.remove(entry.getKey());
                    endpointOutHistory.remove(entry.getKey());
                    endpointSamples.remove(entry.getKey());
                    previousEndpointTime.remove(entry.getKey());
                    endpointRemoteInHistory.remove(entry.getKey());
                    endpointRemoteOutHistory.remove(entry.getKey());
                    endpointRemoteSamples.remove(entry.getKey());
                    previousEndpointRemoteTime.remove(entry.getKey());
                    endpointRemoteStubInHistory.remove(entry.getKey());
                    endpointRemoteStubOutHistory.remove(entry.getKey());
                    endpointRemoteStubSamples.remove(entry.getKey());

                    endpointInSizeHistory.remove(entry.getKey());
                    endpointOutSizeHistory.remove(entry.getKey());
                    previousEndpointSizeTime.remove(entry.getKey());
                    previousEndpointRemoteStubTime.remove(entry.getKey());
                    cpuLoadAvg.remove(entry.getKey());
                    prevCpuSample.remove(entry.getKey());
                    String vanishCbPrefix = entry.getKey() + "/";
                    cbSuccessHistory.keySet().removeIf(k -> k.startsWith(vanishCbPrefix));
                    cbFailHistory.keySet().removeIf(k -> k.startsWith(vanishCbPrefix));
                    cbThroughputSamples.keySet().removeIf(k -> k.startsWith(vanishCbPrefix));
                    previousCbTime.keySet().removeIf(k -> k.startsWith(vanishCbPrefix));
                    String vanishEpPrefix = entry.getKey() + "|";
                    perEndpointInHistory.keySet().removeIf(k -> k.startsWith(vanishEpPrefix));
                    perEndpointOutHistory.keySet().removeIf(k -> k.startsWith(vanishEpPrefix));
                    perEndpointSamples.keySet().removeIf(k -> k.startsWith(vanishEpPrefix));
                    previousPerEndpointTime.keySet().removeIf(k -> k.startsWith(vanishEpPrefix));
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

            // Clear stale selection when the selected integration is gone
            if (ctx.selectedPid != null && !isInfraSelected()) {
                boolean stillAlive = infos.stream()
                        .anyMatch(i -> ctx.selectedPid.equals(i.pid) && !i.vanishing);
                if (!stillAlive) {
                    // Remember the name for auto-reselect when the integration restarts
                    IntegrationInfo gone = infos.stream()
                            .filter(i -> ctx.selectedPid.equals(i.pid))
                            .findFirst().orElse(null);
                    if (gone != null) {
                        ctx.lastSelectedName = gone.name;
                    }
                    ctx.selectedPid = null;
                }
            }

            // Auto-select a newly launched integration
            String autoSelect = actionsPopup.getPendingAutoSelect();
            if (autoSelect != null) {
                for (IntegrationInfo info : infos) {
                    if (!info.vanishing && autoSelect.equalsIgnoreCase(info.name)) {
                        ctx.selectedPid = info.pid;
                        ctx.lastSelectedName = null;
                        actionsPopup.clearPendingAutoSelect();
                        break;
                    }
                }
            }

            // Auto-reselect by remembered name when the integration restarts
            if (ctx.selectedPid == null && ctx.lastSelectedName != null && !isInfraSelected()) {
                for (IntegrationInfo info : infos) {
                    if (!info.vanishing && ctx.lastSelectedName.equalsIgnoreCase(info.name)) {
                        ctx.selectedPid = info.pid;
                        ctx.lastSelectedName = null;
                        break;
                    }
                }
            }

            // Discover running infra services
            refreshInfraData();

            // Auto-select first infra service when no active integrations exist
            if (ctx.selectedPid == null && !infraData.get().isEmpty()
                    && infos.stream().noneMatch(i -> !i.vanishing)) {
                List<InfraInfo> infras = infraData.get();
                if (!infras.isEmpty()) {
                    int firstInfraIndex = infos.size() + (infras.size() > 0 ? 1 : 0);
                    overviewTableState.select(firstInfraIndex);
                    ctx.selectedPid = infras.get(0).pid;
                }
            }

            // Refresh log data only when the Log tab is visible
            if (tabsState.selected() == TAB_LOG) {
                String logPid = null;
                String logFileName = null;
                InfraInfo selInfra = findSelectedInfra();
                if (selInfra != null) {
                    logPid = selInfra.pid;
                    logFileName = "infra-" + selInfra.alias + "-" + selInfra.pid + ".log";
                } else {
                    IntegrationInfo selected = findSelectedIntegration();
                    if (selected != null) {
                        logPid = selected.pid;
                        logFileName = selected.pid + ".log";
                    }
                }
                if (logPid != null) {
                    if (!logPid.equals(logTab.logFilePid)) {
                        logTab.mutableFilteredEntries.clear();
                        logTab.logFilePos = -1;
                        logTab.logTotalLinesRead = 0;
                        logTab.logLineBuffer.setLength(0);
                    }
                    List<String> newRawLines = new ArrayList<>();
                    logTab.readNewLogLinesFromFile(logPid, logFileName, newRawLines);
                    if (!newRawLines.isEmpty()) {
                        logTab.logTotalLinesRead += newRawLines.size();
                        for (String line : newRawLines) {
                            logTab.mutableFilteredEntries.add(LogTab.parseLogLine(line));
                        }
                        if (logTab.mutableFilteredEntries.size() > MAX_LOG_LINES) {
                            logTab.mutableFilteredEntries.subList(0, logTab.mutableFilteredEntries.size() - MAX_LOG_LINES)
                                    .clear();
                        }
                        logTab.filteredLogEntries = new ArrayList<>(logTab.mutableFilteredEntries);
                    }
                }
            }

            // Refresh error data only when the Errors tab is visible
            if (tabsState.selected() == TAB_ERRORS) {
                refreshErrorData(pids);
            }

            // Refresh trace data only when the History tab is visible
            if (tabsState.selected() == TAB_HISTORY) {
                if (historyTab.historyRefreshRequested) {
                    historyTab.historyRefreshRequested = false;
                    refreshHistoryData(pids);
                }
                refreshTraceData(pids);
            }
        } catch (Exception e) {
            // ignore refresh errors
        }
    }

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
                        info.serviceVersion = objToString(info.properties.get("serviceVersion"));
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

    private void updateEndpointHistory(IntegrationInfo info) {
        long inTotal = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction))
                .mapToLong(ep -> ep.hits).sum();
        long outTotal = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction))
                .mapToLong(ep -> ep.hits).sum();
        long inRemote = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction) && ep.remote)
                .mapToLong(ep -> ep.hits).sum();
        long outRemote = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction) && ep.remote)
                .mapToLong(ep -> ep.hits).sum();
        long inRemoteStub = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction) && (ep.remote || ep.stub))
                .mapToLong(ep -> ep.hits).sum();
        long outRemoteStub = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction) && (ep.remote || ep.stub))
                .mapToLong(ep -> ep.hits).sum();

        long now = System.currentTimeMillis();
        String pid = info.pid;

        recordEndpointSample(pid, now, inTotal, outTotal,
                endpointSamples, previousEndpointTime, endpointInHistory, endpointOutHistory);
        recordEndpointSample(pid, now, inRemote, outRemote,
                endpointRemoteSamples, previousEndpointRemoteTime, endpointRemoteInHistory, endpointRemoteOutHistory);
        recordEndpointSample(pid, now, inRemoteStub, outRemoteStub,
                endpointRemoteStubSamples, previousEndpointRemoteStubTime,
                endpointRemoteStubInHistory, endpointRemoteStubOutHistory);

        // Record payload size snapshots (mean body size per direction)
        long inMeanSize = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction) && ep.meanBodySize >= 0)
                .mapToLong(ep -> ep.meanBodySize).max().orElse(0);
        long outMeanSize = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction) && ep.meanBodySize >= 0)
                .mapToLong(ep -> ep.meanBodySize).max().orElse(0);
        Long lastSizeTime = previousEndpointSizeTime.get(pid);
        if (lastSizeTime == null || now - lastSizeTime >= 1000) {
            previousEndpointSizeTime.put(pid, now);
            LinkedList<Long> inSizeHist = endpointInSizeHistory.computeIfAbsent(pid, k -> new LinkedList<>());
            inSizeHist.add(inMeanSize);
            while (inSizeHist.size() > MAX_ENDPOINT_CHART_POINTS) {
                inSizeHist.remove(0);
            }
            LinkedList<Long> outSizeHist = endpointOutSizeHistory.computeIfAbsent(pid, k -> new LinkedList<>());
            outSizeHist.add(outMeanSize);
            while (outSizeHist.size() > MAX_ENDPOINT_CHART_POINTS) {
                outSizeHist.remove(0);
            }
        }

        // Per-endpoint rate history (keyed by pid|uri)
        Map<String, long[]> perUri = new LinkedHashMap<>();
        for (EndpointInfo ep : info.endpoints) {
            if (ep.uri == null) {
                continue;
            }
            long[] inOut = perUri.computeIfAbsent(ep.uri, k -> new long[2]);
            if ("in".equals(ep.direction)) {
                inOut[0] += ep.hits;
            } else if ("out".equals(ep.direction)) {
                inOut[1] += ep.hits;
            }
        }
        for (Map.Entry<String, long[]> entry : perUri.entrySet()) {
            String key = pid + "|" + entry.getKey();
            long[] inOut = entry.getValue();
            recordEndpointSample(key, now, inOut[0], inOut[1],
                    perEndpointSamples, previousPerEndpointTime,
                    perEndpointInHistory, perEndpointOutHistory);
        }
    }

    private void recordEndpointSample(
            String pid, long now, long inTotal, long outTotal,
            Map<String, LinkedList<long[]>> samplesMap, Map<String, Long> prevTimeMap,
            Map<String, LinkedList<Long>> inHistMap, Map<String, LinkedList<Long>> outHistMap) {
        LinkedList<long[]> samples = samplesMap.computeIfAbsent(pid, k -> new LinkedList<>());
        samples.add(new long[] { now, inTotal, outTotal });
        while (!samples.isEmpty() && now - samples.get(0)[0] > 1000) {
            samples.remove(0);
        }
        if (samples.size() >= 2) {
            long[] oldest = samples.get(0);
            long[] newest = samples.get(samples.size() - 1);
            long deltaMs = newest[0] - oldest[0];
            long inRate = deltaMs > 0 ? (newest[1] - oldest[1]) * 1000 / deltaMs : 0;
            long outRate = deltaMs > 0 ? (newest[2] - oldest[2]) * 1000 / deltaMs : 0;
            Long lastTime = prevTimeMap.get(pid);
            if (lastTime == null || now - lastTime >= 1000) {
                prevTimeMap.put(pid, now);
                LinkedList<Long> inHist = inHistMap.computeIfAbsent(pid, k -> new LinkedList<>());
                inHist.add(Math.max(0, inRate));
                while (inHist.size() > MAX_ENDPOINT_CHART_POINTS) {
                    inHist.remove(0);
                }
                LinkedList<Long> outHist = outHistMap.computeIfAbsent(pid, k -> new LinkedList<>());
                outHist.add(Math.max(0, outRate));
                while (outHist.size() > MAX_ENDPOINT_CHART_POINTS) {
                    outHist.remove(0);
                }
            }
        }
    }

    private void updateCbHistory(IntegrationInfo info) {
        long now = System.currentTimeMillis();
        for (CircuitBreakerInfo cb : info.circuitBreakers) {
            if (cb.id == null) {
                continue;
            }
            String key = info.pid + "/" + cb.id;
            long success = cb.successfulCalls;
            long failed = cb.failedCalls;
            recordEndpointSample(key, now, success, failed,
                    cbThroughputSamples, previousCbTime, cbSuccessHistory, cbFailHistory);
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

    private void updateLoadMetrics(ProcessHandle ph, IntegrationInfo info) {
        String pid = info.pid;

        // CPU EWMA — compute % from ProcessHandle CPU duration delta
        Optional<Duration> durOpt = ph.info().totalCpuDuration();
        if (durOpt.isPresent()) {
            long cpuNanos = durOpt.get().toNanos();
            long wallMs = System.currentTimeMillis();
            long[] prev = prevCpuSample.get(pid);
            if (prev != null) {
                long deltaCpuNanos = cpuNanos - prev[0];
                long deltaWallNanos = (wallMs - prev[1]) * 1_000_000L;
                if (deltaWallNanos > 0) {
                    double cpuPct = (double) deltaCpuNanos / deltaWallNanos * 100.0;
                    cpuLoadAvg.computeIfAbsent(pid, k -> new LoadAvg()).update(Math.max(0, cpuPct));
                }
            }
            prevCpuSample.put(pid, new long[] { cpuNanos, wallMs });
        }
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
        boolean done = Boolean.TRUE.equals(json.get("done"));
        boolean failed = Boolean.TRUE.equals(json.get("failed"));
        entry.failed = failed;
        if (entry.failed) {
            entry.status = "Failed";
        } else if (done) {
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
            if (uri != null) {
                entry.processor = indent + "from[" + uri + "]";
            } else {
                entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
            }
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
        historyTab.historyEntries = allEntries;
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
            if (uri != null) {
                entry.processor = indent + "from[" + uri + "]";
            } else {
                entry.processor = indent + (entry.nodeLabel != null ? entry.nodeLabel : "");
            }
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
        info.devMode = context.getBooleanOrDefault("devMode", false);

        JsonObject runtime = (JsonObject) root.get("runtime");
        info.platform = runtime != null ? runtime.getString("platform") : null;
        info.platformVersion = runtime != null ? runtime.getString("platformVersion") : null;
        if ("Camel".equals(info.platform)) {
            String cl = ph.info().commandLine().orElse("");
            if (cl.contains("main.CamelJBang run")) {
                info.platform = "JBang";
                if (info.platformVersion == null) {
                    info.platformVersion = VersionHelper.getJBangVersion();
                }
            }
        }
        info.directory = runtime != null ? runtime.getString("directory") : null;
        info.javaVersion = runtime != null ? runtime.getString("javaVersion") : null;
        info.javaVendor = runtime != null ? runtime.getString("javaVendor") : null;
        info.javaVmName = runtime != null ? runtime.getString("javaVmName") : null;
        info.readmeFiles = runtime != null ? runtime.getString("readmeFiles") : null;

        Map<String, ?> stats = context.getMap("statistics");
        if (stats != null) {
            Object thp = stats.get("exchangesThroughput");
            if (thp != null) {
                info.throughput = thp.toString();
            }
            info.exchangesTotal = objToLong(stats.get("exchangesTotal"));
            info.failed = objToLong(stats.get("exchangesFailed"));
            info.inflight = objToLong(stats.get("exchangesInflight"));
            info.inflightLoad01 = objToString(stats.get("load01"));
            info.inflightLoad05 = objToString(stats.get("load05"));
            info.inflightLoad15 = objToString(stats.get("load15"));
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
            Map<String, ?> reloadStats = (Map<String, ?>) stats.get("reload");
            if (reloadStats != null) {
                info.reloaded = (int) objToLong(reloadStats.get("reloaded"));
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
                ri.supportsSuspension = rj.getBooleanOrDefault("supportsSuspension", false);
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
                    ri.lastTime = Math.max(0, objToLong(rs.get("lastProcessingTime")));
                    ri.deltaTime = objToLong(rs.get("deltaProcessingTime"));
                    ri.load01 = objToString(rs.get("load01"));
                    ri.load05 = objToString(rs.get("load05"));
                    ri.load15 = objToString(rs.get("load15"));
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
                            pi.deltaTime = objToLong(ps.get("deltaProcessingTime"));
                            pi.inflight = objToLong(ps.get("exchangesInflight"));
                            long tsStarted = objToLong(ps.get("lastCreatedExchangeTimestamp"));
                            if (tsStarted > 0) {
                                pi.sinceLastStarted = TimeUtils.printSince(tsStarted);
                            }
                            long tsCompleted = objToLong(ps.get("lastCompletedExchangeTimestamp"));
                            if (tsCompleted > 0) {
                                pi.sinceLastCompleted = TimeUtils.printSince(tsCompleted);
                            }
                            long tsFailed = objToLong(ps.get("lastFailedExchangeTimestamp"));
                            if (tsFailed > 0) {
                                pi.sinceLastFailed = TimeUtils.printSince(tsFailed);
                            }
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
                    ci.polling = Boolean.TRUE.equals(cj.get("polling"));
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
                    ep.minBodySize = TuiHelper.objToLong(ej.get("minBodySize"));
                    ep.maxBodySize = TuiHelper.objToLong(ej.get("maxBodySize"));
                    ep.meanBodySize = TuiHelper.objToLong(ej.get("meanBodySize"));
                    ep.minHeadersSize = TuiHelper.objToLong(ej.get("minHeadersSize"));
                    ep.maxHeadersSize = TuiHelper.objToLong(ej.get("maxHeadersSize"));
                    ep.meanHeadersSize = TuiHelper.objToLong(ej.get("meanHeadersSize"));
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

        // Enrich circuit breakers with processor statistics (matched by id)
        for (CircuitBreakerInfo cb : info.circuitBreakers) {
            if (cb.id != null) {
                for (RouteInfo ri : info.routes) {
                    for (ProcessorInfo pi : ri.processors) {
                        if (cb.id.equals(pi.id)) {
                            cb.total = pi.total;
                            cb.totalFailed = pi.failed;
                            cb.meanTime = pi.meanTime;
                            cb.minTime = pi.minTime;
                            cb.maxTime = pi.maxTime;
                            cb.inflight = pi.inflight;
                            cb.sinceLastStarted = pi.sinceLastStarted;
                            cb.sinceLastSuccess = pi.sinceLastCompleted;
                            cb.sinceLastFail = pi.sinceLastFailed;
                            break;
                        }
                    }
                }
            }
        }

        // Parse error count from error registry (full error data is loaded on demand by ErrorsTab)
        JsonObject errorsObj = (JsonObject) root.get("errors");
        if (errorsObj != null) {
            info.errorCount = errorsObj.getIntegerOrDefault("size", 0);
        }

        // Parse micrometer metrics (optional, only present when --observe is used)
        JsonObject micrometerObj = (JsonObject) root.get("micrometer");
        if (micrometerObj != null) {
            parseMicrometerMeters(micrometerObj, "counters", "counter", info);
            parseMicrometerMeters(micrometerObj, "gauges", "gauge", info);
            parseMicrometerMeters(micrometerObj, "timers", "timer", info);
            parseMicrometerMeters(micrometerObj, "longTaskTimers", "longTaskTimer", info);
            parseMicrometerMeters(micrometerObj, "distribution", "distribution", info);
        }

        // Parse REST DSL services
        JsonObject restsObj = (JsonObject) root.get("rests");
        if (restsObj != null) {
            JsonArray restList = (JsonArray) restsObj.get("rests");
            if (restList != null) {
                for (Object r : restList) {
                    JsonObject rj = (JsonObject) r;
                    HttpEndpointInfo ep = new HttpEndpointInfo();
                    ep.fromRest = true;
                    ep.url = rj.getString("url");
                    ep.method = rj.getString("method");
                    if (ep.method != null) {
                        ep.method = ep.method.toUpperCase(Locale.ENGLISH);
                    }
                    ep.consumes = rj.getString("consumes");
                    ep.produces = rj.getString("produces");
                    ep.description = rj.getString("description");
                    ep.contractFirst = Boolean.TRUE.equals(rj.get("contractFirst"));
                    ep.specification = Boolean.TRUE.equals(rj.get("specification"));
                    ep.routeId = rj.getString("routeId");
                    ep.operationId = rj.getString("operationId");
                    ep.specificationUri = rj.getString("specificationUri");
                    ep.state = rj.getString("state");
                    ep.inType = rj.getString("inType");
                    ep.outType = rj.getString("outType");
                    Long h = rj.getLong("hits");
                    if (h != null) {
                        ep.hits = h;
                    }
                    // derive path from url (strip scheme+host+port)
                    ep.path = extractPath(ep.url);
                    info.httpEndpoints.add(ep);
                }
            }
        }

        // Parse Platform-HTTP services
        JsonObject phpObj = (JsonObject) root.get("platform-http");
        if (phpObj != null) {
            info.httpServer = phpObj.getString("server");
            parseHttpEndpoints(phpObj, "endpoints", false, info);
            parseHttpEndpoints(phpObj, "managementEndpoints", true, info);
        }

        // Parse configuration properties (from PropertiesDevConsole)
        JsonObject propsObj = (JsonObject) root.get("properties");
        if (propsObj != null) {
            JsonArray propArr = (JsonArray) propsObj.get("properties");
            if (propArr != null) {
                for (Object p : propArr) {
                    JsonObject pj = (JsonObject) p;
                    String key = pj.getString("key");
                    if (key != null && !key.startsWith("camel.jbang.")) {
                        ConfigurationTab.ConfigProperty cp = new ConfigurationTab.ConfigProperty();
                        cp.key = key;
                        cp.value = objToString(pj.get("value"));
                        cp.defaultValue = pj.getString("defaultValue");
                        cp.source = pj.getString("source");
                        cp.location = pj.getString("location");
                        info.configProperties.add(cp);
                    }
                }
                info.configProperties.sort(ConfigurationTab::compareCamelFirst);
            }
        }

        return info;
    }

    private static void parseHttpEndpoints(JsonObject phpObj, String key, boolean management, IntegrationInfo info) {
        JsonArray arr = (JsonArray) phpObj.get(key);
        if (arr == null) {
            return;
        }
        for (Object e : arr) {
            JsonObject ej = (JsonObject) e;
            HttpEndpointInfo ep = new HttpEndpointInfo();
            ep.fromRest = false;
            ep.management = management;
            ep.server = phpObj.getString("server");
            ep.url = ej.getString("url");
            ep.path = ej.getString("path");
            ep.method = ej.getString("verbs");
            ep.consumes = ej.getString("consumes");
            ep.produces = ej.getString("produces");
            info.httpEndpoints.add(ep);
        }
    }

    private static String extractPath(String url) {
        if (url == null) {
            return null;
        }
        // strip scheme://host:port prefix — find third '/' or return url as-is
        int idx = url.indexOf("://");
        if (idx < 0) {
            return url;
        }
        int slash = url.indexOf('/', idx + 3);
        return slash >= 0 ? url.substring(slash) : "/";
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

    @SuppressWarnings("unchecked")
    private static void parseMicrometerMeters(
            JsonObject micrometerObj, String section, String type, IntegrationInfo info) {
        JsonArray arr = (JsonArray) micrometerObj.get(section);
        if (arr == null) {
            return;
        }
        for (Object o : arr) {
            JsonObject jo = (JsonObject) o;
            MicrometerMeterInfo m = new MicrometerMeterInfo();
            m.type = type;
            m.name = jo.getString("name");
            m.description = jo.getString("description");
            // parse tags
            JsonArray tagsArr = (JsonArray) jo.get("tags");
            if (tagsArr != null) {
                for (Object t : tagsArr) {
                    JsonObject tj = (JsonObject) t;
                    m.tags.add(new String[] { tj.getString("key"), tj.getString("value") });
                }
            }
            // parse type-specific values
            switch (type) {
                case "counter":
                    m.count = TuiHelper.objToLong(jo.get("count"));
                    break;
                case "gauge":
                    Object v = jo.get("value");
                    m.value = v instanceof Number n ? n.doubleValue() : null;
                    break;
                case "timer":
                    m.count = TuiHelper.objToLong(jo.get("count"));
                    m.mean = TuiHelper.objToLong(jo.get("mean"));
                    m.max = TuiHelper.objToLong(jo.get("max"));
                    m.total = TuiHelper.objToLong(jo.get("total"));
                    break;
                case "longTaskTimer":
                    Object at = jo.get("activeTasks");
                    m.activeTasks = at instanceof Number n ? n.intValue() : null;
                    m.mean = TuiHelper.objToLong(jo.get("mean"));
                    m.max = TuiHelper.objToLong(jo.get("max"));
                    m.total = TuiHelper.objToLong(jo.get("duration"));
                    break;
                case "distribution":
                    m.count = TuiHelper.objToLong(jo.get("count"));
                    Object dm = jo.get("mean");
                    m.meanDouble = dm instanceof Number n ? n.doubleValue() : null;
                    Object dx = jo.get("max");
                    m.maxDouble = dx instanceof Number n ? n.doubleValue() : null;
                    Object dt = jo.get("totalAmount");
                    m.totalDouble = dt instanceof Number n ? n.doubleValue() : null;
                    break;
                default:
                    break;
            }
            info.meters.add(m);
        }
    }

    @SuppressWarnings("unchecked")
    private static void parseKvArray(JsonArray arr, Map<String, Object> values, Map<String, String> types) {
        if (arr == null) {
            return;
        }
        for (Object o : arr) {
            JsonObject jo = (JsonObject) o;
            String key = jo.getString("key");
            if (key != null) {
                values.put(key, jo.get("value"));
                String type = jo.getString("type");
                if (type != null) {
                    types.put(key, type);
                }
            }
        }
    }

    private JsonObject loadErrorFile(long pid) {
        return TuiHelper.loadStatus(pid, this::getErrorFile);
    }

    private void refreshErrorData(List<Long> pids) {
        IntegrationInfo sel = findSelectedIntegration();
        if (sel == null) {
            return;
        }
        try {
            long pid = Long.parseLong(sel.pid);
            JsonObject root = loadErrorFile(pid);
            if (root == null) {
                return;
            }
            JsonArray errorList = (JsonArray) root.get("errors");
            if (errorList == null) {
                return;
            }
            List<ErrorInfo> parsed = new ArrayList<>();
            for (Object e : errorList) {
                JsonObject ej = (JsonObject) e;
                ErrorInfo ei = new ErrorInfo();
                ei.routeId = ej.getString("routeId");
                ei.nodeId = ej.getString("nodeId");
                ei.exchangeId = ej.getString("exchangeId");
                ei.handled = Boolean.TRUE.equals(ej.get("handled"));
                Long ts = ej.getLong("timestamp");
                if (ts != null) {
                    ei.timestamp = ts;
                }
                ei.location = ej.getString("location");
                ei.threadName = ej.getString("threadName");
                Long elapsed = ej.getLong("elapsed");
                if (elapsed != null) {
                    ei.elapsed = elapsed;
                }
                ei.endpointUri = ej.getString("endpointUri");
                ei.fromEndpointUri = ej.getString("fromEndpointUri");
                // exception
                JsonObject ex = (JsonObject) ej.get("exception");
                if (ex != null) {
                    ei.exceptionType = ex.getString("type");
                    ei.exceptionMessage = ex.getString("message");
                    ei.stackTrace = ex.getString("stackTrace");
                }
                // message history
                Object mhObj = ej.get("messageHistory");
                if (mhObj instanceof JsonArray mhArr) {
                    ei.messageHistory = new String[mhArr.size()];
                    for (int i = 0; i < mhArr.size(); i++) {
                        ei.messageHistory[i] = mhArr.get(i).toString();
                    }
                }
                // message (body, headers)
                JsonObject msg = (JsonObject) ej.get("message");
                if (msg != null) {
                    Object bodyObj = msg.get("body");
                    if (bodyObj instanceof JsonObject bodyJson) {
                        ei.body = bodyJson.getString("value");
                        ei.bodyType = bodyJson.getString("type");
                    } else if (bodyObj != null) {
                        ei.body = bodyObj.toString();
                    }
                    JsonArray hdrs = msg.getCollection("headers");
                    if (hdrs != null) {
                        parseKvArray(hdrs, ei.headers, ei.headerTypes);
                    }
                }
                // exchange properties and variables
                JsonArray props = ej.getCollection("exchangeProperties");
                if (props != null) {
                    parseKvArray(props, ei.properties, ei.propertyTypes);
                }
                JsonArray vars = ej.getCollection("exchangeVariables");
                if (vars != null) {
                    parseKvArray(vars, ei.variables, ei.variableTypes);
                }
                parsed.add(ei);
            }
            sel.errors.clear();
            sel.errors.addAll(parsed);
        } catch (Exception e) {
            // ignore
        }
    }

    // ---- Helpers ----

    private IntegrationInfo findSelectedIntegration() {
        return ctx.findSelectedIntegration();
    }

    private InfraInfo findSelectedInfra() {
        return ctx.findSelectedInfra();
    }

    private boolean isInfraSelected() {
        return ctx.isInfraSelected();
    }

    private String selectedName() {
        return ctx.selectedName();
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

    private void takeScreenshot() {
        Buffer buf = lastBuffer;
        if (buf == null) {
            return;
        }
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String baseName = "camel-tui-screenshot-" + timestamp;
            Path txtPath = Path.of(baseName + ".txt");
            Path ansPath = Path.of(baseName + ".ans");
            ExportRequest.export(buf).text().toFile(txtPath);
            ExportRequest.export(buf).text().options(o -> o.styles(true)).toFile(ansPath);
            screenshotMessage = "Screenshot saved to " + txtPath.toAbsolutePath() + " (and .ans with colors)";
            screenshotMessageTime = System.currentTimeMillis();
        } catch (IOException e) {
            screenshotMessage = "Screenshot failed: " + e.getMessage();
            screenshotMessageTime = System.currentTimeMillis();
        }
    }

    private static String objToString(Object o) {
        return o != null ? o.toString() : "";
    }

    private static long objToLong(Object o) {
        return TuiHelper.objToLong(o);
    }

    record KeyRecord(String label, long timestamp) {
    }

    record VanishingInfo(IntegrationInfo info, long startTime) {
    }

    record VanishingInfraInfo(InfraInfo info, long startTime) {
    }

    // ---- MCP .mcp.json lifecycle ----

    private static Path writeMcpJson(int port) {
        Path path = Path.of(".mcp.json");
        try {
            String json = "{\n"
                          + "  \"mcpServers\": {\n"
                          + "    \"camel-tui\": {\n"
                          + "      \"type\": \"http\",\n"
                          + "      \"url\": \"http://localhost:" + port + "/mcp\"\n"
                          + "    }\n"
                          + "  }\n"
                          + "}\n";
            Files.writeString(path, json);
            return path;
        } catch (IOException e) {
            return null;
        }
    }

    private static void deleteMcpJson(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // best effort
            }
        }
    }

    // ---- MCP accessor methods ----

    private static final String[] TAB_NAMES = {
            "Overview", "Log", "Routes", "Endpoints",
            "HTTP", "Health", "Inspect", "Errors", "Circuit Breaker", "Consumers"
    };

    Buffer getLastBuffer() {
        return lastBuffer;
    }

    long getRenderGeneration() {
        return renderGeneration;
    }

    boolean isKeystrokesVisible() {
        return recording;
    }

    TapeRecorder getTapeRecorder() {
        return tapeRecorder;
    }

    boolean isTapeRecording() {
        return tapeRecorder != null && tapeRecorder.isActive();
    }

    void startTapeRecording(String title) {
        tapeRecorder = new TapeRecorder();
        tapeRecorder.start(title);
    }

    void clearTapeRecorder() {
        tapeRecorder = null;
    }

    private void toggleTapeRecording() {
        if (tapeRecorder != null && tapeRecorder.isActive()) {
            String tape = tapeRecorder.stop();
            tapeRecorder = null;
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = "camel-tui-tape-" + timestamp + ".tape";
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(filename), tape);
                captionOverlay.showCaption("Tape saved: " + filename, 5);
            } catch (java.io.IOException e) {
                captionOverlay.showCaption("Failed to save tape: " + e.getMessage(), 5);
            }
        } else {
            tapeRecorder = new TapeRecorder();
            tapeRecorder.start(null);
            captionOverlay.showCaption("Tape recording started", 3);
        }
    }

    TuiEventLog getEventLog() {
        return eventLog;
    }

    int getActiveTabIndex() {
        return tabsState.selected();
    }

    String getActiveTabName() {
        int idx = tabsState.selected();
        return idx >= 0 && idx < TAB_NAMES.length ? TAB_NAMES[idx] : "Unknown";
    }

    String getSelectedPid() {
        return ctx != null ? ctx.selectedPid : null;
    }

    String getSelectedIntegrationName() {
        if (ctx == null) {
            return null;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.name : null;
    }

    int getIntegrationCount() {
        List<IntegrationInfo> list = data.get();
        return (int) list.stream().filter(i -> !i.vanishing).count();
    }

    boolean isCaptionVisible() {
        return captionOverlay.isCaptionVisible();
    }

    void showCaption(String text) {
        captionOverlay.showCaption(text);
    }

    void showCaption(String text, int durationSeconds) {
        captionOverlay.showCaption(text, durationSeconds);
    }

    String navigateToTab(String tabName) {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (TAB_NAMES[i].equalsIgnoreCase(tabName)) {
                handleTabKey(i);
                return TAB_NAMES[i];
            }
        }
        return null;
    }

    String selectIntegration(String nameOrPid) {
        List<IntegrationInfo> infos = data.get();
        for (IntegrationInfo info : infos) {
            if (info.vanishing) {
                continue;
            }
            if (nameOrPid.equals(info.pid)
                    || (info.name != null && info.name.equalsIgnoreCase(nameOrPid))) {
                ctx.selectedPid = info.pid;
                return info.name != null ? info.name : info.pid;
            }
        }
        return null;
    }

    List<String> getTabNames() {
        return List.of(TAB_NAMES);
    }

    List<String> getActionLabels() {
        return actionsPopup.getActionLabels();
    }

    SelectionContext getSelectionContext() {
        SelectionContext popup = actionsPopup.getSelectionContext();
        if (popup != null) {
            return popup;
        }
        if (tabsState.selected() == TAB_OVERVIEW) {
            List<IntegrationInfo> infos = sortedOverviewInfos();
            if (infos.isEmpty()) {
                return null;
            }
            List<String> items = infos.stream().map(i -> i.name != null ? i.name : i.pid).toList();
            Integer sel = overviewTableState.selected();
            return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Integrations");
        }
        MonitorTab tab = activeTab();
        return tab != null ? tab.getSelectionContext() : null;
    }

    List<String> getIntegrationNames() {
        return data.get().stream()
                .filter(i -> !i.vanishing)
                .map(i -> i.name != null ? i.name : i.pid)
                .toList();
    }

    int injectKeys(List<String> keys, int delayMs) {
        long fireAt = System.currentTimeMillis();
        int count = 0;
        for (String key : keys) {
            KeyEvent ke = parseKey(key);
            if (ke != null) {
                pendingKeys.add(new PendingKey(ke, fireAt));
                fireAt += delayMs;
                count++;
            }
        }
        return count;
    }

    static KeyEvent parseKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        boolean ctrl = false;
        boolean shift = false;
        String remainder = key;
        while (remainder.contains("+")) {
            int plus = remainder.indexOf('+');
            String mod = remainder.substring(0, plus).trim();
            remainder = remainder.substring(plus + 1).trim();
            if (mod.equalsIgnoreCase("Ctrl")) {
                ctrl = true;
            } else if (mod.equalsIgnoreCase("Shift")) {
                shift = true;
            }
        }

        KeyModifiers mods = KeyModifiers.of(ctrl, false, shift);

        KeyCode code = switch (remainder.toLowerCase(Locale.ROOT)) {
            case "enter", "return" -> KeyCode.ENTER;
            case "esc", "escape" -> KeyCode.ESCAPE;
            case "tab" -> KeyCode.TAB;
            case "backspace" -> KeyCode.BACKSPACE;
            case "delete", "del" -> KeyCode.DELETE;
            case "up" -> KeyCode.UP;
            case "down" -> KeyCode.DOWN;
            case "left" -> KeyCode.LEFT;
            case "right" -> KeyCode.RIGHT;
            case "home" -> KeyCode.HOME;
            case "end" -> KeyCode.END;
            case "pageup", "pgup" -> KeyCode.PAGE_UP;
            case "pagedown", "pgdn" -> KeyCode.PAGE_DOWN;
            case "f1" -> KeyCode.F1;
            case "f2" -> KeyCode.F2;
            case "f3" -> KeyCode.F3;
            case "f4" -> KeyCode.F4;
            case "f5" -> KeyCode.F5;
            case "f6" -> KeyCode.F6;
            case "f7" -> KeyCode.F7;
            case "f8" -> KeyCode.F8;
            case "f9" -> KeyCode.F9;
            case "f10" -> KeyCode.F10;
            case "f11" -> KeyCode.F11;
            case "f12" -> KeyCode.F12;
            case "space" -> null;
            default -> null;
        };

        if (code != null) {
            return KeyEvent.ofKey(code, mods);
        }
        if ("space".equalsIgnoreCase(remainder)) {
            return KeyEvent.ofChar(' ', mods);
        }
        if (remainder.length() == 1) {
            return KeyEvent.ofChar(remainder.charAt(0), mods);
        }
        return null;
    }

    private record PendingKey(KeyEvent event, long fireAt) {
    }

}
