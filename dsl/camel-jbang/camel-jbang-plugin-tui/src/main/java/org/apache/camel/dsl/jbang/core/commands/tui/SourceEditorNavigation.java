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

import dev.tamboui.widgets.input.TextAreaState;

/**
 * Word navigation and smart-home helpers for the source editor.
 */
final class SourceEditorNavigation {

    private SourceEditorNavigation() {
    }

    static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.';
    }

    static int wordBoundaryLeft(String line, int col) {
        if (line.isEmpty() || col <= 0) {
            return 0;
        }
        int pos = Math.min(col, line.length());
        while (pos > 0 && !isWordChar(line.charAt(pos - 1))) {
            pos--;
        }
        while (pos > 0 && isWordChar(line.charAt(pos - 1))) {
            pos--;
        }
        return pos;
    }

    static int wordBoundaryRight(String line, int col) {
        if (line.isEmpty()) {
            return 0;
        }
        int pos = Math.min(col, line.length());
        while (pos < line.length() && !isWordChar(line.charAt(pos))) {
            pos++;
        }
        while (pos < line.length() && isWordChar(line.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    static void moveWordLeft(TextAreaState state) {
        String line = state.getLine(state.cursorRow());
        int col = state.cursorCol();
        int target = wordBoundaryLeft(line, col);
        if (target == col && col > 0) {
            target = wordBoundaryLeft(line, col - 1);
        }
        positionCursor(state, state.cursorRow(), target);
    }

    static void moveWordRight(TextAreaState state) {
        String line = state.getLine(state.cursorRow());
        int col = state.cursorCol();
        int target = wordBoundaryRight(line, col);
        if (target == col && col < line.length()) {
            target = wordBoundaryRight(line, col + 1);
        }
        positionCursor(state, state.cursorRow(), target);
    }

    static void deleteWordBackward(TextAreaState state) {
        String line = state.getLine(state.cursorRow());
        int col = state.cursorCol();
        if (col == 0) {
            if (state.cursorRow() > 0) {
                state.deleteBackward();
            }
            return;
        }
        int start = wordBoundaryLeft(line, col);
        String prefix = line.substring(0, start);
        String suffix = line.substring(col);
        replaceLine(state, prefix + suffix, start);
    }

    static void deleteWordForward(TextAreaState state) {
        String line = state.getLine(state.cursorRow());
        int col = state.cursorCol();
        if (col >= line.length()) {
            state.deleteForward();
            return;
        }
        int end = wordBoundaryRight(line, col);
        String prefix = line.substring(0, col);
        String suffix = line.substring(end);
        replaceLine(state, prefix + suffix, col);
    }

    static void smartHome(TextAreaState state, boolean toAbsoluteStart) {
        String line = state.getLine(state.cursorRow());
        int contentStart = 0;
        while (contentStart < line.length() && line.charAt(contentStart) == ' ') {
            contentStart++;
        }
        if (toAbsoluteStart || state.cursorCol() <= contentStart) {
            positionCursor(state, state.cursorRow(), 0);
        } else {
            positionCursor(state, state.cursorRow(), contentStart);
        }
    }

    static void positionCursor(TextAreaState state, int row, int col) {
        state.moveCursorToStart();
        for (int i = 0; i < row && i < state.lineCount(); i++) {
            state.moveCursorDown();
        }
        state.moveCursorToLineStart();
        String line = state.getLine(state.cursorRow());
        int target = Math.min(col, line.length());
        for (int i = 0; i < target; i++) {
            state.moveCursorRight();
        }
    }

    private static void replaceLine(TextAreaState state, String newLine, int cursorCol) {
        int row = state.cursorRow();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < state.lineCount(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(i == row ? newLine : state.getLine(i));
        }
        state.setText(text.toString());
        positionCursor(state, row, Math.min(cursorCol, newLine.length()));
    }
}
