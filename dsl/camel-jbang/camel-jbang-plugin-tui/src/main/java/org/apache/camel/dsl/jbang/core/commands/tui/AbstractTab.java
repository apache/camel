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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

abstract class AbstractTab implements MonitorTab {

    /** Presses a key of the tab, as a click on a view of the view bar does. */
    protected void pressKey(char key) {
        handleKeyEvent(KeyEvent.ofChar(key));
    }

    protected final MonitorContext ctx;

    protected AbstractTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    /** The source directory of the selected integration, where its project files and summary are; may be null. */
    protected Path selectedSourceDirectory() {
        IntegrationInfo info = ctx != null ? ctx.findSelectedIntegration() : null;
        return info != null ? FilesBrowser.resolveSourceDirectory(info) : null;
    }

    // ---- Rendering helpers ----

    protected static void renderNoSelection(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));
        for (String row : TuiHelper.SMALL_CAMEL) {
            lines.add(Line.from(Span.styled("   " + row, Style.EMPTY.fg(Theme.accent()))));
        }
        lines.add(Line.from(Span.raw("")));
        List<Span> hintSpans = new ArrayList<>();
        hintSpans.add(Span.raw("   No integration selected.  "));
        TuiHelper.hint(hintSpans, "1", "Overview");
        TuiHelper.hint(hintSpans, "?", "Help");
        lines.add(Line.from(hintSpans));

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(Title.from(Line.from(
                                        Span.styled(" No integration selected ", Theme.title()))))
                                .build())
                        .build(),
                area);
    }

    // ---- Cell construction helpers ----

    protected static Cell rightCell(String text, int width) {
        return Cell.from(String.format("%" + width + "s", text));
    }

    protected static Cell rightCell(String text, int width, Style style) {
        return Cell.from(Span.styled(String.format("%" + width + "s", text), style));
    }

    protected static Cell centerCell(String text, int width) {
        int len = text.length();
        int padding = Math.max(0, width - len);
        int leftPad = padding / 2;
        return Cell.from(" ".repeat(leftPad) + text);
    }

    protected static Cell centerCell(String text, int width, Style style) {
        int len = text.length();
        int padding = Math.max(0, width - len);
        int leftPad = padding / 2;
        return Cell.from(Span.styled(" ".repeat(leftPad) + text, style));
    }

    protected static Row emptyRow(String message, int columnCount) {
        return emptyRow(message, columnCount, 0);
    }

    /**
     * A row that says the table is empty, with the message in the given column: a table whose first column is narrow
     * puts it in its widest column, so it is not cut ("No infligh").
     */
    protected static Row emptyRow(String message, int columnCount, int column) {
        Cell[] cells = new Cell[columnCount];
        for (int i = 0; i < columnCount; i++) {
            cells[i] = i == column ? Cell.from(Span.styled(message, Style.EMPTY.dim())) : Cell.from("");
        }
        return Row.from(cells);
    }

    // ---- Sort helpers ----

    protected static String sortLabel(String label, String column, String currentSort, boolean reversed) {
        return currentSort.equals(column) ? label + (reversed ? TuiIcons.SORT_UP : TuiIcons.SORT_DOWN) : label;
    }

    protected static Style sortStyle(String column, String currentSort) {
        return currentSort.equals(column)
                ? Theme.label().bold()
                : Style.EMPTY.bold();
    }

    // ---- Mouse / scrollbar helpers ----

    /**
     * Draws a scrollbar over the right border of a bordered table with a header row, sized to the rows the table
     * actually shows in {@code tableArea} (so a footer row, as in the Ollama tab, shortens it accordingly).
     */
    protected static void renderTableScrollbar(
            Frame frame, Rect tableArea, Table table, TableState tableState, ScrollbarState scrollState,
            int rowCount) {
        if (tableArea == null || table == null || tableState == null || scrollState == null) {
            return;
        }
        clampTableOffset(table, tableArea, tableState, rowCount);
        int visibleRows = table.viewportHeight(tableArea);
        if (visibleRows <= 0 || rowCount <= visibleRows) {
            return;
        }
        // the data rows start below the top border and the header row
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
     * Pulls the scroll offset of a table back when rows went away, so the rows fill the view. The table only scrolls to
     * keep the selected row in view: when the list shrinks (apps stopped, projects closed), rows that fit could stay
     * above the top, and a running app looked gone.
     */
    static void clampTableOffset(Table table, Rect tableArea, TableState tableState, int rowCount) {
        if (tableArea == null || table == null || tableState == null) {
            return;
        }
        int maxOffset = Math.max(0, rowCount - Math.max(0, table.viewportHeight(tableArea)));
        if (tableState.offset() > maxOffset) {
            tableState.setOffset(maxOffset);
        }
    }

    protected static boolean handleTableClick(MouseEvent me, Rect tableArea, TableState tableState, int rowCount) {
        if (tableArea == null || tableState == null || rowCount <= 0) {
            return false;
        }
        if (!me.isClick()) {
            return false;
        }
        if (!TuiHelper.contains(tableArea, me.x(), me.y())) {
            return false;
        }
        int rowIndex = tableState.offset() + (me.y() - tableArea.y() - 2);
        if (rowIndex >= 0 && rowIndex < rowCount) {
            tableState.select(rowIndex);
            return true;
        }
        return false;
    }
}
