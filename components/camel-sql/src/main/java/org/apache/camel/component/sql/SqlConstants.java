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
package org.apache.camel.component.sql;

/**
 * SQL Constants.
 */
public final class SqlConstants {

    public static final String SQL_QUERY = "CamelSqlQuery";

    public static final String SQL_UPDATE_COUNT = "CamelSqlUpdateCount";

    public static final String SQL_ROW_COUNT = "CamelSqlRowCount";

    /**
     * Boolean input header.
     * Set its value to true to retrieve generated keys, default is false
     */
    public static final String SQL_RETRIEVE_GENERATED_KEYS = "CamelSqlRetrieveGeneratedKeys";

    /**
     * <tt>String[]</tt> or <tt>int[]</tt> input header - optional
     * Set it to specify the expected generated columns, see:
     *
     * @see <a href="http://docs.oracle.com/javase/6/docs/api/java/sql/Statement.html#execute(java.lang.String, int[])">
     *      java.sql.Statement.execute(java.lang.String, int[])</a>
     * @see <a href="http://docs.oracle.com/javase/6/docs/api/java/sql/Statement.html#execute(java.lang.String, java.lang.String[])">
     *      java.sql.Statement.execute(java.lang.String, java.lang.String[])</a>
     */
    public static final String SQL_GENERATED_COLUMNS = "CamelSqlGeneratedColumns";

    /**
     * int output header giving the number of rows of generated keys
     */
    public static final String SQL_GENERATED_KEYS_ROW_COUNT = "CamelSqlGeneratedKeysRowCount";

    /**
     * <tt>List<Map<String, Object>></tt> output header containing the generated keys retrieved
     */
    public static final String SQL_GENERATED_KEYS_DATA = "CamelSqlGeneratedKeyRows";

    /**
     * The SQL parameters when using the option useMessageBodyForSql
     */
    public static final String SQL_PARAMETERS = "CamelSqlParameters";

    private SqlConstants() {
        // Utility class
    }
}
