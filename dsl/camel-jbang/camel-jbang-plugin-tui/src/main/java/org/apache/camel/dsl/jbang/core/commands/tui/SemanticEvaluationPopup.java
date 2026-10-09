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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
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
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;
import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

/** An explicit sample run uses the selected live definition and an isolated exchange. */
final class SemanticEvaluationPopup {
    private final MonitorContext ctx;
    private final String pid;
    private JsonObject definition;
    private JsonObject operation;
    private String submittedInput;
    private String submittedDefinition;
    private JsonObject submittedOperation;
    private List<String> submittedScoreLevels = List.of();
    private Rect inputArea;
    private Rect resultArea;
    private final TextAreaState input
            = new TextAreaState("{\n  \"body\": \"Sample text\",\n  \"headers\": {},\n  \"variables\": {}\n}");
    private CompletableFuture<JsonObject> pending;
    private JsonObject response;
    private String error;
    private boolean resultFocused;
    private int resultScroll;

    SemanticEvaluationPopup(MonitorContext ctx, String pid, JsonObject definition, JsonObject operation) {
        this.ctx = ctx;
        this.pid = pid;
        update(definition, operation);
    }

    void update(JsonObject definition, JsonObject operation) {
        this.definition = definition;
        this.operation = operation;
        resultFocused = false;
    }

    boolean setInputValue(String name, String value) {
        refresh();
        if (pending != null) {
            return false;
        }
        if ("sample".equals(name)) {
            input.setText(value);
        } else if (List.of("sample.body", "sample.headers", "sample.variables").contains(name)) {
            try {
                if (!(Jsoner.deserialize(input.text()) instanceof JsonObject exchange)) {
                    throw new IllegalArgumentException("Sample must be a JSON object");
                }
                String key = name.substring("sample.".length());
                Object field = "body".equals(key) ? value : Jsoner.deserialize(value);
                if (!"body".equals(key) && !(field instanceof JsonObject)) {
                    throw new IllegalArgumentException(key + " must be a JSON object");
                }
                exchange.put(key, field);
                input.setText(Jsoner.prettyPrint(exchange.toJson()));
            } catch (DeserializationException e) {
                throw new IllegalArgumentException("Invalid sample JSON: " + e.getMessage(), e);
            }
        } else {
            return false;
        }
        resultFocused = false;
        return true;
    }

    JsonObject snapshot() {
        refresh();
        JsonObject result = new JsonObject();
        result.put("definition", definition.get("name"));
        result.put("expert", definition.get("expert"));
        result.put("operation", definition.get("operation"));
        result.put("input", input.text());
        result.put("focusedField", resultFocused ? "result" : "sample");
        result.put("pending", pending != null);
        result.put("connected", connected());
        result.put("submittedInput", submittedInput);
        result.put("result", response);
        result.put("error", error);
        result.put("inputChanged", inputChanged());
        return result;
    }

    private boolean inputChanged() {
        return response != null && (!input.text().equals(submittedInput) || !definition.toJson().equals(submittedDefinition)
                || !Objects.equals(operation, submittedOperation));
    }

    void handleMouseEvent(MouseEvent event) {
        if (event.isClick()) {
            if (inputArea != null && contains(inputArea, event.x(), event.y())) {
                resultFocused = false;
            } else if (resultArea != null && contains(resultArea, event.x(), event.y())) {
                resultFocused = true;
            }
        }
    }

    void handleKeyEvent(KeyEvent key) {
        refresh();
        if (key.hasCtrl() && key.isCharIgnoreCase('r')) {
            run();
        } else if (key.isKey(KeyCode.TAB)) {
            resultFocused = !resultFocused;
        } else if (resultFocused) {
            if (key.isUp() || key.isPageUp()) {
                resultScroll = Math.max(0, resultScroll - (key.isPageUp() ? 10 : 1));
            } else if (key.isDown() || key.isPageDown()) {
                resultScroll += key.isPageDown() ? 10 : 1;
            }
        } else if (pending == null) {
            if (key.hasCtrl() && key.isCharIgnoreCase('l')) {
                input.clear();
            } else {
                FormHelper.handleTextArea(key, input);
            }
        }
    }

    void handlePaste(String text) {
        if (!resultFocused && pending == null && text != null) {
            input.insert(text);
        }
    }

    private boolean connected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null && Objects.equals(pid, info.phantom ? info.linkedPid : info.pid)
                && !List.of("Terminating", "Terminated").contains(extractState(info.state));
    }

    private void run() {
        if (pending != null || !connected()) {
            return;
        }
        response = null;
        error = null;
        resultScroll = 0;
        try {
            if (!(Jsoner.deserialize(input.text()) instanceof JsonObject exchange)) {
                throw new IllegalArgumentException("Enter a JSON object with body, headers and variables.");
            }
            JsonObject request = new JsonObject();
            request.put("action", "semantic-evaluate");
            request.put("evaluation", definition.getString("name"));
            for (String key : exchange.keySet()) {
                if (!List.of("body", "headers", "variables").contains(key)) {
                    throw new IllegalArgumentException("Unknown sample field: " + key);
                }
                request.put(key, exchange.get(key));
            }
            submittedInput = input.text();
            submittedDefinition = definition.toJson();
            submittedOperation = operation;
            submittedScoreLevels = SemanticDetails.scoreLevels(operation, definition.get("parameters"));
            pending = CompletableFuture
                    .supplyAsync(() -> ctx.executeIndependentAction(pid, request, 60000), ctx.backgroundExecutor)
                    .exceptionally(SemanticResultView::failure);
        } catch (Exception invalid) {
            error = "Input error: " + invalid.getMessage();
            resultFocused = false;
        }
    }

    private void refresh() {
        if (pending != null && pending.isDone()) {
            response = pending.getNow(null);
            pending = null;
            if (response == null) {
                error = "Connection: no response within 60 seconds. The provider call may still be running.";
            } else if (!response.containsKey("status")) {
                error = "Connection: sample evaluation is unavailable in the running application.";
            }
        }
    }

    void render(Frame frame, Rect area) {
        refresh();
        int width = Math.min(110, area.width());
        int height = Math.min(32, area.height());
        Rect popup = new Rect(area.x() + (area.width() - width) / 2, area.y() + (area.height() - height) / 2, width, height);
        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Theme.accent()))
                .title(" " + truncate("Evaluate " + definition.getString("name"), popup.width() - 4) + " ").build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);
        List<Rect> panels = Layout.vertical().constraints(Constraint.length(2), Constraint.percentage(50), Constraint.fill())
                .split(inner);
        frame.renderWidget(Paragraph.builder().text(Text.from(
                Line.from(" Expert: " + definition.getString("expert") + "   Operation: " + definition.getString("operation")),
                Line.from(" State: " + definition.getString("state") + "   Ctrl+r runs the configured expert"))).build(),
                panels.get(0));
        Block inputBlock = Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED)
                .borderStyle(ctx.paneBorder(!resultFocused)).title(" Sample exchange (JSON) ").build();
        frame.renderWidget(inputBlock, panels.get(1));
        inputArea = inputBlock.inner(panels.get(1));
        TextArea editor = TextArea.builder().overflow(Overflow.WRAP_WORD).cursorStyle(Style.EMPTY.reversed()).build();
        if (!resultFocused && pending == null) {
            editor.renderWithCursor(inputArea, frame.buffer(), input, frame);
        } else {
            editor.render(inputArea, frame.buffer(), input);
        }
        List<Line> output = new ArrayList<>();
        String title = "Result";
        if (pending != null) {
            output.add(Line.from(" Running " + definition.getString("name") + "..."));
        } else if (error != null) {
            output.add(Line.from(Span.styled(error, Theme.error())));
        } else if (response != null) {
            output.addAll(SemanticResultView.lines(response, submittedOperation, submittedScoreLevels));
            if (inputChanged()) {
                output.add(0, Line.from(Span.styled("Input changed · run again to update this result", Theme.warning())));
            }
        } else {
            output.add(Line.from(" Edit the sample body, headers or variables, then press Ctrl+r."));
            output.add(Line.from(" Opening this form does not run the expert."));
        }
        if (!connected()) {
            output.add(0, Line.from(Span.styled("Application disconnected · Run is disabled; sample kept", Theme.warning())));
        }
        Block resultBlock = Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED)
                .borderStyle(ctx.paneBorder(resultFocused)).title(" " + title + " ").build();
        frame.renderWidget(resultBlock, panels.get(2));
        resultArea = resultBlock.inner(panels.get(2));
        resultScroll
                = Math.min(resultScroll, Math.max(0, hangingWrap(output, resultArea.width()).size() - resultArea.height()));
        SemanticDetails.paragraph(frame, resultArea, output, resultScroll);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "close");
        hint(spans, "Ctrl+r", !connected() ? "disconnected" : pending != null ? "running" : "run evaluation");
        if (!resultFocused) {
            hint(spans, "Ctrl+l", "clear sample");
        }
        hintLast(spans, "Tab/⇧Tab", resultFocused ? "sample" : "result");
    }
}
