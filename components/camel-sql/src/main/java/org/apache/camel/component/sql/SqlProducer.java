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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

public class SqlProducer extends DefaultProducer {
    private String query;
    private JdbcTemplate jdbcTemplate;

    public SqlProducer(SqlEndpoint endpoint, String query, JdbcTemplate jdbcTemplate) {
        super(endpoint);
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
    }

    @SuppressWarnings("unchecked")
    public void process(final Exchange exchange) throws Exception {
        String queryHeader = exchange.getIn().getHeader(SqlConstants.SQL_QUERY, String.class);
        jdbcTemplate.execute(queryHeader != null ? queryHeader : query, new PreparedStatementCallback() {
            public Object doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                int argNumber = 1;

                // number of parameters must match
                int expected = ps.getParameterMetaData().getParameterCount();

                if (expected > 0 && exchange.getIn().getBody() != null) {
                    Iterator<?> iterator = exchange.getIn().getBody(Iterator.class);
                    while (iterator != null && iterator.hasNext()) {
                        ps.setObject(argNumber++, iterator.next());
                    }
                }

                if (argNumber - 1 != expected) {
                    throw new SQLException("Number of parameters mismatch. Expected: " + expected + ", was:" + (argNumber - 1));
                }
                
                boolean isResultSet = ps.execute();
                
                if (isResultSet) {
                    RowMapperResultSetExtractor mapper = new RowMapperResultSetExtractor(new ColumnMapRowMapper());
                    List<?> result = (List<?>) mapper.extractData(ps.getResultSet());
                    exchange.getOut().setBody(result);
                    exchange.getIn().setHeader(SqlConstants.SQL_ROW_COUNT, result.size());
                    // preserve headers
                    exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                } else {
                    exchange.getIn().setHeader(SqlConstants.SQL_UPDATE_COUNT, ps.getUpdateCount());
                }

                // data is set on exchange so return null
                return null;
            }
        });
    }

}
