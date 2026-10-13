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
import dev.tamboui.widgets.scrollbar.ScrollbarState;

/**
 * A popup that lists entries and narrows them down as the user types: the typed text is on a prompt row above the
 * entries, Up, Down, PgUp, PgDn, Home and End select, Enter chooses the selected entry and closes, Backspace deletes,
 * Esc closes. With the mouse, the wheel moves the selection, a click on an entry chooses it and a click outside closes.
 * The title counts the entries shown, out of all.
 * <p>
 * A subclass says what its entries are and how a row looks, and what choosing an entry does: Go to Tab, Go to Node, Go
 * to Route, the go to node or line of the source editor, and the table of contents of a document.
 *
 * @param <T> the entry
 */
abstract class FilterListPopup<T> {

    /** The rows above the entries: the prompt and a separator. */
    private static final int HEADER_ROWS = 2;

    protected final FuzzyFilter filter = new FuzzyFilter();
    private final ListState listState = new ListState();
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private boolean visible;
    private List<T> shown;
    private Rect popupRect;
    // the rows the list was scrolled by in the last render, for a click
    private int listOffset;

    boolean isVisible() {
        return visible;
    }

    /** Shows the popup on the entries of {@link #entries()}, with nothing typed and the first entry selected. */
    protected final void show() {
        visible = true;
        filter.clearFilter();
        refilter();
    }

    void close() {
        visible = false;
        filter.clearFilter();
    }

    /** All entries, before the typed text narrows them down. */
    protected abstract List<T> entries();

    /** Whether an entry matches the typed text (not empty, in lower case). */
    protected abstract boolean matches(T entry, String typed);

    /** The rows of the entries shown, one per entry, for a popup of the given width. */
    protected abstract List<Line> rows(List<T> entries, int width);

    /** The name in the title, such as {@code "Go to Node"}. */
    protected abstract String name();

    /** Enter, or a click, chose this entry; the popup is closed already. */
    protected abstract void chosen(T entry);

    /** The entries that match the typed text; by default those {@link #matches(Object, String)} accepts. */
    protected List<T> filter(List<T> entries, String typed) {
        List<T> result = new ArrayList<>();
        for (T entry : entries) {
            if (matches(entry, typed)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Enter takes the typed text itself rather than the selected entry (a line number to go to); returns whether it
     * did. Called before an entry is chosen.
     */
    protected boolean confirmTyped(String typed) {
        return false;
    }

    /** The prompt row, with the typed text and a caret. */
    protected Line prompt(String typed) {
        return Line.from(Span.styled("> " + typed + "█", Theme.info()));
    }

    /** The title: the name and how many entries are shown out of all. */
    protected String title(int shownCount, int total) {
        return shownCount == total
                ? " " + name() + " (" + total + ") "
                : " " + name() + " (" + shownCount + "/" + total + ") ";
    }

    /** The widest the popup gets. */
    protected int maxWidth() {
        return 80;
    }

    /** How many entries PgUp and PgDn move by. */
    protected int pageStep() {
        return 5;
    }

    /** The height of a popup whose entries do not fit in {@code available} rows. */
    protected int overflowHeight(int available) {
        return available - 6;
    }

    /**
     * The usual match: the text contains what was typed, for one or two characters, else the typed characters appear in
     * it in order (a fuzzy match).
     */
    protected final boolean matchesText(String text, String typed) {
        if (typed.length() <= 2) {
            return text.toLowerCase().contains(typed);
        }
        return filter.match(text) != null;
    }

    /** The text with the characters the typed text matches in the match style, when it matches. */
    protected final List<Span> highlighted(String text, Style normal, Style match) {
        if (filter.hasFilter()) {
            int[] matched = FuzzyFilter.fuzzyMatch(text, filter.filter());
            if (matched != null) {
                return FuzzyFilter.highlightLine(text, matched, normal, match).spans();
            }
        }
        return List.of(Span.styled(text, normal));
    }

    /** The entries shown, those matching the typed text. */
    protected final List<T> shown() {
        return shown != null ? shown : List.of();
    }

    /** The position of the selected entry among those shown, or null. */
    protected final Integer selectedIndex() {
        return listState.selected();
    }

    /** Selects the entry at a position among those shown. */
    protected final void select(int index) {
        listState.select(index);
    }

    /** Narrows the entries down again after the typed text, or the entries, changed; selects the first. */
    protected final void refilter() {
        List<T> all = entries();
        if (all == null) {
            shown = List.of();
            return;
        }
        shown = filter.hasFilter() ? filter(all, filter.filter()) : new ArrayList<>(all);
        listState.select(shown.isEmpty() ? null : 0);
    }

    boolean handleKeyEvent(KeyEvent ke) {
        int size = shown().size();
        if (ke.isCancel()) {
            close();
        } else if (ke.isUp()) {
            listState.selectPrevious();
        } else if (ke.isDown()) {
            listState.selectNext(size);
        } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < pageStep(); i++) {
                listState.selectPrevious();
            }
        } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < pageStep(); i++) {
                listState.selectNext(size);
            }
        } else if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            listState.selectFirst();
        } else if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            listState.selectLast(size);
        } else if (ke.isConfirm()) {
            confirm();
        } else if (ke.isKey(KeyCode.BACKSPACE)) {
            filter.deleteChar();
            refilter();
        } else if (ke.code() == KeyCode.CHAR && !ke.hasCtrl() && !ke.hasAlt()) {
            filter.append(ke.string());
            refilter();
        }
        // the popup takes every key while it is open
        return true;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            listState.selectPrevious();
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            listState.selectNext(shown().size());
            return true;
        }
        if (me.isClick()) {
            if (popupRect != null && popupRect.contains(me.x(), me.y())) {
                int idx = TuiHelper.listItemAt(popupRect, listOffset, shown().size() + HEADER_ROWS, me.x(), me.y());
                if (idx >= HEADER_ROWS && idx - HEADER_ROWS < shown().size()) {
                    listState.select(idx - HEADER_ROWS);
                    confirm();
                }
                return true;
            }
            close();
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (shown == null) {
            return;
        }
        int popupW = Math.min(maxWidth(), area.width() - 4);
        int contentH = shown.size() + HEADER_ROWS;
        int maxH = area.height() - 4;
        int popupH = contentH + 2 <= maxH ? contentH + 2 : Math.min(contentH + 2, overflowHeight(maxH));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        popupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);

        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.from(prompt(filter.hasFilter() ? filter.filter() : "")));
        items.add(ListItem.from(Line.from(Span.styled("─".repeat(Math.max(1, popupW - 2)), Style.EMPTY.dim()))));
        for (Line row : rows(shown, popupW)) {
            items.add(ListItem.from(row));
        }

        ListState renderState = new ListState();
        Integer sel = listState.selected();
        if (sel != null) {
            renderState.select(sel + HEADER_ROWS);
        }
        List<T> all = entries();
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title(shown.size(), all != null ? all.size() : 0))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, renderState);
        listOffset = renderState.offset();
        TuiHelper.renderListScrollbar(frame, popup, shown.size() + HEADER_ROWS, listOffset, scrollbarState);
    }

    private void confirm() {
        if (filter.hasFilter() && confirmTyped(filter.filter())) {
            return;
        }
        Integer sel = listState.selected();
        if (sel != null && sel < shown().size()) {
            T entry = shown().get(sel);
            close();
            chosen(entry);
        }
    }
}
