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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "sql",
                     description = "Execute SQL query on a DataSource", sortOptions = false,
                     showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel cmd sql myApp --query=\"SELECT * FROM orders\"",
                             "  camel cmd sql myApp --query=\"SELECT * FROM users\" --datasource=myDS --max-rows=50",
                             "  camel cmd sql myApp --query=\"file:query.sql\"" })
public class CamelSqlQueryAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration",
                            arity = "1")
    String name;

    @CommandLine.Option(names = { "--query", "--sql" }, required = true,
                        description = "The SQL query to execute, or file:<path> to load from a file")
    String query;

    @CommandLine.Option(names = { "--datasource" },
                        description = "Name of the DataSource bean (auto-detected if only one exists)")
    String datasource;

    @CommandLine.Option(names = { "--max-rows" }, defaultValue = "100",
                        description = "Maximum number of rows to return")
    int maxRows = 100;

    @CommandLine.Option(names = { "--query-timeout" }, defaultValue = "30",
                        description = "Query timeout in seconds")
    int queryTimeout = 30;

    public CamelSqlQueryAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.size() != 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        }

        long pid = pids.get(0);

        Path outputFile = getOutputFile(Long.toString(pid));
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "sql-query");
        root.put("sql", query);
        if (datasource != null) {
            root.put("datasource", datasource);
        }
        root.put("maxRows", maxRows);
        root.put("queryTimeout", queryTimeout);

        Path actionFile = getActionFile(Long.toString(pid));
        Files.writeString(actionFile, root.toJson());

        JsonObject jo = getJsonObject(outputFile, (queryTimeout + 10) * 1000L);
        try {
            if (jo == null) {
                printer().println("Timeout waiting for SQL query response");
                return 1;
            }

            String status = jo.getString("status");
            if ("error".equals(status)) {
                printer().println("Error: " + jo.getString("message"));
                return 1;
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
        } finally {
            PathUtils.deleteFile(outputFile);
        }

        return 0;
    }
}
