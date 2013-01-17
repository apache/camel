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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link SqlPrepareStatementStrategy} that supports named query parameters as well index based.
 */
public class DefaultSqlPrepareStatementStrategy implements SqlPrepareStatementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSqlPrepareStatementStrategy.class);

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
    public Iterator<?> createPopulateIterator(final String query, final String preparedQuery, final int expectedParams,
                                              final Exchange exchange, final Object value) throws SQLException {
        if (hasNamedParameters(query)) {
            // create an iterator that returns the value in the named order
            // the body must be a map type when using named parameters
            try {
                final Map map = exchange.getContext().getTypeConverter().mandatoryConvertTo(Map.class, value);

                return new Iterator() {
                    private NamedQueryParser parser = new NamedQueryParser(query);
                    private Object next;
                    private boolean done;

                    @Override
                    public boolean hasNext() {
                        if (done) {
                            return false;
                        }
                        if (next == null) {
                            next = next();
                        }
                        return next != null;
                    }

                    @Override
                    public Object next() {
                        if (next == null) {
                            String key = parser.next();
                            if (key == null) {
                                done = true;
                                return null;
                            }
                            // the key is expected to exist, if not report so end user can see this
                            if (!map.containsKey(key)) {
                                throw new RuntimeExchangeException("Cannot find key [" + key + "] in message body to use when setting named parameter in query [" + query + "]", exchange);
                            }
                            next = map.get(key);
                        }
                        Object answer = next;
                        next = null;
                        return answer;
                    }

                    @Override
                    public void remove() {
                        // noop
                    }
                };
            } catch (Exception e) {
                throw new SQLException("The message body must be a java.util.Map type when using named parameters in the query: " + query, e);
            }


        } else {
            // just use a regular iterator
            return exchange.getContext().getTypeConverter().convertTo(Iterator.class, value);
        }
    }

    @Override
    public void populateStatement(PreparedStatement ps, Iterator<?> iterator, int expectedParams) throws SQLException {
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

        public String replaceAll(String replacement) {
            return matcher.replaceAll(replacement);
        }
    }
}
