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

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelMonitorTest {

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

    @Test
    void dropFKeyHintsRemovesF6BeforeF1() {
        // tab hint + F1/F2/F3/F6, fKeyTotal = 8 spans
        List<Span> spans = footer("Enter", "open");
        hint(spans, "F1", "help");
        hint(spans, "F2", "actions");
        hint(spans, "F3", "switch");
        hint(spans, "F6", "shell");
        int width = width(spans);

        // Available width is one pair short, so exactly one pair (F6) must be dropped.
        int available = width - pairWidth(spans, "F6");
        int newWidth = CamelMonitor.dropFKeyHints(spans, 8, width, available);

        assertFalse(containsKey(spans, "F6"), "F6 (shell) must be dropped first");
        assertTrue(containsKey(spans, "F1"), "F1 (help) must be preserved");
        assertTrue(containsKey(spans, "F2"), "F2 must remain when only one pair needs dropping");
        assertTrue(containsKey(spans, "F3"), "F3 must remain when only one pair needs dropping");
        assertEquals(width(spans), newWidth, "returned width must match the remaining spans");
    }

    @Test
    void dropFKeyHintsNeverDropsF1Help() {
        List<Span> spans = footer("Enter", "open");
        hint(spans, "F1", "help");
        hint(spans, "F2", "actions");
        hint(spans, "F3", "switch");
        hint(spans, "F6", "shell");

        // A tiny terminal forces every droppable pair to go.
        int newWidth = CamelMonitor.dropFKeyHints(spans, 8, width(spans), 1);

        assertTrue(containsKey(spans, "F1"), "F1 (help) must never be dropped");
        assertFalse(containsKey(spans, "F2"), "F2 must be dropped under heavy overflow");
        assertFalse(containsKey(spans, "F3"), "F3 must be dropped under heavy overflow");
        assertFalse(containsKey(spans, "F6"), "F6 must be dropped under heavy overflow");
        assertTrue(containsKey(spans, "Enter"), "the leading tab hint must be preserved");
        assertEquals(width(spans), newWidth, "returned width must match the remaining spans");
    }

    @Test
    void dropFKeyHintsKeepsFirstSecondaryHintWhenNoHelp() {
        // No F1 (tab without help text): F2/F3/F6 only, fKeyTotal = 6 spans
        List<Span> spans = footer("Enter", "open");
        hint(spans, "F2", "actions");
        hint(spans, "F3", "switch");
        hint(spans, "F6", "shell");

        int newWidth = CamelMonitor.dropFKeyHints(spans, 6, width(spans), 1);

        assertTrue(containsKey(spans, "F2"), "first secondary hint is preserved as the loop stops at 2");
        assertFalse(containsKey(spans, "F3"), "F3 must be dropped");
        assertFalse(containsKey(spans, "F6"), "F6 must be dropped");
        assertEquals(width(spans), newWidth, "returned width must match the remaining spans");
    }

    @Test
    void dropFKeyHintsLeavesFooterUntouchedWhenItFits() {
        List<Span> spans = footer("Enter", "open");
        hint(spans, "F1", "help");
        hint(spans, "F2", "actions");
        int width = width(spans);

        int newWidth = CamelMonitor.dropFKeyHints(spans, 4, width, 1000);

        assertEquals(width, newWidth, "no spans dropped when the footer already fits");
        assertTrue(containsKey(spans, "F1"));
        assertTrue(containsKey(spans, "F2"));
    }

    // Tab-bar click support maps a click x-coordinate back to the tab under it. The geometry mirrors how the tab bar
    // is drawn: labels laid out left to right, each separated by a divider. tabStartPositions computes where each label
    // begins; tabIndexAt resolves a click to the owning label (or -1 for a divider gap / outside).

    @Test
    void tabStartPositionsAccumulatesWidthsAndDividers() {
        // Three labels of width 12, 7, 10 separated by a 3-wide " | " divider, starting at column 0.
        int[] starts = CamelMonitor.tabStartPositions(0, new int[] { 12, 7, 10 }, 3);
        // 0; 0+12+3; 15+7+3
        assertArrayEquals(new int[] { 0, 15, 25 }, starts, "each label starts after the previous label plus one divider");
    }

    @Test
    void tabStartPositionsHonorsOriginOffset() {
        int[] starts = CamelMonitor.tabStartPositions(5, new int[] { 12, 7, 10 }, 3);
        assertArrayEquals(new int[] { 5, 20, 30 }, starts, "the first label starts at originX and the rest shift with it");
    }

    @Test
    void tabIndexAtResolvesClicksToTheOwningLabel() {
        int[] starts = { 0, 15, 25 };
        int[] widths = { 12, 7, 10 };
        assertEquals(0, CamelMonitor.tabIndexAt(starts, widths, 0), "left edge of the first label");
        assertEquals(0, CamelMonitor.tabIndexAt(starts, widths, 11), "right edge (inclusive) of the first label");
        assertEquals(1, CamelMonitor.tabIndexAt(starts, widths, 15), "left edge of the second label");
        assertEquals(2, CamelMonitor.tabIndexAt(starts, widths, 34), "right edge (inclusive) of the third label");
    }

    @Test
    void tabIndexAtReturnsMinusOneForGapsAndOutOfRange() {
        int[] starts = { 0, 15, 25 };
        int[] widths = { 12, 7, 10 };
        assertEquals(-1, CamelMonitor.tabIndexAt(starts, widths, 12),
                "column 12 falls in the divider gap after the first label");
        assertEquals(-1, CamelMonitor.tabIndexAt(starts, widths, 22),
                "column 22 falls in the divider gap after the second label");
        assertEquals(-1, CamelMonitor.tabIndexAt(starts, widths, 35), "column 35 is past the last label");
        assertEquals(-1, CamelMonitor.tabIndexAt(starts, widths, -1), "negative column is outside every label");
        assertEquals(-1, CamelMonitor.tabIndexAt(null, widths, 5), "no captured geometry yet");
    }

    @Test
    void tabStartPositionsAndTabIndexAtRoundTrip() {
        // Build geometry the way captureTabBarGeometry does, then confirm a click in the middle of a label resolves back
        // to that label while a click on the divider between two labels resolves to nothing.
        int[] widths = { 12, 7, 10 };
        int[] starts = CamelMonitor.tabStartPositions(0, widths, 3);
        int midOfSecond = starts[1] + widths[1] / 2;
        assertEquals(1, CamelMonitor.tabIndexAt(starts, widths, midOfSecond), "a click inside label 1 maps to index 1");
        int dividerBetweenFirstAndSecond = starts[0] + widths[0]; // first cell after label 0, before label 1
        assertEquals(-1, CamelMonitor.tabIndexAt(starts, widths, dividerBetweenFirstAndSecond),
                "a click on the divider maps to no tab");
    }

    private static List<Span> footer(String key, String label) {
        List<Span> spans = new ArrayList<>();
        hint(spans, key, label);
        return spans;
    }

    private static int width(List<Span> spans) {
        return spans.stream().mapToInt(Span::width).sum();
    }

    private static boolean containsKey(List<Span> spans, String key) {
        return spans.stream().anyMatch(s -> s.content().trim().equals(key));
    }

    private static int pairWidth(List<Span> spans, String key) {
        for (int i = 0; i + 1 < spans.size(); i++) {
            if (spans.get(i).content().trim().equals(key)) {
                return spans.get(i).width() + spans.get(i + 1).width();
            }
        }
        throw new IllegalArgumentException("no hint pair for key " + key);
    }
}
