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
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonObject;

/**
 * Interface for TUI monitor tabs. Each tab handles its own events, rendering, and footer hints.
 */
interface MonitorTab {

    boolean handleKeyEvent(KeyEvent ke);

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

    default TableState getTableState() {
        return null;
    }

    default int getTableRowCount() {
        return 0;
    }

    /**
     * The bounds of the tab's main table (including its block border and header) as last rendered, or {@code null} when
     * the tab has no clickable table. Used to map a click to the row under the pointer. Tabs that expose a
     * {@link #getTableState()} should return the same table's area here so a click selects the right row.
     */
    default Rect getTableArea() {
        return null;
    }

    default boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (!area.contains(me.x(), me.y())) {
            return false;
        }
        TableState ts = getTableState();
        if (ts == null) {
            return false;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            ts.selectPrevious();
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            ts.selectNext(getTableRowCount());
            return true;
        }
        if (me.isClick()) {
            int row = tableRowAt(getTableArea(), ts.offset(), getTableRowCount(), me.x(), me.y());
            if (row >= 0) {
                ts.select(row);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the model row index of the data row at {@code (mouseX, mouseY)} within a bordered table that has a header
     * row, or {@code -1} when the click is on the border, the header, or outside the data rows. The table is drawn with
     * a one-cell border on every side plus a header row, so the first data row is at {@code table.y() + 2}.
     * {@code offset} is the index of the first visible data row (from {@code TableState.offset()}) for scrolled tables.
     */
    static int tableRowAt(Rect table, int offset, int rowCount, int mouseX, int mouseY) {
        if (table == null) {
            return -1;
        }
        int innerLeft = table.x() + 1;
        int innerRight = table.x() + table.width() - 1; // exclusive: right border
        int firstDataRow = table.y() + 2; // top border + header row
        int lastDataRow = table.y() + table.height() - 1; // exclusive: bottom border
        if (mouseX < innerLeft || mouseX >= innerRight) {
            return -1;
        }
        if (mouseY < firstDataRow || mouseY >= lastDataRow) {
            return -1;
        }
        int idx = offset + (mouseY - firstDataRow);
        return idx >= 0 && idx < rowCount ? idx : -1;
    }

    default boolean setInputValue(String field, String value) {
        return false;
    }
}
