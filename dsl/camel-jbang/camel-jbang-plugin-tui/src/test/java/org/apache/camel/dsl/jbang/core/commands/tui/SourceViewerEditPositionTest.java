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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Switching between viewing and editing a file (F4, Esc, F5) keeps the code where it is on the screen (CAMEL-24395).
 */
class SourceViewerEditPositionTest {

    private static final Pattern LINE_NUMBER = Pattern.compile("^│(>>| . |   )\\s*(\\d+) \\|");

    @TempDir
    Path tempDir;

    private SourceViewer viewer;
    private Path sourceFile;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        viewer = new SourceViewer();
        viewer.setValidateOnSave(false);
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            lines.add("key" + i + "=value" + i);
        }
        sourceFile = tempDir.resolve("notes.properties");
        Files.writeString(sourceFile, String.join("\n", lines) + "\n");
        viewer.loadFile(sourceFile);
    }

    @Test
    void editorOpensOnTheScreenOfTheView() {
        viewer.goToLine(49);
        render();
        // move the selection up within the screen, away from where the editor used to put the cursor
        for (int i = 0; i < 8; i++) {
            key(KeyCode.UP);
        }
        Screen view = render();
        assertThat(view.selectedLine()).isEqualTo(42);

        key(KeyCode.F4);
        Screen edit = render();

        assertThat(viewer.isEditMode()).isTrue();
        assertThat(edit.topLine()).isEqualTo(view.topLine());
        assertThat(edit.selectedLine()).isEqualTo(42);
        assertThat(edit.selectedRow()).isEqualTo(view.selectedRow());
    }

    @Test
    void leavingTheEditorKeepsTheCursorLineAndTheScreen() {
        viewer.goToLine(49);
        render();
        key(KeyCode.F4);
        render();
        for (int i = 0; i < 10; i++) {
            key(KeyCode.UP);
        }
        Screen edit = render();
        assertThat(edit.selectedLine()).isEqualTo(40);

        key(KeyCode.ESCAPE);
        Screen view = render();

        assertThat(viewer.isEditMode()).isFalse();
        assertThat(viewer.getSelectedLine()).isEqualTo(39);
        assertThat(view.topLine()).isEqualTo(edit.topLine());
        assertThat(view.selectedRow()).isEqualTo(edit.selectedRow());
    }

    @Test
    void saveAndCloseKeepsTheCursorLine() throws Exception {
        viewer.goToLine(49);
        render();
        key(KeyCode.F4);
        render();
        for (int i = 0; i < 5; i++) {
            key(KeyCode.DOWN);
        }
        viewer.handleKeyEvent(KeyEvent.ofChar('x', KeyModifiers.NONE));
        Screen edit = render();

        key(KeyCode.F5);
        Screen view = render();

        assertThat(viewer.isEditMode()).isFalse();
        assertThat(Files.readString(sourceFile)).contains("xkey55=value55");
        assertThat(view.selectedLine()).isEqualTo(55);
        assertThat(view.topLine()).isEqualTo(edit.topLine());
    }

    @Test
    void editorTopMovesOnlyAsFarAsTheCursorMustBeSeen() {
        assertThat(SourceViewer.editorTopKeepingCursor(27, 49, 24)).isEqualTo(27);
        assertThat(SourceViewer.editorTopKeepingCursor(0, 49, 24)).isEqualTo(26);
        assertThat(SourceViewer.editorTopKeepingCursor(60, 49, 24)).isEqualTo(49);
    }

    private void key(KeyCode code) {
        viewer.handleKeyEvent(KeyEvent.ofKey(code, KeyModifiers.NONE));
    }

    private Screen render() {
        Rect area = new Rect(0, 0, 60, 20);
        Buffer buffer = Buffer.empty(area);
        viewer.render(Frame.forTesting(buffer), area);
        String[] rows = TuiTestHelper.bufferToString(buffer).split("\n");
        int top = -1;
        int selectedRow = -1;
        int selected = -1;
        for (int r = 0; r < rows.length; r++) {
            Matcher m = LINE_NUMBER.matcher(rows[r]);
            if (m.find()) {
                int number = Integer.parseInt(m.group(2));
                if (top < 0) {
                    top = number;
                }
                if (">>".equals(m.group(1))) {
                    selectedRow = r;
                    selected = number;
                }
            }
        }
        return new Screen(top, selectedRow, selected);
    }

    /** The first line number on the screen, and the screen row and number of the line marked with >>. */
    private record Screen(int topLine, int selectedRow, int selectedLine) {
    }
}
