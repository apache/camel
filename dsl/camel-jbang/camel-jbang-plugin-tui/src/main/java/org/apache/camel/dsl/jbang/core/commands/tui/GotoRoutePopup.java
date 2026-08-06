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

class GotoRoutePopup {

    private boolean visible;
    private final FuzzyFilter filter = new FuzzyFilter();
    private final ListState listState = new ListState();
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private List<RouteItem> allEntries;
    private List<RouteItem> filteredEntries;
    private RouteItem selectedEntry;

    record RouteItem(String routeId, String fromUri, String filePath, int fromLine) {
    }

    boolean isVisible() {
        return visible;
    }

    void open(List<SourceTab.RouteEntry> routeIndex) {
        allEntries = new ArrayList<>();
        for (SourceTab.RouteEntry re : routeIndex) {
            allEntries.add(new RouteItem(re.routeId(), re.fromUri(), re.filePath(), re.fromLine()));
        }
        visible = true;
        filter.clearFilter();
        rebuildList();
    }

    void close() {
        visible = false;
        filter.clearFilter();
    }

    RouteItem consumeSelection() {
        RouteItem entry = selectedEntry;
        selectedEntry = null;
        return entry;
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
                selectedEntry = filteredEntries.get(sel);
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

    void render(Frame frame, Rect area) {
        if (filteredEntries == null) {
            return;
        }
        int popupW = Math.min(80, area.width() - 4);
        int contentH = filteredEntries.size() + 2;
        int maxH = area.height() - 4;
        int popupH = contentH + 2 <= maxH ? contentH + 2 : Math.min(contentH + 2, maxH - 6);
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        String filterText = filter.hasFilter() ? filter.filter() : "";
        String prompt = "> " + filterText + "█";

        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.from(Line.from(Span.styled(prompt, Theme.info()))));
        String sep = "─".repeat(Math.max(1, popupW - 2));
        items.add(ListItem.from(Line.from(Span.styled(sep, Style.EMPTY.dim()))));

        // compute column widths for alignment
        int maxIdW = 0;
        int maxUriW = 0;
        for (RouteItem entry : filteredEntries) {
            maxIdW = Math.max(maxIdW, entry.routeId().length());
            maxUriW = Math.max(maxUriW, entry.fromUri().length());
        }

        Style normalStyle = Style.EMPTY;
        Style matchStyle = Theme.label().bold();
        Style dimStyle = Style.EMPTY.dim();
        for (RouteItem entry : filteredEntries) {
            List<Span> spans = new ArrayList<>();
            spans.add(Span.raw(" "));

            String routeId = entry.routeId();
            String padded = routeId + " ".repeat(Math.max(0, maxIdW - routeId.length()));

            if (filter.hasFilter()) {
                int[] nameMatch = FuzzyFilter.fuzzyMatch(routeId, filter.filter());
                if (nameMatch != null) {
                    Line hl = FuzzyFilter.highlightLine(routeId, nameMatch, normalStyle, matchStyle);
                    spans.addAll(hl.spans());
                    spans.add(Span.raw(" ".repeat(Math.max(0, maxIdW - routeId.length()))));
                } else {
                    spans.add(Span.styled(padded, normalStyle));
                }
            } else {
                spans.add(Span.styled(padded, normalStyle));
            }

            String uri = entry.fromUri();
            String paddedUri = uri + " ".repeat(Math.max(0, maxUriW - uri.length()));
            spans.add(Span.styled("  " + paddedUri, dimStyle));

            String fileName = entry.filePath();
            int lastSep = fileName.lastIndexOf('/');
            if (lastSep >= 0) {
                fileName = fileName.substring(lastSep + 1);
            }
            spans.add(Span.styled("  " + fileName + ":" + (entry.fromLine() + 1), dimStyle));
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
                ? " Go to Route (" + total + ") "
                : " Go to Route (" + shown + "/" + total + ") ";

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
            for (RouteItem entry : allEntries) {
                String searchText = entry.routeId() + " " + entry.fromUri();
                boolean matches;
                if (f.length() <= 2) {
                    matches = searchText.toLowerCase().contains(f);
                } else {
                    matches = filter.match(searchText) != null;
                }
                if (matches) {
                    filteredEntries.add(entry);
                }
            }
        }
        listState.select(filteredEntries.isEmpty() ? null : 0);
    }
}
