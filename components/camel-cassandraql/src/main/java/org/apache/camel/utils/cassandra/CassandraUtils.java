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
package org.apache.camel.utils.cassandra;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.querybuilder.Truncate;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.ttl;

public final class CassandraUtils {

    private CassandraUtils() {
    }

    /**
     * Test if the array is null or empty.
     */
    public static boolean isEmpty(Object[] array) {
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
     * Generate Insert CQL.
     */
    public static Insert generateInsert(String table, String[] columns, boolean ifNotExists, Integer ttl) {
        Insert insert = insertInto(table);
        for (String column : columns) {
            insert = insert.value(column, bindMarker());
        }
        if (ifNotExists) {
            insert = insert.ifNotExists();
        }
        if (ttl != null) {
            insert.using(ttl(ttl));
        }
        return insert;
    }

    /**
     * Generate select where columns = ? CQL.
     */
    public static Select generateSelect(String table, String[] selectColumns, String[] whereColumns) {
        return generateSelect(table, selectColumns, whereColumns, size(whereColumns));
    }

    /**
     * Generate select where columns = ? CQL.
     */
    public static Select generateSelect(String table, String[] selectColumns, String[] whereColumns, int whereColumnsMaxIndex) {
        Select select = select(selectColumns).from(table);
        if (isWhereClause(whereColumns, whereColumnsMaxIndex)) {
            Select.Where where = select.where();
            for (int i = 0; i < whereColumns.length && i < whereColumnsMaxIndex; i++) {
                where.and(eq(whereColumns[i], bindMarker()));
            }
        }
        return select;
    }

    /**
     * Generate delete where columns = ? CQL.
     */
    public static Delete generateDelete(String table, String[] whereColumns, boolean ifExists) {
        return generateDelete(table, whereColumns, size(whereColumns), ifExists);
    }

    /**
     * Generate delete where columns = ? CQL.
     */
    public static Delete generateDelete(String table, String[] whereColumns, int whereColumnsMaxIndex, boolean ifExists) {
        Delete delete = delete().from(table);
        if (isWhereClause(whereColumns, whereColumnsMaxIndex)) {
            Delete.Where where = delete.where();
            for (int i = 0; i < whereColumns.length && i < whereColumnsMaxIndex; i++) {
                where.and(eq(whereColumns[i], bindMarker()));
            }
        }
        if (ifExists) {
            delete = delete.ifExists();
        }
        return delete;
    }

    private static boolean isWhereClause(String[] whereColumns, int whereColumnsMaxIndex) {
        return !isEmpty(whereColumns) && whereColumnsMaxIndex > 0;
    }

    /**
     * Generate delete where columns = ? CQL.
     */
    public static Truncate generateTruncate(String table) {
        Truncate truncate = QueryBuilder.truncate(table);
        return truncate;
    }

    /**
     * Apply consistency level if provided, else leave default.
     */
    public static <T extends RegularStatement> T applyConsistencyLevel(T statement, ConsistencyLevel consistencyLevel) {
        if (consistencyLevel != null) {
            statement.setConsistencyLevel(consistencyLevel);
        }
        return statement;
    }
}
