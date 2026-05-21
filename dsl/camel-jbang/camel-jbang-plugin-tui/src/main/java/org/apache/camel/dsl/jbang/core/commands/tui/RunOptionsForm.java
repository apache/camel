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
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hintLast;

class RunOptionsForm {

    // Row indices
    private static final int ROW_NAME = 0;
    private static final int ROW_PORT = 1;
    private static final int ROW_MAX_SECONDS = 2;
    private static final int ROW_DEV = 3;
    private static final int ROW_OBSERVE = 4;
    private static final int ROW_TRACE = 5;
    private static final int ROW_COUNT = 6;

    private boolean visible;
    private int selectedRow;

    // Text fields
    private TextInputState nameInput;
    private TextInputState portInput;
    private TextInputState maxSecondsInput;

    // Checkboxes
    private boolean devMode;
    private boolean observe;
    private boolean backlogTrace;

    private String exampleTitle;

    boolean isVisible() {
        return visible;
    }

    void open(String defaultName, String exampleName) {
        nameInput = new TextInputState(defaultName != null ? defaultName : "");
        portInput = new TextInputState("");
        maxSecondsInput = new TextInputState("");
        devMode = false;
        observe = false;
        backlogTrace = false;
        selectedRow = ROW_NAME;
        exampleTitle = exampleName != null ? exampleName : "Run";
        visible = true;
    }

    void close() {
        visible = false;
    }

    String name() {
        return nameInput != null ? nameInput.text().trim() : "";
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel()) {
            visible = false;
            return true;
        }
        if (ke.isConfirm()) {
            return true;
        }
        if (ke.isUp()) {
            selectedRow = (selectedRow - 1 + ROW_COUNT) % ROW_COUNT;
            return true;
        }
        if (ke.isDown() || ke.isFocusNext()) {
            selectedRow = (selectedRow + 1) % ROW_COUNT;
            return true;
        }
        if (ke.isFocusPrevious()) {
            selectedRow = (selectedRow - 1 + ROW_COUNT) % ROW_COUNT;
            return true;
        }

        // Checkbox rows: Space toggles
        if (ke.isChar(' ') && selectedRow >= ROW_DEV) {
            switch (selectedRow) {
                case ROW_DEV -> devMode = !devMode;
                case ROW_OBSERVE -> observe = !observe;
                case ROW_TRACE -> backlogTrace = !backlogTrace;
            }
            return true;
        }

        // Text field rows: delegate to active input
        if (selectedRow <= ROW_MAX_SECONDS) {
            TextInputState active = activeInput();
            if (active != null) {
                if (ke.isDeleteBackward()) {
                    active.deleteBackward();
                } else if (ke.isDeleteForward()) {
                    active.deleteForward();
                } else if (ke.isLeft()) {
                    active.moveCursorLeft();
                } else if (ke.isRight()) {
                    active.moveCursorRight();
                } else if (ke.isHome()) {
                    active.moveCursorToStart();
                } else if (ke.isEnd()) {
                    active.moveCursorToEnd();
                } else if (ke.code() == KeyCode.CHAR) {
                    // port and max-seconds only accept digits
                    if (selectedRow == ROW_PORT || selectedRow == ROW_MAX_SECONDS) {
                        if (Character.isDigit(ke.character())) {
                            active.insert(ke.character());
                        }
                    } else {
                        active.insert(ke.character());
                    }
                }
            }
            return true;
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        int popupW = Math.min(56, area.width() - 4);
        int popupH = 10;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Run: " + exampleTitle + " ")
                .titleBottom(Title.from(Line.from(
                        Span.styled(" Tab", MonitorContext.HINT_KEY_STYLE), Span.raw(" next │"),
                        Span.styled(" Space", MonitorContext.HINT_KEY_STYLE), Span.raw(" toggle │"),
                        Span.styled(" Enter", MonitorContext.HINT_KEY_STYLE), Span.raw(" launch │"),
                        Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE), Span.raw(" back "))))
                .build();
        frame.renderWidget(block, popup);

        int innerX = popup.left() + 2;
        int innerW = popup.width() - 4;
        int labelW = 16;
        int fieldW = innerW - labelW;
        int rowY = popup.top() + 1;

        // Name
        renderLabel(frame, innerX, rowY, labelW, "Name:", selectedRow == ROW_NAME);
        renderTextInput(frame, innerX + labelW, rowY, fieldW, nameInput, selectedRow == ROW_NAME);
        rowY++;

        // Port
        renderLabel(frame, innerX, rowY, labelW, "Port:", selectedRow == ROW_PORT);
        renderTextInput(frame, innerX + labelW, rowY, fieldW, portInput, selectedRow == ROW_PORT);
        rowY++;

        // Max duration
        renderLabel(frame, innerX, rowY, labelW, "Max seconds:", selectedRow == ROW_MAX_SECONDS);
        renderTextInput(frame, innerX + labelW, rowY, fieldW, maxSecondsInput, selectedRow == ROW_MAX_SECONDS);
        rowY++;

        // Dev mode checkbox
        renderCheckbox(frame, innerX, rowY, innerW, "Dev mode (live reload)", devMode, selectedRow == ROW_DEV);
        rowY++;

        // Observe checkbox
        renderCheckbox(frame, innerX, rowY, innerW, "Observe (health + metrics)", observe, selectedRow == ROW_OBSERVE);
        rowY++;

        // Backlog trace checkbox
        renderCheckbox(frame, innerX, rowY, innerW, "Backlog trace", backlogTrace, selectedRow == ROW_TRACE);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "Tab", "next");
        if (selectedRow >= ROW_DEV) {
            hint(spans, "Space", "toggle");
        }
        hint(spans, "Enter", "launch");
        hintLast(spans, "Esc", "back");
    }

    List<String> buildArgs() {
        List<String> args = new ArrayList<>();
        String name = nameInput.text().trim();
        if (!name.isEmpty()) {
            args.add("--name=" + name);
        }
        String port = portInput.text().trim();
        if (!port.isEmpty()) {
            args.add("--port=" + port);
        }
        String maxSec = maxSecondsInput.text().trim();
        if (!maxSec.isEmpty() && !"0".equals(maxSec)) {
            args.add("--max-seconds=" + maxSec);
        }
        if (devMode) {
            args.add("--dev");
        }
        if (observe) {
            args.add("--observe");
        }
        if (backlogTrace) {
            args.add("--backlog-trace");
        }
        return args;
    }

    private TextInputState activeInput() {
        return switch (selectedRow) {
            case ROW_NAME -> nameInput;
            case ROW_PORT -> portInput;
            case ROW_MAX_SECONDS -> maxSecondsInput;
            default -> null;
        };
    }

    private void renderLabel(Frame frame, int x, int y, int w, String label, boolean selected) {
        Style style = selected ? Style.EMPTY.bold() : Style.EMPTY.dim();
        Rect labelArea = new Rect(x, y, w, 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(label, style))), labelArea);
    }

    private void renderTextInput(Frame frame, int x, int y, int w, TextInputState state, boolean active) {
        Rect inputArea = new Rect(x, y, w, 1);
        if (active) {
            TextInput textInput = TextInput.builder()
                    .cursorStyle(Style.EMPTY.reversed())
                    .build();
            frame.renderStatefulWidget(textInput, inputArea, state);
        } else {
            String text = state.text();
            Style style = text.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY;
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(text.isEmpty() ? "—" : text, style))), inputArea);
        }
    }

    private void renderCheckbox(Frame frame, int x, int y, int w, String label, boolean checked, boolean selected) {
        String box = checked ? "[x]" : "[ ]";
        Style style = selected ? Style.EMPTY.bold().reversed() : Style.EMPTY;
        Rect cbArea = new Rect(x, y, w, 1);
        frame.renderWidget(Paragraph.from(Line.from(
                Span.styled(" " + box + " " + label, style))), cbArea);
    }
}
