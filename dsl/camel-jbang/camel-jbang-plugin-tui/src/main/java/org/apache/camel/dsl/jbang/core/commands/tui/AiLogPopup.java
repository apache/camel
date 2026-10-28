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
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.block.Block;

/**
 * The AI Log: the questions, answers and tool calls of the AI panel. An answer or a prompt is shown as Markdown, a tool
 * call with its arguments or result as JSON.
 */
class AiLogPopup extends ActivityLogPopup<AiPanel.LogEntry> {

    @Override
    protected String title() {
        return " AI Log ";
    }

    @Override
    protected String emptyMessage() {
        return "No AI activity yet. Open the AI panel (F8) and ask a question.";
    }

    @Override
    protected Line entryLine(AiPanel.LogEntry entry) {
        Style levelStyle = switch (entry.level()) {
            case QUESTION -> Style.EMPTY.fg(Theme.accent());
            case TOOL -> Theme.warning();
            case RESULT -> Theme.success();
            case RESPONSE -> Theme.notice();
            case ERROR -> Theme.error();
        };
        String levelTag = switch (entry.level()) {
            case QUESTION -> " ASK      ";
            case TOOL -> " TOOL     ";
            case RESULT -> " RESULT   ";
            case RESPONSE -> " RESPONSE ";
            case ERROR -> " ERROR    ";
        };
        return entryLine(entry.timestamp(), levelTag, levelStyle, entry.message());
    }

    @Override
    protected void renderDetail(Frame frame, Rect area, AiPanel.LogEntry entry) {
        String detail = entry.detail();
        boolean toolCall = entry.level() == AiPanel.LogLevel.TOOL || entry.level() == AiPanel.LogLevel.RESULT;
        if (detail != null && !detail.isBlank() && !toolCall) {
            // an answer or a prompt is Markdown: shown as the AI panel shows it (bold, code, lists), wrapped
            Block block = detailBlock();
            MarkdownView.Builder view = MarkdownView.builder()
                    .source(detail)
                    .styles(Theme.chatMarkdownStyles())
                    .syntaxTheme(Theme.syntaxTheme());
            Rect inner = block.inner(area);
            int scroll = clampDetailScroll(view.build().computeHeight(inner.width()) - inner.height());
            frame.renderWidget(view.scroll(scroll).block(block).build(), area);
            return;
        }
        List<Line> lines = new ArrayList<>();
        if (detail != null && !detail.isBlank()) {
            // a tool call or its result
            boolean call = entry.level() == AiPanel.LogLevel.TOOL;
            lines.add(Line.from(Span.styled(
                    call ? TuiIcons.ARROW_RIGHT + " Arguments" : TuiIcons.ARROW_LEFT + " Result",
                    (call ? Theme.warning() : Theme.success()).bold())));
            addJsonLines(lines, detail);
        } else {
            lines.add(Line.from(Span.styled("(no detail data)", Style.EMPTY.dim())));
        }
        renderDetailLines(frame, area, lines);
    }
}
