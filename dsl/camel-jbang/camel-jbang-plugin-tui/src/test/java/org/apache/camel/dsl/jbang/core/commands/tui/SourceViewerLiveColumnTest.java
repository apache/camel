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
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.tui.SourceViewer.LiveLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live run data column after the line numbers: it is aligned, and its width only grows so the code does not jump
 * when a count goes from 99 to 100 and back.
 */
class SourceViewerLiveColumnTest {

    @TempDir
    Path tempDir;

    private final Map<Integer, LiveLine> data = new HashMap<>();
    private SourceViewer viewer;

    @BeforeEach
    void open() throws IOException {
        Path file = tempDir.resolve("MyRoute.java");
        Files.writeString(file, "from\nfilter\nto\n");
        viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.setLiveRunData(path -> data);
    }

    @Test
    void noDataNoColumn() {
        viewer.refreshLiveLinesForTesting();
        assertThat(viewer.liveColumnForTesting(0)).isEmpty();
    }

    @Test
    void theColumnIsAlignedOnEveryLine() {
        data.put(0, new LiveLine(120, 3, 0));
        data.put(2, new LiveLine(7, 0, 0));
        viewer.refreshLiveLinesForTesting();

        assertThat(viewer.liveColumnForTesting(0)).isEqualTo(" 120 ✗3  │");
        assertThat(viewer.liveColumnForTesting(1)).isEqualTo("         │");
        assertThat(viewer.liveColumnForTesting(2)).isEqualTo("   7     │");
    }

    @Test
    void theWidthDoesNotShrinkWhenACountDoes() {
        data.put(0, new LiveLine(99999, 0, 0));
        viewer.refreshLiveLinesForTesting();
        String wide = viewer.liveColumnForTesting(0);

        // the integration restarted, so the counts start over
        data.put(0, new LiveLine(5, 0, 0));
        viewer.refreshLiveLinesForTesting();

        assertThat(viewer.liveColumnForTesting(0)).hasSameSizeAs(wide).isEqualTo("     5 │");
    }

    @Test
    void theMeanTimeHasItsOwnColumn() {
        data.put(0, new LiveLine(10, 0, 25));
        data.put(1, new LiveLine(10, 0, 0));
        viewer.refreshLiveLinesForTesting();

        assertThat(viewer.liveColumnForTesting(0)).isEqualTo("  10 25ms │");
        assertThat(viewer.liveColumnForTesting(1)).isEqualTo("  10      │");
    }
}
