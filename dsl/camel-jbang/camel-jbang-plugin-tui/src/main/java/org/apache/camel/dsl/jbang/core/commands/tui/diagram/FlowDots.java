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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import org.apache.camel.dsl.jbang.core.commands.tui.Theme;

import static org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors.failColor;

/**
 * The dots of the messages on the arrows of a diagram (a route or the topology), drawn over the lines the arrows are
 * drawn with, at the pace of a {@link FlowAnimation.Track}.
 */
final class FlowDots {

    /** The cell of a line lit up as it is: no dot on it. */
    static final char LIT = 0;

    /**
     * The cells of an arrow in the order a message goes along them, and its head (a char of 0 when the line goes on to
     * the mark of a step, with no head).
     */
    record Path(String key, int fromRow, List<int[]> cells, int[] head, char headChar) {
    }

    private FlowDots() {
    }

    /**
     * The path of an arrow as the diagrams draw one: down from the source, along a row half way when the target is in
     * another column, and down to the target; with a head in the row above the target, or none.
     */
    static Path path(String key, int fromCx, int fromRow, int toCx, int toRow, char headChar) {
        int end = headChar != 0 ? toRow - 1 : toRow;
        List<int[]> cells = new ArrayList<>();
        if (fromCx == toCx) {
            for (int r = fromRow; r < end; r++) {
                cells.add(new int[] { r, fromCx });
            }
        } else {
            int midRow = fromRow + (toRow - fromRow) / 2;
            for (int r = fromRow; r < midRow; r++) {
                cells.add(new int[] { r, fromCx });
            }
            int step = toCx > fromCx ? 1 : -1;
            for (int c = fromCx; c != toCx + step; c += step) {
                cells.add(new int[] { midRow, c });
            }
            for (int r = midRow + 1; r < end; r++) {
                cells.add(new int[] { r, toCx });
            }
        }
        return new Path(key, fromRow, cells, headChar != 0 ? new int[] { toRow - 1, toCx } : null, headChar);
    }

    /**
     * The dots on an arrow that messages go along, red when most of them fail; or with {@code dots} off its line lit
     * up, where a dot would look like the mark of a step. A letter, a digit or a char of {@code keep} on the way stays.
     */
    static void draw(
            Buffer buffer, Rect area, int scrollX, int scrollY, Path path, FlowAnimation.Track t, boolean dots,
            String keep) {
        if (t == null || t.rate() <= 0) {
            return;
        }
        int spacing = FlowAnimation.spacing(t.rate());
        long moved = (long) Math.floor(t.phase());
        // the foreground of the theme: the dots are lights on the lines, whatever the colours of the steps are
        Color head = light(t);
        Color tail = mix(head, Theme.muted().fg().orElse(Color.GRAY), 0.5);
        Style dotStyle = dots ? Style.EMPTY.fg(head) : Style.EMPTY.fg(head).bold();
        List<int[]> cells = path.cells();
        // the head last: an arrow of a compact diagram is only its head, which then lights up as the dots come by
        int last = path.head() != null ? cells.size() : cells.size() - 1;
        for (int i = 0; i <= last; i++) {
            // counted from the top of the diagram: below a box the dots go on at the same pace
            if (Math.floorMod(path.fromRow() + i - moved, spacing) == 0) {
                if (i == cells.size()) {
                    dot(buffer, area, scrollX, scrollY, path.head(), path.headChar(), Style.EMPTY.fg(head).bold(),
                            path.headChar(), keep);
                } else {
                    dot(buffer, area, scrollX, scrollY, cells.get(i), dots ? '●' : LIT, dotStyle, (char) 0, keep);
                }
                if (i > 0) {
                    dot(buffer, area, scrollX, scrollY, cells.get(i - 1), dots ? '•' : LIT, Style.EMPTY.fg(tail),
                            (char) 0, keep);
                }
            }
        }
    }

    /** A dot on a line of an arrow, or its head lit up (only where the head is). */
    private static void dot(
            Buffer buffer, Rect area, int scrollX, int scrollY, int[] cell, char ch, Style style, char onHead,
            String keep) {
        int x = area.x() + cell[1] - scrollX;
        int y = area.y() + cell[0] - scrollY;
        if (x < area.left() || x >= area.right() || y < area.top() || y >= area.bottom()) {
            return;
        }
        String existing = buffer.get(x, y).symbol();
        if (onHead != 0) {
            // a head on the line of a branch is the head of that arrow, not a cell of its line
            if (!existing.equals(String.valueOf(onHead))) {
                return;
            }
        } else if (!existing.isEmpty() && (Character.isLetterOrDigit(existing.codePointAt(0))
                || keep.indexOf(existing.charAt(0)) >= 0)) {
            return;
        }
        if (ch == LIT && existing.isBlank()) {
            return;
        }
        buffer.setString(x, y, ch == LIT ? existing : String.valueOf(ch), style);
    }

    /** The colour of the dots of a track: the foreground of the theme, or red when most of the messages fail. */
    static Color light(FlowAnimation.Track t) {
        return t.failRate() * 2 > t.rate() ? failColor() : Theme.baseFg();
    }

    /**
     * The colour of a mark in a row as the light of the messages passes over it, at the pace of the dots on the lines:
     * the marks of a busy path light up one after another.
     */
    static Color lit(Color base, FlowAnimation.Track t, int row) {
        if (t == null || t.rate() <= 0) {
            return base;
        }
        int spacing = FlowAnimation.spacing(t.rate());
        int at = Math.floorMod(row - (long) Math.floor(t.phase()), spacing);
        if (at == 0) {
            return mix(base, light(t), 0.7);
        }
        return at == spacing - 1 ? mix(base, light(t), 0.35) : base;
    }

    /** The colour between two colours, {@code t} of the way from the first. */
    static Color mix(Color from, Color to, double t) {
        if (t <= 0) {
            return from;
        }
        if (terminalDefault(from) || terminalDefault(to)) {
            // the default of the terminal has no RGB of its own (toRgb gives white), so it is not mixed
            return t < 0.5 ? from : to;
        }
        Color.Rgb a = from.toRgb();
        Color.Rgb b = to.toRgb();
        return Color.rgb(
                a.r() + (int) Math.round((b.r() - a.r()) * t),
                a.g() + (int) Math.round((b.g() - a.g()) * t),
                a.b() + (int) Math.round((b.b() - a.b()) * t));
    }

    /** Whether the colour is the default of the terminal, also when a theme names it. */
    static boolean terminalDefault(Color color) {
        Color c = color;
        while (c instanceof Color.Named named) {
            c = named.defaultValue();
        }
        return c instanceof Color.Reset;
    }
}
