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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

public class SqlProducer extends DefaultProducer {
    private String query;
    private JdbcTemplate jdbcTemplate;
    private boolean batch;
    private boolean alwaysPopulateStatement;
    private SqlPrepareStatementStrategy sqlPrepareStatementStrategy;
    private int parametersCount;

    public SqlProducer(SqlEndpoint endpoint, String query, JdbcTemplate jdbcTemplate, SqlPrepareStatementStrategy sqlPrepareStatementStrategy,
                       boolean batch, boolean alwaysPopulateStatement) {
        super(endpoint);
        this.jdbcTemplate = jdbcTemplate;
        this.sqlPrepareStatementStrategy = sqlPrepareStatementStrategy;
        this.query = query;
        this.batch = batch;
        this.alwaysPopulateStatement = alwaysPopulateStatement;
    }

    @Override
    public SqlEndpoint getEndpoint() {
        return (SqlEndpoint) super.getEndpoint();
    }

    public void process(final Exchange exchange) throws Exception {
        String queryHeader = exchange.getIn().getHeader(SqlConstants.SQL_QUERY, String.class);

        final String sql = queryHeader != null ? queryHeader : query;
        final String preparedQuery = sqlPrepareStatementStrategy.prepareQuery(sql, getEndpoint().isAllowNamedParameters());

        jdbcTemplate.execute(preparedQuery, new PreparedStatementCallback<Map<?, ?>>() {
            public Map<?, ?> doInPreparedStatement(PreparedStatement ps) throws SQLException {
                int expected = parametersCount > 0 ? parametersCount : ps.getParameterMetaData().getParameterCount();

                // only populate if really needed
                if (alwaysPopulateStatement || expected > 0) {
                    // transfer incoming message body data to prepared statement parameters, if necessary
                    if (batch) {
                        Iterator<?> iterator = exchange.getIn().getBody(Iterator.class);
                        while (iterator != null && iterator.hasNext()) {
                            Object value = iterator.next();
                            Iterator<?> i = sqlPrepareStatementStrategy.createPopulateIterator(sql, preparedQuery, expected, exchange, value);
                            sqlPrepareStatementStrategy.populateStatement(ps, i, expected);
                            ps.addBatch();
                        }
                    } else {
                        Iterator<?> i = sqlPrepareStatementStrategy.createPopulateIterator(sql, preparedQuery, expected, exchange, exchange.getIn().getBody());
                        sqlPrepareStatementStrategy.populateStatement(ps, i, expected);
                    }
                }

                // execute the prepared statement and populate the outgoing message
                if (batch) {
                    int[] updateCounts = ps.executeBatch();
                    int total = 0;
                    for (int count : updateCounts) {
                        total += count;
                    }
                    exchange.getIn().setHeader(SqlConstants.SQL_UPDATE_COUNT, total);
                } else {
                    boolean isResultSet = ps.execute();
                    if (isResultSet) {
                        ResultSet rs = ps.getResultSet();
                        SqlOutputType outputType = getEndpoint().getOutputType();
                        log.trace("Got result list from query: {}, outputType={}", rs, outputType);
                        if (outputType == SqlOutputType.SelectList) {
                            List<Map<String, Object>> data = getEndpoint().queryForList(ps.getResultSet());
                            // for noop=true we still want to enrich with the row count header
                            if (getEndpoint().isNoop()) {
                                exchange.getOut().setBody(exchange.getIn().getBody());
                            } else {
                                exchange.getOut().setBody(data);
                            }
                            exchange.getOut().setHeader(SqlConstants.SQL_ROW_COUNT, data.size());
                        } else if (outputType == SqlOutputType.SelectOne) {
                            Object data = getEndpoint().queryForObject(ps.getResultSet());
                            if (data != null) {
                                // for noop=true we still want to enrich with the row count header
                                if (getEndpoint().isNoop()) {
                                    exchange.getOut().setBody(exchange.getIn().getBody());
                                } else {
                                    exchange.getOut().setBody(data);
                                }
                                exchange.getOut().setHeader(SqlConstants.SQL_ROW_COUNT, 1);
                            }
                        } else {
                            throw new IllegalArgumentException("Invalid outputType=" + outputType);
                        }

                        // preserve headers
                        exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
                    } else {
                        exchange.getIn().setHeader(SqlConstants.SQL_UPDATE_COUNT, ps.getUpdateCount());
                    }
                }

                // data is set on exchange so return null
                return null;
            }
        });
    }

    public void setParametersCount(int parametersCount) {
        this.parametersCount = parametersCount;
    }
}
