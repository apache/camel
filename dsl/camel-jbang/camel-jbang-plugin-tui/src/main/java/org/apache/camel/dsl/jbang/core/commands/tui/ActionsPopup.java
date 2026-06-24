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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hintLast;

class ActionsPopup {

    enum Action {
        SEND_MESSAGE,
        RUN_EXAMPLE,
        RUN_FOLDER,
        RUN_INFRA,
        BROWSE_FILES,
        DOCTOR,
        RESET_STATS,
        RESET_SCREEN,
        STOP_ALL,
        SCREENSHOT,
        TAPE_RECORDING,
        TAPE_INSTRUCTIONS,
        CAPTION,
        SHOW_KEYSTROKES,
        SETUP_AI,
        MCP_INFO,
        MCP_LOG,
        SHELL
    }

    private static final int[] GROUP_SIZES = { 5, 4, 5 };
    private static final int MCP_GROUP_SIZE = 3;
    private static final int SHELL_GROUP_SIZE = 1;

    private final Supplier<Set<String>> runningNames;
    private final Supplier<List<IntegrationInfo>> integrations;
    private final Supplier<List<InfraInfo>> infraServices;
    private final Runnable screenshotAction;
    private final Runnable toggleKeystrokes;
    private final Supplier<Boolean> keystrokesEnabled;
    private final Runnable toggleTapeRecording;
    private final Runnable burstCallback;
    private Runnable resetStatsAction;
    private Runnable resetScreenAction;
    private Runnable openShellAction;
    private Runnable browseFilesAction;
    private final Supplier<Boolean> tapeRecordingActive;
    private MonitorContext ctx;
    private boolean mcpEnabled;
    private int mcpPort;
    private Supplier<String> mcpConnectedClient;
    private Supplier<List<TuiMcpServer.LogEntry>> mcpActivityLog;

    private boolean showActionsMenu;
    private final ListState actionsMenuState = new ListState();

    private boolean showExampleBrowser;
    private final ListState exampleBrowserState = new ListState();
    private List<JsonObject> exampleCatalog;

    private final RunOptionsForm runOptionsForm = new RunOptionsForm();
    private JsonObject selectedExample;

    private boolean showDocPicker;
    private final ListState docPickerState = new ListState();
    private List<IntegrationInfo> docPickerIntegrations;
    private boolean showDocViewer;
    private boolean docViewerFromExampleBrowser;
    private String docContent;
    private List<Line> docLines;
    private String docTitle;
    private int docScroll;

    private boolean showInfraBrowser;
    private final ListState infraBrowserState = new ListState();
    private List<InfraServiceEntry> infraCatalog;
    private boolean showInfraPortDialog;
    private InfraServiceEntry selectedInfraService;
    private int infraImplIndex;
    private TextInputState infraPortState;

    private boolean showFolderInput;
    private TextInputState folderInputState;
    private final List<String> folderHistory = new ArrayList<>();
    private int folderHistoryIndex = -1;
    private String selectedFolder;

    private final McpLogPopup mcpLogPopup = new McpLogPopup();

    private final DoctorPopup doctorPopup = new DoctorPopup();
    private final SendMessagePopup sendMessagePopup = new SendMessagePopup();
    private final StopAllPopup stopAllPopup;
    private final CaptionOverlay captionOverlay;
    private ScheduledExecutorService scheduler;

    private final List<PendingLaunch> pendingLaunches = new ArrayList<>();
    private DeferredLaunch deferredLaunch;
    private String launchNotification;
    private boolean launchNotificationError;
    private long launchNotificationExpiry;
    private volatile String pendingAutoSelect;
    private String preSelectedRouteId;

    ActionsPopup(Supplier<Set<String>> runningNames, Supplier<List<IntegrationInfo>> integrations,
                 Supplier<List<InfraInfo>> infraServices, CaptionOverlay captionOverlay,
                 Runnable screenshotAction, Runnable toggleKeystrokes, Supplier<Boolean> keystrokesEnabled,
                 Runnable toggleTapeRecording, Supplier<Boolean> tapeRecordingActive,
                 Runnable burstCallback, Set<String> stoppingPids) {
        this.runningNames = runningNames;
        this.integrations = integrations;
        this.infraServices = infraServices;
        this.captionOverlay = captionOverlay;
        this.screenshotAction = screenshotAction;
        this.toggleKeystrokes = toggleKeystrokes;
        this.keystrokesEnabled = keystrokesEnabled;
        this.toggleTapeRecording = toggleTapeRecording;
        this.tapeRecordingActive = tapeRecordingActive;
        this.burstCallback = burstCallback;
        this.stopAllPopup = new StopAllPopup(integrations, infraServices, burstCallback, stoppingPids);
    }

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    void setPreSelectedRouteId(String routeId) {
        this.preSelectedRouteId = routeId;
    }

    void setResetStatsAction(Runnable resetStatsAction) {
        this.resetStatsAction = resetStatsAction;
    }

    void setResetScreenAction(Runnable resetScreenAction) {
        this.resetScreenAction = resetScreenAction;
    }

    void setOpenShellAction(Runnable openShellAction) {
        this.openShellAction = openShellAction;
    }

    void setBrowseFilesAction(Runnable browseFilesAction) {
        this.browseFilesAction = browseFilesAction;
    }

    void setMcpEnabled(
            boolean enabled, int port, Supplier<String> connectedClient, Supplier<List<TuiMcpServer.LogEntry>> activityLog) {
        this.mcpEnabled = enabled;
        this.mcpPort = port;
        this.mcpConnectedClient = connectedClient;
        this.mcpActivityLog = activityLog;
        mcpLogPopup.setActivityLog(activityLog);
    }

    private int visualActionCount() {
        int total = 0;
        for (int gs : GROUP_SIZES) {
            total += gs;
        }
        int dividers = GROUP_SIZES.length - 1;
        if (mcpEnabled) {
            total += MCP_GROUP_SIZE;
            dividers++;
        }
        total += SHELL_GROUP_SIZE;
        dividers++;
        return total + dividers;
    }

    private boolean isDividerIndex(int visualIndex) {
        int pos = 0;
        for (int i = 0; i < GROUP_SIZES.length; i++) {
            pos += GROUP_SIZES[i];
            if (visualIndex == pos) {
                return true;
            }
            pos++;
        }
        if (mcpEnabled) {
            pos += MCP_GROUP_SIZE;
            if (visualIndex == pos) {
                return true;
            }
            pos++;
        }
        pos += SHELL_GROUP_SIZE;
        return false;
    }

    private Action resolveAction(int visualIndex) {
        List<Action> flat = buildVisualActionList();
        if (visualIndex >= 0 && visualIndex < flat.size()) {
            return flat.get(visualIndex);
        }
        return null;
    }

    private List<Action> buildVisualActionList() {
        List<Action> flat = new ArrayList<>();
        flat.addAll(List.of(
                Action.SEND_MESSAGE, Action.RUN_EXAMPLE, Action.RUN_FOLDER, Action.RUN_INFRA, Action.BROWSE_FILES));
        flat.add(null);
        flat.addAll(List.of(Action.DOCTOR, Action.RESET_STATS, Action.RESET_SCREEN, Action.STOP_ALL));
        flat.add(null);
        flat.addAll(List.of(
                Action.SCREENSHOT, Action.TAPE_RECORDING, Action.TAPE_INSTRUCTIONS, Action.CAPTION,
                Action.SHOW_KEYSTROKES));
        if (mcpEnabled) {
            flat.add(null);
            flat.addAll(List.of(Action.SETUP_AI, Action.MCP_INFO, Action.MCP_LOG));
        }
        flat.add(null);
        flat.add(Action.SHELL);
        return flat;
    }

    private void navigateActionsMenu(int direction) {
        int total = visualActionCount();
        Integer current = actionsMenuState.selected();
        int next = (current != null ? current : 0) + direction;
        next = Math.max(0, Math.min(next, total - 1));
        while (isDividerIndex(next) && next > 0 && next < total - 1) {
            next += direction;
        }
        next = Math.max(0, Math.min(next, total - 1));
        if (!isDividerIndex(next)) {
            actionsMenuState.select(next);
        }
    }

    boolean isVisible() {
        return showActionsMenu || showExampleBrowser || showFolderInput || runOptionsForm.isVisible()
                || showDocPicker || showDocViewer
                || showInfraBrowser || showInfraPortDialog
                || mcpLogPopup.isVisible() || doctorPopup.isVisible()
                || sendMessagePopup.isVisible() || stopAllPopup.isVisible() || captionOverlay.isInlineMode();
    }

    SelectionContext getSelectionContext() {
        if (showInfraBrowser && infraCatalog != null) {
            List<String> items = infraCatalog.stream().map(e -> e.alias).collect(Collectors.toList());
            Integer sel = infraBrowserState.selected();
            return new SelectionContext("list", items, sel != null ? sel : -1, infraCatalog.size(), "Dev/Infra Services");
        }
        if (showExampleBrowser && exampleCatalog != null) {
            List<String> items = new ArrayList<>();
            String currentLevel = null;
            for (JsonObject ex : exampleCatalog) {
                String level = ex.getStringOrDefault("level", "beginner");
                if (!level.equals(currentLevel)) {
                    currentLevel = level;
                    items.add("── " + capitalize(level) + " ──");
                }
                items.add(ex.getStringOrDefault("name", ""));
            }
            int total = countExampleListItems();
            Integer sel = exampleBrowserState.selected();
            return new SelectionContext("list", items, sel != null ? sel : -1, total, "Examples");
        }
        if (showActionsMenu) {
            List<String> items = getActionLabels();
            Integer sel = actionsMenuState.selected();
            return new SelectionContext("popup", items, sel != null ? sel : -1, visualActionCount(), "Actions");
        }
        return null;
    }

    String getPendingAutoSelect() {
        return pendingAutoSelect;
    }

    void clearPendingAutoSelect() {
        pendingAutoSelect = null;
    }

    List<String> getActionLabels() {
        List<String> labels = new ArrayList<>();
        // Group 1: User Actions
        labels.add("Send Message");
        labels.add("Run an example...");
        labels.add("Run from folder...");
        labels.add("Run Dev/Infra Service...");
        labels.add("Browse Files...");
        labels.add("───");
        // Group 2: Diagnostics
        labels.add("Run Doctor");
        labels.add("Reset Stats");
        labels.add("Reset Screen");
        labels.add("Stop All");
        labels.add("───");
        // Group 3: Recording & Presentation
        labels.add("Take Screenshot");
        labels.add(tapeRecordingActive.get() ? "Stop Tape Recording" : "Start Tape Recording");
        labels.add("Tape Recording Guide");
        labels.add("Caption...");
        labels.add(keystrokesEnabled.get() ? "Hide Keystrokes" : "Show Keystrokes");
        // Group 4: MCP
        if (mcpEnabled) {
            labels.add("───");
            labels.add("Setup AI...");
            labels.add("MCP Info");
            labels.add("MCP Log");
        }
        labels.add("───");
        labels.add("Shell");
        return labels;
    }

    void open() {
        showActionsMenu = true;
        actionsMenuState.select(0);
    }

    void close() {
        showActionsMenu = false;
        showExampleBrowser = false;
        showFolderInput = false;
        runOptionsForm.close();
        showDocPicker = false;
        showDocViewer = false;
        showInfraBrowser = false;
        showInfraPortDialog = false;
        mcpLogPopup.close();
        doctorPopup.close();
        sendMessagePopup.close();
        stopAllPopup.close();
        captionOverlay.close();
    }

    String notification() {
        return launchNotification;
    }

    boolean notificationError() {
        return launchNotificationError;
    }

    void handlePaste(String text) {
        if (sendMessagePopup.isVisible()) {
            sendMessagePopup.handlePaste(text);
        }
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (sendMessagePopup.isVisible()) {
            if (ke.isConfirm()) {
                sendMessagePopup.doSend(ctx, scheduler);
            } else {
                sendMessagePopup.handleKeyEvent(ke);
            }
            return true;
        }
        if (mcpLogPopup.handleKeyEvent(ke)) {
            return true;
        }
        if (showDocViewer) {
            if (ke.isCancel()) {
                showDocViewer = false;
                if (docViewerFromExampleBrowser) {
                    docViewerFromExampleBrowser = false;
                    showExampleBrowser = true;
                }
            } else if (ke.isUp() || ke.isChar('k')) {
                docScroll = Math.max(0, docScroll - 1);
            } else if (ke.isDown() || ke.isChar('j')) {
                docScroll++;
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                docScroll = Math.max(0, docScroll - 10);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                docScroll += 10;
            }
            return true;
        }
        if (showDocPicker) {
            if (ke.isCancel()) {
                showDocPicker = false;
                showActionsMenu = true;
            } else if (ke.isUp()) {
                docPickerState.selectPrevious();
            } else if (ke.isDown()) {
                docPickerState.selectNext(docPickerIntegrations != null ? docPickerIntegrations.size() : 0);
            } else if (ke.isConfirm()) {
                loadDocFromSelectedIntegration();
            }
            return true;
        }
        if (showInfraPortDialog) {
            if (ke.isCancel()) {
                showInfraPortDialog = false;
                showInfraBrowser = true;
            } else if (ke.isConfirm()) {
                launchInfraService();
            } else if (selectedInfraService != null && selectedInfraService.implementations.size() > 1) {
                if (ke.isLeft()) {
                    infraImplIndex = (infraImplIndex - 1 + selectedInfraService.implementations.size())
                                     % selectedInfraService.implementations.size();
                    return true;
                } else if (ke.isRight()) {
                    infraImplIndex = (infraImplIndex + 1) % selectedInfraService.implementations.size();
                    return true;
                }
            }
            handlePortInput(ke);
            return true;
        }
        if (showInfraBrowser) {
            if (ke.isCancel()) {
                showInfraBrowser = false;
                showActionsMenu = true;
            } else if (ke.isUp()) {
                navigateInfraBrowser(-1);
            } else if (ke.isDown()) {
                navigateInfraBrowser(1);
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                navigateInfraBrowser(-10);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                navigateInfraBrowser(10);
            } else if (ke.isConfirm()) {
                selectInfraService();
            } else if (ke.code() == KeyCode.CHAR) {
                jumpToInfraService(ke.character());
            }
            return true;
        }
        if (runOptionsForm.isVisible()) {
            if (ke.isCancel()) {
                runOptionsForm.close();
                if (selectedFolder != null) {
                    showFolderInput = true;
                } else {
                    showExampleBrowser = true;
                }
            } else if (ke.isConfirm()) {
                if (selectedFolder != null) {
                    launchFolder();
                } else {
                    launchWithName();
                }
            } else {
                runOptionsForm.handleKeyEvent(ke);
            }
            return true;
        }
        if (showFolderInput) {
            if (ke.isCancel()) {
                showFolderInput = false;
                showActionsMenu = true;
            } else if (ke.isConfirm()) {
                confirmFolderInput();
            } else if (ke.isUp()) {
                navigateFolderHistory(-1);
            } else if (ke.isDown()) {
                navigateFolderHistory(1);
            } else {
                handleFolderTextInput(ke);
            }
            return true;
        }
        if (showExampleBrowser) {
            if (ke.isCancel()) {
                showExampleBrowser = false;
                showActionsMenu = true;
            } else if (ke.isUp()) {
                navigateExampleBrowser(-1);
            } else if (ke.isDown()) {
                navigateExampleBrowser(1);
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                navigateExampleBrowser(-10);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                navigateExampleBrowser(10);
            } else if (ke.isChar('r')) {
                launchSelectedExample();
            } else if (ke.isChar('d')) {
                loadDocFromExample();
            } else if (ke.isConfirm()) {
                openNameInput();
            }
            return true;
        }
        if (captionOverlay.isInlineMode()) {
            return captionOverlay.handleKeyEvent(ke);
        }
        if (stopAllPopup.handleKeyEvent(ke)) {
            checkStopAllNotification();
            return true;
        }
        if (doctorPopup.handleKeyEvent(ke)) {
            return true;
        }
        if (showActionsMenu) {
            if (ke.isCancel()) {
                showActionsMenu = false;
            } else if (ke.isUp()) {
                navigateActionsMenu(-1);
            } else if (ke.isDown()) {
                navigateActionsMenu(1);
            } else if (ke.isConfirm()) {
                Integer sel = actionsMenuState.selected();
                if (sel != null) {
                    Action action = resolveAction(sel);
                    if (action == null) {
                        // divider selected, ignore
                    } else if (action == Action.SHELL) {
                        showActionsMenu = false;
                        if (openShellAction != null) {
                            openShellAction.run();
                        }
                    } else if (action == Action.RUN_EXAMPLE) {
                        openExampleBrowser();
                    } else if (action == Action.RUN_FOLDER) {
                        openFolderInput();
                    } else if (action == Action.SCREENSHOT) {
                        showActionsMenu = false;
                        screenshotAction.run();
                    } else if (action == Action.SHOW_KEYSTROKES) {
                        showActionsMenu = false;
                        toggleKeystrokes.run();
                    } else if (action == Action.TAPE_RECORDING) {
                        showActionsMenu = false;
                        toggleTapeRecording.run();
                    } else if (action == Action.TAPE_INSTRUCTIONS) {
                        showActionsMenu = false;
                        openTapeInstructions();
                    } else if (action == Action.BROWSE_FILES) {
                        if (ctx != null && ctx.selectedPid != null && !ctx.isInfraSelected()) {
                            showActionsMenu = false;
                            if (browseFilesAction != null) {
                                browseFilesAction.run();
                            }
                        }
                    } else if (action == Action.DOCTOR) {
                        showActionsMenu = false;
                        doctorPopup.open();
                    } else if (action == Action.RUN_INFRA) {
                        openInfraBrowser();
                    } else if (action == Action.SETUP_AI) {
                        showActionsMenu = false;
                        openSetupAI();
                    } else if (action == Action.MCP_INFO) {
                        showActionsMenu = false;
                        openMcpInfo();
                    } else if (action == Action.MCP_LOG) {
                        showActionsMenu = false;
                        openMcpLog();
                    } else if (action == Action.SEND_MESSAGE) {
                        showActionsMenu = false;
                        openSendMessage();
                    } else if (action == Action.RESET_STATS) {
                        showActionsMenu = false;
                        if (resetStatsAction != null) {
                            resetStatsAction.run();
                        }
                    } else if (action == Action.RESET_SCREEN) {
                        showActionsMenu = false;
                        if (resetScreenAction != null) {
                            resetScreenAction.run();
                        }
                    } else if (action == Action.STOP_ALL) {
                        showActionsMenu = false;
                        stopAllPopup.open();
                        checkStopAllNotification();
                    } else if (action == Action.CAPTION) {
                        showActionsMenu = false;
                        captionOverlay.openInline();
                    }
                }
            }
            return true;
        }
        return false;
    }

    void render(Frame frame, Rect area) {
        if (showActionsMenu) {
            renderActionsMenu(frame, area);
        }
        if (showInfraBrowser) {
            renderInfraBrowser(frame, area);
        }
        if (showInfraPortDialog) {
            renderInfraPortDialog(frame, area);
        }
        if (showFolderInput) {
            renderFolderInput(frame, area);
        }
        if (showExampleBrowser) {
            renderExampleBrowser(frame, area);
        }
        if (runOptionsForm.isVisible()) {
            runOptionsForm.render(frame, area);
        }
        if (showDocPicker) {
            renderDocPicker(frame, area);
        }
        if (showDocViewer) {
            renderDocViewer(frame, area);
        }
        if (mcpLogPopup.isVisible()) {
            mcpLogPopup.render(frame, area);
        }
        if (doctorPopup.isVisible()) {
            doctorPopup.render(frame, area);
        }
        if (stopAllPopup.isVisible()) {
            stopAllPopup.render(frame, area);
        }
        if (sendMessagePopup.isVisible()) {
            sendMessagePopup.render(frame, area);
        }
        if (captionOverlay.isInlineMode()) {
            captionOverlay.render(frame, area);
        }
    }

    void renderFooter(List<Span> spans) {
        if (sendMessagePopup.isVisible()) {
            sendMessagePopup.renderFooter(spans);
            return;
        }
        if (captionOverlay.isInlineMode()) {
            captionOverlay.renderFooter(spans);
            return;
        }
        if (stopAllPopup.isVisible()) {
            stopAllPopup.renderFooter(spans);
            return;
        }
        if (doctorPopup.isVisible()) {
            doctorPopup.renderFooter(spans);
            return;
        }
        if (mcpLogPopup.isVisible()) {
            mcpLogPopup.renderFooter(spans);
            return;
        }
        if (showDocViewer) {
            hint(spans, "↑↓", "scroll");
            hintLast(spans, "Esc", "back");
            return;
        }
        if (showDocPicker) {
            hint(spans, "↑↓", "navigate");
            hint(spans, "Enter", "view");
            hintLast(spans, "Esc", "back");
            return;
        }
        if (runOptionsForm.isVisible()) {
            runOptionsForm.renderFooter(spans);
            return;
        }
        if (showFolderInput) {
            if (!folderHistory.isEmpty()) {
                hint(spans, "↑↓", "history");
            }
            hint(spans, "Enter", "run...");
            hintLast(spans, "Esc", "back");
            return;
        }
        if (showInfraPortDialog) {
            hint(spans, "Enter", "run");
            hintLast(spans, "Esc", "back");
            return;
        }
        if (showInfraBrowser) {
            hint(spans, "↑↓", "navigate");
            hint(spans, "Enter", "select");
            hintLast(spans, "Esc", "back");
            return;
        }
        if (showExampleBrowser) {
            hint(spans, "↑↓", "navigate");
            hint(spans, "r", "run");
            hint(spans, "Enter", "run...");
            hint(spans, "d", "docs");
            hintLast(spans, "Esc", "back");
            return;
        }
        if (showActionsMenu) {
            hint(spans, "↑↓", "navigate");
            hint(spans, "Enter", "select");
            hintLast(spans, "Esc", "cancel");
        }
    }

    void tick(long now) {
        monitorPendingLaunches(now);
        checkDeferredLaunch(now);
        if (launchNotification != null && now > launchNotificationExpiry) {
            launchNotification = null;
        }
    }

    // ---- Rendering ----

    private void renderActionsMenu(Frame frame, Rect area) {
        int count = visualActionCount();
        int popupW = 40;
        int popupH = 2 + count;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);
        String divider = "  ─────────────────────────────────";
        // CAMEL-23818: use plain 2-wide emoji here. TamboUI mismeasures base-glyph + VS16
        // sequences as 1-wide (fixed upstream in tamboui/tamboui#388), which left stray chars.
        String keystrokeLabel = keystrokesEnabled.get()
                ? "  🔤 Hide Keystrokes"
                : "  🔤 Show Keystrokes";
        String stopLabel = stopAllPopup.hasBothGroups()
                ? "  🛑 Stop All..."
                : "  🛑 Stop All";
        String tapeLabel = tapeRecordingActive.get()
                ? "  🛑 Stop Tape Recording (Ctrl+R)"
                : "  🔴 Start Tape Recording (Ctrl+R)";

        List<ListItem> items = new ArrayList<>();
        // Group 1: User Actions
        items.add(ListItem.from("  📩 Send Message"));
        items.add(ListItem.from("  🐪 Run an example..."));
        items.add(ListItem.from("  📂 Run from folder..."));
        items.add(ListItem.from("  🔧 Run Dev/Infra Service..."));
        boolean hasSelection = ctx != null && ctx.selectedPid != null && !ctx.isInfraSelected();
        items.add(hasSelection
                ? ListItem.from("  📁 Browse Files...")
                : ListItem.from("  📁 Browse Files...").style(Style.EMPTY.dim()));
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        // Group 2: Diagnostics
        items.add(ListItem.from("  🩺 Run Doctor"));
        items.add(ListItem.from("  🔄 Reset Stats"));
        items.add(ListItem.from("  🧹 Reset Screen"));
        items.add(ListItem.from(stopLabel));
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        // Group 3: Recording & Presentation
        items.add(ListItem.from("  📸 Take Screenshot"));
        items.add(ListItem.from(tapeLabel));
        items.add(ListItem.from("  📄 Tape Recording Guide"));
        items.add(ListItem.from("  💬 Caption..."));
        items.add(ListItem.from(keystrokeLabel));
        // Group 4: MCP
        if (mcpEnabled) {
            items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
            items.add(ListItem.from("  🧠 Setup AI..."));
            items.add(ListItem.from("  🤖 MCP Info"));
            items.add(ListItem.from("  📋 MCP Log"));
        }
        // Group 5: Shell
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        items.add(ListItem.from("  >_ Shell"));
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" Actions ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, actionsMenuState);
    }

    private void renderExampleBrowser(Frame frame, Rect area) {
        if (exampleCatalog == null || exampleCatalog.isEmpty()) {
            return;
        }
        int popupW = Math.min(100, area.width() - 4);
        int popupH = Math.min(exampleCatalog.size() + 10, Math.min(22, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        List<ListItem> items = buildExampleListItems(popupW - 4);
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" Run an Example (" + exampleCatalog.size() + ") ")
                        .titleBottom(Title.from(Line.from(
                                Span.styled(" r", MonitorContext.HINT_KEY_STYLE), Span.raw(" run │"),
                                Span.styled(" Enter", MonitorContext.HINT_KEY_STYLE), Span.raw(" run... │"),
                                Span.styled(" d", MonitorContext.HINT_KEY_STYLE), Span.raw(" docs │"),
                                Span.styled(" ↑↓", MonitorContext.HINT_KEY_STYLE), Span.raw(" navigate │"),
                                Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE), Span.raw(" back "))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, exampleBrowserState);
    }

    private List<ListItem> buildExampleListItems(int width) {
        List<ListItem> items = new ArrayList<>();
        String currentLevel = null;
        for (JsonObject ex : exampleCatalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                String header = "── " + capitalize(level) + " ──";
                items.add(ListItem.from(header).style(Style.EMPTY.dim()));
            }
            String name = ex.getStringOrDefault("name", "");
            String desc = ex.getStringOrDefault("description", "");
            boolean docker = ExampleHelper.requiresDocker(ex);
            boolean bundled = ExampleHelper.isBundled(ex);
            boolean citrus = ExampleHelper.hasCitrusTests(ex);
            boolean infra = !ExampleHelper.getInfraServices(ex).isEmpty();

            String icons = (bundled ? "📦" : "🌐") + (docker ? "🐳" : "  ")
                           + (infra ? "🔧" : "  ") + (citrus ? "🍋" : "  ");
            int nameCol = Math.min(30, width / 3);
            String padded = String.format("%-" + nameCol + "s", TuiHelper.truncate(name, nameCol));
            String prefix = " " + icons + " " + padded + " ";
            int descCol = Math.max(10, width - prefix.length());

            Style style = bundled ? Style.EMPTY : Style.EMPTY.dim();
            if (desc.length() <= descCol) {
                items.add(ListItem.from(prefix + desc).style(style));
            } else {
                String indent = " ".repeat(prefix.length());
                List<Line> lines = new ArrayList<>();
                List<String> wrapped = wrapWords(desc, descCol);
                lines.add(Line.from(prefix + wrapped.get(0)));
                for (int w = 1; w < wrapped.size(); w++) {
                    lines.add(Line.from(indent + wrapped.get(w)));
                }
                items.add(ListItem.from(Text.from(lines.toArray(Line[]::new))).style(style));
            }
        }
        items.add(ListItem.from(""));
        items.add(ListItem.from(" 📦 = bundled  🌐 = online  🐳 = Docker  🔧 = infra services  🍋 = Citrus tests")
                .style(Style.EMPTY.dim()));
        return items;
    }

    // ---- Doc Viewer & Picker ----

    private void renderDocViewer(Frame frame, Rect area) {
        frame.renderWidget(Clear.INSTANCE, area);
        Rect popup = new Rect(area.left() + 2, area.top() + 1, area.width() - 4, area.height() - 2);
        Title title;
        if (docTitle != null && docTitle.startsWith("Failed:")) {
            String rest = docTitle.substring("Failed:".length());
            title = Title.from(Line.from(
                    Span.styled(" Failed:", Style.EMPTY.fg(Color.LIGHT_RED).bold()),
                    Span.raw(rest + " ")));
        } else {
            title = Title.from(" " + docTitle + " ");
        }
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(title)
                .titleBottom(Title.from(Line.from(
                        Span.styled(" ↑↓", MonitorContext.HINT_KEY_STYLE), Span.raw(" scroll │"),
                        Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE), Span.raw(" back "))))
                .build();
        if (docLines != null) {
            frame.renderWidget(block, popup);
            Rect inner = block.inner(popup);
            int visibleLines = inner.height();
            int totalLines = docLines.size();
            int clampedScroll = Math.min(docScroll, Math.max(0, totalLines - visibleLines));
            int end = Math.min(clampedScroll + visibleLines, totalLines);
            List<Line> visible = new ArrayList<>(docLines.subList(clampedScroll, end));
            while (visible.size() < visibleLines) {
                visible.add(Line.from(""));
            }
            frame.renderWidget(
                    Paragraph.builder().text(Text.from(visible.toArray(Line[]::new)))
                            .overflow(Overflow.CLIP).build(),
                    inner);
        } else {
            MarkdownView view = MarkdownView.builder()
                    .source(docContent)
                    .scroll(docScroll)
                    .block(block)
                    .build();
            frame.renderWidget(view, popup);
        }
    }

    private void renderDocPicker(Frame frame, Rect area) {
        if (docPickerIntegrations == null || docPickerIntegrations.isEmpty()) {
            return;
        }
        int popupW = Math.min(60, area.width() - 4);
        int popupH = Math.min(docPickerIntegrations.size() + 2, Math.min(15, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);
        List<ListItem> items = new ArrayList<>();
        for (IntegrationInfo info : docPickerIntegrations) {
            String label = "  " + (info.name != null ? info.name : info.pid);
            items.add(ListItem.from(label));
        }
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" Show Integration Doc ")
                        .titleBottom(Title.from(Line.from(
                                Span.styled(" Enter", MonitorContext.HINT_KEY_STYLE), Span.raw(" view │"),
                                Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE), Span.raw(" back "))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, docPickerState);
    }

    private void openDocPicker() {
        showActionsMenu = false;
        List<IntegrationInfo> withDocs = integrations.get().stream()
                .filter(i -> !i.vanishing && i.readmeFiles != null && !i.readmeFiles.isEmpty())
                .collect(Collectors.toList());
        if (withDocs.isEmpty()) {
            launchNotification = "No integrations with documentation found";
            launchNotificationError = true;
            launchNotificationExpiry = System.currentTimeMillis() + 5000;
            return;
        }
        if (withDocs.size() == 1) {
            loadDocFromIntegration(withDocs.get(0));
            return;
        }
        docPickerIntegrations = withDocs;
        showDocPicker = true;
        docPickerState.select(0);
    }

    void openDoc(IntegrationInfo info) {
        showActionsMenu = false;
        loadDocFromIntegration(info);
    }

    private void loadDocFromSelectedIntegration() {
        Integer sel = docPickerState.selected();
        if (sel == null || docPickerIntegrations == null || sel >= docPickerIntegrations.size()) {
            return;
        }
        IntegrationInfo info = docPickerIntegrations.get(sel);
        loadDocFromIntegration(info);
    }

    private void loadDocFromIntegration(IntegrationInfo info) {
        if (ctx == null) {
            return;
        }
        docLines = null;
        showDocPicker = false;
        try {
            Path outputFile = ctx.getOutputFile(info.pid);
            Files.deleteIfExists(outputFile);
            JsonObject action = new JsonObject();
            action.put("action", "readme");
            PathUtils.writeTextSafely(action.toJson(), ctx.getActionFile(info.pid));
            JsonObject response = MonitorContext.pollJsonResponse(outputFile, 5000);
            if (response != null && response.getString("content") != null) {
                String raw = response.getString("content");
                String file = response.getStringOrDefault("file", "README");
                docContent = file.endsWith(".adoc") ? DocHelper.asciidocToMarkdown(raw) : raw;
                docTitle = (info.name != null ? info.name : info.pid) + " - " + Path.of(file).getFileName();
                docScroll = 0;
                showDocViewer = true;
                docViewerFromExampleBrowser = false;
            } else {
                launchNotification = "Could not load documentation";
                launchNotificationError = true;
                launchNotificationExpiry = System.currentTimeMillis() + 5000;
            }
        } catch (Exception e) {
            launchNotification = "Error loading documentation: " + e.getMessage();
            launchNotificationError = true;
            launchNotificationExpiry = System.currentTimeMillis() + 5000;
        }
    }

    private void loadDocFromExample() {
        Integer sel = exampleBrowserState.selected();
        if (sel == null || isSeparatorIndex(sel)) {
            return;
        }
        JsonObject example = getExampleAtListIndex(sel);
        if (example == null) {
            return;
        }
        String name = example.getStringOrDefault("name", "");
        boolean bundled = ExampleHelper.isBundled(example);
        String content = null;
        boolean isAdoc = false;
        if (bundled) {
            content = DocHelper.loadResourceContent("examples/" + name + "/README.md");
            if (content == null) {
                content = DocHelper.loadResourceContent("examples/" + name + "/README.adoc");
                isAdoc = content != null;
            }
        } else {
            String base = "https://raw.githubusercontent.com/apache/camel-jbang-examples/main/" + name + "/";
            content = DocHelper.downloadContent(base + "README.md");
            if (content == null) {
                content = DocHelper.downloadContent(base + "README.adoc");
                isAdoc = content != null;
            }
        }
        if (content != null && !content.isEmpty()) {
            docContent = isAdoc ? DocHelper.asciidocToMarkdown(content) : content;
            docLines = null;
            docTitle = name;
            docScroll = 0;
            showExampleBrowser = false;
            showDocViewer = true;
            docViewerFromExampleBrowser = true;
        } else {
            setNotification("No documentation available for: " + name, true);
        }
    }

    private void setNotification(String msg, boolean error) {
        launchNotification = msg;
        launchNotificationError = error;
        launchNotificationExpiry = System.currentTimeMillis() + 10000;
    }

    private void checkStopAllNotification() {
        String msg = stopAllPopup.consumeNotification();
        if (msg != null) {
            setNotification(msg, false);
        }
    }

    private void openSendMessage() {
        if (ctx == null) {
            return;
        }
        String pid = ctx.selectedPid;
        if (pid == null) {
            List<IntegrationInfo> ints = integrations.get();
            List<IntegrationInfo> alive = ints.stream().filter(i -> !i.vanishing && i.pid != null).toList();
            if (alive.size() == 1) {
                pid = alive.get(0).pid;
            }
        }
        if (pid == null) {
            setNotification("Select an integration first", true);
            return;
        }
        IntegrationInfo info = findIntegration(pid);
        if (info == null || info.routes.isEmpty()) {
            setNotification("No routes available", true);
            return;
        }
        sendMessagePopup.open(ctx, pid, info.name, info.routes, preSelectedRouteId);
        preSelectedRouteId = null;
    }

    private IntegrationInfo findIntegration(String pid) {
        for (IntegrationInfo i : integrations.get()) {
            if (pid.equals(i.pid)) {
                return i;
            }
        }
        return null;
    }

    private void openTapeInstructions() {
        docLines = null;
        docContent = "# Tape Recording Guide\n\n"
                     + "Record your live TUI session as a `.tape` file that captures keystrokes\n"
                     + "with timing. The tape can be replayed inside the TUI to produce\n"
                     + "an Asciinema `.cast` recording.\n\n"
                     + "## Starting and Stopping\n\n"
                     + "- Press **Ctrl+R** to start/stop recording at any time\n"
                     + "- Or use the **F2** actions menu → Start/Stop Tape Recording\n\n"
                     + "When recording stops, the tape is saved to the current directory as\n"
                     + "`camel-tui-tape-<timestamp>.tape`.\n\n"
                     + "## Replaying a Tape\n\n"
                     + "Replay the tape inside the TUI with the `--record` option:\n\n"
                     + "    camel tui monitor --record=camel-tui-tape-20260525-153000.tape\n\n"
                     + "This replays the keystrokes inside the live TUI and produces\n"
                     + "an Asciinema `.cast` file.\n\n"
                     + "## Converting to Animated GIF\n\n"
                     + "Use `agg` to convert the `.cast` file to an animated GIF:\n\n"
                     + "    brew install asciinema/tap/agg\n"
                     + "    agg recording.cast demo.gif\n\n"
                     + "Or upload to [asciinema.org](https://asciinema.org) for a shareable link.\n\n"
                     + "## Tips\n\n"
                     + "- **Ctrl+R** is not captured in the tape, keeping the script clean\n"
                     + "- Natural pauses between keystrokes are preserved as `Sleep` commands\n"
                     + "- Keep recordings focused — one workflow at a time works best\n";
        docTitle = "Tape Recording Guide";
        docScroll = 0;
        showDocViewer = true;
        docViewerFromExampleBrowser = false;
    }

    private void openSetupAI() {
        docLines = null;
        String url = "http://localhost:" + mcpPort + "/mcp";
        String client = mcpConnectedClient != null ? mcpConnectedClient.get() : null;
        String status = client != null
                ? "**Connected:** " + client + "\n\nYour AI agent is already connected and ready to use."
                : "**Status:** Waiting for connection";
        docContent = "# Setup AI Agent\n\n"
                     + status + "\n\n"
                     + "## Connect Claude Code\n\n"
                     + "Run this command in your terminal:\n\n"
                     + "    claude mcp add --transport http camel-tui " + url + "\n\n"
                     + "Then start a new Claude Code session. The TUI footer will turn green\n"
                     + "when the AI agent connects.\n\n"
                     + "## Alternative: .mcp.json\n\n"
                     + "A `.mcp.json` file is auto-generated in the current directory while the\n"
                     + "TUI runs with `--mcp`. AI agents that scan for `.mcp.json` will discover\n"
                     + "the MCP server automatically.\n\n"
                     + "## What the AI Can Do\n\n"
                     + "Once connected, your AI agent can:\n\n"
                     + "- See the TUI screen and follow your key presses\n"
                     + "- Navigate tabs and select integrations\n"
                     + "- Read route diagrams and health status\n"
                     + "- Send test messages to endpoints\n"
                     + "- Record VHS tapes for documentation\n\n"
                     + "Try asking: *\"What's on my Camel TUI screen right now?\"*\n";
        docTitle = "Setup AI";
        docScroll = 0;
        showDocViewer = true;
        docViewerFromExampleBrowser = false;
    }

    private void openMcpInfo() {
        docLines = null;
        String url = "http://localhost:" + mcpPort + "/mcp";
        String client = mcpConnectedClient != null ? mcpConnectedClient.get() : null;
        String status = client != null
                ? "**Connected:** " + client
                : "**Status:** Waiting for connection";
        docContent = "# MCP Server\n\n"
                     + status + "\n\n"
                     + "The TUI has an embedded MCP (Model Context Protocol) server running at:\n\n"
                     + "    " + url + "\n\n"
                     + "This allows AI coding agents to observe your TUI session — see the screen,\n"
                     + "follow your key presses, and understand what you're doing.\n\n"
                     + "## Available Tools\n\n"
                     + "| Tool | Description |\n"
                     + "|------|-------------|\n"
                     + "| `tui_get_screen` | Returns the current screen content as text |\n"
                     + "| `tui_get_events` | Returns recent key presses and navigation events |\n"
                     + "| `tui_get_state` | Returns active tab, selected integration, etc. |\n"
                     + "| `tui_get_options` | Returns available tabs and integrations |\n"
                     + "| `tui_show_caption` | Shows a message on the TUI screen |\n"
                     + "| `tui_navigate` | Switch tabs and select integrations |\n"
                     + "| `tui_send_keys` | Send key presses to control the TUI |\n"
                     + "| `tui_wait_for_idle` | Waits for the screen to settle after an action |\n"
                     + "| `tui_control` | Stop/start routes, restart, stop, or kill integration |\n"
                     + "| `tui_tape_start` | Start recording interactions as a VHS .tape file |\n"
                     + "| `tui_tape_stop` | Stop recording and return the tape content |\n\n"
                     + "## Setup for Claude Code\n\n"
                     + "Run this command to connect Claude Code to the TUI:\n\n"
                     + "    claude mcp add --transport http camel-tui " + url + "\n\n"
                     + "Or add to your project's `.mcp.json`:\n\n"
                     + "    {\n"
                     + "      \"mcpServers\": {\n"
                     + "        \"camel-tui\": {\n"
                     + "          \"type\": \"http\",\n"
                     + "          \"url\": \"" + url + "\"\n"
                     + "        }\n"
                     + "      }\n"
                     + "    }\n\n"
                     + "A `.mcp.json` file is auto-generated in the current directory while the TUI\n"
                     + "is running with `--mcp` enabled. It is removed when the TUI exits.\n\n"
                     + "## Usage Examples\n\n"
                     + "Once connected, ask your AI agent:\n\n"
                     + "- \"What's on my Camel TUI screen right now?\"\n"
                     + "- \"What tab am I on and what integration is selected?\"\n"
                     + "- \"What keys did I press in the last minute?\"\n"
                     + "- \"What color is the throughput chart?\"\n"
                     + "- \"Show me a message on the TUI screen\"\n"
                     + "- \"Switch to the Health tab\"\n"
                     + "- \"Select the myApp integration\"\n";
        docTitle = "MCP Info";
        docScroll = 0;
        showDocViewer = true;
        docViewerFromExampleBrowser = false;
    }

    private void openMcpLog() {
        mcpLogPopup.open();
    }

    // ---- Folder Input ----

    private void openFolderInput() {
        showActionsMenu = false;
        showFolderInput = true;
        folderInputState = new TextInputState("");
        folderHistoryIndex = -1;
    }

    private void confirmFolderInput() {
        String folder = folderInputState.text().trim();
        if (folder.isEmpty()) {
            return;
        }
        // resolve ~ to home directory
        if (folder.startsWith("~")) {
            folder = System.getProperty("user.home") + folder.substring(1);
        }
        Path dirPath = Path.of(folder);
        if (!Files.isDirectory(dirPath)) {
            setNotification("Directory does not exist: " + folder, true);
            return;
        }
        folderHistory.remove(folder);
        folderHistory.add(0, folder);
        if (folderHistory.size() > 20) {
            folderHistory.remove(folderHistory.size() - 1);
        }
        selectedFolder = folder;
        showFolderInput = false;
        String displayName = dirPath.getFileName().toString();
        runOptionsForm.open(displayName, displayName, false, true);
    }

    private void navigateFolderHistory(int direction) {
        if (folderHistory.isEmpty()) {
            return;
        }
        if (direction < 0) {
            if (folderHistoryIndex < folderHistory.size() - 1) {
                folderHistoryIndex++;
            }
        } else {
            if (folderHistoryIndex > 0) {
                folderHistoryIndex--;
            } else if (folderHistoryIndex == 0) {
                folderHistoryIndex = -1;
                folderInputState = new TextInputState("");
                return;
            }
        }
        if (folderHistoryIndex >= 0 && folderHistoryIndex < folderHistory.size()) {
            folderInputState = new TextInputState(folderHistory.get(folderHistoryIndex));
        }
    }

    private void handleFolderTextInput(KeyEvent ke) {
        if (folderInputState == null) {
            return;
        }
        if (ke.isDeleteBackward()) {
            folderInputState.deleteBackward();
        } else if (ke.isDeleteForward()) {
            folderInputState.deleteForward();
        } else if (ke.isLeft()) {
            folderInputState.moveCursorLeft();
        } else if (ke.isRight()) {
            folderInputState.moveCursorRight();
        } else if (ke.isHome()) {
            folderInputState.moveCursorToStart();
        } else if (ke.isEnd()) {
            folderInputState.moveCursorToEnd();
        } else if (ke.code() == KeyCode.CHAR) {
            folderInputState.insert(ke.character());
        }
    }

    private void renderFolderInput(Frame frame, Rect area) {
        int popupW = Math.min(70, area.width() - 4);
        int popupH = 4;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        List<Span> bottomSpans = new ArrayList<>();
        if (!folderHistory.isEmpty()) {
            bottomSpans.add(Span.styled(" ↑↓", MonitorContext.HINT_KEY_STYLE));
            bottomSpans.add(Span.raw(" history │"));
        }
        bottomSpans.add(Span.styled(" Enter", MonitorContext.HINT_KEY_STYLE));
        bottomSpans.add(Span.raw(" run... │"));
        bottomSpans.add(Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE));
        bottomSpans.add(Span.raw(" back "));

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Run from folder ")
                .titleBottom(Title.from(Line.from(bottomSpans)))
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);

        int labelW = 9;
        int fieldW = inner.width() - labelW;
        int row = inner.top();
        int ix = inner.left();

        Rect labelArea = new Rect(ix, row, labelW, 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled("Folder:", Style.EMPTY.bold()))), labelArea);
        Rect inputArea = new Rect(ix + labelW, row, fieldW, 1);
        TextInput textInput = TextInput.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .placeholder("/path/to/folder")
                .build();
        frame.renderStatefulWidget(textInput, inputArea, folderInputState);
    }

    private void launchFolder() {
        if (selectedFolder == null) {
            return;
        }
        String folder = selectedFolder;
        String displayName = runOptionsForm.name();
        if (displayName.isEmpty()) {
            displayName = Path.of(folder).getFileName().toString();
        }
        List<String> extraArgs = runOptionsForm.buildArgs();
        boolean jaegerExport = runOptionsForm.isJaegerExport();
        runOptionsForm.close();
        selectedFolder = null;

        if (jaegerExport && !isJaegerRunning()) {
            if (!isContainerRuntimeAvailable()) {
                setNotification("Docker/Podman required for Jaeger. Run Doctor for details", true);
                return;
            }
            startMissingInfraAndDeferFolder(folder, displayName, extraArgs);
            return;
        }

        doLaunchFolder(folder, displayName, extraArgs);
    }

    private void doLaunchFolder(String folder, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add("--source-dir=" + folder);
            cmd.add("--logging-color=true");
            cmd.addAll(extraArgs);
            Path outputFile = Files.createTempFile("camel-folder-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            pendingLaunches.add(new PendingLaunch(displayName, process, outputFile, System.currentTimeMillis()));
            pendingAutoSelect = displayName;
            burstCallback.run();
            setNotification("Starting: " + displayName, false);
        } catch (Exception e) {
            setNotification("Failed to start: " + folder + " - " + e.getMessage(), true);
        }
    }

    // ---- Name Input ----

    private void openNameInput() {
        Integer sel = exampleBrowserState.selected();
        if (sel == null || isSeparatorIndex(sel)) {
            return;
        }
        JsonObject example = getExampleAtListIndex(sel);
        if (example == null) {
            return;
        }
        selectedExample = example;
        String baseName = example.getStringOrDefault("name", "");
        String autoName = generateUniqueName(baseName);
        showExampleBrowser = false;
        runOptionsForm.open(autoName, baseName, ExampleHelper.isBundled(example));
    }

    private String generateUniqueName(String baseName) {
        Set<String> names = runningNames.get();
        if (!names.contains(baseName)) {
            return baseName;
        }
        for (int i = 2;; i++) {
            String candidate = baseName + i;
            if (!names.contains(candidate)) {
                return candidate;
            }
        }
    }

    private void launchWithName() {
        if (selectedExample == null) {
            return;
        }
        String exampleName = selectedExample.getStringOrDefault("name", "");
        String displayName = runOptionsForm.name();
        if (displayName.isEmpty()) {
            displayName = exampleName;
        }
        List<String> extraArgs = runOptionsForm.buildArgs();
        boolean stub = runOptionsForm.isStubMode();
        boolean jaegerExport = runOptionsForm.isJaegerExport();
        runOptionsForm.close();

        if (!stub) {
            List<String> missing = findMissingInfraServices(selectedExample);
            if (jaegerExport && !isJaegerRunning()) {
                missing = new ArrayList<>(missing);
                missing.add("jaeger");
            }
            if (!missing.isEmpty()) {
                if (!isContainerRuntimeAvailable()) {
                    setNotification("Docker/Podman required for infra services. Run Doctor for details", true);
                    return;
                }
                startMissingInfraAndDeferExample(missing, exampleName, displayName, extraArgs);
                return;
            }
        }

        doLaunchExample(exampleName, displayName, extraArgs);
    }

    // ---- Example Browser Navigation ----

    private void openExampleBrowser() {
        showActionsMenu = false;
        if (exampleCatalog == null) {
            exampleCatalog = loadAndSortExamples();
        }
        if (exampleCatalog.isEmpty()) {
            launchNotification = "No examples found";
            launchNotificationError = true;
            launchNotificationExpiry = System.currentTimeMillis() + 5000;
            return;
        }
        showExampleBrowser = true;
        exampleBrowserState.select(1);
    }

    private List<JsonObject> loadAndSortExamples() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        catalog.sort((a, b) -> {
            int la = levelOrder(a.getStringOrDefault("level", "beginner"));
            int lb = levelOrder(b.getStringOrDefault("level", "beginner"));
            if (la != lb) {
                return Integer.compare(la, lb);
            }
            return a.getStringOrDefault("name", "").compareTo(b.getStringOrDefault("name", ""));
        });
        return catalog;
    }

    private static int levelOrder(String level) {
        return switch (level) {
            case "beginner" -> 0;
            case "intermediate" -> 1;
            case "advanced" -> 2;
            default -> 3;
        };
    }

    private void navigateExampleBrowser(int direction) {
        if (exampleCatalog == null || exampleCatalog.isEmpty()) {
            return;
        }
        int totalItems = countExampleListItems();
        Integer current = exampleBrowserState.selected();
        if (current == null) {
            current = 0;
        }
        int next = current + direction;
        if (next < 0) {
            next = 0;
        }
        if (next >= totalItems) {
            next = totalItems - 1;
        }
        while (isSeparatorIndex(next) && next > 0 && next < totalItems - 1) {
            next += direction;
        }
        if (next < 0) {
            next = 0;
        }
        if (next >= totalItems) {
            next = totalItems - 1;
        }
        if (isSeparatorIndex(next)) {
            return;
        }
        exampleBrowserState.select(next);
    }

    private int countExampleListItems() {
        if (exampleCatalog == null) {
            return 0;
        }
        int count = 0;
        String currentLevel = null;
        for (JsonObject ex : exampleCatalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                count++;
            }
            count++;
        }
        return count + 2;
    }

    private boolean isSeparatorIndex(int index) {
        if (exampleCatalog == null) {
            return false;
        }
        int pos = 0;
        String currentLevel = null;
        for (JsonObject ex : exampleCatalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                if (pos == index) {
                    return true;
                }
                pos++;
            }
            if (pos == index) {
                return false;
            }
            pos++;
        }
        return true;
    }

    private JsonObject getExampleAtListIndex(int index) {
        if (exampleCatalog == null) {
            return null;
        }
        int pos = 0;
        String currentLevel = null;
        for (JsonObject ex : exampleCatalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                pos++;
            }
            if (pos == index) {
                return ex;
            }
            pos++;
        }
        return null;
    }

    // ---- Infra Browser ----

    private void openInfraBrowser() {
        showActionsMenu = false;
        if (!isContainerRuntimeAvailable()) {
            setNotification("Docker or Podman is not running (use F2 → Run Doctor to check)", true);
            return;
        }
        if (infraCatalog == null) {
            infraCatalog = loadInfraCatalog();
        }
        if (infraCatalog.isEmpty()) {
            setNotification("No infra services found", true);
            return;
        }
        showInfraBrowser = true;
        infraBrowserState.select(0);
        // skip to first non-running service
        if (!infraCatalog.isEmpty() && infraCatalog.get(0).running) {
            navigateInfraBrowser(1);
        }
    }

    private List<InfraServiceEntry> loadInfraCatalog() {
        try {
            CamelCatalog catalog = new DefaultCamelCatalog();
            try (InputStream is = catalog.loadResource("test-infra", "metadata.json")) {
                if (is == null) {
                    return List.of();
                }
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonArray arr = (JsonArray) Jsoner.deserialize(json);
                Map<String, InfraServiceEntry> byAlias = new LinkedHashMap<>();
                for (Object obj : arr) {
                    JsonObject svc = (JsonObject) obj;
                    String desc = (String) svc.getOrDefault("description", "");
                    JsonArray aliases = (JsonArray) svc.get("alias");
                    JsonArray impls = (JsonArray) svc.get("aliasImplementation");
                    if (aliases != null) {
                        for (Object a : aliases) {
                            String alias = a.toString();
                            InfraServiceEntry entry = byAlias.get(alias);
                            if (entry == null) {
                                entry = new InfraServiceEntry(alias, desc, new ArrayList<>(), false);
                                byAlias.put(alias, entry);
                            }
                            if (impls != null) {
                                for (Object impl : impls) {
                                    String implStr = impl.toString();
                                    if (!entry.implementations.contains(implStr)) {
                                        entry.implementations.add(implStr);
                                    }
                                }
                            }
                        }
                    }
                }
                // Mark running services
                Set<String> runningAliases = infraServices.get().stream()
                        .filter(i -> i.alive)
                        .map(i -> i.alias)
                        .collect(Collectors.toSet());
                List<InfraServiceEntry> result = new ArrayList<>();
                for (InfraServiceEntry entry : byAlias.values()) {
                    boolean running = runningAliases.contains(entry.alias);
                    result.add(new InfraServiceEntry(entry.alias, entry.description, entry.implementations, running));
                }
                result.sort((a, b) -> a.alias.compareToIgnoreCase(b.alias));
                return result;
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    private void refreshInfraRunningState() {
        if (infraCatalog == null) {
            return;
        }
        Set<String> runningAliases = infraServices.get().stream()
                .filter(i -> i.alive)
                .map(i -> i.alias)
                .collect(Collectors.toSet());
        List<InfraServiceEntry> refreshed = new ArrayList<>();
        for (InfraServiceEntry entry : infraCatalog) {
            boolean running = runningAliases.contains(entry.alias);
            refreshed.add(new InfraServiceEntry(entry.alias, entry.description, entry.implementations, running));
        }
        infraCatalog = refreshed;
    }

    private void navigateInfraBrowser(int direction) {
        if (infraCatalog == null || infraCatalog.isEmpty()) {
            return;
        }
        int total = infraCatalog.size();
        Integer current = infraBrowserState.selected();
        int next = (current != null ? current : 0) + direction;
        next = Math.max(0, Math.min(next, total - 1));
        // skip running services
        while (next >= 0 && next < total && infraCatalog.get(next).running) {
            next += direction;
        }
        next = Math.max(0, Math.min(next, total - 1));
        if (!infraCatalog.get(next).running) {
            infraBrowserState.select(next);
        }
    }

    private void selectInfraService() {
        Integer sel = infraBrowserState.selected();
        if (sel == null || sel >= infraCatalog.size()) {
            return;
        }
        InfraServiceEntry entry = infraCatalog.get(sel);
        if (entry.running) {
            return;
        }
        selectedInfraService = entry;
        infraImplIndex = 0;
        infraPortState = new TextInputState("");
        showInfraBrowser = false;
        showInfraPortDialog = true;
    }

    private void jumpToInfraService(char ch) {
        if (infraCatalog == null) {
            return;
        }
        char lower = Character.toLowerCase(ch);
        for (int i = 0; i < infraCatalog.size(); i++) {
            if (infraCatalog.get(i).alias.toLowerCase().startsWith(String.valueOf(lower))) {
                infraBrowserState.select(i);
                infraBrowserState.setOffset(Math.max(0, i - 2));
                return;
            }
        }
    }

    private void renderInfraBrowser(Frame frame, Rect area) {
        if (infraCatalog == null || infraCatalog.isEmpty()) {
            return;
        }
        refreshInfraRunningState();
        int popupW = Math.min(100, area.width() - 4);
        int popupH = Math.min(infraCatalog.size() + 2, Math.min(22, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        int nameCol = 22;
        List<ListItem> items = new ArrayList<>();
        for (InfraServiceEntry entry : infraCatalog) {
            String padded = String.format("%-" + nameCol + "s", TuiHelper.truncate(entry.alias, nameCol));
            String prefix = "  🔧 " + padded + " ";
            if (entry.running) {
                items.add(ListItem.from(prefix + "(running)").style(Style.EMPTY.dim()));
            } else {
                String implStr = entry.implementations.isEmpty() ? "" : String.join(", ", entry.implementations);
                String desc = entry.description;
                if (!implStr.isEmpty()) {
                    desc = desc + " [" + implStr + "]";
                }
                int descW = Math.max(10, popupW - prefix.length() - 2);
                if (desc.length() <= descW) {
                    items.add(ListItem.from(prefix + desc));
                } else {
                    String indent = " ".repeat(prefix.length());
                    List<Line> lines = new ArrayList<>();
                    List<String> wrapped = wrapWords(desc, descW);
                    lines.add(Line.from(prefix + wrapped.get(0)));
                    for (int w = 1; w < wrapped.size(); w++) {
                        lines.add(Line.from(indent + wrapped.get(w)));
                    }
                    items.add(ListItem.from(Text.from(lines.toArray(Line[]::new))));
                }
            }
        }

        long available = infraCatalog.stream().filter(e -> !e.running).count();
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" Run Dev/Infra Service (" + available + "/" + infraCatalog.size() + ") ")
                        .titleBottom(Title.from(Line.from(
                                Span.styled(" Enter", MonitorContext.HINT_KEY_STYLE), Span.raw(" select │"),
                                Span.styled(" ↑↓", MonitorContext.HINT_KEY_STYLE), Span.raw(" navigate │"),
                                Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE), Span.raw(" back "))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, infraBrowserState);
    }

    // ---- Infra Port Dialog ----

    private void renderInfraPortDialog(Frame frame, Rect area) {
        if (selectedInfraService == null) {
            return;
        }
        boolean hasMultiImpl = selectedInfraService.implementations.size() > 1;
        int popupW = 42;
        int popupH = hasMultiImpl ? 8 : 6;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Run " + selectedInfraService.alias + " ")
                .titleBottom(Title.from(Line.from(
                        Span.styled(" Enter", MonitorContext.HINT_KEY_STYLE), Span.raw(" run │"),
                        Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE), Span.raw(" back "))))
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);

        int labelW = 8;
        int fieldW = inner.width() - labelW;
        int row = inner.top();
        int ix = inner.left();

        // Implementation selector (if multiple)
        if (hasMultiImpl) {
            row++;
            Rect labelArea = new Rect(ix, row, labelW, 1);
            frame.renderWidget(Paragraph.from(Line.from(Span.styled("Impl:", Style.EMPTY.bold()))), labelArea);
            String impl = selectedInfraService.implementations.get(infraImplIndex);
            Rect implArea = new Rect(ix + labelW, row, fieldW, 1);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled("◀ ", MonitorContext.HINT_KEY_STYLE),
                    Span.raw(impl),
                    Span.styled(" ▶", MonitorContext.HINT_KEY_STYLE))), implArea);
            row++;
        }

        // Port input
        row++;
        Rect labelArea = new Rect(ix, row, labelW, 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled("Port:", Style.EMPTY.bold()))), labelArea);
        Rect portArea = new Rect(ix + labelW, row, fieldW, 1);
        TextInput textInput = TextInput.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .placeholder("default")
                .build();
        frame.renderStatefulWidget(textInput, portArea, infraPortState);
    }

    private void handlePortInput(KeyEvent ke) {
        if (infraPortState == null) {
            return;
        }
        if (ke.isDeleteBackward()) {
            infraPortState.deleteBackward();
        } else if (ke.isDeleteForward()) {
            infraPortState.deleteForward();
        } else if (ke.isLeft()) {
            infraPortState.moveCursorLeft();
        } else if (ke.isRight()) {
            infraPortState.moveCursorRight();
        } else if (ke.isHome()) {
            infraPortState.moveCursorToStart();
        } else if (ke.isEnd()) {
            infraPortState.moveCursorToEnd();
        } else if (ke.code() == KeyCode.CHAR && Character.isDigit(ke.character())) {
            infraPortState.insert(ke.character());
        }
    }

    private void launchInfraService() {
        if (selectedInfraService == null) {
            return;
        }
        String alias = selectedInfraService.alias;
        String impl = null;
        if (!selectedInfraService.implementations.isEmpty()) {
            impl = selectedInfraService.implementations.get(infraImplIndex);
        }
        String portStr = infraPortState != null ? infraPortState.text().trim() : "";
        showInfraPortDialog = false;
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("infra");
            cmd.add("run");
            cmd.add(alias);
            if (impl != null) {
                cmd.add(impl);
            }
            cmd.add("--background");
            if (!portStr.isEmpty()) {
                cmd.add("--port=" + portStr);
            }
            Path outputFile = Files.createTempFile("camel-infra-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            pendingLaunches.add(new PendingLaunch(alias, process, outputFile, System.currentTimeMillis()));
            burstCallback.run();
            setNotification("Starting infra: " + alias, false);
            // force reload next time browser opens
            infraCatalog = null;
        } catch (Exception e) {
            setNotification("Failed to start infra: " + alias + " - " + e.getMessage(), true);
        }
    }

    // ---- Process Launch & Monitoring ----

    private void launchSelectedExample() {
        Integer sel = exampleBrowserState.selected();
        if (sel == null || isSeparatorIndex(sel)) {
            return;
        }
        JsonObject example = getExampleAtListIndex(sel);
        if (example == null) {
            return;
        }
        String exampleName = example.getStringOrDefault("name", "");
        showExampleBrowser = false;

        List<String> missing = findMissingInfraServices(example);
        if (!missing.isEmpty()) {
            if (!isContainerRuntimeAvailable()) {
                setNotification("Docker/Podman required for infra services. Run Doctor for details", true);
                return;
            }
            startMissingInfraAndDeferExample(missing, exampleName, exampleName, List.of());
            return;
        }

        doLaunchExample(exampleName, exampleName, List.of());
    }

    private void doLaunchExample(String exampleName, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add("--example=" + exampleName);
            cmd.add("--logging-color=true");
            cmd.addAll(extraArgs);
            Path outputFile = Files.createTempFile("camel-example-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            pendingLaunches.add(new PendingLaunch(displayName, process, outputFile, System.currentTimeMillis()));
            pendingAutoSelect = displayName;
            burstCallback.run();
            setNotification("Starting: " + displayName, false);
        } catch (Exception e) {
            setNotification("Failed to start: " + exampleName + " - " + e.getMessage(), true);
        }
    }

    private List<String> findMissingInfraServices(JsonObject example) {
        List<String> required = ExampleHelper.getInfraServices(example);
        if (required.isEmpty()) {
            return List.of();
        }
        Set<String> runningAliases = infraServices.get().stream()
                .filter(i -> i.alive)
                .map(i -> i.alias)
                .collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String alias : required) {
            if (!runningAliases.contains(alias)) {
                missing.add(alias);
            }
        }
        return missing;
    }

    private boolean isJaegerRunning() {
        return infraServices.get().stream()
                .anyMatch(i -> i.alive && "jaeger".equals(i.alias));
    }

    private void startMissingInfraAndDeferExample(
            List<String> missingInfra, String exampleName, String displayName, List<String> extraArgs) {
        startMissingInfraAndDefer(
                missingInfra, displayName, () -> doLaunchExample(exampleName, displayName, extraArgs));
    }

    private void startMissingInfraAndDeferFolder(String folder, String displayName, List<String> extraArgs) {
        startMissingInfraAndDefer(
                List.of("jaeger"), displayName, () -> doLaunchFolder(folder, displayName, extraArgs));
    }

    private void startMissingInfraAndDefer(List<String> missingInfra, String displayName, Runnable launchAction) {
        for (String alias : missingInfra) {
            try {
                List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
                cmd.add("infra");
                cmd.add("run");
                cmd.add(alias);
                cmd.add("--background");
                Path outputFile = Files.createTempFile("camel-infra-", ".log");
                outputFile.toFile().deleteOnExit();
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                pb.redirectOutput(outputFile.toFile());
                Process process = pb.start();
                pendingLaunches.add(new PendingLaunch(alias, process, outputFile, System.currentTimeMillis()));
            } catch (Exception e) {
                setNotification("Failed to start infra: " + alias + " - " + e.getMessage(), true);
                return;
            }
        }
        deferredLaunch = new DeferredLaunch(displayName, missingInfra, System.currentTimeMillis(), launchAction);
        infraCatalog = null;
        String infraList = String.join(", ", missingInfra);
        setNotification("Starting infra: " + infraList + " → then: " + displayName, false);
    }

    private void checkDeferredLaunch(long now) {
        if (deferredLaunch != null) {
            Set<String> runningAliases = infraServices.get().stream()
                    .filter(i -> i.alive)
                    .map(i -> i.alias)
                    .collect(Collectors.toSet());
            if (runningAliases.containsAll(deferredLaunch.requiredInfra)) {
                DeferredLaunch dl = deferredLaunch;
                deferredLaunch = null;
                dl.launchAction.run();
            } else if (now - deferredLaunch.startTime > 120_000) {
                deferredLaunch = null;
                setNotification("Timeout waiting for infra services to start", true);
            }
        }
    }

    private void monitorPendingLaunches(long now) {
        Iterator<PendingLaunch> it = pendingLaunches.iterator();
        while (it.hasNext()) {
            PendingLaunch pl = it.next();
            if (!pl.process().isAlive()) {
                int exitCode = pl.process().exitValue();
                if (exitCode == 0) {
                    launchNotification = "Started: " + pl.name();
                    launchNotificationError = false;
                    launchNotificationExpiry = now + 5000;
                } else {
                    showFailureLog(pl.name(), pl.outputFile());
                }
                it.remove();
            } else if (now - pl.startTime() > 8000) {
                launchNotification = "Started: " + pl.name();
                launchNotificationError = false;
                launchNotificationExpiry = now + 5000;
                it.remove();
            }
        }
    }

    private void showFailureLog(String name, Path logFile) {
        List<String> logLines = readAllLines(logFile);
        if (logLines.isEmpty()) {
            setNotification("Failed: " + name + " (no output)", true);
            return;
        }
        docTitle = "Failed: " + name;
        docContent = null;
        docLines = logLines.stream()
                .map(line -> TuiHelper.ansiToLine(line.replace("\t", "        "), 0))
                .collect(Collectors.toList());
        docScroll = 0;
        showDocViewer = true;
        docViewerFromExampleBrowser = false;
    }

    private static List<String> readAllLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            return List.of();
        }
    }

    // ---- Utilities ----

    private static String readFirstLine(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    return TuiHelper.truncate(trimmed, 60);
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<String> wrapWords(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.isEmpty()) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxWidth) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static boolean isContainerRuntimeAvailable() {
        for (String cmd : new String[] { "docker", "podman" }) {
            try {
                Process p = new ProcessBuilder(cmd, "info")
                        .redirectErrorStream(true)
                        .start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                int exit = p.waitFor();
                if (exit == 0) {
                    return true;
                }
            } catch (Exception e) {
                // not found, try next
            }
        }
        return false;
    }

    record InfraServiceEntry(String alias, String description, List<String> implementations, boolean running) {
    }

    boolean executeActionByName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.replace("-", "_").toUpperCase();
        Action action;
        try {
            action = Action.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return false;
        }
        switch (action) {
            case RESET_STATS -> {
                if (resetStatsAction != null) {
                    resetStatsAction.run();
                }
            }
            case RESET_SCREEN -> {
                if (resetScreenAction != null) {
                    resetScreenAction.run();
                }
            }
            case SCREENSHOT -> screenshotAction.run();
            case SHOW_KEYSTROKES -> toggleKeystrokes.run();
            case TAPE_RECORDING -> toggleTapeRecording.run();
            case DOCTOR -> doctorPopup.open();
            case CAPTION -> captionOverlay.openInline();
            case SETUP_AI -> openSetupAI();
            case MCP_INFO -> openMcpInfo();
            case MCP_LOG -> openMcpLog();
            default -> {
                return false;
            }
        }
        return true;
    }

    private record PendingLaunch(String name, Process process, Path outputFile, long startTime) {
    }

    private record DeferredLaunch(
            String displayName, List<String> requiredInfra, long startTime,
            Runnable launchAction) {
    }
}
