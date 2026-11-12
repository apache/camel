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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TextEditing;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;

final class FormHelper {

    private FormHelper() {
    }

    record HeaderEntry(TextInputState keyInput, TextInputState valueInput) {
    }

    /**
     * The text a key types into a field: the whole character, also one outside the basic plane such as an emoji (where
     * {@code KeyEvent.character()} gives U+FFFD and {@code string().charAt(0)} half of it), and a keypad digit. Null
     * when the key types nothing: it is no character, a control character, or a shortcut pressed with Ctrl or Alt
     * (Ctrl+A is no "a"). Follows {@link KeyEvent#isTyping()}, as {@link TextEditing} does.
     */
    static String typedText(KeyEvent ke) {
        return ke.isTyping() && !ke.isRelease() ? ke.string() : null;
    }

    /**
     * Edits a single-line field with a key: typed text, Backspace, Delete, Left, Right, Home, End, and the word-wise
     * keys of the bindings (Ctrl+Left/Right, Ctrl+Backspace/Delete and Ctrl+W, Alt instead of Ctrl on macOS).
     */
    static void handleTextInput(KeyEvent ke, TextInputState state) {
        TextEditing.handle(state, ke);
    }

    /**
     * Edits a multi-line field with a key, as {@link #handleTextInput}, and Enter, Up and Down by the rows on screen.
     */
    static void handleTextArea(KeyEvent key, TextAreaState editor) {
        TextEditing.handle(editor, key, Overflow.WRAP_WORD);
    }

    /**
     * Edits a multi-line field that moves the focus itself on Enter, Up and Down: the editing keys of
     * {@link #handleTextArea} but these.
     */
    static void handleTextAreaInPlace(KeyEvent key, TextAreaState editor) {
        if (!key.isKey(KeyCode.ENTER) && !key.isUp() && !key.isDown()) {
            TextEditing.handle(editor, key, Overflow.WRAP_WORD);
        }
    }

    /**
     * The word-wise keys of the bindings in a single-line field that handles its other keys itself; true when the key
     * was one of them.
     */
    static boolean handleWordKey(KeyEvent ke, TextInputState state) {
        if (ke.isWordLeft()) {
            state.moveCursorToPreviousWord();
        } else if (ke.isWordRight()) {
            state.moveCursorToNextWord();
        } else if (ke.isDeleteWordBackward()) {
            state.deleteWordBackward();
        } else if (ke.isDeleteWordForward()) {
            state.deleteWordForward();
        } else {
            return false;
        }
        return true;
    }

    /**
     * Adds a text field to a status line: its text with a block caret at the cursor, so Left and Right show where the
     * next character goes (a caret drawn after the text always looks as if it were at the end).
     */
    static void addCaretField(List<Span> spans, TextInputState state) {
        String text = state.text();
        int pos = Math.max(0, Math.min(state.cursorPosition(), text.length()));
        if (pos == text.length()) {
            spans.add(Span.raw(text + "█"));
            return;
        }
        // the whole character, all the code points of an emoji
        int end = TuiHelper.characterEnd(text, pos);
        spans.add(Span.raw(text.substring(0, pos)));
        spans.add(Span.styled(text.substring(pos, end), Style.EMPTY.reversed()));
        spans.add(Span.raw(text.substring(end)));
    }

    static void renderLabel(Frame frame, int x, int y, int w, String label, boolean selected) {
        Style style = selected ? Style.EMPTY.bold() : Style.EMPTY.dim();
        Rect labelArea = new Rect(x, y, w, 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(label, style))), labelArea);
    }

    /**
     * Renders a single-line text field. The active field paints a reversed caret at the cursor so the user can see
     * where they type; an inactive field shows its text, or the placeholder dimmed while it is empty.
     *
     * @param state       the field state, may be {@code null} for an inactive field with no value yet
     * @param active      whether the field currently has focus
     * @param placeholder dimmed text shown while the field is empty, may be {@code null}
     */
    static void renderTextField(Frame frame, Rect area, TextInputState state, boolean active, String placeholder) {
        if (active && state != null) {
            TextInput.Builder builder = TextInput.builder().cursorStyle(Style.EMPTY.reversed());
            if (placeholder != null) {
                builder.placeholder(placeholder);
            }
            // renderWithCursor (not renderStatefulWidget, which paints no cursor cell) so the caret is visible
            builder.build().renderWithCursor(area, frame.buffer(), state, frame);
            return;
        }
        String text = state != null ? state.text() : "";
        if (text.isEmpty()) {
            if (placeholder != null) {
                frame.renderWidget(Paragraph.from(Line.from(Span.styled(placeholder, Style.EMPTY.dim()))), area);
            }
        } else {
            frame.renderWidget(Paragraph.from(Line.from(Span.raw(text))), area);
        }
    }

    static void handlePaste(String text, TextInputState target) {
        if (text == null || text.isEmpty() || target == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\n' && ch != '\r') {
                target.insert(ch);
            }
        }
    }
}
