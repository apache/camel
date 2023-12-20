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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.util.ObjectHelper;

public class CellCoordinate {

    private int rowIndex;
    private int columnIndex;

    /**
     * Prevent direct instantiation
     */
    CellCoordinate() {
        super();
    }

    /**
     * Construct grid coordinate from given cell identifier representation in A1 form. For instance convert cell id
     * string "A1" to a coordinate with rowIndex=0, and columnIndex=0.
     *
     * @param  cellId
     * @return
     */
    public static CellCoordinate fromCellId(String cellId) {
        CellCoordinate coordinate = new CellCoordinate();

        if (cellId != null) {
            coordinate.setRowIndex(getRowIndex(cellId));
            coordinate.setColumnIndex(getColumnIndex(cellId));
        }

        return coordinate;
    }

    /**
     * Evaluate the column index from cellId in A1 notation. Column name letters are translated to numeric column index
     * values. Column "A" will result in column index 0. Method does support columns with combined name letters such as
     * "AA" where this is the first column after "Z" resulting in a column index of 26.
     *
     * @param  cellId
     * @return
     */
    protected static int getColumnIndex(String cellId) {
        char[] characters = cellId.toCharArray();
        List<Integer> chars = IntStream.range(0, characters.length)
                .mapToObj(i -> characters[i])
                .filter(c -> !Character.isDigit(c))
                .map(Character::toUpperCase)
                .map(Character::getNumericValue)
                .collect(Collectors.toList());

        if (chars.size() > 1) {
            int index = 0;
            for (int i = 0; i < chars.size(); i++) {
                if (i == chars.size() - 1) {
                    index += chars.get(i) - Character.getNumericValue('A');
                } else {
                    index += ((chars.get(i) - Character.getNumericValue('A')) + 1) * 26;
                }
            }
            return index;
        } else if (chars.size() == 1) {
            return chars.get(0) - Character.getNumericValue('A');
        } else {
            return 0;
        }
    }

    /**
     * Evaluates the row index from a given cellId in A1 notation. Extracts the row number and translates that to an
     * numeric index value beginning with 0.
     *
     * @param  cellId
     * @return
     */
    protected static int getRowIndex(String cellId) {
        char[] characters = cellId.toCharArray();
        String index = IntStream.range(0, characters.length)
                .mapToObj(i -> characters[i])
                .filter(Character::isDigit)
                .map(String::valueOf)
                .collect(Collectors.joining());

        if (ObjectHelper.isNotEmpty(index)) {
            return Integer.parseInt(index) - 1;
        }

        return 0;
    }

    /**
     * Evaluates column name in A1 notation based on the column index. Index 0 will be "A" and index 25 will be "Z".
     * Method also supports name overflow where index 26 will be "AA" and index 51 will be "AZ" and so on.
     *
     * @param  columnIndex
     * @return
     */
    public static String getColumnName(int columnIndex) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder columnName = new StringBuilder();

        int index = columnIndex;
        int overflowIndex = -1;
        while (index > 25) {
            overflowIndex++;
            index -= 26;
        }

        if (overflowIndex >= 0) {
            columnName.append(alphabet.toCharArray()[overflowIndex]);
        }

        columnName.append(alphabet.toCharArray()[index]);

        return columnName.toString();
    }

    /**
     * Special getter for column name where user is able to give set of user defined column names. When given column
     * index is resolvable via custom names the custom column name is returned otherwise the evaluated default column
     * name is returned.
     *
     * @param  columnIndex
     * @param  columnStartIndex
     * @param  columnNames
     * @return
     */
    public static String getColumnName(int columnIndex, int columnStartIndex, String... columnNames) {
        String columnName = getColumnName(columnIndex);

        int index;
        if (columnStartIndex > 0) {
            index = columnIndex % columnStartIndex;
        } else {
            index = columnIndex;
        }

        if (index < columnNames.length) {
            String name = columnNames[index];
            if (columnName.equals(name)) {
                return columnName;
            } else {
                return name;
            }
        }

        return columnName;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    /**
     * Specifies the rowIndex.
     *
     * @param rowIndex
     */
    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColumnIndex() {
        return columnIndex;
    }

    /**
     * Specifies the columnIndex.
     *
     * @param columnIndex
     */
    public void setColumnIndex(int columnIndex) {
        this.columnIndex = columnIndex;
    }
}
