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

import java.util.function.Consumer;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;

/**
 * Keeps the text the integrations send from acting on the terminal (CAMEL-25445). The TUI shows log lines, message
 * bodies and headers, exceptions and span attributes as they come. TamboUI turns a C0 control into a space, but hands a
 * C1 control (U+0080 to U+009F) to the terminal, and the browser terminal of {@code --web} acts on it, U+009B as CSI; a
 * terminal that lays out bidirectional text reorders what follows a BiDi control. So after a frame is drawn every cell
 * is cleaned, whichever tab or popup drew it: a control character in a cell of its own becomes a space, as TamboUI does
 * for C0, and a control, a BiDi control or an invisible format character joined to a cell is dropped. The joiners and
 * selectors that emoji and some scripts need (ZWJ, ZWNJ, the variation selectors) stay.
 */
final class ScreenSanitizer {

    private ScreenSanitizer() {
    }

    /** Draws a frame, then cleans it. */
    static void render(Frame frame, Consumer<Frame> draw) {
        draw.accept(frame);
        sanitize(frame.buffer());
    }

    static void sanitize(Buffer buffer) {
        Rect area = buffer.area();
        for (int y = area.top(); y < area.bottom(); y++) {
            for (int x = area.left(); x < area.right(); x++) {
                Cell cell = buffer.get(x, y);
                String symbol = cell.symbol();
                String clean = clean(symbol);
                if (!clean.equals(symbol)) {
                    buffer.set(x, y, cell.symbol(clean));
                }
            }
        }
    }

    /** The symbol of a cell without what a terminal would act on; the symbol itself when there is nothing. */
    static String clean(String symbol) {
        if (symbol.isEmpty() || symbol.length() == 1 && symbol.charAt(0) >= 0x20 && symbol.charAt(0) < 0x7F) {
            return symbol;
        }
        if (symbol.codePoints().noneMatch(ScreenSanitizer::unsafe)) {
            return symbol;
        }
        StringBuilder clean = new StringBuilder(symbol.length());
        int[] codePoints = symbol.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            if (!unsafe(codePoints[i])) {
                clean.appendCodePoint(codePoints[i]);
            } else if (i == 0) {
                // the cell keeps its width
                clean.append(' ');
            }
        }
        return clean.toString();
    }

    static boolean unsafe(int cp) {
        boolean control = cp < 0x20 || cp >= 0x7F && cp <= 0x9F;
        // marks, embeddings, overrides and isolates
        boolean bidi = cp == 0x061C || cp == 0x200E || cp == 0x200F || cp >= 0x202A && cp <= 0x202E
                || cp >= 0x2066 && cp <= 0x2069;
        // zero-width space, word joiner, byte order mark, soft hyphen, Mongolian vowel separator
        boolean invisible = cp == 0x200B || cp == 0x2060 || cp == 0xFEFF || cp == 0x00AD || cp == 0x180E;
        return control || bidi || invisible;
    }
}
