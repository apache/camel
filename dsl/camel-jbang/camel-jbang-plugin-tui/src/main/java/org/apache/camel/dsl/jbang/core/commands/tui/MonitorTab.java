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
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonObject;

/**
 * Interface for TUI monitor tabs. Each tab handles its own events, rendering, and footer hints.
 */
interface MonitorTab {

    boolean handleKeyEvent(KeyEvent ke);

    /**
     * Handle a mouse event within the tab's content area.
     *
     * @param  me   the mouse event
     * @param  area the content area where this tab is rendered
     * @return      true if the event was consumed
     */
    default boolean handleMouseEvent(MouseEvent me, Rect area) {
        return false;
    }

    boolean handleEscape();

    void navigateUp();

    void navigateDown();

    void render(Frame frame, Rect area);

    void renderFooter(List<Span> spans);

    default void onTabSelected() {
    }

    default void onIntegrationChanged() {
    }

    default SelectionContext getSelectionContext() {
        return null;
    }

    default String getHelpText() {
        return null;
    }

    default JsonObject getTableDataAsJson() {
        return null;
    }

    default boolean setFilter(String filter) {
        return false;
    }

    default boolean setInputValue(String field, String value) {
        return false;
    }

    /**
     * Utility: render a vertical scrollbar on the right border of a table. The scrollbar overlays the table's right
     * border column, covering the data row area (skipping top border, header, and bottom border). Only renders when the
     * total row count exceeds the visible row count.
     *
     * @param frame       the rendering frame
     * @param tableArea   the area where the table was rendered
     * @param tableState  the table's state (provides scroll offset)
     * @param scrollState the scrollbar state to update
     * @param rowCount    the total number of data rows in the table
     */
    static void renderTableScrollbar(
            Frame frame, Rect tableArea, TableState tableState, ScrollbarState scrollState, int rowCount) {
        if (tableArea == null || tableState == null || scrollState == null) {
            return;
        }
        // visible data rows = area height - border top(1) - header(1) - border bottom(1)
        int visibleRows = tableArea.height() - 3;
        if (visibleRows <= 0 || rowCount <= visibleRows) {
            return;
        }
        Rect scrollRect = new Rect(
                tableArea.x() + tableArea.width() - 1,
                tableArea.y() + 2,
                1,
                visibleRows);
        scrollState.contentLength(rowCount);
        scrollState.viewportContentLength(visibleRows);
        scrollState.position(tableState.offset());
        frame.renderStatefulWidget(Scrollbar.builder().build(), scrollRect, scrollState);
    }

    /**
     * Utility: handle a mouse click on a table rendered with a Block (Borders.ALL) and a header row. Accounts for
     * border (1 row) + header (1 row) = 2 rows before data, and the table scroll offset.
     *
     * @param  me         the mouse event
     * @param  tableArea  the area where the table was rendered (null-safe)
     * @param  tableState the table's state (null-safe)
     * @param  rowCount   the total number of data rows in the table
     * @return            true if a row was selected
     */
    static boolean handleTableClick(MouseEvent me, Rect tableArea, TableState tableState, int rowCount) {
        if (tableArea == null || tableState == null || rowCount <= 0) {
            return false;
        }
        if (!me.isClick()) {
            return false;
        }
        int mx = me.x();
        int my = me.y();
        if (mx < tableArea.x() || mx >= tableArea.x() + tableArea.width()
                || my < tableArea.y() || my >= tableArea.y() + tableArea.height()) {
            return false;
        }
        // 2 = border top (1) + header row (1); data rows start at y + 2
        int rowIndex = tableState.offset() + (my - tableArea.y() - 2);
        if (rowIndex >= 0 && rowIndex < rowCount) {
            tableState.select(rowIndex);
            return true;
        }
        return false;
    }
}
