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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shift+F9 in the Source editor applies the fix of the problem on the line of the cursor.
 */
class SourceQuickFixTest {

    @TempDir
    Path tempDir;

    private final AtomicReference<String> lastNotification = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void theQuestionToFixAProblemWithAi() {
        String q = AiFixPrompt.of(Path.of("/work/app"), Path.of("/work/app/src/MyRoute.java"), 6,
                "timer: Unknown option 'peroid'", "        from(\"timer:tick?peroid=1000\")");
        assertThat(q).startsWith("Fix the problem on line 6 of src/MyRoute.java: timer: Unknown option 'peroid'\n")
                .contains("The line is: from(\"timer:tick?peroid=1000\")")
                .contains("camel_edit_file").contains("camel_validate_source");
    }

    @Test
    void shiftF8SavesTheFileAndAsksTheAiToFixTheProblem() throws Exception {
        Path file = tempDir.resolve("MyRoute.java");
        Files.writeString(file, """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() throws Exception {
                        from("timer:tick?period=1000")
                            .to("seda:out");
                    }
                }
                """, StandardCharsets.UTF_8);
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        SourceEditAssist assist = new SourceEditAssist(new MonitorContext(data, infraData));
        SourceViewer viewer = new SourceViewer();
        viewer.setRouteValidator(content -> assist.validateRoutes(file, content));
        AtomicReference<String> asked = new AtomicReference<>();
        viewer.setAskAi((f, line, problem, text) -> asked.set(f.getFileName() + ":" + line + ": " + problem));
        viewer.loadFile(file);
        viewer.enterEditMode();
        // an unsaved edit that makes a problem on line 6: period becomes perio
        for (int i = 0; i < 5; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.HOME, KeyModifiers.NONE));
        int col = viewer.editText().split("\n")[5].indexOf("period") + 6;
        for (int i = 0; i < col; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.RIGHT, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE));
        // the problem is marked (Ctrl+S would too; the AI fix needs the problem on the line)
        viewer.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.CTRL));
        assertThat(viewer.inlineErrors()).containsKey(5);

        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked.get()).startsWith("MyRoute.java:6: timer: Unknown option 'perio'");
        // saved as it was in the editor, and the editor shows the file the AI changes
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("perio=1000");
        assertThat(viewer.isEditMode()).isFalse();
    }

    @Test
    void shiftF9AppliesTheFixOfTheProblemOnTheLine() throws Exception {
        Path file = tempDir.resolve("MyRoute.java");
        Files.writeString(file, """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() throws Exception {
                        from("timer:tick?peroid=1000")
                            .to("seda:out");
                    }
                }
                """, StandardCharsets.UTF_8);
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        SourceEditAssist assist = new SourceEditAssist(new MonitorContext(data, infraData));
        SourceViewer viewer = new SourceViewer();
        viewer.setNotificationCallback((msg, error) -> lastNotification.set(msg));
        viewer.setRouteValidator(content -> assist.validateRoutes(file, content));
        viewer.loadFile(file);
        viewer.enterEditMode();
        // a Java file with a problem is saved, and the problem marked on its line
        viewer.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.CTRL));
        assertThat(viewer.inlineErrors()).containsOnlyKeys(5);

        // Shift+F9 elsewhere does nothing
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F9, KeyModifiers.SHIFT));
        assertThat(viewer.editText()).contains("peroid=1000");

        for (int i = 0; i < 5; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F9, KeyModifiers.SHIFT));
        assertThat(viewer.editText().split("\n")[5]).isEqualTo("        from(\"timer:tick?period=1000\")");
        assertThat(lastNotification.get()).isEqualTo("Fixed: peroid → period");

        // undo takes the fix back
        viewer.handleKeyEvent(KeyEvent.ofChar('z', KeyModifiers.CTRL));
        assertThat(viewer.editText()).contains("peroid=1000");
    }
}
