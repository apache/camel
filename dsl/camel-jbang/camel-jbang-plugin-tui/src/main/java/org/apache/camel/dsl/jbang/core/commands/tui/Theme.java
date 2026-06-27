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

/**
 * Central semantic theme for the Camel TUI. Call sites reference intent (accent, border, success, ...) so the concrete
 * palette lives in one place.
 * <p/>
 * Palette policy: the brand accent is Camel orange (truecolor), reserved for accent and borders. Status colors are ANSI
 * named so they respect the user's terminal theme.
 */
final class Theme {

    /** Camel brand orange. */
    static final Color ACCENT = Color.rgb(0xF6, 0x91, 0x23);

    private Theme() {
    }

    static Color accent() {
        return ACCENT;
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
        return Style.EMPTY.fg(Color.WHITE).bg(ACCENT).bold();
    }

    /** Dim border for unfocused panels. */
    static Style border() {
        return Style.EMPTY.fg(Color.DARK_GRAY);
    }

    /** Orange border for the focused panel. */
    static Style borderFocused() {
        return Style.EMPTY.fg(ACCENT);
    }

    /** Panel and border titles. */
    static Style title() {
        return Style.EMPTY.fg(ACCENT).bold();
    }

    static Style success() {
        return Style.EMPTY.fg(Color.GREEN);
    }

    static Style warning() {
        return Style.EMPTY.fg(Color.YELLOW);
    }

    static Style error() {
        return Style.EMPTY.fg(Color.LIGHT_RED);
    }

    static Style muted() {
        return Style.EMPTY.dim();
    }

    /** Row/selection highlight (matches the existing list highlight). */
    static Style selectionBg() {
        return Style.EMPTY.fg(Color.WHITE).bold().onBlue();
    }
}
