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
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;

/**
 * Reusable input history with popup selector. Stores string entries and renders a centered popup overlay for selection.
 */
class InputHistory {

    private static final int MAX_ENTRIES = 20;

    private final List<String> entries = new ArrayList<>();
    private final ListState listState = new ListState();
    private boolean popupVisible;
    private String selected;

    void add(String entry) {
        if (entry == null || entry.isBlank()) {
            return;
        }
        entries.remove(entry);
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    boolean isPopupVisible() {
        return popupVisible;
    }

    void showPopup() {
        popupVisible = true;
        listState.select(0);
        selected = null;
    }

    void hidePopup() {
        popupVisible = false;
        selected = null;
    }

    /**
     * Returns the entry selected on Enter, then resets. Returns null if nothing was selected.
     */
    String takeSelected() {
        String s = selected;
        selected = null;
        return s;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!popupVisible) {
            return false;
        }
        if (ke.isCancel()) {
            hidePopup();
            return true;
        }
        if (ke.isUp()) {
            listState.selectPrevious();
            return true;
        }
        if (ke.isDown()) {
            listState.selectNext(entries.size());
            return true;
        }
        if (ke.isConfirm()) {
            Integer sel = listState.selected();
            if (sel != null && sel >= 0 && sel < entries.size()) {
                selected = entries.get(sel);
            }
            popupVisible = false;
            return true;
        }
        return true;
    }

    void renderPopup(Frame frame, Rect area, String title) {
        if (!popupVisible || entries.isEmpty()) {
            return;
        }

        int maxLabelLen = 0;
        for (String q : entries) {
            String oneLine = q.replace('\n', ' ');
            maxLabelLen = Math.max(maxLabelLen, oneLine.length() + 4);
        }
        int popupW = Math.min(area.width() - 4, Math.max(30, maxLabelLen + 4));
        int popupH = Math.min(area.height() - 4, entries.size() + 2);
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        ListItem[] items = new ListItem[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            String label = entries.get(i).replace('\n', ' ');
            if (label.length() > popupW - 6) {
                label = label.substring(0, popupW - 9) + "...";
            }
            items[i] = ListItem.from(Line.from(Span.raw("  " + label)));
        }

        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(Title.from(Line.from(Span.styled(" " + title + " ", Style.EMPTY.fg(Color.YELLOW).bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }
}
