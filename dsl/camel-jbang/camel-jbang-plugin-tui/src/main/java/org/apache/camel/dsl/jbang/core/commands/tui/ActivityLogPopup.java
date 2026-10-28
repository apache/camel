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

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * A log of activity as a popup over the screen: the entries in a list at the top, the detail of the selected entry
 * below. Up and Down (or k and j) select, PgUp and PgDn scroll the detail, Esc closes. The entries are read when the
 * popup opens, and the newest is selected.
 * <p>
 * The kinds differ in their entries and how an entry is shown: {@link AiLogPopup} (the questions, answers and tool
 * calls of the AI panel) and {@link McpLogPopup} (the calls MCP clients made).
 *
 * @param <E> the log entry
 */
abstract class ActivityLogPopup<E> {

    private boolean visible;
    private Supplier<List<E>> activityLog;
    private List<E> entries;
    private int selected;
    private int detailScroll;

    void setActivityLog(Supplier<List<E>> activityLog) {
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
            // kept within the detail when it is drawn
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
                    .title(title())
                    .titleBottom(Title.from(Line.from(
                            Span.styled(" Esc", Theme.hintKey()), Span.raw(" back "))))
                    .build();
            frame.renderWidget(block, popup);
            frame.renderWidget(Paragraph.from(Line.from(Span.styled(emptyMessage(), Style.EMPTY.dim()))),
                    block.inner(popup));
            return;
        }

        int splitY = popup.top() + Math.max(3, (popup.height() * 2) / 5);
        Rect masterArea = new Rect(popup.left(), popup.top(), popup.width(), splitY - popup.top());
        Rect detailArea = new Rect(popup.left(), splitY, popup.width(), popup.bottom() - splitY);

        renderMaster(frame, masterArea);
        renderDetail(frame, detailArea, entries.get(selected));
    }

    void renderFooter(List<Span> spans) {
        hint(spans, TuiIcons.HINT_SCROLL, "select");
        hint(spans, "PgUp/Dn", "detail");
        hintLast(spans, "Esc", "close");
    }

    /** The title of the popup, such as {@code " AI Log "}. */
    protected abstract String title();

    /** What the popup says while the log is empty. */
    protected abstract String emptyMessage();

    /** The title of the entry list; the plain {@link #title()} unless a kind shows more. */
    protected Title listTitle() {
        return Title.from(title());
    }

    /** The row of an entry in the list; {@link #entryLine(String, String, Style, String)} builds the usual one. */
    protected abstract Line entryLine(E entry);

    /**
     * Draws the detail of the selected entry, with {@link #renderDetailLines(Frame, Rect, List)} or, for content of its
     * own, keeping the scroll within it with {@link #clampDetailScroll(int)}.
     */
    protected abstract void renderDetail(Frame frame, Rect area, E entry);

    /** A list row: the time dimmed, the level tag in its style, and the message. */
    protected static Line entryLine(String timestamp, String levelTag, Style levelStyle, String message) {
        return Line.from(
                Span.styled(timestamp, Style.EMPTY.dim()),
                Span.styled(levelTag, levelStyle),
                Span.raw(message));
    }

    /** The bordered block the detail is drawn in. */
    protected static Block detailBlock() {
        return Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Detail ")
                .build();
    }

    /**
     * Keeps the scroll of the detail within {@code maxScroll} (at least 0) and returns it: PgUp then moves at once
     * after PgDn went past the end.
     */
    protected final int clampDetailScroll(int maxScroll) {
        detailScroll = Math.min(detailScroll, Math.max(0, maxScroll));
        return detailScroll;
    }

    /** Draws lines in the detail block, scrolled with PgUp and PgDn. */
    protected final void renderDetailLines(Frame frame, Rect area, List<Line> lines) {
        Block block = detailBlock();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);

        int visibleLines = inner.height();
        int scroll = clampDetailScroll(lines.size() - visibleLines);
        int end = Math.min(scroll + visibleLines, lines.size());
        if (scroll < end) {
            List<Line> visible = lines.subList(scroll, end);
            frame.renderWidget(Paragraph.builder().text(Text.from(visible.toArray(Line[]::new))).build(), inner);
        }
    }

    /** Adds JSON pretty printed and dimmed, or as it is when it does not parse. */
    protected static void addJsonLines(List<Line> lines, String json) {
        String text;
        try {
            text = Jsoner.prettyPrint(json, 2);
        } catch (Exception e) {
            text = json;
        }
        for (String line : text.split("\n", -1)) {
            lines.add(Line.from(Span.styled("  " + line, Style.EMPTY.dim())));
        }
    }

    private void renderMaster(Frame frame, Rect area) {
        List<ListItem> items = new ArrayList<>();
        for (E entry : entries) {
            items.add(ListItem.from(entryLine(entry)));
        }
        ListState masterState = new ListState();
        masterState.select(selected);
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("▸ ")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(listTitle())
                        .build())
                .build();
        frame.renderStatefulWidget(list, area, masterState);
    }
}
