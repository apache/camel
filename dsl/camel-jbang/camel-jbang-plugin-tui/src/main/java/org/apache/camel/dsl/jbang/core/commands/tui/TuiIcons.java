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

/**
 * Single source of truth for emoji and symbolic icons used across the Camel TUI.
 * <p/>
 * Tab/menu icons use plain 2-column-wide emoji without VS16 variation selectors (see CAMEL-23818). Doctor and legacy
 * status glyphs may still use mixed-width symbols until migrated.
 */
final class TuiIcons {

    // ---- Brand & runtime ----
    static final String CAMEL = "🐪";
    static final String SPRING_BOOT = "🍃";
    static final String QUARKUS = "🚀";
    static final String DEV_PROFILE = "🔨";
    static final String PROD_PROFILE = "📦";
    static final String INFRA = "🔧";

    // ---- Status & health ----
    static final String OK = "✅";
    static final String WARN = "⚠";
    static final String FAIL = "❌";
    static final String HEALTH_UP = "✔";
    static final String HEALTH_DOWN = "✖";
    static final String HEALTH_WARN = "⚠";
    static final String STOPPED = "✖";
    static final String CROSS = "✗";

    // ---- Files & folders ----
    static final String FOLDER = "📁";
    static final String FOLDER_OPEN = "📂";
    static final String CLIPBOARD = "📋";
    static final String DOCUMENT = "📄";
    static final String README = "📖";

    // ---- Actions menu ----
    static final String GO_TO = "🔍";
    static final String MESSAGE = "📩";
    static final String KEYSTROKES = "🔤";
    static final String STOP = "🛑";
    static final String RECORD = "🔴";
    static final String DOCTOR = "🩺";
    static final String RESET = "🔄";
    static final String CLEAN = "🧹";
    static final String LIGHT_THEME = "🌞";
    static final String DARK_THEME = "🌙";
    static final String THEMES = "🎨";
    static final String SCREENSHOT = "📸";
    static final String CAPTION = "💬";
    static final String MCP_BRAIN = "🧠";
    static final String MCP = "🤖";
    static final String MCP_LOG = "📋";

    // ---- Example browser legend ----
    static final String BUNDLED = "📦";
    static final String ONLINE = "🌐";
    static final String DOCKER = "🐳";
    static final String CITRUS = "🍋";

    // ---- Doctor checks ----
    static final String JAVA = "☕";
    static final String ENDPOINT = "🔌";
    static final String OTEL = "🔗";
    static final String MEMORY = "💾";

    // ---- Misc UI ----
    static final String SWITCH = "🔃";
    static final String KEY = "🔑";
    static final String TIP = "💡";
    static final String COMPUTER = "💻";
    static final String SETTINGS = "🧰";
    static final String HELP = "❔";
    static final String THINKING = "🤔";

    // ---- UI symbols (non-emoji glyphs used across the TUI) ----
    static final String SELECTED = "●";
    static final String IDLE = "○";
    static final String MORE_CHEVRON = "▾";
    static final String ARROW_LEFT = "◀";
    static final String ARROW_RIGHT = "▶";
    static final String POINTER = "►";
    static final String KEY_LEFT = "←";
    static final String KEY_RIGHT = "→";
    static final String ARROW_UP = "↑";
    static final String ARROW_DOWN = "↓";
    static final String ARROW_STABLE = "→";
    static final String SORT_UP = "▲";
    static final String SORT_DOWN = "▼";

    static final String ARROW_BOTH = "↔";
    static final String HINT_SCROLL = "↑↓";
    static final String HINT_NAV = "↑↓←→";
    static final String HINT_H = "←→";
    static final String HINT_CTRL_H = "C-←→";

    // ---- Primary tab bar ----
    static final String TAB_OVERVIEW = CAMEL;
    static final String TAB_LOG = DOCUMENT;
    static final String TAB_DIAGRAM = "📊";
    static final String TAB_ROUTES = "🔀";
    static final String TAB_ENDPOINTS = ENDPOINT;
    static final String TAB_HTTP = ONLINE;
    static final String TAB_HEALTH = DOCTOR;
    static final String TAB_INSPECT = GO_TO;
    static final String TAB_ERRORS = FAIL;
    static final String TAB_MORE = FOLDER_OPEN;

    // ---- More submenu tabs ----
    static final String TAB_BEANS = JAVA;
    static final String TAB_BROWSE = MESSAGE;
    static final String TAB_CIRCUIT_BREAKER = "⚡";
    static final String TAB_CLASSPATH = BUNDLED;
    static final String TAB_CONFIGURATION = DOCUMENT;
    static final String TAB_CONSUMERS = "📥";
    static final String TAB_CVE_AUDIT = "🔒";
    static final String TAB_DATASOURCE = "💿";
    static final String TAB_HEAP = MEMORY;
    static final String TAB_INFLIGHT = RESET;
    static final String TAB_MAVEN_DEPENDENCIES = "📜";
    static final String TAB_MEMORY = MEMORY;
    static final String TAB_MEMORY_LEAK = "💧";
    static final String TAB_METRICS = "📈";
    static final String TAB_SQL_QUERY = KEY;
    static final String TAB_SQL_TRACE = "🔎";
    static final String TAB_SPANS = OTEL;
    static final String TAB_PROCESS = CLIPBOARD;
    static final String TAB_STARTUP = QUARKUS;
    static final String TAB_THREADS = "🧵";

    /** Icons for {@link TabRegistry#TAB_OVERVIEW}..{@link TabRegistry#TAB_MORE} (in order). */
    static final List<String> PRIMARY_TAB_ICONS = List.of(
            TAB_OVERVIEW, TAB_LOG, TAB_DIAGRAM, TAB_ROUTES, TAB_ENDPOINTS,
            TAB_HTTP, TAB_HEALTH, TAB_INSPECT, TAB_ERRORS, TAB_MORE);

    /** Marker placed immediately before a label's keyboard-shortcut letter (Windows-style mnemonic). */
    static final char MNEMONIC_MARKER = '&';

    private TuiIcons() {
    }

    static String labeled(String icon, String text) {
        return icon + " " + text;
    }

    /** Prefix for popup list rows: {@code "  🐪 "}. */
    static String indent(String icon) {
        return "  " + icon + " ";
    }

    /** Prefix for actions menu rows: {@code "  🔍 Go to..."}. */
    static String menuItem(String icon, String label) {
        return "  " + icon + " " + label;
    }

    /**
     * Primary tab bar label of the form {@code "<icon>  <key> <name>"} with two spaces between the emoji and the key
     * digit so the two do not visually collide. Rendered without outer padding (compact) for every tab.
     */
    static dev.tamboui.text.Span[] primaryTabHeader(String icon, String key, String name) {
        return new dev.tamboui.text.Span[] {
                dev.tamboui.text.Span.raw(icon + "  "),
                dev.tamboui.text.Span.styled(key, Theme.mnemonic()),
                dev.tamboui.text.Span.raw(" " + name)
        };
    }

    /** Removes the {@value #MNEMONIC_MARKER} mnemonic marker from a label for display. */
    static String stripMnemonic(String label) {
        int i = label.indexOf(MNEMONIC_MARKER);
        return i < 0 ? label : label.substring(0, i) + label.substring(i + 1);
    }

    /**
     * Position of the shortcut letter within the {@link #stripMnemonic(String) stripped} label, which equals the index
     * of the {@value #MNEMONIC_MARKER} marker in {@code label}, or {@code -1} when the label has no marker.
     */
    static int mnemonicIndex(String label) {
        return label.indexOf(MNEMONIC_MARKER);
    }

    static String runtimeIcon(String runtime) {
        return switch (runtime) {
            case "Spring Boot" -> SPRING_BOOT;
            case "Quarkus" -> QUARKUS;
            default -> CAMEL;
        };
    }

    static String platformIcon(String platform) {
        return switch (platform) {
            case "Spring Boot" -> SPRING_BOOT + " ";
            case "Quarkus" -> QUARKUS + " ";
            case "JBang", "Camel" -> CAMEL + " ";
            default -> "";
        };
    }

    static String profilePrefix(String profile) {
        if ("dev".equals(profile)) {
            return DEV_PROFILE + " ";
        }
        if ("prod".equals(profile)) {
            return PROD_PROFILE + " ";
        }
        return "";
    }

    static String moreTabLabel() {
        return "More" + MORE_CHEVRON;
    }
}
