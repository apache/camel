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

import dev.tamboui.layout.Padding;
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
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;

/**
 * Refactoring menu for the Source tab YAML editor (opened with F5 or Ctrl+R in view mode). Presents applicable
 * refactoring actions for the current cursor line and drives the name-entry prompt itself, emitting a single
 * {@link Request} for the host viewer to execute.
 */
class RefactorPopup {

    enum Action {
        EXTRACT_TO_FILE,
        REPLACE_URI,
        EXTRACT_TO_PROPERTY
    }

    /** A completed, ready-to-execute request. */
    record Request(Action action, String value) {
    }

    private record MenuItem(Action action, String icon, String label, String inputTitle, String inputPlaceholder,
            String prefilledValue) {
    }

    private enum Phase {
        MENU,
        INPUT
    }

    private boolean visible;
    private Phase phase = Phase.MENU;

    private List<MenuItem> items = List.of();
    private final ListState menuState = new ListState();

    private String inputTitle;
    private String inputPlaceholder;
    private TextInputState inputState;
    private Action inputAction;

    private Request result;

    void open(List<Action> applicable, String currentUri) {
        this.visible = true;
        this.phase = Phase.MENU;
        this.result = null;
        this.inputState = null;
        buildMenu(applicable, currentUri);
        menuState.select(items.isEmpty() ? null : 0);
    }

    void close() {
        visible = false;
        phase = Phase.MENU;
        inputState = null;
    }

    boolean isVisible() {
        return visible;
    }

    Request consumeResult() {
        Request r = result;
        result = null;
        return r;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        return switch (phase) {
            case MENU -> handleMenuKey(ke);
            case INPUT -> handleInputKey(ke);
        };
    }

    private void buildMenu(List<Action> applicable, String currentUri) {
        List<MenuItem> list = new ArrayList<>();
        for (Action action : applicable) {
            list.add(switch (action) {
                case EXTRACT_TO_FILE -> new MenuItem(
                        Action.EXTRACT_TO_FILE, "📄", "Extract to new file…",
                        "Route name", "my-sub-route", "");
                case REPLACE_URI -> new MenuItem(
                        Action.REPLACE_URI, "🔀", "Replace URI…",
                        "New URI", "component:path", currentUri != null ? currentUri : "");
                case EXTRACT_TO_PROPERTY -> new MenuItem(
                        Action.EXTRACT_TO_PROPERTY, "📦", "Extract to property…",
                        "Property key", "my.property.key", "");
            });
        }
        this.items = list;
    }

    private boolean handleMenuKey(KeyEvent ke) {
        if (ke.isCancel()) {
            close();
            return true;
        }
        if (ke.isUp()) {
            menuState.selectPrevious();
            return true;
        }
        if (ke.isDown()) {
            menuState.selectNext(items.size());
            return true;
        }
        if (ke.isConfirm()) {
            Integer sel = menuState.selected();
            if (sel != null && sel < items.size()) {
                startInput(items.get(sel));
            }
            return true;
        }
        return true;
    }

    private void startInput(MenuItem item) {
        this.inputAction = item.action();
        this.inputTitle = item.inputTitle();
        this.inputPlaceholder = item.inputPlaceholder();
        this.inputState = new TextInputState(item.prefilledValue() != null ? item.prefilledValue() : "");
        this.inputState.moveCursorToEnd();
        this.phase = Phase.INPUT;
    }

    private boolean handleInputKey(KeyEvent ke) {
        if (ke.isCancel()) {
            phase = Phase.MENU;
            inputState = null;
            return true;
        }
        if (ke.isConfirm()) {
            String text = inputState.text().trim();
            if (!text.isEmpty()) {
                result = new Request(inputAction, text);
                close();
            }
            return true;
        }
        if (ke.isDeleteBackward()) {
            inputState.deleteBackward();
        } else if (ke.isDeleteForward()) {
            inputState.deleteForward();
        } else if (ke.isLeft()) {
            inputState.moveCursorLeft();
        } else if (ke.isRight()) {
            inputState.moveCursorRight();
        } else if (ke.isHome()) {
            inputState.moveCursorToStart();
        } else if (ke.isEnd()) {
            inputState.moveCursorToEnd();
        } else if (ke.code() == KeyCode.CHAR) {
            char ch = ke.string().charAt(0);
            if (ch >= 0x20 && ch != 0x7F) {
                inputState.insert(ch);
            }
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (!visible) {
            return;
        }
        switch (phase) {
            case MENU -> renderMenu(frame, area);
            case INPUT -> renderInput(frame, area);
            default -> {
            }
        }
    }

    private void renderMenu(Frame frame, Rect area) {
        int popupW = Math.max(34, Math.min(48, area.width() - 4));
        popupW = Math.min(popupW, area.width() - 2);
        int popupH = items.size() + 4;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 3);
        Rect popup = new Rect(x, y, popupW, Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        List<ListItem> listItems = new ArrayList<>();
        for (MenuItem item : items) {
            List<Span> spans = new ArrayList<>();
            spans.add(Span.raw("  "));
            spans.add(Span.raw(item.icon()));
            spans.add(Span.raw("  "));
            spans.add(Span.raw(item.label()));
            listItems.add(ListItem.from(Line.from(spans)));
        }

        ListWidget list = ListWidget.builder()
                .items(listItems.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .borderStyle(Theme.borderFocused())
                        .padding(Padding.vertical(1))
                        .title(Title.from(Line.from(
                                Span.styled(" 🔧 Refactor ", Theme.title().bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, menuState);
    }

    private void renderInput(Frame frame, Rect area) {
        int popupW = Math.max(50, Math.min(64, area.width() - 4));
        popupW = Math.min(popupW, area.width() - 2);
        int popupH = 5;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 3);
        Rect popup = new Rect(x, y, popupW, Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(Theme.borderFocused())
                .title(Title.from(Line.from(Span.styled(" " + inputTitle + " ", Theme.title().bold()))))
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);

        int pad = 2;
        int fieldW = Math.max(1, inner.width() - 2 * pad);
        int fieldY = inner.top() + Math.max(0, (inner.height() - 1) / 2);
        Rect field = new Rect(inner.left() + pad, fieldY, fieldW, 1);

        TextInput textInput = TextInput.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .placeholder(inputPlaceholder)
                .build();
        textInput.renderWithCursor(field, frame.buffer(), inputState, frame);
    }

    void renderFooter(List<Span> spans) {
        if (!visible) {
            return;
        }
        switch (phase) {
            case MENU -> {
                TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "navigate");
                TuiHelper.hint(spans, "Enter", "select");
                TuiHelper.hintLast(spans, "Esc", "close");
            }
            case INPUT -> {
                TuiHelper.hint(spans, "Enter", "confirm");
                TuiHelper.hintLast(spans, "Esc", "back");
            }
            default -> {
            }
        }
    }
}
