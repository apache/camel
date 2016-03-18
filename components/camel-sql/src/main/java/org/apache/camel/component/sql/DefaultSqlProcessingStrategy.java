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
import java.sql.SQLException;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

/**
 *
 */
public class DefaultSqlProcessingStrategy implements SqlProcessingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSqlProcessingStrategy.class);
    private final SqlPrepareStatementStrategy sqlPrepareStatementStrategy;

    public DefaultSqlProcessingStrategy(SqlPrepareStatementStrategy sqlPrepareStatementStrategy) {
        this.sqlPrepareStatementStrategy = sqlPrepareStatementStrategy;
    }

    @Override
    public int commit(final DefaultSqlEndpoint endpoint, final Exchange exchange, final Object data, final JdbcTemplate jdbcTemplate, final String query) throws Exception {

        final String preparedQuery = sqlPrepareStatementStrategy.prepareQuery(query, endpoint.isAllowNamedParameters(), exchange);

        return jdbcTemplate.execute(preparedQuery, new PreparedStatementCallback<Integer>() {
            public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException {
                int expected = ps.getParameterMetaData().getParameterCount();

                Iterator<?> iterator = sqlPrepareStatementStrategy.createPopulateIterator(query, preparedQuery, expected, exchange, data);
                if (iterator != null) {
                    sqlPrepareStatementStrategy.populateStatement(ps, iterator, expected);
                    LOG.trace("Execute query {}", query);
                    ps.execute();

                    int updateCount = ps.getUpdateCount();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Update count {}", updateCount);
                    }
                    return updateCount;
                }

                return 0;
            };
        });
    }

    @Override
    public int commitBatchComplete(final DefaultSqlEndpoint endpoint, final JdbcTemplate jdbcTemplate, final String query) throws Exception {
        final String preparedQuery = sqlPrepareStatementStrategy.prepareQuery(query, endpoint.isAllowNamedParameters(), null);

        return jdbcTemplate.execute(preparedQuery, new PreparedStatementCallback<Integer>() {
            public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException {
                int expected = ps.getParameterMetaData().getParameterCount();
                if (expected != 0) {
                    throw new IllegalArgumentException("Query onConsumeBatchComplete " + query + " cannot have parameters, was " + expected);
                }

                LOG.trace("Execute query {}", query);
                ps.execute();

                int updateCount = ps.getUpdateCount();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Update count {}", updateCount);
                }
                return updateCount;
            };
        });
    }
}

