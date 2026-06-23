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
package org.apache.camel.dsl.jbang.core.common;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CamelTableColumnsTest {

    private static final Function<String, String> IDENTITY = s -> s;

    // --- measure ---

    @Test
    void measureUsesLongestCellWhenWiderThanHeader() {
        List<String> rows = List.of("ftp", "salesforce", "kafka");
        // longest value "salesforce" = 10, header "NAME" = 4
        assertEquals(10, CamelTableColumns.measure("NAME", CamelTableColumns.NAME_MAX, rows, IDENTITY));
    }

    @Test
    void measureUsesHeaderWhenWiderThanCells() {
        List<String> rows = List.of("x", "y");
        // header "DESCRIPTION" = 11 is wider than any 1-char value
        assertEquals(11, CamelTableColumns.measure("DESCRIPTION", Integer.MAX_VALUE, rows, IDENTITY));
    }

    @Test
    void measureCapsAtMaxWidth() {
        List<String> rows = List.of("a-very-long-component-name-that-exceeds-the-cap-by-a-lot-indeed");
        // value length is > NAME_MAX, so it is capped at NAME_MAX
        assertEquals(CamelTableColumns.NAME_MAX,
                CamelTableColumns.measure("NAME", CamelTableColumns.NAME_MAX, rows, IDENTITY));
    }

    @Test
    void measureIgnoresNullCells() {
        List<String> rows = Arrays.asList("ok", null, "fine");
        assertEquals(4, CamelTableColumns.measure("N", Integer.MAX_VALUE, rows, IDENTITY));
    }

    // --- lastColumnWidth ---

    @Test
    void lastColumnWidthConsumesExactRemainder() {
        // 200 terminal - (20 + 10 + 8) others - 6 borders = 156 for the last column
        assertEquals(156, CamelTableColumns.lastColumnWidth(200, 6, 20, 10, 8));
    }

    @Test
    void lastColumnWidthFloorsAtLastMinOnNarrowTerminal() {
        // remainder would be negative, so it is floored at LAST_MIN
        assertEquals(CamelTableColumns.LAST_MIN, CamelTableColumns.lastColumnWidth(40, 6, 30, 12, 10));
    }
}
