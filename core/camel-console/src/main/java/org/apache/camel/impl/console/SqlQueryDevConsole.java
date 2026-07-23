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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "sql-query", displayName = "SQL Query", description = "Execute SQL queries on DataSource beans")
@Configurer(extended = true)
public class SqlQueryDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "The SQL query to execute",
              javaType = "java.lang.String")
    public static final String SQL = "sql";

    @Metadata(label = "query", description = "Name of the DataSource bean in the registry (auto-detected if only one exists)",
              javaType = "java.lang.String")
    public static final String DATASOURCE = "datasource";

    @Metadata(label = "query", description = "Maximum number of rows to return",
              defaultValue = "100", javaType = "java.lang.Integer")
    public static final String MAX_ROWS = "maxRows";

    @Metadata(label = "query", description = "Query timeout in seconds",
              defaultValue = "30", javaType = "java.lang.Integer")
    public static final String QUERY_TIMEOUT = "queryTimeout";

    @Metadata(label = "query", description = "Action type: query (default) or update-row",
              defaultValue = "query", javaType = "java.lang.String", enums = "query,update-row")
    public static final String ACTION_TYPE = "actionType";

    @Metadata(label = "query", description = "Table name for update-row action",
              javaType = "java.lang.String")
    public static final String TABLE = "table";

    @Metadata(label = "query", description = "Primary key column-value pairs as JSON string for update-row action",
              javaType = "java.lang.String")
    public static final String PRIMARY_KEY_VALUES = "primaryKeyValues";

    @Metadata(label = "query", description = "Changed column-value pairs as JSON string for update-row action",
              javaType = "java.lang.String")
    public static final String COLUMN_VALUES = "columnValues";

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

        String sql = optionString(options, SQL);
        if (sql == null || sql.isBlank()) {
            sb.append("No SQL query provided\n");
            return sb.toString();
        }

        String dsName = optionString(options, DATASOURCE);
        int maxRows = optionInt(options, MAX_ROWS, defaultMaxRows);
        int queryTimeout = optionInt(options, QUERY_TIMEOUT, defaultQueryTimeout);

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
        String actionType = optionString(options, ACTION_TYPE);
        if ("update-row".equals(actionType)) {
            return doUpdateRow(options);
        }
        return doQuery(options);
    }

    private JsonObject doQuery(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        String sql = optionString(options, SQL);
        if (sql == null || sql.isBlank()) {
            root.put("status", "error");
            root.put("message", "No SQL query provided");
            return root;
        }

        String dsName = optionString(options, DATASOURCE);
        int maxRows = optionInt(options, MAX_ROWS, defaultMaxRows);
        int queryTimeout = optionInt(options, QUERY_TIMEOUT, defaultQueryTimeout);

        DataSource ds = resolveDataSource(dsName, root);
        if (ds == null) {
            return root;
        }
        String resolvedName = root.getString("datasource");

        StopWatch watch = new StopWatch();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.setMaxRows(maxRows);
            stmt.setQueryTimeout(queryTimeout);

            boolean hasResultSet = stmt.execute(sql);
            long elapsed = watch.taken();

            root.put("status", "success");
            root.put("elapsed", elapsed);

            if (hasResultSet) {
                // read all data and metadata from ResultSet first, then close it
                // before any DatabaseMetaData calls (some drivers only support one
                // open ResultSet per connection)
                String singleTable = null;
                String catalog = null;
                String schema = null;
                JsonArray columns = new JsonArray();
                JsonArray rows = new JsonArray();
                int rowCount = 0;
                String[] colLabels;

                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    colLabels = new String[colCount];

                    // detect single-table query for editability
                    boolean allSameTable = true;
                    for (int i = 1; i <= colCount; i++) {
                        colLabels[i - 1] = meta.getColumnLabel(i);
                        String tn = meta.getTableName(i);
                        if (tn == null || tn.isEmpty()) {
                            allSameTable = false;
                        } else if (singleTable == null) {
                            singleTable = tn;
                            catalog = meta.getCatalogName(i);
                            schema = meta.getSchemaName(i);
                        } else if (!singleTable.equalsIgnoreCase(tn)) {
                            allSameTable = false;
                        }
                    }
                    if (!allSameTable) {
                        singleTable = null;
                    }

                    for (int i = 1; i <= colCount; i++) {
                        JsonObject col = new JsonObject();
                        col.put("name", meta.getColumnLabel(i));
                        col.put("type", meta.getColumnTypeName(i));
                        columns.add(col);
                    }

                    while (rs.next()) {
                        JsonObject row = new JsonObject();
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            if (val == null) {
                                row.put(colLabels[i - 1], null);
                            } else if (val instanceof Number n) {
                                row.put(colLabels[i - 1], n);
                            } else if (val instanceof Boolean b) {
                                row.put(colLabels[i - 1], b);
                            } else {
                                row.put(colLabels[i - 1], String.valueOf(val));
                            }
                        }
                        rows.add(row);
                        rowCount++;
                    }
                }

                // ResultSet is now closed — safe to query DatabaseMetaData
                Set<String> pkColumns = new LinkedHashSet<>();
                if (singleTable != null) {
                    try {
                        DatabaseMetaData dbMeta = conn.getMetaData();
                        try (ResultSet pkRs = dbMeta.getPrimaryKeys(
                                catalog != null && !catalog.isEmpty() ? catalog : null,
                                schema != null && !schema.isEmpty() ? schema : null,
                                singleTable)) {
                            while (pkRs.next()) {
                                pkColumns.add(pkRs.getString("COLUMN_NAME"));
                            }
                        }
                    } catch (Exception e) {
                        // PK lookup failed — not editable
                    }
                }

                // annotate columns with PK info
                if (singleTable != null && !pkColumns.isEmpty()) {
                    for (int i = 0; i < columns.size(); i++) {
                        JsonObject col = (JsonObject) columns.get(i);
                        col.put("primaryKey", pkColumns.contains(col.getString("name")));
                    }
                    root.put("tableName", singleTable);
                    JsonArray pkArr = new JsonArray();
                    pkColumns.forEach(pkArr::add);
                    root.put("primaryKeys", pkArr);
                    root.put("editable", true);
                }

                root.put("columns", columns);
                root.put("rows", rows);
                root.put("rowCount", rowCount);
                root.put("truncated", rowCount >= maxRows);
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

    private JsonObject doUpdateRow(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        String tableName = optionString(options, TABLE);
        if (tableName == null || tableName.isBlank()) {
            root.put("status", "error");
            root.put("message", "No table name provided");
            return root;
        }

        String pkJson = optionString(options, PRIMARY_KEY_VALUES);
        String colJson = optionString(options, COLUMN_VALUES);
        if (pkJson == null || colJson == null) {
            root.put("status", "error");
            root.put("message", "Missing primaryKeyValues or columnValues");
            return root;
        }

        JsonObject pkValues;
        JsonObject colValues;
        try {
            pkValues = (JsonObject) Jsoner.deserialize(pkJson);
            colValues = (JsonObject) Jsoner.deserialize(colJson);
        } catch (Exception e) {
            root.put("status", "error");
            root.put("message", "Invalid JSON: " + e.getMessage());
            return root;
        }

        if (colValues.isEmpty()) {
            root.put("status", "error");
            root.put("message", "No columns to update");
            return root;
        }

        String dsName = optionString(options, DATASOURCE);
        DataSource ds = resolveDataSource(dsName, root);
        if (ds == null) {
            return root;
        }

        // build UPDATE table SET col1=?, col2=? WHERE pk1=? AND pk2=?
        List<String> setCols = new ArrayList<>(colValues.keySet());
        List<String> pkCols = new ArrayList<>(pkValues.keySet());

        StringBuilder sb = new StringBuilder("UPDATE ");
        sb.append(tableName).append(" SET ");
        for (int i = 0; i < setCols.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(setCols.get(i)).append(" = ?");
        }
        sb.append(" WHERE ");
        for (int i = 0; i < pkCols.size(); i++) {
            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append(pkCols.get(i)).append(" = ?");
        }

        StopWatch watch = new StopWatch();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {

            int idx = 1;
            for (String col : setCols) {
                setParameter(ps, idx++, colValues.get(col));
            }
            for (String col : pkCols) {
                setParameter(ps, idx++, pkValues.get(col));
            }

            int updateCount = ps.executeUpdate();
            long elapsed = watch.taken();
            root.put("status", "success");
            root.put("elapsed", elapsed);
            root.put("updateCount", updateCount);
        } catch (Exception e) {
            long elapsed = watch.taken();
            root.put("status", "error");
            root.put("elapsed", elapsed);
            root.put("message", e.getMessage());
        }

        return root;
    }

    private DataSource resolveDataSource(String dsName, JsonObject root) {
        if (dsName != null && !dsName.isBlank()) {
            DataSource ds = getCamelContext().getRegistry().lookupByNameAndType(dsName, DataSource.class);
            if (ds == null) {
                root.put("status", "error");
                root.put("message", String.format("DataSource '%s' not found in registry", dsName));
                return null;
            }
            root.put("datasource", dsName);
            return ds;
        }

        Map<String, DataSource> all = getCamelContext().getRegistry().findByTypeWithName(DataSource.class);
        if (all.isEmpty()) {
            root.put("status", "error");
            root.put("message", "No DataSource found in registry");
            return null;
        }
        if (all.size() > 1) {
            root.put("status", "error");
            root.put("message", "Multiple DataSources found, specify one: " + String.join(", ", all.keySet()));
            JsonArray names = new JsonArray();
            all.keySet().forEach(names::add);
            root.put("availableDataSources", names);
            return null;
        }
        Map.Entry<String, DataSource> single = all.entrySet().iterator().next();
        root.put("datasource", single.getKey());
        return single.getValue();
    }

    private static void setParameter(PreparedStatement ps, int index, Object value) throws Exception {
        if (value == null) {
            ps.setNull(index, java.sql.Types.NULL);
        } else if (value instanceof Number n) {
            if (value instanceof Integer || value instanceof Long) {
                ps.setLong(index, n.longValue());
            } else {
                ps.setDouble(index, n.doubleValue());
            }
        } else if (value instanceof Boolean b) {
            ps.setBoolean(index, b);
        } else {
            String s = String.valueOf(value);
            if ("null".equalsIgnoreCase(s)) {
                ps.setNull(index, java.sql.Types.NULL);
            } else if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
                ps.setBoolean(index, Boolean.parseBoolean(s));
            } else {
                // try numeric types so the JDBC driver gets the right type
                try {
                    ps.setLong(index, Long.parseLong(s));
                } catch (NumberFormatException e1) {
                    try {
                        ps.setDouble(index, Double.parseDouble(s));
                    } catch (NumberFormatException e2) {
                        ps.setString(index, s);
                    }
                }
            }
        }
    }

}
