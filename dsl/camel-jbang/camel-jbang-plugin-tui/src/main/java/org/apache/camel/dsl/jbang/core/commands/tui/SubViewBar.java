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
 * The views of a tab as one line above its content, with the view settings at the right: Architecture › Topology ›
 * Route in the Diagram tab, History │ Waterfall │ Diagram in the Inspect tab. The view shown is highlighted, the others
 * are dim but visible so the user knows they exist, and a view that does not apply yet is greyed out. A click on a view
 * goes there; a click on a setting presses its key, as a click on a footer hint does.
 */
final class SubViewBar {

    /**
     * One view of a tab.
     *
     * @param label   what the bar shows
     * @param active  whether it is the view shown
     * @param enabled whether it applies now (a route view needs a route)
     * @param select  goes to the view; null when it cannot be chosen by a click
     */
    record View(String label, boolean active, boolean enabled, Runnable select) {
    }

    /** A view setting: its key, name and state (on, off, or a mode such as a filter value). */
    record Toggle(String key, String label, String state) {

        boolean on() {
            return !"off".equals(state);
        }
    }

    /**
     * What a tab shows in the bar.
     *
     * @param cycleKey the key that moves through the views, or null
     * @param levels   whether the views are levels of one another (separated by ›) rather than peers (│)
     */
    record Spec(String cycleKey, List<View> views, List<Toggle> toggles, boolean levels) {
    }

    private record Hit(int from, int to, View view, String key) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private int row = -1;

    /** Draws the bar in a one-row area. */
    void render(Frame frame, Rect area, Spec spec) {
        hits.clear();
        row = area.y();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" "));
        int x = area.x() + 1;
        if (spec.cycleKey() != null) {
            String key = " " + spec.cycleKey() + " ";
            spans.add(Span.styled(key, Theme.hintKey()));
            spans.add(Span.raw(" "));
            x += CharWidth.of(key) + 1;
        }
        String separator = spec.levels() ? "  ›  " : "  │  ";
        List<View> views = spec.views();
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            if (i > 0) {
                spans.add(Span.styled(separator, Theme.muted()));
                x += CharWidth.of(separator);
            }
            String text = (v.active() ? "◆ " : "◇ ") + v.label();
            Style style = v.active() ? Theme.accentBg()
                    : v.enabled() ? Style.EMPTY.fg(Theme.baseFg()) : Theme.muted().dim();
            String shown = v.active() ? " " + text + " " : text;
            spans.add(Span.styled(shown, style));
            int w = CharWidth.of(shown);
            if (v.enabled() && !v.active() && v.select() != null) {
                hits.add(new Hit(x, x + w, v, null));
            }
            x += w;
        }
        int used = x - area.x();
        // the settings at the right, as many as fit
        List<Span> right = new ArrayList<>();
        List<int[]> rightBoxes = new ArrayList<>();
        List<String> rightKeys = new ArrayList<>();
        int rightWidth = 0;
        for (Toggle t : spec.toggles()) {
            // an on/off setting shows its state as a mark, a setting with modes names the mode
            boolean onOff = "on".equals(t.state()) || "off".equals(t.state());
            String chip = " " + t.key() + " ";
            String label = " " + t.label() + (onOff ? "" : ": " + t.state()) + " ";
            String mark = (onOff ? (t.on() ? "●" : "○") : "") + "  ";
            int w = CharWidth.of(chip) + CharWidth.of(label) + CharWidth.of(mark);
            if (used + rightWidth + w + 2 > area.width()) {
                break;
            }
            right.add(Span.styled(chip, Theme.hintKey()));
            right.add(Span.raw(label));
            right.add(Span.styled(mark, t.on() ? Style.EMPTY.fg(Theme.accent()) : Theme.muted()));
            rightBoxes.add(new int[] { rightWidth, rightWidth + w - 2 });
            rightKeys.add(t.key());
            rightWidth += w;
        }
        if (!right.isEmpty()) {
            int start = area.x() + area.width() - rightWidth;
            spans.add(Span.raw(" ".repeat(area.width() - used - rightWidth)));
            spans.addAll(right);
            for (int i = 0; i < rightBoxes.size(); i++) {
                hits.add(new Hit(start + rightBoxes.get(i)[0], start + rightBoxes.get(i)[1], null, rightKeys.get(i)));
            }
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(spans))).build(), area);
    }

    /** The view under a click on the bar, or null. */
    View viewAt(int x, int y) {
        Hit h = hitAt(x, y);
        return h != null ? h.view() : null;
    }

    /** The key of the setting under a click on the bar, or null. */
    String keyAt(int x, int y) {
        Hit h = hitAt(x, y);
        return h != null ? h.key() : null;
    }

    /** Whether the bar was drawn on the row: a click there is the bar's, even between its items. */
    boolean isOnRow(int y) {
        return row >= 0 && y == row;
    }

    /** Forgets where the bar was, when a tab without one is shown. */
    void clear() {
        hits.clear();
        row = -1;
    }

    private Hit hitAt(int x, int y) {
        if (y != row) {
            return null;
        }
        for (Hit h : hits) {
            if (x >= h.from() && x < h.to()) {
                return h;
            }
        }
        return null;
    }
}
