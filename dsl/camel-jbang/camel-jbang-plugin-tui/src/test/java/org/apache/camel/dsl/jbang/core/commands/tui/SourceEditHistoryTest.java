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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for edit undo/redo history (CAMEL-24372).
 */
class SourceEditHistoryTest {

    private SourceEditHistory history;
    private TextAreaState state;

    @BeforeEach
    void setUp() {
        history = new SourceEditHistory();
        state = new TextAreaState("alpha\nbeta\n");
        history.seedInitial(state);
    }

    @Test
    void undoRestoresPreviousSnapshot() {
        history.beforeChange(state);
        state.setText("alpha\nchanged\n");

        assertThat(history.undo(state)).isTrue();
        assertThat(state.text()).isEqualTo("alpha\nbeta\n");
    }

    @Test
    void redoReappliesUndoneChange() {
        history.beforeChange(state);
        state.setText("alpha\nchanged\n");
        history.undo(state);

        assertThat(history.redo(state)).isTrue();
        assertThat(state.text()).isEqualTo("alpha\nchanged\n");
    }

    @Test
    void undoAtInitialStateReturnsFalse() {
        assertThat(history.undo(state)).isFalse();
    }

    @Test
    void redoWhenEmptyReturnsFalse() {
        assertThat(history.redo(state)).isFalse();
    }

    @Test
    void positionCursorRestoresRowAndColumn() {
        SourceEditHistory.positionCursor(state, 1, 2);

        assertThat(state.cursorRow()).isEqualTo(1);
        assertThat(state.cursorCol()).isEqualTo(2);
        assertThat(state.getLine(1)).startsWith("be");
    }

    @Test
    void clearEmptiesStacks() {
        history.beforeChange(state);
        state.insert('X');
        history.clear();
        history.seedInitial(state);

        assertThat(history.undo(state)).isFalse();
    }
}
