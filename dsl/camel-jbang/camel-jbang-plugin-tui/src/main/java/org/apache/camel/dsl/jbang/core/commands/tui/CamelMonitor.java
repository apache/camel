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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.tabs.Tabs;
import dev.tamboui.widgets.tabs.TabsState;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import sun.misc.Signal;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

@Command(name = "monitor",
         description = "Live dashboard for monitoring Camel integrations",
         sortOptions = false)
public class CamelMonitor extends CamelCommand {

    private static final long VANISH_DURATION_MS = 6000;
    private static final long DEFAULT_REFRESH_MS = 100;
    private static final int MAX_SPARKLINE_POINTS = 60;
    private static final int MAX_ENDPOINT_CHART_POINTS = 60;
    private static final int MAX_HEAP_HISTORY_POINTS = 120;
    private static final long HEAP_SAMPLE_INTERVAL_MS = 5000;
    private static final int MAX_LOG_LINES = 3000;
    private static final int MAX_TRACES = 200;
    private static final int NUM_TABS = 10;

    // Tab indices
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_LOG = 1;
    private static final int TAB_DIAGRAM = 2;
    private static final int TAB_ROUTES = 3;
    private static final int TAB_ENDPOINTS = 4;
    private static final int TAB_HTTP = 5;
    private static final int TAB_HEALTH = 6;
    private static final int TAB_HISTORY = 7;
    private static final int TAB_ERRORS = 8;
    private static final int TAB_MORE = 9;

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

    // Heap memory usage history per PID (one point per 5 seconds, in bytes)
    private final Map<String, LinkedList<Long>> heapMemHistory = new ConcurrentHashMap<>();
    private final Map<String, Long> previousHeapTime = new ConcurrentHashMap<>();

    // Load averages (EWMA) — CPU%, per PID (inflight EWMA is read from the management JSON)
    private final Map<String, LoadAvg> cpuLoadAvg = new ConcurrentHashMap<>();
    private final Map<String, long[]> prevCpuSample = new ConcurrentHashMap<>();

    // Cached PID list — full process scan throttled to every 2 seconds
    private volatile List<Long> cachedPids = Collections.emptyList();
    private volatile long lastFullScanTime;

    // Trace/history data — shared between CamelMonitor and tabs
    private final AtomicReference<List<TraceEntry>> traces = new AtomicReference<>(Collections.emptyList());
    private final Map<String, Long> traceFilePositions = new ConcurrentHashMap<>();

    // selectedPid is stored on ctx (MonitorContext) so tabs can access it

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
    private final DrawOverlay drawOverlay = new DrawOverlay();
    private final HelpOverlay helpOverlay = new HelpOverlay();

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
    private DiagramTab diagramTab;
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
    private BeansTab beansTab;
    private BrowseTab browseTab;
    private ClasspathTab classpathTab;
    private InflightTab inflightTab;
    private MemoryTab memoryTab;
    private ThreadsTab threadsTab;
    private OverviewTab overviewTab;

    // "Switch integration" popup state
    private boolean showSwitchPopup;
    private final ListState switchPopupState = new ListState();

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
        diagramTab = new DiagramTab(ctx);
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
        beansTab = new BeansTab(ctx);
        browseTab = new BrowseTab(ctx);
        classpathTab = new ClasspathTab(ctx);
        inflightTab = new InflightTab(ctx);
        memoryTab = new MemoryTab(ctx, heapMemHistory);
        threadsTab = new ThreadsTab(ctx);
        overviewTab = new OverviewTab(
                ctx, throughputHistory, failedHistory, cpuLoadAvg,
                this::resetIntegrationTabState);

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
            actionsPopup.setResetScreenAction(() -> tui.terminal().clear());
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
            if (helpOverlay.isVisible()) {
                return helpOverlay.handleKeyEvent(ke);
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
                    morePopupState.selectNext(11);
                    return true;
                }
                // Shortcut keys for quick selection
                int shortcutSel = morePopupShortcut(ke);
                if (shortcutSel >= 0) {
                    morePopupState.select(shortcutSel);
                }
                if (ke.isConfirm() || shortcutSel >= 0) {
                    showMorePopup = false;
                    Integer sel = shortcutSel >= 0 ? shortcutSel : morePopupState.selected();
                    if (sel != null) {
                        lastMoreSelection = sel;
                        activeMoreTab = switch (sel) {
                            case 0 -> beansTab;
                            case 1 -> browseTab;
                            case 2 -> circuitBreakerTab;
                            case 3 -> classpathTab;
                            case 4 -> configurationTab;
                            case 5 -> consumersTab;
                            case 6 -> inflightTab;
                            case 7 -> memoryTab;
                            case 8 -> metricsTab;
                            case 9 -> startupTab;
                            case 10 -> threadsTab;
                            default -> null;
                        };
                        if (activeMoreTab != null) {
                            overviewTab.selectCurrentIntegration();
                            tabsState.select(TAB_MORE);
                            activeMoreTab.onTabSelected();
                        }
                    }
                    return true;
                }
                return true;
            }
            // "Switch integration" popup
            if (showSwitchPopup) {
                if (ke.isCancel()) {
                    showSwitchPopup = false;
                    return true;
                }
                List<IntegrationInfo> switchList = getNonVanishingIntegrations();
                if (ke.isUp()) {
                    switchPopupState.selectPrevious();
                    return true;
                }
                if (ke.isDown()) {
                    switchPopupState.selectNext(switchList.size());
                    return true;
                }
                if (ke.isConfirm()) {
                    showSwitchPopup = false;
                    Integer sel = switchPopupState.selected();
                    if (sel != null && sel >= 0 && sel < switchList.size()) {
                        IntegrationInfo chosen = switchList.get(sel);
                        ctx.selectedPid = chosen.pid;
                        ctx.lastSelectedName = chosen.name;
                        resetIntegrationTabState();
                        if (tabsState.selected() == TAB_LOG) {
                            refreshLogData();
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
            // Quit: q or Ctrl+c (skip when text input is active)
            boolean probeEditing = tabsState.selected() == TAB_HTTP && httpTab.isProbeMode();
            boolean logSearchActive = tabsState.selected() == TAB_LOG && logTab.isSearchInputActive();
            boolean textEditing = probeEditing || logSearchActive;
            if (!textEditing && (ke.isCharIgnoreCase('q') || ke.isCtrlC())) {
                runner.quit();
                return true;
            }
            if (ke.isCtrlC()) {
                runner.quit();
                return true;
            }
            // Tab switching with number keys (skip when text input is active)
            // When infra is selected, only Overview (1) and Log (2) are available
            if (!textEditing) {
                if (ke.isChar('1')) {
                    return handleTabKey(TAB_OVERVIEW);
                }
                if (ke.isChar('2')) {
                    return handleTabKey(TAB_LOG);
                }
                if (!isInfraSelected()) {
                    if (ke.isChar('3')) {
                        return handleTabKey(TAB_DIAGRAM);
                    }
                    if (ke.isChar('4')) {
                        return handleTabKey(TAB_ROUTES);
                    }
                    if (ke.isChar('5')) {
                        return handleTabKey(TAB_ENDPOINTS);
                    }
                    if (ke.isChar('6')) {
                        return handleTabKey(TAB_HTTP);
                    }
                    if (ke.isChar('7')) {
                        return handleTabKey(TAB_HEALTH);
                    }
                    if (ke.isChar('8')) {
                        return handleTabKey(TAB_HISTORY);
                    }
                    if (ke.isChar('9')) {
                        return handleTabKey(TAB_ERRORS);
                    }
                    if (ke.isChar('0')) {
                        return handleTabKey(TAB_MORE);
                    }
                }
            }

            // Tab cycling (check Shift+Tab before Tab since Tab binding also matches Shift+Tab)
            // Skip tab cycling when text input is active (Tab navigates fields)
            if (ke.isFocusPrevious() && !textEditing) {
                if (isInfraSelected()) {
                    // Cycle between Overview and Log only
                    int prev = tabsState.selected() == TAB_OVERVIEW ? TAB_LOG : TAB_OVERVIEW;
                    tabsState.select(prev);
                } else {
                    int prev = (tabsState.selected() - 1 + NUM_TABS) % NUM_TABS;
                    if (prev != TAB_OVERVIEW) {
                        overviewTab.selectCurrentIntegration();
                    }
                    tabsState.select(prev);
                }
                return true;
            }
            if (ke.isFocusNext() && !textEditing) {
                if (isInfraSelected()) {
                    int next = tabsState.selected() == TAB_OVERVIEW ? TAB_LOG : TAB_OVERVIEW;
                    tabsState.select(next);
                } else {
                    int next = (tabsState.selected() + 1) % NUM_TABS;
                    if (next != TAB_OVERVIEW) {
                        overviewTab.selectCurrentIntegration();
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

            // F1 opens context-sensitive help
            if (ke.isKey(KeyCode.F1)) {
                if (helpOverlay.isVisible()) {
                    helpOverlay.close();
                } else {
                    MonitorTab tab = activeTab();
                    if (tab != null) {
                        String help = tab.getHelpText();
                        if (help != null) {
                            helpOverlay.open(help);
                        }
                    }
                }
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

            // F3 opens switch integration popup
            if (ke.isKey(KeyCode.F3)) {
                List<IntegrationInfo> switchList = getNonVanishingIntegrations();
                if (switchList.size() > 1) {
                    showSwitchPopup = true;
                    // Pre-select the currently active integration
                    for (int i = 0; i < switchList.size(); i++) {
                        if (switchList.get(i).pid.equals(ctx.selectedPid)) {
                            switchPopupState.select(i);
                            break;
                        }
                    }
                }
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
                overviewTab.selectCurrentIntegration();
                if (ctx.selectedPid != null) {
                    tabsState.select(TAB_LOG);
                    refreshLogData();
                }
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
            if (httpTab.isProbeMode()) {
                httpTab.handlePaste(pe.text());
                return true;
            }
            if (logTab.isSearchInputActive()) {
                logTab.handlePaste(pe.text());
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
            drawOverlay.tick(now);
            captionOverlay.tick(now);
            if (recording && !recentKeys.isEmpty()) {
                long cutoff = now - 2000;
                recentKeys.removeIf(k -> k.timestamp() < cutoff);
            }
            boolean anyDiagramShowing = routesTab.isShowDiagram() || diagramTab.isShowDiagram();
            long interval = anyDiagramShowing ? Math.max(refreshInterval, 1000) : refreshInterval;
            if (now - lastRefresh >= interval) {
                refreshData();
                routesTab.refreshDiagramIfNeeded();
                diagramTab.refreshDiagramIfNeeded();
                return true;
            }
            return true;
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
            overviewTab.selectCurrentIntegration();
        }
        if (tab == TAB_LOG) {
            refreshLogData();
            logTab.onTabSelected();
        }
        if (tab == TAB_DIAGRAM) {
            diagramTab.onTabSelected();
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

    private List<Long> selectedPidAsList() {
        if (ctx.selectedPid == null) {
            return Collections.emptyList();
        }
        try {
            return List.of(Long.parseLong(ctx.selectedPid));
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
    }

    private void resetIntegrationTabState() {
        diagramTab.onIntegrationChanged();
        routesTab.onIntegrationChanged();
        httpTab.onIntegrationChanged();
        logTab.onIntegrationChanged();
        historyTab.onIntegrationChanged();
        beansTab.onIntegrationChanged();
        browseTab.onIntegrationChanged();
        threadsTab.onIntegrationChanged();
        startupTab.onIntegrationChanged();
        configurationTab.onIntegrationChanged();
        consumersTab.onIntegrationChanged();
        circuitBreakerTab.onIntegrationChanged();
        inflightTab.onIntegrationChanged();
    }

    private void navigateUp() {
        activeTab().navigateUp();
    }

    private void navigateDown() {
        activeTab().navigateDown();
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
        if (drawOverlay.isVisible()) {
            drawOverlay.render(frame, mainChunks.get(4));
        }
        if (showKillConfirm) {
            renderKillConfirm(frame, mainChunks.get(4));
        }
        actionsPopup.render(frame, mainChunks.get(4));
        if (captionOverlay.isCaptionVisible()) {
            captionOverlay.render(frame, mainChunks.get(4));
        }
        if (helpOverlay.isVisible()) {
            helpOverlay.render(frame, mainChunks.get(4));
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
        long historyCount = hasSelection
                ? historyTab.historyEntries.stream().map(e -> e.exchangeId).distinct().count()
                : 0;
        boolean hasTraces = hasSelection && !traces.get().isEmpty();
        int httpCount = hasSelection ? sel.httpEndpoints.size() : 0;

        // Row 0: label-only titles — fixed width so the tab bar never shifts when badges appear
        Line[] labels = {
                Line.from(" 1 Overview "),
                Line.from(" 2 Log "),
                Line.from(" 3 Diagram "),
                Line.from(routesTab.isTopMode() ? " 4  Top  " : " 4 Route "),
                Line.from(" 5 Endpoint "),
                Line.from(" 6 HTTP "),
                Line.from(" 7 Health "),
                Line.from(" 8 Inspect "),
                Line.from(" 9 Errors "),
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
                badgeTexts[TAB_DIAGRAM] = "(1)";
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
        tab.render(frame, area);
        // Render "More" popup overlay when visible
        if (showMorePopup) {
            renderMorePopup(frame, area);
        }
        // Render "Switch integration" popup overlay when visible
        if (showSwitchPopup) {
            renderSwitchPopup(frame, area);
        }
    }

    private void renderMorePopup(Frame frame, Rect area) {
        int popupW = 22;
        int popupH = 12;
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

        Style keyStyle = Style.EMPTY.fg(Color.YELLOW).bold();
        ListItem[] items = {
                ListItem.from(Line.from(Span.raw("  "), Span.styled("B", keyStyle), Span.raw("eans"))),
                ListItem.from(Line.from(Span.raw("  Bro"), Span.styled("w", keyStyle), Span.raw("se"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("C", keyStyle), Span.raw("ircuit Breaker"))),
                ListItem.from(Line.from(Span.raw("  Cl"), Span.styled("a", keyStyle), Span.raw("sspath"))),
                ListItem.from(Line.from(Span.raw("  Confi"), Span.styled("g", keyStyle), Span.raw("uration"))),
                ListItem.from(Line.from(Span.raw("  Co"), Span.styled("n", keyStyle), Span.raw("sumers"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("I", keyStyle), Span.raw("nflight"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("M", keyStyle), Span.raw("emory"))),
                ListItem.from(Line.from(Span.raw("  M"), Span.styled("e", keyStyle), Span.raw("trics"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("S", keyStyle), Span.raw("tartup"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("T", keyStyle), Span.raw("hreads"))),
        };
        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(Title.from(Line.from(Span.styled(" More Tabs ", Style.EMPTY.fg(Color.YELLOW).bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, morePopupState);
    }

    private void renderSwitchPopup(Frame frame, Rect area) {
        List<IntegrationInfo> integrations = getNonVanishingIntegrations();
        if (integrations.isEmpty()) {
            showSwitchPopup = false;
            return;
        }

        int maxLabelLen = integrations.stream()
                .mapToInt(i -> {
                    String n = i.name != null ? i.name : "?";
                    return n.length() + i.pid.length() + 14;
                })
                .max().orElse(30);
        int popupW = Math.min(area.width() - 4, Math.max(40, maxLabelLen + 4));
        int popupH = Math.min(area.height() - 4, integrations.size() + 2);

        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        ListItem[] items = new ListItem[integrations.size()];
        for (int i = 0; i < integrations.size(); i++) {
            IntegrationInfo info = integrations.get(i);
            String name = info.name != null ? info.name : "?";
            boolean current = info.pid.equals(ctx.selectedPid);
            String label = String.format("  🐪 %s (pid:%s)%s", name, info.pid, current ? " ●" : "");
            if (current) {
                items[i] = ListItem.from(Line.from(Span.styled(label, Style.EMPTY.fg(Color.CYAN))));
            } else {
                items[i] = ListItem.from(label);
            }
        }

        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(Title.from(Line.from(Span.styled(" Switch Integration ", Style.EMPTY.fg(Color.YELLOW).bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, switchPopupState);
    }

    private List<IntegrationInfo> getNonVanishingIntegrations() {
        return data.get().stream()
                .filter(i -> !i.vanishing && i.name != null)
                .sorted(Comparator.comparing(i -> i.name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private static int morePopupShortcut(KeyEvent ke) {
        if (ke.isChar('b')) {
            return 0;
        }
        if (ke.isChar('w')) {
            return 1;
        }
        if (ke.isChar('c')) {
            return 2;
        }
        if (ke.isChar('a')) {
            return 3;
        }
        if (ke.isChar('g')) {
            return 4;
        }
        if (ke.isChar('n')) {
            return 5;
        }
        if (ke.isChar('i')) {
            return 6;
        }
        if (ke.isChar('m')) {
            return 7;
        }
        if (ke.isChar('e')) {
            return 8;
        }
        if (ke.isChar('s')) {
            return 9;
        }
        if (ke.isChar('t')) {
            return 10;
        }
        return -1;
    }

    private MonitorTab activeTab() {
        return switch (tabsState.selected()) {
            case TAB_OVERVIEW -> overviewTab;
            case TAB_LOG -> logTab;
            case TAB_DIAGRAM -> diagramTab;
            case TAB_ROUTES -> routesTab;
            case TAB_ENDPOINTS -> endpointsTab;
            case TAB_HEALTH -> healthTab;
            case TAB_HISTORY -> historyTab;
            case TAB_HTTP -> httpTab;
            case TAB_ERRORS -> errorsTab;
            case TAB_MORE -> activeMoreTab;
            default -> null;
        };
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
        // Clear local sparkline history — heap memory
        heapMemHistory.remove(pid);
        previousHeapTime.remove(pid);
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
        if (msg != null && System.currentTimeMillis() - screenshotMessageTime < 5000) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled(" " + msg, Style.EMPTY.fg(Color.GREEN)))),
                    area);
            return;
        }
        screenshotMessage = null;

        List<Span> spans = new ArrayList<>();

        if (helpOverlay.isVisible()) {
            helpOverlay.renderFooter(spans);
            frame.renderWidget(Paragraph.from(Line.from(spans)), area);
            return;
        }

        if (captionOverlay.isCaptionVisible()) {
            captionOverlay.renderFooter(spans);
            frame.renderWidget(Paragraph.from(Line.from(spans)), area);
            return;
        }

        if (showSwitchPopup) {
            hint(spans, "Up/Down", "select");
            hint(spans, "Enter", "switch");
            hint(spans, "Esc", "close");
        } else if (showMorePopup) {
            hint(spans, "Up/Down", "select");
            hint(spans, "Enter", "open");
            hint(spans, "Esc", "close");
        } else {
            MonitorTab tab = activeTab();

            if (tabsState.selected() == TAB_OVERVIEW) {
                renderOverviewFooter(spans);
            } else {
                tab.renderFooter(spans);
                int insertPos = Math.min(2, spans.size());
                List<Span> fKeySpans = new ArrayList<>();
                if (activeTab() != null && activeTab().getHelpText() != null) {
                    hint(fKeySpans, "F1", "help");
                }
                hint(fKeySpans, "F2", "actions");
                if (getNonVanishingIntegrations().size() > 1) {
                    hint(fKeySpans, "F3", "switch");
                }
                spans.addAll(insertPos, fKeySpans);
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
        overviewTab.renderFooter(spans);
        // Insert F2/F3 after first hint (q) — each hint is 2 spans (key + label)
        int insertPos = Math.min(2, spans.size());
        List<Span> fKeySpans = new ArrayList<>();
        hint(fKeySpans, "F1", "help");
        hint(fKeySpans, "F2", "actions");
        if (getNonVanishingIntegrations().size() > 1) {
            hint(fKeySpans, "F3", "switch");
        }
        spans.addAll(insertPos, fKeySpans);
        // Process action hints
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
    }

    // ---- Data Loading ----

    private void refreshLogData() {
        if (tabsState.selected() != TAB_LOG) {
            return;
        }
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
        if (logPid == null) {
            return;
        }
        if (!logPid.equals(logTab.logFilePid)) {
            logTab.mutableFilteredEntries.clear();
            logTab.logFilePos = -1;
            logTab.logTotalLinesRead = 0;
            logTab.logLineBuffer.setLength(0);
            logTab.logLoading = true;
        }
        // Load older lines when scrolled to the top or Home pressed
        boolean changed = false;
        boolean loadAll = logTab.loadAllRequested;
        if (logTab.logFileStartPos > 0
                && (loadAll || (!logTab.followMode && logTab.scroll == 0))) {
            logTab.loadAllRequested = false;
            List<String> olderLines = new ArrayList<>();
            logTab.readOlderLogLines(logFileName, loadAll, olderLines);
            if (!olderLines.isEmpty()) {
                changed = true;
                List<LogEntry> olderEntries = new ArrayList<>();
                for (String line : olderLines) {
                    olderEntries.add(LogTab.parseLogLine(line));
                }
                logTab.mutableFilteredEntries.addAll(0, olderEntries);
                logTab.logTotalLinesRead += olderLines.size();
                logTab.scroll = olderEntries.size();
            }
        }
        List<String> newRawLines = new ArrayList<>();
        logTab.readNewLogLinesFromFile(logPid, logFileName, newRawLines);
        changed |= !newRawLines.isEmpty();
        if (changed) {
            logTab.logTotalLinesRead += newRawLines.size();
            for (String line : newRawLines) {
                logTab.mutableFilteredEntries.add(LogTab.parseLogLine(line));
            }
            if (logTab.mutableFilteredEntries.size() > MAX_LOG_LINES) {
                logTab.mutableFilteredEntries.subList(0, logTab.mutableFilteredEntries.size() - MAX_LOG_LINES)
                        .clear();
            }
        }
        if (changed || logTab.logLoading) {
            logTab.filteredLogEntries = new ArrayList<>(logTab.mutableFilteredEntries);
        }
        logTab.logLoading = false;
    }

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
            // Read log data early — before the heavy PID/status scan
            refreshLogData();

            List<IntegrationInfo> infos = new ArrayList<>();
            long now = System.currentTimeMillis();
            boolean wantFullScan = tabsState.selected() == TAB_OVERVIEW || showSwitchPopup || cachedPids.isEmpty();
            boolean fullScan = wantFullScan && (now - lastFullScanTime >= 2000);
            List<Long> pids;
            if (fullScan) {
                pids = findPids(name);
                cachedPids = pids;
                lastFullScanTime = now;
            } else {
                pids = cachedPids;
            }

            // On non-Overview tabs, only refresh the selected integration for speed
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
                        updateThroughputHistory(info);
                        updateEndpointHistory(info);
                        updateCbHistory(info);
                        updateHeapHistory(info);
                        updateLoadMetrics(ph, info);
                    }
                }
            }
            // Carry forward non-selected integrations from previous data so they don't vanish
            if (!fullScan && ctx.selectedPid != null) {
                List<IntegrationInfo> previous = data.get();
                for (IntegrationInfo prev : previous) {
                    if (!prev.vanishing && !ctx.selectedPid.equals(prev.pid)) {
                        infos.add(prev);
                    }
                }
            }

            // Detect disappeared integrations and start vanishing
            Set<String> livePids = infos.stream().map(i -> i.pid).collect(Collectors.toSet());
            List<IntegrationInfo> previous = data.get();
            for (IntegrationInfo prev : previous) {
                if (!prev.vanishing && !livePids.contains(prev.pid) && !vanishing.containsKey(prev.pid)) {
                    vanishing.put(prev.pid, new VanishingInfo(prev, System.currentTimeMillis()));
                }
            }

            // Expire old vanishing entries
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
                    heapMemHistory.remove(entry.getKey());
                    previousHeapTime.remove(entry.getKey());
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

            // Discover running infra services (only on Overview or switch popup)
            if (fullScan) {
                refreshInfraData();
            }

            // Auto-select first infra service when no active integrations exist
            if (ctx.selectedPid == null && !infraData.get().isEmpty()
                    && infos.stream().noneMatch(i -> !i.vanishing)) {
                List<InfraInfo> infras = infraData.get();
                if (!infras.isEmpty()) {
                    int firstInfraIndex = infos.size() + (infras.size() > 0 ? 1 : 0);
                    overviewTab.tableState.select(firstInfraIndex);
                    ctx.selectedPid = infras.get(0).pid;
                }
            }

            // Log data is now refreshed at the top of refreshDataSync() via refreshLogData()

            // Scope history/error/trace refresh to the selected integration only
            List<Long> selectedPids = selectedPidAsList();

            // Refresh error data only when the Errors tab is visible
            if (tabsState.selected() == TAB_ERRORS && !selectedPids.isEmpty()) {
                refreshErrorData(selectedPids);
            }

            // Refresh trace data only when the History tab is visible
            if (tabsState.selected() == TAB_HISTORY && !selectedPids.isEmpty()) {
                if (historyTab.historyRefreshRequested) {
                    historyTab.historyRefreshRequested = false;
                    refreshHistoryData(selectedPids);
                }
                refreshTraceData(selectedPids);
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

    private void updateHeapHistory(IntegrationInfo info) {
        if (info.heapMemUsed > 0) {
            long now = System.currentTimeMillis();
            Long lastTime = previousHeapTime.get(info.pid);
            if (lastTime == null || now - lastTime >= HEAP_SAMPLE_INTERVAL_MS) {
                previousHeapTime.put(info.pid, now);
                LinkedList<Long> hist = heapMemHistory.computeIfAbsent(info.pid, k -> new LinkedList<>());
                hist.add(info.heapMemUsed);
                while (hist.size() > MAX_HEAP_HISTORY_POINTS) {
                    hist.remove(0);
                }
            }
        }
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

    private JsonObject loadErrorFile(long pid) {
        return TuiHelper.loadStatus(pid, this::getErrorFile);
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
        historyTab.historyEntries = allEntries;
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
                        StatusParser.parseKvArray(hdrs, ei.headers, ei.headerTypes);
                    }
                }
                // exchange properties and variables
                JsonArray props = ej.getCollection("exchangeProperties");
                if (props != null) {
                    StatusParser.parseKvArray(props, ei.properties, ei.propertyTypes);
                }
                JsonArray vars = ej.getCollection("exchangeVariables");
                if (vars != null) {
                    StatusParser.parseKvArray(vars, ei.variables, ei.variableTypes);
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
            "Overview", "Log", "Diagram", "Routes", "Endpoints",
            "HTTP", "Health", "Inspect", "Errors", "More"
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

    boolean isDrawVisible() {
        return drawOverlay.isVisible();
    }

    void setDrawing(List<DrawOverlay.DrawCell> cells, int durationSeconds) {
        drawOverlay.setDrawing(cells, durationSeconds);
    }

    void appendDrawing(List<DrawOverlay.DrawCell> cells) {
        drawOverlay.appendDrawing(cells);
    }

    void clearDrawing() {
        drawOverlay.clear();
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

    JsonObject getTableData(String tabName) {
        if (tabName != null && !tabName.isBlank()) {
            String prev = getActiveTabName();
            String switched = navigateToTab(tabName);
            if (switched == null) {
                return null;
            }
        }
        MonitorTab tab = activeTab();
        return tab != null ? tab.getTableDataAsJson() : null;
    }

    boolean executeAction(String actionName) {
        return actionsPopup.executeActionByName(actionName);
    }

    JsonObject getLogData(int limit, String filter, String level) {
        return logTab.getLogDataAsJson(limit, filter, level);
    }

    JsonObject getDiagramData() {
        MonitorTab tab = activeTab();
        if (tab instanceof DiagramTab dt) {
            return dt.getTableDataAsJson();
        }
        return diagramTab.getTableDataAsJson();
    }

    JsonObject sendMessage(String endpoint, String body, String headers) {
        if (ctx.selectedPid == null) {
            return null;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            return null;
        }
        return RuntimeHelper.sendMessage(pid, endpoint, body, headers);
    }

    private record PendingKey(KeyEvent event, long fireAt) {
    }

}
