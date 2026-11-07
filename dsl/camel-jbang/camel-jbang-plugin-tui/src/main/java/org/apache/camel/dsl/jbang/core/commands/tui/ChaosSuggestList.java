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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
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
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonObject;

/**
 * What to try ({@code w} on the Chaos screen, CAMEL-26727): the rules the integration suggests per route from what its
 * routes do, the most worth trying first, each with why. Enter types the selected rule in, to change or add.
 */
final class ChaosSuggestList {

    /** What a key did to the list. */
    enum Outcome {
        NONE,
        CLOSED,
        PICKED
    }

    /** A suggestion, as the chaos action of the integration gives it. */
    record Item(String route, String kind, String title, String rule, String why, boolean tried) {
    }

    private final String name;
    private final List<Item> items = new ArrayList<>();
    // what has been tried per route, from the report (CAMEL-26728)
    private final Map<String, ChaosInfo.TriedRoute> tried;
    private int selected;

    ChaosSuggestList(String name, JsonObject reply) {
        this(name, reply, Map.of());
    }

    ChaosSuggestList(String name, JsonObject reply, Map<String, ChaosInfo.TriedRoute> tried) {
        this.name = name;
        this.tried = tried;
        if (reply != null && reply.getCollection("suggestions") != null) {
            for (Object o : reply.getCollection("suggestions")) {
                if (o instanceof JsonObject jo && jo.getString("rule") != null) {
                    items.add(new Item(
                            jo.getString("route"), jo.getString("kind"), jo.getString("title"),
                            jo.getString("rule"), jo.getString("why"), jo.getBooleanOrDefault("tried", false)));
                }
            }
        }
    }

    List<Item> items() {
        return items;
    }

    /** The selected suggestion, or null when there are none. */
    Item selected() {
        return items.isEmpty() ? null : items.get(selected);
    }

    Outcome handleKey(KeyEvent ke) {
        if (ke.isCancel()) {
            return Outcome.CLOSED;
        }
        if (ke.isUp() && selected > 0) {
            selected--;
        } else if (ke.isDown() && selected < items.size() - 1) {
            selected++;
        } else if (ke.isConfirm() && !items.isEmpty()) {
            return Outcome.PICKED;
        }
        return Outcome.NONE;
    }

    void render(Frame frame, Rect area) {
        int width = Math.min(area.width() - 4, 150);
        int height = Math.min(area.height() - 2, 30);
        Rect popup = DialogHelper.centered(area, width, height);
        frame.renderWidget(Clear.INSTANCE, popup);
        Block outer = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).borderStyle(Theme.warning())
                .title(" What to try in " + name + " ").build();
        frame.renderWidget(outer, popup);
        Rect inner = outer.inner(popup);
        if (items.isEmpty()) {
            frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(List.of(Line.empty(),
                    Line.from(Span.styled(" Nothing to suggest: the routes call nothing remote, and have no input from"
                                          + " outside, dead letter channel or hand-off to another route",
                            Theme.muted())))))
                    .build(),
                    inner);
            return;
        }
        List<Rect> cols = Layout.horizontal()
                .constraints(Constraint.length(Math.min(56, inner.width() / 2)), Constraint.fill())
                .split(inner);
        renderList(frame, cols.get(0));
        renderDetail(frame, cols.get(1));
    }

    private void renderList(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(" The most worth trying first", Style.EMPTY.bold())));
        int selectedLine = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean sel = i == selected;
            if (sel) {
                selectedLine = lines.size();
            }
            Style style = sel ? Theme.selectionBg().bold() : Style.EMPTY;
            List<Span> spans = new ArrayList<>();
            spans.add(Span.styled((sel ? " > " : "   ") + item.title(), style));
            spans.add(Span.styled("  " + item.route(), Theme.muted()));
            if (item.tried()) {
                spans.add(Span.styled("  tried", Theme.success()));
            }
            lines.add(Line.from(spans));
        }
        // keep the selected one in sight
        int scroll = Math.max(0, selectedLine - (area.height() - 2));
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).scroll(scroll)
                .block(Block.builder().borders(EnumSet.of(Borders.RIGHT)).borderStyle(Theme.border()).build()).build(), area);
    }

    private void renderDetail(Frame frame, Rect area) {
        Item item = selected();
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(" " + item.title(), Style.EMPTY.bold()),
                Span.styled("  route " + item.route() + " · " + item.kind(), Theme.muted())));
        lines.add(Line.empty());
        lines.add(Line.from(Span.raw(" " + item.why())));
        lines.add(Line.empty());
        lines.add(Line.from(Span.styled(" " + item.rule(), Theme.warning())));
        if (item.tried()) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled(" A rule of this kind was tried in route " + item.route() + " already",
                    Theme.success())));
        }
        ChaosInfo.TriedRoute route = tried.get(item.route());
        if (route != null) {
            // what has been tried in the route, and what not yet: when it is all tried, the route is done
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled(" Tried in route " + item.route() + ": ", Theme.label()),
                    Span.raw(route.tried().isEmpty() ? "nothing yet" : String.join(", ", route.tried()))));
            if (!route.notTried().isEmpty()) {
                lines.add(Line.from(Span.styled(" Not tried yet: ", Theme.label()),
                        Span.styled(String.join(", ", route.notTried()), Theme.warning())));
            }
        }
        frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(lines)).build(), area);
    }

    void renderFooter(List<Span> spans) {
        if (!items.isEmpty()) {
            TuiHelper.hint(spans, "↑↓", "choose");
            TuiHelper.hint(spans, "Enter", "type it in");
        }
        TuiHelper.hint(spans, "Esc", "close");
    }
}
