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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;
import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

/** An inline, explicitly invoked expert call. No declaration or route is needed. */
final class SemanticPlayground {
    private final MonitorContext ctx;
    private final String pid;
    private final String expert;
    private final JsonObject operation;
    private final TextAreaState input = new TextAreaState();
    private final SemanticParameterForm instructions;
    private final SemanticParameterForm parameters;
    private boolean structured;
    private boolean submittedStructured;
    private CompletableFuture<JsonObject> pending;
    private JsonObject response;
    private String error;
    private String submittedInput;
    private JsonObject submittedRequest;
    private List<String> submittedScoreLevels = List.of();
    private String parameterSource;
    private boolean expanded;
    private long submittedInstructions;
    private long submittedParameters;
    private int field;
    private int outputScroll;
    private Rect inputArea;
    private Rect parameterArea;
    private Rect outputArea;
    private Rect runArea;

    SemanticPlayground(MonitorContext ctx, String pid, String expert, JsonObject operation) {
        this.ctx = ctx;
        this.pid = pid;
        this.expert = expert;
        this.operation = operation;
        structured = !supports("text") && supports("structured");
        List<JsonObject> contracts = SemanticTab.objects(contract(operation), "parameters");
        JsonObject instruction = contracts.stream()
                .filter(p -> "instructions".equals(p.get("name")) && "String".equals(p.get("type"))
                        && (!(p.get("values") instanceof List<?> choices) || choices.isEmpty()))
                .findFirst().orElse(null);
        instructions = new SemanticParameterForm(instruction == null ? List.of() : List.of(instruction));
        parameters = new SemanticParameterForm(contracts.stream().filter(p -> p != instruction).toList());
        focusFirstField();
    }

    boolean loadParameters(JsonObject definition) {
        if (pending != null) {
            return false;
        }
        Map<?, ?> values = definition.get("parameters") instanceof Map<?, ?> map ? map : Map.of();
        instructions.load(values);
        parameters.load(values);
        parameterSource = text(definition, "name");
        return true;
    }

    boolean setInputValue(String name, String value) {
        refresh();
        if (pending != null || !available()) {
            return false;
        }
        if ("input".equals(name)) {
            input.setText(value);
            field = 0;
        } else if ("inputMode".equals(name)) {
            if (!("text".equals(value) && supports("text") || "json".equals(value) && supports("structured"))) {
                throw new IllegalArgumentException("Unsupported input mode: " + value);
            }
            structured = "json".equals(value);
            field = 0;
        } else if (name.startsWith("parameter.")) {
            String parameter = name.substring("parameter.".length());
            if (instructions.setInputValue(parameter, value)) {
                field = -1;
            } else if (parameters.setInputValue(parameter, value)) {
                field = 1;
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    JsonObject snapshot() {
        refresh();
        JsonObject result = new JsonObject();
        result.put("expert", expert);
        result.put("operation", operation.get("name"));
        result.put("input", input.text());
        result.put("inputMode", structured ? "json" : "text");
        List<JsonObject> fields = new ArrayList<>(instructions.snapshot());
        fields.addAll(parameters.snapshot());
        result.put("parameters", fields);
        result.put("parameterSource", parameterSource);
        result.put("focusedField", field == -1 ? "instructions" : field == 0 ? "input" : field == 1 ? "parameters" : "output");
        result.put("pending", pending != null);
        result.put("connected", connected());
        result.put("submittedRequest", submittedRequest);
        result.put("result", response);
        result.put("error", error);
        result.put("inputChanged", inputChanged());
        return result;
    }

    private boolean inputChanged() {
        return response != null && (submittedStructured != structured || !Objects.equals(submittedInput, input.text())
                || submittedInstructions != instructions.revision() || submittedParameters != parameters.revision());
    }

    boolean matches(String pid, String expert, JsonObject operation) {
        return Objects.equals(this.pid, pid) && Objects.equals(this.expert, expert)
                && Objects.equals(this.operation, operation);
    }

    private boolean supports(String type) {
        return contract(operation).get("inputTypes") instanceof List<?> types && types.contains(type);
    }

    boolean available() {
        return supports("text") || supports("structured");
    }

    private boolean connected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null && Objects.equals(pid, info.phantom ? info.linkedPid : info.pid)
                && !List.of("Terminating", "Terminated").contains(extractState(info.state));
    }

    void focusFirstField() {
        field = instructions.isEmpty() ? 0 : -1;
    }

    boolean handleKeyEvent(KeyEvent key) {
        refresh();
        if (key.hasCtrl() && key.isCharIgnoreCase('r')) {
            run();
        } else if (key.isKey(KeyCode.TAB)) {
            boolean back = key.hasShift();
            if (field == 1 && parameters.next(back)) {
                return true;
            }
            field += back ? -1 : 1;
            if (field == 1 && parameters.isEmpty()) {
                field += back ? -1 : 1;
            }
            if (field < (instructions.isEmpty() ? 0 : -1) || field > 2) {
                focusFirstField();
                return false;
            }
            if (field == 1) {
                parameters.focus(back);
            }
        } else if (field == 2) {
            if (key.isHome()) {
                outputScroll = 0;
            } else if (key.isUp() || key.isPageUp()) {
                outputScroll = Math.max(0, outputScroll - (key.isPageUp() ? 5 : 1));
            } else if (key.isDown() || key.isPageDown()) {
                outputScroll += key.isPageDown() ? 5 : 1;
            }
        } else if (pending == null && available()) {
            if (field == 0 && key.hasCtrl() && key.isCharIgnoreCase('t') && supports("text") && supports("structured")) {
                structured = !structured;
            } else if (key.hasCtrl() && key.isCharIgnoreCase('l')) {
                if (field == 0) {
                    input.clear();
                } else {
                    (field == -1 ? instructions : parameters).handleKeyEvent(key);
                }
            } else if (field == -1) {
                instructions.handleKeyEvent(key);
            } else if (field == 1) {
                parameters.handleKeyEvent(key);
            } else {
                FormHelper.handleTextArea(key, input);
            }
        }
        return true;
    }

    void handlePaste(String value) {
        if (pending == null && available() && value != null && field != 2) {
            if (field == 0) {
                input.insert(value);
            } else {
                (field == -1 ? instructions : parameters).handlePaste(value);
            }
        }
    }

    boolean handleMouseEvent(MouseEvent event) {
        if (!event.isClick()) {
            return false;
        }
        if (runArea != null && contains(runArea, event.x(), event.y())) {
            run();
            return true;
        }
        if (pending == null && instructions.click(event.x(), event.y())) {
            field = -1;
            return true;
        }
        if (pending == null && parameters.click(event.x(), event.y())) {
            field = 1;
            return true;
        }
        for (int i = 0; i < 3; i++) {
            Rect area = i == 0 ? inputArea : i == 1 ? parameterArea : outputArea;
            if (area != null && contains(area, event.x(), event.y())) {
                field = i;
                return true;
            }
        }
        return false;
    }

    private void run() {
        refresh();
        if (pending != null || !available() || !connected()) {
            return;
        }
        error = null;
        response = null;
        outputScroll = 0;
        try {
            if (input.text().isBlank()) {
                field = 0;
                throw new IllegalArgumentException(
                        structured ? "Enter a JSON object or array to evaluate." : "Enter text to evaluate.");
            }
            JsonObject params;
            try {
                params = instructions.values();
            } catch (IllegalArgumentException invalid) {
                field = -1;
                throw invalid;
            }
            try {
                params.putAll(parameters.values());
            } catch (IllegalArgumentException invalid) {
                field = 1;
                throw invalid;
            }
            Object value = input.text();
            if (structured) {
                value = Jsoner.deserialize(input.text());
                if (!(value instanceof JsonObject || value instanceof List<?>)) {
                    throw new IllegalArgumentException("Structured input must be a JSON object or array.");
                }
            }
            JsonObject request = new JsonObject();
            request.put("action", "semantic-evaluate");
            request.put("expert", expert);
            request.put("operation", operation.get("name"));
            request.put("input", value);
            request.put("parameters", params);
            submittedRequest = request;
            submittedStructured = structured;
            submittedInput = input.text();
            submittedScoreLevels = scoreLevels(operation, params);
            submittedInstructions = instructions.revision();
            submittedParameters = parameters.revision();
            pending = CompletableFuture
                    .supplyAsync(() -> ctx.executeIndependentAction(pid, request, 60000), ctx.backgroundExecutor)
                    .exceptionally(SemanticResultView::failure);
        } catch (Exception invalid) {
            error = "Input error: " + invalid.getMessage();
        }
    }

    private void refresh() {
        if (pending != null && pending.isDone()) {
            response = pending.getNow(null);
            pending = null;
            if (response == null) {
                error = "Connection: request timed out after 60 seconds. The provider call may still be running. Input kept; Ctrl+r retries.";
            } else if (!response.containsKey("status")) {
                error = "Connection: direct expert evaluation is unavailable. Update the running application's camel-semantic.";
            }
        }
    }

    void render(Frame frame, Rect area, boolean focused, boolean expanded) {
        this.expanded = expanded;
        refresh();
        instructions.clearHits();
        parameters.clearHits();
        inputArea = null;
        parameterArea = null;
        outputArea = null;
        runArea = null;
        Block block = Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED)
                .borderStyle(ctx.paneBorder(focused))
                .title(" " + truncate("Try expert · " + text(operation, "name") + " · direct"
                                      + (expanded ? " · expanded" : ""),
                        area.width() - 4)
                       + " ")
                .build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        if (inner.height() < (instructions.isEmpty() ? 7 : 11) || inner.width() < 30) {
            paragraph(frame, inner, List.of(Line.from("Enlarge the terminal to use the expert playground.")), 0);
            return;
        }
        if (!available()) {
            paragraph(frame, inset(inner), List.of(Line.from("No supported input type is published for this operation.")), 0);
            return;
        }
        Rect title = new Rect(inner.x() + 1, inner.y(), Math.max(0, inner.width() - 20), 1);
        paragraph(frame, title, List.of(Line.from(Span.styled(expert + " › ", Theme.info()),
                Span.raw(text(operation, "name") + " → " + text(operation, "resultType")),
                Span.styled(" · input: " + (structured ? "JSON" : "text"), Theme.muted()))), 0);
        runArea = new Rect(inner.right() - 17, inner.y(), 16, 1);
        frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(Span.styled(
                !connected() ? " Disconnected " : pending == null ? " Ctrl+r  Run " : " Running… ",
                connected() ? Theme.hintKey() : Theme.muted())))).build(), runArea);
        boolean sideBySide = !parameters.isEmpty();
        int editorHeight = Math.max(instructions.isEmpty() ? 3 : 6,
                Math.min(expanded ? Integer.MAX_VALUE : sideBySide || !instructions.isEmpty() ? 22 : 5,
                        (inner.height() - (parameterSource == null ? 0 : 1)) * 2 / 3));
        int y = inner.y() + 1;
        if (parameterSource != null) {
            paragraph(frame, new Rect(inner.x() + 1, y++, inner.width() - 2, 1),
                    List.of(Line
                            .from(Span.styled("Parameters loaded from " + parameterSource + " · editable copy", Theme.info()))),
                    0);
        }
        int width = sideBySide ? (inner.width() - 3) / 2 : inner.width() - 2;
        Rect inputBox = new Rect(inner.x() + 1, y, width, editorHeight);
        Rect stateBox = inputBox;
        if (!instructions.isEmpty()) {
            int instructionHeight = Math.max(3, Math.min(6, editorHeight / 3));
            Rect instructionBox = new Rect(inputBox.x(), y, width, instructionHeight);
            instructions.renderInstructions(frame, instructionBox, focused && field == -1, pending == null,
                    ctx.paneBorder(focused && field == -1));
            stateBox = new Rect(inputBox.x(), instructionBox.bottom(), width, editorHeight - instructionHeight);
        }
        String label = instructions.isEmpty() ? "Input" : structured ? "State to assess" : "Text to assess";
        inputArea = renderEditor(frame, stateBox, input, label + " · " + (structured ? "JSON" : "text"),
                focused && field == 0, structured ? "Paste a JSON object or array" : "Type or paste the content to assess");
        int outputY = inputBox.bottom();
        if (sideBySide) {
            Rect paramsBox = new Rect(inputBox.right() + 1, y, inner.right() - inputBox.right() - 2, editorHeight);
            parameterArea = paramsBox;
            parameters.render(frame, paramsBox, focused && field == 1, pending == null, ctx.paneBorder(focused && field == 1));

        }

        outputArea = new Rect(inner.x() + 1, outputY, inner.width() - 2, Math.max(0, inner.bottom() - outputY));
        List<Line> lines = hangingWrap(output(), outputArea.width());
        outputScroll = Math.min(outputScroll, Math.max(0, lines.size() - outputArea.height()));
        paragraph(frame, outputArea, lines, outputScroll);
    }

    private Rect renderEditor(Frame frame, Rect area, TextAreaState state, String title, boolean focused, String placeholder) {
        Block block = Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED)
                .borderStyle(ctx.paneBorder(focused)).title(" " + truncate(title, area.width() - 4) + " ").build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        TextArea editor = TextArea.builder().overflow(Overflow.WRAP_WORD).cursorStyle(Style.EMPTY.reversed()).build();
        if (focused && pending == null) {
            editor.renderWithCursor(inner, frame.buffer(), state, frame);
        } else if (state.text().isEmpty()) {
            paragraph(frame, inner, List.of(Line.from(Span.styled(placeholder, Theme.muted()))), 0);
        } else {
            editor.render(inner, frame.buffer(), state);
        }
        return inner;
    }

    private List<Line> output() {
        List<Line> lines = new ArrayList<>();
        if (!connected()) {
            lines.add(Line.from(Span.styled("Application disconnected · Run is disabled", Theme.warning())));
            lines.add(Line.from("Reconnect to this application to run the expert. Your draft is kept here."));
        }
        if (pending != null) {
            lines.add(Line.from(Span.styled("Running " + expert + " / " + text(operation, "name") + "…", Theme.info())));
        } else if (error != null) {
            lines.add(Line.from(Span.styled(error, Theme.error())));
        } else if (response == null) {
            try {
                scoreRange(lines, scoreLevels(operation, parameters.values(false)));
            } catch (IllegalArgumentException invalid) {
                // Incomplete drafts have no effective scale yet; Run reports the parameter validation error.
            }
            lines.add(Line.from(Span.styled("Output", Theme.label())));
            lines.add(Line.from(Span.styled("Run to see the expert's answer here.", Theme.muted())));
            lines.add(Line
                    .from(Span.styled("Calls the expert directly; no route or state selector is executed.", Theme.muted())));
        } else {
            lines.addAll(SemanticResultView.lines(response, operation, submittedScoreLevels));
        }
        if (inputChanged()) {
            lines.add(0, Line.from(Span.styled("Input changed · run again to update this result", Theme.warning())));
        }
        return lines;
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "leave");
        hint(spans, "Ctrl+r", !connected() ? "disconnected" : pending == null ? "run" : "running");
        hint(spans, "Tab/⇧Tab", "field");
        hint(spans, "Ctrl+l", "clear");
        if (field == 1) {
            parameters.renderFooter(spans);
        }
        hint(spans, "Ctrl+e", expanded ? "restore" : "expand");
        if (field == 0) {
            if (supports("text") && supports("structured")) {
                hint(spans, "Ctrl+t", "text/JSON");
            }
            hintLast(spans, "Enter", "new line");
        } else if (field == 2) {
            hintLast(spans, "↑↓", "scroll output");
        }
    }
}
