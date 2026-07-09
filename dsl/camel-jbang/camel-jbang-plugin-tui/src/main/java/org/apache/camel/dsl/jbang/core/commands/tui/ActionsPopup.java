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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class ActionsPopup {

    enum Action {
        GOTO_TAB,
        SWITCH_INTEGRATION,
        SEND_MESSAGE,
        RUN_EXAMPLE,
        RUN_FOLDER,
        RUN_INFRA,
        BROWSE_FILES,
        DOCTOR,
        RESET_STATS,
        STOP_ALL,
        SHELL,
        SCREEN_SUBMENU,
        SETTINGS,
        MCP_SUBMENU,
        BACK,
        SCREENSHOT,
        TOGGLE_THEME,
        THEMES_SUBMENU,
        RESET_SCREEN,
        TAPE_RECORDING,
        TAPE_INSTRUCTIONS,
        CAPTION,
        SHOW_KEYSTROKES,
        CAMEL_ANIMATION,
        SETUP_AI,
        MCP_INFO,
        MCP_LOG,
        AI_LOG,
        AI_PROMPT
    }

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
    private Runnable themeRefreshAction;
    private Runnable openShellAction;
    private Runnable openAiPromptAction;
    private Runnable browseFilesAction;
    private Runnable camelAnimationAction;
    private Runnable switchIntegrationAction;
    private final Supplier<Boolean> tapeRecordingActive;
    private MonitorContext ctx;
    private boolean mcpEnabled;
    private int mcpPort;
    private Supplier<String> mcpConnectedClient;
    private Supplier<List<TuiMcpServer.LogEntry>> mcpActivityLog;

    private boolean showActionsMenu;
    private final ListState actionsMenuState = new ListState();
    private String currentSubmenu;
    private int savedMainSelection;
    // Absolute bounds of the single-line list popups captured during render, used to hit-test clicks.
    private Rect actionsMenuRect;

    private final GotoTabPopup gotoTabPopup = new GotoTabPopup();
    private final ThemePopup themePopup = new ThemePopup();
    private final SettingsPopup settingsPopup = new SettingsPopup();
    private final DocViewerPopup docViewerPopup = new DocViewerPopup();

    private final ExampleBrowserPopup exampleBrowserPopup;
    private final RunOptionsForm runOptionsForm = new RunOptionsForm();
    private final InfraBrowserPopup infraBrowserPopup;
    private final FolderInputPopup folderInputPopup;

    private final McpLogPopup mcpLogPopup = new McpLogPopup();
    private final AiLogPopup aiLogPopup = new AiLogPopup();

    private final DoctorPopup doctorPopup = new DoctorPopup();
    private final FolderBrowser sendFileBrowser = new FolderBrowser();
    private final SendMessagePopup sendMessagePopup = new SendMessagePopup();
    private final StopAllPopup stopAllPopup;
    private final CaptionOverlay captionOverlay;
    private ScheduledExecutorService scheduler;

    private final LaunchManager launchManager;
    private BiConsumer<String, Boolean> notificationCallback;
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
        docViewerPopup.setIntegrations(integrations);
        this.launchManager = new LaunchManager(infraServices);
        launchManager.setFailureLogCallback(this::showFailureLog);
        this.exampleBrowserPopup = new ExampleBrowserPopup(docViewerPopup, launchManager);
        exampleBrowserPopup.setBurstCallback(burstCallback);
        exampleBrowserPopup.setOnNameInputRequest(this::openExampleNameInput);
        this.infraBrowserPopup = new InfraBrowserPopup(infraServices, launchManager);
        infraBrowserPopup.setBurstCallback(burstCallback);
        this.folderInputPopup = new FolderInputPopup(launchManager);
        folderInputPopup.setBurstCallback(burstCallback);
        folderInputPopup.setOnFolderConfirmed(this::openFolderRunOptionsForm);
        folderInputPopup.setInfraCatalogClearer(infraBrowserPopup::clearCatalog);
        sendMessagePopup.setFileBrowser(sendFileBrowser);
    }

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
        docViewerPopup.setContext(ctx);
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

    void setThemeRefreshAction(Runnable themeRefreshAction) {
        this.themeRefreshAction = themeRefreshAction;
        themePopup.setThemeRefreshAction(themeRefreshAction);
    }

    void setOpenShellAction(Runnable openShellAction) {
        this.openShellAction = openShellAction;
    }

    void setOpenAiPromptAction(Runnable openAiPromptAction) {
        this.openAiPromptAction = openAiPromptAction;
    }

    void setBrowseFilesAction(Runnable browseFilesAction) {
        this.browseFilesAction = browseFilesAction;
    }

    void setCamelAnimationAction(Runnable camelAnimationAction) {
        this.camelAnimationAction = camelAnimationAction;
    }

    void setSwitchIntegrationAction(Runnable switchIntegrationAction) {
        this.switchIntegrationAction = switchIntegrationAction;
    }

    void setGotoTabSupport(List<TabRegistry.TabEntry> entries, Runnable callback) {
        gotoTabPopup.setTabEntries(entries, callback);
        settingsPopup.setTabEntries(entries);
    }

    void setClearScreenAction(Runnable clearScreen) {
        settingsPopup.setClearScreen(clearScreen);
    }

    void setMcpEnabled(
            boolean enabled, int port, Supplier<String> connectedClient,
            Supplier<List<TuiMcpServer.LogEntry>> activityLog, Supplier<Integer> toolCallCount) {
        this.mcpEnabled = enabled;
        this.mcpPort = port;
        this.mcpConnectedClient = connectedClient;
        this.mcpActivityLog = activityLog;
        mcpLogPopup.setActivityLog(activityLog);
        mcpLogPopup.setToolCallCount(toolCallCount);
        doctorPopup.setMcpState(enabled, port, connectedClient);
        docViewerPopup.setMcpState(port, connectedClient);
    }

    void setAiActivityLog(Supplier<List<AiPanel.LogEntry>> activityLog) {
        aiLogPopup.setActivityLog(activityLog);
    }

    private int visualActionCount() {
        return buildVisualActionList().size();
    }

    private boolean isDividerIndex(int visualIndex) {
        List<Action> flat = buildVisualActionList();
        return visualIndex >= 0 && visualIndex < flat.size() && flat.get(visualIndex) == null;
    }

    private Action resolveAction(int visualIndex) {
        List<Action> flat = buildVisualActionList();
        if (visualIndex >= 0 && visualIndex < flat.size()) {
            return flat.get(visualIndex);
        }
        return null;
    }

    private List<Action> buildVisualActionList() {
        if ("screen".equals(currentSubmenu)) {
            return buildScreenSubmenuList();
        }
        if ("mcp".equals(currentSubmenu)) {
            return buildMcpSubmenuList();
        }
        return buildMainMenuList();
    }

    private List<Action> buildMainMenuList() {
        List<Action> flat = new ArrayList<>();
        flat.add(Action.GOTO_TAB);
        flat.add(Action.SWITCH_INTEGRATION);
        flat.add(null);
        flat.addAll(List.of(
                Action.SEND_MESSAGE, Action.RUN_EXAMPLE, Action.RUN_FOLDER, Action.RUN_INFRA, Action.BROWSE_FILES,
                Action.STOP_ALL));
        flat.add(null);
        flat.addAll(List.of(Action.DOCTOR, Action.RESET_STATS, Action.SCREEN_SUBMENU, Action.SETTINGS));
        if (mcpEnabled) {
            flat.add(null);
            flat.add(Action.MCP_SUBMENU);
        }
        flat.add(null);
        flat.add(Action.SHELL);
        return flat;
    }

    private List<Action> buildScreenSubmenuList() {
        List<Action> flat = new ArrayList<>();
        flat.add(Action.BACK);
        flat.add(null);
        flat.addAll(List.of(Action.THEMES_SUBMENU, Action.SCREENSHOT, Action.RESET_SCREEN));
        flat.add(null);
        flat.addAll(List.of(Action.TAPE_RECORDING, Action.TAPE_INSTRUCTIONS, Action.CAPTION, Action.SHOW_KEYSTROKES));
        flat.add(null);
        flat.add(Action.CAMEL_ANIMATION);
        return flat;
    }

    private List<Action> buildMcpSubmenuList() {
        List<Action> flat = new ArrayList<>();
        flat.add(Action.BACK);
        flat.add(null);
        flat.addAll(List.of(Action.AI_PROMPT, Action.SETUP_AI, Action.AI_LOG, Action.MCP_INFO, Action.MCP_LOG));
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

    private void navigateActionsMenuToSection(int direction) {
        int total = visualActionCount();
        Integer current = actionsMenuState.selected();
        int pos = current != null ? current : 0;
        // move until we hit a divider
        pos += direction;
        while (pos > 0 && pos < total - 1 && !isDividerIndex(pos)) {
            pos += direction;
        }
        // skip past the divider to the first item in the section
        if (isDividerIndex(pos)) {
            pos += direction;
        }
        pos = Math.max(0, Math.min(pos, total - 1));
        if (!isDividerIndex(pos)) {
            actionsMenuState.select(pos);
        }
    }

    boolean isVisible() {
        return showActionsMenu || themePopup.isVisible() || gotoTabPopup.isVisible() || settingsPopup.isVisible()
                || exampleBrowserPopup.isVisible()
                || folderInputPopup.isVisible()
                || runOptionsForm.isVisible()
                || docViewerPopup.isVisible()
                || infraBrowserPopup.isVisible()
                || mcpLogPopup.isVisible() || aiLogPopup.isVisible() || doctorPopup.isVisible()
                || sendMessagePopup.isVisible() || stopAllPopup.isVisible() || captionOverlay.isInlineMode();
    }

    SelectionContext getSelectionContext() {
        if (themePopup.isVisible()) {
            return themePopup.getSelectionContext();
        }
        if (gotoTabPopup.isVisible()) {
            return gotoTabPopup.getSelectionContext();
        }
        if (infraBrowserPopup.isBrowserVisible()) {
            return infraBrowserPopup.getSelectionContext();
        }
        if (exampleBrowserPopup.isVisible()) {
            return exampleBrowserPopup.getSelectionContext();
        }
        if (showActionsMenu) {
            List<String> items = getActionLabels();
            Integer sel = actionsMenuState.selected();
            return new SelectionContext("popup", items, sel != null ? sel : -1, visualActionCount(), "Actions");
        }
        return null;
    }

    String getPendingAutoSelect() {
        return launchManager.getPendingAutoSelect();
    }

    void clearPendingAutoSelect() {
        launchManager.clearPendingAutoSelect();
    }

    List<String> getActionLabels() {
        List<String> labels = new ArrayList<>();
        if ("screen".equals(currentSubmenu)) {
            labels.add("..");
            labels.add("───");
            labels.add("Themes...");
            labels.add("Take Screenshot");
            labels.add("Reset Screen");
            labels.add("───");
            labels.add(tapeRecordingActive.get() ? "Stop Tape Recording" : "Start Tape Recording");
            labels.add("Tape Recording Guide");
            labels.add("Caption...");
            labels.add(keystrokesEnabled.get() ? "Hide Keystrokes" : "Show Keystrokes");
            labels.add("───");
            labels.add("Camel Animation");
            return labels;
        }
        if ("mcp".equals(currentSubmenu)) {
            labels.add("..");
            labels.add("───");
            labels.add("AI Prompt");
            labels.add("Setup MCP");
            labels.add("AI Log");
            labels.add("MCP Info");
            labels.add("MCP Log");
            return labels;
        }
        // Main menu
        labels.add("Go to... (Shift+F2)");
        labels.add("Switch Integration (F3)");
        labels.add("───");
        labels.add("Send Message");
        labels.add("Run an Example...");
        labels.add("Run from Folder...");
        labels.add("Run Dev/Infra Service...");
        labels.add("Browse Files... (Ctrl+F)");
        labels.add("Stop All");
        labels.add("───");
        labels.add("Run Doctor");
        labels.add("Reset Stats");
        labels.add("Screen...");
        labels.add("Settings...");
        if (mcpEnabled) {
            labels.add("───");
            labels.add("AI & MCP...");
        }
        labels.add("───");
        labels.add("Shell");
        return labels;
    }

    void open() {
        showActionsMenu = true;
        currentSubmenu = null;
        actionsMenuState.select(0);
    }

    void openGotoTab() {
        gotoTabPopup.open();
    }

    void close() {
        showActionsMenu = false;
        currentSubmenu = null;
        themePopup.close();
        gotoTabPopup.close();
        settingsPopup.close();
        exampleBrowserPopup.close();
        folderInputPopup.close();
        runOptionsForm.close();
        docViewerPopup.close();
        infraBrowserPopup.close();
        mcpLogPopup.close();
        aiLogPopup.close();
        doctorPopup.close();
        sendMessagePopup.close();
        stopAllPopup.close();
        captionOverlay.close();
    }

    void setNotificationCallback(BiConsumer<String, Boolean> callback) {
        this.notificationCallback = callback;
        launchManager.setNotificationCallback(callback);
        exampleBrowserPopup.setNotificationCallback(callback);
        infraBrowserPopup.setNotificationCallback(callback);
        folderInputPopup.setNotificationCallback(callback);
    }

    void handlePaste(String text) {
        if (sendMessagePopup.isVisible()) {
            sendMessagePopup.handlePaste(text);
        }
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (sendMessagePopup.isVisible()) {
            if (ke.isKey(KeyCode.F5) && !sendFileBrowser.isVisible()) {
                sendMessagePopup.doSend(ctx, scheduler);
            } else {
                sendMessagePopup.handleKeyEvent(ke);
            }
            return true;
        }
        if (mcpLogPopup.handleKeyEvent(ke)) {
            return true;
        }
        if (aiLogPopup.handleKeyEvent(ke)) {
            return true;
        }
        if (docViewerPopup.isVisible()) {
            boolean wasPicker = docViewerPopup.isPickerVisible();
            docViewerPopup.handleKeyEvent(ke);
            if (wasPicker && !docViewerPopup.isVisible() && ke.isCancel()) {
                showActionsMenu = true;
            }
            return true;
        }
        if (infraBrowserPopup.isVisible()) {
            infraBrowserPopup.handleKeyEvent(ke);
            if (!infraBrowserPopup.isVisible() && ke.isCancel()) {
                showActionsMenu = true;
            }
            return true;
        }
        if (runOptionsForm.isVisible()) {
            if (ke.isCancel()) {
                runOptionsForm.close();
                if (folderInputPopup.getSelectedFolder() != null) {
                    folderInputPopup.showInput();
                } else {
                    exampleBrowserPopup.open();
                }
            } else if (ke.isConfirm()) {
                String error = runOptionsForm.validate();
                if (error != null) {
                    runOptionsForm.setError(error);
                } else if (folderInputPopup.getSelectedFolder() != null) {
                    launchFromFolder();
                } else {
                    launchWithName();
                }
            } else {
                runOptionsForm.handleKeyEvent(ke);
            }
            return true;
        }
        if (folderInputPopup.isVisible()) {
            folderInputPopup.handleKeyEvent(ke);
            if (!folderInputPopup.isVisible() && ke.isCancel()) {
                showActionsMenu = true;
            }
            return true;
        }
        if (exampleBrowserPopup.isVisible()) {
            exampleBrowserPopup.handleKeyEvent(ke);
            if (!exampleBrowserPopup.isVisible() && ke.isCancel()) {
                showActionsMenu = true;
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
        if (themePopup.handleKeyEvent(ke)) {
            return true;
        }
        if (gotoTabPopup.isVisible()) {
            boolean wasVisible = gotoTabPopup.isVisible();
            gotoTabPopup.handleKeyEvent(ke);
            if (wasVisible && !gotoTabPopup.isVisible() && ke.isCancel()) {
                showActionsMenu = true;
            }
            return true;
        }
        if (settingsPopup.isVisible()) {
            settingsPopup.handleKeyEvent(ke);
            if (!settingsPopup.isVisible() && ke.isCancel()) {
                showActionsMenu = true;
            }
            return true;
        }
        if (showActionsMenu) {
            if (ke.isCancel()) {
                if (currentSubmenu != null) {
                    currentSubmenu = null;
                    actionsMenuState.select(savedMainSelection);
                } else {
                    showActionsMenu = false;
                }
            } else if (ke.isUp()) {
                navigateActionsMenu(-1);
            } else if (ke.isDown()) {
                navigateActionsMenu(1);
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                navigateActionsMenuToSection(-1);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                navigateActionsMenuToSection(1);
            } else if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
                actionsMenuState.select(0);
                if (isDividerIndex(0)) {
                    navigateActionsMenu(1);
                }
            } else if (ke.isEnd() || ke.isKey(KeyCode.END)) {
                int last = visualActionCount() - 1;
                actionsMenuState.select(last);
                if (isDividerIndex(last)) {
                    navigateActionsMenu(-1);
                }
            } else if (ke.isConfirm()) {
                Integer sel = actionsMenuState.selected();
                if (sel != null) {
                    Action action = resolveAction(sel);
                    if (action == null) {
                        // divider selected, ignore
                    } else if (action == Action.BACK) {
                        currentSubmenu = null;
                        actionsMenuState.select(savedMainSelection);
                    } else if (action == Action.THEMES_SUBMENU) {
                        showActionsMenu = false;
                        themePopup.open();
                    } else if (action == Action.SCREEN_SUBMENU) {
                        savedMainSelection = sel;
                        currentSubmenu = "screen";
                        actionsMenuState.select(0);
                    } else if (action == Action.MCP_SUBMENU) {
                        savedMainSelection = sel;
                        currentSubmenu = "mcp";
                        actionsMenuState.select(0);
                    } else if (action == Action.GOTO_TAB) {
                        showActionsMenu = false;
                        gotoTabPopup.open();
                    } else if (action == Action.SETTINGS) {
                        showActionsMenu = false;
                        settingsPopup.open();
                    } else if (action == Action.SWITCH_INTEGRATION) {
                        if (hasMultipleIntegrations()) {
                            showActionsMenu = false;
                            if (switchIntegrationAction != null) {
                                switchIntegrationAction.run();
                            }
                        }
                    } else if (action == Action.SHELL) {
                        showActionsMenu = false;
                        if (openShellAction != null) {
                            openShellAction.run();
                        }
                    } else if (action == Action.RUN_EXAMPLE) {
                        showActionsMenu = false;
                        exampleBrowserPopup.open();
                    } else if (action == Action.RUN_FOLDER) {
                        showActionsMenu = false;
                        folderInputPopup.open();
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
                        showActionsMenu = false;
                        infraBrowserPopup.open();
                    } else if (action == Action.AI_PROMPT) {
                        showActionsMenu = false;
                        if (openAiPromptAction != null) {
                            openAiPromptAction.run();
                        }
                    } else if (action == Action.SETUP_AI) {
                        showActionsMenu = false;
                        openSetupAI();
                    } else if (action == Action.MCP_INFO) {
                        showActionsMenu = false;
                        openMcpInfo();
                    } else if (action == Action.MCP_LOG) {
                        showActionsMenu = false;
                        openMcpLog();
                    } else if (action == Action.AI_LOG) {
                        showActionsMenu = false;
                        openAiLog();
                    } else if (action == Action.SEND_MESSAGE) {
                        if (ctx != null && ctx.selectedPid != null && !ctx.isInfraSelected()) {
                            showActionsMenu = false;
                            openSendMessage();
                        }
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
                    } else if (action == Action.TOGGLE_THEME) {
                        Theme.toggle();
                        refreshTheme();
                        showActionsMenu = false;
                    } else if (action == Action.STOP_ALL) {
                        showActionsMenu = false;
                        stopAllPopup.open();
                        checkStopAllNotification();
                    } else if (action == Action.CAPTION) {
                        showActionsMenu = false;
                        captionOverlay.openInline();
                    } else if (action == Action.CAMEL_ANIMATION) {
                        showActionsMenu = false;
                        if (camelAnimationAction != null) {
                            camelAnimationAction.run();
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    // ---- Mouse handling ----

    /**
     * Handles a mouse event while an Actions sub-popup is open. The Actions menu and the doc picker (both single-line
     * lists) accept clicks: a click on an entry selects it and activates it via a synthetic Enter (reusing the keyboard
     * activation path), a click outside goes back one level via a synthetic Esc, and the wheel moves the highlight.
     * Every other sub-popup stays modal for the mouse (events are consumed but not acted on). Returns {@code false}
     * only when no Actions popup is open, so the caller can fall back to its normal routing.
     */
    boolean handleMouseEvent(MouseEvent me) {
        if (!isVisible()) {
            return false;
        }
        if (themePopup.isVisible()) {
            return themePopup.handleMouseEvent(me);
        }
        if (gotoTabPopup.isVisible()) {
            return gotoTabPopup.handleMouseEvent(me);
        }
        if (showActionsMenu) {
            return handleListPopupMouse(me, actionsMenuRect, actionsMenuState, visualActionCount(), this::isDividerIndex);
        }
        if (exampleBrowserPopup.isVisible()) {
            return exampleBrowserPopup.handleMouseEvent(me);
        }
        if (infraBrowserPopup.isBrowserVisible()) {
            return infraBrowserPopup.handleMouseEvent(me);
        }
        if (docViewerPopup.isPickerVisible() && docViewerPopup.getPickerIntegrations() != null) {
            return handleListPopupMouse(me, docViewerPopup.getPickerRect(), docViewerPopup.getPickerState(),
                    docViewerPopup.getPickerIntegrations().size(), i -> false);
        }
        if (docViewerPopup.isVisible()) {
            return docViewerPopup.handleMouseEvent(me);
        }
        if (folderInputPopup.isVisible()) {
            return folderInputPopup.handleMouseEvent(me);
        }
        // Other sub-popups (forms, viewers) stay modal: consume the event without acting on it.
        return true;
    }

    private boolean handleListPopupMouse(MouseEvent me, Rect rect, ListState state, int itemCount, IntPredicate separator) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            handleKeyEvent(KeyEvent.ofKey(KeyCode.UP));
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN));
            return true;
        }
        if (me.isClick()) {
            if (rect != null && rect.contains(me.x(), me.y())) {
                int idx = listItemAt(rect, state.offset(), itemCount, me.x(), me.y());
                if (idx >= 0 && !separator.test(idx)) {
                    state.select(idx);
                    handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
                }
                // A click on the border or a divider inside the popup is consumed without acting.
                return true;
            }
            // A click outside the popup dismisses it (one level back), mirroring Esc.
            handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
            return true;
        }
        // Consume any other mouse event so the popup stays modal.
        return true;
    }

    /**
     * Returns the index of the single-line list entry at {@code (mouseX, mouseY)} for a bordered list popup, or
     * {@code -1} when the click is on the border, outside the popup, or past the last entry. {@code offset} is the
     * index of the first visible entry (from {@code ListState.offset()}) for scrolled lists.
     */
    static int listItemAt(Rect popup, int offset, int itemCount, int mouseX, int mouseY) {
        return TuiHelper.listItemAt(popup, offset, itemCount, mouseX, mouseY);
    }

    void render(Frame frame, Rect area) {
        if (themePopup.isVisible()) {
            themePopup.render(frame, area);
        }
        if (gotoTabPopup.isVisible()) {
            gotoTabPopup.render(frame, area);
        }
        if (settingsPopup.isVisible()) {
            settingsPopup.render(frame, area);
        }
        if (showActionsMenu) {
            renderActionsMenu(frame, area);
        }
        if (infraBrowserPopup.isVisible()) {
            infraBrowserPopup.render(frame, area);
        }
        if (folderInputPopup.isVisible()) {
            folderInputPopup.render(frame, area);
        }
        if (exampleBrowserPopup.isVisible()) {
            exampleBrowserPopup.render(frame, area);
        }
        if (runOptionsForm.isVisible()) {
            runOptionsForm.render(frame, area);
        }
        if (docViewerPopup.isVisible()) {
            docViewerPopup.render(frame, area);
        }
        if (mcpLogPopup.isVisible()) {
            mcpLogPopup.render(frame, area);
        }
        if (aiLogPopup.isVisible()) {
            aiLogPopup.render(frame, area);
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
        if (aiLogPopup.isVisible()) {
            aiLogPopup.renderFooter(spans);
            return;
        }
        if (mcpLogPopup.isVisible()) {
            mcpLogPopup.renderFooter(spans);
            return;
        }
        if (docViewerPopup.isVisible()) {
            docViewerPopup.renderFooter(spans);
            return;
        }
        if (runOptionsForm.isVisible()) {
            runOptionsForm.renderFooter(spans);
            return;
        }
        if (folderInputPopup.isVisible()) {
            folderInputPopup.renderFooter(spans);
            return;
        }
        if (infraBrowserPopup.isVisible()) {
            infraBrowserPopup.renderFooter(spans);
            return;
        }
        if (exampleBrowserPopup.isVisible()) {
            exampleBrowserPopup.renderFooter(spans);
            return;
        }
        if (themePopup.isVisible()) {
            themePopup.renderFooter(spans);
            return;
        }
        if (settingsPopup.isVisible()) {
            settingsPopup.renderFooter(spans);
            return;
        }
        if (gotoTabPopup.isVisible()) {
            hint(spans, "type", "filter");
            hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            hint(spans, "Enter", "go to");
            hintLast(spans, "Esc", "back");
            return;
        }
        if (showActionsMenu) {
            hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            hint(spans, "Enter", "select");
            hintLast(spans, "Esc", currentSubmenu != null ? "back" : "cancel");
        }
    }

    void tick(long now) {
        launchManager.tick(now);
    }

    // ---- Rendering ----

    private void renderActionsMenu(Frame frame, Rect area) {
        if ("screen".equals(currentSubmenu)) {
            renderScreenSubmenu(frame, area);
        } else if ("mcp".equals(currentSubmenu)) {
            renderMcpSubmenu(frame, area);
        } else {
            renderMainMenu(frame, area);
        }
    }

    private void renderMainMenu(Frame frame, Rect area) {
        int count = visualActionCount();
        int popupW = 40;
        int popupH = 2 + count;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        this.actionsMenuRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);
        String divider = "  ─────────────────────────────────";
        String stopLabel = stopAllPopup.hasBothGroups()
                ? TuiIcons.menuItem(TuiIcons.STOP, "Stop All...")
                : TuiIcons.menuItem(TuiIcons.STOP, "Stop All");

        boolean canSwitch = hasMultipleIntegrations();
        List<ListItem> items = new ArrayList<>();
        // Group 0: Navigation
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.GO_TO, "Go to... (Shift+F2)")));
        items.add(canSwitch
                ? ListItem.from(TuiIcons.menuItem(TuiIcons.SWITCH, "Switch Integration (F3)"))
                : ListItem.from(TuiIcons.menuItem(TuiIcons.SWITCH, "Switch Integration (F3)")).style(Style.EMPTY.dim()));
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        // Group 1: User Actions
        boolean hasSelection = ctx != null && ctx.selectedPid != null && !ctx.isInfraSelected();
        items.add(hasSelection
                ? ListItem.from(TuiIcons.menuItem(TuiIcons.MESSAGE, "Send Message"))
                : ListItem.from(TuiIcons.menuItem(TuiIcons.MESSAGE, "Send Message")).style(Style.EMPTY.dim()));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.CAMEL, "Run an Example...")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.FOLDER_OPEN, "Run from Folder...")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.INFRA, "Run Dev/Infra Service...")));
        items.add(hasSelection
                ? ListItem.from(TuiIcons.menuItem(TuiIcons.FOLDER, "Browse Files... (Ctrl+F)"))
                : ListItem.from(TuiIcons.menuItem(TuiIcons.FOLDER, "Browse Files... (Ctrl+F)")).style(Style.EMPTY.dim()));
        items.add(ListItem.from(stopLabel));
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        // Group 2: Diagnostics & Screen
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.DOCTOR, "Run Doctor")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.RESET, "Reset Stats")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.COMPUTER, "Screen...")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.SETTINGS, "Settings...")));
        // Group 3: MCP (conditional)
        if (mcpEnabled) {
            items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
            items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.MCP, "AI & MCP...")));
        }
        // Group 4: Shell
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        items.add(ListItem.from("  >_ Shell (F6)"));
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Actions ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, actionsMenuState);
    }

    private void renderScreenSubmenu(Frame frame, Rect area) {
        int count = visualActionCount();
        int popupW = 40;
        int popupH = 2 + count;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        this.actionsMenuRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);
        String divider = "  ─────────────────────────────────";
        String keystrokeLabel = keystrokesEnabled.get()
                ? TuiIcons.menuItem(TuiIcons.KEYSTROKES, "Hide Keystrokes")
                : TuiIcons.menuItem(TuiIcons.KEYSTROKES, "Show Keystrokes");
        String tapeLabel = tapeRecordingActive.get()
                ? TuiIcons.menuItem(TuiIcons.STOP, "Stop Tape Recording (Ctrl+R)")
                : TuiIcons.menuItem(TuiIcons.RECORD, "Start Tape Recording (Ctrl+R)");
        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.from("  .."));
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.THEMES, "Themes...")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.SCREENSHOT, "Take Screenshot")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.CLEAN, "Reset Screen")));
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        items.add(ListItem.from(tapeLabel));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.DOCUMENT, "Tape Recording Guide")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.CAPTION, "Caption...")));
        items.add(ListItem.from(keystrokeLabel));
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.CAMEL, "Camel Animation")));
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Screen ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, actionsMenuState);
    }

    private void renderMcpSubmenu(Frame frame, Rect area) {
        int count = visualActionCount();
        int popupW = 40;
        int popupH = 2 + count;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        this.actionsMenuRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);
        String divider = "  ─────────────────────────────────";

        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.from("  .."));
        items.add(ListItem.from(divider).style(Style.EMPTY.dim()));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.MCP_BRAIN, "AI Prompt (F8)")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.README, "Setup MCP")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.CAPTION, "AI Log")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.MCP, "MCP Info")));
        items.add(ListItem.from(TuiIcons.menuItem(TuiIcons.MCP_LOG, "MCP Log")));
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" AI & MCP ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, actionsMenuState);
    }

    private boolean hasMultipleIntegrations() {
        List<IntegrationInfo> ints = integrations.get();
        return ints != null && ints.stream().filter(i -> !i.vanishing && i.pid != null).count() > 1;
    }

    void openDoc(IntegrationInfo info) {
        showActionsMenu = false;
        String error = docViewerPopup.openDoc(info);
        if (error != null) {
            setNotification(error, true);
        }
    }

    private void setNotification(String msg, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(msg, error);
        }
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
        sendMessagePopup.open(ctx, pid, info.name, info.routes, preSelectedRouteId, info.directory);
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
        docViewerPopup.openTapeInstructions();
    }

    private void openSetupAI() {
        docViewerPopup.openSetupAI();
    }

    private void openMcpInfo() {
        docViewerPopup.openMcpInfo();
    }

    private void openMcpLog() {
        mcpLogPopup.open();
    }

    private void openAiLog() {
        aiLogPopup.open();
    }

    // ---- Folder Input ----

    private void openFolderRunOptionsForm() {
        String folder = folderInputPopup.getSelectedFolder();
        if (folder == null) {
            return;
        }
        String displayName = Path.of(folder).getFileName().toString();
        String pomPath = folderInputPopup.getDetectedPomPath();
        if (pomPath != null) {
            String runtime = TuiHelper.detectPomRuntime(Path.of(pomPath));
            int lockedRuntime = 0;
            if ("spring-boot".equals(runtime)) {
                lockedRuntime = 1;
            } else if ("quarkus".equals(runtime)) {
                lockedRuntime = 2;
            }
            runOptionsForm.open(displayName, displayName, false, true, lockedRuntime);
        } else {
            runOptionsForm.open(displayName, displayName, false, true);
        }
    }

    private void launchFromFolder() {
        String displayName = runOptionsForm.name();
        if (displayName.isEmpty()) {
            String folder = folderInputPopup.getSelectedFolder();
            displayName = folder != null ? Path.of(folder).getFileName().toString() : "folder";
        }
        List<String> extraArgs = runOptionsForm.buildArgs();
        boolean jaegerExport = runOptionsForm.isJaegerExport();
        runOptionsForm.close();
        folderInputPopup.launchFolder(displayName, extraArgs, jaegerExport);
    }

    // ---- Name Input ----

    private void openExampleNameInput() {
        JsonObject example = exampleBrowserPopup.getSelectedExample();
        if (example == null) {
            return;
        }
        String baseName = example.getStringOrDefault("name", "");
        String autoName = generateUniqueName(baseName);
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
        JsonObject example = exampleBrowserPopup.getSelectedExample();
        if (example == null) {
            return;
        }
        String exampleName = example.getStringOrDefault("name", "");
        String displayName = runOptionsForm.name();
        if (displayName.isEmpty()) {
            displayName = exampleName;
        }
        List<String> extraArgs = runOptionsForm.buildArgs();
        boolean stub = runOptionsForm.isStubMode();
        boolean jaegerExport = runOptionsForm.isJaegerExport();
        runOptionsForm.close();

        if (!stub) {
            List<String> missing = launchManager.findMissingInfraServices(example);
            if (jaegerExport && !launchManager.isJaegerRunning()) {
                missing = new ArrayList<>(missing);
                missing.add("jaeger");
            }
            if (!missing.isEmpty()) {
                if (!LaunchManager.isContainerRuntimeAvailable()) {
                    setNotification("Docker/Podman required for infra services. Run Doctor for details", true);
                    return;
                }
                exampleBrowserPopup.startMissingInfraAndDefer(
                        missing, exampleName, displayName, extraArgs, infraBrowserPopup::clearCatalog);
                return;
            }
        }

        exampleBrowserPopup.doLaunch(exampleName, displayName, extraArgs);
    }

    private void showFailureLog(String name, Path logFile) {
        if (!docViewerPopup.hasFailureContent(logFile)) {
            setNotification("Failed: " + name + " (no output)", true);
            return;
        }
        docViewerPopup.showFailureLog(name, logFile);
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
            case TOGGLE_THEME -> {
                Theme.toggle();
                refreshTheme();
            }
            case SCREENSHOT -> screenshotAction.run();
            case SHOW_KEYSTROKES -> toggleKeystrokes.run();
            case TAPE_RECORDING -> toggleTapeRecording.run();
            case DOCTOR -> doctorPopup.open();
            case CAPTION -> captionOverlay.openInline();
            case SETUP_AI -> openSetupAI();
            case MCP_INFO -> openMcpInfo();
            case MCP_LOG -> openMcpLog();
            case AI_LOG -> openAiLog();
            case AI_PROMPT -> {
                if (openAiPromptAction != null) {
                    openAiPromptAction.run();
                }
            }
            case CAMEL_ANIMATION -> {
                if (camelAnimationAction != null) {
                    camelAnimationAction.run();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    TabRegistry.TabEntry consumePendingGotoEntry() {
        return gotoTabPopup.consumePendingEntry();
    }

    private void refreshTheme() {
        if (themeRefreshAction != null) {
            themeRefreshAction.run();
        }
    }

}
