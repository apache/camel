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
import java.util.concurrent.atomic.AtomicInteger;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The refactorings of a YAML route in the editor, chosen with Ctrl+R: extract a step to a new file, replace the URI of
 * an endpoint, extract a value to a property. RouteRefactoringsTest has those of Java and XML routes.
 */
class SourceRefactorActionsTest {

    private static final String YAML = """
            - route:
                from:
                  uri: timer:tick
                  steps:
                    - split:
                        simple: ${body}
                        steps:
                          - to:
                              uri: direct:item
                    - log:
                        message: done
            """;

    @TempDir
    Path tempDir;

    @Test
    void extractAStepToANewFile() throws Exception {
        Path file = tempDir.resolve("orders.camel.yaml");
        SourceViewer viewer = viewer(file);
        AtomicInteger created = new AtomicInteger();
        viewer.setOnFileCreated(created::incrementAndGet);
        cursorAt(viewer, 4);
        // the split step offers one refactoring: extract to a new file
        refactor(viewer, 0, 0, "items");

        assertThat(line(viewer, 4)).isEqualTo("        - to:");
        assertThat(line(viewer, 5)).isEqualTo("            uri: direct:items");
        assertThat(line(viewer, 6)).isEqualTo("        - log:");
        // saved at once, so the jump links see both files
        assertThat(viewer.isDirty()).isFalse();
        assertThat(Files.readString(file)).contains("uri: direct:items").doesNotContain("split:");
        assertThat(Files.readString(tempDir.resolve("items.camel.yaml")))
                .contains("      uri: direct:items", "      - split:", "            - to:", "                uri: direct:item");
        assertThat(created).hasValue(1);
    }

    @Test
    void replaceTheUriOfAnEndpoint() throws Exception {
        SourceViewer viewer = viewer(tempDir.resolve("orders.camel.yaml"));
        cursorAt(viewer, 2);
        // the input starts with the uri of the line
        refactor(viewer, 0, "timer:tick".length(), "timer:clock");

        assertThat(line(viewer, 2)).isEqualTo("      uri: timer:clock");
        assertThat(viewer.isDirty()).isTrue();
    }

    @Test
    void extractAValueToAProperty() throws Exception {
        SourceViewer viewer = viewer(tempDir.resolve("orders.camel.yaml"));
        cursorAt(viewer, 10);
        // extract the log step to a new file comes first
        refactor(viewer, 1, 0, "app.done");

        assertThat(line(viewer, 10)).isEqualTo("            message: \"{{app.done}}\"");
        assertThat(Files.readString(tempDir.resolve("application.properties"))).isEqualTo("app.done=done\n");
    }

    private static SourceViewer viewer(Path file) throws Exception {
        Files.writeString(file, YAML, StandardCharsets.UTF_8);
        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        return viewer;
    }

    /** Ctrl+R, the refactoring below the first, its input cleared of the given chars, the value typed, Enter. */
    private static void refactor(SourceViewer viewer, int down, int clear, String value) {
        viewer.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.CTRL));
        for (int i = 0; i < down; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        for (int i = 0; i < clear; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE));
        }
        for (char ch : value.toCharArray()) {
            viewer.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
    }

    /** Puts the cursor at the end of the line. */
    private static void cursorAt(SourceViewer viewer, int row) {
        for (int i = 0; i < row; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
    }

    private static String line(SourceViewer viewer, int row) {
        return viewer.editText().split("\n")[row];
    }
}
