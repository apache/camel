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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;

final class FormHelper {

    private FormHelper() {
    }

    record HeaderEntry(TextInputState keyInput, TextInputState valueInput) {
    }

    static void handleTextInput(KeyEvent ke, TextInputState state) {
        if (ke.isDeleteBackward()) {
            state.deleteBackward();
        } else if (ke.isDeleteForward()) {
            state.deleteForward();
        } else if (ke.isLeft()) {
            state.moveCursorLeft();
        } else if (ke.isRight()) {
            state.moveCursorRight();
        } else if (ke.isHome()) {
            state.moveCursorToStart();
        } else if (ke.isEnd()) {
            state.moveCursorToEnd();
        } else if (ke.code() == KeyCode.CHAR) {
            state.insert(ke.character());
        }
    }

    static void renderLabel(Frame frame, int x, int y, int w, String label, boolean selected) {
        Style style = selected ? Style.EMPTY.bold() : Style.EMPTY.dim();
        Rect labelArea = new Rect(x, y, w, 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(label, style))), labelArea);
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
