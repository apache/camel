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
package org.apache.camel.component.jdbc;

import org.apache.camel.spi.Metadata;

/**
 * JDBC Constants
 */
public final class JdbcConstants {

    @Metadata(label = "producer", description = "If the query is an `UPDATE`, query the update count is returned in this\n" +
                                                "OUT header.",
              javaType = "int")
    public static final String JDBC_UPDATE_COUNT = "CamelJdbcUpdateCount";
    @Metadata(label = "producer", description = "If the query is a `SELECT`, query the row count is returned in this OUT\n" +
                                                "header.",
              javaType = "int")
    public static final String JDBC_ROW_COUNT = "CamelJdbcRowCount";
    @Metadata(label = "producer", description = "The column names from the ResultSet as a `java.util.Set`\n" +
                                                "type.",
              javaType = "Set<String>")
    public static final String JDBC_COLUMN_NAMES = "CamelJdbcColumnNames";
    @Metadata(label = "producer", description = "A `java.util.Map` which has the headers to be used if\n" +
                                                "`useHeadersAsParameters` has been enabled.",
              javaType = "Map")
    public static final String JDBC_PARAMETERS = "CamelJdbcParameters";

    /**
     * Boolean input header. Set its value to true to retrieve generated keys, default is false
     */
    @Metadata(label = "producer", description = "Set its value to true to retrieve generated keys", javaType = "Boolean",
              defaultValue = "false")
    public static final String JDBC_RETRIEVE_GENERATED_KEYS = "CamelRetrieveGeneratedKeys";

    /**
     * <tt>String[]</tt> or <tt>int[]</tt> input header - optional Set it to specify the expected generated columns,
     * see:
     *
     * @see <a href="http://docs.oracle.com/javase/6/docs/api/java/sql/Statement.html#execute(java.lang.String, int[])">
     *      java.sql.Statement.execute(java.lang.String, int[])</a>
     * @see <a
     *      href="http://docs.oracle.com/javase/6/docs/api/java/sql/Statement.html#execute(java.lang.String, java.lang.String[])">
     *      java.sql.Statement.execute(java.lang.String, java.lang.String[])</a>
     */
    @Metadata(label = "producer", description = "Set it to specify the expected generated columns",
              javaType = "String[] or int[]")
    public static final String JDBC_GENERATED_COLUMNS = "CamelGeneratedColumns";

    /**
     * int output header giving the number of rows of generated keys
     */
    @Metadata(label = "producer", description = "The number of rows in the header that contains generated\n" +
                                                "keys.",
              javaType = "int")
    public static final String JDBC_GENERATED_KEYS_ROW_COUNT = "CamelGeneratedKeysRowCount";

    /**
     * <tt>List<Map<String, Object>></tt> output header containing the generated keys retrieved
     */
    @Metadata(label = "producer", description = "Rows that contains the generated keys.",
              javaType = "List<Map<String, Object>>")
    public static final String JDBC_GENERATED_KEYS_DATA = "CamelGeneratedKeysRows";

    private JdbcConstants() {
        // Utility class
    }
}
