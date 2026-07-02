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
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.tabs.Tabs;
import dev.tamboui.widgets.tabs.TabsState;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import sun.misc.Signal;

import static org.apache.camel.dsl.jbang.core.commands.tui.TabRegistry.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;

@Command(name = "monitor",
         description = "Live dashboard for monitoring Camel integrations",
         sortOptions = false)
public class CamelMonitor extends CamelCommand {

    private static final Logger LOG = System.getLogger(CamelMonitor.class.getName());
    private static final long DEFAULT_REFRESH_MS = 100;

    // Compact tab bar (10 labels + 9 "|" dividers) needs 88 chars — that is the true minimum
    private static final int MIN_WIDTH = 88;
    private static final int MIN_HEIGHT = 24;
    // Full tab bar (10 labels + 9 " | " dividers) needs 126 chars; use compact below that
    private static final int TABS_FULL_MIN_WIDTH = 126;

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
    private final TabsState tabsState = new TabsState(TAB_OVERVIEW);
    private TabRegistry tabRegistry;

    // selectedPid is stored on ctx (MonitorContext) so tabs can access it

    private DataRefreshService dataService;
    private String monitorNotification;
    private boolean monitorNotificationError;
    private long monitorNotificationExpiry;
    private boolean mcpInjectedKey;
    private TuiMcpServer mcpServer;
    private McpFacade mcpFacade;
    private final Queue<McpFacade.PendingKey> pendingKeys = new ConcurrentLinkedQueue<>();
    private final CaptionOverlay captionOverlay = new CaptionOverlay();
    private final RecordingManager recordingManager = new RecordingManager(captionOverlay);
    private final DrawOverlay drawOverlay = new DrawOverlay();
    private final HelpOverlay helpOverlay = new HelpOverlay();
    private final ShellPanel shellPanel = new ShellPanel();
    private final AiPanel aiPanel = new AiPanel();

    private ActionsPopup actionsPopup;
    private TuiRunner runner;

    private MonitorContext ctx;

    private final FilesBrowser filesBrowser = new FilesBrowser();
    private PopupManager popupManager;

    // Mouse support: last rendered areas for hit-testing
    private Rect lastTabsArea;
    private Rect lastContentArea;
    private Line[] lastTabLabels;
    private String lastTabDivider;
    // Panel resize drag state
    private final DragSplit panelSplit = new DragSplit();
    // Footer key-binding hit-testing: each clickable hint records its [startX, endX) column range on
    // the footer row and the KeyEvent to synthesize when clicked.
    private int footerRowY = -1;
    private int[] footerRegionStartX = new int[0];
    private int[] footerRegionEndX = new int[0];
    private KeyEvent[] footerRegionKey = new KeyEvent[0];

    private final ClassLoader classLoader;

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

        recordingManager.init(record != null);

        // to make ServiceLoader work with tamboui for downloaded JARs
        Thread.currentThread().setContextClassLoader(classLoader);

        // Create data refresh service first — tabs and popups reference its state
        dataService = new DataRefreshService(
                name,
                new DataRefreshService.RefreshContext() {
                    @Override
                    public int selectedTab() {
                        return tabRegistry != null ? tabRegistry.selectedTabIndex() : tabsState.selected();
                    }

                    @Override
                    public boolean isSwitchPopupVisible() {
                        return popupManager != null && popupManager.isSwitchPopupVisible();
                    }

                    @Override
                    public String getPendingAutoSelect() {
                        return actionsPopup != null ? actionsPopup.getPendingAutoSelect() : null;
                    }

                    @Override
                    public void clearPendingAutoSelect() {
                        if (actionsPopup != null) {
                            actionsPopup.clearPendingAutoSelect();
                        }
                    }

                    @Override
                    public void onInfraAutoSelected(int tableIndex, String pid) {
                        if (tabRegistry != null) {
                            tabRegistry.overviewTab().tableState.select(tableIndex);
                        }
                        ctx.selectedPid = pid;
                    }

                    @Override
                    public boolean isInfraSelected() {
                        return CamelMonitor.this.isInfraSelected();
                    }
                },
                this::getStatusFile,
                this::getErrorFile);

        // Create shared context and tab instances
        ctx = new MonitorContext(dataService.data(), dataService.infraData());
        dataService.setContext(ctx);

        actionsPopup = new ActionsPopup(
                () -> dataService.data().get().stream()
                        .filter(i -> !i.vanishing && i.name != null)
                        .map(i -> i.name)
                        .collect(Collectors.toSet()),
                () -> dataService.data().get().stream()
                        .filter(i -> !i.vanishing)
                        .collect(Collectors.toList()),
                () -> dataService.infraData().get().stream()
                        .filter(i -> !i.vanishing)
                        .collect(Collectors.toList()),
                captionOverlay,
                () -> recordingManager.requestScreenshot(),
                () -> recordingManager.toggleRecording(),
                () -> recordingManager.isRecording(),
                () -> recordingManager.toggleTapeRecording(),
                () -> recordingManager.isTapeRecording(),
                dataService::enableBurstMode, dataService.stoppingPids());

        actionsPopup.setContext(ctx);
        actionsPopup.setResetStatsAction(this::resetStats);
        shellPanel.setContext(ctx);
        aiPanel.setContext(ctx);
        actionsPopup.setOpenShellAction(shellPanel::open);
        actionsPopup.setBrowseFilesAction(this::openFilesPopup);

        tabRegistry = new TabRegistry(tabsState);
        tabRegistry.initTabs(ctx, dataService, this::resetIntegrationTabState);
        tabRegistry.setCallbacks(new TabRegistry.TabCallbacks() {
            @Override
            public void refreshLogData() {
                CamelMonitor.this.refreshLogData();
            }

            @Override
            public void refreshHistoryData(List<Long> pids) {
                dataService.loadHistoryData(pids);
            }

            @Override
            public void refreshTraceData(List<Long> pids) {
                dataService.refreshTraceData(pids);
            }

            @Override
            public void refreshErrorData(List<Long> pids) {
                dataService.refreshErrorData(pids);
            }

            @Override
            public void openMorePopup() {
                popupManager.openMorePopup();
            }

            @Override
            public void closeMorePopup() {
                popupManager.closeMorePopup();
            }

            @Override
            public void selectMorePopupEntry(int index) {
                popupManager.selectMorePopupEntry(index);
            }
        });

        actionsPopup.setGotoTabSupport(tabRegistry.allTabEntries(), () -> {
            TabRegistry.TabEntry entry = actionsPopup.consumePendingGotoEntry();
            if (entry != null) {
                if (entry.moreIndex() >= 0) {
                    tabRegistry.selectMoreTab(entry.moreIndex());
                } else {
                    tabRegistry.handleTabKey(entry.tabIndex(), ctx, dataService);
                }
            }
        });

        popupManager = new PopupManager(
                ctx, this::getNonVanishingIntegrations, filesBrowser,
                new PopupManager.PopupCallbacks() {
                    @Override
                    public void selectMoreTab(int index) {
                        tabRegistry.selectMoreTab(index);
                    }

                    @Override
                    public void resetIntegrationTabState() {
                        CamelMonitor.this.resetIntegrationTabState();
                    }

                    @Override
                    public void refreshLogData() {
                        CamelMonitor.this.refreshLogData();
                    }

                    @Override
                    public void stopSelectedProcess(boolean forceKill) {
                        CamelMonitor.this.stopSelectedProcess(forceKill);
                    }
                });

        tabRegistry.overviewTab().setActions(new OverviewTab.OverviewActions() {
            @Override
            public void sendRouteCommand(String pid, String routeId, String command) {
                CamelMonitor.this.sendRouteCommand(pid, routeId, command);
            }

            @Override
            public void stopSelectedProcess(boolean forceKill) {
                CamelMonitor.this.stopSelectedProcess(forceKill);
            }

            @Override
            public void restartSelectedProcess() {
                CamelMonitor.this.restartSelectedProcess();
            }

            @Override
            public void showKillConfirm() {
                popupManager.showKillConfirm();
            }

            @Override
            public void openDoc(IntegrationInfo info) {
                actionsPopup.openDoc(info);
            }

            @Override
            public void openFilesPopup() {
                CamelMonitor.this.openFilesPopup();
            }
        });

        // Initial data load (synchronous before TUI starts)
        dataService.refreshSync(this::refreshLogData, this::refreshConditionalData);

        // Auto-select if there's exactly one integration running
        tabRegistry.overviewTab().selectCurrentIntegration();

        mcpFacade = new McpFacade(
                ctx, dataService.data(), tabsState, recordingManager,
                captionOverlay, drawOverlay, helpOverlay,
                actionsPopup, filesBrowser,
                tabRegistry,
                pendingKeys,
                new McpFacade.MonitorBridge() {
                    @Override
                    public MonitorTab activeTab() {
                        return tabRegistry.activeTab();
                    }

                    @Override
                    public void handleTabKey(int tabIndex) {
                        tabRegistry.handleTabKey(tabIndex, ctx, dataService);
                    }

                    @Override
                    public void selectMoreTab(int moreIndex) {
                        tabRegistry.selectMoreTab(moreIndex);
                    }

                    @Override
                    public boolean isSwitchPopupVisible() {
                        return popupManager.isSwitchPopupVisible();
                    }

                    @Override
                    public boolean isMorePopupVisible() {
                        return popupManager.isMorePopupVisible();
                    }

                    @Override
                    public void renderOverviewFooter(List<Span> spans) {
                        CamelMonitor.this.renderOverviewFooter(spans);
                    }

                    @Override
                    public void insertFKeyHints(List<Span> spans) {
                        CamelMonitor.this.insertFKeyHints(spans);
                    }

                    @Override
                    public void sendRouteCommand(String pid, String routeId, String command) {
                        CamelMonitor.this.sendRouteCommand(pid, routeId, command);
                    }

                    @Override
                    public void restartProcess() {
                        restartSelectedProcess();
                    }

                    @Override
                    public void stopProcess(boolean forceKill) {
                        stopSelectedProcess(forceKill);
                    }
                });
        Path mcpJsonFile = null;
        actionsPopup.setAiActivityLog(aiPanel::getActivityLog);
        if (mcp) {
            mcpServer = new TuiMcpServer(mcpPort, mcpFacade);
            try {
                mcpServer.start();
                actionsPopup.setMcpEnabled(true, mcpPort, mcpServer::getConnectedClient,
                        mcpServer::getActivityLog, mcpServer::getToolCallCount);
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
            // Preload diagram data if an integration was auto-selected
            tabRegistry.routesTab().preloadDiagram();
            tabRegistry.diagramTab().preloadDiagram();
            // Intercept Ctrl+C: quit the TUI cleanly instead of letting
            // the JVM tear down the classloader while we're still running
            Signal.handle(new Signal("INT"), sig -> tui.quit());
            tui.run(
                    this::handleEvent,
                    this::render);
        } finally {
            shellPanel.destroy();
            aiPanel.destroy();
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
            recordingManager.recordKey(ke, mcpInjectedKey);
            if (ke.hasCtrl() && ke.isChar('r')) {
                recordingManager.toggleTapeRecording();
                return true;
            }
            if (captionOverlay.isVisible()) {
                if (captionOverlay.handleKeyEvent(ke)) {
                    return true;
                }
            }
            if (ke.hasCtrl() && ke.isChar('k')) {
                recordingManager.toggleRecording();
                return true;
            }
            if (ke.hasCtrl() && ke.isChar('t')) {
                captionOverlay.openInline();
                return true;
            }
            if (helpOverlay.isVisible()) {
                return helpOverlay.handleKeyEvent(ke);
            }
            if (shellPanel.isOpen()) {
                // Shift+F6 cycles shell height — handle before delegating to shell
                if (ke.isKey(KeyCode.F6) && ke.hasShift()) {
                    if (lastContentArea != null) {
                        shellPanel.cycleHeight(lastContentArea.height());
                    }
                    return true;
                }
                return shellPanel.handleKeyEvent(ke);
            }
            if (aiPanel.isOpen()) {
                if (ke.isKey(KeyCode.F8) && ke.hasShift()) {
                    if (lastContentArea != null) {
                        aiPanel.cycleHeight(lastContentArea.height());
                    }
                    return true;
                }
                return aiPanel.handleKeyEvent(ke);
            }
            if (actionsPopup.isVisible()) {
                return actionsPopup.handleKeyEvent(ke);
            }
            if (popupManager.handleKeyEvent(ke, tabRegistry.selectedTabIndex(), TAB_LOG)) {
                return true;
            }
            if (handleGlobalKeys(ke, runner)) {
                return true;
            }
            if (handleTabKeys(ke)) {
                return true;
            }
        }
        if (event instanceof MouseEvent me) {
            if (shellPanel.isOpen() && shellPanel.handleMouseEvent(me)) {
                return true;
            }
            if (aiPanel.isOpen() && aiPanel.handleMouseEvent(me)) {
                return true;
            }
            if (handleMouseEvent(me, runner)) {
                return true;
            }
        }
        if (event instanceof PasteEvent pe) {
            return handlePasteEvent(pe);
        }
        if (event instanceof TickEvent) {
            return handleTickEvent(runner);
        }
        return false;
    }

    private boolean handleGlobalKeys(KeyEvent ke, TuiRunner runner) {
        if (ke.isCancel()) {
            MonitorTab tab = tabRegistry.activeTab();
            if (tab != null && tab.handleEscape()) {
                return true;
            }
            if (tabRegistry.selectedTabIndex() != TAB_OVERVIEW) {
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
        boolean probeEditing = tabRegistry.selectedTabIndex() == TAB_HTTP
                && tabRegistry.httpTab().isProbeMode();
        boolean logSearchActive = tabRegistry.selectedTabIndex() == TAB_LOG
                && tabRegistry.logTab().isSearchInputActive();
        boolean spanFilterActive = tabRegistry.selectedTabIndex() == TAB_MORE
                && tabRegistry.getActiveMoreTab() == tabRegistry.spansTab()
                && tabRegistry.spansTab().isFilterInputActive();
        boolean sqlInputActive = tabRegistry.selectedTabIndex() == TAB_MORE
                && tabRegistry.getActiveMoreTab() == tabRegistry.sqlQueryTab()
                && tabRegistry.sqlQueryTab().isInputActive();
        boolean textEditing = probeEditing || logSearchActive || spanFilterActive || sqlInputActive;
        if (!textEditing && (ke.isCharIgnoreCase('q') || ke.isCtrlC())) {
            runner.quit();
            return true;
        }
        if (ke.isCtrlC()) {
            runner.quit();
            return true;
        }
        if (!textEditing) {
            if (ke.isChar('1')) {
                return tabRegistry.handleTabKey(TAB_OVERVIEW, ctx, dataService);
            }
            if (ke.isChar('2')) {
                return tabRegistry.handleTabKey(TAB_LOG, ctx, dataService);
            }
            if (!isInfraSelected()) {
                if (ke.isChar('3')) {
                    return tabRegistry.handleTabKey(TAB_DIAGRAM, ctx, dataService);
                }
                if (ke.isChar('4')) {
                    return tabRegistry.handleTabKey(TAB_ROUTES, ctx, dataService);
                }
                if (ke.isChar('5')) {
                    return tabRegistry.handleTabKey(TAB_ENDPOINTS, ctx, dataService);
                }
                if (ke.isChar('6')) {
                    return tabRegistry.handleTabKey(TAB_HTTP, ctx, dataService);
                }
                if (ke.isChar('7')) {
                    return tabRegistry.handleTabKey(TAB_HEALTH, ctx, dataService);
                }
                if (ke.isChar('8')) {
                    return tabRegistry.handleTabKey(TAB_HISTORY, ctx, dataService);
                }
                if (ke.isChar('9')) {
                    return tabRegistry.handleTabKey(TAB_ERRORS, ctx, dataService);
                }
                if (ke.isChar('0')) {
                    return tabRegistry.handleTabKey(TAB_MORE, ctx, dataService);
                }
            }
        }
        if (ke.isFocusPrevious() && !textEditing) {
            if (isInfraSelected()) {
                int prev = tabRegistry.selectedTabIndex() == TAB_OVERVIEW ? TAB_LOG : TAB_OVERVIEW;
                tabsState.select(prev);
            } else {
                // Skip TAB_MORE when cycling – it is a popup trigger, not a renderable tab
                int prev = (tabRegistry.selectedTabIndex() - 1 + NUM_TABS) % NUM_TABS;
                if (prev == TAB_MORE) {
                    prev = (prev - 1 + NUM_TABS) % NUM_TABS;
                }
                if (prev != TAB_OVERVIEW) {
                    tabRegistry.overviewTab().selectCurrentIntegration();
                }
                tabsState.select(prev);
            }
            return true;
        }
        if (ke.isFocusNext() && !textEditing) {
            if (isInfraSelected()) {
                int next = tabRegistry.selectedTabIndex() == TAB_OVERVIEW ? TAB_LOG : TAB_OVERVIEW;
                tabsState.select(next);
            } else {
                // Skip TAB_MORE when cycling – it is a popup trigger, not a renderable tab
                int next = (tabRegistry.selectedTabIndex() + 1) % NUM_TABS;
                if (next == TAB_MORE) {
                    next = (next + 1) % NUM_TABS;
                }
                if (next != TAB_OVERVIEW) {
                    tabRegistry.overviewTab().selectCurrentIntegration();
                }
                tabsState.select(next);
            }
            return true;
        }
        if (ke.isKey(KeyCode.F5) && ke.hasShift()) {
            recordingManager.takeScreenshot();
            return true;
        }
        if (opensHelp(ke, textEditing)) {
            // Only opens the overlay: while it is visible, dispatch delegates to
            // helpOverlay.handleKeyEvent (which handles F1/?/q/Esc to close) before reaching here.
            MonitorTab tab = tabRegistry.activeTab();
            if (tab != null) {
                String help = tab.getHelpText();
                if (help != null) {
                    helpOverlay.open(help);
                }
            }
            return true;
        }
        if (ke.isKey(KeyCode.F6)) {
            if (shellPanel.isOpen()) {
                shellPanel.close();
            } else {
                if (aiPanel.isOpen()) {
                    aiPanel.close();
                }
                shellPanel.open();
            }
            return true;
        }
        if (ke.isKey(KeyCode.F8)) {
            if (aiPanel.isOpen()) {
                aiPanel.close();
            } else {
                if (shellPanel.isOpen()) {
                    shellPanel.close();
                }
                aiPanel.open();
            }
            return true;
        }
        if (ke.isKey(KeyCode.F2)) {
            if (tabRegistry.selectedTabIndex() == TAB_ROUTES && tabRegistry.routesTab() != null) {
                actionsPopup.setPreSelectedRouteId(tabRegistry.routesTab().selectedRouteId());
            }
            actionsPopup.open();
            return true;
        }
        if (ke.isKey(KeyCode.F3)) {
            popupManager.openSwitchPopup(ctx.selectedPid, getNonVanishingIntegrations());
            return true;
        }
        return false;
    }

    /**
     * Whether the key event should open the help overlay. F1 always opens it; '?' opens it too, but only when no text
     * input is focused, so the character is not swallowed while the user is typing in a search or probe field.
     */
    static boolean opensHelp(KeyEvent ke, boolean textEditing) {
        return ke.isKey(KeyCode.F1) || (!textEditing && ke.isChar('?'));
    }

    private boolean handleTabKeys(KeyEvent ke) {
        MonitorTab activeTab = tabRegistry.activeTab();

        if (ke.isUp()) {
            if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                return true;
            }
            tabRegistry.navigateUp();
            return true;
        }
        if (ke.isDown()) {
            if (activeTab != null && activeTab.handleKeyEvent(ke)) {
                return true;
            }
            tabRegistry.navigateDown();
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

        int tab = tabRegistry.selectedTabIndex();
        if (ke.isConfirm() && tab == TAB_OVERVIEW) {
            tabRegistry.overviewTab().selectCurrentIntegration();
            if (ctx.selectedPid != null) {
                tabsState.select(TAB_LOG);
                refreshLogData();
            }
            return true;
        }
        if (activeTab != null && activeTab.handleKeyEvent(ke)) {
            return true;
        }
        return false;
    }

    private boolean handleMouseEvent(MouseEvent me, TuiRunner runner) {
        // Panel border drag resize: detect press on the border row, then track drag
        if (lastContentArea != null && (shellPanel.isOpen() || aiPanel.isOpen())
                && panelSplit.handleMouse(me, me.y())) {
            if (panelSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                int contentHeight = lastContentArea.height();
                if (contentHeight > 0) {
                    int newHeight = lastContentArea.y() + contentHeight - me.y();
                    newHeight = Math.max(3, Math.min(contentHeight - 3, newHeight));
                    if (shellPanel.isOpen()) {
                        shellPanel.setPanelHeight(newHeight);
                    } else {
                        aiPanel.setPanelHeight(newHeight);
                    }
                }
            }
            return true;
        }

        // Tab bar clicks: detect which tab was clicked and switch to it
        if (me.isClick() && lastTabsArea != null && lastTabLabels != null) {
            int tabsY = lastTabsArea.height() >= 2 ? lastTabsArea.y() + 1 : lastTabsArea.y();
            if (me.y() == tabsY && TuiHelper.contains(lastTabsArea, me.x(), me.y())) {
                int clickedTab = findClickedTab(me.x() - lastTabsArea.x());
                if (clickedTab >= 0) {
                    if (isInfraSelected()) {
                        // Infra mode: map 0→Overview, 1→Log
                        int realTab = clickedTab == 1 ? TAB_LOG : TAB_OVERVIEW;
                        tabsState.select(realTab);
                    } else {
                        tabRegistry.handleTabKey(clickedTab, ctx, dataService);
                    }
                    return true;
                }
            }
        }

        // Footer key-binding clicks: a click on a hint fires the matching key
        if (me.isClick() && handleFooterClick(me, runner)) {
            return true;
        }

        // Mouse events in the content area: delegate to the active tab
        if (TuiHelper.contains(lastContentArea, me.x(), me.y())) {
            if (popupManager.isMorePopupVisible() || popupManager.isSwitchPopupVisible()) {
                return popupManager.handleMouseEvent(me, tabRegistry.selectedTabIndex(), TAB_LOG);
            }
            if (actionsPopup.isVisible()) {
                return actionsPopup.handleMouseEvent(me);
            }
            if (filesBrowser.isVisible()) {
                return false;
            }
            MonitorTab activeTab = tabRegistry.activeTab();
            if (activeTab != null && activeTab.handleMouseEvent(me, lastContentArea)) {
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                tabRegistry.navigateUp();
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                tabRegistry.navigateDown();
                return true;
            }
        }
        return false;
    }

    /**
     * Given a click x-offset within the tab bar, determine which tab index was clicked. Returns -1 if the click falls
     * on a divider or outside tab labels.
     */
    private int findClickedTab(int xOffset) {
        if (lastTabLabels == null || lastTabDivider == null) {
            return -1;
        }
        int dividerW = CharWidth.of(lastTabDivider);
        int x = 0;
        for (int i = 0; i < lastTabLabels.length; i++) {
            if (i > 0) {
                // Skip divider region
                if (xOffset >= x && xOffset < x + dividerW) {
                    return -1;
                }
                x += dividerW;
            }
            int tabW = lastTabLabels[i].width();
            if (xOffset >= x && xOffset < x + tabW) {
                return i;
            }
            x += tabW;
        }
        return -1;
    }

    /**
     * Records the clickable footer key-binding regions from the final list of footer spans. A hint is drawn by
     * {@link TuiHelper#hint} as a key span styled with {@link Theme#hintKey()} immediately followed by its label span,
     * so each key span styled that way (whose token maps to an unambiguous single key) contributes a clickable region
     * covering both the key and its label. {@code area} is the single footer row.
     */
    private void captureFooterRegions(Rect area, List<Span> spans) {
        List<int[]> bounds = new ArrayList<>();
        List<KeyEvent> keys = new ArrayList<>();
        Style hintKeyStyle = Theme.hintKey();
        int x = area.x();
        for (int i = 0; i < spans.size(); i++) {
            Span s = spans.get(i);
            int w = s.width();
            if (hintKeyStyle.equals(s.style())) {
                KeyEvent ke = footerKeyEvent(s.content());
                if (ke != null) {
                    // Extend the region over the following label span so clicking the label works too.
                    int end = x + w + (i + 1 < spans.size() ? spans.get(i + 1).width() : 0);
                    bounds.add(new int[] { x, end });
                    keys.add(ke);
                }
            }
            x += w;
        }
        footerRowY = area.y();
        footerRegionStartX = bounds.stream().mapToInt(b -> b[0]).toArray();
        footerRegionEndX = bounds.stream().mapToInt(b -> b[1]).toArray();
        footerRegionKey = keys.toArray(new KeyEvent[0]);
    }

    /**
     * Hit-tests a click against the footer key-binding regions captured during the last render and, when it lands on a
     * hint, feeds the synthesized key back through the normal key path so it acts exactly like pressing that key.
     */
    private boolean handleFooterClick(MouseEvent me, TuiRunner runner) {
        if (footerRowY < 0 || me.y() != footerRowY) {
            return false;
        }
        int idx = footerRegionAt(footerRegionStartX, footerRegionEndX, me.x());
        if (idx < 0) {
            return false;
        }
        return handleEvent(footerRegionKey[idx], runner);
    }

    /**
     * Returns the index of the footer hint region whose column range contains {@code mouseX}, or {@code -1} when the
     * click is outside every region. Each region spans the half-open range {@code [startX[i], endX[i])}.
     */
    static int footerRegionAt(int[] startX, int[] endX, int mouseX) {
        if (startX == null) {
            return -1;
        }
        for (int i = 0; i < startX.length; i++) {
            if (mouseX >= startX[i] && mouseX < endX[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Maps a footer hint key token (the trimmed content of a {@link Theme#hintKey()} span) to the {@link KeyEvent} that
     * pressing it would produce, or {@code null} when the token is not an unambiguous single key. Recognizes the
     * function keys {@code F1}-{@code F12}, {@code Enter}, {@code Esc}, {@code Tab} and any single character; ambiguous
     * multi-key hints such as {@code Up/Down}, {@code PgUp/PgDn} or arrow glyphs are left non-clickable.
     */
    static KeyEvent footerKeyEvent(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() >= 2 && (t.charAt(0) == 'F' || t.charAt(0) == 'f')
                && t.chars().skip(1).allMatch(Character::isDigit)) {
            KeyCode fkey = functionKeyCode(Integer.parseInt(t.substring(1)));
            return fkey != null ? KeyEvent.ofKey(fkey) : null;
        }
        switch (t) {
            case "Enter":
                return KeyEvent.ofKey(KeyCode.ENTER);
            case "Esc":
                return KeyEvent.ofKey(KeyCode.ESCAPE);
            case "Tab":
                return KeyEvent.ofKey(KeyCode.TAB);
            default:
                return t.length() == 1 ? KeyEvent.ofChar(t.charAt(0), KeyModifiers.NONE) : null;
        }
    }

    private static KeyCode functionKeyCode(int n) {
        return switch (n) {
            case 1 -> KeyCode.F1;
            case 2 -> KeyCode.F2;
            case 3 -> KeyCode.F3;
            case 4 -> KeyCode.F4;
            case 5 -> KeyCode.F5;
            case 6 -> KeyCode.F6;
            case 7 -> KeyCode.F7;
            case 8 -> KeyCode.F8;
            case 9 -> KeyCode.F9;
            case 10 -> KeyCode.F10;
            case 11 -> KeyCode.F11;
            case 12 -> KeyCode.F12;
            default -> null;
        };
    }

    private boolean handlePasteEvent(PasteEvent pe) {
        if (actionsPopup.isVisible()) {
            actionsPopup.handlePaste(pe.text());
            return true;
        }
        if (tabRegistry.httpTab().isProbeMode()) {
            tabRegistry.httpTab().handlePaste(pe.text());
            return true;
        }
        if (tabRegistry.logTab().isSearchInputActive()) {
            tabRegistry.logTab().handlePaste(pe.text());
            return true;
        }
        if (filesBrowser.isSourceViewerPasteActive()) {
            filesBrowser.handlePaste(pe.text());
            return true;
        }
        if (tabRegistry.getActiveMoreTab() == tabRegistry.sqlQueryTab()
                && tabRegistry.sqlQueryTab().isInputActive()) {
            tabRegistry.sqlQueryTab().handlePaste(pe.text());
            return true;
        }
        return false;
    }

    private boolean handleTickEvent(TuiRunner runner) {
        long now = System.currentTimeMillis();
        boolean keyProcessed = false;
        McpFacade.PendingKey pk;
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
        recordingManager.tickRecentKeys(now);
        boolean anyDiagramShowing = tabRegistry.routesTab().isShowDiagram()
                || tabRegistry.diagramTab().isShowDiagram();
        long interval = anyDiagramShowing ? Math.max(refreshInterval, 1000) : refreshInterval;
        if (now - dataService.lastRefresh() >= interval) {
            dataService.refresh(runner, this::refreshLogData, this::refreshConditionalData);
            tabRegistry.routesTab().refreshDiagramIfNeeded();
            tabRegistry.diagramTab().refreshDiagramIfNeeded();
            return true;
        }
        return true;
    }

    private void resetIntegrationTabState() {
        tabRegistry.resetIntegrationTabState(dataService, filesBrowser);
    }

    // ---- Rendering ----

    private void render(Frame frame) {
        Rect area = frame.area();

        if (area.width() < MIN_WIDTH || area.height() < MIN_HEIGHT) {
            renderTooSmall(frame, area);
            return;
        }

        // Layout: header (1 row) + tabs (2 rows) + content (fill) + footer (1 row)
        List<Rect> mainChunks = Layout.vertical()
                .constraints(
                        Constraint.length(1),
                        Constraint.length(2),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, mainChunks.get(0));
        renderTabs(frame, mainChunks.get(1));
        lastTabsArea = mainChunks.get(1);
        Rect contentArea = mainChunks.get(2);
        lastContentArea = contentArea;
        shellPanel.tickAnimation();
        aiPanel.tickAnimation();
        if (shellPanel.isOpen()) {
            shellPanel.initHeight(contentArea.height());
            int ph = shellPanel.panelHeight();
            ctx.shellPercent = ph * 100 / Math.max(1, contentArea.height());
            if (ph >= contentArea.height()) {
                shellPanel.render(frame, contentArea);
                panelSplit.setBorderPos(contentArea.y());
            } else {
                List<Rect> splitChunks = Layout.vertical()
                        .constraints(Constraint.fill(), Constraint.length(ph))
                        .split(contentArea);
                renderContent(frame, splitChunks.get(0));
                shellPanel.render(frame, splitChunks.get(1));
                panelSplit.setBorderPos(splitChunks.get(1).y());
            }
        } else if (aiPanel.isOpen()) {
            aiPanel.initHeight(contentArea.height());
            int ph = aiPanel.panelHeight();
            ctx.shellPercent = ph * 100 / Math.max(1, contentArea.height());
            List<Rect> splitChunks = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(ph))
                    .split(contentArea);
            renderContent(frame, splitChunks.get(0));
            aiPanel.render(frame, splitChunks.get(1));
            panelSplit.setBorderPos(splitChunks.get(1).y());
        } else {
            ctx.shellPercent = 0;
            renderContent(frame, contentArea);
            panelSplit.clearBorderPos();
        }
        // Overlays render on top of the full content area regardless of shell state
        if (drawOverlay.isVisible()) {
            drawOverlay.render(frame, contentArea);
        }
        if (popupManager.isKillConfirmVisible()) {
            popupManager.renderKillConfirm(frame, contentArea);
        }
        actionsPopup.render(frame, contentArea);
        if (captionOverlay.isCaptionVisible()) {
            captionOverlay.render(frame, contentArea);
        }
        if (helpOverlay.isVisible()) {
            helpOverlay.render(frame, contentArea);
        }
        renderFooter(frame, mainChunks.get(3));

        recordingManager.updateBuffer(frame.buffer());
        recordingManager.processPendingScreenshot();
    }

    private void renderHeader(Frame frame, Rect area) {
        List<IntegrationInfo> infos = dataService.data().get();
        String camelVersion = VersionHelper.extractCamelVersion();
        long activeCount = infos.stream().filter(i -> !i.vanishing).count();

        List<Span> titleSpans = new ArrayList<>();
        titleSpans.add(Span.styled(" Camel TUI", Theme.title()));
        titleSpans.add(Span.raw("  "));
        titleSpans.add(Span.styled(camelVersion != null ? "v" + camelVersion : "", Theme.success()));
        titleSpans.add(Span.raw("  "));
        titleSpans.add(Span.styled(activeCount + " integration(s)", Theme.info()));
        long activeInfra = dataService.infraData().get().stream().filter(i -> !i.vanishing).count();
        if (activeInfra > 0) {
            titleSpans.add(Span.raw("  "));
            titleSpans.add(Span.styled(activeInfra + " infra(s)", Theme.notice()));
        }
        if (ctx.selectedPid != null) {
            titleSpans.add(Span.raw("  "));
            InfraInfo selInfra = findSelectedInfra();
            if (selInfra != null) {
                titleSpans.add(Span.styled("selected: " + selectedName(), Theme.notice()));
            } else {
                titleSpans.add(Span.styled("selected: " + selectedName(), Theme.warning()));
            }
        }
        if (actionsPopup.notification() != null) {
            titleSpans.add(Span.raw("  "));
            Style style = actionsPopup.notificationError() ? Theme.error() : Theme.success();
            titleSpans.add(Span.styled(actionsPopup.notification(), style));
        }
        if (monitorNotification != null) {
            if (System.currentTimeMillis() > monitorNotificationExpiry) {
                monitorNotification = null;
            } else {
                titleSpans.add(Span.raw("  "));
                Style style = monitorNotificationError ? Theme.error() : Theme.success();
                titleSpans.add(Span.styled(monitorNotification, style));
            }
        }
        Line titleLine = Line.from(titleSpans);

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).build(),
                area);
    }

    private void renderTooSmall(Frame frame, Rect area) {
        Style orange = Style.EMPTY.fg(Color.rgb(0xF6, 0x91, 0x23));
        Style normal = Style.EMPTY;
        Style bold = Style.EMPTY.bold();

        String line1 = "Terminal size too small:";
        String wLabel = " Width = ";
        String wVal = String.valueOf(area.width());
        String hLabel = "  Height = ";
        String hVal = String.valueOf(area.height());
        String line2 = wLabel + wVal + hLabel + hVal;

        String line4 = "Needed for current config:";
        String line5 = " Width = " + MIN_WIDTH + "  Height = " + MIN_HEIGHT;

        // 5 content lines (2 + blank + 2 + blank), center vertically
        int startY = area.y() + Math.max(0, (area.height() - 5) / 2);

        int x1 = area.x() + Math.max(0, (area.width() - CharWidth.of(line1)) / 2);
        frame.buffer().setString(x1, startY, line1, bold);

        int x2 = area.x() + Math.max(0, (area.width() - CharWidth.of(line2)) / 2);
        int wLabelW = CharWidth.of(wLabel);
        int wValW = CharWidth.of(wVal);
        int hLabelW = CharWidth.of(hLabel);
        frame.buffer().setString(x2, startY + 1, wLabel, normal);
        frame.buffer().setString(x2 + wLabelW, startY + 1, wVal,
                area.width() < MIN_WIDTH ? orange : normal);
        frame.buffer().setString(x2 + wLabelW + wValW, startY + 1, hLabel, normal);
        frame.buffer().setString(x2 + wLabelW + wValW + hLabelW, startY + 1, hVal,
                area.height() < MIN_HEIGHT ? orange : normal);

        int x4 = area.x() + Math.max(0, (area.width() - CharWidth.of(line4)) / 2);
        frame.buffer().setString(x4, startY + 3, line4, bold);

        int x5 = area.x() + Math.max(0, (area.width() - CharWidth.of(line5)) / 2);
        frame.buffer().setString(x5, startY + 4, line5, normal);
    }

    private void renderTabs(Frame frame, Rect area) {
        boolean compact = area.width() < TABS_FULL_MIN_WIDTH;
        String dividerStr = compact ? "|" : " | ";
        Span divider = Span.styled(dividerStr, Theme.muted());
        boolean infraSelected = isInfraSelected();

        if (infraSelected) {
            // Infra mode: only Overview and Log tabs
            Line[] labels = compact
                    ? new Line[] {
                            Line.from("1 Overview"),
                            Line.from("2 Log"),
                    }
                    : new Line[] {
                            Line.from(" 1 Overview "),
                            Line.from(" 2 Log "),
                    };

            // Map real tab index to infra tab index for highlight
            int infraTabIdx = tabsState.selected() == TAB_LOG ? 1 : 0;
            TabsState infraTabsState = new TabsState(infraTabIdx);

            Tabs tabs = Tabs.builder()
                    .titles(labels)
                    .highlightStyle(Theme.accentBg())
                    .divider(divider)
                    .build();

            Rect labelsArea = area.height() >= 2
                    ? new Rect(area.x(), area.y() + 1, area.width(), 1)
                    : area;
            frame.renderStatefulWidget(tabs, labelsArea, infraTabsState);
            lastTabLabels = labels;
            lastTabDivider = dividerStr;
            return;
        }

        Line[] labels = compact
                ? new Line[] {
                        Line.from("1 Overview"),
                        Line.from("2 Log"),
                        Line.from("3 Diagram"),
                        Line.from(tabRegistry.routesTab().isTopMode() ? "4  Top " : "4 Route"),
                        Line.from("5 Endpoint"),
                        Line.from("6 HTTP"),
                        Line.from("7 Health"),
                        Line.from("8 Inspect"),
                        Line.from("9 Errors"),
                        Line.from("0 More▾"),
                }
                : new Line[] {
                        Line.from(" 1 Overview "),
                        Line.from(" 2 Log "),
                        Line.from(" 3 Diagram "),
                        Line.from(tabRegistry.routesTab().isTopMode() ? " 4  Top  " : " 4 Route "),
                        Line.from(" 5 Endpoint "),
                        Line.from(" 6 HTTP "),
                        Line.from(" 7 Health "),
                        Line.from(" 8 Inspect "),
                        Line.from(" 9 Errors "),
                        Line.from(" 0 More▾ "),
                };
        popupManager.setCurrentTabLabels(labels);
        lastTabLabels = labels;
        lastTabDivider = dividerStr;

        Tabs tabs = Tabs.builder()
                .titles(labels)
                .highlightStyle(Theme.accentBg())
                .divider(divider)
                .build();

        Rect labelsArea = area.height() >= 2
                ? new Rect(area.x(), area.y() + 1, area.width(), 1)
                : area;
        frame.renderStatefulWidget(tabs, labelsArea, tabsState);

        if (area.height() >= 2) {
            String[] badgeTexts = new String[labels.length];
            Style[] badgeStyles = new Style[labels.length];
            computeTabBadges(badgeTexts, badgeStyles);

            int badgeY = area.y();
            int dividerW = CharWidth.of(dividerStr);
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
        MonitorTab tab = tabRegistry.activeTab();
        if (tab != null) {
            tab.render(frame, area);
        }
        // Render "More" popup overlay when visible
        if (popupManager.isMorePopupVisible()) {
            popupManager.renderMorePopup(frame, area);
        }
        // Render "Switch integration" popup overlay when visible
        if (popupManager.isSwitchPopupVisible()) {
            popupManager.renderSwitchPopup(frame, area);
        }
        // Render "Files" popup overlay when visible
        if (filesBrowser.isVisible()) {
            filesBrowser.render(frame, area);
        }
    }

    private void computeTabBadges(String[] badgeTexts, Style[] badgeStyles) {
        Style yellow = Style.EMPTY.fg(Color.YELLOW).bold();
        Style cyan = Style.EMPTY.fg(Color.CYAN).bold();
        Style red = Style.EMPTY.fg(Color.LIGHT_RED).bold();
        for (int j = 0; j < badgeStyles.length; j++) {
            badgeTexts[j] = "";
            badgeStyles[j] = yellow;
        }

        List<IntegrationInfo> infos = dataService.data().get();
        long activeCount = infos.stream().filter(i -> !i.vanishing).count();
        IntegrationInfo sel = findSelectedIntegration();
        boolean hasSelection = ctx.selectedPid != null && sel != null;

        if (activeCount > 0) {
            badgeTexts[TAB_OVERVIEW] = "(" + activeCount + ")";
        }
        int routeCount = hasSelection ? sel.routes.size() : 0;
        if (routeCount > 0) {
            badgeTexts[TAB_DIAGRAM] = "(1)";
            badgeTexts[TAB_ROUTES] = "(" + routeCount + ")";
        }
        int endpointCount = hasSelection ? sel.endpoints.size() : 0;
        if (endpointCount > 0) {
            badgeTexts[TAB_ENDPOINTS] = "(" + endpointCount + ")";
        }
        int httpCount = hasSelection ? sel.httpEndpoints.size() : 0;
        if (httpCount > 0) {
            badgeTexts[TAB_HTTP] = "(" + httpCount + ")";
        }
        long healthDownCount = hasSelection
                ? sel.healthChecks.stream().filter(hc -> "DOWN".equals(hc.state)).count() : 0;
        if (healthDownCount > 0) {
            badgeTexts[TAB_HEALTH] = "(" + healthDownCount + " DOWN)";
            badgeStyles[TAB_HEALTH] = red;
        } else {
            int healthCount = hasSelection ? sel.healthChecks.size() : 0;
            if (healthCount > 0) {
                badgeTexts[TAB_HEALTH] = "(" + healthCount + ")";
            }
        }
        boolean hasTraces = hasSelection && !dataService.traces().get().isEmpty();
        if (hasTraces) {
            badgeTexts[TAB_HISTORY] = "(*)";
            badgeStyles[TAB_HISTORY] = cyan;
        } else {
            long historyCount = hasSelection
                    ? tabRegistry.historyTab().historyEntries.stream()
                            .map(e -> {
                                if (e.headers != null) {
                                    Object bid = e.headers.get("breadcrumbId");
                                    if (bid != null) {
                                        return bid.toString();
                                    }
                                }
                                return e.exchangeId;
                            })
                            .distinct().count()
                    : 0;
            if (historyCount > 0) {
                badgeTexts[TAB_HISTORY] = "(" + historyCount + ")";
            }
        }
        long cbOpenCount = hasSelection
                ? sel.circuitBreakers.stream()
                        .filter(cb -> cb.state != null && (cb.state.equalsIgnoreCase("open")
                                || cb.state.equalsIgnoreCase("forced_open")))
                        .count()
                : 0;
        if (cbOpenCount > 0) {
            badgeTexts[TAB_MORE] = "(" + cbOpenCount + " OPEN)";
            badgeStyles[TAB_MORE] = red;
        }
        int errorCount = hasSelection ? sel.errorCount : 0;
        if (errorCount > 0) {
            badgeTexts[TAB_ERRORS] = "(" + errorCount + ")";
            badgeStyles[TAB_ERRORS] = red;
        }
    }

    private void openFilesPopup() {
        IntegrationInfo info = findSelectedIntegration();
        if (info != null) {
            filesBrowser.open(info);
        }
    }

    private List<IntegrationInfo> getNonVanishingIntegrations() {
        return dataService.data().get().stream()
                .filter(i -> !i.vanishing && i.name != null)
                .sorted(Comparator.comparing(i -> i.name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private void stopSelectedProcess(boolean forceKill) {
        dataService.enableBurstMode();
        if (ctx.selectedPid == null) {
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            LOG.log(Level.DEBUG, "Cannot parse selected PID: {0}", ctx.selectedPid);
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
            String pidStr = ctx.selectedPid;
            ProcessHandle.of(pid).ifPresent(ph -> {
                if (forceKill) {
                    ph.destroyForcibly();
                    Path camelDir = CommandLineHelper.getCamelDir();
                    PathUtils.deleteFile(camelDir.resolve(pidStr + ".log"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-status.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-action.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-output.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-trace.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-history.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-debug.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-receive.json"));
                } else {
                    dataService.stoppingPids().add(pidStr);
                    ph.destroy();
                }
            });
        }
    }

    private void restartSelectedProcess() {
        dataService.enableBurstMode();
        if (ctx.selectedPid == null || isInfraSelected()) {
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            LOG.log(Level.DEBUG, "Cannot parse selected PID for restart: {0}", ctx.selectedPid);
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

        String platform = info.platform;
        boolean isSpringBoot = "Spring Boot".equals(platform);
        boolean isQuarkus = "Quarkus".equals(platform);

        // TODO: restart for Spring Boot and Quarkus is not yet reliable
        if (isSpringBoot || isQuarkus) {
            setNotification("Restart not supported for " + platform, true);
            return;
        }

        restartCamelMainProcess(ph, info);
    }

    private void restartCamelMainProcess(ProcessHandle ph, IntegrationInfo info) {
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
                    } else {
                        cmdLineOpt.ifPresent(s -> cmd.addAll(parseCommandLine(s)));
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
        monitorNotificationExpiry = System.currentTimeMillis() + (error ? 15000 : 5000);
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
        dataService.metrics().resetStats(pid);
    }

    private void sendRouteCommand(String pid, String routeId, String command) {
        dataService.enableBurstMode();
        JsonObject root = new JsonObject();
        root.put("action", "route");
        root.put("id", routeId);
        root.put("command", command);
        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);
    }

    private void renderFooter(Frame frame, Rect area) {
        // Disable footer key-binding clicks until the normal path below re-captures the hint regions.
        // Overlay/early-return states (screenshot flash, help, caption) leave the footer non-clickable.
        footerRowY = -1;

        // Show screenshot flash message briefly
        String msg = recordingManager.screenshotFlashMessage();
        if (msg != null) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled(" " + msg, Theme.success()))),
                    area);
            return;
        }

        List<Span> spans = new ArrayList<>();
        int fKeyTotal = 0;

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

        if (filesBrowser.isVisible()) {
            filesBrowser.renderFooter(spans);
        } else if (popupManager.isSwitchPopupVisible()) {
            hint(spans, "Up/Down", "select");
            hint(spans, "Enter", "switch");
            hint(spans, "Esc", "close");
        } else if (popupManager.isMorePopupVisible()) {
            hint(spans, "Up/Down", "select");
            hint(spans, "Enter", "open");
            hint(spans, "Esc", "close");
        } else if (shellPanel.isOpen()) {
            shellPanel.renderFooter(spans);
        } else if (aiPanel.isOpen()) {
            aiPanel.renderFooter(spans);
        } else {
            MonitorTab tab = tabRegistry.activeTab();

            if (tabRegistry.selectedTabIndex() == TAB_OVERVIEW) {
                fKeyTotal = renderOverviewFooter(spans);
            } else {
                tab.renderFooter(spans);
                fKeyTotal = insertFKeyHints(spans);
            }
        }

        List<Span> rightSpans = new ArrayList<>();

        if (recordingManager.isRecording() && !recordingManager.getRecentKeys().isEmpty()) {
            long now = System.currentTimeMillis();
            List<RecordingManager.KeyRecord> recentKeys = recordingManager.getRecentKeys();
            int maxKeys = Math.min(recentKeys.size(), 8);
            List<RecordingManager.KeyRecord> visible = recentKeys.subList(recentKeys.size() - maxKeys, recentKeys.size());
            for (RecordingManager.KeyRecord kr : visible) {
                long age = now - kr.timestamp();
                Style style = age < 1000
                        ? Theme.selectionBg()
                        : Theme.muted();
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
                labelStyle = Theme.success();
                suffixStyle = active ? Theme.mcpActive() : Theme.mcpIdle();
            } else {
                suffix = " ✗";
                labelStyle = Theme.muted();
                suffixStyle = Theme.mcpDown();
            }
            rightSpans.add(Span.styled(mcpLabel, labelStyle));
            rightSpans.add(Span.styled(suffix, suffixStyle));
            if (client == null) {
                rightSpans.add(Span.styled("  F2 → Setup AI", Theme.muted()));
            }
        }

        int hintsWidth = spans.stream().mapToInt(Span::width).sum();
        int rightWidth = rightSpans.stream().mapToInt(Span::width).sum();
        int minGap = rightSpans.isEmpty() ? 0 : 1;

        if (hintsWidth + rightWidth + minGap > area.width()) {
            // Drop decorative right-side content first
            rightSpans.clear();
            rightWidth = 0;
            minGap = 0;
            // Drop secondary F-key hints (F2/F3/F6) before tab-specific action hints.
            hintsWidth = dropFKeyHints(spans, fKeyTotal, hintsWidth, area.width());
            // Then drop tab-specific hints from the tail, keeping at least 4 spans
            while (spans.size() > 4 && hintsWidth > area.width()) {
                Span labelSpan = spans.remove(spans.size() - 1);
                Span keySpan = spans.remove(spans.size() - 1);
                hintsWidth -= keySpan.width() + labelSpan.width();
            }
        }

        if (!rightSpans.isEmpty()) {
            int gap = Math.max(1, area.width() - hintsWidth - rightWidth);
            spans.add(Span.raw(" ".repeat(gap)));
            spans.addAll(rightSpans);
        }

        captureFooterRegions(area, spans);
        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }

    private int insertFKeyHints(List<Span> spans) {
        int insertPos = Math.min(2, spans.size());
        List<Span> fKeySpans = new ArrayList<>();
        MonitorTab tab = tabRegistry.activeTab();
        boolean hasHelp = tab != null && tab.getHelpText() != null;
        if (hasHelp) {
            hint(fKeySpans, "F1", "help");
        }
        hint(fKeySpans, "F2", "actions");
        if (popupManager.getNonVanishingIntegrations().size() > 1) {
            hint(fKeySpans, "F3", "switch");
        }
        hint(fKeySpans, "F6", "shell");
        hint(fKeySpans, "F8", "AI");
        spans.addAll(insertPos, fKeySpans);
        // Return total F-key span count. The footer drop loop uses this to remove pairs from
        // the tail (F6, then F3, F2), stopping before the first pair (F1 help when present).
        return fKeySpans.size();
    }

    /**
     * Drops secondary F-key hint pairs from an overflowing footer. The F-key pairs are inserted at position 2 (after
     * the first tab hint), so the last pair's key span sits at index {@code fKeyTotal}. Pairs are removed from the
     * tail, so F6 goes first, then F3, then F2, and the loop stops at 2 so the first pair (F1 help when present) is
     * always preserved.
     *
     * @param  spans      the footer spans, mutated in place by removing dropped pairs
     * @param  fKeyTotal  total number of F-key spans that were inserted (e.g. 8 for F1/F2/F3/F6)
     * @param  hintsWidth the current rendered width of {@code spans}
     * @param  available  the available footer width
     * @return            the rendered width of {@code spans} after dropping
     */
    static int dropFKeyHints(List<Span> spans, int fKeyTotal, int hintsWidth, int available) {
        while (fKeyTotal > 2 && hintsWidth > available) {
            Span labelSpan = spans.remove(fKeyTotal + 1);
            Span keySpan = spans.remove(fKeyTotal);
            hintsWidth -= keySpan.width() + labelSpan.width();
            fKeyTotal -= 2;
        }
        return hintsWidth;
    }

    private int renderOverviewFooter(List<Span> spans) {
        if (actionsPopup.isVisible()) {
            actionsPopup.renderFooter(spans);
            return 0;
        }
        tabRegistry.overviewTab().renderFooter(spans);
        int fKeyTotal = insertFKeyHints(spans);
        // Process action hints
        if (ctx.selectedPid != null && !isInfraSelected()) {
            IntegrationInfo selInfo = findSelectedIntegration();
            if (selInfo != null) {
                if (selInfo.readmeFiles != null && !selInfo.readmeFiles.isEmpty()) {
                    hint(spans, "d", "docs");
                }
                if (selInfo.directory != null && !selInfo.directory.isEmpty()) {
                    hint(spans, "f", "files");
                }
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
        return fKeyTotal;
    }

    // ---- Data Loading ----

    private void refreshLogData() {
        if (tabRegistry.selectedTabIndex() != TAB_LOG) {
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
        if (logPid != null) {
            tabRegistry.logTab().refreshFromFile(logPid, logFileName);
        }
    }

    private void refreshConditionalData() {
        List<Long> selectedPids = dataService.selectedPidAsList();
        if (tabRegistry.selectedTabIndex() == TAB_ERRORS && !selectedPids.isEmpty()) {
            dataService.refreshErrorData(selectedPids);
        }
        if (tabRegistry.selectedTabIndex() == TAB_HISTORY && !selectedPids.isEmpty()) {
            if (tabRegistry.historyTab().historyRefreshRequested) {
                tabRegistry.historyTab().historyRefreshRequested = false;
                tabRegistry.historyTab().historyEntries = dataService.loadHistoryData(selectedPids);
            }
            dataService.refreshTraceData(selectedPids);
        }
        if (tabRegistry.selectedTabIndex() == TAB_MORE
                && tabRegistry.getActiveMoreTab() == tabRegistry.spansTab()
                && ctx.selectedPid != null && tabRegistry.spansTab().spanRefreshRequested) {
            tabRegistry.spansTab().spanRefreshRequested = false;
            dataService.refreshSpanData();
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

    // ---- MCP .mcp.json lifecycle ----

    private static Path writeMcpJson(int port) {
        Path path = Path.of(".mcp.json");
        try {
            String json = """
                    {
                      "mcpServers": {
                        "camel-tui": {
                          "type": "http",
                          "url": "http://localhost:%d/mcp"
                        }
                      }
                    }
                    """.formatted(port);
            Files.writeString(path, json);
            return path;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to write .mcp.json: {0}", e.getMessage());
            return null;
        }
    }

    private static void deleteMcpJson(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                LOG.log(Level.DEBUG, "Failed to delete .mcp.json: {0}", e.getMessage());
            }
        }
    }

}
