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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Overflow;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class DocViewerPopup {

    private boolean showViewer;
    private boolean showPicker;
    private String docContent;
    private List<Line> docLines;
    private String docTitle;
    private int docScroll;
    private Runnable onCloseCallback;
    private String catalogEntryName;
    private String catalogEntryKind;
    private org.apache.camel.catalog.CamelCatalog catalogEntryRef;
    private boolean wantsOptions;
    private final TocPopup tocPopup = new TocPopup();
    private int lastContentWidth;
    private int lastTotalHeight;

    private final ScrollbarState scrollbarState = new ScrollbarState();
    private final ListState pickerState = new ListState();
    private List<IntegrationInfo> pickerIntegrations;
    private Rect pickerRect;

    private MonitorContext ctx;
    private Supplier<List<IntegrationInfo>> integrations;
    private int mcpPort;
    private Supplier<String> mcpConnectedClient;

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    void setIntegrations(Supplier<List<IntegrationInfo>> integrations) {
        this.integrations = integrations;
    }

    void setMcpState(int port, Supplier<String> connectedClient) {
        this.mcpPort = port;
        this.mcpConnectedClient = connectedClient;
    }

    boolean isViewerVisible() {
        return showViewer;
    }

    boolean isPickerVisible() {
        return showPicker;
    }

    boolean isVisible() {
        return showViewer || showPicker;
    }

    Rect getPickerRect() {
        return pickerRect;
    }

    ListState getPickerState() {
        return pickerState;
    }

    List<IntegrationInfo> getPickerIntegrations() {
        return pickerIntegrations;
    }

    boolean consumeWantsOptions() {
        boolean v = wantsOptions;
        wantsOptions = false;
        return v;
    }

    String getCatalogEntryName() {
        return catalogEntryName;
    }

    String getCatalogEntryKind() {
        return catalogEntryKind;
    }

    org.apache.camel.catalog.CamelCatalog getCatalogEntryRef() {
        return catalogEntryRef;
    }

    void close() {
        showViewer = false;
        showPicker = false;
        onCloseCallback = null;
        catalogEntryName = null;
        catalogEntryKind = null;
        catalogEntryRef = null;
    }

    void openMarkdown(String title, String markdown) {
        docLines = null;
        docContent = markdown;
        docTitle = title;
        docScroll = 0;
        lastTotalHeight = 0;
        showViewer = true;
        onCloseCallback = null;
    }

    void openMarkdown(String title, String markdown, Runnable onClose) {
        openMarkdown(title, markdown);
        this.onCloseCallback = onClose;
    }

    void openCatalogDoc(
            String title, String markdown, String entryName, String entryKind,
            org.apache.camel.catalog.CamelCatalog catalog) {
        openMarkdown(title, markdown);
        this.catalogEntryName = entryName;
        this.catalogEntryKind = entryKind;
        this.catalogEntryRef = catalog;
    }

    void openLines(String title, List<Line> lines) {
        docContent = null;
        docLines = lines;
        docTitle = title;
        docScroll = 0;
        showViewer = true;
        onCloseCallback = null;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (showViewer) {
            if (tocPopup.isVisible()) {
                tocPopup.handleKeyEvent(ke);
                TocPopup.TocEntry entry = tocPopup.consumePendingEntry();
                if (entry != null) {
                    jumpToHeading(entry);
                }
                return true;
            }
            if (ke.isCancel()) {
                showViewer = false;
                Runnable cb = onCloseCallback;
                onCloseCallback = null;
                if (cb != null) {
                    cb.run();
                }
            } else if (ke.isCharIgnoreCase('t') && docContent != null) {
                List<TocPopup.TocEntry> headings = TocPopup.extractHeadings(docContent);
                if (!headings.isEmpty()) {
                    tocPopup.open(headings);
                }
            } else if (ke.isCharIgnoreCase('o') && catalogEntryName != null) {
                wantsOptions = true;
                showViewer = false;
            } else if (ke.isUp() || ke.isChar('k')) {
                docScroll = Math.max(0, docScroll - 1);
            } else if (ke.isDown() || ke.isChar('j')) {
                docScroll++;
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                docScroll = Math.max(0, docScroll - 10);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                docScroll += 10;
            } else if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
                docScroll = 0;
            } else if (ke.isEnd() || ke.isKey(KeyCode.END)) {
                docScroll = Integer.MAX_VALUE;
            }
            return true;
        }
        if (showPicker) {
            if (ke.isCancel()) {
                showPicker = false;
                return true;
            }
            if (ke.isUp()) {
                pickerState.selectPrevious();
            } else if (ke.isDown()) {
                pickerState.selectNext(pickerIntegrations != null ? pickerIntegrations.size() : 0);
            } else if (ke.isConfirm()) {
                loadDocFromSelectedIntegration();
            }
            return true;
        }
        return false;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (showViewer) {
            if (tocPopup.isVisible()) {
                tocPopup.handleMouseEvent(me);
                TocPopup.TocEntry entry = tocPopup.consumePendingEntry();
                if (entry != null) {
                    jumpToHeading(entry);
                }
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                docScroll = Math.max(0, docScroll - 3);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                docScroll += 3;
                return true;
            }
            return true;
        }
        return false;
    }

    private void jumpToHeading(TocPopup.TocEntry entry) {
        if (docContent == null || lastContentWidth <= 0) {
            return;
        }
        String prefix = docContent.substring(0, Math.min(entry.charOffset(), docContent.length()));
        int offset = MarkdownView.builder()
                .source(prefix)
                .styles(Theme.markdownStyles())
                .build()
                .computeHeight(lastContentWidth);
        docScroll = offset;
    }

    void render(Frame frame, Rect area) {
        if (showPicker) {
            renderPicker(frame, area);
        }
        if (showViewer) {
            renderViewer(frame, area);
        }
    }

    void renderFooter(List<Span> spans) {
        if (showViewer) {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "scroll");
            if (docContent != null) {
                hint(spans, "t", "toc");
            }
            if (catalogEntryName != null) {
                hintLast(spans, "o", "options");
            }
        } else if (showPicker) {
            hint(spans, "↑↓", "navigate");
            hint(spans, "Enter", "view");
            hintLast(spans, "Esc", "back");
        }
    }

    // --- Picker ---

    String openDocPicker() {
        List<IntegrationInfo> withDocs = integrations.get().stream()
                .filter(i -> !i.vanishing && i.readmeFiles != null && !i.readmeFiles.isEmpty())
                .collect(Collectors.toList());
        if (withDocs.isEmpty()) {
            return "No integrations with documentation found";
        }
        if (withDocs.size() == 1) {
            return loadDocFromIntegration(withDocs.get(0));
        }
        pickerIntegrations = withDocs;
        showPicker = true;
        pickerState.select(0);
        return null;
    }

    String openDoc(IntegrationInfo info) {
        return loadDocFromIntegration(info);
    }

    private void loadDocFromSelectedIntegration() {
        Integer sel = pickerState.selected();
        if (sel == null || pickerIntegrations == null || sel >= pickerIntegrations.size()) {
            return;
        }
        IntegrationInfo info = pickerIntegrations.get(sel);
        loadDocFromIntegration(info);
    }

    private String loadDocFromIntegration(IntegrationInfo info) {
        if (ctx == null) {
            return null;
        }
        docLines = null;
        showPicker = false;
        try {
            Path outputFile = ctx.getOutputFile(info.pid);
            Files.deleteIfExists(outputFile);
            org.apache.camel.util.json.JsonObject action = new org.apache.camel.util.json.JsonObject();
            action.put("action", "readme");
            PathUtils.writeTextSafely(action.toJson(), ctx.getActionFile(info.pid));
            org.apache.camel.util.json.JsonObject response = TuiHelper.pollJsonResponse(outputFile, 5000);
            if (response != null && response.getString("content") != null) {
                String raw = response.getString("content");
                String file = response.getStringOrDefault("file", "README");
                docContent = file.endsWith(".adoc") ? DocHelper.asciidocToMarkdown(raw) : raw;
                docTitle = (info.name != null ? info.name : info.pid) + " - " + Path.of(file).getFileName();
                docScroll = 0;
                showViewer = true;
                onCloseCallback = null;
            } else {
                return "Could not load documentation";
            }
        } catch (Exception e) {
            return "Error loading documentation: " + e.getMessage();
        }
        return null;
    }

    // --- Content generators ---

    void openTapeInstructions() {
        openMarkdown("Tape Recording Guide",
                "# Tape Recording Guide\n\n"
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
                                             + "- Keep recordings focused — one workflow at a time works best\n");
    }

    void openSetupAI() {
        String url = "http://localhost:" + mcpPort + "/mcp";
        String client = mcpConnectedClient != null ? mcpConnectedClient.get() : null;
        String status = client != null
                ? "**Connected:** " + client + "\n\nYour AI agent is already connected and ready to use."
                : "**Status:** Waiting for connection";
        openMarkdown("Setup MCP",
                "# Setup MCP\n\n"
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
                                  + "Try asking: *\"What's on my Camel TUI screen right now?\"*\n");
    }

    void openMcpInfo() {
        String url = "http://localhost:" + mcpPort + "/mcp";
        String client = mcpConnectedClient != null ? mcpConnectedClient.get() : null;
        String status = client != null
                ? "**Connected:** " + client
                : "**Status:** Waiting for connection";
        openMarkdown("MCP Info",
                "# MCP Server\n\n"
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
                                 + "- \"Select the myApp integration\"\n");
    }

    void showFailureLog(String name, Path logFile) {
        List<String> logLines = TuiHelper.readAllLines(logFile);
        if (logLines.isEmpty()) {
            return;
        }
        List<Line> lines = logLines.stream()
                .map(line -> TuiHelper.ansiToLine(line.replace("\t", "        "), 0))
                .collect(Collectors.toList());
        openLines("Failed: " + name, lines);
    }

    boolean hasFailureContent(Path logFile) {
        return !TuiHelper.readAllLines(logFile).isEmpty();
    }

    // --- Rendering ---

    private void renderViewer(Frame frame, Rect area) {
        frame.renderWidget(Clear.INSTANCE, area);
        Title title;
        if (docTitle != null && docTitle.startsWith("Failed:")) {
            String rest = docTitle.substring("Failed:".length());
            title = Title.from(Line.from(
                    Span.styled(" Failed:", Theme.error().bold()),
                    Span.raw(rest + " ")));
        } else {
            title = Title.from(" " + docTitle + " ");
        }
        List<Span> footerSpans = new ArrayList<>();
        footerSpans.add(Span.styled(" Esc ", Theme.hintKey()));
        footerSpans.add(Span.raw(" back  "));
        footerSpans.add(Span.styled(" ↑↓ ", Theme.hintKey()));
        footerSpans.add(Span.raw(" scroll  "));
        if (docContent != null) {
            footerSpans.add(Span.styled(" t ", Theme.hintKey()));
            footerSpans.add(Span.raw(" toc  "));
        }
        if (catalogEntryName != null) {
            footerSpans.add(Span.styled(" o ", Theme.hintKey()));
            footerSpans.add(Span.raw(" options "));
        }
        Title footer = Title.from(Line.from(footerSpans));
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(title)
                .titleBottom(footer)
                .build();
        if (docLines != null) {
            frame.renderWidget(block, area);
            Rect inner = block.inner(area);
            List<Rect> hChunks = Layout.horizontal()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(inner);
            int visibleLines = hChunks.get(0).height();
            int totalLines = docLines.size();
            int clampedScroll = Math.min(docScroll, Math.max(0, totalLines - visibleLines));
            docScroll = clampedScroll;
            int end = Math.min(clampedScroll + visibleLines, totalLines);
            List<Line> visible = new ArrayList<>(docLines.subList(clampedScroll, end));
            while (visible.size() < visibleLines) {
                visible.add(Line.from(""));
            }
            frame.renderWidget(
                    Paragraph.builder().text(Text.from(visible.toArray(Line[]::new)))
                            .overflow(Overflow.CLIP).build(),
                    hChunks.get(0));
            if (totalLines > visibleLines) {
                scrollbarState
                        .contentLength(totalLines)
                        .viewportContentLength(visibleLines)
                        .position(clampedScroll);
                frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), scrollbarState);
            }
        } else {
            frame.renderWidget(block, area);
            Rect inner = block.inner(area);
            List<Rect> hChunks = Layout.horizontal()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(inner);
            lastContentWidth = hChunks.get(0).width();
            int viewportHeight = hChunks.get(0).height();
            if (lastTotalHeight > 0) {
                int maxScroll = Math.max(0, lastTotalHeight - viewportHeight);
                if (docScroll > maxScroll) {
                    docScroll = maxScroll;
                }
            }
            MarkdownView view = MarkdownView.builder()
                    .source(docContent)
                    .scroll(docScroll)
                    .styles(Theme.markdownStyles())
                    .build();
            frame.renderWidget(view, hChunks.get(0));
            int totalHeight = view.computeHeight(lastContentWidth);
            lastTotalHeight = totalHeight;
            if (totalHeight > viewportHeight) {
                scrollbarState
                        .contentLength(totalHeight)
                        .viewportContentLength(viewportHeight)
                        .position(docScroll);
                frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), scrollbarState);
            }
        }
        if (tocPopup.isVisible()) {
            tocPopup.render(frame, area);
        }
    }

    private void renderPicker(Frame frame, Rect area) {
        if (pickerIntegrations == null || pickerIntegrations.isEmpty()) {
            return;
        }
        int popupW = Math.min(60, area.width() - 4);
        int popupH = Math.min(pickerIntegrations.size() + 2, Math.min(15, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        this.pickerRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);
        List<ListItem> items = new ArrayList<>();
        for (IntegrationInfo info : pickerIntegrations) {
            String label = "  " + (info.name != null ? info.name : info.pid);
            items.add(ListItem.from(label));
        }
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Show Integration Doc ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, pickerState);
    }
}
