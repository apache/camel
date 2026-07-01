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

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.table.TableState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MonitorTab}'s default mouse handler, which maps the scroll wheel to table selection so that every
 * table-backed tab gains wheel scrolling by exposing its {@link TableState} and row count.
 */
class MonitorTabTest {

    private static final Rect AREA = new Rect(0, 0, 80, 24);

    /** A minimal tab whose only real behavior is exposing a table state and row count. */
    private static MonitorTab tableTab(TableState state, int rowCount) {
        return tableTab(state, rowCount, null);
    }

    /** As {@link #tableTab(TableState, int)} but also exposing a table area so click-to-select can be exercised. */
    private static MonitorTab tableTab(TableState state, int rowCount, Rect tableArea) {
        return new MonitorTab() {
            @Override
            public boolean handleKeyEvent(KeyEvent ke) {
                return false;
            }

            @Override
            public boolean handleEscape() {
                return false;
            }

            @Override
            public void navigateUp() {
            }

            @Override
            public void navigateDown() {
            }

            @Override
            public void render(Frame frame, Rect area) {
            }

            @Override
            public void renderFooter(List<Span> spans) {
            }

            @Override
            public TableState getTableState() {
                return state;
            }

            @Override
            public int getTableRowCount() {
                return rowCount;
            }

            @Override
            public Rect getTableArea() {
                return tableArea;
            }
        };
    }

    @Test
    void scrollDownAdvancesSelectionByOneRow() {
        TableState state = new TableState();
        state.select(1);
        MonitorTab tab = tableTab(state, 5);

        assertTrue(tab.handleMouseEvent(MouseEvent.scrollDown(10, 10), AREA), "a scroll inside the tab is consumed");
        assertEquals(2, state.selected(), "scroll down moves the selection to the next row");
    }

    @Test
    void scrollUpMovesSelectionBackByOneRow() {
        TableState state = new TableState();
        state.select(2);
        MonitorTab tab = tableTab(state, 5);

        assertTrue(tab.handleMouseEvent(MouseEvent.scrollUp(10, 10), AREA), "a scroll inside the tab is consumed");
        assertEquals(1, state.selected(), "scroll up moves the selection to the previous row");
    }

    @Test
    void scrollDownDoesNotMovePastTheLastRow() {
        TableState state = new TableState();
        state.select(4);
        MonitorTab tab = tableTab(state, 5);

        assertTrue(tab.handleMouseEvent(MouseEvent.scrollDown(10, 10), AREA));
        assertEquals(4, state.selected(), "scroll down clamps at the last row (index rowCount - 1)");
    }

    @Test
    void scrollOutsideTheTabAreaIsIgnored() {
        TableState state = new TableState();
        state.select(1);
        MonitorTab tab = tableTab(state, 5);

        assertFalse(tab.handleMouseEvent(MouseEvent.scrollDown(200, 200), AREA),
                "a scroll outside the tab area is not consumed");
        assertEquals(1, state.selected(), "selection is unchanged when the scroll is outside the tab");
    }

    @Test
    void clickInsideTheTableDoesNotChangeSelection() {
        TableState state = new TableState();
        state.select(1);
        MonitorTab tab = tableTab(state, 5);

        assertFalse(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 10, 10), AREA),
                "only the scroll wheel drives table selection; a plain click is not consumed here");
        assertEquals(1, state.selected(), "a click leaves the selection untouched");
    }

    @Test
    void tabWithoutATableStateIgnoresScroll() {
        // The default getTableState() returns null (non-table tabs); such a tab must not consume scroll events.
        MonitorTab tab = tableTab(null, 0);

        assertFalse(tab.handleMouseEvent(MouseEvent.scrollDown(10, 10), AREA),
                "a tab without a table state does not consume scroll events");
    }

    // ---- Click-to-select-row (tableRowAt + the click branch of the default handler) ----
    // Tables are drawn with a one-cell border on every side plus a header row, so the first data row is at
    // table.y() + 2. For scrolled tables, on-screen data row r maps to model row offset + r.

    @Test
    void tableRowAtResolvesClicksToDataRows() {
        Rect table = new Rect(0, 0, 80, 20);
        assertEquals(0, MonitorTab.tableRowAt(table, 0, 30, 5, 2), "the first row below border+header is data row 0");
        assertEquals(3, MonitorTab.tableRowAt(table, 0, 30, 5, 5), "the fourth data row is row 3");
    }

    @Test
    void tableRowAtAppliesScrollOffset() {
        Rect table = new Rect(0, 0, 80, 20);
        assertEquals(10, MonitorTab.tableRowAt(table, 10, 40, 5, 2), "offset shifts the first visible data row to row 10");
        assertEquals(12, MonitorTab.tableRowAt(table, 10, 40, 5, 4), "third visible data row maps to row 10 + 2");
    }

    @Test
    void tableRowAtRejectsHeaderBorderAndOutside() {
        Rect table = new Rect(0, 0, 80, 20);
        assertEquals(-1, MonitorTab.tableRowAt(table, 0, 30, 5, 0), "top border is not a data row");
        assertEquals(-1, MonitorTab.tableRowAt(table, 0, 30, 5, 1), "the header row is not a data row");
        assertEquals(-1, MonitorTab.tableRowAt(table, 0, 30, 0, 5), "left border is not a data row");
        assertEquals(-1, MonitorTab.tableRowAt(table, 0, 30, 79, 5), "right border is not a data row");
        assertEquals(-1, MonitorTab.tableRowAt(table, 0, 30, 5, 19), "bottom border is not a data row");
        assertEquals(-1, MonitorTab.tableRowAt(table, 0, 30, 200, 5), "a click outside the table is not a data row");
    }

    @Test
    void tableRowAtRejectsRowsPastTheLastAndNullArea() {
        Rect table = new Rect(0, 0, 80, 20);
        assertEquals(-1, MonitorTab.tableRowAt(table, 0, 3, 5, 6), "row 4 has no data when only 3 rows exist");
        assertEquals(-1, MonitorTab.tableRowAt(null, 0, 30, 5, 5), "no table area captured yet");
    }

    @Test
    void clickSelectsTheRowUnderThePointer() {
        TableState state = new TableState();
        Rect tableArea = new Rect(0, 0, 80, 20);
        MonitorTab tab = tableTab(state, 30, tableArea);

        // First data row is at y = table.y() + 2 (border + header), so the fourth data row (row 3) is at y = 5.
        assertTrue(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 5, 5), AREA), "a click on a data row is consumed");
        assertEquals(3, state.selected(), "clicking the fourth data row selects row 3");
    }

    @Test
    void clickOnHeaderOrBorderDoesNotSelect() {
        TableState state = new TableState();
        state.select(2);
        Rect tableArea = new Rect(0, 0, 80, 20);
        MonitorTab tab = tableTab(state, 30, tableArea);

        assertFalse(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 5, 1), AREA),
                "a click on the header row is not consumed");
        assertEquals(2, state.selected(), "selection is unchanged when the header is clicked");
    }

    @Test
    void clickWithoutATableAreaDoesNotSelect() {
        TableState state = new TableState();
        state.select(1);
        MonitorTab tab = tableTab(state, 30); // no table area

        assertFalse(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 5, 4), AREA),
                "a click is not consumed when the tab exposes no table area");
        assertEquals(1, state.selected(), "selection is unchanged");
    }
}
