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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellPanelTest {

    // ---- convertRow tests ----

    @Test
    void convertRowPlainAscii() {
        // Build a buffer with plain ASCII chars and zeroed attributes
        long[] buffer = new long[3];
        buffer[0] = 'H';
        buffer[1] = 'i';
        buffer[2] = '!';

        Line line = ShellPanel.convertRow(buffer, 0, 3);
        assertEquals("Hi!", rawContent(line));
    }

    @Test
    void convertRowNullCodepointBecomesSpace() {
        // A zero codepoint (null cell) should be rendered as a space
        long[] buffer = new long[3];
        buffer[0] = 'A';
        buffer[1] = 0; // null codepoint
        buffer[2] = 'B';

        Line line = ShellPanel.convertRow(buffer, 0, 3);
        assertEquals("A B", rawContent(line));
    }

    @Test
    void convertRowBoldAttr() {
        // Bold is bit 3 of X nibble → set bit 27 (attr bit 3 in X at position 24+3=27)
        long attr = 0x8L << 24; // Bold
        long[] buffer = new long[] { 'B' | (attr << 32) };

        Line line = ShellPanel.convertRow(buffer, 0, 1);
        List<Span> spans = line.spans();
        assertEquals(1, spans.size());
        assertTrue(spans.get(0).style().effectiveModifiers().contains(Modifier.BOLD));
    }

    @Test
    void convertRowUnderlineAttr() {
        // Underline is bit 0 of X nibble → bit 24
        long attr = 0x1L << 24; // Underline
        long[] buffer = new long[] { 'U' | (attr << 32) };

        Line line = ShellPanel.convertRow(buffer, 0, 1);
        List<Span> spans = line.spans();
        assertEquals(1, spans.size());
        assertTrue(spans.get(0).style().effectiveModifiers().contains(Modifier.UNDERLINED));
    }

    @Test
    void convertRowFgColor() {
        // FG set flag: bit 0 of Y (bit 28), FG color in bits 12-23
        // FG = 0xF00 → red=0xF, green=0x0, blue=0x0
        // Y=0x1 (FG set), X=0x0, FFF=0xF00, BBB=0x000
        // attr = 0x10F00000L
        long attr = 0x10F00000L;
        long[] buffer = new long[] { 'R' | (attr << 32) };

        Line line = ShellPanel.convertRow(buffer, 0, 1);
        List<Span> spans = line.spans();
        assertEquals(1, spans.size());
        assertTrue(spans.get(0).style().fg().isPresent());
    }

    @Test
    void convertRowBgColor() {
        // BG set flag: bit 1 of Y (bit 29), BG color in bits 0-11
        // Y=0x2 (BG set), BBB=0x080 (green)
        long attr = 0x20000080L;
        long[] buffer = new long[] { 'G' | (attr << 32) };

        Line line = ShellPanel.convertRow(buffer, 0, 1);
        List<Span> spans = line.spans();
        assertEquals(1, spans.size());
        assertTrue(spans.get(0).style().bg().isPresent());
    }

    @Test
    void convertRowMergesContiguousSameAttr() {
        // Two cells with the same attribute should be merged into one span
        long attr = 0x8L << 24; // Bold
        long[] buffer = new long[] {
                'A' | (attr << 32),
                'B' | (attr << 32)
        };

        Line line = ShellPanel.convertRow(buffer, 0, 2);
        List<Span> spans = line.spans();
        assertEquals(1, spans.size());
        assertEquals("AB", spans.get(0).content());
    }

    @Test
    void convertRowSplitsDifferentAttrs() {
        // Two cells with different attributes should become two spans
        long boldAttr = 0x8L << 24;
        long ulAttr = 0x1L << 24;
        long[] buffer = new long[] {
                'A' | (boldAttr << 32),
                'B' | (ulAttr << 32)
        };

        Line line = ShellPanel.convertRow(buffer, 0, 2);
        List<Span> spans = line.spans();
        assertEquals(2, spans.size());
        assertEquals("A", spans.get(0).content());
        assertEquals("B", spans.get(1).content());
    }

    @Test
    void convertRowWithOffset() {
        // Test that offset is correctly used to read from the middle of the buffer
        long[] buffer = new long[] { 'X', 'Y', 'A', 'B', 'C' };

        Line line = ShellPanel.convertRow(buffer, 2, 3);
        assertEquals("ABC", rawContent(line));
    }

    // ---- encodeKeyEvent tests ----

    @Test
    void encodeEnter() {
        KeyEvent ke = KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE);
        byte[] result = ShellPanel.encodeKeyEvent(ke);
        assertArrayEquals(new byte[] { '\r' }, result);
    }

    @Test
    void encodeBackspace() {
        KeyEvent ke = KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE);
        byte[] result = ShellPanel.encodeKeyEvent(ke);
        assertArrayEquals(new byte[] { 0x7f }, result);
    }

    @Test
    void encodeTab() {
        KeyEvent ke = KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE);
        byte[] result = ShellPanel.encodeKeyEvent(ke);
        assertArrayEquals(new byte[] { '\t' }, result);
    }

    @Test
    void encodeArrowKeys() {
        assertArrayEquals("\033OA".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE)));
        assertArrayEquals("\033OB".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE)));
        assertArrayEquals("\033OC".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.RIGHT, KeyModifiers.NONE)));
        assertArrayEquals("\033OD".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE)));
    }

    @Test
    void encodeHomeEnd() {
        assertArrayEquals("\033OH".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.HOME, KeyModifiers.NONE)));
        assertArrayEquals("\033OF".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE)));
    }

    @Test
    void encodeFKeys() {
        assertArrayEquals("\033OP".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.F1, KeyModifiers.NONE)));
        assertArrayEquals("\033OQ".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.F2, KeyModifiers.NONE)));
        assertArrayEquals("\033[15~".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE)));
        assertArrayEquals("\033[24~".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.F12, KeyModifiers.NONE)));
    }

    @Test
    void encodeCtrlLetter() {
        // Ctrl+c should produce byte 3 (0x03)
        KeyEvent ke = KeyEvent.ofChar('c', KeyModifiers.of(true, false, false));
        byte[] result = ShellPanel.encodeKeyEvent(ke);
        assertArrayEquals(new byte[] { 3 }, result);
    }

    @Test
    void encodeCtrlUpperCaseLetter() {
        // Ctrl+A should also produce byte 1
        KeyEvent ke = KeyEvent.ofChar('A', KeyModifiers.of(true, false, false));
        byte[] result = ShellPanel.encodeKeyEvent(ke);
        assertArrayEquals(new byte[] { 1 }, result);
    }

    @Test
    void encodeRegularChar() {
        KeyEvent ke = KeyEvent.ofChar('x', KeyModifiers.NONE);
        byte[] result = ShellPanel.encodeKeyEvent(ke);
        assertArrayEquals("x".getBytes(StandardCharsets.UTF_8), result);
    }

    @Test
    void encodePageUpDown() {
        assertArrayEquals("\033[5~".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_UP, KeyModifiers.NONE)));
        assertArrayEquals("\033[6~".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_DOWN, KeyModifiers.NONE)));
    }

    @Test
    void encodeInsertDelete() {
        assertArrayEquals("\033[2~".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.INSERT, KeyModifiers.NONE)));
        assertArrayEquals("\033[3~".getBytes(StandardCharsets.UTF_8),
                ShellPanel.encodeKeyEvent(KeyEvent.ofKey(KeyCode.DELETE, KeyModifiers.NONE)));
    }

    @Test
    void encodeUnhandledKeyReturnsNull() {
        // F11 is not handled by encodeKeyEvent
        KeyEvent ke = KeyEvent.ofKey(KeyCode.F11, KeyModifiers.NONE);
        byte[] result = ShellPanel.encodeKeyEvent(ke);
        assertNull(result);
    }

    // ---- convertCellToStyle tests ----
    // convertCellToStyle takes a full 64-bit cell (attr in upper 32, codepoint in lower 32).
    // Helper to build a cell from a 32-bit attr value.
    private static long cellWithAttr(long attr) {
        return attr << 32;
    }

    @Test
    void convertCellToStyleNoFlags() {
        Style style = ShellPanel.convertCellToStyle(cellWithAttr(0));
        assertTrue(style.effectiveModifiers().isEmpty());
    }

    @Test
    void convertCellToStyleBold() {
        // Bold = bit 3 of X nibble (bits 24-27)
        Style style = ShellPanel.convertCellToStyle(cellWithAttr(0x08000000L));
        assertTrue(style.effectiveModifiers().contains(Modifier.BOLD));
    }

    @Test
    void convertCellToStyleUnderline() {
        Style style = ShellPanel.convertCellToStyle(cellWithAttr(0x01000000L));
        assertTrue(style.effectiveModifiers().contains(Modifier.UNDERLINED));
    }

    @Test
    void convertCellToStyleReversed() {
        Style style = ShellPanel.convertCellToStyle(cellWithAttr(0x02000000L));
        assertTrue(style.effectiveModifiers().contains(Modifier.REVERSED));
    }

    @Test
    void convertCellToStyleDim() {
        // Dim = bit 2 of Y nibble (bits 28-31) → 0x4 << 28
        Style style = ShellPanel.convertCellToStyle(cellWithAttr(0x40000000L));
        assertTrue(style.effectiveModifiers().contains(Modifier.DIM));
    }

    @Test
    void convertCellToStyleItalic() {
        // Italic = bit 3 of Y nibble → 0x8 << 28
        Style style = ShellPanel.convertCellToStyle(cellWithAttr(0x80000000L));
        assertTrue(style.effectiveModifiers().contains(Modifier.ITALIC));
    }

    @Test
    void convertCellToStyleCombinedFgBgBoldItalic() {
        // Y = 0xB (FG set + BG set + italic: bits 0+1+3), X = 0x8 (bold)
        // FFF = 0xF00 (red FG), BBB = 0x080 (green BG)
        Style style = ShellPanel.convertCellToStyle(cellWithAttr(0xB8F00080L));
        assertTrue(style.effectiveModifiers().contains(Modifier.BOLD));
        assertTrue(style.effectiveModifiers().contains(Modifier.ITALIC));
        assertTrue(style.fg().isPresent());
        assertTrue(style.bg().isPresent());
    }

    // ---- panelHeight / cycleHeight tests ----

    @Test
    void panelHeightDefaultIsUninitialized() {
        ShellPanel panel = new ShellPanel();
        assertEquals(-1, panel.panelHeight());
    }

    @Test
    void initHeightSetsTo50Percent() {
        ShellPanel panel = new ShellPanel();
        panel.initHeight(40);
        assertEquals(20, panel.panelHeight());
    }

    @Test
    void cycleHeightCyclesThroughPercents() {
        ShellPanel panel = new ShellPanel();
        int contentHeight = 40;
        panel.initHeight(contentHeight);
        assertEquals(20, panel.panelHeight()); // 50%

        panel.cycleHeight(contentHeight);
        runAnimationToCompletion(panel);
        assertEquals(30, panel.panelHeight()); // 75%

        panel.cycleHeight(contentHeight);
        runAnimationToCompletion(panel);
        assertEquals(40, panel.panelHeight()); // 100%

        panel.cycleHeight(contentHeight);
        runAnimationToCompletion(panel);
        assertEquals(10, panel.panelHeight()); // 25%

        panel.cycleHeight(contentHeight);
        runAnimationToCompletion(panel);
        assertEquals(20, panel.panelHeight()); // wraps to 50%
    }

    @Test
    void cycleHeightAnimates() {
        ShellPanel panel = new ShellPanel();
        panel.initHeight(60);
        assertEquals(30, panel.panelHeight()); // 50% of 60

        panel.cycleHeight(60); // target = 75% = 45
        assertTrue(panel.isAnimating());
        assertEquals(30, panel.panelHeight()); // not yet moved

        panel.tickAnimation();
        assertTrue(panel.panelHeight() > 30); // moved toward target
        assertTrue(panel.panelHeight() < 45); // not yet at target

        runAnimationToCompletion(panel);
        assertEquals(45, panel.panelHeight());
        assertFalse(panel.isAnimating());
    }

    @Test
    void setPanelHeightSetsDirectly() {
        ShellPanel panel = new ShellPanel();
        panel.setPanelHeight(15);
        assertEquals(15, panel.panelHeight());
        assertFalse(panel.isAnimating());
    }

    private void runAnimationToCompletion(ShellPanel panel) {
        for (int i = 0; i < 100 && panel.isAnimating(); i++) {
            panel.tickAnimation();
        }
    }

    // ---- renderFooter tests ----

    @Test
    void renderFooterShowsResizePercent() {
        ShellPanel panel = new ShellPanel();
        panel.initHeight(40);
        List<Span> spans = new ArrayList<>();
        panel.renderFooter(spans);

        String footer = spansToString(spans);
        assertTrue(footer.contains("resize (50%)"), "Footer should show 'resize (50%)'");
    }

    @Test
    void renderFooterUpdatesAfterCycleHeight() {
        ShellPanel panel = new ShellPanel();
        panel.initHeight(40);
        panel.cycleHeight(40); // cycles to 75%
        runAnimationToCompletion(panel);

        List<Span> spans = new ArrayList<>();
        panel.renderFooter(spans);

        String footer = spansToString(spans);
        assertTrue(footer.contains("resize (75%)"), "Footer should show 'resize (75%)' after cycling once");
    }

    @Test
    void renderFooterContainsExpectedHints() {
        ShellPanel panel = new ShellPanel();
        List<Span> spans = new ArrayList<>();
        panel.renderFooter(spans);

        String footer = spansToString(spans);
        assertTrue(footer.contains("F6"), "Footer should contain F6 hint");
        assertTrue(footer.contains("close"), "Footer should contain 'close' label for F6");
        assertTrue(footer.contains("Shift+F6"), "Footer should contain Shift+F6 hint");
        assertTrue(footer.contains("resize"), "Footer should contain 'resize' action label");
        assertTrue(footer.contains("PgUp/Dn"), "Footer should contain PgUp/Dn hint");
        assertTrue(footer.contains("scroll"), "Footer should contain 'scroll' label");
    }

    private static String rawContent(Line line) {
        StringBuilder sb = new StringBuilder();
        for (Span span : line.spans()) {
            sb.append(span.content());
        }
        return sb.toString();
    }

    private static String spansToString(List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        for (Span span : spans) {
            sb.append(span.content());
        }
        return sb.toString();
    }
}
