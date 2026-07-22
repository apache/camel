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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
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
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;

class TocPopup {

    record TocEntry(int level, String title, int charOffset) {
    }

    private boolean visible;
    private final FuzzyFilter filter = new FuzzyFilter();
    private final ListState listState = new ListState();
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private List<TocEntry> allEntries;
    private List<TocEntry> filteredEntries;
    private Rect popupRect;
    private TocEntry pendingEntry;

    boolean isVisible() {
        return visible;
    }

    void open(List<TocEntry> entries) {
        this.allEntries = entries;
        this.pendingEntry = null;
        this.visible = true;
        filter.clearFilter();
        rebuildList();
    }

    void close() {
        visible = false;
        filter.clearFilter();
    }

    TocEntry consumePendingEntry() {
        TocEntry e = pendingEntry;
        pendingEntry = null;
        return e;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        int size = filteredEntries != null ? filteredEntries.size() : 0;
        if (ke.isCancel()) {
            close();
            return true;
        }
        if (ke.isUp()) {
            listState.selectPrevious();
            return true;
        }
        if (ke.isDown()) {
            listState.selectNext(size);
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 5; i++) {
                listState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < 5; i++) {
                listState.selectNext(size);
            }
            return true;
        }
        if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            listState.selectFirst();
            return true;
        }
        if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            listState.selectLast(size);
            return true;
        }
        if (ke.isConfirm()) {
            Integer sel = listState.selected();
            if (sel != null && filteredEntries != null && sel < filteredEntries.size()) {
                pendingEntry = filteredEntries.get(sel);
                close();
            }
            return true;
        }
        if (ke.isKey(KeyCode.BACKSPACE)) {
            filter.deleteChar();
            rebuildList();
            return true;
        }
        if (ke.code() == KeyCode.CHAR && !ke.hasCtrl() && !ke.hasAlt()) {
            filter.appendChar(ke.string().charAt(0));
            rebuildList();
            return true;
        }
        return true;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            listState.selectPrevious();
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            int size = filteredEntries != null ? filteredEntries.size() : 0;
            listState.selectNext(size);
            return true;
        }
        if (me.isClick()) {
            if (popupRect != null && popupRect.contains(me.x(), me.y())) {
                int idx = TuiHelper.listItemAt(popupRect, 0,
                        (filteredEntries != null ? filteredEntries.size() : 0) + 2,
                        me.x(), me.y());
                if (idx >= 2 && filteredEntries != null && idx - 2 < filteredEntries.size()) {
                    listState.select(idx - 2);
                    handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
                }
                return true;
            }
            close();
            return true;
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (filteredEntries == null) {
            return;
        }
        int popupW = Math.min(80, area.width() - 4);
        int contentH = filteredEntries.size() + 2;
        int maxH = area.height() - 4;
        int popupH = contentH + 2 <= maxH ? contentH + 2 : Math.min(contentH + 2, maxH);
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        this.popupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);

        String filterText = filter.hasFilter() ? filter.filter() : "";
        String prompt = "> " + filterText + "█";

        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.from(Line.from(Span.styled(prompt, Theme.info()))));
        String sep = "─".repeat(Math.max(1, popupW - 2));
        items.add(ListItem.from(Line.from(Span.styled(sep, Style.EMPTY.dim()))));

        Style normalStyle = Style.EMPTY;
        Style matchStyle = Theme.label().bold();
        Style dimStyle = Style.EMPTY.dim();
        int titleColWidth = popupW - 8;

        for (TocEntry entry : filteredEntries) {
            List<Span> spans = new ArrayList<>();
            String indent = entry.level() >= 3 ? "    " : "  ";
            String prefix = entry.level() >= 3 ? "└ " : "";
            String title = prefix + entry.title();
            if (title.length() > titleColWidth) {
                title = title.substring(0, titleColWidth - 1) + "…";
            }

            spans.add(Span.raw(indent));
            if (filter.hasFilter()) {
                int[] nameMatch = FuzzyFilter.fuzzyMatch(title, filter.filter());
                if (nameMatch != null) {
                    Line hl = FuzzyFilter.highlightLine(title, nameMatch, normalStyle, matchStyle);
                    spans.addAll(hl.spans());
                } else {
                    spans.add(Span.styled(title, normalStyle));
                }
            } else {
                spans.add(Span.styled(title, normalStyle));
            }
            items.add(ListItem.from(Line.from(spans)));
        }

        ListState renderState = new ListState();
        Integer sel = listState.selected();
        if (sel != null) {
            renderState.select(sel + 2);
        }

        int total = allEntries != null ? allEntries.size() : 0;
        int shown = filteredEntries.size();
        String blockTitle = shown == total
                ? " Table of Contents (" + total + ") "
                : " Table of Contents (" + shown + "/" + total + ") ";

        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(blockTitle)
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, renderState);

        int visibleRows = Math.max(1, popup.height() - 2);
        if (shown + 2 > visibleRows) {
            scrollbarState
                    .contentLength(shown)
                    .viewportContentLength(visibleRows)
                    .position(sel != null ? sel : 0);
            frame.renderStatefulWidget(Scrollbar.builder().build(), popup, scrollbarState);
        }
    }

    private void rebuildList() {
        if (allEntries == null) {
            filteredEntries = List.of();
            return;
        }
        if (!filter.hasFilter()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            filteredEntries = new ArrayList<>();
            String f = filter.filter();
            for (TocEntry entry : allEntries) {
                boolean match;
                if (f.length() <= 2) {
                    match = entry.title().toLowerCase().contains(f);
                } else {
                    match = filter.match(entry.title()) != null;
                }
                if (match) {
                    filteredEntries.add(entry);
                }
            }
        }
        listState.select(filteredEntries.isEmpty() ? null : 0);
    }

    static List<TocEntry> extractHeadings(String markdown) {
        List<TocEntry> entries = new ArrayList<>();
        if (markdown == null) {
            return entries;
        }
        int offset = 0;
        for (String line : markdown.split("\n", -1)) {
            if (line.startsWith("### ")) {
                entries.add(new TocEntry(3, line.substring(4).trim(), offset));
            } else if (line.startsWith("## ")) {
                entries.add(new TocEntry(2, line.substring(3).trim(), offset));
            }
            offset += line.length() + 1;
        }
        return entries;
    }
}
