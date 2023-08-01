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
package org.apache.camel.component.sql;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;

import static org.springframework.jdbc.support.JdbcUtils.closeConnection;
import static org.springframework.jdbc.support.JdbcUtils.closeResultSet;
import static org.springframework.jdbc.support.JdbcUtils.closeStatement;

public class SqlProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SqlProducer.class);

    private static final Object EMPTY_RESULT = new Object();

    private final String query;
    private String resolvedQuery;
    private final JdbcTemplate jdbcTemplate;
    private final boolean batch;
    private final boolean alwaysPopulateStatement;
    private final SqlPrepareStatementStrategy sqlPrepareStatementStrategy;
    private final boolean useMessageBodyForSql;
    private int parametersCount;

    public SqlProducer(SqlEndpoint endpoint, String query, JdbcTemplate jdbcTemplate,
                       SqlPrepareStatementStrategy sqlPrepareStatementStrategy,
                       boolean batch, boolean alwaysPopulateStatement, boolean useMessageBodyForSql) {
        super(endpoint);
        this.jdbcTemplate = jdbcTemplate;
        this.sqlPrepareStatementStrategy = sqlPrepareStatementStrategy;
        this.query = query;
        this.batch = batch;
        this.alwaysPopulateStatement = alwaysPopulateStatement;
        this.useMessageBodyForSql = useMessageBodyForSql;
    }

    @Override
    public SqlEndpoint getEndpoint() {
        return (SqlEndpoint) super.getEndpoint();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (ResourceHelper.isClasspathUri(query)) {
            String placeholder = getEndpoint().isUsePlaceholder() ? getEndpoint().getPlaceholder() : null;
            resolvedQuery = SqlHelper.resolveQuery(getEndpoint().getCamelContext(), query, placeholder);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!ResourceHelper.isClasspathUri(query)) {
            String placeholder = getEndpoint().isUsePlaceholder() ? getEndpoint().getPlaceholder() : null;
            resolvedQuery = SqlHelper.resolveQuery(getEndpoint().getCamelContext(), query, placeholder);
        }
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final String sql;
        if (useMessageBodyForSql) {
            sql = exchange.getIn().getBody(String.class);
        } else {
            String queryHeader = exchange.getIn().getHeader(SqlConstants.SQL_QUERY, String.class);
            if (queryHeader != null) {
                String placeholder = getEndpoint().isUsePlaceholder() ? getEndpoint().getPlaceholder() : null;
                sql = SqlHelper.resolvePlaceholders(queryHeader, placeholder);
            } else {
                sql = resolvedQuery;
            }
        }
        final String preparedQuery
                = sqlPrepareStatementStrategy.prepareQuery(sql, getEndpoint().isAllowNamedParameters(), exchange);

        final Boolean shouldRetrieveGeneratedKeys
                = exchange.getIn().getHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, false, Boolean.class);

        PreparedStatementCreator statementCreator = con -> {
            if (!shouldRetrieveGeneratedKeys) {
                return con.prepareStatement(preparedQuery);
            } else {
                Object expectedGeneratedColumns = exchange.getIn().getHeader(SqlConstants.SQL_GENERATED_COLUMNS);
                if (expectedGeneratedColumns == null) {
                    return con.prepareStatement(preparedQuery, Statement.RETURN_GENERATED_KEYS);
                } else if (expectedGeneratedColumns instanceof String[]) {
                    return con.prepareStatement(preparedQuery, (String[]) expectedGeneratedColumns);
                } else if (expectedGeneratedColumns instanceof int[]) {
                    return con.prepareStatement(preparedQuery, (int[]) expectedGeneratedColumns);
                } else {
                    throw new IllegalArgumentException(
                            "Header specifying expected returning columns isn't an instance of String[] or int[] but "
                                                       + expectedGeneratedColumns.getClass());
                }
            }
        };

        Object data;
        if (getEndpoint().getOutputType() == SqlOutputType.StreamList) {
            data = processStreamList(exchange, statementCreator, sql, preparedQuery);
        } else {
            data = processInternal(exchange, statementCreator, sql, preparedQuery, shouldRetrieveGeneratedKeys);
        }
        if (getEndpoint().getOutputHeader() != null) {
            exchange.getIn().setHeader(getEndpoint().getOutputHeader(), data == EMPTY_RESULT ? null : data);
        } else if (data != null && !getEndpoint().isNoop()) {
            exchange.getIn().setBody(data == EMPTY_RESULT ? null : data);
        }
    }

    private Object processInternal(
            Exchange exchange, PreparedStatementCreator statementCreator,
            String sql, String preparedQuery, Boolean shouldRetrieveGeneratedKeys) {
        LOG.trace("jdbcTemplate.execute: {}", preparedQuery);
        return jdbcTemplate.execute(statementCreator, new PreparedStatementCallback<Object>() {
            public Object doInPreparedStatement(PreparedStatement ps) throws SQLException {
                Object data = null;
                ResultSet rs = null;
                try {
                    populateStatement(ps, exchange, sql, preparedQuery);
                    boolean isResultSet = false;

                    if (batch) {
                        int[] updateCounts = ps.executeBatch();
                        int total = 0;
                        for (int count : updateCounts) {
                            total += count;
                        }
                        exchange.getIn().setHeader(SqlConstants.SQL_UPDATE_COUNT, total);
                    } else {
                        isResultSet = ps.execute();
                        if (isResultSet) {

                            rs = ps.getResultSet();
                            SqlOutputType outputType = getEndpoint().getOutputType();
                            LOG.trace("Got result list from query: {}, outputType={}", rs, outputType);

                            int rowCount = 0;
                            if (outputType == SqlOutputType.SelectList) {
                                data = getEndpoint().queryForList(rs, true);
                                rowCount = ((List<?>) data).size();
                            } else if (outputType == SqlOutputType.SelectOne) {
                                data = getEndpoint().queryForObject(rs);
                                if (data != null) {
                                    rowCount = 1;
                                } else {
                                    // need to mark special when no data
                                    data = EMPTY_RESULT;
                                }
                            } else {
                                throw new IllegalArgumentException("Invalid outputType=" + outputType);
                            }
                            exchange.getIn().setHeader(SqlConstants.SQL_ROW_COUNT, rowCount);
                        } else {
                            exchange.getIn().setHeader(SqlConstants.SQL_UPDATE_COUNT, ps.getUpdateCount());
                        }
                    }

                    if (shouldRetrieveGeneratedKeys) {
                        if (isResultSet) {
                            // we won't return generated keys for SELECT statements
                            exchange.getIn().setHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, Collections.emptyList());
                            exchange.getIn().setHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT, 0);
                        } else {
                            List<?> generatedKeys = getEndpoint().queryForList(ps.getGeneratedKeys(), false);
                            exchange.getIn().setHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, generatedKeys);
                            exchange.getIn().setHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT, generatedKeys.size());
                        }
                    }

                    return data;
                } finally {
                    closeResultSet(rs);
                }
            }
        });
    }

    protected Object processStreamList(
            Exchange exchange, PreparedStatementCreator statementCreator, String sql, String preparedQuery)
            throws Exception {
        LOG.trace("processStreamList: {}", preparedQuery);

        // do not use the jdbcTemplate as it will auto-close connection/ps/rs when exiting the execute method
        // and we need to keep the connection alive while routing and close it when the Exchange is done being routed
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = jdbcTemplate.getDataSource().getConnection();
            ps = statementCreator.createPreparedStatement(con);
            ResultSetIterator iterator = null;

            populateStatement(ps, exchange, sql, preparedQuery);

            boolean isResultSet = ps.execute();
            if (isResultSet) {
                rs = ps.getResultSet();
                iterator = getEndpoint().queryForStreamList(con, ps, rs);

                // we do not know the row count so we cannot set a ROW_COUNT header
                // defer closing the iterator when the exchange is complete
                exchange.getExchangeExtension().addOnCompletion(new ResultSetIteratorCompletion(iterator));
            }
            return iterator;
        } catch (Exception e) {
            // in case of exception then close all this before rethrow
            closeConnection(con);
            closeStatement(ps);
            closeResultSet(rs);
            throw e;
        }
    }

    private void populateStatement(PreparedStatement ps, Exchange exchange, String sql, String preparedQuery)
            throws SQLException {
        int expected;
        if (parametersCount > 0) {
            expected = parametersCount;
        } else {
            ParameterMetaData meta = ps.getParameterMetaData();
            expected = meta != null ? meta.getParameterCount() : 0;
        }

        // only populate if really needed
        if (alwaysPopulateStatement || expected > 0) {
            // transfer incoming message body data to prepared statement parameters, if necessary
            if (batch) {
                Iterator<?> iterator;
                if (useMessageBodyForSql) {
                    iterator = exchange.getIn().getHeader(SqlConstants.SQL_PARAMETERS, Iterator.class);
                } else {
                    iterator = exchange.getIn().getBody(Iterator.class);
                }
                while (iterator != null && iterator.hasNext()) {
                    Object value = iterator.next();
                    Iterator<?> i = sqlPrepareStatementStrategy.createPopulateIterator(sql, preparedQuery, expected,
                            exchange, value);
                    sqlPrepareStatementStrategy.populateStatement(ps, i, expected);
                    ps.addBatch();
                }
            } else {
                Object value;
                if (useMessageBodyForSql) {
                    value = exchange.getIn().getHeader(SqlConstants.SQL_PARAMETERS);
                } else {
                    value = exchange.getIn().getBody();
                }
                Iterator<?> i = sqlPrepareStatementStrategy.createPopulateIterator(sql, preparedQuery, expected,
                        exchange, value);
                sqlPrepareStatementStrategy.populateStatement(ps, i, expected);
            }
        }
    }

    public void setParametersCount(int parametersCount) {
        this.parametersCount = parametersCount;
    }
}
