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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.tamboui.style.Color;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the code colors of every theme: whether they come from the theme's own {@code syntax-*} tokens or from the
 * Monokai / GitHub fallback, code must stay readable on the theme's background.
 */
@Isolated
class ThemeSyntaxPaletteTest {

    /** Minimum contrast of a code color on the theme background (WCAG ratio; 3:1 is the large-text level). */
    private static final double MIN_CONTRAST = 3.0;

    private static final List<String> SYNTAX_TOKENS = List.of(
            "syntax-comment", "syntax-string", "syntax-keyword", "syntax-function", "syntax-type",
            "syntax-constant", "syntax-text");

    /** Themes that keep the built-in Monokai (dark) or GitHub (light) code palette on purpose. */
    private static final Set<ThemeMode> FALLBACK_PALETTE = EnumSet.of(
            ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.MONOCHROME, ThemeMode.CRT);

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        Theme.resetForTesting();
    }

    @ParameterizedTest
    @EnumSource(ThemeMode.class)
    void codeColorsAreReadableOnTheBackground(ThemeMode mode) {
        Theme.setMode(mode.id());
        Color bg = Theme.baseBg();
        syntaxColors().forEach((name, color) -> {
            double ratio = contrast(color, bg);
            assertTrue(ratio >= MIN_CONTRAST,
                    () -> String.format("%s: %s %s has contrast %.2f on %s, below %.1f",
                            mode.id(), name, color, ratio, bg, MIN_CONTRAST));
        });
    }

    @ParameterizedTest
    @EnumSource(ThemeMode.class)
    void keywordsStringsAndCommentsStandApart(ThemeMode mode) {
        Theme.setMode(mode.id());
        Color keyword = Theme.syntaxKeyword();
        Color string = Theme.syntaxString();
        Color comment = Theme.syntaxComment();
        Color text = Theme.syntaxText();
        assertNotEquals(keyword, string, mode.id() + ": keywords and strings share a color");
        assertNotEquals(keyword, comment, mode.id() + ": keywords and comments share a color");
        assertNotEquals(string, comment, mode.id() + ": strings and comments share a color");
        for (Color c : List.of(keyword, string, comment)) {
            assertNotEquals(text, c, mode.id() + ": plain code text has the color of keywords, strings or comments");
        }
    }

    @ParameterizedTest
    @EnumSource(ThemeMode.class)
    void editorSchemeThemesDefineTheirOwnCodePalette(ThemeMode mode) throws IOException {
        String css = stylesheet(mode);
        if (FALLBACK_PALETTE.contains(mode)) {
            assertTrue(SYNTAX_TOKENS.stream().noneMatch(t -> css.contains("#" + t)),
                    mode.id() + " is expected to keep the built-in code palette");
            return;
        }
        // A theme defines all syntax tokens or none, so a half-defined palette never mixes with Monokai.
        for (String token : SYNTAX_TOKENS) {
            assertTrue(css.contains("#" + token), mode.id() + " lacks " + token);
        }
        Theme.setMode(mode.id());
        Color fallback = mode.isLight() ? SyntaxHighlighter.LIGHT_KEYWORD : SyntaxHighlighter.MONOKAI_KEYWORD;
        assertNotEquals(fallback, Theme.syntaxKeyword(), mode.id() + " still shows the fallback keyword color");
    }

    @ParameterizedTest
    @EnumSource(ThemeMode.class)
    void plainCodeTextMatchesTheThemeText(ThemeMode mode) {
        if (FALLBACK_PALETTE.contains(mode) || mode == ThemeMode.TURBO_PASCAL) {
            return;
        }
        Theme.setMode(mode.id());
        assertEquals(Theme.baseFg(), Theme.syntaxText(), mode.id() + ": code text should be the theme's text color");
    }

    private static Map<String, Color> syntaxColors() {
        Map<String, Color> colors = new LinkedHashMap<>();
        colors.put("comment", Theme.syntaxComment());
        colors.put("string", Theme.syntaxString());
        colors.put("keyword", Theme.syntaxKeyword());
        colors.put("function", Theme.syntaxFunction());
        colors.put("type", Theme.syntaxType());
        colors.put("constant", Theme.syntaxConstant());
        colors.put("text", Theme.syntaxText());
        return colors;
    }

    private static String stylesheet(ThemeMode mode) throws IOException {
        try (InputStream in = ThemeSyntaxPaletteTest.class.getClassLoader()
                .getResourceAsStream(mode.stylesheetResource())) {
            assertNotNull(in, "missing " + mode.stylesheetResource());
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color color) {
        Color.Rgb rgb = color.toRgb();
        return 0.2126 * channel(rgb.r()) + 0.7152 * channel(rgb.g()) + 0.0722 * channel(rgb.b());
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
