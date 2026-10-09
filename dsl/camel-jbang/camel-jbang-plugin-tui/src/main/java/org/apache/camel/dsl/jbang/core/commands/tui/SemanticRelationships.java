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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.*;

/** A paged reference map with one shared target card per expert operation. */
final class SemanticRelationships {
    private record Hit(Rect area, int index) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private int offset;

    void reset() {
        offset = 0;
        hits.clear();
    }

    int clicked(int x, int y) {
        return hits.stream().filter(hit -> TuiHelper.contains(hit.area(), x, y)).mapToInt(Hit::index).findFirst().orElse(-1);
    }

    void render(
            Frame frame, Rect area, JsonObject data, List<JsonObject> definitions, Integer selected, Block block,
            String emptyMessage) {
        hits.clear();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        inner = new Rect(inner.x() + 1, inner.y(), Math.max(0, inner.width() - 2), inner.height());
        if (inner.width() < 38 || inner.height() < 10) {
            frame.renderWidget(Paragraph.from("Enlarge the relationship pane"), inner);
            return;
        }
        if (definitions.isEmpty()) {
            frame.renderWidget(Paragraph.from(emptyMessage), inner);
            return;
        }
        int capacity = Math.max(1, (inner.height() - 3) / 7);
        int index = selected == null ? 0 : selected;
        if (index < offset) {
            offset = index;
        } else if (index >= offset + capacity) {
            offset = index - capacity + 1;
        }
        offset = Math.max(0, Math.min(offset, Math.max(0, definitions.size() - capacity)));
        int end = Math.min(definitions.size(), offset + capacity);
        int gap = 10;
        int leftWidth = (inner.width() - gap) * 45 / 100;
        int leftEnd = inner.x() + leftWidth;
        int junction = leftEnd + 5;
        int rightX = leftEnd + gap;
        int rightWidth = inner.width() - leftWidth - gap;
        put(frame, inner.x() + 1, inner.y(), "DEFINITIONS", Theme.label());
        put(frame, rightX + 1, inner.y(), "EXPERT / OPERATION", Theme.label());
        for (int start = offset; start < end;) {
            JsonObject first = definitions.get(start);
            int groupEnd = start + 1;
            while (groupEnd < end && sameTarget(first, definitions.get(groupEnd))) {
                groupEnd++;
            }
            int firstY = inner.y() + 4 + (start - offset) * 7;
            int lastY = inner.y() + 4 + (groupEnd - 1 - offset) * 7;
            int targetY = (firstY + lastY) / 2;
            boolean groupActive = index >= start && index < groupEnd;
            Style groupStyle = groupActive ? Style.EMPTY.fg(Theme.accent()) : Theme.border();
            for (int y = firstY; y <= lastY; y++) {
                put(frame, junction, y, "│", groupStyle);
            }
            for (int i = start; i < groupEnd; i++) {
                int y = inner.y() + 4 + (i - offset) * 7;
                boolean active = i == index;
                Style style = active ? Style.EMPTY.fg(Theme.accent()) : Theme.border();
                Rect node = new Rect(inner.x(), y - 2, leftWidth, 4);
                List<Line> lines = List.of(Line.from(Span.styled(" " + text(definitions.get(i), "name"),
                        active ? Theme.selectionBg() : Style.EMPTY.bold())),
                        Line.from(Span.styled(" " + text(definitions.get(i), "state"), Theme.info())));
                node(frame, node, lines, style, active);
                hits.add(new Hit(node, i));
                put(frame, leftEnd, y - 1, " uses", active ? Theme.label() : Theme.muted());
                put(frame, leftEnd, y, "─────", style);
                put(frame, junction, y, groupEnd - start == 1 ? "─" : y == firstY ? "┐" : y == lastY ? "┘" : "┤", style);
            }
            put(frame, junction, targetY, groupEnd - start == 1 ? "────▶" : "├───▶", groupStyle);
            JsonObject op = operation(data, first);
            long count = SemanticTab.objects(data, "evaluations").stream().filter(d -> sameTarget(first, d)).count();
            Rect target = new Rect(rightX, targetY - 2, rightWidth, 6);
            node(frame, target, List.of(
                    Line.from(Span.styled(" " + text(first, "expert"), groupActive ? Theme.title() : Style.EMPTY.bold())),
                    Line.from(" " + text(first, "operation") + "(" + inputTypes(op) + ")"),
                    Line.from(Span.styled(" → " + text(first, "resultType"), Theme.info())),
                    Line.from(Span.styled(" " + count + (count == 1 ? " definition" : " definitions"), Theme.muted()))),
                    groupStyle, false);
            hits.add(new Hit(target, groupActive ? index : start));
            start = groupEnd;
        }
        String footer = (offset + 1) + "–" + end + "/" + definitions.size() + " · references, not execution flow";
        paragraph(frame, new Rect(inner.x(), inner.bottom() - 1, inner.width(), 1),
                List.of(Line.from(Span.styled(footer, Theme.muted()))), 0);
    }

    private static boolean sameTarget(JsonObject a, JsonObject b) {
        return Objects.equals(a.get("expert"), b.get("expert")) && Objects.equals(a.get("operation"), b.get("operation"));
    }

    private static void node(Frame frame, Rect area, List<Line> lines, Style border, boolean active) {
        frame.renderWidget(Paragraph.builder().text(Text.from(lines))
                .style(active ? Theme.selectionBg() : Style.EMPTY)
                .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).borderStyle(border).build())
                .build(), area);
    }

    private static void put(Frame frame, int x, int y, String value, Style style) {
        frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(Span.styled(value, style)))).build(),
                new Rect(x, y, value.length(), 1));
    }
}
