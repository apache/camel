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

import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterListPopupTest {

    /** A popup of words: the smallest subclass. */
    private static final class WordPopup extends FilterListPopup<String> {

        private final List<String> words = List.of("timer", "kafka", "seda", "direct", "log");
        private String chosen;

        void open() {
            show();
        }

        @Override
        protected List<String> entries() {
            return words;
        }

        @Override
        protected boolean matches(String entry, String typed) {
            return matchesText(entry, typed);
        }

        @Override
        protected List<Line> rows(List<String> entries, int width) {
            return entries.stream().map(e -> Line.from(highlighted(e, Style.EMPTY, Style.EMPTY.bold()))).toList();
        }

        @Override
        protected String name() {
            return "Words";
        }

        @Override
        protected void chosen(String entry) {
            chosen = entry;
        }
    }

    private static String render(FilterListPopup<?> popup) {
        Rect area = new Rect(0, 0, 60, 20);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        return TuiTestHelper.bufferToString(buffer);
    }

    private static void type(FilterListPopup<?> popup, String text) {
        for (char ch : text.toCharArray()) {
            popup.handleKeyEvent(KeyEvent.ofChar(ch));
        }
    }

    @Test
    void typingNarrowsTheEntriesAndTheTitleCountsThem() {
        WordPopup popup = new WordPopup();
        popup.open();
        assertThat(render(popup)).contains("Words (5)").contains("> █");

        type(popup, "ka");
        assertThat(popup.shown()).containsExactly("kafka");
        assertThat(render(popup)).contains("Words (1/5)").contains("> ka█");

        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
        assertThat(popup.shown()).containsExactly("kafka");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
        assertThat(popup.shown()).hasSize(5);
    }

    @Test
    void enterChoosesTheSelectedEntryAndCloses() {
        WordPopup popup = new WordPopup();
        popup.open();
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(popup.chosen).isEqualTo("kafka");
        assertThat(popup.isVisible()).isFalse();
    }

    @Test
    void escClosesWithoutChoosing() {
        WordPopup popup = new WordPopup();
        popup.open();
        assertThat(popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE))).isTrue();

        assertThat(popup.isVisible()).isFalse();
        assertThat(popup.chosen).isNull();
    }

    @Test
    void aClickOnAnEntryChoosesItAndAClickOutsideCloses() {
        WordPopup popup = new WordPopup();
        popup.open();
        render(popup);
        // the popup starts on row 2: its border, the prompt and the separator are above the entries (from row 5)
        assertThat(popup.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 10, 7))).isTrue();
        assertThat(popup.chosen).isEqualTo("seda");

        popup.open();
        render(popup);
        popup.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 0, 0));
        assertThat(popup.isVisible()).isFalse();
    }
}
