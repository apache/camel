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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class CamelMonitorTest {

    @BeforeEach
    void resetTheme() {
        Theme.resetForTesting();
        Theme.setMode("dark");
    }

    // --theme is validated at the very top of doCall(), before any TUI/terminal setup, so an invalid value
    // must be rejected immediately without needing a real terminal.

    @Test
    void doCallRejectsInvalidThemeBeforeAnyTuiSetup() {
        CamelMonitor monitor = new CamelMonitor(new CamelJBangMain(), Thread.currentThread().getContextClassLoader());
        monitor.theme = "sepia";

        CommandLine.ParameterException ex = assertThrows(CommandLine.ParameterException.class, monitor::doCall,
                "an invalid --theme value must be rejected before any TUI/terminal setup runs");
        assertTrue(ex.getMessage().contains("sepia"), "the rejected value should be echoed back in the error message");
    }

    // '?' is an alternative to F1 for opening the help overlay. Unlike F1, it must be suppressed
    // while a text input is focused, otherwise typing '?' in a search or probe field would
    // pop up help instead of inserting the character.

    @Test
    void f1OpensHelpEvenWhileTextEditing() {
        assertTrue(CamelMonitor.opensHelp(KeyEvent.ofKey(KeyCode.F1), true), "F1 must open help regardless of text editing");
        assertTrue(CamelMonitor.opensHelp(KeyEvent.ofKey(KeyCode.F1), false), "F1 must open help");
    }

    @Test
    void questionMarkOpensHelpWhenNotTextEditing() {
        assertTrue(CamelMonitor.opensHelp(KeyEvent.ofChar('?'), false), "'?' must open help when no input is focused");
    }

    @Test
    void questionMarkIsSuppressedWhileTextEditing() {
        assertFalse(CamelMonitor.opensHelp(KeyEvent.ofChar('?'), true),
                "'?' must not open help while a text input is focused");
    }

    @Test
    void unrelatedKeyDoesNotOpenHelp() {
        assertFalse(CamelMonitor.opensHelp(KeyEvent.ofChar('x'), false), "an unrelated key must not open help");
    }

    // dropFKeyHints trims an overflowing footer by removing secondary F-key hints from the tail
    // (F6 first, then F3, F2). The first F-key pair must survive: F1 (help) when present, so the
    // user can always reach help, even on a narrow terminal.

    // Theme.applyBaseColors patches unset terminal cells with the active theme canvas colors while
    // preserving widget-specific foreground and background styles.

    @Test
    void applyThemeBaseColorsPatchesUnsetCells() {
        Rect area = new Rect(0, 0, 4, 2);
        Buffer buffer = Buffer.empty(area);

        Theme.applyBaseColors(buffer, area);

        Cell cell = buffer.get(0, 0);
        assertEquals(Theme.baseBg(), cell.style().bg().orElse(null), "unset cells receive the theme background");
        assertEquals(Theme.baseFg(), cell.style().fg().orElse(null), "unset cells receive the theme foreground");
    }

    @Test
    void applyThemeBaseColorsPreservesExplicitSelectionStyle() {
        Rect area = new Rect(0, 0, 4, 2);
        Buffer buffer = Buffer.empty(area);
        Style selection = Theme.selectionBg();
        buffer.setString(1, 0, "X", selection);

        Theme.applyBaseColors(buffer, area);

        Cell cell = buffer.get(1, 0);
        assertEquals(selection.bg().orElse(null), cell.style().bg().orElse(null), "selection background is preserved");
        assertEquals(selection.fg().orElse(null), cell.style().fg().orElse(null), "selection foreground is preserved");
    }

    @Test
    void applyThemeBaseColorsPatchesOnlyMissingBackground() {
        Rect area = new Rect(0, 0, 4, 2);
        Buffer buffer = Buffer.empty(area);
        Color explicitFg = Color.rgb(0xFF, 0x00, 0x00);
        buffer.setString(2, 0, "Y", Style.EMPTY.fg(explicitFg));

        Theme.applyBaseColors(buffer, area);

        Cell cell = buffer.get(2, 0);
        assertEquals(explicitFg, cell.style().fg().orElse(null), "explicit foreground is preserved");
        assertEquals(Theme.baseBg(), cell.style().bg().orElse(null), "missing background is patched");
    }

    @Test
    void applyThemeBaseColorsPatchesOnlyMissingForeground() {
        Rect area = new Rect(0, 0, 4, 2);
        Buffer buffer = Buffer.empty(area);
        Color explicitBg = Color.rgb(0x00, 0x00, 0xFF);
        buffer.setString(3, 0, "Z", Style.EMPTY.bg(explicitBg));

        Theme.applyBaseColors(buffer, area);

        Cell cell = buffer.get(3, 0);
        assertEquals(explicitBg, cell.style().bg().orElse(null), "explicit background is preserved");
        assertEquals(Theme.baseFg(), cell.style().fg().orElse(null), "missing foreground is patched");
    }

    @Test
    void applyThemeBaseColorsUsesActiveThemePalette() {
        Rect area = new Rect(0, 0, 2, 1);
        Buffer buffer = Buffer.empty(area);
        Theme.setMode("dark");
        Color darkBg = Theme.baseBg();

        Theme.setMode("light");
        Theme.applyBaseColors(buffer, area);

        Cell cell = buffer.get(0, 0);
        assertEquals(Theme.baseBg(), cell.style().bg().orElse(null), "light palette background is applied");
        assertEquals(Theme.baseFg(), cell.style().fg().orElse(null), "light palette foreground is applied");
        assertFalse(darkBg.equals(cell.style().bg().orElse(null)), "patched background must follow the active theme");
    }

    @Test
    void aiPanelQuitCommandRequestsTuiQuit() {
        CamelMonitor monitor = new CamelMonitor(new CamelJBangMain(), getClass().getClassLoader());
        TestRunner runner = new TestRunner();
        monitor.setRunnerForTesting(runner::quit);

        AiPanel panel = monitor.aiPanelForTesting();
        panel.open();
        type(panel, "/quit");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(runner.quitRequested());
    }

    private static void type(AiPanel panel, String text) {
        for (char ch : text.toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
    }

    private static final class TestRunner {
        private boolean quitRequested;

        void quit() {
            quitRequested = true;
        }

        boolean quitRequested() {
            return quitRequested;
        }
    }
}
