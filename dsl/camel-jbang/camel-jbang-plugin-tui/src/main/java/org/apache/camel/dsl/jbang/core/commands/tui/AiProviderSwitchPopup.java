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

final class AiProviderSwitchPopup {

    record ProviderChoice(String provider, String model, String url, boolean persistedDefault) {
        ProviderChoice {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("provider must not be blank");
            }
        }

        String label() {
            String modelLabel = model == null || model.isBlank() ? "auto" : model;
            return provider + "  " + modelLabel + (persistedDefault ? "  default" : "");
        }
    }

    private boolean visible;
    private List<ProviderChoice> choices = List.of();
    private final ListState listState = new ListState();
    private ProviderChoice pendingChoice;
    private Rect popupRect;

    boolean isVisible() {
        return visible;
    }

    void open(List<ProviderChoice> choices) {
        this.choices = new ArrayList<>(choices);
        listState.selectFirst();
        pendingChoice = null;
        visible = true;
    }

    void close() {
        visible = false;
    }

    ProviderChoice consumePendingChoice() {
        ProviderChoice choice = pendingChoice;
        pendingChoice = null;
        return choice;
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
                pendingChoice = choices.get(selected);
                close();
            }
        }
    }

    void render(Frame frame, Rect area) {
        int popupW = Math.max(1, Math.min(72, area.width() - 4));
        int popupH = Math.max(1, Math.min(choices.size() + 2, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 4);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));
        this.popupRect = popup;
        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" AI Provider ")
                .build();
        frame.renderWidget(block, popup);

        List<ListItem> items = new ArrayList<>();
        for (ProviderChoice choice : choices) {
            items.add(ListItem.from(Line.from(Span.styled(" " + choice.label(), Style.EMPTY))));
        }
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .build();
        frame.renderStatefulWidget(list, block.inner(popup), listState);
    }
}
