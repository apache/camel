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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SourceViewerGoToLineTest {

    @TempDir
    Path tempDir;

    @Test
    void goToLinePositionsCursorInEditMode() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - log:",
                "          message: hello",
                "      - to:",
                "          uri: kafka:orders",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        viewer.goToLine(4);

        assertThat(viewer.getSelectedLine()).isEqualTo(4);
        assertThat(viewer.editState().cursorRow()).isEqualTo(4);
    }

    @Test
    void goToLineWorksInViewMode() throws IOException {
        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, "line0\nline1\nline2\n");

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);

        viewer.goToLine(2);

        assertThat(viewer.getSelectedLine()).isEqualTo(2);
        assertThat(viewer.isEditMode()).isFalse();
    }
}
