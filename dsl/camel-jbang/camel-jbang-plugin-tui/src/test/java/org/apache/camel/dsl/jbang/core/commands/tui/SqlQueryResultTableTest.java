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
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The columns of the SQL result grid are as wide as their content (CAMEL-26860): measured as shown on screen, and the
 * narrow columns keep their width when the wide ones do not fit.
 */
class SqlQueryResultTableTest {

    @Test
    void eachColumnIsAsWideAsItsWidestValueOrName() {
        Buffer buffer = render(40, new String[] { "ID", "NAME" }, row("ID", 1, "NAME", "Alice"),
                row("ID", 22, "NAME", "Bob"));

        assertThat(line(buffer, 0)).isEqualTo("ID   NAME");
        assertThat(line(buffer, 1)).isEqualTo("1    Alice");
        assertThat(line(buffer, 2)).isEqualTo("22   Bob");
    }

    @Test
    void aNullValueTakesTheWidthOfNull() {
        Buffer buffer = render(40, new String[] { "A", "B" }, row("A", null, "B", "x"));

        assertThat(line(buffer, 1)).isEqualTo("null   x");
    }

    @Test
    void wideCharactersTakeTwoColumns() {
        // 日本語 is three characters but six columns on screen: the next column starts after them, not inside
        Buffer buffer = render(40, new String[] { "CITY", "CODE" }, row("CITY", "日本語", "CODE", "JP"));

        assertThat(buffer.get(9, 0).symbol()).isEqualTo("C");
        assertThat(buffer.get(9, 1).symbol()).isEqualTo("J");
        assertThat(buffer.get(10, 1).symbol()).isEqualTo("P");
    }

    @Test
    void theNarrowColumnsKeepTheirWidthWhenTheWideOneDoesNotFit() {
        String payload = "x".repeat(100);
        Buffer buffer = render(40, new String[] { "ID", "PAYLOAD", "OK" },
                row("ID", 7, "PAYLOAD", payload, "OK", true));

        // 40 columns: ID 2, OK 4, the gaps 2 x 3, PAYLOAD the 28 left
        assertThat(line(buffer, 0)).isEqualTo("ID   PAYLOAD" + " ".repeat(24) + "OK");
        assertThat(line(buffer, 1)).isEqualTo("7    " + "x".repeat(28) + "   true");
    }

    @Test
    void thePrimaryKeyMarkCountsInTheWidthOfItsName() {
        String[] columns = { "ID", "NAME" };
        List<JsonObject> rows = List.of(row("ID", 1, "NAME", "Alice"));
        Rect area = new Rect(0, 0, 40, 3);
        Buffer buffer = Buffer.empty(area);
        Frame.forTesting(buffer).renderStatefulWidget(
                SqlQueryTab.resultTable(columns, new boolean[] { true, false }, rows).build(), area, new TableState());

        assertThat(line(buffer, 0)).startsWith("ID " + TuiIcons.KEY);
        // NAME starts after the key mark, as wide as it is on screen, and its values below it
        int name = column(buffer, 0, "N");
        assertThat(name).isEqualTo(CharWidth.of("ID " + TuiIcons.KEY) + 3);
        assertThat(column(buffer, 1, "A")).isEqualTo(name);
    }

    private static Buffer render(int width, String[] columns, JsonObject... rows) {
        Rect area = new Rect(0, 0, width, rows.length + 1);
        Buffer buffer = Buffer.empty(area);
        Frame.forTesting(buffer).renderStatefulWidget(
                SqlQueryTab.resultTable(columns, new boolean[columns.length], List.of(rows)).build(), area,
                new TableState());
        return buffer;
    }

    private static JsonObject row(Object... keysAndValues) {
        JsonObject row = new JsonObject();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    private static int column(Buffer buffer, int y, String symbol) {
        for (int x = 0; x < buffer.width(); x++) {
            if (symbol.equals(buffer.get(x, y).symbol())) {
                return x;
            }
        }
        return -1;
    }

    /** One line of the buffer, the continuation cell of a wide character left out, without trailing spaces. */
    private static String line(Buffer buffer, int y) {
        List<String> symbols = new ArrayList<>();
        for (int x = 0; x < buffer.width(); x++) {
            symbols.add(buffer.get(x, y).symbol());
        }
        return String.join("", symbols).stripTrailing();
    }
}
