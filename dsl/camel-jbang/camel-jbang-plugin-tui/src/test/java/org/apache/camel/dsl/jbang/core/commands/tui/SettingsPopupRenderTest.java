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

import java.nio.file.Path;
import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders {@link SettingsPopup} into a virtual buffer and asserts the dialog shows its title and all setting rows,
 * mirroring the other {@code *RenderTest} classes in this package.
 */
@Isolated
class SettingsPopupRenderTest {

    private String originalHome;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        Theme.resetForTesting();
        if (originalHome != null) {
            CommandLineHelper.useHomeDir(originalHome);
        }
    }

    private void useHome(Path dir) {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(dir.toString());
    }

    @Test
    void rendersTitleAndAllSettingRows(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(List.of(
                new TabRegistry.TabEntry("🐪", "Overview", "overview", "1", 0, -1),
                new TabRegistry.TabEntry("🩺", "Health", "health", "7", 6, -1)));
        popup.open();

        Rect area = new Rect(0, 0, 80, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        popup.render(frame, area);
        String rendered = TuiTestHelper.bufferToString(buffer);

        assertTrue(rendered.contains("Settings"), "the popup title should be shown");
        assertTrue(rendered.contains("Theme"), "the Theme row should be shown");
        assertTrue(rendered.contains("Starting Tab"), "the Starting Tab row should be shown");
        assertTrue(rendered.contains("Log Pin"), "the Log Pin row should be shown");
        assertTrue(rendered.contains("Default Folder"), "the Default Folder row should be shown");
    }
}
