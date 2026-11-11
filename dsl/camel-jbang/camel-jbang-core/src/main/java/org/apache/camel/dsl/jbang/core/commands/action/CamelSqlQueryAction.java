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
package org.apache.camel.dsl.jbang.core.commands.action;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "sql", description = "Execute SQL query on a DataSource", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel cmd sql myApp --query=\"SELECT * FROM orders\"",
                           "  camel cmd sql myApp --query=\"SELECT * FROM users\" --datasource=myDS --max-rows=50",
                           "  camel cmd sql myApp --query=\"file:query.sql\"" })
public class CamelSqlQueryAction extends ActionBaseCommand {

    @Argument(required = true, description = "Name or pid of running Camel integration")
    String name;

    @Option(name = "query", aliases = { "sql" }, description = "The SQL query to execute, or file:<path> to load from a file",
            required = true)
    String query;

    @Option(name = "datasource", description = "Name of the DataSource bean (auto-detected if only one exists)")
    String datasource;

    @Option(name = "max-rows", description = "Maximum number of rows to return", defaultValue = "100")
    int maxRows = 100;

    @Option(name = "query-timeout", description = "Query timeout in seconds", defaultValue = "30")
    int queryTimeout = 30;

    @Option(name = "json", hasValue = false, description = "Output in JSON Format")
    boolean jsonOutput;

    public CamelSqlQueryAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = findOne(name);

        // the query has its own timeout, so wait a little longer for the answer
        ActionResult result = ActionClient.execute(process, "sql-query", root -> {
            root.put("sql", query);
            if (datasource != null) {
                root.put("datasource", datasource);
            }
            root.put("maxRows", maxRows);
            root.put("queryTimeout", queryTimeout);
        }, (queryTimeout + 10) * 1000L);
        JsonObject jo = result.orThrow();
        if (jsonOutput) {
            // the columns and rows, or the update count, as the integration answers
            JsonOutput.print(printer(), jo);
            return 0;
        }
        if (jo.containsKey("updateCount")) {
            int updateCount = jo.getInteger("updateCount");
            long elapsed = jo.getLongOrDefault("elapsed", 0);
            printer().println("Update count: " + updateCount);
            printer().println("Elapsed: " + elapsed + "ms");
            return 0;
        }

        JsonArray columns = jo.getCollection("columns");
        JsonArray rows = jo.getCollection("rows");
        if (columns == null || rows == null) {
            printer().println("No result data");
            return 0;
        }

        // compute column widths
        String[] colNames = new String[columns.size()];
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            JsonObject col = (JsonObject) columns.get(i);
            colNames[i] = col.getString("name");
            widths[i] = colNames[i].length();
        }
        for (Object rowObj : rows) {
            JsonObject row = (JsonObject) rowObj;
            for (int i = 0; i < colNames.length; i++) {
                Object val = row.get(colNames[i]);
                int len = val != null ? String.valueOf(val).length() : 4;
                widths[i] = Math.max(widths[i], len);
            }
        }

        // print header
        StringBuilder header = new StringBuilder();
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < colNames.length; i++) {
            if (i > 0) {
                header.append(" | ");
                separator.append("-+-");
            }
            header.append(String.format("%-" + widths[i] + "s", colNames[i]));
            separator.append("-".repeat(widths[i]));
        }
        printer().println(header.toString());
        printer().println(separator.toString());

        // print rows
        for (Object rowObj : rows) {
            JsonObject row = (JsonObject) rowObj;
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < colNames.length; i++) {
                if (i > 0) {
                    line.append(" | ");
                }
                Object val = row.get(colNames[i]);
                String s = val != null ? String.valueOf(val) : "null";
                line.append(String.format("%-" + widths[i] + "s", s));
            }
            printer().println(line.toString());
        }

        int rowCount = jo.getIntegerOrDefault("rowCount", rows.size());
        boolean truncated = jo.getBooleanOrDefault("truncated", false);
        long elapsed = jo.getLongOrDefault("elapsed", 0);
        printer().println();
        printer().println(rowCount + " row(s)" + (truncated ? " (truncated)" : "") + " in " + elapsed + "ms");

        return 0;
    }
}
