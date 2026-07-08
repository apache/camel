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

class GotoTabPopup {

    private boolean visible;
    private final FuzzyFilter filter = new FuzzyFilter();
    private final ListState listState = new ListState();
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private List<TabRegistry.TabEntry> allEntries;
    private List<TabRegistry.TabEntry> filteredEntries;
    private Rect popupRect;
    private Runnable callback;
    private TabRegistry.TabEntry pendingEntry;

    void setTabEntries(List<TabRegistry.TabEntry> entries, Runnable callback) {
        this.allEntries = entries;
        this.callback = callback;
    }

    boolean isVisible() {
        return visible;
    }

    void open() {
        visible = true;
        filter.clearFilter();
        rebuildList();
    }

    void close() {
        visible = false;
        filter.clearFilter();
    }

    TabRegistry.TabEntry consumePendingEntry() {
        TabRegistry.TabEntry e = pendingEntry;
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
                TabRegistry.TabEntry entry = filteredEntries.get(sel);
                close();
                navigateToEntry(entry);
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
        int nameColWidth = 18;
        int popupW = Math.min(100, area.width() - 4);
        int descColWidth = popupW - nameColWidth - 8;
        int contentH = filteredEntries.size() + 2;
        int popupH = Math.min(contentH + 2, area.height() - 4);
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
        for (TabRegistry.TabEntry entry : filteredEntries) {
            List<Span> spans = new ArrayList<>();
            String sc = entry.shortcut();
            spans.add(Span.raw(" "));
            spans.add(Span.styled(sc, Theme.mnemonic()));
            spans.add(Span.raw(" " + entry.icon() + " "));
            String name = entry.name();
            if (name.length() > nameColWidth) {
                name = name.substring(0, nameColWidth);
            } else {
                name = String.format("%-" + nameColWidth + "s", name);
            }
            if (filter.hasFilter()) {
                int[] nameMatch = FuzzyFilter.fuzzyMatch(name, filter.filter());
                if (nameMatch != null) {
                    Line hl = FuzzyFilter.highlightLine(name, nameMatch, normalStyle, matchStyle);
                    spans.addAll(hl.spans());
                } else {
                    spans.add(Span.styled(name, normalStyle));
                }
            } else {
                spans.add(Span.styled(name, normalStyle));
            }
            String desc = entry.description();
            if (desc != null) {
                if (desc.length() > descColWidth) {
                    desc = desc.substring(0, Math.max(0, descColWidth - 1)) + "…";
                }
                spans.add(Span.styled(" ", dimStyle));
                if (filter.hasFilter() && filter.filter().length() >= 2) {
                    String f = filter.filter();
                    int idx = desc.toLowerCase().indexOf(f);
                    if (idx >= 0) {
                        if (idx > 0) {
                            spans.add(Span.styled(desc.substring(0, idx), dimStyle));
                        }
                        spans.add(Span.styled(desc.substring(idx, idx + f.length()), matchStyle));
                        if (idx + f.length() < desc.length()) {
                            spans.add(Span.styled(desc.substring(idx + f.length()), dimStyle));
                        }
                    } else {
                        spans.add(Span.styled(desc, dimStyle));
                    }
                } else {
                    spans.add(Span.styled(desc, dimStyle));
                }
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
        String title = shown == total
                ? " Go to Tab (" + total + ") "
                : " Go to Tab (" + shown + "/" + total + ") ";

        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title)
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

    SelectionContext getSelectionContext() {
        if (!visible || filteredEntries == null || filteredEntries.isEmpty()) {
            return null;
        }
        List<String> items = filteredEntries.stream().map(TabRegistry.TabEntry::name).toList();
        Integer sel = listState.selected();
        return new SelectionContext("popup", items, sel != null ? sel : -1, items.size(), "Go to Tab");
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
            for (TabRegistry.TabEntry entry : allEntries) {
                boolean nameMatch;
                if (f.length() == 1) {
                    nameMatch = entry.name().toLowerCase().startsWith(f);
                } else {
                    nameMatch = filter.match(entry.name()) != null;
                }
                boolean descMatch = f.length() >= 2 && entry.description() != null
                        && entry.description().toLowerCase().contains(f);
                if (nameMatch || descMatch) {
                    filteredEntries.add(entry);
                }
            }
        }
        listState.select(filteredEntries.isEmpty() ? null : 0);
    }

    private void navigateToEntry(TabRegistry.TabEntry entry) {
        if (callback != null) {
            pendingEntry = entry;
            callback.run();
        }
    }
}
