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
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
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
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;

class AutocompletePopup {

    record CompletionItem(String key, String description, String type, Object defaultValue,
            boolean deprecated, String deprecationNote, String group, boolean required) {

        CompletionItem(String key, String description, String type, Object defaultValue,
                       boolean deprecated, String deprecationNote, String group) {
            this(key, description, type, defaultValue, deprecated, deprecationNote, group, false);
        }
    }

    @FunctionalInterface
    interface AutocompleteProvider {
        List<CompletionItem> provide(String linePrefix);
    }

    @FunctionalInterface
    interface ValueProvider {
        List<CompletionItem> provide(String key);
    }

    enum Result {
        CONSUMED,
        CLOSED,
        CURSOR_RIGHT,
        CURSOR_LEFT
    }

    private final ListState listState = new ListState();
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private final FuzzyFilter filter = new FuzzyFilter();
    private final List<CompletionItem> allItems;
    private final String lineKeyText;
    private final int minCursorPos;
    private int cursorPos;
    private final boolean valueMode;
    private List<CompletionItem> filteredItems;
    private CompletionItem selectedItem;
    private Rect popupRect;
    private String titlePrefix;

    AutocompletePopup(List<CompletionItem> items, String initialPrefix, String lineKeyText) {
        this(items, initialPrefix, lineKeyText, false);
    }

    AutocompletePopup(List<CompletionItem> items, String initialPrefix, String lineKeyText, boolean valueMode) {
        this.allItems = items;
        this.lineKeyText = lineKeyText != null ? lineKeyText : "";
        this.valueMode = valueMode;
        this.cursorPos = initialPrefix != null ? initialPrefix.length() : 0;
        // cursor can't go left past the group prefix (last dot in initial prefix)
        int lastDot = initialPrefix != null ? initialPrefix.lastIndexOf('.') : -1;
        this.minCursorPos = lastDot >= 0 ? lastDot + 1 : 0;
        if (initialPrefix != null && !initialPrefix.isEmpty()) {
            for (char c : initialPrefix.toCharArray()) {
                filter.appendChar(c);
            }
        }
        rebuildList();
    }

    boolean isOpen() {
        return true;
    }

    CompletionItem consumeSelectedItem() {
        CompletionItem item = selectedItem;
        selectedItem = null;
        return item;
    }

    Result handleKeyEvent(KeyEvent ke) {
        int size = filteredItems != null ? filteredItems.size() : 0;

        if (ke.isCancel()) {
            return Result.CLOSED;
        }
        if (ke.isUp()) {
            listState.selectPrevious();
            return Result.CONSUMED;
        }
        if (ke.isDown()) {
            listState.selectNext(size);
            return Result.CONSUMED;
        }
        if (ke.isRight()) {
            if (cursorPos < lineKeyText.length()) {
                filter.appendChar(lineKeyText.charAt(cursorPos));
                cursorPos++;
                rebuildList();
                return Result.CURSOR_RIGHT;
            }
            return Result.CONSUMED;
        }
        if (ke.isLeft()) {
            if (cursorPos > minCursorPos && filter.hasFilter()) {
                filter.deleteChar();
                cursorPos--;
                rebuildList();
                return Result.CURSOR_LEFT;
            }
            return Result.CONSUMED;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 10; i++) {
                listState.selectPrevious();
            }
            return Result.CONSUMED;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < 10; i++) {
                listState.selectNext(size);
            }
            return Result.CONSUMED;
        }
        if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            listState.selectFirst();
            return Result.CONSUMED;
        }
        if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            listState.selectLast(size);
            return Result.CONSUMED;
        }
        if (ke.isConfirm()) {
            Integer sel = listState.selected();
            if (sel != null && filteredItems != null && sel < filteredItems.size()) {
                selectedItem = filteredItems.get(sel);
            }
            return Result.CLOSED;
        }
        if (ke.isKey(KeyCode.BACKSPACE)) {
            if (filter.hasFilter()) {
                filter.deleteChar();
                rebuildList();
                return Result.CONSUMED;
            }
            return Result.CLOSED;
        }
        if (ke.code() == KeyCode.CHAR && !ke.hasCtrl() && !ke.hasAlt()) {
            filter.appendChar(ke.string().charAt(0));
            List<CompletionItem> prev = filteredItems;
            rebuildList();
            if (filteredItems.isEmpty()) {
                // undo: keep current list instead of showing empty
                filter.deleteChar();
                filteredItems = prev;
                listState.select(prev != null && !prev.isEmpty() ? 0 : null);
            }
            return Result.CONSUMED;
        }
        return Result.CONSUMED;
    }

    Result handleMouseEvent(MouseEvent me) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            listState.selectPrevious();
            return Result.CONSUMED;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            int size = filteredItems != null ? filteredItems.size() : 0;
            listState.selectNext(size);
            return Result.CONSUMED;
        }
        if (me.isClick()) {
            if (popupRect != null && popupRect.contains(me.x(), me.y())) {
                int idx = TuiHelper.listItemAt(popupRect, 0,
                        (filteredItems != null ? filteredItems.size() : 0) + 2,
                        me.x(), me.y());
                if (idx >= 2 && filteredItems != null && idx - 2 < filteredItems.size()) {
                    listState.select(idx - 2);
                    selectedItem = filteredItems.get(idx - 2);
                    return Result.CLOSED;
                }
                return Result.CONSUMED;
            }
            return Result.CLOSED;
        }
        return Result.CONSUMED;
    }

    void render(Frame frame, Rect area, int cursorScreenRow, int cursorScreenCol) {
        if (filteredItems == null || filteredItems.isEmpty()) {
            return;
        }

        int popupW = Math.max(70, area.width() - 4);
        int contentH = filteredItems.size() + 2;
        int maxH = area.height() - 2;
        int popupH = Math.min(contentH + 2, maxH);
        popupH = Math.max(popupH, 12);

        // in value mode, ensure enough height for the detail panel description
        if (valueMode) {
            Integer sel = listState.selected();
            if (sel != null && sel < filteredItems.size()) {
                CompletionItem item = filteredItems.get(sel);
                if (item.description() != null) {
                    int rightW = popupW - Math.max(30, popupW * 2 / 5);
                    int descWidth = Math.max(20, rightW - 4);
                    int metaLines = 6;
                    int descLines = (item.description().length() + descWidth - 1) / descWidth;
                    int neededH = metaLines + descLines + 4;
                    popupH = Math.max(popupH, Math.min(neededH, maxH));
                }
            }
        }

        int x = area.left() + 2;
        int y;
        int spaceBelow = area.bottom() - (area.top() + cursorScreenRow + 1);
        int spaceAbove = cursorScreenRow;
        if (valueMode && popupH > Math.max(spaceBelow, spaceAbove)) {
            // value mode with long description: use full area height, position at top
            popupH = Math.min(popupH, area.height());
            y = area.top();
        } else if (spaceBelow >= popupH || spaceBelow >= spaceAbove) {
            y = area.top() + cursorScreenRow + 1;
            popupH = Math.min(popupH, Math.max(8, spaceBelow));
        } else {
            popupH = Math.min(popupH, Math.max(8, spaceAbove));
            y = area.top() + cursorScreenRow - popupH;
        }

        Rect popup = new Rect(
                x, Math.max(area.top(), y),
                Math.min(popupW, area.width()), Math.min(popupH, area.height()));
        this.popupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);

        int leftW = Math.max(30, popup.width() * 2 / 5);
        int rightW = popup.width() - leftW;

        Rect leftRect = new Rect(popup.x(), popup.y(), leftW, popup.height());
        Rect rightRect = new Rect(popup.x() + leftW, popup.y(), rightW, popup.height());

        renderList(frame, leftRect);
        renderDetail(frame, rightRect);
    }

    private void renderList(Frame frame, Rect listRect) {
        String filterText = filter.hasFilter() ? filter.filter() : "";
        String prompt = "> " + filterText + "█";

        int nameColW = listRect.width() - 4;

        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.from(Line.from(Span.styled(prompt, Theme.info()))));
        String sep = "─".repeat(Math.max(1, listRect.width() - 2));
        items.add(ListItem.from(Line.from(Span.styled(sep, Style.EMPTY.dim()))));

        Style normalStyle = Style.EMPTY;
        Style boldStyle = Style.EMPTY.bold();
        Style dimStyle = Style.EMPTY.dim();
        Style deprecatedStyle = Style.EMPTY.dim().crossedOut();

        for (CompletionItem ci : filteredItems) {
            List<Span> spans = new ArrayList<>();

            if (ci.deprecated()) {
                spans.add(Span.styled(" ✘ ", dimStyle));
            } else if (ci.required()) {
                spans.add(Span.styled(" * ", boldStyle));
            } else {
                spans.add(Span.raw("   "));
            }

            String key = ci.key();
            Style keyStyle = ci.deprecated() ? deprecatedStyle : ci.required() ? boldStyle : normalStyle;

            String displayKey = key;
            if (!key.startsWith("{{") && !key.endsWith(".")) {
                int lastDot = key.lastIndexOf('.');
                if (lastDot >= 0) {
                    displayKey = key.substring(lastDot + 1);
                }
            }
            if (displayKey.length() > nameColW - 10) {
                displayKey = displayKey.substring(0, Math.max(1, nameColW - 11)) + "…";
            }
            spans.add(Span.styled(displayKey, keyStyle));

            if (ci.type() != null) {
                String type = simplifyType(ci.type());
                if ("object".equals(type) && ci.group() != null && !ci.group().isEmpty()) {
                    String label = primaryLabel(ci.group());
                    if (label != null) {
                        type = label;
                    }
                }
                int remaining = nameColW - displayKey.length() - 3;
                if (remaining > 3 && type.length() <= remaining) {
                    int pad = remaining - type.length();
                    spans.add(Span.styled(" ".repeat(pad + 1), dimStyle));
                    spans.add(Span.styled(type, dimStyle));
                }
            }

            items.add(ListItem.from(Line.from(spans)));
        }

        ListState renderState = new ListState();
        Integer sel = listState.selected();
        if (sel != null) {
            renderState.select(sel + 2);
        }

        int total = allItems.size();
        int shown = filteredItems.size();
        String label = titlePrefix != null ? titlePrefix : "Completions";
        String title = shown == total
                ? " " + label + " (" + total + ") "
                : " " + label + " (" + shown + "/" + total + ") ";

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
        frame.renderStatefulWidget(list, listRect, renderState);

        int visibleRows = Math.max(1, listRect.height() - 2);
        if (shown + 2 > visibleRows) {
            scrollbarState
                    .contentLength(shown)
                    .viewportContentLength(visibleRows)
                    .position(sel != null ? sel : 0);
            frame.renderStatefulWidget(Scrollbar.builder().build(), listRect, scrollbarState);
        }
    }

    private void renderDetail(Frame frame, Rect detailRect) {
        Integer sel = listState.selected();
        CompletionItem selected = null;
        if (sel != null && filteredItems != null && sel < filteredItems.size()) {
            selected = filteredItems.get(sel);
        }

        Style normalStyle = Style.EMPTY;
        Style dimStyle = Style.EMPTY.dim();
        List<Line> lines = new ArrayList<>();

        if (selected != null) {
            lines.add(Line.from(Span.styled(selected.key(), Theme.label().bold())));
            lines.add(Line.empty());

            if (selected.type() != null) {
                String typeDisplay = simplifyType(selected.type());
                if ("object".equals(typeDisplay) && selected.group() != null && !selected.group().isEmpty()) {
                    String label = primaryLabel(selected.group());
                    if (label != null) {
                        lines.add(Line.from(
                                Span.styled("Category: ", normalStyle.bold()),
                                Span.styled(label, normalStyle)));
                        typeDisplay = null;
                    }
                }
                if (typeDisplay != null) {
                    lines.add(Line.from(
                            Span.styled("Type: ", normalStyle.bold()),
                            Span.styled(typeDisplay, normalStyle)));
                }
            }
            if (selected.defaultValue() != null) {
                lines.add(Line.from(
                        Span.styled("Default: ", normalStyle.bold()),
                        Span.styled(String.valueOf(selected.defaultValue()), normalStyle)));
            }
            if (selected.group() != null && !selected.group().isEmpty()) {
                lines.add(Line.from(
                        Span.styled("Group: ", normalStyle.bold()),
                        Span.styled(selected.group(), normalStyle)));
            }
            if (selected.required()) {
                lines.add(Line.from(
                        Span.styled("Required: ", normalStyle.bold()),
                        Span.styled("true", Theme.info())));
            }
            if (selected.deprecated()) {
                String depText = "Deprecated";
                if (selected.deprecationNote() != null && !selected.deprecationNote().isEmpty()) {
                    depText += ": " + selected.deprecationNote();
                }
                lines.add(Line.from(Span.styled(depText, Theme.error().italic())));
            }
            if (selected.description() != null) {
                lines.add(Line.empty());
                lines.add(Line.from(Span.styled(selected.description(), dimStyle)));
            }
        }

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Details ")
                .build();
        frame.renderWidget(block, detailRect);

        Rect inner = block.inner(detailRect);
        if (!lines.isEmpty()) {
            Paragraph detail = Paragraph.builder()
                    .text(Text.from(lines))
                    .overflow(Overflow.WRAP_WORD)
                    .build();
            frame.renderWidget(detail, inner);
        }
    }

    private static String simplifyType(String type) {
        if (type == null) {
            return "";
        }
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    private static final String[][] LABEL_PRIORITY = {
            { "errorhandling", "error handling" },
            { "resilience", "resilience" },
            { "loadbalancing", "load balancing" },
            { "flowcontrol", "flow control" },
            { "enrichment", "enrichment" },
            { "transformation", "transformation" },
            { "dataformat", "data format" },
            { "messaging", "messaging" },
            { "ai", "ai" },
            { "routing", "routing" },
            { "rest", "rest" },
            { "configuration", "configuration" },
            { "monitoring", "monitoring" },
            { "security", "security" },
            { "health", "health" },
            { "validation", "validation" },
            { "endpoint", "endpoint" },
            { "language", "language" },
    };

    static String primaryLabel(String group) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        String[] parts = group.split(",");
        for (String[] entry : LABEL_PRIORITY) {
            for (String part : parts) {
                if (entry[0].equals(part.trim())) {
                    return entry[1];
                }
            }
        }
        return null;
    }

    boolean isValueMode() {
        return valueMode;
    }

    boolean hasItems() {
        return filteredItems != null && !filteredItems.isEmpty();
    }

    boolean hasFilter() {
        return filter.hasFilter();
    }

    void setTitlePrefix(String titlePrefix) {
        this.titlePrefix = titlePrefix;
    }

    private boolean listItemInsertion;

    void setListItemInsertion(boolean listItemInsertion) {
        this.listItemInsertion = listItemInsertion;
    }

    boolean isListItemInsertion() {
        return listItemInsertion;
    }

    private void rebuildList() {
        if (!filter.hasFilter()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            filteredItems = new ArrayList<>();
            String f = filter.filter();
            for (CompletionItem item : allItems) {
                if (FuzzyFilter.camelCaseMatch(item.key(), f) || matchesLabel(item.group(), f)) {
                    filteredItems.add(item);
                }
            }
        }
        listState.select(filteredItems.isEmpty() ? null : 0);
    }

    private static boolean matchesLabel(String group, String filter) {
        if (group == null || group.isEmpty()) {
            return false;
        }
        for (String label : group.split(",")) {
            if (FuzzyFilter.camelCaseMatch(label.trim(), filter)) {
                return true;
            }
        }
        return false;
    }
}
