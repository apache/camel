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

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * Asks the user to apply a file write requested by an AI tool. The summary names the file, the directory and the size
 * of the change; {@code d} switches to a unified diff (removed lines on red, added lines on green, like the source
 * editor's F7 overlay), so the user sees what the model wants to change before deciding. The diff is a view only:
 * Enter, Esc and d return to the summary, where Enter applies the write and Esc rejects it.
 */
final class FileWritePopup {

    private boolean visible;
    private McpFacade.FileWrite request;
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

    void open(McpFacade.FileWrite request, CompletableFuture<Boolean> answer) {
        this.request = request;
        this.answer = answer;
        List<String> before = request.oldContent() == null || request.oldContent().isEmpty()
                ? List.of() : request.oldContent().lines().toList();
        List<String> after = request.newContent().isEmpty() ? List.of() : request.newContent().lines().toList();
        this.entries = EditDiff.unifiedDiff(before, after, 3);
        this.summary = EditDiff.summary(entries);
        this.showDiff = false;
        this.diffScrollY = 0;
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
        } else if (ke.isChar('d') || ke.isChar('D')) {
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
        if (!visible || request == null) {
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
        if (showDiff) {
            hint(spans, "↑↓/PgUp/PgDn", "scroll");
            hintLast(spans, "Enter/Esc", "back");
        } else {
            hint(spans, "Enter", "apply");
            hint(spans, "d", "diff");
            hintLast(spans, "Esc", "reject");
        }
    }
}
