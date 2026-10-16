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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * Asks the user to confirm what an AI tool wants to do: write a file, or one of the calls {@link ToolConfirms} names (a
 * message to an endpoint the integration does not have, SQL that is not a SELECT, an expression in a language that runs
 * code). For a file the summary names the file, the directory and the size of the change; {@code d} switches to a
 * unified diff (removed lines on red, added lines on green, like the source editor's F7 overlay), so the user sees what
 * the model wants to change before deciding. The diff is a view only: Enter, Esc and d return to the summary. For a
 * call the popup shows the endpoint, the statement or the expression, and why it asks. Enter applies or runs it, Esc
 * rejects it.
 */
final class ToolConfirmPopup {

    // how long a tool waits for the user before it gives up, as rejected
    private static final long WAIT_MINUTES = 2;
    // the lines of a statement or an expression the popup shows
    private static final int MAX_TARGET_LINES = 6;

    // two tools that ask at once wait in turn, rather than one replacing the other's question
    private final Object asking = new Object();
    private volatile boolean visible;
    private McpFacade.FileWrite request;
    private ToolConfirms.Call call;
    private CompletableFuture<Boolean> answer;
    private List<EditDiff.DiffEntry> entries = List.of();
    private String summary = "";
    private boolean showDiff;
    private int diffScrollY;
    private int diffPageSize = 10;

    boolean isVisible() {
        return visible;
    }

    boolean isDiffVisible() {
        return visible && showDiff;
    }

    /** Asks to apply a file write, blocking the calling (tool) thread until the user answers or no one does. */
    boolean ask(McpFacade.FileWrite request) {
        synchronized (asking) {
            CompletableFuture<Boolean> pending = new CompletableFuture<>();
            open(request, pending);
            return await(pending);
        }
    }

    /** Asks to run a call, blocking the calling (tool) thread until the user answers or no one does. */
    boolean ask(ToolConfirms.Call call) {
        synchronized (asking) {
            CompletableFuture<Boolean> pending = new CompletableFuture<>();
            open(call, pending);
            return await(pending);
        }
    }

    private boolean await(CompletableFuture<Boolean> pending) {
        try {
            return pending.get(WAIT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            close();
            return false;
        } catch (Exception e) {
            close();
            return false;
        }
    }

    void open(McpFacade.FileWrite request, CompletableFuture<Boolean> answer) {
        List<String> before = request.oldContent() == null || request.oldContent().isEmpty()
                ? List.of() : request.oldContent().lines().toList();
        List<String> after = request.newContent().isEmpty() ? List.of() : request.newContent().lines().toList();
        this.entries = EditDiff.unifiedDiff(before, after, 3);
        this.summary = EditDiff.summary(entries);
        this.call = null;
        this.request = request;
        this.answer = answer;
        this.showDiff = false;
        this.diffScrollY = 0;
        this.visible = true;
    }

    void open(ToolConfirms.Call call, CompletableFuture<Boolean> answer) {
        this.entries = List.of();
        this.summary = "";
        this.request = null;
        this.call = call;
        this.answer = answer;
        this.showDiff = false;
        this.visible = true;
    }

    /** Closes without an answer (the tool gave up waiting); a pending future is completed as rejected. */
    void close() {
        visible = false;
        if (answer != null && !answer.isDone()) {
            answer.complete(false);
        }
    }

    String summaryForTesting() {
        return summary;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (showDiff) {
            // the diff is a view: any of these keys returns to the summary, where the decision is made
            if (ke.isConfirm() || ke.isCancel() || ke.isChar('d') || ke.isChar('D')) {
                showDiff = false;
            } else if (ke.isUp()) {
                diffScrollY = Math.max(0, diffScrollY - 1);
            } else if (ke.isDown()) {
                diffScrollY++;
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                diffScrollY = Math.max(0, diffScrollY - diffPageSize);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                diffScrollY += diffPageSize;
            }
            return true;
        }
        if (ke.isConfirm()) {
            finish(true);
        } else if (ke.isCancel()) {
            finish(false);
        } else if (request != null && (ke.isChar('d') || ke.isChar('D'))) {
            showDiff = true;
            diffScrollY = 0;
        }
        return true;
    }

    private void finish(boolean apply) {
        visible = false;
        CompletableFuture<Boolean> pending = answer;
        answer = null;
        if (pending != null) {
            pending.complete(apply);
        }
    }

    void render(Frame frame, Rect area) {
        if (!visible) {
            return;
        }
        if (call != null) {
            renderCall(frame, area);
            return;
        }
        if (request == null) {
            return;
        }
        Style accent = Theme.warning();
        String action = request.oldContent() == null ? "create" : "overwrite";
        String titleText = " AI wants to " + action + " " + request.file() + "  " + summary + " ";
        if (showDiff) {
            renderDiff(frame, area, accent, titleText);
            return;
        }
        List<Line> lines = new ArrayList<>();
        lines.add(Line.empty());
        lines.add(Line.from(Span.styled(request.directory().toString(), Style.EMPTY.dim())));
        List<Span> facts = new ArrayList<>();
        facts.add(Span.styled(summary + " lines", accent.bold()));
        if (request.temporary()) {
            facts.add(Span.styled("   temporary copy", Theme.muted()));
        }
        if (request.devMode()) {
            facts.add(Span.styled("   reloads in dev mode", Theme.muted()));
        }
        lines.add(Line.from(facts));
        lines.add(Line.empty());
        List<Span> hints = new ArrayList<>();
        hint(hints, "Enter", "apply");
        hint(hints, "d", "diff");
        hintLast(hints, "Esc", "reject");
        lines.add(Line.from(hints));
        renderBox(frame, area, accent, titleText, lines);
    }

    /** The endpoint, the statement or the expression, why it asks, and the keys. */
    private void renderCall(Frame frame, Rect area) {
        Style accent = Theme.warning();
        String titleText = " AI wants to " + call.title() + " ";
        int width = Math.max(10, area.width() - 10);
        List<Line> lines = new ArrayList<>();
        lines.add(Line.empty());
        List<String> target = call.target() == null ? List.of() : call.target().strip().lines().toList();
        for (String line : target.subList(0, Math.min(target.size(), MAX_TARGET_LINES))) {
            lines.add(Line.from(Span.styled(TuiHelper.truncate(line, width), accent.bold())));
        }
        if (target.size() > MAX_TARGET_LINES) {
            lines.add(Line.from(Span.styled("... " + (target.size() - MAX_TARGET_LINES) + " more lines", Theme.muted())));
        }
        lines.add(Line.from(Span.styled(call.reason(), Style.EMPTY.dim())));
        lines.add(Line.empty());
        List<Span> hints = new ArrayList<>();
        hint(hints, "Enter", "run");
        hintLast(hints, "Esc", "reject");
        lines.add(Line.from(hints));
        renderBox(frame, area, accent, titleText, lines);
    }

    private static void renderBox(Frame frame, Rect area, Style accent, String titleText, List<Line> lines) {
        int longest = lines.stream().mapToInt(l -> l.toString().length()).max().orElse(20);
        int popupW = Math.max(1, Math.min(area.width() - 4, Math.max(longest + 6, titleText.length() + 4)));
        int popupH = Math.min(area.height() - 2, lines.size() + 2);
        Rect popup = DialogHelper.centered(area, popupW, popupH);
        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(accent)
                .title(Title.from(Line.from(Span.styled(titleText, accent.bold()))))
                .build();
        frame.renderWidget(block, popup);
        frame.renderWidget(Paragraph.builder().centered().text(Text.from(lines.toArray(Line[]::new))).build(),
                block.inner(popup));
    }

    private void renderDiff(Frame frame, Rect area, Style accent, String titleText) {
        int popupW = Math.max(20, Math.min(area.width() - 4, 120));
        int popupH = Math.max(6, Math.min(area.height() - 2, entries.size() + 2));
        Rect popup = DialogHelper.centered(area, popupW, popupH);
        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(accent)
                .title(Title.from(Line.from(Span.styled(titleText, accent.bold()))))
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);
        diffPageSize = Math.max(1, inner.height() - 1);
        diffScrollY = EditDiff.render(frame, inner, entries, diffScrollY);
    }

    void renderFooter(List<Span> spans) {
        if (call != null) {
            hint(spans, "Enter", "run");
            hintLast(spans, "Esc", "reject");
        } else if (showDiff) {
            hint(spans, "↑↓/PgUp/PgDn", "scroll");
            hintLast(spans, "Enter/Esc", "back");
        } else {
            hint(spans, "Enter", "apply");
            hint(spans, "d", "diff");
            hintLast(spans, "Esc", "reject");
        }
    }
}
