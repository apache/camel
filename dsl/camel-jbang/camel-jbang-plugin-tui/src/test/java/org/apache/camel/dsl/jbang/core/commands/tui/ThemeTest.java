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

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThemeTest {

    @TempDir
    Path home;

    @BeforeEach
    void setUp() {
        // Isolate user config per test so persistence never touches the real home dir.
        CommandLineHelper.useHomeDir(home.toString());
        Theme.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        // Reset the process-wide engine so toggles do not leak across tests.
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
        assertEquals(Style.EMPTY.fg(Color.DARK_GRAY), Theme.border());
        assertEquals(Style.EMPTY.fg(Theme.ACCENT), Theme.borderFocused());
        assertEquals(Style.EMPTY.fg(Theme.ACCENT).bold(), Theme.title());
        assertEquals(Style.EMPTY.fg(Color.LIGHT_GREEN), Theme.success());
        assertEquals(Style.EMPTY.fg(Color.LIGHT_YELLOW), Theme.warning());
        assertEquals(Style.EMPTY.fg(Color.LIGHT_RED), Theme.error());
        assertEquals(Style.EMPTY.dim(), Theme.muted());
        assertEquals(Style.EMPTY.fg(Color.WHITE).bold().onBlue(), Theme.selectionBg());
        assertEquals(Style.EMPTY.fg(Color.CYAN), Theme.info());
        assertEquals(Style.EMPTY.fg(Color.MAGENTA), Theme.notice());
        assertEquals(Style.EMPTY.fg(Color.LIGHT_GREEN), Theme.mcpActive());
        assertEquals(Style.EMPTY.fg(Color.DARK_GRAY), Theme.mcpIdle());
        assertEquals(Style.EMPTY.fg(Color.LIGHT_RED), Theme.mcpDown());
        assertEquals(Color.rgb(0x1C, 0x1C, 0x1C), Theme.zebra());
    }

    @Test
    void lightPaletteDiffersForStatusTokensButKeepsBrand() {
        // Light theme: status hues are explicit dark hex; brand orange is unchanged.
        Theme.setMode("light");
        assertEquals(Color.rgb(0xF6, 0x91, 0x23), Theme.accent());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x00, 0x77, 0x00)), Theme.success());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x88, 0x88, 0x88)), Theme.border());
        // MCP indicator hues track the light palette: idle gray and down red differ from the dark ANSI variants.
        assertEquals(Style.EMPTY.fg(Color.rgb(0x00, 0x77, 0x00)), Theme.mcpActive());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x88, 0x88, 0x88)), Theme.mcpIdle());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xcc, 0x00, 0x00)), Theme.mcpDown());
        // Zebra background is theme-aware: light gray on light, unlike the dark gray used on dark.
        assertEquals(Color.rgb(0xEB, 0xEB, 0xEB), Theme.zebra());
    }

    @Test
    void toggleFlipsModeAndChangesThemeDependentToken() {
        Theme.setMode("dark");
        assertEquals("dark", Theme.mode());
        Style darkBorder = Theme.border();

        String newMode = Theme.toggle();

        assertEquals("light", newMode);
        assertEquals("light", Theme.mode());
        assertNotEquals(darkBorder, Theme.border());
    }

    @Test
    void togglePersistsAndAFreshLoadActivatesIt() {
        Theme.setMode("dark");
        Theme.toggle(); // -> light, persisted to camel.tui.theme

        String[] stored = { null };
        CommandLineHelper.loadProperties(p -> stored[0] = p.getProperty("camel.tui.theme"));
        assertEquals("light", stored[0]);

        // A fresh Theme load (statics reset) must read back the persisted mode.
        Theme.resetForTesting();
        assertEquals("light", Theme.mode());
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
        assertNotNull(Theme.mcpActive());
        assertNotNull(Theme.mcpIdle());
        assertNotNull(Theme.mcpDown());
        assertNotNull(Theme.zebra());
    }
}
