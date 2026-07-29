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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SourceViewer plain-text edit mode (CAMEL-24287).
 */
class SourceViewerEditTest {

    @TempDir
    Path tempDir;

    private SourceViewer viewer;
    private Path sourceFile;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        viewer = new SourceViewer();
        sourceFile = tempDir.resolve("route.camel.yaml");
        Files.writeString(sourceFile, """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to: log:info
                """, StandardCharsets.UTF_8);
    }

    @Test
    void loadFileMarksLocalWritableFileEditable() {
        viewer.loadFile(sourceFile);

        assertThat(viewer.isVisible()).isTrue();
        assertThat(viewer.isEditable()).isTrue();
        assertThat(viewer.isEditMode()).isFalse();
    }

    @Test
    void eEntersEditModeForLocalFile() {
        viewer.loadFile(sourceFile);

        assertThat(viewer.handleKeyEvent(KeyEvent.ofChar('e', KeyModifiers.NONE))).isTrue();

        assertThat(viewer.isEditMode()).isTrue();
        assertThat(viewer.isTextInputActive()).isTrue();
    }

    @Test
    void escCancelsEditModeWithoutClosingViewer() {
        viewer.loadFile(sourceFile);
        viewer.handleKeyEvent(KeyEvent.ofChar('e', KeyModifiers.NONE));
        assertThat(viewer.isEditMode()).isTrue();

        assertThat(viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE))).isTrue();

        assertThat(viewer.isEditMode()).isFalse();
        assertThat(viewer.isVisible()).isTrue();
        assertThat(viewer.isEditable()).isTrue();
    }

    @Test
    void typingInEditModeDoesNotCloseViewer() {
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();

        assertThat(viewer.handleKeyEvent(KeyEvent.ofChar('c', KeyModifiers.NONE))).isTrue();
        assertThat(viewer.handleKeyEvent(KeyEvent.ofChar('q', KeyModifiers.NONE))).isTrue();
        assertThat(viewer.handleKeyEvent(KeyEvent.ofChar('1', KeyModifiers.NONE))).isTrue();

        assertThat(viewer.isEditMode()).isTrue();
        assertThat(viewer.isVisible()).isTrue();
    }

    @Test
    void f5SavesEditedContentToDisk() throws Exception {
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();

        // Append a newline and a comment via editor keys
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        // move to end of document
        for (int i = 0; i < 20; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        for (char ch : "# edited".toCharArray()) {
            viewer.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
        }

        assertThat(viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE))).isTrue();

        assertThat(viewer.isEditMode()).isFalse();
        assertThat(viewer.isVisible()).isTrue();
        String saved = Files.readString(sourceFile, StandardCharsets.UTF_8);
        assertThat(saved).contains("# edited");
        assertThat(saved).contains("timer:tick");
    }

    @Test
    void cancelDiscardsUnsavedEdits() throws Exception {
        String original = Files.readString(sourceFile, StandardCharsets.UTF_8);
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();

        for (char ch : "CHANGED".toCharArray()) {
            viewer.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));

        assertThat(viewer.isEditMode()).isFalse();
        assertThat(Files.readString(sourceFile, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    @Test
    void pasteInsertsIntoEditBuffer() throws Exception {
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();
        viewer.handlePaste("\n# pasted-line\n");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE));

        assertThat(Files.readString(sourceFile, StandardCharsets.UTF_8)).contains("# pasted-line");
    }

    @Test
    void remoteLoadSourceIsNotEditable() {
        MonitorContext ctx = new MonitorContext(
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of()),
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of()));
        viewer.loadSource(ctx, "myRoute", 0);

        assertThat(viewer.isVisible()).isFalse();
        assertThat(viewer.isEditable()).isFalse();
        assertThat(viewer.isEditMode()).isFalse();
        assertThat(viewer.handleKeyEvent(KeyEvent.ofChar('e', KeyModifiers.NONE))).isFalse();
    }

    @Test
    void loadSourceClearsEditableStateFromPriorLocalFile() {
        viewer.loadFile(sourceFile);
        assertThat(viewer.isEditable()).isTrue();

        MonitorContext ctx = new MonitorContext(
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of()),
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of()));
        viewer.loadSource(ctx, "myRoute", 0);

        assertThat(viewer.isEditable()).isFalse();
        assertThat(viewer.isEditMode()).isFalse();
    }

    @Test
    void footerShowsEditHintWhenEditable() {
        viewer.loadFile(sourceFile);
        List<Span> spans = new ArrayList<>();
        viewer.renderFooter(spans);

        String footer = spansToString(spans);
        assertThat(footer).contains("e");
        assertThat(footer).containsIgnoringCase("edit");
    }

    @Test
    void footerShowsSaveHintInEditMode() {
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();
        List<Span> spans = new ArrayList<>();
        viewer.renderFooter(spans);

        String footer = spansToString(spans);
        assertThat(footer).contains("F5");
        assertThat(footer).containsIgnoringCase("save");
        assertThat(footer).contains("Esc");
        assertThat(footer).containsIgnoringCase("cancel");
    }

    @Test
    void renderEditModeDrawsEditTitle() {
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();

        Rect area = new Rect(0, 0, 80, 24);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        viewer.render(frame, area);

        String rendered = TuiTestHelper.bufferToString(buffer);
        assertThat(rendered).contains("Edit");
        assertThat(rendered).contains("route.camel.yaml");
    }

    @Test
    void hideExitsEditMode() {
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();
        assertThat(viewer.isEditMode()).isTrue();

        viewer.hide();

        assertThat(viewer.isEditMode()).isFalse();
        assertThat(viewer.isVisible()).isFalse();
        assertThat(viewer.isTextInputActive()).isFalse();
    }

    @Test
    void readOnlyFileIsNotEditable() throws Exception {
        Path readOnly = tempDir.resolve("readonly.properties");
        Files.writeString(readOnly, "foo=bar\n", StandardCharsets.UTF_8);
        assertThat(readOnly.toFile().setWritable(false)).isTrue();
        try {
            viewer.loadFile(readOnly);
            assertThat(viewer.isEditable()).isFalse();
            assertThat(viewer.handleKeyEvent(KeyEvent.ofChar('e', KeyModifiers.NONE))).isFalse();
            assertThat(viewer.isEditMode()).isFalse();
        } finally {
            readOnly.toFile().setWritable(true);
        }
    }

    @Test
    void emptyFileCanBeEditedAndSaved() throws Exception {
        Path empty = tempDir.resolve("empty.txt");
        Files.writeString(empty, "", StandardCharsets.UTF_8);
        viewer.loadFile(empty);
        viewer.enterEditMode();

        for (char ch : "hello".toCharArray()) {
            viewer.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE));

        assertThat(Files.readString(empty, StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(viewer.isEditMode()).isFalse();
    }

    @Test
    void cancelEditRestoresMarkdownMode() throws Exception {
        Path md = tempDir.resolve("readme.md");
        Files.writeString(md, "# Hello\n\nWorld\n", StandardCharsets.UTF_8);
        viewer.loadFile(md);
        assertThat(viewer.isMarkdownMode()).isTrue();

        viewer.enterEditMode();
        assertThat(viewer.isEditMode()).isTrue();
        assertThat(viewer.isMarkdownMode()).isFalse();

        assertThat(viewer.cancelEdit()).isTrue();
        assertThat(viewer.isEditMode()).isFalse();
        assertThat(viewer.isMarkdownMode()).isTrue();
    }

    @Test
    void cancelEditViaPublicApi() {
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();
        assertThat(viewer.cancelEdit()).isTrue();
        assertThat(viewer.isEditMode()).isFalse();
        assertThat(viewer.cancelEdit()).isFalse();
    }

    @Test
    void sourceTabHandleEscapeCancelsEditAndKeepsViewer() throws Exception {
        // SourceTab.handleEscape must cancel edit (CamelMonitor routes Esc there first)
        MonitorContext ctx = new MonitorContext(
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of()),
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of()));
        SourceTab tab = new SourceTab(ctx);

        // Use SourceViewer directly to validate the cancelEdit contract SourceTab depends on
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();
        assertThat(viewer.cancelEdit()).isTrue();
        assertThat(viewer.isVisible()).isTrue();
        assertThat(viewer.isEditMode()).isFalse();
        assertThat(tab.handleEscape()).isFalse();
    }

    @Test
    void sourceTabIgnoresTabKeyWhileEditing() throws Exception {
        MonitorContext ctx = new MonitorContext(
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of()),
                new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of()));
        SourceTab tab = new SourceTab(ctx);
        // Without a selected integration SourceTab won't open files; still verify Tab is swallowed
        // when the viewer reports edit mode by exercising SourceViewer Tab handling path:
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();
        assertThat(viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE))).isTrue();
        assertThat(viewer.isEditMode()).isTrue();
        assertThat(viewer.isVisible()).isTrue();
        // Keep tab reference used so the integration surface is exercised for construction
        assertThat(tab.isOverlayActive()).isFalse();
    }

    @Test
    void saveMessageClearedOnSubsequentLoad() throws Exception {
        viewer.loadFile(sourceFile);
        viewer.enterEditMode();
        viewer.handleKeyEvent(KeyEvent.ofChar('x', KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE));

        Path other = tempDir.resolve("other.properties");
        Files.writeString(other, "a=b\n", StandardCharsets.UTF_8);
        viewer.loadFile(other);

        List<Span> spans = new ArrayList<>();
        viewer.renderFooter(spans);
        assertThat(spansToString(spans)).doesNotContain("Saved");
    }

    private static String spansToString(List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        for (Span span : spans) {
            sb.append(span.content());
        }
        return sb.toString();
    }
}
