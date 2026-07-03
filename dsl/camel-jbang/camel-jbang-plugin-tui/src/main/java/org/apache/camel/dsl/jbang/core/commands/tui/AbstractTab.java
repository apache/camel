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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
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
import dev.tamboui.widgets.table.TableState;

abstract class AbstractTab implements MonitorTab {

    protected final MonitorContext ctx;

    protected AbstractTab(MonitorContext ctx) {
        this.ctx = ctx;
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
        Cell[] cells = new Cell[columnCount];
        cells[0] = Cell.from(Span.styled(message, Style.EMPTY.dim()));
        for (int i = 1; i < columnCount; i++) {
            cells[i] = Cell.from("");
        }
        return Row.from(cells);
    }

    // ---- Sort helpers ----

    protected static String sortLabel(String label, String column, String currentSort, boolean reversed) {
        return currentSort.equals(column) ? label + (reversed ? "▲" : "▼") : label;
    }

    protected static Style sortStyle(String column, String currentSort) {
        return currentSort.equals(column)
                ? Style.EMPTY.fg(Color.YELLOW).bold()
                : Style.EMPTY.bold();
    }

    // ---- Mouse / scrollbar helpers ----

    protected static void renderTableScrollbar(
            Frame frame, Rect tableArea, TableState tableState, ScrollbarState scrollState, int rowCount) {
        if (tableArea == null || tableState == null || scrollState == null) {
            return;
        }
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
