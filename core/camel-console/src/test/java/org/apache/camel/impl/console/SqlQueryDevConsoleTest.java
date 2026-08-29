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
package org.apache.camel.impl.console;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * SqlQueryDevConsole needs a real javax.sql.DataSource to execute anything; camel-console has no JDBC driver test
 * dependency, so these tests exercise the option-validation and DataSource-resolution error paths only.
 */
public class SqlQueryDevConsoleTest extends ContextTestSupport {

    @Test
    public void testSqlQueryConsoleNoSql() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("sql-query");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("sql-query", con.getId());

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertEquals("error", out.getString("status"));
        Assertions.assertEquals("No SQL query provided", out.getString("message"));
    }

    @Test
    public void testSqlQueryConsoleNoDataSource() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("sql-query");

        Map<String, Object> options = new HashMap<>();
        options.put(SqlQueryDevConsole.SQL, "SELECT 1");

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON, options);
        Assertions.assertEquals("error", out.getString("status"));
        Assertions.assertEquals("No DataSource found in registry", out.getString("message"));
    }

    @Test
    public void testSqlQueryConsoleUpdateRowNoTable() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("sql-query");

        Map<String, Object> options = new HashMap<>();
        options.put(SqlQueryDevConsole.ACTION_TYPE, "update-row");

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON, options);
        Assertions.assertEquals("error", out.getString("status"));
        Assertions.assertEquals("No table name provided", out.getString("message"));
    }

    @Test
    public void testSqlQueryConsoleUpdateRowMissingValues() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("sql-query");

        Map<String, Object> options = new HashMap<>();
        options.put(SqlQueryDevConsole.ACTION_TYPE, "update-row");
        options.put(SqlQueryDevConsole.TABLE, "my_table");

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON, options);
        Assertions.assertEquals("error", out.getString("status"));
        Assertions.assertEquals("Missing primaryKeyValues or columnValues", out.getString("message"));
    }
}
