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
import java.util.function.Supplier;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.block.Title;

/**
 * The MCP Log: the calls MCP clients made to the TUI's MCP server, with the request and response of each.
 */
class McpLogPopup extends ActivityLogPopup<TuiMcpServer.LogEntry> {

    private Supplier<Integer> toolCallCount;

    void setToolCallCount(Supplier<Integer> toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    @Override
    protected String title() {
        return " MCP Log ";
    }

    @Override
    protected String emptyMessage() {
        return "No MCP activity yet.";
    }

    @Override
    protected Title listTitle() {
        int count = toolCallCount != null ? toolCallCount.get() : 0;
        if (count > 0) {
            return Title.from(Line.from(
                    Span.styled(title(), Style.EMPTY.bold()),
                    Span.styled("(" + count + " calls) ", Style.EMPTY.dim())));
        }
        return Title.from(Line.from(Span.styled(title(), Style.EMPTY.bold())));
    }

    @Override
    protected Line entryLine(TuiMcpServer.LogEntry entry) {
        Style levelStyle = switch (entry.level()) {
            case CONNECT -> Theme.success();
            case TOOL -> Style.EMPTY.fg(Theme.accent());
            case ERROR -> Theme.error();
            default -> Theme.success();
        };
        String levelTag = switch (entry.level()) {
            case CONNECT -> " CONNECT ";
            case TOOL -> " TOOL    ";
            case ERROR -> " ERROR   ";
            default -> " INFO    ";
        };
        return entryLine(entry.timestamp(), levelTag, levelStyle, entry.message());
    }

    @Override
    protected void renderDetail(Frame frame, Rect area, TuiMcpServer.LogEntry entry) {
        List<Line> lines = new ArrayList<>();
        if (entry.requestBody() != null) {
            lines.add(Line.from(Span.styled(TuiIcons.ARROW_RIGHT + " Request", Theme.label().bold())));
            addJsonLines(lines, entry.requestBody());
            lines.add(Line.from(Span.raw("")));
        }
        if (entry.responseBody() != null) {
            lines.add(Line.from(Span.styled(TuiIcons.ARROW_LEFT + " Response", Theme.success().bold())));
            addJsonLines(lines, entry.responseBody());
        }
        if (entry.requestBody() == null && entry.responseBody() == null) {
            lines.add(Line.from(Span.styled("(no request/response data)", Style.EMPTY.dim())));
        }
        renderDetailLines(frame, area, lines);
    }
}
