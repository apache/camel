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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Color;
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
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hintLast;

class ActionsPopup {

    private static final int ACTION_RUN_EXAMPLE = 0;
    private static final int ACTION_SHOW_DOCS = 1;
    private static final int ACTION_CAPTION = 2;
    private static final int ACTION_SCREENSHOT = 3;
    private static final int ACTION_SHOW_KEYSTROKES = 4;
    private static final int ACTION_DOCTOR = 5;
    private static final int ACTION_CLASSPATH = 6;
    private static final int ACTION_MCP_INFO = 7;
    private static final int ACTION_MCP_LOG = 8;
    private static final int ACTION_STOP_ALL = 9;

    private final Supplier<Set<String>> runningNames;
    private final Supplier<List<IntegrationInfo>> integrations;
    private final Runnable screenshotAction;
    private final Runnable toggleKeystrokes;
    private final Supplier<Boolean> keystrokesEnabled;
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

    private final McpLogPopup mcpLogPopup = new McpLogPopup();

    private final DoctorPopup doctorPopup = new DoctorPopup();
    private final ClasspathPopup classpathPopup = new ClasspathPopup();
    private final StopAllPopup stopAllPopup;
    private final CaptionOverlay captionOverlay;

    private final List<PendingLaunch> pendingLaunches = new ArrayList<>();
    private String launchNotification;
    private boolean launchNotificationError;
    private long launchNotificationExpiry;

    ActionsPopup(Supplier<Set<String>> runningNames, Supplier<List<IntegrationInfo>> integrations,
                 Supplier<List<InfraInfo>> infraServices, CaptionOverlay captionOverlay,
                 Runnable screenshotAction, Runnable toggleKeystrokes, Supplier<Boolean> keystrokesEnabled) {
        this.runningNames = runningNames;
        this.integrations = integrations;
        this.captionOverlay = captionOverlay;
        this.screenshotAction = screenshotAction;
        this.toggleKeystrokes = toggleKeystrokes;
        this.keystrokesEnabled = keystrokesEnabled;
        this.stopAllPopup = new StopAllPopup(integrations, infraServices);
    }

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    void setMcpEnabled(
            boolean enabled, int port, Supplier<String> connectedClient, Supplier<List<TuiMcpServer.LogEntry>> activityLog) {
        this.mcpEnabled = enabled;
        this.mcpPort = port;
        this.mcpConnectedClient = connectedClient;
        this.mcpActivityLog = activityLog;
        mcpLogPopup.setActivityLog(activityLog);
    }

    private int actionCount() {
        return mcpEnabled ? 10 : 8;
    }

    boolean isVisible() {
        return showActionsMenu || showExampleBrowser || runOptionsForm.isVisible() || showDocPicker || showDocViewer
                || mcpLogPopup.isVisible() || doctorPopup.isVisible() || classpathPopup.isVisible()
                || stopAllPopup.isVisible() || captionOverlay.isInputVisible();
    }

    List<String> getActionLabels() {
        List<String> labels = new ArrayList<>();
        labels.add("Run an example...");
        labels.add("Show Documentation");
        labels.add("Caption...");
        labels.add("Take Screenshot");
        labels.add(keystrokesEnabled.get() ? "Hide Keystrokes" : "Show Keystrokes");
        labels.add("Run Doctor");
        labels.add("Show Classpath");
        if (mcpEnabled) {
            labels.add("MCP Info");
            labels.add("MCP Log");
        }
        labels.add("Stop All");
        return labels;
    }

    void open() {
        showActionsMenu = true;
        actionsMenuState.select(0);
    }

    void close() {
        showActionsMenu = false;
        showExampleBrowser = false;
        runOptionsForm.close();
        showDocPicker = false;
        showDocViewer = false;
        mcpLogPopup.close();
        doctorPopup.close();
        classpathPopup.close();
        stopAllPopup.close();
        captionOverlay.close();
    }

    String notification() {
        return launchNotification;
    }

    boolean notificationError() {
        return launchNotificationError;
    }

    boolean handleKeyEvent(KeyEvent ke) {
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
        if (runOptionsForm.isVisible()) {
            if (ke.isCancel()) {
                runOptionsForm.close();
                showExampleBrowser = true;
            } else if (ke.isConfirm()) {
                launchWithName();
            } else {
                runOptionsForm.handleKeyEvent(ke);
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
                openNameInput();
            } else if (ke.isChar('d')) {
                loadDocFromExample();
            } else if (ke.isConfirm()) {
                launchSelectedExample();
            }
            return true;
        }
        if (captionOverlay.isInputVisible()) {
            return captionOverlay.handleKeyEvent(ke);
        }
        if (classpathPopup.handleKeyEvent(ke)) {
            return true;
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
                actionsMenuState.selectPrevious();
            } else if (ke.isDown()) {
                actionsMenuState.selectNext(actionCount());
            } else if (ke.isConfirm()) {
                Integer sel = actionsMenuState.selected();
                if (sel != null) {
                    int action = resolveAction(sel);
                    if (action == ACTION_RUN_EXAMPLE) {
                        openExampleBrowser();
                    } else if (action == ACTION_SHOW_DOCS) {
                        openDocPicker();
                    } else if (action == ACTION_SCREENSHOT) {
                        showActionsMenu = false;
                        screenshotAction.run();
                    } else if (action == ACTION_SHOW_KEYSTROKES) {
                        showActionsMenu = false;
                        toggleKeystrokes.run();
                    } else if (action == ACTION_DOCTOR) {
                        showActionsMenu = false;
                        doctorPopup.open();
                    } else if (action == ACTION_CLASSPATH) {
                        showActionsMenu = false;
                        openClasspath();
                    } else if (action == ACTION_MCP_INFO) {
                        showActionsMenu = false;
                        openMcpInfo();
                    } else if (action == ACTION_MCP_LOG) {
                        showActionsMenu = false;
                        openMcpLog();
                    } else if (action == ACTION_STOP_ALL) {
                        showActionsMenu = false;
                        stopAllPopup.open();
                        checkStopAllNotification();
                    } else if (action == ACTION_CAPTION) {
                        showActionsMenu = false;
                        captionOverlay.openInput();
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
        if (classpathPopup.isVisible()) {
            classpathPopup.render(frame, area);
        }
        if (captionOverlay.isInputVisible()) {
            captionOverlay.render(frame, area);
        }
    }

    void renderFooter(List<Span> spans) {
        if (captionOverlay.isInputVisible()) {
            captionOverlay.renderFooter(spans);
            return;
        }
        if (classpathPopup.isVisible()) {
            classpathPopup.renderFooter(spans);
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
        if (showExampleBrowser) {
            hint(spans, "↑↓", "navigate");
            hint(spans, "Enter", "run");
            hint(spans, "r", "run...");
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
        if (launchNotification != null && now > launchNotificationExpiry) {
            launchNotification = null;
        }
    }

    // ---- Rendering ----

    private void renderActionsMenu(Frame frame, Rect area) {
        int count = actionCount();
        int popupW = 34;
        int popupH = 2 + count;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);
        // extra space after ⌨️ because it renders narrower than other emoji
        String keystrokeLabel = keystrokesEnabled.get()
                ? "  ⌨️  Hide Keystrokes"
                : "  ⌨️  Show Keystrokes";
        String stopLabel = stopAllPopup.hasBothGroups()
                ? "  🛑 Stop All..."
                : "  🛑 Stop All";
        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.from("  🐪 Run an example..."));
        items.add(ListItem.from("  📖 Show Documentation"));
        items.add(ListItem.from("  💬 Caption... (Ctrl+T)"));
        items.add(ListItem.from("  📸 Take Screenshot"));
        items.add(ListItem.from(keystrokeLabel));
        items.add(ListItem.from("  🩺 Run Doctor"));
        items.add(ListItem.from("  📦 Show Classpath"));
        if (mcpEnabled) {
            items.add(ListItem.from("  🤖 MCP Info"));
            items.add(ListItem.from("  📋 MCP Log"));
        }
        items.add(ListItem.from(stopLabel));
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
        int popupH = Math.min(exampleCatalog.size() + 10, Math.min(22, area.height() - 6));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

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
                                Span.styled(" Enter", MonitorContext.HINT_KEY_STYLE), Span.raw(" run │"),
                                Span.styled(" r", MonitorContext.HINT_KEY_STYLE), Span.raw(" run... │"),
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

            String icons = (bundled ? "📦" : "🌐") + (docker ? "🐳" : "  ") + (citrus ? "🍋" : "  ");
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
        items.add(ListItem.from(" 📦 = bundled (offline)  🌐 = online (GitHub)  🐳 = Docker  🍋 = Citrus tests")
                .style(Style.EMPTY.dim()));
        return items;
    }

    // ---- Doc Viewer & Picker ----

    private void renderDocViewer(Frame frame, Rect area) {
        Rect popup = new Rect(area.left() + 2, area.top() + 1, area.width() - 4, area.height() - 2);
        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" " + docTitle + " ")
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
            List<Line> visible = docLines.subList(clampedScroll, end);
            frame.renderWidget(
                    Paragraph.builder().text(Text.from(visible.toArray(Line[]::new))).build(),
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
        int popupH = Math.min(docPickerIntegrations.size() + 2, Math.min(15, area.height() - 6));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

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
                        .title(" Show Documentation ")
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

    private int resolveAction(int index) {
        if (!mcpEnabled && index >= ACTION_MCP_INFO) {
            return index + 2;
        }
        return index;
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
                     + "| `tui_wait_for_idle` | Waits for the screen to settle after an action |\n\n"
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

    private void openClasspath() {
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
        classpathPopup.open(ctx, pid, ctx.selectedName());
        String err = classpathPopup.consumeError();
        if (err != null) {
            setNotification(err, true);
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
        runOptionsForm.close();
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add("--example=" + exampleName);
            cmd.addAll(extraArgs);
            Path outputFile = Files.createTempFile("camel-example-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            pendingLaunches.add(new PendingLaunch(displayName, process, outputFile, System.currentTimeMillis()));
            launchNotification = "Starting: " + displayName;
            launchNotificationError = false;
            launchNotificationExpiry = System.currentTimeMillis() + 5000;
        } catch (Exception e) {
            launchNotification = "Failed to start: " + exampleName + " - " + e.getMessage();
            launchNotificationError = true;
            launchNotificationExpiry = System.currentTimeMillis() + 10000;
        }
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
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add("--example=" + exampleName);
            Path outputFile = Files.createTempFile("camel-example-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            pendingLaunches.add(new PendingLaunch(exampleName, process, outputFile, System.currentTimeMillis()));
            launchNotification = "Starting: " + exampleName;
            launchNotificationError = false;
            launchNotificationExpiry = System.currentTimeMillis() + 5000;
        } catch (Exception e) {
            launchNotification = "Failed to start: " + exampleName + " - " + e.getMessage();
            launchNotificationError = true;
            launchNotificationExpiry = System.currentTimeMillis() + 10000;
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
                    String detail = readFirstLine(pl.outputFile());
                    launchNotification = "Failed: " + pl.name()
                                         + (detail != null ? " - " + detail : "");
                    launchNotificationError = true;
                    launchNotificationExpiry = now + 10000;
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

    private record PendingLaunch(String name, Process process, Path outputFile, long startTime) {
    }
}
