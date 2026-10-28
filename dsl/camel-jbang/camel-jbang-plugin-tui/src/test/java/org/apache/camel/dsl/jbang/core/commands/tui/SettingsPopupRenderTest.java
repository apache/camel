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
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        Rect area = new Rect(0, 0, 80, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        popup.render(frame, area);
        String rendered = TuiTestHelper.bufferToString(buffer);

        assertTrue(rendered.contains("Settings"), "the popup title should be shown");
        assertTrue(rendered.contains("Theme"), "the Theme row should be shown");
        assertTrue(rendered.contains("Starting Tab"), "the Starting Tab row should be shown");
        assertTrue(rendered.contains("Log Pin"), "the Log Pin row should be shown");
        assertTrue(rendered.contains("Rate per"), "the Rate per row should be shown");
        assertTrue(rendered.contains("Panel Theme"), "the Panel Theme row should be shown");
        assertTrue(rendered.contains("Default Folder"), "the Default Folder row should be shown");
        assertTrue(rendered.contains("AI Provider"), "the AI Provider row should be shown");
        assertTrue(rendered.contains("AI Model"), "the AI Model row should be shown");
        assertTrue(rendered.contains("AI Base URL"), "the AI Base URL row should be shown");
        assertTrue(rendered.contains("Shell History"), "the Shell History row should be shown");
        assertTrue(rendered.contains("AI History"), "the AI History row should be shown");
        assertTrue(rendered.contains("ACP Command"), "the ACP Command row should be shown");
    }

    @Test
    void lineOfAccountsForEveryRowAndDivider() {
        assertEquals(SettingsPopup.ROW_COUNT + SettingsPopup.DIVIDERS,
                SettingsPopup.lineOf(SettingsPopup.ROW_COUNT - 1) + 1);
    }

    @Test
    void lineOfIsTheLineEachRowIsDrawnOn(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(List.of(new TabRegistry.TabEntry("🐪", "Overview", "overview", "1", 0, -1)));
        popup.open();
        Rect area = new Rect(0, 0, 80, 40);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        String[] lines = TuiTestHelper.bufferToString(buffer).split("\n");

        // the popup starts two rows down, and its rows below its top border
        int first = 3;
        assertTrue(lines[first + SettingsPopup.lineOf(SettingsPopup.ROW_THEME)].contains("Theme:"));
        assertTrue(lines[first + SettingsPopup.lineOf(SettingsPopup.ROW_PANEL_THEME)].contains("Panel Theme:"));
        assertTrue(lines[first + SettingsPopup.lineOf(SettingsPopup.ROW_ROUTE_TREE)].contains("Route Tree:"));
        assertTrue(lines[first + SettingsPopup.lineOf(SettingsPopup.ROW_FOLDER)].contains("Default Folder:"));
        assertTrue(lines[first + SettingsPopup.lineOf(SettingsPopup.ROW_SHELL_HISTORY)].contains("Shell History:"));
        assertTrue(lines[first + SettingsPopup.lineOf(SettingsPopup.ROW_AI_PROVIDER)].contains("AI Provider:"));
        assertTrue(lines[first + SettingsPopup.lineOf(SettingsPopup.ROW_COUNT - 1)].contains("ACP Command:"));
    }

    @Test
    void scrollingUpShowsTheDefaultFolderRow(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(List.of(new TabRegistry.TabEntry("🐪", "Overview", "overview", "1", 0, -1)));
        popup.open();
        Rect area = new Rect(0, 0, 80, 16);
        for (int i = 0; i < SettingsPopup.ROW_AI_ACP_COMMAND; i++) {
            popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        popup.render(Frame.forTesting(Buffer.empty(area)), area);
        // from ACP Command back up to Default Folder, the row the view scrolls up to
        for (int i = 0; i < SettingsPopup.ROW_AI_ACP_COMMAND - SettingsPopup.ROW_FOLDER; i++) {
            popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE));
            popup.render(Frame.forTesting(Buffer.empty(area)), area);
        }
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);

        assertTrue(TuiTestHelper.bufferToString(buffer).contains("Default Folder:"),
                TuiTestHelper.bufferToString(buffer));
    }

    @Test
    void scrollsTheSelectedRowIntoViewOnAShortTerminal(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(List.of(
                new TabRegistry.TabEntry("🐪", "Overview", "overview", "1", 0, -1),
                new TabRegistry.TabEntry("🩺", "Health", "health", "7", 6, -1)));
        popup.open();
        Rect area = new Rect(0, 0, 80, 25);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("Theme:"), rendered);
        assertFalse(rendered.contains("ACP Command"), "the last rows do not fit in 25 lines");
        for (int i = 0; i < SettingsPopup.ROW_AI_ACP_COMMAND; i++) {
            popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area);
        rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("ACP Command"), rendered);
        assertTrue(rendered.contains("AI History"), rendered);
        // not "Theme:", which the Panel Theme row still shows
        assertFalse(rendered.contains("Starting Tab:"), "the first rows scrolled out");
    }
}
