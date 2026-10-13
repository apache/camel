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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SourceViewerWordWrapTest {

    @TempDir
    Path tempDir;

    @Test
    void theSelectedLineStaysOnScreenWhenLongLinesWrap() throws IOException {
        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(longLines());
        viewer.handleKeyEvent(KeyEvent.ofChar('w'));
        viewer.goToLine(29);

        assertThat(render(viewer)).contains("the last line");
    }

    @Test
    void theSelectedLineStaysOnScreenWhenLongLinesWrapWithoutLineNumbers() throws IOException {
        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(longLines());
        viewer.handleKeyEvent(KeyEvent.ofChar('w'));
        viewer.handleKeyEvent(KeyEvent.ofChar('p'));
        viewer.goToLine(29);

        assertThat(render(viewer)).contains("the last line");
    }

    /** 30 indented lines that each wrap onto a few rows. */
    private Path longLines() throws IOException {
        String words = "a long message with words that wrap ".repeat(6);
        StringBuilder yaml = new StringBuilder();
        for (int i = 0; i < 29; i++) {
            yaml.append("          message: \"").append(words).append(i).append("\"\n");
        }
        yaml.append("          message: \"").append(words).append("the last line\"\n");
        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml.toString());
        return file;
    }

    private static String render(SourceViewer viewer) {
        Rect area = new Rect(0, 0, 80, 24);
        Buffer buffer = Buffer.empty(area);
        viewer.render(Frame.forTesting(buffer), area);
        return TuiTestHelper.bufferToString(buffer);
    }
}
