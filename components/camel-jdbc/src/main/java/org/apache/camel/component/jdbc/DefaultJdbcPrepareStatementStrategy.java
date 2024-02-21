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
package org.apache.camel.component.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link JdbcPrepareStatementStrategy} which is a copy from the camel-sql component having this functionality
 * first.
 */
public class DefaultJdbcPrepareStatementStrategy implements JdbcPrepareStatementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultJdbcPrepareStatementStrategy.class);

    @Override
    public String prepareQuery(String query, boolean allowNamedParameters) throws SQLException {
        String answer;
        if (allowNamedParameters && hasNamedParameters(query)) {
            // replace all :?word with just ?
            answer = query.replaceAll("\\:\\?\\w+", "\\?");
        } else {
            answer = query;
        }

        LOG.trace("Prepared query: {}", answer);
        return answer;
    }

    @Override
    public Iterator<?> createPopulateIterator(
            final String query, final String preparedQuery, final int expectedParams,
            final Exchange exchange, final Object value)
            throws SQLException {
        Map<?, ?> map = null;
        if (exchange.getIn().hasHeaders()) {
            if (exchange.getIn().getHeader(JdbcConstants.JDBC_PARAMETERS) != null) {
                // header JDBC_PARAMETERS takes precedence over regular headers
                map = exchange.getIn().getHeader(JdbcConstants.JDBC_PARAMETERS, Map.class);
            } else {
                map = exchange.getIn().getHeaders();
            }
        }

        final Map<?, ?> headerMap = map;

        if (hasNamedParameters(query)) {
            // create an iterator that returns the value in the named order
            try {

                return new Iterator<>() {
                    private final NamedQueryParser parser = new NamedQueryParser(query);
                    private Object next;
                    private boolean done;
                    private boolean preFetched;

                    @Override
                    public boolean hasNext() {
                        if (!done && !preFetched) {
                            next();
                            preFetched = true;
                        }
                        return !done;
                    }

                    @Override
                    public Object next() {
                        if (!preFetched) {
                            String key = parser.next();
                            if (key == null) {
                                done = true;
                                return null;
                            }
                            // the key is expected to exist, if not report so end user can see this
                            boolean contains = headerMap != null && headerMap.containsKey(key);
                            if (!contains) {
                                throw new RuntimeExchangeException(
                                        "Cannot find key [" + key + "] in message body or headers to use when setting named parameter in query ["
                                                                   + query + "]",
                                        exchange);
                            }
                            next = headerMap.get(key);
                        }
                        preFetched = false;
                        return next;
                    }

                    @Override
                    public void remove() {
                        // noop
                    }
                };
            } catch (Exception e) {
                throw new SQLException("Error iterating parameters for the query: " + query, e);
            }

        } else {
            // just use a regular iterator
            return exchange.getContext().getTypeConverter().convertTo(Iterator.class,
                    headerMap != null ? headerMap.values() : null);
        }
    }

    @Override
    public void populateStatement(PreparedStatement ps, Iterator<?> iterator, int expectedParams) throws SQLException {
        int argNumber = 1;
        if (expectedParams > 0) {
            // as the headers may have more values than the SQL needs we just break out when we reached the expected number
            while (iterator != null && iterator.hasNext() && argNumber <= expectedParams) {
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

    protected boolean hasNamedParameters(String query) {
        NamedQueryParser parser = new NamedQueryParser(query);
        return parser.next() != null;
    }

    private static final class NamedQueryParser {

        private static final Pattern PATTERN = Pattern.compile("\\:\\?(\\w+)");
        private final Matcher matcher;

        private NamedQueryParser(String query) {
            this.matcher = PATTERN.matcher(query);
        }

        public String next() {
            if (!matcher.find()) {
                return null;
            }

            return matcher.group(1);
        }
    }
}
