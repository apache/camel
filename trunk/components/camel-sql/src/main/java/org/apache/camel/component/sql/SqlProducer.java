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
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

public class SqlProducer extends DefaultProducer {
    private String query;
    private JdbcTemplate jdbcTemplate;
    private boolean batch;

    public SqlProducer(SqlEndpoint endpoint, String query, JdbcTemplate jdbcTemplate, boolean batch) {
        super(endpoint);
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
        this.batch = batch;
    }

    public void process(final Exchange exchange) throws Exception {
        String queryHeader = exchange.getIn().getHeader(SqlConstants.SQL_QUERY, String.class);
        String sql = queryHeader != null ? queryHeader : query;

        jdbcTemplate.execute(sql, new PreparedStatementCallback<Map<?, ?>>() {
            public Map<?, ?> doInPreparedStatement(PreparedStatement ps) throws SQLException {
                int expected = ps.getParameterMetaData().getParameterCount();

                // transfer incoming message body data to prepared statement parameters, if necessary
                if (exchange.getIn().getBody() != null) {
                    Iterator<?> iterator = exchange.getIn().getBody(Iterator.class);
                    
                    if (batch) {
                        while (iterator != null && iterator.hasNext()) {
                            Object value = iterator.next();
                            Iterator<?> i = exchange.getContext().getTypeConverter().convertTo(Iterator.class, value);
                            populateStatement(ps, i, expected);
                            ps.addBatch();
                        }
                    } else {
                        populateStatement(ps, iterator, expected);
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
                        RowMapperResultSetExtractor<Map<String, Object>> mapper = new RowMapperResultSetExtractor<Map<String, Object>>(new ColumnMapRowMapper());
                        List<Map<String, Object>> result = mapper.extractData(ps.getResultSet());
                        exchange.getOut().setBody(result);
                        exchange.getIn().setHeader(SqlConstants.SQL_ROW_COUNT, result.size());
                        // preserve headers
                        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                    } else {
                        exchange.getIn().setHeader(SqlConstants.SQL_UPDATE_COUNT, ps.getUpdateCount());
                    }
                }

                // data is set on exchange so return null
                return null;
            }
        });
    }

    private void populateStatement(PreparedStatement ps, Iterator<?> iterator, int expectedParams) throws SQLException {
        int argNumber = 1;
        if (expectedParams > 0) {
            while (iterator != null && iterator.hasNext()) {
                Object value = iterator.next();
                log.trace("Setting parameter #{} with value: {}", argNumber, value);
                ps.setObject(argNumber, value);
                argNumber++;
            }
        }
        
        if (argNumber - 1 != expectedParams) {
            throw new SQLException("Number of parameters mismatch. Expected: " + expectedParams + ", was:" + (argNumber - 1));
        }
    }
}
