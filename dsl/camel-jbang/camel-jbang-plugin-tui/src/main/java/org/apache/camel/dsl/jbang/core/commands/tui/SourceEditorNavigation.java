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

import dev.tamboui.widgets.input.TextAreaState;

/**
 * Word navigation and smart-home helpers for the source editor.
 */
final class SourceEditorNavigation {

    private SourceEditorNavigation() {
    }

    /** The lines of the edit buffer, a copy. */
    static List<String> lines(TextAreaState state) {
        List<String> answer = new ArrayList<>(state.lineCount());
        for (int i = 0; i < state.lineCount(); i++) {
            answer.add(state.getLine(i));
        }
        return answer;
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
        moveToColumn(state, col);
    }

    /**
     * Moves the cursor along its line to the column, a char index into the line as {@code cursorCol()} is. TamboUI
     * moves the cursor a grapheme cluster at a time, and an emoji is two chars or more, so the moves are counted by the
     * column reached and not by the chars. A column inside a cluster ends at the start of the cluster.
     */
    static void moveToColumn(TextAreaState state, int col) {
        int row = state.cursorRow();
        int target = Math.max(0, Math.min(col, state.getLine(row).length()));
        while (state.cursorCol() < target && state.cursorRow() == row) {
            state.moveCursorRight();
        }
        while (state.cursorCol() > target && state.cursorRow() == row) {
            state.moveCursorLeft();
        }
    }

    /** Deletes the text of the cursor's line, a grapheme cluster at a time, so the line below stays as it is. */
    static void clearLine(TextAreaState state) {
        int row = state.cursorRow();
        state.moveCursorToLineStart();
        while (!state.getLine(row).isEmpty()) {
            state.deleteForward();
        }
    }

    /** Deletes the text before the cursor back to the column of its line, a grapheme cluster at a time. */
    static void deleteBackTo(TextAreaState state, int col) {
        int row = state.cursorRow();
        while (state.cursorCol() > Math.max(0, col) && state.cursorRow() == row) {
            state.deleteBackward();
        }
    }

    /**
     * Deletes the text after the cursor up to the column of its line as it is now, a grapheme cluster at a time, and
     * never past the end of the line.
     */
    static void deleteForwardTo(TextAreaState state, int col) {
        int row = state.cursorRow();
        int length = state.getLine(row).length();
        int keep = length - Math.max(0, Math.min(col, length) - state.cursorCol());
        while (state.getLine(row).length() > keep && state.cursorCol() < state.getLine(row).length()) {
            state.deleteForward();
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
