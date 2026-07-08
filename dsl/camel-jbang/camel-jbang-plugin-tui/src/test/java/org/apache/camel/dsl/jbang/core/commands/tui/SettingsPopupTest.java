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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
 * Drives {@link SettingsPopup} through key events to verify it seeds from persisted {@link TuiSettings}, cycles theme
 * and starting-tab values, edits the folder field, persists on Enter (applying the theme live), and discards on Esc.
 * Assertions check concrete persisted values, not merely non-null, so a change in save/apply logic will fail the test.
 */
@Isolated
class SettingsPopupTest {

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

    private static List<TabRegistry.TabEntry> tabs() {
        return List.of(
                new TabRegistry.TabEntry("🐪", "Overview", "overview", "1", 0, -1),
                new TabRegistry.TabEntry("🩺", "Health", "health", "7", 6, -1),
                new TabRegistry.TabEntry("☕", "Beans", "beans", "B", 9, 0));
    }

    private static KeyEvent key(KeyCode code) {
        return KeyEvent.ofKey(code, KeyModifiers.NONE);
    }

    @Test
    void openSeedsCurrentValuesFromSettings(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Files.writeString(tempDir.resolve(CommandLineHelper.USER_CONFIG),
                "camel.tui.theme=light\ncamel.tui.startTab=Health\ncamel.tui.defaultFolder=/tmp/foo\n");

        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        assertTrue(popup.isVisible());
        assertEquals("light", popup.selectedThemeId());
        assertEquals("Health", popup.selectedStartTab());
        assertEquals("/tmp/foo", popup.folderText());
    }

    @Test
    void themeCyclerWrapsAroundValues(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        assertEquals("dark", popup.selectedThemeId(), "with no config, theme defaults to the active mode (dark)");
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("light", popup.selectedThemeId());
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("dark", popup.selectedThemeId(), "cycling wraps back to the first value");
    }

    @Test
    void startingTabCyclerWrapsAcrossAllTabs(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        popup.handleKeyEvent(key(KeyCode.DOWN));
        assertEquals(1, popup.selectedRow());
        assertEquals("Overview", popup.selectedStartTab());
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("Health", popup.selectedStartTab());
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("Beans", popup.selectedStartTab());
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("Overview", popup.selectedStartTab(), "cycling wraps past the last tab");
    }

    @Test
    void folderFieldAcceptsTypedText(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        popup.handleKeyEvent(key(KeyCode.DOWN));
        popup.handleKeyEvent(key(KeyCode.DOWN));
        assertEquals(2, popup.selectedRow());
        popup.handleKeyEvent(KeyEvent.ofChar('/'));
        popup.handleKeyEvent(KeyEvent.ofChar('a'));
        assertEquals("/a", popup.folderText());
    }

    @Test
    void folderFieldRejectsControlCharacters(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        popup.handleKeyEvent(key(KeyCode.DOWN));
        popup.handleKeyEvent(key(KeyCode.DOWN));
        assertEquals(2, popup.selectedRow());
        popup.handleKeyEvent(KeyEvent.ofChar(0x01));
        popup.handleKeyEvent(KeyEvent.ofChar(0x00));
        popup.handleKeyEvent(KeyEvent.ofChar('x'));

        assertEquals("x", popup.folderText());
    }

    @Test
    void enterPersistsSelectionsAndAppliesThemeLive(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        // Exercise the real persistence path (disk read/write) against the isolated home directory.
        Theme.resetForTesting(false);

        boolean[] cleared = { false };
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.setClearScreen(() -> cleared[0] = true);
        popup.open();

        popup.handleKeyEvent(KeyEvent.ofChar(' ')); // theme dark -> light
        assertEquals("light", popup.selectedThemeId());
        popup.handleKeyEvent(key(KeyCode.DOWN));
        popup.handleKeyEvent(KeyEvent.ofChar(' ')); // starting tab Overview -> Health
        assertEquals("Health", popup.selectedStartTab());
        popup.handleKeyEvent(key(KeyCode.DOWN));
        for (char c : "/tmp/p".toCharArray()) {
            popup.handleKeyEvent(KeyEvent.ofChar(c));
        }

        popup.handleKeyEvent(key(KeyCode.ENTER));

        assertFalse(popup.isVisible(), "Enter closes the popup");
        assertTrue(cleared[0], "a theme change on save must trigger a screen clear");
        assertEquals("light", Theme.mode(), "the theme must be applied live");

        TuiSettings persisted = TuiSettings.load();
        assertEquals("light", persisted.getThemeId());
        assertEquals("Health", persisted.getStartTab());
        assertEquals("/tmp/p", persisted.getDefaultFolder());
    }

    @Test
    void escapeDiscardsInFormChanges(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Files.writeString(tempDir.resolve(CommandLineHelper.USER_CONFIG), "camel.tui.theme=dark\n");

        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();
        popup.handleKeyEvent(KeyEvent.ofChar(' ')); // cycle theme dark -> light, but do not save
        popup.handleKeyEvent(key(KeyCode.ESCAPE));

        assertFalse(popup.isVisible());
        assertEquals("dark", TuiSettings.load().getThemeId(), "Esc must not persist the in-form change");
    }
}
