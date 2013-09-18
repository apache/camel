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
package org.apache.camel.component.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version
 */
public class JdbcProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcProducer.class);
    private DataSource dataSource;
    private int readSize;
    private Map<String, Object> parameters;

    public JdbcProducer(JdbcEndpoint endpoint, DataSource dataSource, int readSize, Map<String, Object> parameters) throws Exception {
        super(endpoint);
        this.dataSource = dataSource;
        this.readSize = readSize;
        this.parameters = parameters;
    }

    @Override
    public JdbcEndpoint getEndpoint() {
        return (JdbcEndpoint) super.getEndpoint();
    }

    /**
     * Execute sql of exchange and set results on output
     */
    public void process(Exchange exchange) throws Exception {
        if (getEndpoint().isResetAutoCommit()) {
            processingSqlBySettingAutoCommit(exchange);
        } else {
            processingSqlWithoutSettingAutoCommit(exchange);
        }
        // populate headers
        exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
    }

    private void processingSqlBySettingAutoCommit(Exchange exchange) throws Exception {
        String sql = exchange.getIn().getBody(String.class);
        Connection conn = null;
        Boolean autoCommit = null;
        try {
            conn = dataSource.getConnection();
            autoCommit = conn.getAutoCommit();
            if (autoCommit) {
                conn.setAutoCommit(false);
            }

            createAndExecuteSqlStatement(exchange, sql, conn);

            conn.commit();
        } catch (Exception e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException sqle) {
                LOG.warn("Error occurred during jdbc rollback. This exception will be ignored.", sqle);
            }
            throw e;
        } finally {
            resetAutoCommit(conn, autoCommit);
            closeQuietly(conn);
        }
    }

    private void processingSqlWithoutSettingAutoCommit(Exchange exchange) throws Exception {
        String sql = exchange.getIn().getBody(String.class);
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            createAndExecuteSqlStatement(exchange, sql, conn);
        } finally {
            closeQuietly(conn);
        }
    }

    private void createAndExecuteSqlStatement(Exchange exchange, String sql, Connection conn) throws Exception {
        if (getEndpoint().isUseHeadersAsParameters()) {
            doCreateAndExecuteSqlStatementWithHeaders(exchange, sql, conn);
        } else {
            doCreateAndExecuteSqlStatement(exchange, sql, conn);
        }
    }

    private void doCreateAndExecuteSqlStatementWithHeaders(Exchange exchange, String sql, Connection conn) throws Exception {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            final String preparedQuery = getEndpoint().getPrepareStatementStrategy().prepareQuery(sql, getEndpoint().isAllowNamedParameters());
            ps = conn.prepareStatement(preparedQuery);
            int expectedCount = ps.getParameterMetaData().getParameterCount();

            if (expectedCount > 0) {
                Iterator<?> it = getEndpoint().getPrepareStatementStrategy().createPopulateIterator(sql, preparedQuery, expectedCount, exchange, exchange.getIn().getBody());
                getEndpoint().getPrepareStatementStrategy().populateStatement(ps, it, expectedCount);
            }

            LOG.debug("Executing JDBC PreparedStatement: {}", sql);

            boolean stmtExecutionResult = ps.execute();
            if (stmtExecutionResult) {
                rs = ps.getResultSet();
                setResultSet(exchange, rs);
            } else {
                int updateCount = ps.getUpdateCount();
                exchange.getOut().setHeader(JdbcConstants.JDBC_UPDATE_COUNT, updateCount);
            }
        } finally {
            closeQuietly(rs);
            closeQuietly(ps);
        }
    }

    private void doCreateAndExecuteSqlStatement(Exchange exchange, String sql, Connection conn) throws Exception {
        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.createStatement();

            if (parameters != null && !parameters.isEmpty()) {
                IntrospectionSupport.setProperties(stmt, parameters);
            }

            LOG.debug("Executing JDBC Statement: {}", sql);

            Boolean shouldRetrieveGeneratedKeys =
                    exchange.getIn().getHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, false, Boolean.class);

            boolean stmtExecutionResult;
            if (shouldRetrieveGeneratedKeys) {
                Object expectedGeneratedColumns = exchange.getIn().getHeader(JdbcConstants.JDBC_GENERATED_COLUMNS);
                if (expectedGeneratedColumns == null) {
                    stmtExecutionResult = stmt.execute(sql, Statement.RETURN_GENERATED_KEYS);
                } else if (expectedGeneratedColumns instanceof String[]) {
                    stmtExecutionResult = stmt.execute(sql, (String[]) expectedGeneratedColumns);
                } else if (expectedGeneratedColumns instanceof int[]) {
                    stmtExecutionResult = stmt.execute(sql, (int[]) expectedGeneratedColumns);
                } else {
                    throw new IllegalArgumentException(
                            "Header specifying expected returning columns isn't an instance of String[] or int[] but "
                                    + expectedGeneratedColumns.getClass());
                }
            } else {
                stmtExecutionResult = stmt.execute(sql);
            }

            if (stmtExecutionResult) {
                rs = stmt.getResultSet();
                setResultSet(exchange, rs);
            } else {
                int updateCount = stmt.getUpdateCount();
                exchange.getOut().setHeader(JdbcConstants.JDBC_UPDATE_COUNT, updateCount);
            }

            if (shouldRetrieveGeneratedKeys) {
                setGeneratedKeys(exchange, stmt.getGeneratedKeys());
            }
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }

    private void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException sqle) {
                LOG.warn("Error by closing result set: " + sqle, sqle);
            }
        }
    }

    private void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException sqle) {
                LOG.warn("Error by closing statement: " + sqle, sqle);
            }
        }
    }

    private void resetAutoCommit(Connection con, Boolean autoCommit) {
        if (con != null && autoCommit != null) {
            try {
                con.setAutoCommit(autoCommit);
            } catch (SQLException sqle) {
                LOG.warn("Error by resetting auto commit to its original value: " + sqle, sqle);
            }
        }
    }

    private void closeQuietly(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException sqle) {
                LOG.warn("Error by closing connection: " + sqle, sqle);
            }
        }
    }


    /**
     * Sets the generated if any to the Exchange in headers :
     * - {@link JdbcConstants#JDBC_GENERATED_KEYS_ROW_COUNT} : the row count of generated keys
     * - {@link JdbcConstants#JDBC_GENERATED_KEYS_DATA} : the generated keys data
     *
     * @param exchange      The exchange where to store the generated keys
     * @param generatedKeys The result set containing the generated keys
     */
    protected void setGeneratedKeys(Exchange exchange, ResultSet generatedKeys) throws SQLException {
        if (generatedKeys != null) {
            List<Map<String, Object>> data = extractResultSetData(generatedKeys);

            exchange.getOut().setHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT, data.size());
            exchange.getOut().setHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA, data);
        }
    }

    /**
     * Sets the result from the ResultSet to the Exchange as its OUT body.
     */
    protected void setResultSet(Exchange exchange, ResultSet rs) throws SQLException {
        JdbcOutputType outputType = getEndpoint().getOutputType();

        if (outputType == JdbcOutputType.SelectList) {
            List<Map<String, Object>> data = extractResultSetData(rs);
            exchange.getOut().setHeader(JdbcConstants.JDBC_ROW_COUNT, data.size());
            if (!data.isEmpty()) {
                exchange.getOut().setHeader(JdbcConstants.JDBC_COLUMN_NAMES, data.get(0).keySet());
            }
            exchange.getOut().setBody(data);
        } else if (outputType == JdbcOutputType.SelectOne) {
            Object obj = queryForObject(rs);
            exchange.getOut().setBody(obj);
        }
    }

    /**
     * Extract the result from the ResultSet
     *
     * @param rs rs produced by the SQL request
     * @return All the resulting rows containing each field of the ResultSet
     */
    protected List<Map<String, Object>> extractResultSetData(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();

        // should we use jdbc4 or jdbc3 semantics
        boolean jdbc4 = getEndpoint().isUseJDBC4ColumnNameAndLabelSemantics();

        int count = meta.getColumnCount();
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        int rowNumber = 0;
        while (rs.next() && (readSize == 0 || rowNumber < readSize)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (int i = 0; i < count; i++) {
                int columnNumber = i + 1;
                // use column label to get the name as it also handled SQL SELECT aliases
                String columnName;
                if (jdbc4) {
                    // jdbc 4 should use label to get the name
                    columnName = meta.getColumnLabel(columnNumber);
                } else {
                    // jdbc 3 uses the label or name to get the name
                    try {
                        columnName = meta.getColumnLabel(columnNumber);
                    } catch (SQLException e) {
                        columnName = meta.getColumnName(columnNumber);
                    }
                }
                // use index based which should be faster
                int columnType = meta.getColumnType(columnNumber);
                if (columnType == Types.CLOB || columnType == Types.BLOB) {
                    row.put(columnName, rs.getString(columnNumber));
                } else {
                    row.put(columnName, rs.getObject(columnNumber));
                }
            }
            data.add(row);
            rowNumber++;
        }
        return data;
    }


    @SuppressWarnings("unchecked")
    protected Object queryForObject(ResultSet rs) throws SQLException {
        Object result = null;
        List<Map<String, Object>> data = extractResultSetData(rs);
        if (data.size() > 1) {
            throw new SQLDataException("Query result not unique for outputType=SelectOne. Got " + data.size() + " count instead.");
        } else if (data.size() == 1) {
            if (getEndpoint().getOutputClass() == null) {
                // Set content depend on number of column from query result
                Map<String, Object> row = data.get(0);
                if (row.size() == 1) {
                    result = row.values().iterator().next();
                } else {
                    result = row;
                }
            } else {
                Class<?> outputClzz = getEndpoint().getCamelContext().getClassResolver().resolveClass(getEndpoint().getOutputClass());
                Object answer = getEndpoint().getCamelContext().getInjector().newInstance(outputClzz);

                Map<String, Object> row = data.get(0);
                Map<String, Object> properties = new LinkedHashMap<String, Object>(data.size());

                // map row names using the bean row mapper
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    Object value = entry.getValue();
                    String name = getEndpoint().getBeanRowMapper().map(entry.getKey(), value);
                    properties.put(name, value);
                }
                try {
                    IntrospectionSupport.setProperties(answer, properties);
                } catch (Exception e) {
                    throw new SQLException("Error setting properties on output class " + outputClzz, e);
                }

                // check we could map all properties to the bean
                if (!properties.isEmpty()) {
                    throw new IllegalArgumentException("Cannot map all properties to bean of type " + outputClzz + ". There are " + properties.size() + " unmapped properties. " + properties);
                }
                return answer;
            }
        }

        // If data.size is zero, let result be null.
        return result;
    }

}
