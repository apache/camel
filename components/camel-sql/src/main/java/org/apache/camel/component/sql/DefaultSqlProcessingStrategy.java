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
import java.util.Map;

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

    @Override
    public void commit(SqlEndpoint endpoint, final Exchange exchange, Object data, JdbcTemplate jdbcTemplate, final String query) throws Exception {
        jdbcTemplate.execute(query, new PreparedStatementCallback<Map<?, ?>>() {
            public Map<?, ?> doInPreparedStatement(PreparedStatement ps) throws SQLException {
                int expected = ps.getParameterMetaData().getParameterCount();

                Iterator<?> iterator = createIterator(exchange, query, expected);
                if (iterator != null) {
                    populateStatement(ps, iterator, expected);
                    LOG.trace("Execute query {}", query);
                    ps.execute();
                }

                return null;
            };
        });
    }

    private Iterator<?> createIterator(Exchange exchange, final String query, final int expectedParams) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return null;
        }

        // TODO: support named parameters
/*
        if (body instanceof Map) {
            final Map map = (Map) body;
            return new Iterator() {

                private int current;

                @Override
                public boolean hasNext() {
                    return current < expectedParams;
                }

                @Override
                public Object next() {
                    current++;
                    // TODO: Fix me
                    return map.get("ID");
                }

                @Override
                public void remove() {
                    // noop
                }
            };
        }*/

        // else force as iterator based
        Iterator<?> iterator = exchange.getIn().getBody(Iterator.class);
        return iterator;
    }

    private void populateStatement(PreparedStatement ps, Iterator<?> iterator, int expectedParams) throws SQLException {
        int argNumber = 1;
        if (expectedParams > 0) {
            while (iterator != null && iterator.hasNext()) {
                Object value = iterator.next();
                LOG.trace("Setting parameter #{} with value: {}", argNumber, value);
                ps.setObject(argNumber, value);
                argNumber++;
            }
        }

        if (argNumber - 1 != expectedParams) {
            throw new SQLException("Number of parameters mismatch. Expected: " + expectedParams + ", was:" + (argNumber - 1));
        }
    }

}

