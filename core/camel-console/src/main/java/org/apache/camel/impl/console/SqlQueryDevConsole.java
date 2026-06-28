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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "sql-query", displayName = "SQL Query", description = "Execute SQL queries on DataSource beans")
@Configurer(extended = true)
public class SqlQueryDevConsole extends AbstractDevConsole {

    /**
     * The SQL query to execute
     */
    public static final String SQL = "sql";

    /**
     * Name of the DataSource bean in the registry (auto-detected if only one exists)
     */
    public static final String DATASOURCE = "datasource";

    /**
     * Maximum number of rows to return
     */
    public static final String MAX_ROWS = "maxRows";

    /**
     * Query timeout in seconds
     */
    public static final String QUERY_TIMEOUT = "queryTimeout";

    @Metadata(defaultValue = "100",
              description = "Maximum number of rows to return from a query")
    private int defaultMaxRows = 100;

    @Metadata(defaultValue = "30",
              description = "Default query timeout in seconds")
    private int defaultQueryTimeout = 30;

    public SqlQueryDevConsole() {
        super("camel", "sql-query", "SQL Query", "Execute SQL queries on DataSource beans");
    }

    public int getDefaultMaxRows() {
        return defaultMaxRows;
    }

    public void setDefaultMaxRows(int defaultMaxRows) {
        this.defaultMaxRows = defaultMaxRows;
    }

    public int getDefaultQueryTimeout() {
        return defaultQueryTimeout;
    }

    public void setDefaultQueryTimeout(int defaultQueryTimeout) {
        this.defaultQueryTimeout = defaultQueryTimeout;
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        String sql = (String) options.get(SQL);
        if (sql == null || sql.isBlank()) {
            sb.append("No SQL query provided\n");
            return sb.toString();
        }

        String dsName = (String) options.get(DATASOURCE);
        int maxRows = parseIntOption(options, MAX_ROWS, defaultMaxRows);
        int queryTimeout = parseIntOption(options, QUERY_TIMEOUT, defaultQueryTimeout);

        DataSource ds;
        String resolvedName;
        if (dsName != null && !dsName.isBlank()) {
            ds = getCamelContext().getRegistry().lookupByNameAndType(dsName, DataSource.class);
            resolvedName = dsName;
            if (ds == null) {
                sb.append(String.format("DataSource '%s' not found in registry%n", dsName));
                return sb.toString();
            }
        } else {
            Map<String, DataSource> all = getCamelContext().getRegistry().findByTypeWithName(DataSource.class);
            if (all.isEmpty()) {
                sb.append("No DataSource found in registry\n");
                return sb.toString();
            }
            if (all.size() > 1) {
                sb.append(String.format("Multiple DataSources found: %s. Specify one with --datasource%n",
                        String.join(", ", all.keySet())));
                return sb.toString();
            }
            Map.Entry<String, DataSource> single = all.entrySet().iterator().next();
            ds = single.getValue();
            resolvedName = single.getKey();
        }

        StopWatch watch = new StopWatch();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.setMaxRows(maxRows);
            stmt.setQueryTimeout(queryTimeout);

            boolean hasResultSet = stmt.execute(sql);
            long elapsed = watch.taken();

            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    sb.append(String.format("DataSource: %s%n", resolvedName));
                    sb.append(String.format("Elapsed: %s%n%n", TimeUtils.printDuration(elapsed)));

                    // column headers
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) {
                            sb.append(" | ");
                        }
                        sb.append(meta.getColumnLabel(i));
                    }
                    sb.append("\n");

                    int rowCount = 0;
                    while (rs.next()) {
                        for (int i = 1; i <= colCount; i++) {
                            if (i > 1) {
                                sb.append(" | ");
                            }
                            Object val = rs.getObject(i);
                            sb.append(val != null ? String.valueOf(val) : "null");
                        }
                        sb.append("\n");
                        rowCount++;
                    }
                    sb.append(String.format("%n%d row(s)%n", rowCount));
                    if (rowCount >= maxRows) {
                        sb.append("(truncated)\n");
                    }
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                sb.append(String.format("DataSource: %s%n", resolvedName));
                sb.append(String.format("Update count: %d%n", updateCount));
                sb.append(String.format("Elapsed: %s%n", TimeUtils.printDuration(elapsed)));
            }
        } catch (Exception e) {
            long elapsed = watch.taken();
            sb.append(String.format("Error: %s%n", e.getMessage()));
            sb.append(String.format("Elapsed: %s%n", TimeUtils.printDuration(elapsed)));
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        String sql = (String) options.get(SQL);
        if (sql == null || sql.isBlank()) {
            root.put("status", "error");
            root.put("message", "No SQL query provided");
            return root;
        }

        String dsName = (String) options.get(DATASOURCE);
        int maxRows = parseIntOption(options, MAX_ROWS, defaultMaxRows);
        int queryTimeout = parseIntOption(options, QUERY_TIMEOUT, defaultQueryTimeout);

        DataSource ds;
        String resolvedName;
        if (dsName != null && !dsName.isBlank()) {
            ds = getCamelContext().getRegistry().lookupByNameAndType(dsName, DataSource.class);
            resolvedName = dsName;
            if (ds == null) {
                root.put("status", "error");
                root.put("message", String.format("DataSource '%s' not found in registry", dsName));
                return root;
            }
        } else {
            Map<String, DataSource> all = getCamelContext().getRegistry().findByTypeWithName(DataSource.class);
            if (all.isEmpty()) {
                root.put("status", "error");
                root.put("message", "No DataSource found in registry");
                return root;
            }
            if (all.size() > 1) {
                root.put("status", "error");
                root.put("message", "Multiple DataSources found, specify one: " + String.join(", ", all.keySet()));
                // include available names for the caller
                JsonArray names = new JsonArray();
                all.keySet().forEach(names::add);
                root.put("availableDataSources", names);
                return root;
            }
            Map.Entry<String, DataSource> single = all.entrySet().iterator().next();
            ds = single.getValue();
            resolvedName = single.getKey();
        }

        StopWatch watch = new StopWatch();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.setMaxRows(maxRows);
            stmt.setQueryTimeout(queryTimeout);

            boolean hasResultSet = stmt.execute(sql);
            long elapsed = watch.taken();

            root.put("status", "success");
            root.put("elapsed", elapsed);
            root.put("datasource", resolvedName);

            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    JsonArray columns = new JsonArray();
                    for (int i = 1; i <= colCount; i++) {
                        JsonObject col = new JsonObject();
                        col.put("name", meta.getColumnLabel(i));
                        col.put("type", meta.getColumnTypeName(i));
                        columns.add(col);
                    }
                    root.put("columns", columns);

                    JsonArray rows = new JsonArray();
                    int rowCount = 0;
                    while (rs.next()) {
                        JsonObject row = new JsonObject();
                        for (int i = 1; i <= colCount; i++) {
                            String colName = meta.getColumnLabel(i);
                            Object val = rs.getObject(i);
                            if (val == null) {
                                row.put(colName, null);
                            } else if (val instanceof Number n) {
                                row.put(colName, n);
                            } else if (val instanceof Boolean b) {
                                row.put(colName, b);
                            } else {
                                row.put(colName, String.valueOf(val));
                            }
                        }
                        rows.add(row);
                        rowCount++;
                    }
                    root.put("rows", rows);
                    root.put("rowCount", rowCount);
                    root.put("truncated", rowCount >= maxRows);
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                root.put("updateCount", updateCount);
            }
        } catch (Exception e) {
            long elapsed = watch.taken();
            root.put("status", "error");
            root.put("elapsed", elapsed);
            root.put("message", e.getMessage());
        }

        return root;
    }

    private static int parseIntOption(Map<String, Object> options, String key, int defaultValue) {
        Object val = options.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return defaultValue;
    }
}
