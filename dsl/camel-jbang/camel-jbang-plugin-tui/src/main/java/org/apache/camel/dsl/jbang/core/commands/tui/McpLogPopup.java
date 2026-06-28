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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.tamboui.layout.Rect;
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
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hintLast;

class McpLogPopup {

    private boolean visible;
    private Supplier<List<TuiMcpServer.LogEntry>> activityLog;
    private List<TuiMcpServer.LogEntry> entries;
    private int selected;
    private int detailScroll;

    void setActivityLog(Supplier<List<TuiMcpServer.LogEntry>> activityLog) {
        this.activityLog = activityLog;
    }

    boolean isVisible() {
        return visible;
    }

    void open() {
        entries = activityLog != null ? activityLog.get() : List.of();
        selected = entries.isEmpty() ? 0 : entries.size() - 1;
        detailScroll = 0;
        visible = true;
    }

    void close() {
        visible = false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel()) {
            visible = false;
        } else if (ke.isUp() || ke.isChar('k')) {
            if (entries != null && !entries.isEmpty()) {
                selected = Math.max(0, selected - 1);
                detailScroll = 0;
            }
        } else if (ke.isDown() || ke.isChar('j')) {
            if (entries != null && !entries.isEmpty()) {
                selected = Math.min(entries.size() - 1, selected + 1);
                detailScroll = 0;
            }
        } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            detailScroll = Math.max(0, detailScroll - 5);
        } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            detailScroll += 5;
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        Rect popup = new Rect(area.left() + 2, area.top() + 1, area.width() - 4, area.height() - 2);
        frame.renderWidget(Clear.INSTANCE, popup);

        if (entries == null || entries.isEmpty()) {
            Block block = Block.builder()
                    .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                    .title(" MCP Log ")
                    .titleBottom(Title.from(Line.from(
                            Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE), Span.raw(" back "))))
                    .build();
            frame.renderWidget(block, popup);
            Rect inner = block.inner(popup);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled("No MCP activity yet.", Style.EMPTY.dim()))), inner);
            return;
        }

        int splitY = popup.top() + Math.max(3, (popup.height() * 2) / 5);
        Rect masterArea = new Rect(popup.left(), popup.top(), popup.width(), splitY - popup.top());
        Rect detailArea = new Rect(popup.left(), splitY, popup.width(), popup.bottom() - splitY);

        renderMaster(frame, masterArea);
        renderDetail(frame, detailArea);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "↑↓", "select");
        hint(spans, "PgUp/Dn", "scroll detail");
        hintLast(spans, "Esc", "back");
    }

    private void renderMaster(Frame frame, Rect area) {
        List<ListItem> items = new ArrayList<>();
        for (TuiMcpServer.LogEntry entry : entries) {
            Style levelStyle = switch (entry.level()) {
                case CONNECT -> Style.EMPTY.fg(Color.GREEN);
                case TOOL -> Style.EMPTY.fg(Color.CYAN);
                case ERROR -> Style.EMPTY.fg(Color.LIGHT_RED);
                default -> Style.EMPTY.fg(Color.GREEN);
            };
            String levelTag = switch (entry.level()) {
                case CONNECT -> " CONNECT ";
                case TOOL -> " TOOL    ";
                case ERROR -> " ERROR   ";
                default -> " INFO    ";
            };
            items.add(ListItem.from(Line.from(
                    Span.styled(entry.timestamp(), Style.EMPTY.dim()),
                    Span.styled(levelTag, levelStyle),
                    Span.raw(entry.message()))));
        }

        ListState masterState = new ListState();
        masterState.select(selected);
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("▸ ")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" MCP Log ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, area, masterState);
    }

    private void renderDetail(Frame frame, Rect area) {
        TuiMcpServer.LogEntry entry = entries.get(selected);
        List<Line> lines = new ArrayList<>();
        if (entry.requestBody() != null) {
            lines.add(Line.from(Span.styled("▶ Request", Style.EMPTY.fg(Color.YELLOW).bold())));
            addJsonLines(lines, entry.requestBody());
            lines.add(Line.from(Span.raw("")));
        }
        if (entry.responseBody() != null) {
            lines.add(Line.from(Span.styled("◀ Response", Style.EMPTY.fg(Color.GREEN).bold())));
            addJsonLines(lines, entry.responseBody());
        }
        if (entry.requestBody() == null && entry.responseBody() == null) {
            lines.add(Line.from(Span.styled("(no request/response data)", Style.EMPTY.dim())));
        }

        Block detailBlock = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Detail ")
                .build();
        frame.renderWidget(detailBlock, area);
        Rect inner = detailBlock.inner(area);

        int visibleLines = inner.height();
        int totalLines = lines.size();
        int clampedScroll = Math.min(detailScroll, Math.max(0, totalLines - visibleLines));
        int end = Math.min(clampedScroll + visibleLines, totalLines);
        if (clampedScroll < end) {
            List<Line> visible = lines.subList(clampedScroll, end);
            frame.renderWidget(
                    Paragraph.builder().text(Text.from(visible.toArray(Line[]::new))).build(),
                    inner);
        }
    }

    private static void addJsonLines(List<Line> lines, String json) {
        String pretty = Jsoner.prettyPrint(json, 2);
        for (String line : pretty.split("\n", -1)) {
            lines.add(Line.from(Span.styled("  " + line, Style.EMPTY.dim())));
        }
    }
}
