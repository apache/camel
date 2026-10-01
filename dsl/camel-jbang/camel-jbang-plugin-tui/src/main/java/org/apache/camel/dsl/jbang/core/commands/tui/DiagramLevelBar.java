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
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * The zoom levels of the Diagram tab as one line above the diagram (CAMEL-25147): Architecture › Topology › Route. The
 * level shown is highlighted, the others are dim but visible so the user knows they exist, and a level that does not
 * apply yet (no route selected) is greyed out. The labels carry the path: the capability a topology is filtered to, the
 * route a diagram shows. A click on a level goes there.
 */
final class DiagramLevelBar {

    enum Level {
        ARCHITECTURE,
        TOPOLOGY,
        ROUTE
    }

    /** One level as the bar shows it. */
    record Segment(Level level, String label, boolean enabled) {
    }

    /** A view setting of the level shown: its key, name and state (on, off, or a mode such as edges). */
    record Toggle(String key, String label, String state) {

        boolean on() {
            return !"off".equals(state);
        }
    }

    private static final String SEPARATOR = "  ›  ";

    private final List<int[]> hitBoxes = new ArrayList<>();
    private final List<Level> hitLevels = new ArrayList<>();
    private int row = -1;

    /** Draws the bar in a one-row area, with the view settings of the level at the right. */
    void render(Frame frame, Rect area, List<Segment> segments, Level current, List<Toggle> toggles) {
        hitBoxes.clear();
        hitLevels.clear();
        row = area.y();
        List<Span> spans = new ArrayList<>();
        // the key that moves through the levels, as the settings on the right show theirs
        String key = " v ";
        spans.add(Span.raw(" "));
        spans.add(Span.styled(key, Theme.hintKey()));
        spans.add(Span.raw(" "));
        int x = area.x() + 1 + CharWidth.of(key) + 1;
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            if (i > 0) {
                spans.add(Span.styled(SEPARATOR, Theme.muted()));
                x += CharWidth.of(SEPARATOR);
            }
            boolean active = s.level() == current;
            String text = (active ? "◆ " : "◇ ") + s.label();
            Style style = active ? Theme.accentBg()
                    : s.enabled() ? Style.EMPTY.fg(Theme.baseFg()) : Theme.muted().dim();
            String shown = active ? " " + text + " " : text;
            spans.add(Span.styled(shown, style));
            int w = CharWidth.of(shown);
            hitBoxes.add(new int[] { x, x + w });
            hitLevels.add(s.enabled() ? s.level() : null);
            x += w;
        }
        int used = x - area.x();
        // the settings at the right, as many as fit
        List<Span> right = new ArrayList<>();
        int rightWidth = 0;
        for (Toggle t : toggles) {
            // an on/off setting shows its state as a mark, a setting with modes names the mode
            boolean onOff = "on".equals(t.state()) || "off".equals(t.state());
            String chip = " " + t.key() + " ";
            String label = " " + t.label() + (onOff ? "" : ": " + t.state()) + " ";
            String mark = (onOff ? (t.on() ? "\u25cf" : "\u25cb") : "") + "  ";
            int w = CharWidth.of(chip) + CharWidth.of(label) + CharWidth.of(mark);
            if (used + rightWidth + w + 2 > area.width()) {
                break;
            }
            right.add(Span.styled(chip, Theme.hintKey()));
            right.add(Span.raw(label));
            right.add(Span.styled(mark, t.on() ? Style.EMPTY.fg(Theme.accent()) : Theme.muted()));
            rightWidth += w;
        }
        if (!right.isEmpty()) {
            spans.add(Span.raw(" ".repeat(area.width() - used - rightWidth)));
            spans.addAll(right);
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(spans))).build(), area);
    }

    /** The level under a click on the bar, or null when the click is elsewhere or on a level that does not apply. */
    Level hit(int x, int y) {
        if (y != row) {
            return null;
        }
        for (int i = 0; i < hitBoxes.size(); i++) {
            int[] box = hitBoxes.get(i);
            if (x >= box[0] && x < box[1]) {
                return hitLevels.get(i);
            }
        }
        return null;
    }
}
