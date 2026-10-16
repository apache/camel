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
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class ThemeTest {

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        Theme.resetForTesting();
    }

    @Test
    void accentIsBrandOrangeInDefaultTheme() {
        // Guards palette drift: the brand accent must stay Camel orange #F69123 and load from CSS.
        Theme.setMode("dark");
        assertEquals(Color.rgb(0xF6, 0x91, 0x23), Theme.accent());
        assertEquals(Color.rgb(0xF6, 0x91, 0x23), Theme.ACCENT);
    }

    @Test
    void darkPaletteResolvesExpectedTokens() {
        // Asserts the dark stylesheet actually loads and each token resolves to the intended style,
        // not merely that the accessor exists.
        Theme.setMode("dark");
        assertEquals(Style.EMPTY.fg(Color.WHITE).bg(Theme.ACCENT).bold(), Theme.accentBg());
        assertEquals(Style.EMPTY.fg(Color.BLACK).bg(Theme.ACCENT).bold(), Theme.hintKey());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x50, 0x50, 0x50)), Theme.border());
        assertEquals(Style.EMPTY.fg(Theme.ACCENT), Theme.borderFocused());
        assertEquals(Style.EMPTY.fg(Theme.ACCENT).bold(), Theme.title());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x4E, 0xC9, 0xB0)), Theme.success());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xDC, 0xDC, 0xAA)), Theme.warning());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xF4, 0x87, 0x71)), Theme.error());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x80, 0x80, 0x80)), Theme.muted());
        assertEquals(Style.EMPTY.fg(Color.WHITE).bg(Color.rgb(0x26, 0x4F, 0x78)).bold(), Theme.selectionBg());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x9C, 0xDC, 0xFE)), Theme.info());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xC5, 0x86, 0xC0)), Theme.notice());
        assertEquals(Color.rgb(0x25, 0x25, 0x25), Theme.zebra());
        assertEquals(Color.rgb(0x1E, 0x1E, 0x1E), Theme.baseBg());
        assertEquals(Color.rgb(0xD4, 0xD4, 0xD4), Theme.baseFg());
    }

    @Test
    void lightPaletteDiffersForStatusTokensButKeepsBrand() {
        // Light theme: status hues are explicit dark hex; brand orange is unchanged.
        Theme.setMode("light");
        assertEquals(Color.rgb(0xD9, 0x77, 0x06), Theme.accent());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x22, 0x86, 0x3A)), Theme.success());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xC0, 0xC0, 0xC0)), Theme.border());
        // Zebra background is theme-aware: light gray on light, unlike the dark gray used on dark.
        assertEquals(Color.rgb(0xF6, 0xF8, 0xFA), Theme.zebra());
        // Base colors differ significantly between themes: white background and dark text on light mode.
        assertEquals(Color.rgb(0xFF, 0xFF, 0xFF), Theme.baseBg());
        assertEquals(Color.rgb(0x24, 0x29, 0x2E), Theme.baseFg());
    }

    @Test
    void toggleCyclesToNextThemeAndChangesToken() {
        Theme.setMode("dark");
        assertEquals("dark", Theme.mode());
        Style darkBorder = Theme.border();

        String newMode = Theme.toggle();

        assertEquals("light", newMode);
        assertEquals("light", Theme.mode());
        assertNotEquals(darkBorder, Theme.border());
    }

    @Test
    void toggleCyclesThroughAllThemes() {
        Theme.setMode("dark");
        assertEquals("light", Theme.toggle());
        assertEquals("dracula", Theme.toggle());
    }

    @Test
    void applyStartupModeSelectsLightPalette() {
        Theme.setMode("dark");
        Theme.applyStartupMode("light");
        assertEquals("light", Theme.mode());
        assertEquals(Color.rgb(0xFF, 0xFF, 0xFF), Theme.baseBg());
    }

    @Test
    void applyStartupModeAcceptsMixedCase() {
        Theme.applyStartupMode("LIGHT");
        assertEquals("light", Theme.mode());
    }

    @Test
    void applyStartupModeRejectsUnknownValue() {
        // Unlike setMode(), an invalid --theme value must never be silently coerced to a default: CamelMonitor relies
        // on isValidMode() for a friendly CLI error, but the type itself must also refuse to guess.
        assertThrows(IllegalArgumentException.class, () -> Theme.applyStartupMode("sepia"));
    }

    @Test
    void setModeAcceptsMixedCase() {
        Theme.setMode("LIGHT");
        assertEquals("light", Theme.mode());
    }

    @Test
    void isValidModeAcceptsAllKnownThemes() {
        assertTrue(Theme.isValidMode("dark"));
        assertTrue(Theme.isValidMode("LIGHT"));
        assertTrue(Theme.isValidMode("dracula"));
        assertTrue(Theme.isValidMode("catppuccin-mocha"));
        assertTrue(Theme.isValidMode("tokyo-night"));
        assertFalse(Theme.isValidMode("sepia"));
        assertFalse(Theme.isValidMode(null));
    }

    @Test
    void toggleTakesEffectOnFirstActivationOutsideTestMode(@TempDir Path tempDir) {
        // Regression test for the "toggle needs two activations" bug: every other test here runs through
        // Theme.resetForTesting(), which forces testMode=true and short-circuits both persist() and the
        // persisted-mode disk-reload guard in engine() - exactly the two paths responsible for the original bug.
        // This test leaves testMode disabled, against an isolated home directory, to actually exercise them.
        String originalHomeDir = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(tempDir.toString());
        try {
            Theme.resetForTesting(false);
            Theme.setMode("dark");
            assertEquals("dark", Theme.mode());

            String newMode = Theme.toggle();

            assertEquals("light", newMode, "a single toggle() call must flip the mode immediately");
            assertEquals("light", Theme.mode(), "the effective mode must reflect the toggle without a second activation");
        } finally {
            CommandLineHelper.useHomeDir(originalHomeDir);
            Theme.resetForTesting();
        }
    }

    @Test
    void persistedModeReadFailureFallsBackAndRetriesOnNextInit(@TempDir Path tempDir) throws Exception {
        // Regression test for the read-failure branch of tryLoadPersistedMode(): a corrupt config file must not crash
        // the TUI, and must not permanently pin the session to the default theme - the very next reinitialization
        // (triggered here by setMode()) should retry the disk read and pick up a since-fixed value.
        String originalHomeDir = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(tempDir.toString());
        try {
            Path configFile = tempDir.resolve(CommandLineHelper.USER_CONFIG);
            Files.writeString(configFile, "camel.tui.theme=\\uZZZZ\n");

            Theme.resetForTesting(false);
            assertEquals("dark", Theme.mode(), "a corrupt config file must fall back to the default theme, not crash");

            Files.writeString(configFile, "camel.tui.theme=light\n");
            Theme.setMode("dark");

            assertEquals("light", Theme.mode(),
                    "a later reinitialization must retry the persisted-mode read instead of being pinned by the earlier failure");
        } finally {
            CommandLineHelper.useHomeDir(originalHomeDir);
            Theme.resetForTesting();
        }
    }

    @Test
    void applyStartupModeOverridesPersistedConfigOutsideTestMode(@TempDir Path tempDir) throws Exception {
        // Regression test for the "--theme wins over camel.tui.theme" contract: exercised against real disk I/O
        // (outside test mode), unlike applyStartupModeSelectsLightPalette which runs under testMode=true, where
        // persistence loading is unconditionally skipped and so can't prove an override actually happened.
        String originalHomeDir = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(tempDir.toString());
        try {
            Path configFile = tempDir.resolve(CommandLineHelper.USER_CONFIG);
            Files.writeString(configFile, "camel.tui.theme=light\n");

            Theme.resetForTesting(false);
            Theme.applyStartupMode("dark");

            assertEquals("dark", Theme.mode(), "--theme must win over a persisted camel.tui.theme value");
            assertEquals(Color.rgb(0x1E, 0x1E, 0x1E), Theme.baseBg(), "the dark palette must actually be active");
        } finally {
            CommandLineHelper.useHomeDir(originalHomeDir);
            Theme.resetForTesting();
        }
    }

    @Test
    void previewAppliesThemeWithoutPersisting() {
        Theme.setMode("dark");
        Theme.preview("dracula");
        assertEquals("dracula", Theme.mode());
        assertEquals("dark", Theme.persistedMode());
    }

    @Test
    void revertPreviewRestoresOriginalTheme() {
        Theme.setMode("dark");
        Theme.preview("nord");
        assertEquals("nord", Theme.mode());

        Theme.revertPreview();
        assertEquals("dark", Theme.mode());
        assertEquals("dark", Theme.persistedMode());
    }

    @Test
    void confirmPreviewClearsPreviewState() {
        Theme.setMode("dark");
        Theme.preview("tokyo-night");
        Theme.confirmPreview();
        assertEquals("tokyo-night", Theme.mode());
        assertEquals("tokyo-night", Theme.persistedMode());
    }

    @Test
    void previewSequenceTracksOnlyOriginal() {
        Theme.setMode("dark");
        Theme.preview("dracula");
        Theme.preview("nord");
        Theme.preview("catppuccin-mocha");
        assertEquals("dark", Theme.persistedMode());

        Theme.revertPreview();
        assertEquals("dark", Theme.mode());
    }

    @Test
    void syntaxColorsFallBackToMonokaiForDarkThemesWithoutSyntaxTokens() {
        Theme.setMode("dark");
        assertEquals(SyntaxHighlighter.MONOKAI_COMMENT, Theme.syntaxComment());
        assertEquals(SyntaxHighlighter.MONOKAI_STRING, Theme.syntaxString());
        assertEquals(SyntaxHighlighter.MONOKAI_KEYWORD, Theme.syntaxKeyword());
        assertEquals(SyntaxHighlighter.MONOKAI_FUNCTION, Theme.syntaxFunction());
        assertEquals(SyntaxHighlighter.MONOKAI_TYPE, Theme.syntaxType());
        assertEquals(SyntaxHighlighter.MONOKAI_CONSTANT, Theme.syntaxConstant());
        assertEquals(SyntaxHighlighter.MONOKAI_TEXT, Theme.syntaxText());
    }

    @Test
    void syntaxColorsFallBackToLightPaletteForLightThemesWithoutSyntaxTokens() {
        Theme.setMode("light");
        assertEquals(SyntaxHighlighter.LIGHT_COMMENT, Theme.syntaxComment());
        assertEquals(SyntaxHighlighter.LIGHT_STRING, Theme.syntaxString());
        assertEquals(SyntaxHighlighter.LIGHT_KEYWORD, Theme.syntaxKeyword());
        assertEquals(SyntaxHighlighter.LIGHT_TEXT, Theme.syntaxText());
    }

    @Test
    void turboPascalOverridesSyntaxColorsFromStylesheet() {
        Theme.setMode("turbo-pascal");
        // Borland's editor: reserved words white, code yellow, comments grey, strings cyan.
        assertEquals(Color.rgb(0xFF, 0xFF, 0xFF), Theme.syntaxKeyword());
        assertEquals(Color.rgb(0xFF, 0xFF, 0x55), Theme.syntaxText());
        assertEquals(Color.rgb(0xAA, 0xAA, 0xAA), Theme.syntaxComment());
        assertEquals(Color.rgb(0x0B, 0xDF, 0xEA), Theme.syntaxString());
        assertNotEquals(SyntaxHighlighter.MONOKAI_KEYWORD, Theme.syntaxKeyword());
    }

    @Test
    void syntaxStylesCarryTextStyleFromStylesheet() {
        // CRT: a green phosphor terminal tells code apart by weight, keywords in bold high intensity.
        Theme.setMode("crt");
        assertEquals(Style.EMPTY.fg(Color.rgb(0x66, 0xFF, 0x66)).bold(), Theme.syntaxKeywordStyle());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x22, 0xAA, 0x22)), Theme.syntaxCommentStyle());

        // Monochrome: no color, comments in italic grey.
        Theme.setMode("monochrome");
        assertEquals(Style.EMPTY.fg(Color.rgb(0x8A, 0x8A, 0x8A)).italic(), Theme.syntaxCommentStyle());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xFF, 0xFF, 0xFF)).bold(), Theme.syntaxKeywordStyle());
    }

    @Test
    void syntaxStylesArePlainColorsForThemesWithoutSyntaxTokens() {
        Theme.setMode("dark");
        assertEquals(Style.EMPTY.fg(SyntaxHighlighter.MONOKAI_KEYWORD), Theme.syntaxKeywordStyle());
        assertEquals(Style.EMPTY.fg(SyntaxHighlighter.MONOKAI_COMMENT), Theme.syntaxCommentStyle());
    }

    @Test
    void syntaxColorsFollowThemeSwitches() {
        Theme.setMode("turbo-pascal");
        assertEquals(Color.rgb(0xFF, 0xFF, 0xFF), Theme.syntaxKeyword());

        Theme.setMode("dark");
        assertEquals(SyntaxHighlighter.MONOKAI_KEYWORD, Theme.syntaxKeyword());

        Theme.preview("turbo-pascal");
        assertEquals(Color.rgb(0xFF, 0xFF, 0xFF), Theme.syntaxKeyword());
        Theme.revertPreview();
        assertEquals(SyntaxHighlighter.MONOKAI_KEYWORD, Theme.syntaxKeyword());
    }

    @Test
    void turboPascalPaletteResolvesExpectedTokens() {
        Theme.setMode("turbo-pascal");
        assertEquals(Color.rgb(0x01, 0x03, 0x78), Theme.baseBg());
        assertEquals(Color.rgb(0xFF, 0xFF, 0x55), Theme.baseFg());
        assertEquals(Color.rgb(0x0B, 0xDF, 0xEA), Theme.accent());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x0B, 0xDF, 0xEA)), Theme.borderFocused());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xAA, 0xAA, 0xAA)), Theme.border());
        // The selection bar: editor blue on the highlight yellow.
        assertEquals(Style.EMPTY.fg(Color.rgb(0x01, 0x03, 0x78)).bg(Color.rgb(0xF7, 0xEE, 0x0B)).bold(),
                Theme.selectionBg());
    }

    @Test
    void allThemesLoadSuccessfully() {
        for (ThemeMode m : ThemeMode.values()) {
            Theme.resetForTesting();
            Theme.setMode(m.id());
            assertEquals(m.id(), Theme.mode());
            assertNotNull(Theme.accent());
            assertNotNull(Theme.baseBg());
            assertNotNull(Theme.baseFg());
            assertNotNull(Theme.success());
            assertNotNull(Theme.error());
        }
    }

    @Test
    void accessorsNeverReturnNull() {
        // Resilience: even when resolution is exercised the palette is always usable.
        Theme.setMode("dark");
        assertNotNull(Theme.accentBg());
        assertNotNull(Theme.hintKey());
        assertNotNull(Theme.border());
        assertNotNull(Theme.borderFocused());
        assertNotNull(Theme.title());
        assertNotNull(Theme.success());
        assertNotNull(Theme.warning());
        assertNotNull(Theme.error());
        assertNotNull(Theme.muted());
        assertNotNull(Theme.selectionBg());
        assertNotNull(Theme.info());
        assertNotNull(Theme.notice());
        assertNotNull(Theme.zebra());
        assertNotNull(Theme.baseBg());
        assertNotNull(Theme.baseFg());
    }

    @Test
    void panelThemeAppliesOnlyWhileAPanelDraws() {
        Theme.setMode("dark");
        Theme.setPanelTheme("light");
        assertThat(Theme.panelTheme()).isEqualTo("light");
        // resolved outside first, so a cached main token must not leak into the panel or the other way round
        Style mainSuccess = Theme.success();

        Color[] colors = new Color[2];
        Style[] styles = new Style[1];
        boolean[] dark = new boolean[1];
        Theme.inPanel(() -> {
            colors[0] = Theme.baseBg();
            colors[1] = Theme.accent();
            styles[0] = Theme.success();
            dark[0] = Theme.isDark();
        });

        assertThat(colors[0]).isEqualTo(Color.rgb(0xFF, 0xFF, 0xFF));
        assertThat(colors[1]).isEqualTo(Color.rgb(0xD9, 0x77, 0x06));
        assertThat(styles[0]).isEqualTo(Style.EMPTY.fg(Color.rgb(0x22, 0x86, 0x3A)));
        assertThat(dark[0]).isFalse();
        // and the main theme is untouched around it
        assertThat(Theme.baseBg()).isEqualTo(Color.rgb(0x1E, 0x1E, 0x1E));
        assertThat(Theme.accent()).isEqualTo(Theme.ACCENT);
        assertThat(Theme.success()).isEqualTo(mainSuccess);
        assertThat(Theme.isDark()).isTrue();
        assertThat(Theme.mode()).isEqualTo("dark");
    }

    @Test
    void otherThreadsKeepTheMainThemeWhileAPanelDraws() {
        Theme.setMode("dark");
        Theme.setPanelTheme("light");
        // a thread of its own: CompletableFuture.join() may run a queued task on the joining thread itself
        ExecutorService otherThread = Executors.newSingleThreadExecutor();
        try {
            Theme.inPanel(() -> assertThat(otherThread.submit(Theme::baseBg))
                    .succeedsWithin(Duration.ofSeconds(10))
                    .isEqualTo(Color.rgb(0x1E, 0x1E, 0x1E)));
        } finally {
            otherThread.shutdownNow();
        }
    }

    @Test
    void sameOrUnknownPanelThemeDrawsThePanelsWithTheMainTheme() {
        Theme.setMode("dark");
        for (String id : new String[] { null, Theme.PANEL_THEME_SAME, "no-such-theme" }) {
            Theme.setPanelTheme("light");
            Theme.setPanelTheme(id);
            assertThat(Theme.panelTheme()).as("panel theme after %s", id).isNull();
            Color[] inside = new Color[1];
            Theme.inPanel(() -> inside[0] = Theme.baseBg());
            assertThat(inside[0]).as("panel background after %s", id).isEqualTo(Color.rgb(0x1E, 0x1E, 0x1E));
        }
    }

    @Test
    void thePanelAreaTakesThePanelThemeBackground() {
        // the order CamelMonitor fills a frame in: the panel's area inside the panel scope, then the whole frame
        Theme.setMode("dark");
        Theme.setPanelTheme("light");
        Rect frame = new Rect(0, 0, 10, 4);
        Buffer buffer = Buffer.empty(frame);
        Rect panel = new Rect(0, 2, 10, 2);
        Theme.inPanel(() -> Theme.applyBaseColors(buffer, panel));
        Theme.applyBaseColors(buffer, frame);

        assertThat(buffer.get(0, 1).style().bg()).contains(Color.rgb(0x1E, 0x1E, 0x1E));
        assertThat(buffer.get(0, 2).style().bg()).contains(Color.rgb(0xFF, 0xFF, 0xFF));
        assertThat(buffer.get(9, 3).style().bg()).contains(Color.rgb(0xFF, 0xFF, 0xFF));
    }
}
