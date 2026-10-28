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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** Moves the selection down to the row with Down, as the user does. */
    private static void moveTo(SettingsPopup popup, int row) {
        while (popup.selectedRow() < row) {
            popup.handleKeyEvent(key(KeyCode.DOWN));
        }
        assertThat(popup.selectedRow()).isEqualTo(row);
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

        int themeCount = ThemeMode.ids().size();
        for (int i = 0; i < themeCount - 1; i++) {
            popup.handleKeyEvent(KeyEvent.ofChar(' '));
        }
        assertEquals("dark", popup.selectedThemeId(), "cycling through every theme wraps back to the first value");
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

        moveTo(popup, SettingsPopup.ROW_FOLDER);
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

        moveTo(popup, SettingsPopup.ROW_FOLDER);
        popup.handleKeyEvent(KeyEvent.ofChar(0x01));
        popup.handleKeyEvent(KeyEvent.ofChar(0x00));
        popup.handleKeyEvent(KeyEvent.ofChar('x'));

        assertEquals("x", popup.folderText());
    }

    @Test
    void aiRowsPersistProviderModelAndUrl(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        moveTo(popup, SettingsPopup.ROW_AI_PROVIDER);
        assertEquals("auto", popup.selectedAiProvider());
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("ollama", popup.selectedAiProvider());

        popup.handleKeyEvent(key(KeyCode.DOWN));
        for (char c : "gemini-3.5-flash".toCharArray()) {
            popup.handleKeyEvent(KeyEvent.ofChar(c));
        }
        assertEquals("gemini-3.5-flash", popup.aiModelText());

        popup.handleKeyEvent(key(KeyCode.DOWN));
        for (char c : "https://example.test".toCharArray()) {
            popup.handleKeyEvent(KeyEvent.ofChar(c));
        }
        assertEquals("https://example.test", popup.aiUrlText());

        popup.handleKeyEvent(key(KeyCode.ENTER));

        TuiSettings persisted = TuiSettings.load();
        assertEquals("ollama", persisted.getAiProvider());
        assertEquals("gemini-3.5-flash", persisted.getAiModel());
        assertEquals("https://example.test", persisted.getAiUrl());
    }

    @Test
    void aiToolsRowCyclesModesAndPersistsNonDefault(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        moveTo(popup, SettingsPopup.ROW_AI_TOOLS);
        assertEquals("auto", popup.selectedAiTools());

        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("core", popup.selectedAiTools());
        popup.handleKeyEvent(key(KeyCode.RIGHT));
        assertEquals("full", popup.selectedAiTools());
        popup.handleKeyEvent(key(KeyCode.RIGHT));
        assertEquals("auto", popup.selectedAiTools());
        popup.handleKeyEvent(key(KeyCode.LEFT));
        assertEquals("full", popup.selectedAiTools());

        popup.handleKeyEvent(key(KeyCode.ENTER));
        assertEquals("full", TuiSettings.load().getAiTools());

        // auto is the default and is not written to the settings file
        SettingsPopup reopened = new SettingsPopup();
        reopened.setTabEntries(tabs());
        reopened.open();
        assertEquals("full", reopened.selectedAiTools());
        moveTo(reopened, SettingsPopup.ROW_AI_TOOLS);
        reopened.handleKeyEvent(key(KeyCode.RIGHT));
        assertEquals("auto", reopened.selectedAiTools());
        reopened.handleKeyEvent(key(KeyCode.ENTER));
        assertNull(TuiSettings.load().getAiTools());
    }

    @Test
    void aiOverviewRowCyclesModesAndPersistsNonDefault(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        moveTo(popup, SettingsPopup.ROW_AI_OVERVIEW);
        assertEquals("manual", popup.selectedAiOverview());
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("auto", popup.selectedAiOverview());
        popup.handleKeyEvent(key(KeyCode.RIGHT));
        assertEquals("off", popup.selectedAiOverview());
        popup.handleKeyEvent(key(KeyCode.ENTER));
        assertEquals("off", TuiSettings.load().getAiOverview());

        // manual is the default and is not written to the settings file
        SettingsPopup reopened = new SettingsPopup();
        reopened.setTabEntries(tabs());
        reopened.open();
        assertEquals("off", reopened.selectedAiOverview());
        moveTo(reopened, SettingsPopup.ROW_AI_OVERVIEW);
        reopened.handleKeyEvent(key(KeyCode.RIGHT));
        assertEquals("manual", reopened.selectedAiOverview());
        reopened.handleKeyEvent(key(KeyCode.ENTER));
        assertNull(TuiSettings.load().getAiOverview());
    }

    @Test
    void historyFieldsPersistValues(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        moveTo(popup, SettingsPopup.ROW_SHELL_HISTORY);
        for (char c : "50".toCharArray()) {
            popup.handleKeyEvent(KeyEvent.ofChar(c));
        }
        assertEquals("50", popup.shellHistoryText());

        moveTo(popup, SettingsPopup.ROW_AI_PROMPT_HISTORY);
        for (char c : "200".toCharArray()) {
            popup.handleKeyEvent(KeyEvent.ofChar(c));
        }
        assertEquals("200", popup.aiPromptHistoryText());

        popup.handleKeyEvent(key(KeyCode.ENTER));

        TuiSettings persisted = TuiSettings.load();
        assertEquals("50", persisted.getShellHistory());
        assertEquals("200", persisted.getAiPromptHistory());
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
        moveTo(popup, SettingsPopup.ROW_FOLDER);
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
        assertEquals("dark", Theme.mode(), "Esc must revert the live preview back to the theme active before open()");
    }

    @Test
    void themeCyclingPreviewsLiveWithoutClosingThePopup(@TempDir Path tempDir) {
        useHome(tempDir);
        boolean[] cleared = { false };
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.setClearScreen(() -> cleared[0] = true);
        popup.open();

        assertEquals("dark", Theme.mode());
        popup.handleKeyEvent(KeyEvent.ofChar(' ')); // theme dark -> light

        assertTrue(popup.isVisible(), "cycling the theme row must not close the dialog");
        assertEquals("light", Theme.mode(), "the previewed theme must apply immediately, without saving");
        assertTrue(cleared[0], "the screen must refresh so the live preview is visible");
    }

    @Test
    void panelRowsPersistPositionAndSpace(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        moveTo(popup, SettingsPopup.ROW_PANEL_POSITION);
        assertEquals("bottom", popup.selectedPanelPosition(), "panels open at the bottom by default");
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("top", popup.selectedPanelPosition());

        popup.handleKeyEvent(key(KeyCode.DOWN));
        assertEquals("move", popup.selectedPanelSpace(), "panels take space from the tab by default");
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertEquals("overlay", popup.selectedPanelSpace());

        popup.handleKeyEvent(key(KeyCode.ENTER));

        TuiSettings persisted = TuiSettings.load();
        assertEquals("top", persisted.getPanelPosition());
        assertEquals("overlay", persisted.getPanelSpace());
        assertTrue(persisted.isPanelTop());
        assertTrue(persisted.isPanelOverlay());
    }

    @Test
    void panelThemeRowPreviewsSavesAndRevertsThePanelTheme(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();

        moveTo(popup, SettingsPopup.ROW_PANEL_THEME);
        assertThat(popup.selectedPanelTheme()).as("the panels use the main theme by default").isNull();
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertThat(popup.selectedPanelTheme()).isEqualTo("dark");
        popup.handleKeyEvent(key(KeyCode.RIGHT));
        assertThat(popup.selectedPanelTheme()).isEqualTo("light");
        assertThat(Theme.panelTheme()).as("previewed before it is saved").isEqualTo("light");

        // Esc puts the panels back as they were
        popup.handleKeyEvent(key(KeyCode.ESCAPE));
        assertThat(Theme.panelTheme()).isNull();
        assertThat(TuiSettings.load().getPanelTheme()).isNull();

        // Enter keeps it and saves it
        popup.open();
        moveTo(popup, SettingsPopup.ROW_PANEL_THEME);
        popup.handleKeyEvent(key(KeyCode.LEFT));
        assertThat(popup.selectedPanelTheme()).as("Left wraps to the last theme").isEqualTo("turbo-pascal");
        popup.handleKeyEvent(key(KeyCode.ENTER));
        assertThat(Theme.panelTheme()).isEqualTo("turbo-pascal");
        assertThat(TuiSettings.load().getPanelTheme()).isEqualTo("turbo-pascal");

        // after the last theme comes the main theme again, which removes the key instead of writing it
        popup.open();
        moveTo(popup, SettingsPopup.ROW_PANEL_THEME);
        assertThat(popup.selectedPanelTheme()).isEqualTo("turbo-pascal");
        popup.handleKeyEvent(KeyEvent.ofChar(' '));
        assertThat(popup.selectedPanelTheme()).isNull();
        popup.handleKeyEvent(key(KeyCode.ENTER));
        assertThat(Theme.panelTheme()).isNull();
        assertThat(TuiSettings.load().getPanelTheme()).isNull();
    }

    @Test
    void aiProviderDropdownOffersAcpAgents() {
        SettingsPopup popup = new SettingsPopup();
        popup.open();
        assertTrue(popup.aiProviderOptionsForTesting().contains("acp:claude"));
        assertTrue(popup.aiProviderOptionsForTesting().contains("acp:custom"));
        assertEquals("auto", popup.aiProviderOptionsForTesting().get(popup.aiProviderOptionsForTesting().size() - 1));
    }

    @Test
    void panelRowsDefaultValuesAreNotPersisted(@TempDir Path tempDir) {
        useHome(tempDir);
        SettingsPopup popup = new SettingsPopup();
        popup.setTabEntries(tabs());
        popup.open();
        popup.handleKeyEvent(key(KeyCode.ENTER));

        TuiSettings persisted = TuiSettings.load();
        assertEquals(null, persisted.getPanelPosition(), "the default position is not written to the config file");
        assertEquals(null, persisted.getPanelSpace(), "the default space mode is not written to the config file");
        assertFalse(persisted.isPanelTop());
        assertFalse(persisted.isPanelOverlay());
    }
}
