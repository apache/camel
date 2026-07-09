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

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ThemeModeTest {

    @Test
    void parseAcceptsCanonicalIds() {
        assertEquals(Optional.of(ThemeMode.DARK), ThemeMode.parse("dark"));
        assertEquals(Optional.of(ThemeMode.LIGHT), ThemeMode.parse("light"));
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(Optional.of(ThemeMode.LIGHT), ThemeMode.parse("LIGHT"));
        assertEquals(Optional.of(ThemeMode.LIGHT), ThemeMode.parse("LiGhT"));
    }

    @Test
    void parseStripsSurroundingWhitespace() {
        assertEquals(Optional.of(ThemeMode.DARK), ThemeMode.parse("  dark  "));
        assertEquals(Optional.of(ThemeMode.DARK), ThemeMode.parse("\tdark\n"));
    }

    @Test
    void parseRejectsUnknownOrNullValues() {
        assertEquals(Optional.empty(), ThemeMode.parse("sepia"));
        assertEquals(Optional.empty(), ThemeMode.parse(""));
        assertEquals(Optional.empty(), ThemeMode.parse(null));
    }

    @Test
    void parseOrDefaultFallsBackToDarkForUnknownValues() {
        assertEquals(ThemeMode.DARK, ThemeMode.parseOrDefault("sepia"));
        assertEquals(ThemeMode.DARK, ThemeMode.parseOrDefault(null));
        assertEquals(ThemeMode.LIGHT, ThemeMode.parseOrDefault("light"));
    }

    @Test
    void nextCyclesToNextThemeInDeclarationOrder() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.DARK.next());
        assertEquals(ThemeMode.DRACULA, ThemeMode.LIGHT.next());
        assertEquals(ThemeMode.DARK, ThemeMode.CRT.next());
    }

    @Test
    void idsListsAllCanonicalValuesInDeclarationOrder() {
        assertEquals(List.of(
                "dark", "light", "dracula", "nord", "solarized-dark", "solarized-light",
                "gruvbox-dark", "catppuccin-mocha", "catppuccin-latte", "tokyo-night",
                "rose-pine", "kanagawa", "everforest", "monochrome", "crt"), ThemeMode.ids());
    }

    @Test
    void stylesheetResourcePointsAtTheModeSpecificTcssFile() {
        assertEquals("tui/themes/dark.tcss", ThemeMode.DARK.stylesheetResource());
        assertEquals("tui/themes/light.tcss", ThemeMode.LIGHT.stylesheetResource());
        assertEquals("tui/themes/catppuccin-mocha.tcss", ThemeMode.CATPPUCCIN_MOCHA.stylesheetResource());
        assertEquals("tui/themes/tokyo-night.tcss", ThemeMode.TOKYO_NIGHT.stylesheetResource());
    }

    @Test
    void idIsDistinctLowercaseCanonicalFormPerMode() {
        assertEquals("dark", ThemeMode.DARK.id());
        assertEquals("light", ThemeMode.LIGHT.id());
        assertEquals("catppuccin-mocha", ThemeMode.CATPPUCCIN_MOCHA.id());
        assertNotEquals(ThemeMode.DARK.id(), ThemeMode.LIGHT.id());
    }

    @Test
    void labelReturnsHumanReadableDisplayName() {
        assertEquals("Dark", ThemeMode.DARK.label());
        assertEquals("Catppuccin Mocha", ThemeMode.CATPPUCCIN_MOCHA.label());
        assertEquals("Rosé Pine", ThemeMode.ROSE_PINE.label());
    }

    @Test
    void parseRecognizesAllThemeIds() {
        for (ThemeMode m : ThemeMode.values()) {
            assertEquals(Optional.of(m), ThemeMode.parse(m.id()));
        }
    }
}
