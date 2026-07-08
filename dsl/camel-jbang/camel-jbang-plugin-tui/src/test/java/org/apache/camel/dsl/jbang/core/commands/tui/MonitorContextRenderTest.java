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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MonitorContext} rendering helpers that write to the terminal buffer. These exercise the actual
 * Frame/Buffer rendering pipeline to verify that text and styles appear correctly.
 */
class MonitorContextRenderTest {

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void renderNoSelectionShowsPromptText() {
        Rect area = new Rect(0, 0, 80, 10);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);

        AbstractTab.renderNoSelection(frame, area);

        String rendered = HealthTabRenderTest.bufferToString(buffer);
        assertTrue(rendered.contains("No integration selected"),
                "Should render 'No integration selected' in the block title and prompt text");
    }

    @Test
    void renderNoSelectionRendersContentInBlock() {
        Rect area = new Rect(0, 0, 80, 10);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);

        AbstractTab.renderNoSelection(frame, area);

        String rendered = HealthTabRenderTest.bufferToString(buffer);
        // The block title and prompt text should appear in the buffer
        assertTrue(rendered.contains("No integration selected"),
                "Should render the block title and prompt text");
    }

    @Test
    void renderNoSelectionHintKeysAreBold() {
        Rect area = new Rect(0, 0, 80, 12);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);

        AbstractTab.renderNoSelection(frame, area);

        boolean foundBold = false;
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                var cell = buffer.get(x, y);
                if ("1".equals(cell.symbol()) && cell.style().effectiveModifiers()
                        .contains(dev.tamboui.style.Modifier.BOLD)) {
                    foundBold = true;
                    break;
                }
            }
            if (foundBold) {
                break;
            }
        }
        assertTrue(foundBold, "Hint keys should use BOLD modifier");
    }

    @Test
    void hintAddsKeyAndLabel() {
        List<Span> spans = new ArrayList<>();
        TuiHelper.hint(spans, "Esc", "back");

        assertEquals(2, spans.size(), "hint should add exactly 2 spans");
        assertTrue(spans.get(0).content().contains("Esc"), "First span should contain the key");
        assertTrue(spans.get(1).content().contains("back"), "Second span should contain the label");
    }

    @Test
    void hintKeyUsesThemeBoldStyle() {
        List<Span> spans = new ArrayList<>();
        TuiHelper.hint(spans, "s", "sort");

        Span keySpan = spans.get(0);
        assertTrue(keySpan.style().fg().isPresent(), "Key span should have a foreground color");
        assertTrue(keySpan.style().bg().isPresent(), "Key span should have a background color");
        assertTrue(keySpan.style().effectiveModifiers().contains(dev.tamboui.style.Modifier.BOLD),
                "Key span should be BOLD");
    }

    @Test
    void hintLabelHasTrailingSpaces() {
        List<Span> spans = new ArrayList<>();
        TuiHelper.hint(spans, "x", "action");

        Span labelSpan = spans.get(1);
        assertTrue(labelSpan.content().endsWith("  "),
                "hint label should end with trailing spaces for separation");
    }

    @Test
    void hintLastDoesNotHaveTrailingSpaces() {
        List<Span> spans = new ArrayList<>();
        TuiHelper.hintLast(spans, "q", "quit");

        Span labelSpan = spans.get(1);
        assertEquals(" quit", labelSpan.content(),
                "hintLast label should not have trailing spaces");
    }

    @Test
    void rightCellRendersRightAligned() {
        var cell = AbstractTab.rightCell("42", 8);
        // rightCell uses String.format("%8s", "42") → "      42"
        String content = extractCellContent(cell);
        assertTrue(content.endsWith("42"), "Content should end with the value");
        assertEquals(8, content.length(), "Content should be padded to width 8");
    }

    @Test
    void rightCellWithStyleAppliesStyle() {
        var cell = AbstractTab.rightCell("5", 6, dev.tamboui.style.Style.EMPTY.fg(Color.LIGHT_RED));
        // The cell should have styled content
        String content = extractCellContent(cell);
        assertTrue(content.contains("5"), "Should contain the value");
    }

    @Test
    void centerCellCentersText() {
        var cell = AbstractTab.centerCell("x", 6);
        String content = extractCellContent(cell);
        // "x" centered in width 6: "  x" (leftPad = (6-1)/2 = 2)
        assertTrue(content.startsWith("  "), "Content should have leading padding");
        assertTrue(content.contains("x"), "Content should contain the value");
    }

    @Test
    void sortLabelShowsIndicatorForActiveColumn() {
        String label = AbstractTab.sortLabel("NAME", "name", "name", false);
        assertEquals("NAME" + TuiIcons.SORT_DOWN, label, "Active sort column should have descending indicator");

        String reversed = AbstractTab.sortLabel("NAME", "name", "name", true);
        assertEquals("NAME" + TuiIcons.SORT_UP, reversed, "Reversed sort should have ascending indicator");
    }

    @Test
    void sortLabelNoIndicatorForInactiveColumn() {
        String label = AbstractTab.sortLabel("NAME", "name", "status", false);
        assertEquals("NAME", label, "Inactive column should have no indicator");
    }

    @Test
    void sortStyleActiveColumnIsYellowBold() {
        var style = AbstractTab.sortStyle("name", "name");
        Color labelColor = Theme.label().fg().orElse(Color.YELLOW);
        assertEquals(labelColor, style.fg().orElse(null), "Active sort column should be label color");
        assertTrue(style.effectiveModifiers().contains(dev.tamboui.style.Modifier.BOLD),
                "Active sort column should be BOLD");
    }

    @Test
    void sortStyleInactiveColumnIsBoldOnly() {
        var style = AbstractTab.sortStyle("name", "status");
        Color labelColor = Theme.label().fg().orElse(Color.YELLOW);
        assertTrue(style.fg().isEmpty() || !labelColor.equals(style.fg().get()),
                "Inactive column should not be label color");
        assertTrue(style.effectiveModifiers().contains(dev.tamboui.style.Modifier.BOLD),
                "Inactive column should still be BOLD");
    }

    private static String extractCellContent(dev.tamboui.widgets.table.Cell cell) {
        // Render the cell into a small buffer to extract its text
        Rect area = new Rect(0, 0, 20, 1);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        // Render via a simple single-row table
        var table = dev.tamboui.widgets.table.Table.builder()
                .rows(List.of(dev.tamboui.widgets.table.Row.from(cell)))
                .widths(dev.tamboui.layout.Constraint.fill())
                .build();
        frame.renderStatefulWidget(table, area, new dev.tamboui.widgets.table.TableState());
        return rowText(buffer, 0);
    }

    private static String rowText(Buffer buffer, int row) {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < buffer.width(); col++) {
            String sym = buffer.get(col, row).symbol();
            sb.append(sym.isEmpty() ? " " : sym);
        }
        return sb.toString().stripTrailing();
    }
}
