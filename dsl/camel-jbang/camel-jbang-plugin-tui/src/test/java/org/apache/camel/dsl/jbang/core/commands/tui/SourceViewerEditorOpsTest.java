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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SourceViewer YAML editor operations (CAMEL-24372).
 */
class SourceViewerEditorOpsTest {

    private static final KeyModifiers CTRL = KeyModifiers.of(true, false, false);
    private static final KeyModifiers ALT = KeyModifiers.of(false, true, false);
    private static final KeyModifiers CTRL_SHIFT = KeyModifiers.of(true, false, true);

    @TempDir
    Path tempDir;

    private SourceViewer viewer;
    private Path yamlFile;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        viewer = new SourceViewer();
        viewer.setValidateOnSave(false);
        yamlFile = tempDir.resolve("route.camel.yaml");
        Files.writeString(yamlFile, """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to: log:info
                        - to: log:warn
                """, StandardCharsets.UTF_8);
        viewer.loadFile(yamlFile);
        viewer.enterEditMode();
    }

    @Test
    void ctrlZUndoesTypedChange() {
        viewer.handleKeyEvent(KeyEvent.ofChar('X', KeyModifiers.NONE));
        assertThat(viewer.editText()).contains("X");

        viewer.handleKeyEvent(KeyEvent.ofChar('z', CTRL));

        assertThat(viewer.editText()).doesNotContain("X");
    }

    @Test
    void ctrlYRedoesUndoneChange() {
        viewer.handleKeyEvent(KeyEvent.ofChar('X', KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofChar('z', CTRL));
        viewer.handleKeyEvent(KeyEvent.ofChar('y', CTRL));

        assertThat(viewer.editText()).contains("X");
    }

    @Test
    void ctrlDDuplicatesYamlBlock() {
        moveCursorToLineContaining("log:info");
        int before = countOccurrences(viewer.editText(), "log:info");

        viewer.handleKeyEvent(KeyEvent.ofChar('d', CTRL));

        assertThat(countOccurrences(viewer.editText(), "log:info")).isEqualTo(before + 1);
    }

    @Test
    void ctrlKDeletesCurrentLine() {
        moveCursorToLineContaining("log:warn");

        viewer.handleKeyEvent(KeyEvent.ofChar('k', CTRL));

        assertThat(viewer.editText()).doesNotContain("log:warn");
        assertThat(viewer.editText()).contains("log:info");
    }

    @Test
    void smartHomeInEditModeUsesContentThenLineStart() {
        moveCursorToLineContaining("uri:");
        String line = viewer.editState().getLine(viewer.editState().cursorRow());
        SourceEditorNavigation.positionCursor(viewer.editState(), viewer.editState().cursorRow(), line.length());
        int contentStart = YamlBlockEditor.leadingSpaces(line);

        SourceEditorNavigation.smartHome(viewer.editState(), false);
        assertThat(viewer.editState().cursorCol()).isEqualTo(contentStart);

        SourceEditorNavigation.smartHome(viewer.editState(), false);
        assertThat(viewer.editState().cursorCol()).isZero();
    }

    @Test
    void altDownMovesBlockDown() {
        moveCursorToLineContaining("log:info");

        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.ALT));

        assertThat(viewer.editText().indexOf("log:warn")).isLessThan(viewer.editText().indexOf("log:info"));
    }

    @Test
    void ctrlLeftAndRightMoveByWordViaKeyBindings() {
        moveCursorToLineContaining("uri:");
        SourceEditorNavigation.positionCursor(viewer.editState(), viewer.editState().cursorRow(), 0);

        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.RIGHT, CTRL));
        assertThat(viewer.editState().cursorCol()).isEqualTo(9);

        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT, CTRL));
        assertThat(viewer.editState().cursorCol()).isEqualTo(6);
    }

    @Test
    void footerShowsNewEditorHints() {
        List<dev.tamboui.text.Span> spans = new ArrayList<>();
        viewer.renderFooter(spans);
        String footer = spansToString(spans);

        assertThat(footer).contains("Ctrl+Z");
        assertThat(footer).contains("Ctrl+Y");
        assertThat(footer).contains("Alt+↑/↓");
        assertThat(footer).contains("Ctrl+D");
        assertThat(footer).contains("Ctrl+K");
    }

    @Test
    void pasteAutoIndentsToMatchSurroundingBlock() {
        // place cursor at column 0 of the "log:warn" line; its siblings are indented 8 spaces
        String[] lines = viewer.editText().split("\n", -1);
        int row = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("log:warn")) {
                row = i;
                break;
            }
        }
        assertThat(row).isGreaterThanOrEqualTo(0);
        SourceEditorNavigation.positionCursor(viewer.editState(), row, 0);

        // paste an unindented step; it should be reindented to align with the 8-space siblings
        viewer.handlePaste("- to: log:error\n");

        assertThat(viewer.editText()).contains("        - to: log:error\n");
    }

    @Test
    void pasteAlignsWithCurrentLineNotDeeperPredecessor() throws Exception {
        // a "- log:" step (indent 8) preceded by a far deeper line (indent 16); pasting before it
        // must align with the step's own indent, not the deeper predecessor
        Path nested = tempDir.resolve("nested.camel.yaml");
        Files.writeString(nested, """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            expression:
                              simple:
                                expression: "hi"
                        - log:
                            message: "x"
                """, StandardCharsets.UTF_8);
        viewer.loadFile(nested);
        viewer.enterEditMode();

        String[] lines = viewer.editText().split("\n", -1);
        int row = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("- log:")) {
                row = i;
                break;
            }
        }
        assertThat(row).isGreaterThanOrEqualTo(0);
        SourceEditorNavigation.positionCursor(viewer.editState(), row, 0);

        viewer.handlePaste("- to:\n    uri: mock:dead\n");

        assertThat(viewer.editText()).contains("        - to:\n            uri: mock:dead\n        - log:");
    }

    @Test
    void pasteListItemOnBlankLineAlignsWithNearestSiblingStep() throws Exception {
        // last line is a deep leaf (indent 12); pasting a step on a blank line after it must align
        // with the nearest sibling step (indent 8), not the deeper leaf above it
        Path nested = tempDir.resolve("nested-append.camel.yaml");
        Files.writeString(nested, """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "${body}"
                """, StandardCharsets.UTF_8);
        viewer.loadFile(nested);
        viewer.enterEditMode();

        // place the cursor at the end of the deep leaf line (message:, indent 12) and press ENTER —
        // the editor auto-indents the new line to col 12; pasting a step must still align it with the
        // nearest sibling step (indent 8), not the auto-indent column
        int row = -1;
        String[] lines = viewer.editText().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("message:")) {
                row = i;
                break;
            }
        }
        assertThat(row).isGreaterThanOrEqualTo(0);
        SourceEditorNavigation.positionCursor(viewer.editState(), row, viewer.editState().getLine(row).length());
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        viewer.handlePaste("- to:\n    uri: mock:dead\n");

        assertThat(viewer.editText()).contains("        - to:\n            uri: mock:dead");
    }

    private void moveCursorToLineContaining(String needle) {
        String[] lines = viewer.editText().split("\n", -1);
        for (int row = 0; row < lines.length; row++) {
            if (lines[row].contains(needle)) {
                SourceEditorNavigation.positionCursor(viewer.editState(), row, lines[row].indexOf(needle));
                return;
            }
        }
        throw new AssertionError("Line not found: " + needle);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String spansToString(List<dev.tamboui.text.Span> spans) {
        StringBuilder sb = new StringBuilder();
        for (dev.tamboui.text.Span span : spans) {
            sb.append(span.content());
        }
        return sb.toString();
    }
}
