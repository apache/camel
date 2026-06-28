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
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hintLast;

class RunOptionsForm {

    private static final int PAGE_OPTIONS = 0;
    private static final int PAGE_PROPERTIES = 1;

    // Row indices for page 0
    private static final int ROW_NAME = 0;
    private static final int ROW_PORT = 1;
    private static final int ROW_MAX = 2;
    private static final int ROW_CONSOLE = 3;
    private static final int ROW_DEV = 4;
    private static final int ROW_OBSERVE = 5;
    private static final int ROW_TRACE = 6;
    private static final int ROW_STUB = 7;
    private static final int ROW_OTEL_AGENT = 8;
    private static final int ROW_COUNT = 9;

    private boolean visible;
    private int page;
    private int selectedRow;

    private static final String[] MAX_MODES = { "Max seconds:", "Max messages:", "Max idle secs:" };
    private static final String[] MAX_FLAGS = { "--max-seconds=", "--max-messages=", "--max-idle-seconds=" };

    // Text fields
    private TextInputState nameInput;
    private TextInputState portInput;
    private TextInputState maxInput;
    private int maxMode;

    // Checkboxes
    private boolean devMode;
    private boolean observe;
    private boolean backlogTrace;
    private boolean stubMode;
    private boolean otelAgent;
    private int otelExportTarget; // 0=TUI, 1=Jaeger
    private boolean webConsole;

    private String exampleTitle;

    // Properties (page 2)
    private List<PropertyEntry> properties;
    private int selectedProperty;
    private boolean editingKey;

    boolean isVisible() {
        return visible;
    }

    void open(String defaultName, String exampleName, boolean bundled) {
        open(defaultName, exampleName, bundled, false);
    }

    void open(String defaultName, String exampleName, boolean bundled, boolean dev) {
        nameInput = new TextInputState(defaultName != null ? defaultName : "");
        portInput = new TextInputState("");
        maxInput = new TextInputState("");
        maxMode = 0;
        devMode = dev;
        observe = false;
        backlogTrace = false;
        stubMode = false;
        otelAgent = false;
        otelExportTarget = 0;
        webConsole = false;
        selectedRow = ROW_NAME;
        page = PAGE_OPTIONS;
        selectedProperty = 0;
        exampleTitle = exampleName != null ? exampleName : "Run";
        loadProperties(exampleName, bundled);
        visible = true;
    }

    void close() {
        visible = false;
    }

    String name() {
        return nameInput != null ? nameInput.text().trim() : "";
    }

    boolean isStubMode() {
        return stubMode;
    }

    boolean isJaegerExport() {
        return otelAgent && otelExportTarget == 1;
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

        if (page == PAGE_OPTIONS) {
            return handleOptionsPage(ke);
        } else {
            return handlePropertiesPage(ke);
        }
    }

    void render(Frame frame, Rect area) {
        if (page == PAGE_OPTIONS) {
            renderOptionsPage(frame, area);
        } else {
            renderPropertiesPage(frame, area);
        }
    }

    void renderFooter(List<Span> spans) {
        if (page == PAGE_OPTIONS) {
            if (hasProperties()) {
                hint(spans, "Tab", "next");
            } else {
                hint(spans, "Tab", "next");
            }
            if (selectedRow >= ROW_CONSOLE) {
                hint(spans, "Space", "toggle");
            }
            if (hasProperties()) {
                hint(spans, "→", "properties");
            }
            hint(spans, "Enter", "launch");
            hintLast(spans, "Esc", "back");
        } else {
            hint(spans, "←", "options");
            hint(spans, "↑↓", "navigate");
            hint(spans, "+", "add");
            hint(spans, "Enter", "launch");
            hintLast(spans, "Esc", "back");
        }
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
        String maxVal = maxInput.text().trim();
        if (!maxVal.isEmpty() && !"0".equals(maxVal)) {
            args.add(MAX_FLAGS[maxMode] + maxVal);
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
        if (stubMode) {
            args.add("--stub=remote");
        }
        if (otelAgent) {
            args.add("--open-telemetry-agent");
            if (otelExportTarget == 1) {
                args.add("--open-telemetry-agent-export=jaeger");
            }
        }
        if (webConsole) {
            args.add("--console");
        }
        if (properties != null) {
            for (PropertyEntry pe : properties) {
                String current = pe.valueInput().text();
                String key = pe.effectiveKey();
                if (key.isEmpty()) {
                    continue;
                }
                if (pe.isCustom() || !current.equals(pe.originalValue())) {
                    args.add("--prop=" + key + "=" + current);
                }
            }
        }
        return args;
    }

    // ---- Options page (page 0) ----

    private boolean handleOptionsPage(KeyEvent ke) {
        if (ke.isUp()) {
            selectedRow = (selectedRow - 1 + ROW_COUNT) % ROW_COUNT;
            return true;
        }
        if (ke.isDown()) {
            if (selectedRow == ROW_OTEL_AGENT && hasProperties()) {
                page = PAGE_PROPERTIES;
                selectedProperty = 0;
            } else {
                selectedRow = (selectedRow + 1) % ROW_COUNT;
            }
            return true;
        }
        if (ke.isFocusNext()) {
            if (selectedRow == ROW_OTEL_AGENT && hasProperties()) {
                page = PAGE_PROPERTIES;
                selectedProperty = 0;
            } else {
                selectedRow = (selectedRow + 1) % ROW_COUNT;
            }
            return true;
        }
        if (ke.isFocusPrevious()) {
            selectedRow = (selectedRow - 1 + ROW_COUNT) % ROW_COUNT;
            return true;
        }
        if (ke.isRight() && selectedRow == ROW_OTEL_AGENT && otelAgent) {
            otelExportTarget = (otelExportTarget + 1) % 2;
            return true;
        }
        if (ke.isLeft() && selectedRow == ROW_OTEL_AGENT && otelAgent) {
            otelExportTarget = (otelExportTarget + 1) % 2;
            return true;
        }
        if (ke.isRight() && hasProperties() && selectedRow >= ROW_OTEL_AGENT) {
            page = PAGE_PROPERTIES;
            selectedProperty = 0;
            return true;
        }

        if (ke.isChar('+') && selectedRow >= ROW_CONSOLE) {
            page = PAGE_PROPERTIES;
            addCustomProperty();
            return true;
        }

        // Max row: Space cycles mode
        if (ke.isChar(' ') && selectedRow == ROW_MAX) {
            maxMode = (maxMode + 1) % MAX_MODES.length;
            return true;
        }

        // Checkbox rows: Space toggles
        if (ke.isChar(' ') && selectedRow >= ROW_CONSOLE) {
            switch (selectedRow) {
                case ROW_DEV -> devMode = !devMode;
                case ROW_OBSERVE -> observe = !observe;
                case ROW_TRACE -> backlogTrace = !backlogTrace;
                case ROW_STUB -> stubMode = !stubMode;
                case ROW_OTEL_AGENT -> otelAgent = !otelAgent;
                case ROW_CONSOLE -> webConsole = !webConsole;
            }
            return true;
        }

        // Text field rows: delegate to active input
        if (selectedRow <= ROW_MAX) {
            TextInputState active = activeInput();
            if (active != null) {
                handleTextInput(ke, active, selectedRow == ROW_PORT || selectedRow == ROW_MAX);
            }
            return true;
        }

        return true;
    }

    // ---- Properties page (page 1) ----

    private boolean handlePropertiesPage(KeyEvent ke) {
        if (ke.isChar('+')) {
            addCustomProperty();
            return true;
        }
        if (ke.isUp()) {
            editingKey = false;
            if (selectedProperty == 0) {
                page = PAGE_OPTIONS;
                selectedRow = ROW_OTEL_AGENT;
            } else {
                selectedProperty--;
            }
            return true;
        }
        if (ke.isDown()) {
            editingKey = false;
            if (selectedProperty < properties.size() - 1) {
                selectedProperty++;
            }
            return true;
        }
        if (ke.isFocusNext()) {
            PropertyEntry current = properties.get(selectedProperty);
            if (current.isCustom() && editingKey) {
                editingKey = false;
            } else if (selectedProperty < properties.size() - 1) {
                editingKey = false;
                selectedProperty++;
            }
            return true;
        }
        if (ke.isFocusPrevious()) {
            PropertyEntry current = properties.get(selectedProperty);
            if (current.isCustom() && !editingKey) {
                editingKey = true;
            } else if (selectedProperty == 0) {
                editingKey = false;
                page = PAGE_OPTIONS;
                selectedRow = ROW_OTEL_AGENT;
            } else {
                editingKey = false;
                selectedProperty--;
            }
            return true;
        }
        if (ke.isLeft()) {
            PropertyEntry current = properties.get(selectedProperty);
            TextInputState active = editingKey ? current.keyInput() : current.valueInput();
            if (active.cursorPosition() == 0) {
                if (current.isCustom() && !editingKey) {
                    editingKey = true;
                    return true;
                }
                page = PAGE_OPTIONS;
                selectedRow = ROW_OTEL_AGENT;
                editingKey = false;
                return true;
            }
        }
        if (ke.isDeleteBackward()) {
            PropertyEntry current = properties.get(selectedProperty);
            if (current.isCustom() && editingKey) {
                if (current.keyInput().text().isEmpty()) {
                    properties.remove(selectedProperty);
                    if (selectedProperty >= properties.size() && selectedProperty > 0) {
                        selectedProperty--;
                    }
                    if (properties.isEmpty()) {
                        page = PAGE_OPTIONS;
                        selectedRow = ROW_OTEL_AGENT;
                    }
                    return true;
                }
            }
        }

        // Text editing for selected property
        PropertyEntry current = properties.get(selectedProperty);
        TextInputState active = (current.isCustom() && editingKey) ? current.keyInput() : current.valueInput();
        handleTextInput(ke, active, false);
        return true;
    }

    private void addCustomProperty() {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        PropertyEntry entry = new PropertyEntry("", "", new TextInputState(""), new TextInputState(""));
        properties.add(entry);
        selectedProperty = properties.size() - 1;
        editingKey = true;
    }

    // ---- Rendering ----

    private void renderOptionsPage(Frame frame, Rect area) {
        int popupW = Math.min(56, area.width() - 4);
        int popupH = 13;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 4);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        String title = " Run: " + exampleTitle;
        if (hasProperties()) {
            title += " (1/2) ";
        } else {
            title += " ";
        }

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(title)
                .build();
        frame.renderWidget(block, popup);

        int innerX = popup.left() + 2;
        int innerW = popup.width() - 4;
        int labelW = 16;
        int fieldW = innerW - labelW;
        int rowY = popup.top() + 1;

        renderLabel(frame, innerX, rowY, labelW, "Name:", selectedRow == ROW_NAME);
        renderTextInput(frame, innerX + labelW, rowY, fieldW, nameInput, selectedRow == ROW_NAME);
        rowY++;

        renderLabel(frame, innerX, rowY, labelW, "Port:", selectedRow == ROW_PORT);
        renderTextInput(frame, innerX + labelW, rowY, fieldW, portInput, selectedRow == ROW_PORT);
        rowY++;

        renderLabel(frame, innerX, rowY, labelW, MAX_MODES[maxMode], selectedRow == ROW_MAX);
        renderTextInput(frame, innerX + labelW, rowY, fieldW, maxInput, selectedRow == ROW_MAX);
        rowY++;

        renderCheckbox(frame, innerX, rowY, innerW, "Web console (/q/dev)", webConsole, selectedRow == ROW_CONSOLE);
        rowY++;

        renderCheckbox(frame, innerX, rowY, innerW, "Dev mode (live reload)", devMode, selectedRow == ROW_DEV);
        rowY++;

        renderCheckbox(frame, innerX, rowY, innerW, "Observe (health + metrics)", observe, selectedRow == ROW_OBSERVE);
        rowY++;

        renderCheckbox(frame, innerX, rowY, innerW, "Backlog trace", backlogTrace, selectedRow == ROW_TRACE);
        rowY++;

        renderCheckbox(frame, innerX, rowY, innerW, "Stub (no Docker needed)", stubMode, selectedRow == ROW_STUB);
        rowY++;

        renderCheckbox(frame, innerX, rowY, innerW, "OTel Java Agent (auto-instrument)", otelAgent,
                selectedRow == ROW_OTEL_AGENT);
        if (otelAgent) {
            String tuiLabel = otelExportTarget == 0 ? "[TUI]" : " TUI ";
            String jaegerLabel = otelExportTarget == 1 ? "[Jaeger]" : " Jaeger ";
            Style tuiStyle = otelExportTarget == 0 ? Style.EMPTY.bold() : Style.EMPTY.dim();
            Style jaegerStyle = otelExportTarget == 1 ? Style.EMPTY.bold() : Style.EMPTY.dim();
            int exportX = innerX + 38;
            Rect exportArea = new Rect(exportX, rowY, innerW - 36, 1);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(tuiLabel, tuiStyle),
                    Span.styled(" ", Style.EMPTY),
                    Span.styled(jaegerLabel, jaegerStyle))), exportArea);
        }
    }

    private void renderPropertiesPage(Frame frame, Rect area) {
        int popupW = Math.min(100, area.width() - 4);
        int propCount = properties != null ? properties.size() : 0;
        int popupH = Math.min(propCount + 2, Math.min(20, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 4);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Run: " + exampleTitle + " — Properties (2/2) ")
                .build();
        frame.renderWidget(block, popup);

        int innerX = popup.left() + 2;
        int innerW = popup.width() - 4;
        int maxKeyLen = 0;
        for (PropertyEntry pe : properties) {
            maxKeyLen = Math.max(maxKeyLen, pe.key().length());
        }
        int labelW = Math.min(maxKeyLen + 2, innerW * 3 / 5);
        int fieldW = innerW - labelW;

        int rowY = popup.top() + 1;
        int visibleRows = popup.height() - 2;
        int scrollOffset = Math.max(0, selectedProperty - visibleRows + 1);

        for (int i = scrollOffset; i < properties.size() && (rowY - popup.top() - 1) < visibleRows; i++) {
            PropertyEntry pe = properties.get(i);
            boolean selected = (i == selectedProperty);

            if (pe.isCustom()) {
                // Custom entry: editable key and value
                if (selected && editingKey) {
                    renderTextInput(frame, innerX, rowY, labelW - 1, pe.keyInput(), true);
                } else {
                    String keyText = pe.keyInput().text();
                    Style keyStyle = selected ? Style.EMPTY.bold() : (keyText.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY);
                    Rect keyArea = new Rect(innerX, rowY, labelW - 1, 1);
                    frame.renderWidget(Paragraph.from(Line.from(
                            Span.styled(keyText.isEmpty() ? "<key>" : keyText, keyStyle))), keyArea);
                }
                Rect colonArea = new Rect(innerX + labelW - 1, rowY, 1, 1);
                frame.renderWidget(Paragraph.from(Line.from(Span.styled(":", Style.EMPTY.dim()))), colonArea);
                if (selected && !editingKey) {
                    renderTextInput(frame, innerX + labelW, rowY, fieldW, pe.valueInput(), true);
                } else {
                    String text = pe.valueInput().text();
                    Style style = text.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY;
                    Rect valArea = new Rect(innerX + labelW, rowY, fieldW, 1);
                    frame.renderWidget(Paragraph.from(Line.from(
                            Span.styled(text.isEmpty() ? "<value>" : text, style))), valArea);
                }
            } else {
                // Loaded entry: fixed key, editable value
                String keyLabel = pe.key() + ":";
                renderLabel(frame, innerX, rowY, labelW, TuiHelper.truncate(keyLabel, labelW), selected);

                boolean modified = !pe.valueInput().text().equals(pe.originalValue());
                if (selected) {
                    renderTextInput(frame, innerX + labelW, rowY, fieldW, pe.valueInput(), true);
                } else {
                    String text = pe.valueInput().text();
                    Style style = modified ? Style.EMPTY.bold() : Style.EMPTY;
                    Rect inputArea = new Rect(innerX + labelW, rowY, fieldW, 1);
                    frame.renderWidget(Paragraph.from(Line.from(
                            Span.styled(text.isEmpty() ? "—" : text, style))), inputArea);
                }
            }
            rowY++;
        }
    }

    // ---- Properties loading ----

    private void loadProperties(String exampleName, boolean bundled) {
        properties = new ArrayList<>();
        if (exampleName == null || exampleName.isEmpty()) {
            return;
        }
        String content;
        if (bundled) {
            content = DocHelper.loadResourceContent("examples/" + exampleName + "/application.properties");
        } else {
            content = DocHelper.downloadContent(
                    "https://raw.githubusercontent.com/apache/camel-jbang-examples/main/"
                                                + exampleName + "/application.properties");
        }
        if (content == null || content.isBlank()) {
            return;
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                properties.add(new PropertyEntry(key, value, null, new TextInputState(value)));
            }
        }
    }

    private boolean hasProperties() {
        return properties != null && !properties.isEmpty();
    }

    // ---- Shared helpers ----

    private TextInputState activeInput() {
        return switch (selectedRow) {
            case ROW_NAME -> nameInput;
            case ROW_PORT -> portInput;
            case ROW_MAX -> maxInput;
            default -> null;
        };
    }

    private void handleTextInput(KeyEvent ke, TextInputState active, boolean digitsOnly) {
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
            if (digitsOnly) {
                if (Character.isDigit(ke.character())) {
                    active.insert(ke.character());
                }
            } else {
                active.insert(ke.character());
            }
        }
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

    record PropertyEntry(String key, String originalValue, TextInputState keyInput, TextInputState valueInput) {
        boolean isCustom() {
            return keyInput != null;
        }

        String effectiveKey() {
            return keyInput != null ? keyInput.text().trim() : key;
        }
    }
}
