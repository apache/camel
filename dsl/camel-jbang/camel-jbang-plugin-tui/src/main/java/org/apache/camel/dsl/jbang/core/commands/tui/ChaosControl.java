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
import java.util.function.Function;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonObject;

/**
 * The actions of the chaos screen, through the {@code chaos} action of the CLI connector as {@code camel cmd chaos-*}
 * sends it: add a rule ({@code a}) or change the selected one ({@code e}) typed as a one-line rule, remove the selected
 * rule ({@code x}), clear all rules ({@code c}) and start a new experiment ({@code R}, a clear that also resets the
 * report). Each asks to confirm, with the rule shown; only the user answers ({@link Confirmations}), and while a tape
 * drives the monitor nothing asks ({@link MonitorContext#confirmFirst()}). The connector checks the rule as
 * {@code camel.chaos.rules} is checked: a rule that is not valid comes back with the reason, and a rule that matches
 * nothing in the integration with its warnings, shown in a dialog from which the rule can be changed.
 */
final class ChaosControl {

    /** What the control shows: nothing, the rule being typed, the question, or the outcome to read. */
    enum Step {
        NONE,
        INPUT,
        CONFIRM,
        RESULT
    }

    /** An action on the rules. */
    enum Command {
        ADD,
        CHANGE,
        REMOVE,
        CLEAR,
        RESET
    }

    private final MonitorContext ctx;
    // sends the request of the chaos action to the integration and returns its reply (null when it did not answer)
    private final Function<JsonObject, JsonObject> sender;
    // set by the background task that sends the action, read when the screen is drawn
    private volatile Step step = Step.NONE;
    private Command command;
    private String pid;
    private String name;
    private String id;
    private String rule;
    private TextInputState input;
    private String resultTitle;
    private final List<String> resultLines = new ArrayList<>();
    private boolean resultError;

    ChaosControl(MonitorContext ctx) {
        this(ctx, null);
    }

    ChaosControl(MonitorContext ctx, Function<JsonObject, JsonObject> sender) {
        this.ctx = ctx;
        this.sender = sender;
    }

    Step step() {
        return step;
    }

    boolean isTextInputActive() {
        return step == Step.INPUT;
    }

    boolean isConfirming() {
        return step == Step.CONFIRM;
    }

    boolean isOpen() {
        return step != Step.NONE;
    }

    /**
     * Handles a key of the chaos screen: the keys of an open dialog, else the action keys.
     *
     * @param  info     the selected integration
     * @param  selected the selected rule, or null
     * @return          whether the key was used
     */
    boolean handleKey(KeyEvent ke, IntegrationInfo info, ChaosInfo.Rule selected) {
        switch (step) {
            case INPUT -> {
                return handleInputKey(ke);
            }
            case CONFIRM -> {
                if (ke.isChar('y') || ke.isChar('Y')) {
                    execute();
                } else if (ke.isCancel() || ke.isChar('n') || ke.isChar('N')) {
                    close();
                }
                // the question waits for its answer: no other key goes through
                return true;
            }
            case RESULT -> {
                if (ke.isChar('e') && canChange()) {
                    // type the rule again: a refused rule as it was, a rule that matches nothing as a change of it
                    openInput(command, rule);
                } else if (ke.isCancel() || ke.isConfirm()) {
                    close();
                }
                return true;
            }
            default -> {
                return handleActionKey(ke, info, selected);
            }
        }
    }

    private boolean handleActionKey(KeyEvent ke, IntegrationInfo info, ChaosInfo.Rule selected) {
        if (info == null || info.chaos == null || !info.chaos.enabled) {
            return false;
        }
        pid = info.pid;
        name = info.name;
        if (ke.isChar('a')) {
            id = null;
            openInput(Command.ADD, "");
            return true;
        }
        if (selected != null && selected.id != null && ke.isChar('e')) {
            id = selected.id;
            openInput(Command.CHANGE, selected.text);
            return true;
        }
        if (selected != null && selected.id != null && ke.isChar('x')) {
            id = selected.id;
            rule = selected.text;
            ask(Command.REMOVE);
            return true;
        }
        if (ke.isChar('c') && !info.chaos.rules.isEmpty()) {
            ask(Command.CLEAR);
            return true;
        }
        if (ke.isChar('R')) {
            ask(Command.RESET);
            return true;
        }
        return false;
    }

    private void openInput(Command what, String text) {
        command = what;
        input = new TextInputState(text != null ? text : "");
        input.moveCursorToEnd();
        step = Step.INPUT;
    }

    private boolean handleInputKey(KeyEvent ke) {
        if (ke.isCancel()) {
            close();
        } else if (ke.isConfirm()) {
            String text = input.text().strip();
            if (!text.isEmpty()) {
                rule = text;
                ask(command);
            }
        } else {
            FormHelper.handleTextInput(ke, input);
        }
        return true;
    }

    /** Pastes into the rule being typed. */
    boolean handlePaste(String text) {
        if (step != Step.INPUT || text == null) {
            return false;
        }
        // a rule is one line
        input.insert(text.replace('\n', ' ').replace('\r', ' '));
        return true;
    }

    private void ask(Command what) {
        command = what;
        if (ctx.confirmFirst()) {
            step = Step.CONFIRM;
        } else {
            execute();
        }
    }

    /** Esc: closes the open dialog. */
    boolean escape() {
        if (step == Step.NONE) {
            return false;
        }
        close();
        return true;
    }

    private void close() {
        step = Step.NONE;
        input = null;
    }

    /** The request of the chaos action for the command, as {@code camel cmd chaos-*} sends it. */
    JsonObject request() {
        JsonObject root = new JsonObject();
        root.put("action", "chaos");
        switch (command) {
            case ADD -> {
                root.put("command", "add");
                root.put("rule", rule);
            }
            case CHANGE -> {
                root.put("command", "change");
                root.put("id", id);
                root.put("rule", rule);
            }
            case REMOVE -> {
                root.put("command", "remove");
                root.put("id", id);
            }
            case CLEAR -> root.put("command", "clear");
            case RESET -> {
                root.put("command", "clear");
                root.put("reset", true);
            }
            default -> throw new IllegalStateException("Unknown command " + command);
        }
        return root;
    }

    /** Sends the action in the background (the integration answers within seconds), and shows how it went. */
    private void execute() {
        JsonObject request = request();
        String target = pid;
        step = Step.NONE;
        Runnable task = () -> {
            JsonObject reply = sender != null ? sender.apply(request) : ctx.executeAction(target, request, 5000);
            outcome(reply);
        };
        if (sender != null) {
            // the tests answer at once
            task.run();
        } else {
            ctx.backgroundExecutor.execute(task);
        }
    }

    /** How the action went: a notification, or a dialog with the reason of a refusal or the warnings of the rule. */
    void outcome(JsonObject reply) {
        resultLines.clear();
        if (reply == null) {
            result("Chaos", true, "The integration " + name + " did not answer");
            return;
        }
        String error = reply.getString("error");
        if (error != null || "failed".equals(reply.getString("status"))) {
            result(command == Command.CHANGE ? "Chaos rule not changed" : "Chaos rule not added", true,
                    error != null ? error : "The integration refused it");
            return;
        }
        String done = switch (command) {
            case ADD -> "Added chaos rule " + idsOf(reply);
            case CHANGE -> "Changed chaos rule " + id + " (version " + reply.get("version") + ")";
            case REMOVE -> "Removed chaos rule " + id;
            case CLEAR -> "Removed all chaos rules";
            case RESET -> "New chaos experiment: no rules, a new report";
        };
        if (reply.get("warnings") instanceof List<?> warnings && !warnings.isEmpty()) {
            List<String> lines = new ArrayList<>();
            lines.add(done + ", with a warning: it matches nothing in " + name + " now (a typo?)");
            warnings.forEach(w -> lines.add("  " + w));
            if (command == Command.ADD) {
                // change the rule that was just added
                id = reply.getString("id");
            }
            command = command == Command.ADD && id != null ? Command.CHANGE : command;
            result("Chaos rule matches nothing", false, lines.toArray(new String[0]));
            return;
        }
        if (ctx.notificationCallback != null) {
            ctx.notificationCallback.accept(done, false);
        }
    }

    private static String idsOf(JsonObject reply) {
        if (reply.getString("id") != null) {
            return reply.getString("id");
        }
        return reply.get("ids") instanceof List<?> ids ? String.join(", ", ids.stream().map(String::valueOf).toList()) : "";
    }

    private void result(String title, boolean error, String... lines) {
        resultTitle = title;
        resultError = error;
        resultLines.clear();
        resultLines.addAll(List.of(lines));
        step = Step.RESULT;
    }

    String resultText() {
        return String.join("\n", resultLines);
    }

    // ---- rendering ----

    void render(Frame frame, Rect area) {
        switch (step) {
            case INPUT -> DialogHelper.renderInputDialog(frame, area,
                    command == Command.CHANGE ? "Change chaos rule " + id : "Add a chaos rule to " + name, input,
                    "endpoint=kafka:orders* error rate=0.1 until=5m");
            case CONFIRM -> DialogHelper.renderConfirm(frame, area, title(), question(), detail(), "y", "yes", null, null);
            case RESULT -> renderResult(frame, area);
            default -> {
                // nothing open
            }
        }
    }

    private String title() {
        return switch (command) {
            case ADD -> "Add Chaos Rule";
            case CHANGE -> "Change Chaos Rule";
            case REMOVE -> "Remove Chaos Rule";
            case CLEAR -> "Clear Chaos Rules";
            case RESET -> "New Chaos Experiment";
        };
    }

    /** The question of the confirmation. */
    String question() {
        return switch (command) {
            case ADD -> "Add this chaos rule to " + name + "? It injects faults while it runs";
            case CHANGE -> "Change chaos rule " + id + " of " + name + " to this rule?";
            case REMOVE -> "Remove chaos rule " + id + " from " + name + "?";
            case CLEAR -> "Remove all chaos rules from " + name + "? The report keeps what they did";
            case RESET -> "Start a new chaos experiment in " + name + "? No rules, and a new report (same seed)";
        };
    }

    private String detail() {
        return switch (command) {
            case ADD, CHANGE, REMOVE -> rule;
            default -> null;
        };
    }

    private void renderResult(Frame frame, Rect area) {
        Style accent = resultError ? Theme.error() : Theme.warning();
        int width = Math.max(40, Math.min(area.width() - 4, 110));
        List<Line> lines = new ArrayList<>();
        lines.add(Line.empty());
        for (String text : resultLines) {
            lines.add(Line.from(Span.styled(" " + text, text.startsWith("  ") ? Theme.muted() : accent)));
        }
        if (rule != null && command != Command.REMOVE) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled(" " + rule, Theme.muted())));
        }
        lines.add(Line.empty());
        lines.add(canChange()
                ? TuiHelper.hintLine("e", "change it", "Esc", "close")
                : TuiHelper.hintLine("Esc", "close"));
        // the reason of a refusal can be long: it wraps
        int height
                = Math.min(area.height() - 2, lines.size() + 2 + resultLines.stream().mapToInt(l -> l.length() / width).sum());
        Rect popup = DialogHelper.centered(area, width, Math.max(6, height));
        frame.renderWidget(Clear.INSTANCE, popup);
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines))
                .overflow(Overflow.WRAP_WORD)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).borderStyle(accent)
                        .title(" " + resultTitle + " ").build())
                .build(), popup);
    }

    private boolean canChange() {
        return rule != null && (command == Command.ADD || command == Command.CHANGE);
    }

    /** The keys of the screen, or of the open dialog, for the footer. */
    void renderFooter(List<Span> spans, boolean hasRules) {
        switch (step) {
            case INPUT -> {
                TuiHelper.hint(spans, "Enter", "confirm");
                TuiHelper.hint(spans, "Esc", "cancel");
            }
            case CONFIRM -> {
                TuiHelper.hint(spans, "y", "yes");
                TuiHelper.hint(spans, "Esc", "no");
            }
            case RESULT -> TuiHelper.hint(spans, "Esc", "close");
            default -> {
                TuiHelper.hint(spans, "Esc", "back");
                TuiHelper.hint(spans, "a", "add");
                if (hasRules) {
                    TuiHelper.hint(spans, "e", "change");
                    TuiHelper.hint(spans, "x", "remove");
                    TuiHelper.hint(spans, "c", "clear");
                }
                TuiHelper.hint(spans, "R", "new experiment");
            }
        }
    }
}
