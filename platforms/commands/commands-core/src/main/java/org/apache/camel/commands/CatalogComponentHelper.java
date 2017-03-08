/**
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
package org.apache.camel.commands;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.ObjectHelper;

@Deprecated
public final class CatalogComponentHelper {
    private CatalogComponentHelper() {
    }

    public static final class TableColumn {
        private final String header;
        private final String key;
        private int maxLen;
        private String formatString;

        public TableColumn(String header, String key) {
            this.header = header;
            this.key = key;
            this.maxLen = header.length();
            this.formatString = null;
        }

        public String getHeader() {
            return header;
        }

        public String getKey() {
            return key;
        }

        public int getMaxLen() {
            return maxLen;
        }

        public void computeMaxLen(Map<String, String> row) {
            String val = row.get(key);
            if (val != null) {
                maxLen = Math.max(maxLen, val.length());
            }
        }

        private String headerSeparator() {
            StringBuilder sb = new StringBuilder(header.length());
            while (sb.length() < header.length()) {
                sb.append('-');
            }

            return sb.toString();
        }

        private String formatString() {
            if (formatString == null) {
                formatString = new StringBuilder()
                    .append("%-").append(maxLen).append('.').append(maxLen).append('s')
                    .toString();
            }

            return formatString;
        }

        public void printHeader(PrintStream out, boolean newLine) {
            String outStr = String.format(formatString(), header);

            if (newLine) {
                out.println(outStr.trim());
            } else {
                out.print(outStr);
                out.print(' ');
            }
        }

        public void printHeaderSeparator(PrintStream out, boolean newLine) {
            String outStr = String.format(formatString(), headerSeparator());

            if (newLine) {
                out.println(outStr.trim());
            } else {
                out.print(outStr);
                out.print(' ');
            }
        }

        public void printValue(PrintStream out, Map<String, String> row, boolean newLine) {
            String val = row.get(key);
            if (val == null) {
                val = "";
            }

            String outStr = String.format(formatString(), val);

            if (newLine) {
                out.println(outStr.trim());
            } else {
                out.print(outStr);
                out.print(' ');
            }
        }
    }

    public static final class Table {
        private final List<TableColumn> columns;
        private final List<Map<String, String>> rows;

        public Table(String[] headers, String[] keys) {
            assert headers.length == keys.length;

            this.columns = new LinkedList<>();
            this.rows = new LinkedList<>();

            for (int i = 0; i < headers.length; i++) {
                columns.add(new TableColumn(headers[i], keys[i]));
            }
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public void addRow(String name, Map<String, Object> row) {
            Map<String, String> rowData = null;

            for (TableColumn column : columns) {
                Object val = row.get(column.getKey());
                if (ObjectHelper.isNotEmpty(val)) {
                    if (rowData == null) {
                        rowData = new LinkedHashMap<>();
                    }

                    rowData.put(column.getKey(), val.toString());
                }
            }

            if (rowData != null) {
                rowData.put("key", name);
                rows.add(rowData);

                for (TableColumn column : columns) {
                    column.computeMaxLen(rowData);
                }
            }
        }

        public void print(PrintStream out) {
            for (int r = 0; r < rows.size(); r++) {
                if (r == 0) {
                    printHeader(out);
                }

                Map<String, String> row = rows.get(r);
                for (int c = 0; c < columns.size(); c++) {
                    columns.get(c).printValue(out, row, c == columns.size() - 1);
                }
            }
        }

        private void printHeader(PrintStream out) {
            for (int c = 0; c < columns.size(); c++) {
                TableColumn column = columns.get(c);
                column.printHeader(out, c == columns.size() - 1);
            }
            for (int c = 0; c < columns.size(); c++) {
                TableColumn column = columns.get(c);
                column.printHeaderSeparator(out, c == columns.size() - 1);
            }
        }
    }
}
