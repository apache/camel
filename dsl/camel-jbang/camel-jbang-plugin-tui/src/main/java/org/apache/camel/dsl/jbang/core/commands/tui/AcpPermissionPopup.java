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

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
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
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Asks the user to answer an ACP {@code session/request_permission}: shows the tool title, kind and the first lines of
 * its raw input, then the options exactly as the agent sent them. Enter selects, Esc picks a reject-once option when
 * the agent offers one and otherwise answers "cancelled".
 */
final class AcpPermissionPopup {

    private static final int MAX_INPUT_LINES = 8;

    /** {@code optionId} is null when the request should be answered "cancelled". */
    record Decision(String optionId) {
    }

    // open() is called from the ACP request thread while isVisible()/render()/handleKeyEvent() run on the TUI event
    // thread; volatile so the write to visible (the last statement of open()) publishes the other fields set above it.
    private volatile boolean visible;
    private String title = "";
    private String kind = "";
    private List<String> inputLines = List.of();
    private List<JsonObject> options = List.of();
    private final ListState listState = new ListState();
    private Decision pending;
    private Rect listRect;

    boolean isVisible() {
        return visible;
    }

    Rect listRectForTesting() {
        return listRect;
    }

    void open(JsonObject toolCall, List<JsonObject> options) {
        this.title = String.valueOf(toolCall.getStringOrDefault("title", "Use tool?"));
        this.kind = String.valueOf(toolCall.getStringOrDefault("kind", "other"));
        this.inputLines = formatInput(toolCall.get("rawInput"));
        this.options = new ArrayList<>(options);
        listState.selectFirst();
        pending = null;
        visible = true;
    }

    void close() {
        visible = false;
    }

    Decision consumeDecision() {
        Decision decision = pending;
        pending = null;
        return decision;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (!visible) {
            return false;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            handleKeyEvent(KeyEvent.ofKey(KeyCode.UP));
        } else if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN));
        } else if (me.isClick() && listRect != null && listRect.contains(me.x(), me.y())) {
            // only a click on an option row answers; a click elsewhere in the dialog must not grant anything.
            // listRect is already the list's own content area (no border to skip), unlike the bordered popup
            // rects TuiHelper.listItemAt expects everywhere else it is called, so the row is computed directly.
            int idx = listState.offset() + (me.y() - listRect.y());
            if (idx >= 0 && idx < options.size()) {
                listState.select(idx);
                handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
            }
        }
        return true;
    }

    void handleKeyEvent(KeyEvent ke) {
        if (ke.isCancel()) {
            pending = new Decision(firstOptionOfKind("reject_once"));
            close();
        } else if (ke.isUp()) {
            listState.selectPrevious();
        } else if (ke.isDown()) {
            listState.selectNext(options.size());
        } else if (ke.isConfirm()) {
            Integer selected = listState.selected();
            if (selected != null && selected < options.size()) {
                pending = new Decision(options.get(selected).getString("optionId"));
                close();
            }
        }
    }

    private String firstOptionOfKind(String optionKind) {
        for (JsonObject option : options) {
            if (optionKind.equals(option.getString("kind"))) {
                return option.getString("optionId");
            }
        }
        return null;
    }

    void render(Frame frame, Rect area) {
        int headerRows = 2 + inputLines.size() + 1;
        int popupW = Math.max(20, Math.min(80, area.width() - 4));
        int popupH = Math.max(6, Math.min(2 + headerRows + options.size(), area.height() - 2));
        Rect popup = DialogHelper.centered(area, popupW, popupH);
        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Permission ")
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);
        // the choices come first: on a short screen the header is clipped (title first, so it survives longest)
        int optionRows = Math.min(options.size(), Math.max(1, inner.height() - 1));
        int headerHeight = Math.max(0, Math.min(headerRows, inner.height() - optionRows));
        List<Rect> parts = Layout.vertical()
                .constraints(Constraint.length(headerHeight), Constraint.fill())
                .split(inner);

        List<Line> header = new ArrayList<>();
        header.add(Line.from(Span.styled(title, Style.EMPTY.bold())));
        header.add(Line.from(Span.styled("kind: " + kind, Style.EMPTY.dim())));
        for (String line : inputLines) {
            header.add(Line.from(Span.styled(line, Style.EMPTY.dim())));
        }
        header.add(Line.from(Span.styled("Allow the agent to run this tool?", Style.EMPTY)));
        frame.renderWidget(Paragraph.builder().text(Text.from(header.toArray(Line[]::new))).build(), parts.get(0));

        List<ListItem> items = new ArrayList<>();
        for (JsonObject option : options) {
            items.add(ListItem.from(Line.from(Span.styled(" " + option.getStringOrDefault("name", "?"), Style.EMPTY))));
        }
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .build();
        this.listRect = parts.get(1);
        frame.renderStatefulWidget(list, parts.get(1), listState);
    }

    void renderFooter(List<Span> spans) {
        TuiHelper.hint(spans, "Enter", "select");
        TuiHelper.hint(spans, "Ctrl+C", "cancel turn");
        TuiHelper.hintLast(spans, "Esc", "reject");
    }

    private static List<String> formatInput(Object rawInput) {
        if (rawInput == null) {
            return List.of();
        }
        String[] lines = Jsoner.prettyPrint(Jsoner.serialize(rawInput)).split("\n");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.length && i < MAX_INPUT_LINES; i++) {
            out.add(lines[i]);
        }
        if (lines.length > MAX_INPUT_LINES) {
            out.add("…");
        }
        return out;
    }
}
