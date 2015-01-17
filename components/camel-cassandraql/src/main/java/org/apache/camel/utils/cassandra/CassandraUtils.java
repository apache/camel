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
package org.apache.camel.utils.cassandra;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;

/**
 *
 */
public class CassandraUtils {
    /**
     * Apply consistency level if provided, else leave default.
     */
    public static PreparedStatement applyConsistencyLevel(PreparedStatement statement, ConsistencyLevel consistencyLevel) {
        if (consistencyLevel != null) {
            statement.setConsistencyLevel(consistencyLevel);
        }
        return statement;
    }
    private static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }
    /**
     * Concatenate 2 arrays.
     */
    public static Object[] concat(Object[] array1, Object[] array2) {
        if (isEmpty(array1)) {
            return array2;
        }
        if (isEmpty(array2)) {
            return array1;
        }
        Object[] array = new Object[array1.length + array2.length];
        System.arraycopy(array1, 0, array, 0, array1.length);
        System.arraycopy(array2, 0, array, array1.length, array2.length);
        return array;
    }

    private static int size(String[] array) {
        return array == null ? 0 : array.length;
    }

    private static boolean isEmpty(String[] array) {
        return size(array) == 0;
    }
    /**
     * Concatenate 2 arrays.
     */
    public static String[] concat(String[] array1, String[] array2) {
        if (isEmpty(array1)) {
            return array2;
        }
        if (isEmpty(array2)) {
            return array1;
        }
        String[] array = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, array, 0, array1.length);
        System.arraycopy(array2, 0, array, array1.length, array2.length);
        return array;
    }

    /**
     * Append values to given array.
     */
    public static Object[] append(Object[] array1, Object... array2) {
        return concat(array1, array2);
    }

    /**
     * Append values to given array.
     */
    public static String[] append(String[] array1, String... array2) {
        return concat(array1, array2);
    }

    /**
     * Append columns to CQL.
     */
    public static void appendColumns(StringBuilder cqlBuilder, String[] columns, String sep, int maxColumnIndex) {
        for (int i = 0; i < maxColumnIndex; i++) {
            if (i > 0) {
                cqlBuilder.append(sep);
            }
            cqlBuilder.append(columns[i]);
        }
    }

    /**
     * Append columns to CQL.
     */
    public static void appendColumns(StringBuilder cqlBuilder, String[] columns, String sep) {
        appendColumns(cqlBuilder, columns, sep, columns.length);
    }

    /**
     * Append where columns = ? to CQL.
     */
    public static void appendWhere(StringBuilder cqlBuilder, String[] columns, int maxColumnIndex) {
        if (isEmpty(columns) || maxColumnIndex<= 0) {
            return;
        }
        cqlBuilder.append(" where ");
        appendColumns(cqlBuilder, columns, "=? and ", maxColumnIndex);
        cqlBuilder.append("=?");
    }

    /**
     * Append where columns = ? to CQL.
     */
    public void appendWhere(StringBuilder cqlBuilder, String[] columns) {
        appendWhere(cqlBuilder, columns, columns.length);
    }

    /**
     * Append ?,? to CQL.
     */
    public static void appendPlaceholders(StringBuilder cqlBuilder, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            if (i > 0) {
                cqlBuilder.append(",");
            }
            cqlBuilder.append("?");
        }
    }

    /**
     * Generate Insert CQL.
     */
    public static StringBuilder generateInsert(String table, String[] columns, boolean ifNotExists, Integer ttl) {
        StringBuilder cqlBuilder = new StringBuilder("insert into ")
                .append(table).append("(");
        appendColumns(cqlBuilder, columns, ",");
        cqlBuilder.append(") values (");
        appendPlaceholders(cqlBuilder, columns.length);
        cqlBuilder.append(")");
        if (ifNotExists) {
            cqlBuilder.append(" if not exists");
        }
        if (ttl != null) {
            cqlBuilder.append(" using ttl=").append(ttl);
        }
        return cqlBuilder;
    }
    /**
     * Generate select where columns = ? CQL.
     */
    public static StringBuilder generateSelect(String table, String[] selectColumns, String[] whereColumns) {
        return generateSelect(table, selectColumns, whereColumns, size(whereColumns));
    }

    /**
     * Generate select where columns = ? CQL.
     */
    public static StringBuilder generateSelect(String table, String[] selectColumns, String[] whereColumns, int whereColumnsMaxIndex) {
        StringBuilder cqlBuilder = new StringBuilder("select ");
        appendColumns(cqlBuilder, selectColumns, ",");
        cqlBuilder.append(" from ").append(table);
        appendWhere(cqlBuilder, whereColumns, whereColumnsMaxIndex);
        return cqlBuilder;
    }

    /**
     * Generate delete where columns = ? CQL.
     */
    public static StringBuilder generateDelete(String table, String[] whereColumns, boolean ifExists) {
        return generateDelete(table, whereColumns, size(whereColumns), ifExists);
    }

    /**
     * Generate delete where columns = ? CQL.
     */
    public static StringBuilder generateDelete(String table, String[] whereColumns, int whereColumnsMaxIndex, boolean ifExists) {
        StringBuilder cqlBuilder = new StringBuilder("delete from ")
                .append(table);
        appendWhere(cqlBuilder, whereColumns, whereColumnsMaxIndex);
        if (ifExists) {
            cqlBuilder.append(" if exists");
        }
        return cqlBuilder;
    }
}
