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

/**
 * A file an AI tool wrote is shown again in the viewer, unless it is being edited.
 */
class SourceViewerReloadTest {

    @TempDir
    Path tempDir;

    @Test
    void theViewerShowsWhatTheAiWrote() throws IOException {
        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, "line0\nline1 ${bdy}\nline2\n");
        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.goToLine(1);

        Files.writeString(file, "line0\nline1 ${body}\nline2\n");
        viewer.reloadIfShowing(file);

        viewer.enterEditMode();
        assertThat(viewer.editState().text()).contains("${body}").doesNotContain("${bdy}");
        assertThat(viewer.getSelectedLine()).isEqualTo(1);
    }

    @Test
    void anEditInProgressIsLeftAlone() throws IOException {
        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, "mine\n");
        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        Files.writeString(file, "theirs\n");
        viewer.reloadIfShowing(file);

        assertThat(viewer.isEditMode()).isTrue();
        assertThat(viewer.editState().text()).contains("mine");
    }

    @Test
    void anotherFileIsNotReloaded() throws IOException {
        Path file = tempDir.resolve("a.camel.yaml");
        Path other = tempDir.resolve("b.camel.yaml");
        Files.writeString(file, "a\n");
        Files.writeString(other, "b\n");
        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);

        Files.writeString(file, "changed\n");
        viewer.reloadIfShowing(other);

        viewer.enterEditMode();
        assertThat(viewer.editState().text()).contains("a").doesNotContain("changed");
    }
}
