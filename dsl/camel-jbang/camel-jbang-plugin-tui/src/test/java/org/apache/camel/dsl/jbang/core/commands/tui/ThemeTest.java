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

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals(Style.EMPTY.fg(Color.rgb(0x4E, 0xC9, 0xB0)), Theme.mcpActive());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x60, 0x60, 0x60)), Theme.mcpIdle());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xF4, 0x87, 0x71)), Theme.mcpDown());
        assertEquals(Color.rgb(0x25, 0x25, 0x25), Theme.zebra());
        assertEquals(Color.rgb(0x1E, 0x1E, 0x1E), Theme.baseBg());
        assertEquals(Color.rgb(0xD4, 0xD4, 0xD4), Theme.baseFg());
    }

    @Test
    void lightPaletteDiffersForStatusTokensButKeepsBrand() {
        // Light theme: status hues are explicit dark hex; brand orange is unchanged.
        Theme.setMode("light");
        assertEquals(Color.rgb(0xF6, 0x91, 0x23), Theme.accent());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x22, 0x86, 0x3A)), Theme.success());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xC0, 0xC0, 0xC0)), Theme.border());
        // MCP indicator hues track the light palette: idle gray and down red differ from the dark ANSI variants.
        assertEquals(Style.EMPTY.fg(Color.rgb(0x22, 0x86, 0x3A)), Theme.mcpActive());
        assertEquals(Style.EMPTY.fg(Color.rgb(0x95, 0x9D, 0xA5)), Theme.mcpIdle());
        assertEquals(Style.EMPTY.fg(Color.rgb(0xD7, 0x3A, 0x49)), Theme.mcpDown());
        // Zebra background is theme-aware: light gray on light, unlike the dark gray used on dark.
        assertEquals(Color.rgb(0xF6, 0xF8, 0xFA), Theme.zebra());
        // Base colors differ significantly between themes: white background and dark text on light mode.
        assertEquals(Color.rgb(0xFF, 0xFF, 0xFF), Theme.baseBg());
        assertEquals(Color.rgb(0x24, 0x29, 0x2E), Theme.baseFg());
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
    void toggleFlipsModeBackAndForth() {
        Theme.setMode("dark");
        assertEquals("light", Theme.toggle());
        assertEquals("light", Theme.mode());
        assertEquals("dark", Theme.toggle());
        assertEquals("dark", Theme.mode());
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
        assertNotNull(Theme.baseBg());
        assertNotNull(Theme.baseFg());
    }
}
