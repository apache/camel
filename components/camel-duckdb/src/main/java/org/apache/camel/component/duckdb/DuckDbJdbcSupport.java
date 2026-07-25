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

import java.util.Properties;

import org.apache.camel.util.ObjectHelper;
import org.duckdb.DuckDBDriver;

final class DuckDbJdbcSupport {

    private DuckDbJdbcSupport() {
    }

    static String resolveJdbcUrl(String jdbcUrl, String databasePath) {
        if (ObjectHelper.isNotEmpty(jdbcUrl)) {
            return jdbcUrl.trim();
        }
        if (ObjectHelper.isEmpty(databasePath) || ":memory:".equals(databasePath.trim())) {
            return "jdbc:duckdb:";
        }
        return "jdbc:duckdb:" + databasePath.trim();
    }

    static Properties connectionProperties(boolean readOnly) {
        Properties properties = new Properties();
        if (readOnly) {
            properties.setProperty(DuckDBDriver.DUCKDB_READONLY_PROPERTY, "true");
        }
        return properties;
    }

    static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    /**
     * Quotes a single SQL identifier such as a column name.
     */
    static String quoteIdentifier(String identifier) {
        String trimmed = identifier.trim();
        if (trimmed.startsWith("\"")) {
            return trimmed;
        }
        return '"' + trimmed.replace("\"", "\"\"") + '"';
    }

    /**
     * Quotes a possibly qualified SQL identifier such as {@code schema.table}, quoting each segment on its own so the
     * qualification is preserved.
     */
    static String quoteQualifiedIdentifier(String identifier) {
        String trimmed = identifier.trim();
        if (trimmed.startsWith("\"")) {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : trimmed.split("\\.", -1)) {
            if (!sb.isEmpty()) {
                sb.append('.');
            }
            sb.append(quoteIdentifier(segment));
        }
        return sb.toString();
    }
}
