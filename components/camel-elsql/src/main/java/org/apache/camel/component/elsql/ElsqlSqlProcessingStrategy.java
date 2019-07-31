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
package org.apache.camel.component.elsql;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.opengamma.elsql.ElSql;
import com.opengamma.elsql.SpringSqlParams;
import org.apache.camel.Exchange;
import org.apache.camel.component.sql.DefaultSqlEndpoint;
import org.apache.camel.component.sql.SqlNamedProcessingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

public class ElsqlSqlProcessingStrategy implements SqlNamedProcessingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ElsqlSqlProcessingStrategy.class);
    private final ElSql elSql;

    public ElsqlSqlProcessingStrategy(ElSql elSql) {
        this.elSql = elSql;
    }

    @Override
    public int commit(DefaultSqlEndpoint defaultSqlEndpoint, Exchange exchange, Object data, NamedParameterJdbcTemplate jdbcTemplate,
                      SqlParameterSource parameterSource, String query) throws Exception {

        final SqlParameterSource param = new ElsqlSqlMapSource(exchange, data);
        final String sql = elSql.getSql(query, new SpringSqlParams(param));
        LOG.debug("commit @{} using sql: {}", query, sql);

        return jdbcTemplate.execute(sql, param, new PreparedStatementCallback<Integer>() {
            @Override
            public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                ps.execute();

                int updateCount = ps.getUpdateCount();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Update count {}", updateCount);
                }
                return updateCount;
            }
        });
    }

    @Override
    public int commitBatchComplete(DefaultSqlEndpoint endpoint, NamedParameterJdbcTemplate namedJdbcTemplate,
                            SqlParameterSource parameterSource, String query) throws Exception {

        final SqlParameterSource param = new EmptySqlParameterSource();
        final String sql = elSql.getSql(query, new SpringSqlParams(param));
        LOG.debug("commitBatchComplete @{} using sql: {}", query, sql);

        return namedJdbcTemplate.execute(sql, param, new PreparedStatementCallback<Integer>() {
            @Override
            public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                ps.execute();

                int updateCount = ps.getUpdateCount();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Update count {}", updateCount);
                }
                return updateCount;
            }
        });
    }

    @Override
    public int commit(DefaultSqlEndpoint defaultSqlEndpoint, Exchange exchange, Object data, JdbcTemplate jdbcTemplate, String query) throws Exception {
        throw new UnsupportedOperationException("Should not be called");
    }

    @Override
    public int commitBatchComplete(DefaultSqlEndpoint defaultSqlEndpoint, JdbcTemplate jdbcTemplate, String query) throws Exception {
        throw new UnsupportedOperationException("Should not be called");
    }
}
