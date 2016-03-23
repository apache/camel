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
package org.apache.camel.component.elsql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.opengamma.elsql.ElSql;
import com.opengamma.elsql.SpringSqlParams;
import org.apache.camel.Exchange;
import org.apache.camel.component.sql.SqlConstants;
import org.apache.camel.component.sql.SqlOutputType;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.springframework.jdbc.support.JdbcUtils.closeResultSet;

public class ElsqlProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ElsqlProducer.class);
    private final ElSql elSql;
    private final String elSqlName;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ElsqlProducer(ElsqlEndpoint endpoint, ElSql elSql, String elSqlName, NamedParameterJdbcTemplate jdbcTemplate) {
        super(endpoint);
        this.elSql = elSql;
        this.elSqlName = elSqlName;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ElsqlEndpoint getEndpoint() {
        return (ElsqlEndpoint) super.getEndpoint();
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        Object data = exchange.getIn().getBody();

        final SqlParameterSource param = new ElsqlSqlMapSource(exchange, data);
        final String sql = elSql.getSql(elSqlName, new SpringSqlParams(param));
        LOG.debug("ElsqlProducer @{} using sql: {}", elSqlName, sql);

        jdbcTemplate.execute(sql, param, new PreparedStatementCallback<Object>() {
            @Override
            public Object doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                ResultSet rs = null;
                try {
                    boolean isResultSet = ps.execute();
                    if (isResultSet) {
                        rs = ps.getResultSet();

                        // preserve headers first, so we can override the SQL_ROW_COUNT header
                        exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());

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
                            }
                        } else {
                            throw new IllegalArgumentException("Invalid outputType=" + outputType);
                        }
                    }
                } finally {
                    closeResultSet(rs);
                }

                return null;
            }
        });
    }
}
