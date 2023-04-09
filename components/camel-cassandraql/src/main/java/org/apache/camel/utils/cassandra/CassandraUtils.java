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

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.delete.DeleteSelection;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.select.SelectFrom;
import com.datastax.oss.driver.api.querybuilder.truncate.Truncate;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

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
        InsertInto into = insertInto(table);
        final RegularInsert regularInsert = createRegularInsert(columns, into);

        Insert insert = null;
        if (ifNotExists && regularInsert != null) {
            insert = regularInsert.ifNotExists();
        }
        if (ttl != null && insert != null) {
            insert = (insert != null ? insert : regularInsert).usingTtl(ttl);
        }
        return insert != null ? insert : regularInsert;
    }

    private static RegularInsert createRegularInsert(String[] columns, InsertInto into) {
        RegularInsert regularInsert = null;

        for (String column : columns) {
            regularInsert = (regularInsert != null ? regularInsert : into).value(column, bindMarker());
        }
        return regularInsert;
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
        SelectFrom from = selectFrom(table);
        Select select = null;
        for (String column : selectColumns) {
            select = (select != null ? select : from).column(column);
        }
        if (select == null) {
            select = from.all();
        }
        if (isWhereClause(whereColumns, whereColumnsMaxIndex)) {
            for (int i = 0; i < whereColumns.length && i < whereColumnsMaxIndex; i++) {
                select = select.whereColumn(whereColumns[i]).isEqualTo(bindMarker());
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
        DeleteSelection deleteSelection = QueryBuilder.deleteFrom(table);
        Delete delete = null;

        if (isWhereClause(whereColumns, whereColumnsMaxIndex)) {
            for (int i = 0; i < whereColumns.length && i < whereColumnsMaxIndex; i++) {
                delete = (delete != null ? delete : deleteSelection).whereColumn(whereColumns[i]).isEqualTo(bindMarker());
            }
        } else {
            // Once there is at least one relation, the statement can be built
            //(see https://docs.datastax.com/en/developer/java-driver/4.6/manual/query_builder/delete/#relations)
            throw new IllegalArgumentException(
                    "Invalid delete statement. There has to be at least one relation. "
                                               + "To delete all records, use Truncate");
        }

        if (ifExists && delete != null) {
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
        return QueryBuilder.truncate(table);
    }

    /**
     * Apply consistency level if provided, else leave default.
     */
    public static <T extends SimpleStatement> T applyConsistencyLevel(T statement, ConsistencyLevel consistencyLevel) {
        if (consistencyLevel != null) {
            statement.setConsistencyLevel(consistencyLevel);
        }
        return statement;
    }
}
