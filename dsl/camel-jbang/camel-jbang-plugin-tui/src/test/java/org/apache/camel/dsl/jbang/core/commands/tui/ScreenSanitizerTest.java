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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Text from the integrations does not act on the terminal (CAMEL-25445): what a terminal would take as a control is
 * cleaned out of every cell after a frame is drawn.
 */
class ScreenSanitizerTest {

    private static Buffer line(String text) {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 60, 1));
        buffer.setString(0, 0, text, Style.EMPTY);
        return buffer;
    }

    private static List<String> symbols(Buffer buffer) {
        List<String> symbols = new ArrayList<>();
        Rect area = buffer.area();
        for (int x = area.left(); x < area.right(); x++) {
            symbols.add(buffer.get(x, 0).symbol());
        }
        return symbols;
    }

    private static String text(Buffer buffer) {
        return TuiTestHelper.bufferToString(buffer).stripTrailing();
    }

    @Test
    void aC1ControlBecomesASpaceInItsCell() {
        // U+009B is CSI in one character: the browser terminal of --web would colour what follows red
        Buffer buffer = line("plain \u009b31mRED\u009b0m after");

        ScreenSanitizer.sanitize(buffer);

        assertThat(symbols(buffer)).noneMatch(s -> s.codePoints().anyMatch(ScreenSanitizer::unsafe));
        // the parameters show as text, in the cells they had
        assertThat(text(buffer)).isEqualTo("plain  31mRED 0m after");
    }

    @Test
    void aBiDiControlOrAnInvisibleCharacterJoinedToACellIsDropped() {
        Buffer buffer = line("bidi \u202eDESREVER\u202c end, ad\u200bmin\ufeff");

        ScreenSanitizer.sanitize(buffer);

        assertThat(symbols(buffer)).noneMatch(s -> s.codePoints().anyMatch(ScreenSanitizer::unsafe));
        assertThat(text(buffer)).isEqualTo("bidi DESREVER end, admin");
    }

    @Test
    void aCellSetWithAControlCharacterBecomesASpace() {
        // a widget can set a cell directly, which TamboUI does not clean, not even C0
        Buffer buffer = Buffer.empty(new Rect(0, 0, 3, 1));
        buffer.set(0, 0, new Cell("\u001b", Style.EMPTY));
        buffer.set(1, 0, new Cell("\u009d", Style.EMPTY));
        assertThat(symbols(buffer)).startsWith("\u001b", "\u009d");

        ScreenSanitizer.sanitize(buffer);

        assertThat(symbols(buffer)).startsWith(" ", " ");
    }

    @Test
    void emojiAndScriptsStayWhole() {
        // the joiners and selectors emoji and some scripts need: ZWJ, variation selector 16, ZWNJ
        Buffer buffer = line("👩\u200D💻 🇫🇷 ❤\uFE0F می\u200Cخواهم");
        List<String> before = symbols(buffer);

        ScreenSanitizer.sanitize(buffer);

        assertThat(symbols(buffer)).isEqualTo(before);
        assertThat(String.join("", symbols(buffer))).contains("\u200D", "\uFE0F", "\u200C");
    }

    @Test
    void theFrameIsCleanedAfterItIsDrawn() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 10, 1));

        ScreenSanitizer.render(Frame.forTesting(buffer), f -> f.buffer().setString(0, 0, "\u009b2J ok", Style.EMPTY));

        assertThat(text(buffer)).isEqualTo(" 2J ok");
        assertThat(ScreenSanitizer.clean("a")).isSameAs("a");
    }
}
