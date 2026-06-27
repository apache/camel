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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.tamboui.css.Styleable;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central semantic theme for the Camel TUI. Call sites reference intent (accent, border, success, ...) so the concrete
 * palette lives in one place. Values are resolved from CSS stylesheets ({@code dark.tcss} / {@code light.tcss}) through
 * a shared {@link StyleEngine}; the active stylesheet can be switched at runtime and is persisted to user config.
 * <p/>
 * Palette policy: the brand accent is Camel orange (truecolor), reserved for accent and borders. Status colors use ANSI
 * names in dark mode (so they respect the terminal) and explicit darker hex in light mode. If a stylesheet is missing
 * or malformed, the facade falls back to the built-in palette below and logs once, so a cosmetic failure never crashes
 * the TUI.
 */
final class Theme {

    /** Camel brand orange. */
    static final Color ACCENT = Color.rgb(0xF6, 0x91, 0x23);

    private static final Logger LOG = LoggerFactory.getLogger(Theme.class);

    private static final String PROP_KEY = "camel.tui.theme";
    private static final String DARK = "dark";
    private static final String LIGHT = "light";

    // Fallback palette mirrors the dark stylesheet, used when CSS is unavailable.
    private static final Style FALLBACK_ACCENT_BG = Style.EMPTY.fg(Color.WHITE).bg(ACCENT).bold();
    private static final Style FALLBACK_BORDER = Style.EMPTY.fg(Color.DARK_GRAY);
    private static final Style FALLBACK_BORDER_FOCUSED = Style.EMPTY.fg(ACCENT);
    private static final Style FALLBACK_TITLE = Style.EMPTY.fg(ACCENT).bold();
    private static final Style FALLBACK_SUCCESS = Style.EMPTY.fg(Color.LIGHT_GREEN);
    private static final Style FALLBACK_WARNING = Style.EMPTY.fg(Color.LIGHT_YELLOW);
    private static final Style FALLBACK_ERROR = Style.EMPTY.fg(Color.LIGHT_RED);
    private static final Style FALLBACK_MUTED = Style.EMPTY.dim();
    private static final Style FALLBACK_SELECTION = Style.EMPTY.fg(Color.WHITE).bold().onBlue();
    private static final Style FALLBACK_INFO = Style.EMPTY.fg(Color.CYAN);
    private static final Style FALLBACK_NOTICE = Style.EMPTY.fg(Color.MAGENTA);
    private static final Color FALLBACK_ZEBRA = Color.rgb(0x1C, 0x1C, 0x1C);

    private static final Map<String, Style> CACHE = new HashMap<>();

    private static boolean initialized;
    private static boolean fallbackLogged;
    private static StyleEngine engine;
    private static String mode = DARK;

    private Theme() {
    }

    static Color accent() {
        StyleEngine e = engine();
        if (e == null) {
            return ACCENT;
        }
        try {
            return e.resolve(new Token("accent")).foreground().orElse(ACCENT);
        } catch (RuntimeException ex) {
            logFallbackOnce(ex);
            return ACCENT;
        }
    }

    /**
     * Subtle alternating-row background for zebra striping. Theme-aware (dark gray on dark, light gray on light) so
     * stripes stay readable on both terminals. Applied at the row level so it never overrides the selection highlight.
     */
    static Color zebra() {
        StyleEngine e = engine();
        if (e == null) {
            return FALLBACK_ZEBRA;
        }
        try {
            return e.resolve(new Token("row-alt")).background().orElse(FALLBACK_ZEBRA);
        } catch (RuntimeException ex) {
            logFallbackOnce(ex);
            return FALLBACK_ZEBRA;
        }
    }

    /** White-on-orange: active tab highlight and hint keys. */
    static Style accentBg() {
        return style("accent-bg", FALLBACK_ACCENT_BG);
    }

    /** Dim border for unfocused panels. */
    static Style border() {
        return style("border", FALLBACK_BORDER);
    }

    /** Orange border for the focused panel. */
    static Style borderFocused() {
        return style("border-focused", FALLBACK_BORDER_FOCUSED);
    }

    /** Panel and border titles. */
    static Style title() {
        return style("title", FALLBACK_TITLE);
    }

    static Style success() {
        return style("success", FALLBACK_SUCCESS);
    }

    static Style warning() {
        return style("warning", FALLBACK_WARNING);
    }

    static Style error() {
        return style("error", FALLBACK_ERROR);
    }

    static Style muted() {
        return style("muted", FALLBACK_MUTED);
    }

    /** Row/selection highlight (matches the existing list highlight). */
    static Style selectionBg() {
        return style("selection", FALLBACK_SELECTION);
    }

    /** Informational accent (header integration count). */
    static Style info() {
        return style("info", FALLBACK_INFO);
    }

    /** Secondary accent (header infra / selected). */
    static Style notice() {
        return style("notice", FALLBACK_NOTICE);
    }

    /** The active theme mode: {@code "dark"} or {@code "light"}. */
    static String mode() {
        engine();
        return mode;
    }

    /** Flip the active theme, clear the cache, persist the new value, and return the new mode. */
    static synchronized String toggle() {
        String next = DARK.equals(mode) ? LIGHT : DARK;
        setMode(next);
        persist(next);
        return next;
    }

    /** Activate a specific mode without persisting. Unknown values fall back to dark. */
    static synchronized void setMode(String newMode) {
        engine();
        String resolved = DARK.equals(newMode) || LIGHT.equals(newMode) ? newMode : DARK;
        if (engine != null) {
            try {
                engine.setActiveStylesheet(resolved);
            } catch (RuntimeException ex) {
                logFallbackOnce(ex);
            }
        }
        mode = resolved;
        CACHE.clear();
    }

    /** Test hook: drop all process-wide state so the next access reinitializes from config. */
    static synchronized void resetForTesting() {
        initialized = false;
        fallbackLogged = false;
        engine = null;
        mode = DARK;
        CACHE.clear();
    }

    private static synchronized Style style(String id, Style fallback) {
        StyleEngine e = engine();
        if (e == null) {
            return fallback;
        }
        return CACHE.computeIfAbsent(id, key -> {
            try {
                return e.resolve(new Token(key)).toStyle();
            } catch (RuntimeException ex) {
                logFallbackOnce(ex);
                return fallback;
            }
        });
    }

    private static synchronized StyleEngine engine() {
        if (initialized) {
            return engine;
        }
        initialized = true;
        mode = loadPersistedMode();
        try {
            StyleEngine e = StyleEngine.create();
            e.loadStylesheet(DARK, "tui/themes/dark.tcss");
            e.loadStylesheet(LIGHT, "tui/themes/light.tcss");
            e.setActiveStylesheet(mode);
            engine = e;
        } catch (Exception ex) {
            engine = null;
            logFallbackOnce(ex);
        }
        return engine;
    }

    private static String loadPersistedMode() {
        String[] holder = { DARK };
        try {
            CommandLineHelper.loadProperties(props -> {
                String v = props.getProperty(PROP_KEY);
                if (DARK.equals(v) || LIGHT.equals(v)) {
                    holder[0] = v;
                }
            });
        } catch (RuntimeException ex) {
            // Config unreadable; keep the default mode.
        }
        return holder[0];
    }

    private static void persist(String newMode) {
        try {
            CommandLineHelper.createPropertyFile(false);
            CommandLineHelper.loadProperties(props -> {
                props.setProperty(PROP_KEY, newMode);
                CommandLineHelper.storeProperties(props,
                        new Printer.QuietPrinter(new Printer.SystemOutPrinter()), false);
            });
        } catch (Exception ex) {
            logFallbackOnce(ex);
        }
    }

    private static void logFallbackOnce(Throwable t) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            LOG.warn("Camel TUI theme stylesheet unavailable; using built-in palette", t);
        }
    }

    /** Minimal synthetic {@link Styleable}: an id-only token used for engine resolution. */
    private record Token(String id) implements Styleable {

        @Override
        public Optional<String> cssId() {
            return Optional.of(id);
        }

        @Override
        public Set<String> cssClasses() {
            return Collections.emptySet();
        }

        @Override
        public Optional<Styleable> cssParent() {
            return Optional.empty();
        }
    }
}
