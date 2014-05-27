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
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import javax.sql.*;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.Synchronization;
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
        boolean shouldCloseResources = true;

        try {
            conn = dataSource.getConnection();
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
            } catch (SQLException sqle) {
                LOG.warn("Error occurred during jdbc rollback. This exception will be ignored.", sqle);
            }
            throw e;
        } finally {
            if (shouldCloseResources) {
                resetAutoCommit(conn, autoCommit);
                closeQuietly(conn);
            }
        }
    }

    private void processingSqlWithoutSettingAutoCommit(Exchange exchange) throws Exception {
        String sql = exchange.getIn().getBody(String.class);
        Connection conn = null;
        boolean shouldCloseResources = true;

        try {
            conn = dataSource.getConnection();
            shouldCloseResources = createAndExecuteSqlStatement(exchange, sql, conn);
        } finally {
            if (shouldCloseResources) {
                closeQuietly(conn);
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
            final String preparedQuery = getEndpoint().getPrepareStatementStrategy().prepareQuery(sql, getEndpoint().isAllowNamedParameters());

            Boolean shouldRetrieveGeneratedKeys = exchange.getIn().getHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, false, Boolean.class);

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
                            "Header specifying expected returning columns isn't an instance of String[] or int[] but " + expectedGeneratedColumns.getClass());
                }
            } else {
                ps = conn.prepareStatement(preparedQuery);
            }

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
                setResultSet(exchange, rs);
                shouldCloseResources = false;
            } else {
                int updateCount = ps.getUpdateCount();
                exchange.getOut().setHeader(JdbcConstants.JDBC_UPDATE_COUNT, updateCount);
            }

            if (shouldRetrieveGeneratedKeys) {
                setGeneratedKeys(exchange, ps.getGeneratedKeys());
            }
        } finally {
            if (shouldCloseResources) {
                closeQuietly(rs);
                closeQuietly(ps);
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

            if (parameters != null && !parameters.isEmpty()) {
                IntrospectionSupport.setProperties(stmt, parameters);
            }

            LOG.debug("Executing JDBC Statement: {}", sql);

            Boolean shouldRetrieveGeneratedKeys = exchange.getIn().getHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, false, Boolean.class);

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
                            "Header specifying expected returning columns isn't an instance of String[] or int[] but " + expectedGeneratedColumns.getClass());
                }
            } else {
                stmtExecutionResult = stmt.execute(sql);
            }

            if (stmtExecutionResult) {
                rs = stmt.getResultSet();
                setResultSet(exchange, rs);
                shouldCloseResources = false;
            } else {
                int updateCount = stmt.getUpdateCount();
                exchange.getOut().setHeader(JdbcConstants.JDBC_UPDATE_COUNT, updateCount);
            }

            if (shouldRetrieveGeneratedKeys) {
                setGeneratedKeys(exchange, stmt.getGeneratedKeys());
            }
        } finally {
            if (shouldCloseResources) {
                closeQuietly(rs);
                closeQuietly(stmt);
            }
        }
        return shouldCloseResources;
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
     * @param exchange The exchange where to store the generated keys
     * @param generatedKeys The result set containing the generated keys
     */
    protected void setGeneratedKeys(Exchange exchange, ResultSet generatedKeys) throws SQLException {
        if (generatedKeys != null) {
            ResultSetIterator iterator = new ResultSetIterator(generatedKeys, getEndpoint().isUseJDBC4ColumnNameAndLabelSemantics());
            List<Map<String, Object>> data = extractRows(iterator);

            exchange.getOut().setHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT, data.size());
            exchange.getOut().setHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA, data);
        }
    }

    /**
     * Sets the result from the ResultSet to the Exchange as its OUT body.
     */
    protected void setResultSet(Exchange exchange, ResultSet rs) throws SQLException {
        ResultSetIterator iterator = new ResultSetIterator(rs, getEndpoint().isUseJDBC4ColumnNameAndLabelSemantics());

        JdbcOutputType outputType = getEndpoint().getOutputType();
        exchange.getOut().setHeader(JdbcConstants.JDBC_COLUMN_NAMES, iterator.getColumnNames());
        if (outputType == JdbcOutputType.StreamList) {
            exchange.getOut().setBody(iterator);
            exchange.addOnCompletion(new ResultSetIteratorCompletion(iterator));
        } else if (outputType == JdbcOutputType.SelectList) {
            List<Map<String, Object>> list = extractRows(iterator);
            exchange.getOut().setHeader(JdbcConstants.JDBC_ROW_COUNT, list.size());
            exchange.getOut().setBody(list);
        } else if (outputType == JdbcOutputType.SelectOne) {
            exchange.getOut().setBody(extractSingleRow(iterator));
        }
    }

    private List<Map<String, Object>> extractRows(ResultSetIterator iterator) {
        try {
            List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
            int maxRowCount = readSize == 0 ? Integer.MAX_VALUE : readSize;
            for (int i = 0; iterator.hasNext() && i < maxRowCount; i++) {
                result.add(iterator.next());
            }
            return result;
        } finally {
            iterator.close();
        }
    }

    private Object extractSingleRow(ResultSetIterator iterator) throws SQLException {
        try {
            if (!iterator.hasNext()) {
                return null;
            }

            Map<String, Object> row = iterator.next();
            if (iterator.hasNext()) {
                throw new SQLDataException("Query result not unique for outputType=SelectOne.");
            } else if (getEndpoint().getOutputClass() != null) {
                return newBeanInstance(row);
            } else if (row.size() == 1) {
                return row.values().iterator().next();
            } else {
                return row;
            }
        } finally {
            iterator.close();
        }
    }

    private Object newBeanInstance(Map<String, Object> row) throws SQLException {
        Class<?> outputClass = getEndpoint().getCamelContext().getClassResolver().resolveClass(getEndpoint().getOutputClass());
        Object answer = getEndpoint().getCamelContext().getInjector().newInstance(outputClass);

        Map<String, Object> properties = new LinkedHashMap<String, Object>();

        // map row names using the bean row mapper
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            String name = getEndpoint().getBeanRowMapper().map(entry.getKey(), value);
            properties.put(name, value);
        }
        try {
            IntrospectionSupport.setProperties(answer, properties);
        } catch (Exception e) {
            throw new SQLException("Error setting properties on output class " + outputClass, e);
        }

        // check we could map all properties to the bean
        if (!properties.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot map all properties to bean of type " + outputClass + ". There are " + properties.size() + " unmapped properties. " + properties);
        }
        return answer;
    }

    private static final class ResultSetIteratorCompletion implements Synchronization {
        private final ResultSetIterator iterator;

        private ResultSetIteratorCompletion(ResultSetIterator iterator) {
            this.iterator = iterator;
        }

        @Override
        public void onComplete(Exchange exchange) {
            iterator.close();
        }

        @Override
        public void onFailure(Exchange exchange) {
            iterator.close();
        }
    }
}
