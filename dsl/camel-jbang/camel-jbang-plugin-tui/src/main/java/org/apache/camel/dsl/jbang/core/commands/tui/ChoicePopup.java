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

/**
 * A small centered popup with a list of choices: Up, Down, PgUp and PgDn select, Enter chooses and closes, Esc closes.
 * The mouse wheel selects, a click on a choice chooses it, a click outside closes. After passing on a key or a click,
 * the owner reads what was chosen with {@link #consumePendingChoice()}.
 *
 * @param <C> the choice
 */
abstract class ChoicePopup<C> {

    private boolean visible;
    private List<C> choices = List.of();
    private final ListState listState = new ListState();
    private C pendingChoice;
    private Rect popupRect;

    boolean isVisible() {
        return visible;
    }

    /** Shows the choices, the first selected. */
    protected final void show(List<C> choices) {
        this.choices = new ArrayList<>(choices);
        listState.selectFirst();
        pendingChoice = null;
        visible = true;
    }

    void close() {
        visible = false;
    }

    /** What was chosen, or null; cleared by the call. */
    C consumePendingChoice() {
        C choice = pendingChoice;
        pendingChoice = null;
        return choice;
    }

    /** The title of the popup, such as {@code " Copy "}. */
    protected abstract String title();

    /** The text of a choice in the list. */
    protected abstract String label(C choice);

    /** What Enter does, for the footer, such as {@code "copy"}. */
    protected abstract String confirmLabel();

    /** Whether the digits 1 to 9 choose the choice of that number at once (the labels then start with it). */
    protected boolean pickByNumber() {
        return false;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (!visible) {
            return false;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            handleKeyEvent(KeyEvent.ofKey(KeyCode.UP));
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN));
            return true;
        }
        if (me.isClick()) {
            if (popupRect != null && popupRect.contains(me.x(), me.y())) {
                int idx = TuiHelper.listItemAt(popupRect, listState.offset(), choices.size(), me.x(), me.y());
                if (idx >= 0) {
                    listState.select(idx);
                    handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
                }
                return true;
            }
            handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
            return true;
        }
        return true;
    }

    void handleKeyEvent(KeyEvent ke) {
        if (ke.isCancel()) {
            close();
        } else if (ke.isUp()) {
            listState.selectPrevious();
        } else if (ke.isDown()) {
            listState.selectNext(choices.size());
        } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 5; i++) {
                listState.selectPrevious();
            }
        } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < 5; i++) {
                listState.selectNext(choices.size());
            }
        } else if (ke.isConfirm()) {
            Integer selected = listState.selected();
            if (selected != null && selected < choices.size()) {
                choose(selected);
            }
        } else if (pickByNumber() && Character.isDigit(ke.character())) {
            int idx = ke.character() - '1';
            if (idx >= 0 && idx < choices.size()) {
                choose(idx);
            }
        }
    }

    void render(Frame frame, Rect area) {
        int popupW = Math.max(1, Math.min(72, area.width() - 4));
        int popupH = Math.max(1, Math.min(choices.size() + 2, area.height() - 4));
        Rect popup = DialogHelper.centered(area, popupW, popupH);
        this.popupRect = popup;
        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(title())
                .build();
        frame.renderWidget(block, popup);

        List<ListItem> items = new ArrayList<>();
        for (C choice : choices) {
            items.add(ListItem.from(Line.from(Span.styled(" " + label(choice), Style.EMPTY))));
        }
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .build();
        frame.renderStatefulWidget(list, block.inner(popup), listState);
    }

    void renderFooter(List<Span> spans) {
        TuiHelper.hint(spans, "Enter", confirmLabel());
        if (pickByNumber()) {
            TuiHelper.hint(spans, "1-9", "pick");
        }
        TuiHelper.hintLast(spans, "Esc", "close");
    }

    private void choose(int index) {
        pendingChoice = choices.get(index);
        close();
    }
}
