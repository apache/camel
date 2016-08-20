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
package org.apache.camel.component.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;

import static org.springframework.jdbc.support.JdbcUtils.closeConnection;
import static org.springframework.jdbc.support.JdbcUtils.closeResultSet;
import static org.springframework.jdbc.support.JdbcUtils.closeStatement;

public class SqlProducer extends DefaultProducer {
    private final String query;
    private String resolvedQuery;
    private final JdbcTemplate jdbcTemplate;
    private final boolean batch;
    private final boolean alwaysPopulateStatement;
    private final SqlPrepareStatementStrategy sqlPrepareStatementStrategy;
    private final boolean useMessageBodyForSql;
    private int parametersCount;

    public SqlProducer(SqlEndpoint endpoint, String query, JdbcTemplate jdbcTemplate, SqlPrepareStatementStrategy sqlPrepareStatementStrategy,
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
    protected void doStart() throws Exception {
        super.doStart();

        String placeholder = getEndpoint().isUsePlaceholder() ? getEndpoint().getPlaceholder() : null;
        resolvedQuery = SqlHelper.resolveQuery(getEndpoint().getCamelContext(), query, placeholder);
    }

    public void process(final Exchange exchange) throws Exception {
        final String sql;
        if (useMessageBodyForSql) {
            sql = exchange.getIn().getBody(String.class);
        } else {
            String queryHeader = exchange.getIn().getHeader(SqlConstants.SQL_QUERY, String.class);
            sql = queryHeader != null ? queryHeader : resolvedQuery;
        }
        final String preparedQuery = sqlPrepareStatementStrategy.prepareQuery(sql, getEndpoint().isAllowNamedParameters(), exchange);

        final Boolean shouldRetrieveGeneratedKeys =
            exchange.getIn().getHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, false, Boolean.class);

        PreparedStatementCreator statementCreator = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
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
            }
        };

        // special for processing stream list (batch not supported)
        SqlOutputType outputType = getEndpoint().getOutputType();
        if (outputType == SqlOutputType.StreamList) {
            processStreamList(exchange, statementCreator, sql, preparedQuery);
            return;
        }

        log.trace("jdbcTemplate.execute: {}", preparedQuery);
        jdbcTemplate.execute(statementCreator, new PreparedStatementCallback<Map<?, ?>>() {
            public Map<?, ?> doInPreparedStatement(PreparedStatement ps) throws SQLException {
                ResultSet rs = null;
                try {
                    int expected = parametersCount > 0 ? parametersCount : ps.getParameterMetaData().getParameterCount();

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
                                Iterator<?> i = sqlPrepareStatementStrategy.createPopulateIterator(sql, preparedQuery, expected, exchange, value);
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
                            Iterator<?> i = sqlPrepareStatementStrategy.createPopulateIterator(sql, preparedQuery, expected, exchange, value);
                            sqlPrepareStatementStrategy.populateStatement(ps, i, expected);
                        }
                    }

                    boolean isResultSet = false;

                    // execute the prepared statement and populate the outgoing message
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
                            // preserve headers first, so we can override the SQL_ROW_COUNT header
                            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
                            exchange.getOut().getAttachments().putAll(exchange.getIn().getAttachments());

                            rs = ps.getResultSet();
                            SqlOutputType outputType = getEndpoint().getOutputType();
                            log.trace("Got result list from query: {}, outputType={}", rs, outputType);
                            if (outputType == SqlOutputType.SelectList) {
                                List<?> data = getEndpoint().queryForList(rs, true);
                                // for noop=true we still want to enrich with the row count header
                                if (getEndpoint().isNoop()) {
                                    exchange.getOut().setBody(exchange.getIn().getBody());
                                } else if (getEndpoint().getOutputHeader() != null) {
                                    exchange.getOut().setBody(exchange.getIn().getBody());
                                    exchange.getOut().setHeader(getEndpoint().getOutputHeader(), data);
                                } else {
                                    exchange.getOut().setBody(data);
                                }
                                exchange.getOut().setHeader(SqlConstants.SQL_ROW_COUNT, data.size());
                            } else if (outputType == SqlOutputType.SelectOne) {
                                Object data = getEndpoint().queryForObject(rs);
                                if (data != null) {
                                    // for noop=true we still want to enrich with the row count header
                                    if (getEndpoint().isNoop()) {
                                        exchange.getOut().setBody(exchange.getIn().getBody());
                                    } else if (getEndpoint().getOutputHeader() != null) {
                                        exchange.getOut().setBody(exchange.getIn().getBody());
                                        exchange.getOut().setHeader(getEndpoint().getOutputHeader(), data);
                                    } else {
                                        exchange.getOut().setBody(data);
                                    }
                                    exchange.getOut().setHeader(SqlConstants.SQL_ROW_COUNT, 1);
                                } else { 
                                    if (getEndpoint().isNoop()) {
                                        exchange.getOut().setBody(exchange.getIn().getBody());
                                    } else if (getEndpoint().getOutputHeader() != null) {
                                        exchange.getOut().setBody(exchange.getIn().getBody());
                                    }
                                    exchange.getOut().setHeader(SqlConstants.SQL_ROW_COUNT, 0);
                                }
                            } else {
                                throw new IllegalArgumentException("Invalid outputType=" + outputType);
                            }
                        } else {
                            exchange.getIn().setHeader(SqlConstants.SQL_UPDATE_COUNT, ps.getUpdateCount());
                        }
                    }

                    if (shouldRetrieveGeneratedKeys) {
                        // if no OUT message yet then create one and propagate headers
                        if (!exchange.hasOut()) {
                            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
                            exchange.getOut().getAttachments().putAll(exchange.getIn().getAttachments());
                        }

                        if (isResultSet) {
                            // we won't return generated keys for SELECT statements
                            exchange.getOut().setHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, Collections.EMPTY_LIST);
                            exchange.getOut().setHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT, 0);
                        } else {
                            List<?> generatedKeys = getEndpoint().queryForList(ps.getGeneratedKeys(), false);
                            exchange.getOut().setHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, generatedKeys);
                            exchange.getOut().setHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT, generatedKeys.size());
                        }
                    }

                    // data is set on exchange so return null
                    return null;
                } finally {
                    closeResultSet(rs);
                }
            }
        });
    }

    protected void processStreamList(Exchange exchange, PreparedStatementCreator statementCreator, String sql, String preparedQuery) throws Exception {
        log.trace("processStreamList: {}", preparedQuery);

        // do not use the jdbcTemplate as it will auto-close connection/ps/rs when exiting the execute method
        // and we need to keep the connection alive while routing and close it when the Exchange is done being routed
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = jdbcTemplate.getDataSource().getConnection();
            ps = statementCreator.createPreparedStatement(con);

            int expected = parametersCount > 0 ? parametersCount : ps.getParameterMetaData().getParameterCount();

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
                        Iterator<?> i = sqlPrepareStatementStrategy.createPopulateIterator(sql, preparedQuery, expected, exchange, value);
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
                    Iterator<?> i = sqlPrepareStatementStrategy.createPopulateIterator(sql, preparedQuery, expected, exchange, value);
                    sqlPrepareStatementStrategy.populateStatement(ps, i, expected);
                }
            }

            boolean isResultSet = ps.execute();
            if (isResultSet) {
                rs = ps.getResultSet();
                ResultSetIterator iterator = getEndpoint().queryForStreamList(con, ps, rs);
                //pass through all headers
                exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
                exchange.getOut().getAttachments().putAll(exchange.getIn().getAttachments());

                if (getEndpoint().isNoop()) {
                    exchange.getOut().setBody(exchange.getIn().getBody());
                } else if (getEndpoint().getOutputHeader() != null) {
                    exchange.getOut().setBody(exchange.getIn().getBody());
                    exchange.getOut().setHeader(getEndpoint().getOutputHeader(), iterator);
                } else {
                    exchange.getOut().setBody(iterator);
                }
                // we do not know the row count so we cannot set a ROW_COUNT header
                // defer closing the iterator when the exchange is complete
                exchange.addOnCompletion(new ResultSetIteratorCompletion(iterator));
            }
        } catch (Exception e) {
            // in case of exception then close all this before rethrow
            closeConnection(con);
            closeStatement(ps);
            closeResultSet(rs);
            throw e;
        }
    }

    public void setParametersCount(int parametersCount) {
        this.parametersCount = parametersCount;
    }
}
