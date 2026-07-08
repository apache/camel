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
 * Palette policy: the brand accent is Camel orange (truecolor), used for accent tokens, focused borders, and titles.
 * Status colors use explicit truecolor hex values in both themes (no ANSI names); accent-bg/hint-key/selection keep
 * plain white/black foregrounds. Fallbacks mirror {@code dark.tcss}. If a stylesheet is missing or malformed, the
 * facade falls back to the built-in palette below and logs once, so a cosmetic failure never crashes the TUI.
 */
public final class Theme {

    /** Camel brand orange. */
    static final Color ACCENT = Color.rgb(0xF6, 0x91, 0x23);

    private static final Logger LOG = LoggerFactory.getLogger(Theme.class);

    private static final String PROP_KEY = "camel.tui.theme";

    // Fallback palette mirrors the dark stylesheet, used when CSS is unavailable.
    private static final Style FALLBACK_ACCENT_BG = Style.EMPTY.fg(Color.WHITE).bg(ACCENT).bold();
    private static final Style FALLBACK_HINT_KEY = Style.EMPTY.fg(Color.BLACK).bg(ACCENT).bold();
    private static final Style FALLBACK_BORDER = Style.EMPTY.fg(Color.rgb(0x50, 0x50, 0x50));
    private static final Style FALLBACK_BORDER_FOCUSED = Style.EMPTY.fg(ACCENT);
    private static final Style FALLBACK_TITLE = Style.EMPTY.fg(ACCENT).bold();
    private static final Style FALLBACK_SUCCESS = Style.EMPTY.fg(Color.rgb(0x4E, 0xC9, 0xB0));
    private static final Style FALLBACK_WARNING = Style.EMPTY.fg(Color.rgb(0xDC, 0xDC, 0xAA));
    private static final Style FALLBACK_ERROR = Style.EMPTY.fg(Color.rgb(0xF4, 0x87, 0x71));
    private static final Style FALLBACK_MUTED = Style.EMPTY.fg(Color.rgb(0x80, 0x80, 0x80));
    private static final Style FALLBACK_SELECTION = Style.EMPTY.fg(Color.WHITE).bg(Color.rgb(0x26, 0x4F, 0x78)).bold();
    private static final Style FALLBACK_INFO = Style.EMPTY.fg(Color.rgb(0x9C, 0xDC, 0xFE));
    private static final Style FALLBACK_NOTICE = Style.EMPTY.fg(Color.rgb(0xC5, 0x86, 0xC0));
    private static final Style FALLBACK_MCP_ACTIVE = Style.EMPTY.fg(Color.rgb(0x4E, 0xC9, 0xB0));
    private static final Style FALLBACK_MCP_IDLE = Style.EMPTY.fg(Color.rgb(0x60, 0x60, 0x60));
    private static final Style FALLBACK_MCP_DOWN = Style.EMPTY.fg(Color.rgb(0xF4, 0x87, 0x71));
    private static final Color FALLBACK_ZEBRA = Color.rgb(0x25, 0x25, 0x25);
    private static final Color FALLBACK_BASE_BG = Color.rgb(0x1E, 0x1E, 0x1E);
    private static final Color FALLBACK_BASE_FG = Color.rgb(0xD4, 0xD4, 0xD4);

    // Content token fallbacks (dark-theme defaults).
    private static final Style FALLBACK_LABEL = Style.EMPTY.fg(Color.rgb(0xDC, 0xDC, 0xAA));
    private static final Style FALLBACK_CHANGE = Style.EMPTY.fg(Color.rgb(0xDC, 0xDC, 0xAA));
    private static final Style FALLBACK_SEARCH_MATCH = Style.EMPTY.fg(Color.BLACK).bg(Color.rgb(0xDC, 0xDC, 0xAA));

    // Diagram token fallbacks (dark-theme defaults).
    private static final Color FALLBACK_DIAGRAM_BORDER = Color.rgb(0x80, 0x80, 0x80);
    private static final Color FALLBACK_DIAGRAM_ID = FALLBACK_BASE_FG;
    private static final Color FALLBACK_DIAGRAM_FROM = Color.rgb(0x4E, 0xC9, 0xB0);
    private static final Color FALLBACK_DIAGRAM_TO = Color.rgb(0x56, 0xB6, 0xC2);
    private static final Color FALLBACK_DIAGRAM_CHOICE = Color.rgb(0xDC, 0xDC, 0xAA);
    private static final Color FALLBACK_DIAGRAM_ACTION = Color.rgb(0xC5, 0x86, 0xC0);
    private static final Color FALLBACK_DIAGRAM_EIP = Color.rgb(0x89, 0x57, 0xE5);
    private static final Color FALLBACK_DIAGRAM_DEFAULT = Color.rgb(0x80, 0x80, 0x80);

    private static final Map<String, Style> CACHE = new HashMap<>();

    private static boolean initialized;
    private static boolean persistedModeLoaded;
    private static boolean stylesheetFallbackLogged;
    private static boolean tokenFallbackLogged;
    private static boolean persistedReadFallbackLogged;
    private static boolean persistedWriteFallbackLogged;
    private static boolean testMode;
    private static StyleEngine engine;
    private static ThemeMode mode = ThemeMode.DARK;

    private Theme() {
    }

    public static synchronized Color accent() {
        StyleEngine e = engine();
        if (e == null) {
            return ACCENT;
        }
        try {
            return e.resolve(new Token("accent")).foreground().orElse(ACCENT);
        } catch (RuntimeException ex) {
            logTokenFallbackOnce(ex);
            return ACCENT;
        }
    }

    /**
     * Subtle alternating-row background for zebra striping. Theme-aware (dark gray on dark, light gray on light) so
     * stripes stay readable on both terminals. Applied at the row level so it never overrides the selection highlight.
     */
    public static synchronized Color zebra() {
        StyleEngine e = engine();
        if (e == null) {
            return FALLBACK_ZEBRA;
        }
        try {
            return e.resolve(new Token("row-alt")).background().orElse(FALLBACK_ZEBRA);
        } catch (RuntimeException ex) {
            logTokenFallbackOnce(ex);
            return FALLBACK_ZEBRA;
        }
    }

    /**
     * Base background color for the main content area. Theme-aware (dark on dark mode, white on light mode).
     */
    public static Color baseBg() {
        StyleEngine e = engine();
        if (e == null) {
            return FALLBACK_BASE_BG;
        }
        try {
            return e.resolve(new Token("base-bg")).background().orElse(FALLBACK_BASE_BG);
        } catch (RuntimeException ex) {
            logTokenFallbackOnce(ex);
            return FALLBACK_BASE_BG;
        }
    }

    /**
     * Base foreground color for normal text. Theme-aware (light gray on dark mode, dark gray on light mode).
     */
    public static Color baseFg() {
        StyleEngine e = engine();
        if (e == null) {
            return FALLBACK_BASE_FG;
        }
        try {
            return e.resolve(new Token("base-fg")).foreground().orElse(FALLBACK_BASE_FG);
        } catch (RuntimeException ex) {
            logTokenFallbackOnce(ex);
            return FALLBACK_BASE_FG;
        }
    }

    /** White-on-orange: active tab highlight. */
    public static Style accentBg() {
        return style("accent-bg", FALLBACK_ACCENT_BG);
    }

    /** Black-on-orange chip: key hints in footers and prompts. */
    public static Style hintKey() {
        return style("hint-key", FALLBACK_HINT_KEY);
    }

    /** Dim border for unfocused panels. */
    public static Style border() {
        return style("border", FALLBACK_BORDER);
    }

    /** Orange border for the focused panel. */
    public static Style borderFocused() {
        return style("border-focused", FALLBACK_BORDER_FOCUSED);
    }

    /** Panel and border titles. */
    public static Style title() {
        return style("title", FALLBACK_TITLE);
    }

    public static Style success() {
        return style("success", FALLBACK_SUCCESS);
    }

    public static Style warning() {
        return style("warning", FALLBACK_WARNING);
    }

    public static Style error() {
        return style("error", FALLBACK_ERROR);
    }

    public static Style muted() {
        return style("muted", FALLBACK_MUTED);
    }

    /** Row/selection highlight (matches the existing list highlight). */
    public static Style selectionBg() {
        return style("selection", FALLBACK_SELECTION);
    }

    /** Informational accent (header integration count, integration name text, popup prompts). */
    public static Style info() {
        return style("info", FALLBACK_INFO);
    }

    /** Secondary accent (header infra / selected). */
    public static Style notice() {
        return style("notice", FALLBACK_NOTICE);
    }

    /** MCP indicator: connected with recent activity. */
    public static Style mcpActive() {
        return style("mcp-active", FALLBACK_MCP_ACTIVE);
    }

    /** MCP indicator: connected but idle. */
    public static Style mcpIdle() {
        return style("mcp-idle", FALLBACK_MCP_IDLE);
    }

    /** MCP indicator: not connected. */
    public static Style mcpDown() {
        return style("mcp-down", FALLBACK_MCP_DOWN);
    }

    /** Secondary accent for field labels, section headers, key hints, and popup titles. */
    public static Style label() {
        return style("label", FALLBACK_LABEL);
    }

    /** Changed-value indicator in trace diffs. */
    public static Style change() {
        return style("change", FALLBACK_CHANGE);
    }

    /** Search/find match highlight (foreground + background). */
    public static Style searchMatch() {
        return style("search-match", FALLBACK_SEARCH_MATCH);
    }

    /** Diagram box-drawing border color. */
    public static synchronized Color diagramBorder() {
        return color("diagram-border", FALLBACK_DIAGRAM_BORDER);
    }

    /** Route ID text color in diagrams. */
    public static synchronized Color diagramId() {
        return color("diagram-id", FALLBACK_DIAGRAM_ID);
    }

    /** Diagram color for "from" EIP nodes. */
    public static synchronized Color diagramFrom() {
        return color("diagram-from", FALLBACK_DIAGRAM_FROM);
    }

    /** Diagram color for "to", "enrich", marshal, and transform EIP nodes. */
    public static synchronized Color diagramTo() {
        return color("diagram-to", FALLBACK_DIAGRAM_TO);
    }

    /** Diagram color for "choice"/"when"/"otherwise" EIP nodes. */
    public static synchronized Color diagramChoice() {
        return color("diagram-choice", FALLBACK_DIAGRAM_CHOICE);
    }

    /** Diagram color for "bean"/"process"/"log"/"script" EIP nodes. */
    public static synchronized Color diagramAction() {
        return color("diagram-action", FALLBACK_DIAGRAM_ACTION);
    }

    /** Diagram color for routing EIPs (split, aggregate, multicast, etc.). */
    public static synchronized Color diagramEip() {
        return color("diagram-eip", FALLBACK_DIAGRAM_EIP);
    }

    /** Diagram fallback color for unknown EIP types. */
    public static synchronized Color diagramDefault() {
        return color("diagram-default", FALLBACK_DIAGRAM_DEFAULT);
    }

    /** The active theme mode: {@code "dark"} or {@code "light"}. */
    public static String mode() {
        engine();
        return mode.id();
    }

    /** True if the active mode is dark. */
    public static boolean isDark() {
        engine();
        return mode == ThemeMode.DARK;
    }

    /** Flip the active theme mode, persist it (outside test mode), and activate it, returning the new mode. */
    public static synchronized String toggle() {
        ThemeMode next = mode.toggle();
        if (!testMode) {
            persist(next);
        }
        activate(next, false);
        return next.id();
    }

    /**
     * Activate a specific mode without persisting. Unknown values fall back to dark.
     * <p/>
     * Test-only entry point: unlike {@link #applyStartupMode(String)}, this does not mark the persisted preference as
     * loaded, so outside test mode a later {@link #engine()} re-initialization can still overwrite the mode with
     * whatever is on disk.
     */
    public static synchronized void setMode(String newMode) {
        activate(ThemeMode.parseOrDefault(newMode), false);
    }

    /**
     * Applies a CLI-selected theme for this process only. Marks the persisted preference as already loaded so the value
     * from {@code --theme} wins over {@code camel.tui.theme} in user config.
     *
     * @throws IllegalArgumentException if {@code newMode} is not a known theme mode; callers should normally validate
     *                                  with {@link #isValidMode(String)} first to report a friendlier CLI error.
     */
    public static synchronized void applyStartupMode(String newMode) {
        ThemeMode parsed = ThemeMode.parse(newMode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Camel TUI theme mode: " + newMode));
        activate(parsed, true);
    }

    /** Whether {@code value} parses to a known theme mode. */
    public static boolean isValidMode(String value) {
        return ThemeMode.parse(value).isPresent();
    }

    /** Test hook: enable in-memory-only mode so tests never touch user config files. */
    public static synchronized void resetForTesting() {
        resetForTesting(true);
    }

    /**
     * Test hook like {@link #resetForTesting()}, but lets the caller keep {@code testMode} disabled so a test can
     * exercise the real persistence path (disk read/write) against an isolated home directory.
     */
    public static synchronized void resetForTesting(boolean testModeValue) {
        testMode = testModeValue;
        stylesheetFallbackLogged = false;
        tokenFallbackLogged = false;
        persistedReadFallbackLogged = false;
        persistedWriteFallbackLogged = false;
        persistedModeLoaded = false;
        CACHE.clear();
        engine = null;
        initialized = false;
        mode = ThemeMode.DARK;
    }

    private static synchronized void activate(ThemeMode newMode, boolean cliOverride) {
        mode = newMode;
        if (cliOverride) {
            persistedModeLoaded = true;
        }
        CACHE.clear();
        engine = null;
        initialized = false;
        engine();
    }

    private static synchronized Color color(String id, Color fallback) {
        StyleEngine e = engine();
        if (e == null) {
            return fallback;
        }
        try {
            return e.resolve(new Token(id)).foreground().orElse(fallback);
        } catch (RuntimeException ex) {
            logTokenFallbackOnce(ex);
            return fallback;
        }
    }

    private static synchronized Style style(String id, Style fallback) {
        StyleEngine e = engine();
        if (e == null) {
            return fallback;
        }
        Style cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        try {
            Style resolved = e.resolve(new Token(id)).toStyle();
            CACHE.put(id, resolved);
            return resolved;
        } catch (RuntimeException ex) {
            // Not cached: a transient resolution failure must not permanently lock this token to the fallback.
            logTokenFallbackOnce(ex);
            return fallback;
        }
    }

    private static synchronized StyleEngine engine() {
        if (initialized) {
            return engine;
        }
        initialized = true;
        if (!testMode && !persistedModeLoaded) {
            ThemeMode[] loaded = { ThemeMode.DARK };
            if (tryLoadPersistedMode(loaded)) {
                // Only mark as loaded on success (including "nothing persisted yet"). A read failure leaves this
                // false so the next reinitialization gets another chance instead of pinning the session forever.
                mode = loaded[0];
                persistedModeLoaded = true;
            }
        }
        try {
            StyleEngine e = StyleEngine.create();
            String stylesheet = mode.id();
            // Read CSS content via Theme's own classloader instead of delegating
            // to StyleEngine.loadStylesheet() which uses StyleEngine.class.getClassLoader().
            // Under parallel test execution the two classloaders can diverge, causing
            // intermittent "resource not found" failures in CI.
            String cssContent = loadCssResource(mode.stylesheetResource());
            e.addStylesheet(stylesheet, cssContent);
            e.setActiveStylesheet(stylesheet);
            engine = e;
        } catch (Exception ex) {
            engine = null;
            logStylesheetFallbackOnce(ex);
        }
        return engine;
    }

    /**
     * Reads a CSS resource from the classpath using this class's own classloader, which in the normal Camel
     * class-loading hierarchy has access to project resources. Falls back to the thread-context classloader when the
     * primary lookup returns null (e.g., in OSGi or custom classloader setups) or when the class is bootstrap-loaded.
     */
    private static String loadCssResource(String path) throws IOException {
        InputStream is = null;
        ClassLoader cl = Theme.class.getClassLoader();
        if (cl != null) {
            is = cl.getResourceAsStream(path);
        }
        if (is == null) {
            // Fallback to the thread context classloader in case the class's own classloader
            // doesn't have visibility or is null (bootstrap-loaded classes).
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) {
                is = tccl.getResourceAsStream(path);
            }
        }
        if (is == null) {
            throw new IOException("Theme CSS resource not found on classpath: " + path);
        }
        try (InputStream in = is) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean tryLoadPersistedMode(ThemeMode[] result) {
        try {
            CommandLineHelper.loadProperties(props -> {
                ThemeMode.parse(props.getProperty(PROP_KEY)).ifPresent(parsed -> result[0] = parsed);
            });
            return true;
        } catch (RuntimeException ex) {
            logPersistedReadFallbackOnce(ex);
            return false;
        }
    }

    private static void persist(ThemeMode newMode) {
        try {
            CommandLineHelper.createPropertyFile(false);
            CommandLineHelper.loadProperties(props -> {
                props.setProperty(PROP_KEY, newMode.id());
                CommandLineHelper.storeProperties(props,
                        new Printer.QuietPrinter(new Printer.SystemOutPrinter()), false);
            });
        } catch (Exception ex) {
            logPersistedWriteFallbackOnce(ex);
        }
    }

    private static void logStylesheetFallbackOnce(Throwable t) {
        if (!stylesheetFallbackLogged) {
            stylesheetFallbackLogged = true;
            LOG.warn("Camel TUI theme stylesheet unavailable; using built-in palette", t);
        }
    }

    private static void logTokenFallbackOnce(Throwable t) {
        if (!tokenFallbackLogged) {
            tokenFallbackLogged = true;
            LOG.warn("Camel TUI theme token resolution failed; using built-in fallback color", t);
        }
    }

    private static void logPersistedReadFallbackOnce(Throwable t) {
        if (!persistedReadFallbackLogged) {
            persistedReadFallbackLogged = true;
            LOG.warn("Camel TUI theme preference could not be read; falling back to the default theme for this session",
                    t);
        }
    }

    private static void logPersistedWriteFallbackOnce(Throwable t) {
        if (!persistedWriteFallbackLogged) {
            persistedWriteFallbackLogged = true;
            LOG.warn("Camel TUI theme preference could not be saved; the selected theme may not persist across restarts",
                    t);
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
