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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
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
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors;

class GotoSourceNodePopup {

    private boolean visible;
    private final FuzzyFilter filter = new FuzzyFilter();
    private final ListState listState = new ListState();
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private List<YamlRouteNodeScanner.NodeEntry> allEntries;
    private List<YamlRouteNodeScanner.NodeEntry> filteredEntries;
    private YamlRouteNodeScanner.NodeEntry selectedEntry;
    private int gotoLineNumber = -1;
    private int totalLineCount;

    boolean isVisible() {
        return visible;
    }

    void open(List<YamlRouteNodeScanner.NodeEntry> entries, int lineCount) {
        allEntries = entries != null ? new ArrayList<>(entries) : List.of();
        totalLineCount = lineCount;
        visible = true;
        filter.clearFilter();
        gotoLineNumber = -1;
        rebuildList();
    }

    void close() {
        visible = false;
        filter.clearFilter();
        gotoLineNumber = -1;
    }

    YamlRouteNodeScanner.NodeEntry consumeSelection() {
        YamlRouteNodeScanner.NodeEntry entry = selectedEntry;
        selectedEntry = null;
        return entry;
    }

    int consumeGotoLineNumber() {
        int line = gotoLineNumber;
        gotoLineNumber = -1;
        return line;
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
            if (filter.hasFilter() && isLineNumber(filter.filter())) {
                int num = Integer.parseInt(filter.filter().trim());
                gotoLineNumber = Math.max(1, Math.min(num, totalLineCount));
                visible = false;
                filter.clearFilter();
                return true;
            }
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
        int popupW = Math.min(90, area.width() - 4);
        int contentH = filteredEntries.size() + 2;
        int maxH = area.height() - 4;
        int popupH = contentH + 2 <= maxH ? contentH + 2 : Math.min(contentH + 2, maxH - 6);
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        String filterText = filter.hasFilter() ? filter.filter() : "";
        boolean lineNumberMode = filter.hasFilter() && isLineNumber(filterText);
        String prompt = "> " + filterText + "█";

        List<ListItem> items = new ArrayList<>();
        if (lineNumberMode) {
            items.add(ListItem.from(Line.from(
                    Span.styled(prompt, Theme.info()),
                    Span.styled("  Go to line " + filterText.trim(), Style.EMPTY.dim()))));
        } else {
            items.add(ListItem.from(Line.from(Span.styled(prompt, Theme.info()))));
        }
        String sep = "─".repeat(Math.max(1, popupW - 2));
        items.add(ListItem.from(Line.from(Span.styled(sep, Style.EMPTY.dim()))));

        int maxTypeW = 0;
        for (YamlRouteNodeScanner.NodeEntry entry : filteredEntries) {
            if (entry.kind() == YamlRouteNodeScanner.EntryKind.PROCESSOR) {
                maxTypeW = Math.max(maxTypeW, entry.type().length());
            }
        }
        int maxLabelW = Math.max(10, popupW - maxTypeW - 14);

        Style normalStyle = Style.EMPTY;
        Style matchStyle = Theme.label().bold();
        Style dimStyle = Style.EMPTY.dim();
        Style treeStyle = Style.EMPTY.dim();
        Style routeStyle = Theme.label().bold();

        for (int idx = 0; idx < filteredEntries.size(); idx++) {
            YamlRouteNodeScanner.NodeEntry entry = filteredEntries.get(idx);
            List<Span> spans = new ArrayList<>();

            if (entry.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                // route[routeId]  from[uri]
                spans.add(Span.raw(" "));
                String searchable = "route[" + entry.routeId() + "]";
                Color routeColor = DiagramColors.getEipColor("route");
                if (filter.hasFilter()) {
                    int[] match = FuzzyFilter.fuzzyMatch(searchable, filter.filter());
                    if (match != null) {
                        spans.addAll(FuzzyFilter.highlightLine(searchable, match, routeStyle, matchStyle).spans());
                    } else {
                        spans.add(Span.styled(searchable, routeStyle));
                    }
                } else {
                    spans.add(Span.styled(searchable, routeStyle));
                }
            } else {
                // tree prefix + type[label] matching diagram panel format
                String prefix = buildTreePrefix(filteredEntries, idx, entry);
                spans.add(Span.styled(prefix, treeStyle));
                String typeTag = entry.type();
                Color eipColor = DiagramColors.getEipColor(SourceViewer.dashToCamelCase(typeTag));
                String label = entry.label().isBlank() ? "" : entry.label();
                if (label.length() > maxLabelW && maxLabelW > 3) {
                    label = label.substring(0, maxLabelW - 1) + "…";
                }
                String searchable = typeTag + "[" + label + "]";

                if (filter.hasFilter()) {
                    int[] nameMatch = FuzzyFilter.fuzzyMatch(searchable, filter.filter());
                    if (nameMatch != null) {
                        Line hl = FuzzyFilter.highlightLine(searchable, nameMatch,
                                Style.EMPTY.fg(eipColor), matchStyle);
                        spans.addAll(hl.spans());
                    } else {
                        spans.add(Span.styled(searchable, Style.EMPTY.fg(eipColor)));
                    }
                } else {
                    spans.add(Span.styled(searchable, Style.EMPTY.fg(eipColor)));
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
        String title;
        if (lineNumberMode) {
            title = " Go to Line ";
        } else if (total == 0) {
            title = " Go to Line (type a line number) ";
        } else if (shown == total) {
            title = " Go to Node (" + total + ") ";
        } else {
            title = " Go to Node (" + shown + "/" + total + ") ";
        }

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

    private static String buildTreePrefix(
            List<YamlRouteNodeScanner.NodeEntry> entries, int idx, YamlRouteNodeScanner.NodeEntry entry) {
        // Normalize indent to sequential depth within the route group
        int depth = normalizeDepth(entries, idx, entry);
        boolean last = isLastSiblingNorm(entries, idx, entry.indent());
        StringBuilder sb = new StringBuilder(" ");
        for (int d = 1; d < depth; d++) {
            sb.append(hasAncestorSiblingNorm(entries, idx, d, entries) ? "│ " : "  ");
        }
        if (depth > 0) {
            sb.append(last ? "└─" : "├─");
        }
        return sb.toString();
    }

    private static int normalizeDepth(
            List<YamlRouteNodeScanner.NodeEntry> entries, int idx,
            YamlRouteNodeScanner.NodeEntry entry) {
        // Walk backwards to find ancestor chain and count distinct indent levels
        int depth = 1;
        int curIndent = entry.indent();
        for (int i = idx - 1; i >= 0; i--) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                break;
            }
            if (e.indent() < curIndent) {
                depth++;
                curIndent = e.indent();
            }
        }
        return depth;
    }

    private static boolean isLastSiblingNorm(List<YamlRouteNodeScanner.NodeEntry> entries, int idx, int rawIndent) {
        for (int i = idx + 1; i < entries.size(); i++) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                return true;
            }
            if (e.indent() < rawIndent) {
                return true;
            }
            if (e.indent() == rawIndent) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAncestorSiblingNorm(
            List<YamlRouteNodeScanner.NodeEntry> entries, int idx, int normDepth,
            List<YamlRouteNodeScanner.NodeEntry> allEntries) {
        // Find the raw indent that corresponds to normDepth for this entry's route
        int rawIndent = findRawIndentForDepth(entries, idx, normDepth);
        if (rawIndent < 0) {
            return false;
        }
        for (int i = idx + 1; i < entries.size(); i++) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                return false;
            }
            if (e.indent() < rawIndent) {
                return false;
            }
            if (e.indent() == rawIndent) {
                return true;
            }
        }
        return false;
    }

    private static int findRawIndentForDepth(List<YamlRouteNodeScanner.NodeEntry> entries, int idx, int targetDepth) {
        // Walk backwards collecting ancestor indent levels
        java.util.List<Integer> indents = new java.util.ArrayList<>();
        int curIndent = entries.get(idx).indent();
        indents.add(curIndent);
        for (int i = idx - 1; i >= 0; i--) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                break;
            }
            if (e.indent() < curIndent) {
                indents.add(e.indent());
                curIndent = e.indent();
            }
        }
        java.util.Collections.reverse(indents);
        if (targetDepth > 0 && targetDepth <= indents.size()) {
            return indents.get(targetDepth - 1);
        }
        return -1;
    }

    private static boolean isLineNumber(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String shortFileName(String filePath) {
        int lastSep = filePath.lastIndexOf('/');
        return lastSep >= 0 ? filePath.substring(lastSep + 1) : filePath;
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
            String f = filter.filter().toLowerCase();
            Map<String, Boolean> routeMatched = new LinkedHashMap<>();

            for (YamlRouteNodeScanner.NodeEntry entry : allEntries) {
                if (entry.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                    routeMatched.put(routeKey(entry), matches(entry, f));
                }
            }

            for (YamlRouteNodeScanner.NodeEntry entry : allEntries) {
                String key = routeKey(entry);
                if (entry.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                    if (Boolean.TRUE.equals(routeMatched.get(key)) || hasMatchingProcessor(entry, f)) {
                        filteredEntries.add(entry);
                    }
                } else if (Boolean.TRUE.equals(routeMatched.get(key)) || matches(entry, f)) {
                    filteredEntries.add(entry);
                }
            }
        }
        listState.select(filteredEntries.isEmpty() ? null : 0);
    }

    private boolean hasMatchingProcessor(YamlRouteNodeScanner.NodeEntry routeEntry, String f) {
        String key = routeKey(routeEntry);
        for (YamlRouteNodeScanner.NodeEntry entry : allEntries) {
            if (entry.kind() == YamlRouteNodeScanner.EntryKind.PROCESSOR
                    && routeKey(entry).equals(key)
                    && matches(entry, f)) {
                return true;
            }
        }
        return false;
    }

    private static String routeKey(YamlRouteNodeScanner.NodeEntry entry) {
        return entry.filePath() + "#" + entry.routeFromLine();
    }

    private boolean matches(YamlRouteNodeScanner.NodeEntry entry, String f) {
        String searchText = entry.kind() == YamlRouteNodeScanner.EntryKind.ROUTE
                ? entry.routeId() + " " + entry.fromUri()
                : entry.type() + " " + entry.label() + " " + entry.routeId();
        if (f.length() <= 2) {
            return searchText.toLowerCase().contains(f);
        }
        return filter.match(searchText) != null;
    }
}
