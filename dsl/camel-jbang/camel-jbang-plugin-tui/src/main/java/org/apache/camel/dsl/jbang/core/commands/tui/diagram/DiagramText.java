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
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;

/**
 * Text in the diagrams, measured by display width as the terminal shows it: a CJK character or an emoji takes two
 * columns, a combining mark none.
 */
final class DiagramText {

    // a label is broken after these where it can
    private static final String BREAK_AFTER = " :/.,&?";

    private DiagramText() {
    }

    /**
     * The text on lines of at most the width, broken after a space or one of {@code : / . , & ?} where it can, and cut
     * with ... on the last line when it needs more than {@code maxLines} lines.
     */
    static List<String> wrap(String text, int maxWidth, int maxLines) {
        if (maxWidth <= 0 || CharWidth.of(text) <= maxWidth) {
            return new ArrayList<>(List.of(text));
        }
        List<String> lines = new ArrayList<>();
        String remaining = text;
        // the text from where the last line starts, for cutting it with ...
        String lastLineText = text;
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (CharWidth.of(remaining) <= maxWidth) {
                lines.add(remaining);
                remaining = "";
                break;
            }
            int fit = CharWidth.substringByWidth(remaining, maxWidth).length();
            int breakAt = -1;
            for (int i = 0; i < fit; i++) {
                if (BREAK_AFTER.indexOf(remaining.charAt(i)) >= 0) {
                    breakAt = i + 1;
                }
            }
            if (breakAt <= 0) {
                // no place to break within the width: the word is cut, by one character at least
                breakAt = Math.max(fit, Character.charCount(remaining.codePointAt(0)));
            }
            lastLineText = remaining;
            lines.add(remaining.substring(0, breakAt).stripTrailing());
            remaining = remaining.substring(breakAt).stripLeading();
        }
        if (!remaining.isEmpty()) {
            // the last line goes on as the text does, with the space or the : it was broken at
            lines.set(lines.size() - 1, CharWidth.substringByWidth(lastLineText, Math.max(1, maxWidth - 3)) + "...");
        }
        return lines;
    }

    /**
     * Writes the text from column {@code x} of the screen, cut by display width to the columns of the area on both
     * sides (the text may start left of the area when the diagram is scrolled).
     */
    static void write(Buffer buffer, Rect area, int x, int y, String text, Style style) {
        if (y < area.top() || y >= area.bottom() || x >= area.right()) {
            return;
        }
        String visible = text;
        if (x < area.left()) {
            // the characters left of the area are skipped; a wide one cut by the edge goes too and leaves its column in
            // the area blank, so the rest of the text keeps its columns
            int i = 0;
            while (i < text.length() && x < area.left()) {
                int cp = text.codePointAt(i);
                x += CharWidth.of(cp);
                i += Character.charCount(cp);
            }
            if (x >= area.right()) {
                return;
            }
            visible = text.substring(i);
        }
        visible = CharWidth.substringByWidth(visible, area.right() - x);
        if (!visible.isEmpty()) {
            buffer.setString(x, y, visible, style);
        }
    }

    /** The text cut to the width with an ellipsis (…) when it is wider. */
    static String fit(String text, int width) {
        return CharWidth.truncateWithEllipsis(text, Math.max(1, width), "…", CharWidth.TruncatePosition.END);
    }
}
