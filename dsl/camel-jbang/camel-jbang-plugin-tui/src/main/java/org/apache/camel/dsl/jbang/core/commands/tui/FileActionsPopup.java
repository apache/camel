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
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;

/**
 * File-actions menu for the Source tab file list (opened with F12). Presents basic file management (new file, new
 * folder, rename, duplicate, delete, copy path) and drives the name-entry prompt and delete confirmation itself,
 * emitting a single {@link Request} for the host tab to execute.
 */
class FileActionsPopup {

    enum Action {
        NEW_FILE,
        NEW_FOLDER,
        RENAME,
        DUPLICATE,
        DELETE,
        COPY_PATH
    }

    /** A completed, ready-to-execute request. {@code name} is null for DELETE and COPY_PATH. */
    record Request(Action action, String name) {
    }

    private record MenuItem(Action action, String icon, String label) {
    }

    private enum Phase {
        MENU,
        INPUT,
        CONFIRM
    }

    private boolean visible;
    private Phase phase = Phase.MENU;

    private String targetName;
    private boolean hasTarget;

    private final ListState menuState = new ListState();
    private List<MenuItem> items = List.of();

    private Action inputAction;
    private String inputTitle;
    private TextInputState inputState;

    private Rect popupRect;
    private Request result;

    void open(String selectedName, boolean hasTarget) {
        this.visible = true;
        this.phase = Phase.MENU;
        this.targetName = selectedName;
        this.hasTarget = hasTarget;
        this.result = null;
        this.inputState = null;
        buildMenu();
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

    private void buildMenu() {
        List<MenuItem> list = new ArrayList<>();
        list.add(new MenuItem(Action.NEW_FILE, TuiIcons.NEW_FILE, "New file…"));
        list.add(new MenuItem(Action.NEW_FOLDER, TuiIcons.NEW_FOLDER, "New folder…"));
        if (hasTarget) {
            list.add(new MenuItem(Action.RENAME, TuiIcons.RENAME, "Rename…"));
            list.add(new MenuItem(Action.DUPLICATE, TuiIcons.DUPLICATE, "Duplicate…"));
            list.add(new MenuItem(Action.DELETE, TuiIcons.DELETE, "Delete"));
            list.add(new MenuItem(Action.COPY_PATH, TuiIcons.CLIPBOARD, "Copy path to clipboard"));
        }
        this.items = list;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        switch (phase) {
            case MENU:
                return handleMenuKey(ke);
            case INPUT:
                return handleInputKey(ke);
            case CONFIRM:
                return handleConfirmKey(ke);
            default:
                return true;
        }
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
                dispatch(items.get(sel).action());
            }
            return true;
        }
        // Menu is navigated with the cursor only (no accelerator keys); swallow everything else.
        return true;
    }

    private void dispatch(Action action) {
        switch (action) {
            case NEW_FILE -> startInput(action, "New file", "");
            case NEW_FOLDER -> startInput(action, "New folder", "");
            case RENAME -> startInput(action, "Rename", targetName);
            case DUPLICATE -> startInput(action, "Duplicate", SourceFileOps.suggestDuplicateName(targetName));
            case DELETE -> phase = Phase.CONFIRM;
            case COPY_PATH -> {
                result = new Request(Action.COPY_PATH, null);
                close();
            }
        }
    }

    private void startInput(Action action, String title, String initial) {
        this.inputAction = action;
        this.inputTitle = title;
        this.inputState = new TextInputState(initial != null ? initial : "");
        this.inputState.moveCursorToEnd();
        this.phase = Phase.INPUT;
    }

    private boolean handleInputKey(KeyEvent ke) {
        if (ke.isCancel()) {
            // go back to the menu rather than dismissing everything
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

    private boolean handleConfirmKey(KeyEvent ke) {
        // Delete is a destructive action: only an explicit "y" confirms it. Enter must NOT delete. Esc (or "n")
        // returns to the menu; any other key is swallowed so a stray keystroke neither deletes nor dismisses.
        if (ke.code() == KeyCode.CHAR && "y".equalsIgnoreCase(ke.string())) {
            result = new Request(Action.DELETE, null);
            close();
            return true;
        }
        if (ke.isCancel() || (ke.code() == KeyCode.CHAR && "n".equalsIgnoreCase(ke.string()))) {
            phase = Phase.MENU;
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
            case CONFIRM -> renderConfirm(frame, area);
            default -> {
            }
        }
    }

    private void renderMenu(Frame frame, Rect area) {
        int popupW = Math.max(34, Math.min(44, area.width() - 4));
        popupW = Math.min(popupW, area.width() - 2);
        // one blank padding row above and below the items, plus the two border rows
        int popupH = items.size() + 4;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 3);
        Rect popup = new Rect(x, y, popupW, Math.min(popupH, area.height() - 2));
        this.popupRect = popup;

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
                                Span.styled(" " + TuiIcons.FOLDER_OPEN + " File Actions ", Theme.title().bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, menuState);
    }

    private void renderInput(Frame frame, Rect area) {
        this.popupRect = DialogHelper.renderInputDialog(frame, area, inputTitle, inputState, "name");
    }

    private void renderConfirm(Frame frame, Rect area) {
        // Deleting a file is irreversible, so this dialog is error-styled and deliberately accepts "y" only.
        this.popupRect = DialogHelper.renderConfirm(frame, area, TuiIcons.DELETE + " Delete file?",
                "Delete " + targetName + "?", true, "y", "delete");
    }

    void renderFooter(List<Span> spans) {
        if (!visible) {
            return;
        }
        switch (phase) {
            case MENU -> {
                TuiHelper.hint(spans, "Enter", "select");
                TuiHelper.hintLast(spans, "Esc", "close");
            }
            case INPUT -> {
                TuiHelper.hint(spans, "Enter", "confirm");
                TuiHelper.hintLast(spans, "Esc", "back");
            }
            case CONFIRM -> {
                TuiHelper.hint(spans, "y", "delete");
                TuiHelper.hintLast(spans, "Esc", "cancel");
            }
            default -> {
            }
        }
    }
}
