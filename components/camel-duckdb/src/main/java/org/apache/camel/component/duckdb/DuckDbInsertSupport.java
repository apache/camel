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
package org.apache.camel.component.duckdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DuckDbInsertSupport {

    private DuckDbInsertSupport() {
    }

    static long insertMaps(Connection connection, String table, List<?> rows, int batchSize) throws Exception {
        if (rows.isEmpty()) {
            return 0;
        }
        if (!(rows.get(0) instanceof Map)) {
            throw new DuckDbException("Insert body must be a List of Map entries when using operation=insert");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> maps = (List<Map<String, Object>>) rows;
        List<String> columns = new ArrayList<>(maps.get(0).keySet());
        if (columns.isEmpty()) {
            throw new DuckDbException("Insert row maps must contain at least one column");
        }

        String sql = buildInsertSql(table, columns);
        long written = 0;
        int effectiveBatch = batchSize > 0 ? batchSize : maps.size();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < maps.size(); i++) {
                Map<String, Object> row = maps.get(i);
                for (int c = 0; c < columns.size(); c++) {
                    ps.setObject(c + 1, row.get(columns.get(c)));
                }
                ps.addBatch();
                if ((i + 1) % effectiveBatch == 0 || i + 1 == maps.size()) {
                    int[] counts = ps.executeBatch();
                    for (int count : counts) {
                        if (count >= 0) {
                            written += count;
                        } else {
                            written += 1;
                        }
                    }
                }
            }
        }
        return written;
    }

    private static String buildInsertSql(String table, List<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(DuckDbJdbcSupport.quoteQualifiedIdentifier(table)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append(DuckDbJdbcSupport.quoteIdentifier(columns.get(i)));
        }
        sql.append(") VALUES (");
        sql.append("?,".repeat(columns.size()));
        sql.setLength(sql.length() - 1);
        sql.append(')');
        return sql.toString();
    }
}
