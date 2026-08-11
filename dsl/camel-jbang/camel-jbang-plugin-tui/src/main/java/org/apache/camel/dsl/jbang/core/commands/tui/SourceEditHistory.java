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

import java.util.ArrayDeque;
import java.util.Deque;

import dev.tamboui.widgets.input.TextAreaState;

/**
 * Undo/redo stack for {@link SourceViewer} plain-text edit mode.
 */
final class SourceEditHistory {

    private static final int MAX_DEPTH = 100;

    record Snapshot(String text, int row, int col) {
    }

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();

    void clear() {
        undo.clear();
        redo.clear();
    }

    void seedInitial(TextAreaState state) {
        clear();
        undo.push(capture(state));
    }

    void beforeChange(TextAreaState state) {
        Snapshot snap = capture(state);
        undo.push(snap);
        trim(undo);
        redo.clear();
    }

    boolean undo(TextAreaState state) {
        if (undo.size() <= 1) {
            return false;
        }
        redo.push(capture(state));
        undo.pop();
        restore(state, undo.peek());
        return true;
    }

    boolean redo(TextAreaState state) {
        if (redo.isEmpty()) {
            return false;
        }
        Snapshot next = redo.pop();
        undo.push(capture(state));
        trim(undo);
        restore(state, next);
        return true;
    }

    static Snapshot capture(TextAreaState state) {
        return new Snapshot(state.text(), state.cursorRow(), state.cursorCol());
    }

    private static void restore(TextAreaState state, Snapshot snapshot) {
        state.setText(snapshot.text());
        SourceEditorNavigation.positionCursor(state, snapshot.row(), snapshot.col());
    }

    private static void trim(Deque<Snapshot> stack) {
        while (stack.size() > MAX_DEPTH) {
            stack.removeLast();
        }
    }
}
