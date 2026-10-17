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

import java.nio.file.Path;
import java.util.Optional;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI prompt moves over and deletes an emoji of several code points (a family, a flag) as one character, and draws
 * it whole.
 */
class AiInputLineTest {

    // a family: three emoji joined by ZWJs, eight chars that take two columns
    private static final String FAMILY = "👨\u200D👩\u200D👧";

    @TempDir
    Path dir;

    @Test
    void anEmojiOfSeveralCodePointsIsMovedOverAndDeletedAsOneCharacter() {
        AiInputLine input = new AiInputLine();
        input.insert("a" + FAMILY + "b");

        // Left over the b and the whole family: x goes in front of it
        input.handleEditKey(KeyEvent.ofKey(KeyCode.LEFT));
        input.handleEditKey(KeyEvent.ofKey(KeyCode.LEFT));
        input.handleEditKey(KeyEvent.ofChar('x'));
        assertThat(input.text()).isEqualTo("ax" + FAMILY + "b");

        // Delete takes the whole family
        input.handleEditKey(KeyEvent.ofKey(KeyCode.DELETE));
        assertThat(input.text()).isEqualTo("axb");

        // Backspace takes a flag, then a family, each whole
        input.handleEditKey(KeyEvent.ofKey(KeyCode.END));
        input.insert(FAMILY + "🇫🇷");
        input.handleEditKey(KeyEvent.ofKey(KeyCode.BACKSPACE));
        assertThat(input.text()).isEqualTo("axb" + FAMILY);
        input.handleEditKey(KeyEvent.ofKey(KeyCode.BACKSPACE));
        assertThat(input.text()).isEqualTo("axb");
    }

    @Test
    void upAndDownKeepTheColumnOnTheScreenAndNeverLandInsideAnEmoji() {
        AiInputLine input = new AiInputLine();
        input.insert("abcdef\n" + FAMILY + FAMILY + "x");

        // after the two families, four columns in: Up goes to after the d
        input.handleEditKey(KeyEvent.ofKey(KeyCode.HOME));
        input.handleEditKey(KeyEvent.ofKey(KeyCode.RIGHT));
        input.handleEditKey(KeyEvent.ofKey(KeyCode.RIGHT));
        assertThat(input.moveCursorLine(-1)).isTrue();
        input.handleEditKey(KeyEvent.ofChar('|'));
        assertThat(input.text()).isEqualTo("abcd|ef\n" + FAMILY + FAMILY + "x");

        // three columns in: Down goes to after the first family, not into the second
        input.handleEditKey(KeyEvent.ofKey(KeyCode.BACKSPACE));
        input.handleEditKey(KeyEvent.ofKey(KeyCode.LEFT));
        assertThat(input.moveCursorLine(1)).isTrue();
        input.handleEditKey(KeyEvent.ofChar('|'));
        assertThat(input.text()).isEqualTo("abcdef\n" + FAMILY + "|" + FAMILY + "x");
    }

    @Test
    void theCursorMarksTheWholeEmojiUnderIt() {
        AiInputLine input = new AiInputLine();
        input.insert("a" + FAMILY + "b");
        input.handleEditKey(KeyEvent.ofKey(KeyCode.LEFT));
        input.handleEditKey(KeyEvent.ofKey(KeyCode.LEFT));

        Buffer buffer = render(input, 20);

        // after the "> " prompt and the a: the family in one reversed cell, then the b
        assertThat(buffer.get(3, 0).symbol()).isEqualTo(FAMILY);
        assertThat(buffer.get(3, 0).style().effectiveModifiers()).contains(Modifier.REVERSED);
        assertThat(buffer.get(5, 0).symbol()).isEqualTo("b");
        assertThat(buffer.get(5, 0).style().effectiveModifiers()).doesNotContain(Modifier.REVERSED);
    }

    @Test
    void aLongLineScrollsByColumnsAndCutsNoEmoji() {
        AiInputLine input = new AiInputLine();
        input.insert("a" + FAMILY.repeat(20));

        // the cursor at the end: the nine families that fit before it in 19 columns, each whole
        Buffer buffer = render(input, 21);

        for (int x = 2; x <= 18; x += 2) {
            assertThat(buffer.get(x, 0).symbol()).isEqualTo(FAMILY);
        }
        assertThat(buffer.get(20, 0).style().effectiveModifiers()).contains(Modifier.REVERSED);
    }

    @Test
    void theSearchPreviewIsCutBetweenEmoji() {
        AiInputLine input = new AiInputLine();
        input.setPromptHistory(TuiPromptHistory.load(10, dir.resolve("history")));
        input.remember("x" + FAMILY.repeat(10));
        input.enterSearch();
        input.handleSearchKey(KeyEvent.ofChar('x'));

        Buffer buffer = render(input, 30);

        // the families that fit, each whole, and the ellipsis in the last column
        for (int x = 0; x < 30; x++) {
            String symbol = buffer.get(x, 0).symbol();
            if (symbol.contains("👨") || symbol.contains("👩") || symbol.contains("👧")) {
                assertThat(symbol).isEqualTo(FAMILY);
            }
        }
        assertThat(buffer.get(29, 0).symbol()).isEqualTo("…");
    }

    private static Buffer render(AiInputLine input, int width) {
        Rect area = new Rect(0, 0, width, 1);
        Buffer buffer = Buffer.empty(area);
        input.render(Frame.forTesting(buffer), area, "> ", false, text -> Optional.empty());
        return buffer;
    }
}
