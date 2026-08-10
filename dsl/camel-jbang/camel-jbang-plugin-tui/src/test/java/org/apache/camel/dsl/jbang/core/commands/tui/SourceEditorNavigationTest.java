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
 * Tests for word navigation and smart home (CAMEL-24372).
 */
class SourceEditorNavigationTest {

    private TextAreaState state;

    @BeforeEach
    void setUp() {
        state = new TextAreaState("  from: timer:tick\n");
        SourceEditorNavigation.positionCursor(state, 0, 14);
    }

    @Test
    void wordBoundaryLeftSkipsToPreviousToken() {
        assertThat(SourceEditorNavigation.wordBoundaryLeft("hello world", 11)).isEqualTo(6);
        assertThat(SourceEditorNavigation.wordBoundaryLeft("hello world", 0)).isZero();
    }

    @Test
    void wordBoundaryRightSkipsToNextToken() {
        assertThat(SourceEditorNavigation.wordBoundaryRight("hello world", 0)).isEqualTo(5);
        assertThat(SourceEditorNavigation.wordBoundaryRight("hello world", 6)).isEqualTo(11);
    }

    @Test
    void colonSeparatesWordsInComponentNames() {
        assertThat(SourceEditorNavigation.wordBoundaryLeft("timer:tick", 10)).isEqualTo(6);
        assertThat(SourceEditorNavigation.wordBoundaryRight("timer:tick", 0)).isEqualTo(5);
        assertThat(SourceEditorNavigation.wordBoundaryRight("timer:tick", 6)).isEqualTo(10);
    }

    @Test
    void moveWordLeftAndRightUpdateCursor() {
        SourceEditorNavigation.moveWordRight(state);
        assertThat(state.cursorCol()).isEqualTo(18);

        SourceEditorNavigation.moveWordLeft(state);
        assertThat(state.cursorCol()).isEqualTo(14);
    }

    @Test
    void deleteWordBackwardRemovesPreviousWord() {
        SourceEditorNavigation.deleteWordBackward(state);

        assertThat(state.getLine(0)).isEqualTo("  from: tick");
        assertThat(state.cursorCol()).isEqualTo(8);
    }

    @Test
    void deleteWordForwardRemovesNextWordToken() {
        SourceEditorNavigation.positionCursor(state, 0, 8);

        SourceEditorNavigation.deleteWordForward(state);

        assertThat(state.getLine(0)).isEqualTo("  from: :tick");
        assertThat(state.cursorCol()).isEqualTo(8);
    }

    @Test
    void smartHomeTogglesBetweenContentStartAndColumnZero() {
        SourceEditorNavigation.smartHome(state, false);
        assertThat(state.cursorCol()).isEqualTo(2);

        SourceEditorNavigation.smartHome(state, false);
        assertThat(state.cursorCol()).isZero();

        SourceEditorNavigation.smartHome(state, true);
        assertThat(state.cursorCol()).isZero();
    }
}
