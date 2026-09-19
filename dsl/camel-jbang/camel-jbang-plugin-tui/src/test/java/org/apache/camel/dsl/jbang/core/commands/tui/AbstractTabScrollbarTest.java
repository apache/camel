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
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The table scrollbar has to cover exactly the data rows the table shows: a footer row (as in the Ollama tab) takes one
 * row away from the viewport, and a scrollbar sized from the area height alone would run over it.
 */
class AbstractTabScrollbarTest {

    private static final String THUMB = "█";
    private static final int ROWS = 40;

    @Test
    void scrollbarEndsOnTheLastDataRowOfAPlainTable() {
        Rect area = new Rect(0, 0, 30, 12);
        Buffer buffer = render(area, table(false));

        // top border + header, then 9 data rows, then the bottom border
        int lastDataRow = area.height() - 2;
        assertThat(symbol(buffer, lastDataRow)).isEqualTo(THUMB);
        assertThat(symbol(buffer, area.height() - 1)).isEqualTo("╯");
    }

    @Test
    void scrollbarStopsAboveTheFooterRow() {
        Rect area = new Rect(0, 0, 30, 12);
        Buffer buffer = render(area, table(true));

        int footerRow = area.height() - 2;
        assertThat(symbol(buffer, footerRow - 1)).isEqualTo(THUMB);
        assertThat(symbol(buffer, footerRow)).as("footer row keeps the border").isEqualTo("│");
    }

    private static Buffer render(Rect area, Table table) {
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        TableState tableState = new TableState();
        // scrolled to the end, so the thumb sits on the last data row of the viewport
        tableState.selectLast(ROWS);
        frame.renderStatefulWidget(table, area, tableState);
        AbstractTab.renderTableScrollbar(frame, area, table, tableState, new ScrollbarState(), ROWS);
        return buffer;
    }

    private static String symbol(Buffer buffer, int y) {
        return buffer.get(buffer.area().width() - 1, y).symbol();
    }

    private static Table table(boolean footer) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            rows.add(Row.from(Cell.from("row " + i), Cell.from("value")));
        }
        Table.Builder builder = Table.builder()
                .rows(rows)
                .header(Row.from(Cell.from("NAME"), Cell.from("VALUE")))
                .widths(Constraint.length(10), Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).build());
        if (footer) {
            builder.footer(Row.from(Cell.from("total"), Cell.from(String.valueOf(ROWS))));
        }
        return builder.build();
    }
}
