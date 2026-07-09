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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Named color theme selector for the Camel TUI. */
enum ThemeMode {

    DARK("dark", "Dark", false),
    LIGHT("light", "Light", true),
    DRACULA("dracula", "Dracula", false),
    NORD("nord", "Nord", false),
    SOLARIZED_DARK("solarized-dark", "Solarized Dark", false),
    SOLARIZED_LIGHT("solarized-light", "Solarized Light", true),
    GRUVBOX_DARK("gruvbox-dark", "Gruvbox Dark", false),
    CATPPUCCIN_MOCHA("catppuccin-mocha", "Catppuccin Mocha", false),
    CATPPUCCIN_LATTE("catppuccin-latte", "Catppuccin Latte", true),
    TOKYO_NIGHT("tokyo-night", "Tokyo Night", false),
    ROSE_PINE("rose-pine", "Rosé Pine", false),
    KANAGAWA("kanagawa", "Kanagawa", false),
    EVERFOREST("everforest", "Everforest", false),
    MONOCHROME("monochrome", "Monochrome", false),
    CRT("crt", "CRT", false);

    private final String id;
    private final String label;
    private final boolean light;

    ThemeMode(String id, String label, boolean light) {
        this.id = id;
        this.label = label;
        this.light = light;
    }

    /** Lowercase config / CLI value (e.g. {@code dark}, {@code catppuccin-mocha}). */
    String id() {
        return id;
    }

    /** Human-readable display name (e.g. {@code Catppuccin Mocha}). */
    String label() {
        return label;
    }

    /** Whether this is a light-background theme. */
    boolean isLight() {
        return light;
    }

    /** Classpath resource path of this mode's stylesheet, e.g. {@code tui/themes/dark.tcss}. */
    String stylesheetResource() {
        return "tui/themes/" + id + ".tcss";
    }

    /** Returns the next theme in declaration order, wrapping around at the end. */
    ThemeMode next() {
        ThemeMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    static Optional<ThemeMode> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (ThemeMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    static ThemeMode parseOrDefault(String value) {
        return parse(value).orElse(DARK);
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(m -> m.id).toList();
    }

    /** Dark themes in declaration order. */
    static List<ThemeMode> darkThemes() {
        return Arrays.stream(values()).filter(m -> !m.light).toList();
    }

    /** Light themes in declaration order. */
    static List<ThemeMode> lightThemes() {
        return Arrays.stream(values()).filter(m -> m.light).toList();
    }
}
