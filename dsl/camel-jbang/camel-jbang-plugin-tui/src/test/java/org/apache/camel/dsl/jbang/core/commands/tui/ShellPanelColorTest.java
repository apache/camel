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

import dev.tamboui.style.AnsiColor;
import dev.tamboui.style.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShellPanelColorTest {

    // ScreenTerminal stores colors as the top nibble of each channel of its xterm palette. The 16 standard
    // ANSI colors must be recognized and mapped to terminal-themed colors, otherwise (for example) ANSI red
    // is reconstructed as a literal RGB(136,0,0) that is unreadable on dark backgrounds.

    @Test
    void detectsStandardAnsiColors() {
        assertEquals(AnsiColor.BLACK, ShellPanel.ansiColorFor(0x000));
        assertEquals(AnsiColor.RED, ShellPanel.ansiColorFor(0x800));
        assertEquals(AnsiColor.GREEN, ShellPanel.ansiColorFor(0x080));
        assertEquals(AnsiColor.YELLOW, ShellPanel.ansiColorFor(0x880));
        assertEquals(AnsiColor.BLUE, ShellPanel.ansiColorFor(0x008));
        assertEquals(AnsiColor.MAGENTA, ShellPanel.ansiColorFor(0x808));
        assertEquals(AnsiColor.CYAN, ShellPanel.ansiColorFor(0x088));
        assertEquals(AnsiColor.WHITE, ShellPanel.ansiColorFor(0xccc));
    }

    @Test
    void detectsBrightAnsiColors() {
        assertEquals(AnsiColor.BRIGHT_BLACK, ShellPanel.ansiColorFor(0x888));
        assertEquals(AnsiColor.BRIGHT_RED, ShellPanel.ansiColorFor(0xf00));
        assertEquals(AnsiColor.BRIGHT_GREEN, ShellPanel.ansiColorFor(0x0f0));
        assertEquals(AnsiColor.BRIGHT_YELLOW, ShellPanel.ansiColorFor(0xff0));
        assertEquals(AnsiColor.BRIGHT_BLUE, ShellPanel.ansiColorFor(0x00f));
        assertEquals(AnsiColor.BRIGHT_MAGENTA, ShellPanel.ansiColorFor(0xf0f));
        assertEquals(AnsiColor.BRIGHT_CYAN, ShellPanel.ansiColorFor(0x0ff));
        assertEquals(AnsiColor.BRIGHT_WHITE, ShellPanel.ansiColorFor(0xfff));
    }

    @Test
    void leavesTrueColorValuesUnmapped() {
        // The shell prompt's orange (0xF69123) reduces to 0xf92, which is not a palette color and must stay true-color.
        assertNull(ShellPanel.ansiColorFor(0xf92));
        assertNull(ShellPanel.ansiColorFor(0x123));
    }

    @Test
    void resolvesAnsiRedToThemedColorNotDimRgb() {
        // The core of the bug fix: ANSI red must become a terminal-themed color, not the dim RGB(136,0,0)
        // that the old code produced.
        assertEquals(Color.ansi(AnsiColor.RED), ShellPanel.resolveColor(0x800));
    }

    @Test
    void resolvesTrueColorToLiteralRgb() {
        // A non-palette value keeps its literal colour, with each 4-bit channel expanded back to 8 bits (* 17).
        assertEquals(Color.rgb(0xf * 17, 0x9 * 17, 0x2 * 17), ShellPanel.resolveColor(0xf92));
    }
}
