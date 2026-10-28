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

import dev.tamboui.style.Modifier;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormHelperTest {

    private static final String SMILE = "😀";
    // a family: three emoji joined by ZWJs, eight chars that take two columns
    private static final String FAMILY = "👨\u200D👩\u200D👧";
    private static final KeyModifiers CTRL = KeyModifiers.of(true, false, false);
    private static final KeyModifiers ALT = KeyModifiers.of(false, true, false);

    @Test
    void typedTextIsTheWholeCharacter() {
        assertThat(FormHelper.typedText(KeyEvent.ofChar('a'))).isEqualTo("a");
        assertThat(FormHelper.typedText(KeyEvent.ofChar('A', KeyModifiers.of(false, false, true)))).isEqualTo("A");
        assertThat(FormHelper.typedText(KeyEvent.ofChar(0x1F600))).isEqualTo(SMILE);
        assertThat(FormHelper.typedText(KeyEvent.ofChar('é'))).isEqualTo("é");
    }

    @Test
    void typedTextIsNothingForCtrlAltAndOtherKeys() {
        assertThat(FormHelper.typedText(KeyEvent.ofChar('a', CTRL))).isNull();
        assertThat(FormHelper.typedText(KeyEvent.ofChar('x', ALT))).isNull();
        assertThat(FormHelper.typedText(KeyEvent.ofChar('\u0007'))).isNull();
        assertThat(FormHelper.typedText(KeyEvent.ofKey(KeyCode.ENTER))).isNull();
    }

    @Test
    void textInputTakesAnEmojiAndSkipsCtrlLetters() {
        TextInputState state = new TextInputState("ab");
        state.moveCursorToEnd();

        FormHelper.handleTextInput(KeyEvent.ofChar(0x1F600), state);
        FormHelper.handleTextInput(KeyEvent.ofChar('u', CTRL), state);
        FormHelper.handleTextInput(KeyEvent.ofChar('c'), state);
        assertThat(state.text()).isEqualTo("ab" + SMILE + "c");

        // Left goes over the c and the whole emoji
        FormHelper.handleTextInput(KeyEvent.ofKey(KeyCode.LEFT), state);
        FormHelper.handleTextInput(KeyEvent.ofKey(KeyCode.LEFT), state);
        assertThat(state.cursorPosition()).isEqualTo(2);
    }

    @Test
    void textAreaTakesAnEmojiAndSkipsCtrlLetters() {
        TextAreaState editor = new TextAreaState("");

        FormHelper.handleTextArea(KeyEvent.ofChar(0x1F600), editor);
        FormHelper.handleTextArea(KeyEvent.ofChar('k', CTRL), editor);
        FormHelper.handleTextArea(KeyEvent.ofChar('!'), editor);

        assertThat(editor.text()).isEqualTo(SMILE + "!");
    }

    @Test
    void caretFieldMarksTheCharacterAtTheCursor() {
        TextInputState state = new TextInputState("camel");
        state.moveCursorToEnd();
        List<Span> atEnd = new ArrayList<>();
        FormHelper.addCaretField(atEnd, state);
        assertThat(atEnd).extracting(Span::content).containsExactly("camel█");

        state.moveCursorLeft();
        state.moveCursorLeft();
        List<Span> inside = new ArrayList<>();
        FormHelper.addCaretField(inside, state);
        assertThat(inside).extracting(Span::content).containsExactly("cam", "e", "l");
        assertThat(inside.get(1).style().effectiveModifiers()).contains(Modifier.REVERSED);
    }

    @Test
    void caretFieldMarksTheWholeEmojiAtTheCursor() {
        TextInputState state = new TextInputState("a" + FAMILY + "b");
        state.moveCursorToEnd();
        state.moveCursorLeft();
        state.moveCursorLeft();

        List<Span> spans = new ArrayList<>();
        FormHelper.addCaretField(spans, state);
        assertThat(spans).extracting(Span::content).containsExactly("a", FAMILY, "b");
    }
}
