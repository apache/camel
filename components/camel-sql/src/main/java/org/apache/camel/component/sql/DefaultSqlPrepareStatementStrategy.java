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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.util.StringQuoteHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;

/**
 * Default {@link SqlPrepareStatementStrategy} that supports named query parameters as well index based.
 */
public class DefaultSqlPrepareStatementStrategy implements SqlPrepareStatementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSqlPrepareStatementStrategy.class);
    private final char separator;

    public DefaultSqlPrepareStatementStrategy() {
        this(',');
    }

    public DefaultSqlPrepareStatementStrategy(char separator) {
        this.separator = separator;
    }

    @Override
    public String prepareQuery(String query, boolean allowNamedParameters) throws SQLException {
        String answer;
        if (allowNamedParameters && hasNamedParameters(query)) {
            // replace all :?word and :?${foo} with just ?
            answer = query.replaceAll("\\:\\?\\w+|\\:\\?\\$\\{[^\\}]+\\}", "\\?");
        } else {
            answer = query;
        }

        LOG.trace("Prepared query: {}", answer);
        return answer;
    }

    @Override
    public Iterator<?> createPopulateIterator(final String query, final String preparedQuery, final int expectedParams, final Exchange exchange,
                                              final Object value) throws SQLException {
        if (hasNamedParameters(query)) {
            // create an iterator that returns the value in the named order
            return new PopulateIterator(query, exchange, value);
        } else {
            // if only 1 parameter and the body is a String then use body as is
            if (expectedParams == 1 && value instanceof String) {
                return Collections.singletonList(value).iterator();
            } else {
                // is the body a String
                if (value instanceof String) {
                    // if the body is a String then honor quotes etc.
                    String[] tokens = StringQuoteHelper.splitSafeQuote((String) value, separator, true);
                    List<String> list = Arrays.asList(tokens);
                    return list.iterator();
                } else {
                    // just use a regular iterator
                    return exchange.getContext().getTypeConverter().convertTo(Iterator.class, value);
                }
            }
        }
    }

    @Override
    public void populateStatement(PreparedStatement ps, Iterator<?> iterator, int expectedParams) throws SQLException {
        if (expectedParams <= 0) {
            return;
        }

        final Object[] args = new Object[expectedParams];
        int i = 0;
        int argNumber = 1;

        while (iterator != null && iterator.hasNext()) {
            Object value = iterator.next();
            LOG.trace("Setting parameter #{} with value: {}", argNumber, value);
            if (argNumber <= expectedParams) {
                args[i] = value;
            }
            argNumber++;
            i++;
        }
        if (argNumber - 1 != expectedParams) {
            throw new SQLException("Number of parameters mismatch. Expected: " + expectedParams + ", was: " + (argNumber - 1));
        }

        // use argument setter as it deals with various JDBC drivers setObject vs setLong/setInteger/setString etc.
        ArgumentPreparedStatementSetter setter = new ArgumentPreparedStatementSetter(args);
        setter.setValues(ps);
    }

    protected boolean hasNamedParameters(String query) {
        NamedQueryParser parser = new NamedQueryParser(query);
        return parser.next() != null;
    }

    private static final class NamedQueryParser {

        private static final Pattern PATTERN = Pattern.compile("\\:\\?(\\w+|\\$\\{[^\\}]+\\})");
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

    private static final class PopulateIterator implements Iterator<Object> {
        private static final String MISSING_PARAMETER_EXCEPTION =
                "Cannot find key [%s] in message body or headers to use when setting named parameter in query [%s]";
        private final String query;
        private final NamedQueryParser parser;
        private final Exchange exchange;
        private final Map<?, ?> bodyMap;
        private final Map<?, ?> headersMap;
        private String nextParam;

        private PopulateIterator(String query, Exchange exchange, Object body) {
            this.query = query;
            this.parser = new NamedQueryParser(query);
            this.exchange = exchange;
            this.bodyMap = safeMap(exchange.getContext().getTypeConverter().tryConvertTo(Map.class, body));
            this.headersMap = safeMap(exchange.getIn().getHeaders());

            this.nextParam = parser.next();
        }

        @Override
        public boolean hasNext() {
            return nextParam != null;
        }

        @Override
        public Object next() {
            if (nextParam == null) {
                throw new NoSuchElementException();
            }

            try {
                if (nextParam.startsWith("${") && nextParam.endsWith("}")) {
                    return SimpleLanguage.expression(nextParam).evaluate(exchange, Object.class);
                } else if (bodyMap.containsKey(nextParam)) {
                    return bodyMap.get(nextParam);
                } else if (headersMap.containsKey(nextParam)) {
                    return headersMap.get(nextParam);
                }
                throw new RuntimeExchangeException(String.format(MISSING_PARAMETER_EXCEPTION, nextParam, query), exchange);
            } finally {
                nextParam = parser.next();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private static Map<?, ?> safeMap(Map<?, ?> map) {
            return (map == null || map.isEmpty()) ? Collections.emptyMap() : map;
        }
    }
}
