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
package org.apache.camel.component.google.sheets.transform;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CellCoordinateTest {

    @Test
    public void testColumnNamesWithOneAndTwoLetters() {
        Assertions.assertEquals("A", CellCoordinate.getColumnName(0));
        Assertions.assertEquals("Z", CellCoordinate.getColumnName(25));
        Assertions.assertEquals("AA", CellCoordinate.getColumnName(26));
        Assertions.assertEquals("ZZ", CellCoordinate.getColumnName(701));
        Assertions.assertEquals(0, CellCoordinate.fromCellId("A1").getColumnIndex());
        Assertions.assertEquals(27, CellCoordinate.fromCellId("AB12").getColumnIndex());
        Assertions.assertEquals(701, CellCoordinate.fromCellId("ZZ3").getColumnIndex());
        Assertions.assertEquals(11, CellCoordinate.fromCellId("ZZ12").getRowIndex());
    }

    @Test
    public void testColumnNamesWithThreeLetters() {
        Assertions.assertEquals("AAA", CellCoordinate.getColumnName(702));
        Assertions.assertEquals("XFD", CellCoordinate.getColumnName(16383));
        Assertions.assertEquals(702, CellCoordinate.fromCellId("AAA1").getColumnIndex());
        Assertions.assertEquals(16383, CellCoordinate.fromCellId("XFD1").getColumnIndex());

        RangeCoordinate range = RangeCoordinate.fromRange("Sheet1!ZZ1:AAB2");
        Assertions.assertEquals(701, range.getColumnStartIndex());
        Assertions.assertEquals(704, range.getColumnEndIndex());
        Assertions.assertEquals("ZZ,AAA,AAB", range.getColumnNames());
    }

    @Test
    public void testColumnNameRoundTrip() {
        for (int i = 0; i < 18278; i++) {
            Assertions.assertEquals(i, CellCoordinate.fromCellId(CellCoordinate.getColumnName(i) + "1").getColumnIndex(),
                    "column " + i);
        }
    }

    @Test
    public void testCustomColumnNamesByPosition() {
        String[] names = { "name", "age", "city" };
        // range starting at column A
        Assertions.assertEquals("name", CellCoordinate.getColumnName(0, 0, names));
        Assertions.assertEquals("city", CellCoordinate.getColumnName(2, 0, names));
        Assertions.assertEquals("D", CellCoordinate.getColumnName(3, 0, names));
        // range starting at column B
        Assertions.assertEquals("name", CellCoordinate.getColumnName(1, 1, names));
        Assertions.assertEquals("age", CellCoordinate.getColumnName(2, 1, names));
        Assertions.assertEquals("city", CellCoordinate.getColumnName(3, 1, names));
        Assertions.assertEquals("E", CellCoordinate.getColumnName(4, 1, names));
        // range starting at column C
        Assertions.assertEquals("name", CellCoordinate.getColumnName(2, 2, names));
        Assertions.assertEquals("age", CellCoordinate.getColumnName(3, 2, names));
        Assertions.assertEquals("city", CellCoordinate.getColumnName(4, 2, names));
        Assertions.assertEquals("F", CellCoordinate.getColumnName(5, 2, names));
        // the default column names "A": the first column of the range is named "A" as before (e.g. range B:B),
        // the other columns keep their A1 name instead of being named "A" as well
        Assertions.assertEquals("A", CellCoordinate.getColumnName(1, 1, "A"));
        Assertions.assertEquals("C", CellCoordinate.getColumnName(2, 1, "A"));
        Assertions.assertEquals("D", CellCoordinate.getColumnName(3, 1, "A"));
    }
}
