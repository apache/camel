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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.SynchronizationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcProducer.class);

    private final DataSource dataSource;
    private final ConnectionStrategy connectionStrategy;
    private final int readSize;
    private final Map<String, Object> parameters;

    public JdbcProducer(JdbcEndpoint endpoint, DataSource dataSource, ConnectionStrategy connectionStrategy,
                        int readSize, Map<String, Object> parameters) {
        super(endpoint);
        this.dataSource = dataSource;
        this.connectionStrategy = connectionStrategy;
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
    @Override
    public void process(Exchange exchange) throws Exception {
        if (getEndpoint().isResetAutoCommit()) {
            processingSqlBySettingAutoCommit(exchange);
        } else {
            processingSqlWithoutSettingAutoCommit(exchange);
        }
    }

    private void processingSqlBySettingAutoCommit(Exchange exchange) throws Exception {
        String sql = exchange.getIn().getBody(String.class);
        Connection conn = null;
        boolean autoCommit = false;
        boolean shouldCloseResources = true;

        try {
            conn = connectionStrategy.getConnection(dataSource);
            autoCommit = conn.getAutoCommit();
            if (autoCommit) {
                conn.setAutoCommit(false);
            }

            shouldCloseResources = createAndExecuteSqlStatement(exchange, sql, conn);

            conn.commit();
        } catch (Exception e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (Exception sqle) {
                LOG.warn("Error occurred during JDBC rollback. This exception will be ignored.", sqle);
            }
            throw e;
        } finally {
            if (shouldCloseResources) {
                resetAutoCommit(conn, autoCommit);
                closeQuietly(conn);
            } else {
                final Connection finalConn = conn;
                final boolean finalAutoCommit = autoCommit;
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        resetAutoCommit(finalConn, finalAutoCommit);
                        closeQuietly(finalConn);
                    }

                    @Override
                    public int getOrder() {
                        return LOWEST + 200;
                    }
                });
            }
        }
    }

    private void processingSqlWithoutSettingAutoCommit(Exchange exchange) throws Exception {
        String sql = exchange.getIn().getBody(String.class);
        Connection conn = null;
        boolean shouldCloseResources = true;

        try {
            conn = connectionStrategy.getConnection(dataSource);
            shouldCloseResources = createAndExecuteSqlStatement(exchange, sql, conn);
        } finally {
            if (shouldCloseResources && !connectionStrategy.isConnectionTransactional(conn, dataSource)) {
                closeQuietly(conn);
            } else {
                final Connection finalConn = conn;
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        closeQuietly(finalConn);
                    }

                    @Override
                    public int getOrder() {
                        return LOWEST + 200;
                    }
                });
            }
        }
    }

    private boolean createAndExecuteSqlStatement(Exchange exchange, String sql, Connection conn) throws Exception {
        if (getEndpoint().isUseHeadersAsParameters()) {
            return doCreateAndExecuteSqlStatementWithHeaders(exchange, sql, conn);
        } else {
            return doCreateAndExecuteSqlStatement(exchange, sql, conn);
        }
    }

    private boolean doCreateAndExecuteSqlStatementWithHeaders(Exchange exchange, String sql, Connection conn) throws Exception {
        PreparedStatement ps = null;
        ResultSet rs = null;
        boolean shouldCloseResources = true;

        try {
            final String preparedQuery
                    = getEndpoint().getPrepareStatementStrategy().prepareQuery(sql, getEndpoint().isAllowNamedParameters());

            boolean shouldRetrieveGeneratedKeys
                    = exchange.getIn().getHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, false, Boolean.class);

            if (shouldRetrieveGeneratedKeys) {
                Object expectedGeneratedColumns = exchange.getIn().getHeader(JdbcConstants.JDBC_GENERATED_COLUMNS);
                if (expectedGeneratedColumns == null) {
                    ps = conn.prepareStatement(preparedQuery, Statement.RETURN_GENERATED_KEYS);
                } else if (expectedGeneratedColumns instanceof String[]) {
                    ps = conn.prepareStatement(preparedQuery, (String[]) expectedGeneratedColumns);
                } else if (expectedGeneratedColumns instanceof int[]) {
                    ps = conn.prepareStatement(preparedQuery, (int[]) expectedGeneratedColumns);
                } else {
                    throw new IllegalArgumentException(
                            "Header specifying expected returning columns isn't an instance of String[] or int[] but "
                                                       + expectedGeneratedColumns.getClass());
                }
            } else {
                ps = conn.prepareStatement(preparedQuery);
            }

            bindParameters(exchange, ps);

            int expectedCount = ps.getParameterMetaData().getParameterCount();

            if (expectedCount > 0) {
                Iterator<?> it = getEndpoint().getPrepareStatementStrategy()
                        .createPopulateIterator(sql, preparedQuery, expectedCount, exchange, exchange.getIn().getBody());
                getEndpoint().getPrepareStatementStrategy().populateStatement(ps, it, expectedCount);
            }

            LOG.debug("Executing JDBC PreparedStatement: {}", sql);

            boolean stmtExecutionResult = ps.execute();
            if (stmtExecutionResult) {
                rs = ps.getResultSet();
                shouldCloseResources = setResultSet(exchange, conn, rs);
            } else {
                int updateCount = ps.getUpdateCount();
                // and then set the new header
                exchange.getMessage().setHeader(JdbcConstants.JDBC_UPDATE_COUNT, updateCount);
            }

            if (shouldRetrieveGeneratedKeys) {
                setGeneratedKeys(exchange, conn, ps.getGeneratedKeys());
            }
        } finally {
            if (shouldCloseResources) {
                closeQuietly(rs);
                closeQuietly(ps);
            } else {
                final Statement finalPs = ps;
                final ResultSet finalRs = rs;
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        closeQuietly(finalRs);
                        closeQuietly(finalPs);
                    }

                    @Override
                    public int getOrder() {
                        // Make sure it happens before close Connection.
                        return LOWEST + 100;
                    }
                });
            }
        }
        return shouldCloseResources;
    }

    private boolean doCreateAndExecuteSqlStatement(Exchange exchange, String sql, Connection conn) throws Exception {

        Statement stmt = null;
        ResultSet rs = null;
        boolean shouldCloseResources = true;

        try {
            stmt = conn.createStatement();
            bindParameters(exchange, stmt);

            LOG.debug("Executing JDBC Statement: {}", sql);

            boolean shouldRetrieveGeneratedKeys
                    = exchange.getIn().getHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, false, Boolean.class);

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
                shouldCloseResources = setResultSet(exchange, conn, rs);
            } else {
                int updateCount = stmt.getUpdateCount();
                // and then set the new header
                exchange.getMessage().setHeader(JdbcConstants.JDBC_UPDATE_COUNT, updateCount);
            }

            if (shouldRetrieveGeneratedKeys) {
                setGeneratedKeys(exchange, conn, stmt.getGeneratedKeys());
            }
        } finally {
            if (shouldCloseResources) {
                closeQuietly(rs);
                closeQuietly(stmt);
            } else {
                final Statement finalStmt = stmt;
                final ResultSet finalRs = rs;
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        closeQuietly(finalRs);
                        closeQuietly(finalStmt);
                    }

                    @Override
                    public int getOrder() {
                        // Make sure it happens before close Connection.
                        return LOWEST + 100;
                    }
                });
            }
        }
        return shouldCloseResources;
    }

    private void bindParameters(Exchange exchange, Statement stmt) {
        if (parameters != null && !parameters.isEmpty()) {
            Map<String, Object> copy = new HashMap<>(parameters);
            PropertyBindingSupport.bindProperties(exchange.getContext(), stmt, copy);
        }
    }

    private void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                if (!rs.isClosed()) {
                    rs.close();
                }
            } catch (Exception sqle) {
                LOG.debug("Error by closing result set", sqle);
            }
        }
    }

    private void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try {
                if (!stmt.isClosed()) {
                    stmt.close();
                }
            } catch (Exception sqle) {
                LOG.debug("Error by closing statement", sqle);
            }
        }
    }

    private void resetAutoCommit(Connection con, boolean autoCommit) {
        if (con != null) {
            try {
                con.setAutoCommit(autoCommit);
            } catch (Exception sqle) {
                LOG.debug("Error by resetting auto commit to its original value", sqle);
            }
        }
    }

    private void closeQuietly(Connection con) {
        if (con != null) {
            try {
                if (!con.isClosed()) {
                    con.close();
                }
            } catch (Exception sqle) {
                LOG.debug("Error by closing connection", sqle);
            }
        }
    }

    /**
     * Sets the generated if any to the Exchange in headers : - {@link JdbcConstants#JDBC_GENERATED_KEYS_ROW_COUNT} :
     * the row count of generated keys - {@link JdbcConstants#JDBC_GENERATED_KEYS_DATA} : the generated keys data
     *
     * @param exchange      The exchange where to store the generated keys
     * @param conn          Current JDBC connection
     * @param generatedKeys The result set containing the generated keys
     */
    protected void setGeneratedKeys(Exchange exchange, Connection conn, ResultSet generatedKeys) throws SQLException {
        if (generatedKeys != null) {
            ResultSetIterator iterator = new ResultSetIterator(
                    conn, generatedKeys, getEndpoint().isUseJDBC4ColumnNameAndLabelSemantics(),
                    getEndpoint().isUseGetBytesForBlob());
            List<Map<String, Object>> data = extractRows(iterator);

            exchange.getMessage().setHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT, data.size());
            exchange.getMessage().setHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA, data);
        }
    }

    /**
     * Sets the result from the ResultSet to the Exchange as its OUT body.
     *
     * @return whether to close resources
     */
    protected boolean setResultSet(Exchange exchange, Connection conn, ResultSet rs) throws SQLException {
        boolean answer = true;

        ResultSetIterator iterator = new ResultSetIterator(
                conn, rs, getEndpoint().isUseJDBC4ColumnNameAndLabelSemantics(), getEndpoint().isUseGetBytesForBlob());

        JdbcOutputType outputType = getEndpoint().getOutputType();
        exchange.getMessage().setHeader(JdbcConstants.JDBC_COLUMN_NAMES, iterator.getColumnNames());
        if (outputType == JdbcOutputType.StreamList) {
            exchange.getMessage()
                    .setBody(new StreamListIterator(
                            getEndpoint().getCamelContext(), getEndpoint().getOutputClass(), getEndpoint().getBeanRowMapper(),
                            iterator));
            // do not close resources as we are in streaming mode
            answer = false;
        } else if (outputType == JdbcOutputType.SelectList) {
            List<?> list = extractRows(iterator);
            exchange.getMessage().setHeader(JdbcConstants.JDBC_ROW_COUNT, list.size());
            exchange.getMessage().setBody(list);
        } else if (outputType == JdbcOutputType.SelectOne) {
            exchange.getMessage().setBody(extractSingleRow(iterator));
        }

        return answer;
    }

    @SuppressWarnings("unchecked")
    private List extractRows(ResultSetIterator iterator) throws SQLException {
        List result = new ArrayList();
        int maxRowCount = readSize == 0 ? Integer.MAX_VALUE : readSize;
        for (int i = 0; iterator.hasNext() && i < maxRowCount; i++) {
            Map<String, Object> row = iterator.next();
            Object value;
            if (getEndpoint().getOutputClass() != null) {
                value = JdbcHelper.newBeanInstance(getEndpoint().getCamelContext(), getEndpoint().getOutputClass(),
                        getEndpoint().getBeanRowMapper(), row);
            } else {
                value = row;
            }
            result.add(value);
        }
        return result;
    }

    private Object extractSingleRow(ResultSetIterator iterator) throws SQLException {
        if (!iterator.hasNext()) {
            return null;
        }

        Map<String, Object> row = iterator.next();
        if (iterator.hasNext()) {
            throw new SQLDataException("Query result not unique for outputType=SelectOne.");
        } else if (getEndpoint().getOutputClass() != null) {
            return JdbcHelper.newBeanInstance(getEndpoint().getCamelContext(), getEndpoint().getOutputClass(),
                    getEndpoint().getBeanRowMapper(), row);
        } else if (row.size() == 1) {
            return row.values().iterator().next();
        } else {
            return row;
        }
    }
}
