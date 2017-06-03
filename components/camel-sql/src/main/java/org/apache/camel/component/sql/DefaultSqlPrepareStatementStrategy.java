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
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringQuoteHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.util.CompositeIterator;

/**
 * Default {@link SqlPrepareStatementStrategy} that supports named query parameters as well index based.
 */
public class DefaultSqlPrepareStatementStrategy implements SqlPrepareStatementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSqlPrepareStatementStrategy.class);
    private static final Pattern REPLACE_IN_PATTERN = Pattern.compile("\\:\\?in\\:(\\w+|\\$\\{[^\\}]+\\}|\\$simple\\{[^\\}]+\\})", Pattern.MULTILINE);
    private static final Pattern REPLACE_PATTERN = Pattern.compile("\\:\\?\\w+|\\:\\?\\$\\{[^\\}]+\\}|\\:\\?\\$simple\\{[^\\}]+\\}", Pattern.MULTILINE);
    private static final Pattern NAME_PATTERN = Pattern.compile("\\:\\?((in\\:(\\w+|\\$\\{[^\\}]+\\}|\\$simple\\{[^\\}]+\\}))|(\\w+|\\$\\{[^\\}]+\\}|\\$simple\\{[^\\}]+\\}))", Pattern.MULTILINE);
    private final char separator;

    public DefaultSqlPrepareStatementStrategy() {
        this(',');
    }

    public DefaultSqlPrepareStatementStrategy(char separator) {
        this.separator = separator;
    }

    @Override
    public String prepareQuery(String query, boolean allowNamedParameters, final Exchange exchange) throws SQLException {
        String answer;
        if (allowNamedParameters && hasNamedParameters(query)) {
            if (exchange != null) {
                // replace all :?in:word with a number of placeholders for how many values are expected in the IN values
                Matcher matcher = REPLACE_IN_PATTERN.matcher(query);
                while (matcher.find()) {
                    String found = matcher.group(1);
                    Object parameter = lookupParameter(found, exchange, exchange.getIn().getBody());
                    if (parameter != null) {
                        Iterator it = createInParameterIterator(parameter);
                        CollectionStringBuffer csb = new CollectionStringBuffer(",");
                        while (it.hasNext()) {
                            it.next();
                            csb.append("\\?");
                        }
                        String replace = csb.toString();
                        String foundEscaped = found.replace("$", "\\$").replace("{", "\\{").replace("}", "\\}");
                        Matcher paramMatcher = Pattern.compile("\\:\\?in\\:" + foundEscaped, Pattern.MULTILINE).matcher(query);
                        query = paramMatcher.replaceAll(replace);
                    }
                }
            }
            // replace all :?word and :?${foo} with just ?
            answer = REPLACE_PATTERN.matcher(query).replaceAll("\\?");
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

            // special for SQL IN where we need to set dynamic number of values
            if (value instanceof CompositeIterator) {
                Iterator it = (Iterator) value;
                while (it.hasNext()) {
                    Object val = it.next();
                    LOG.trace("Setting parameter #{} with value: {}", argNumber, val);
                    if (argNumber <= expectedParams) {
                        args[i] = val;
                    }
                    argNumber++;
                    i++;
                }
            } else {
                LOG.trace("Setting parameter #{} with value: {}", argNumber, value);
                if (argNumber <= expectedParams) {
                    args[i] = value;
                }
                argNumber++;
                i++;
            }
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

        private final Matcher matcher;

        private NamedQueryParser(String query) {
            this.matcher = NAME_PATTERN.matcher(query);
        }

        public String next() {
            if (matcher.find()) {
                return matcher.group(1);
            }

            return null;
        }
    }

    protected static Object lookupParameter(String nextParam, Exchange exchange, Object body) {
        Map<?, ?> bodyMap = safeMap(exchange.getContext().getTypeConverter().tryConvertTo(Map.class, body));
        Map<?, ?> headersMap = safeMap(exchange.getIn().getHeaders());

        Object answer = null;
        if ((nextParam.startsWith("$simple{") || nextParam.startsWith("${")) && nextParam.endsWith("}")) {
            answer = SimpleLanguage.expression(nextParam).evaluate(exchange, Object.class);
        } else if (bodyMap.containsKey(nextParam)) {
            answer = bodyMap.get(nextParam);
        } else if (headersMap.containsKey(nextParam)) {
            answer = headersMap.get(nextParam);
        }

        return answer;
    }

    protected static boolean hasParameter(String nextParam, Exchange exchange, Object body) {
        Map<?, ?> bodyMap = safeMap(exchange.getContext().getTypeConverter().tryConvertTo(Map.class, body));
        Map<?, ?> headersMap = safeMap(exchange.getIn().getHeaders());

        if ((nextParam.startsWith("$simple{") || nextParam.startsWith("${")) && nextParam.endsWith("}")) {
            return true;
        } else if (bodyMap.containsKey(nextParam)) {
            return true;
        } else if (headersMap.containsKey(nextParam)) {
            return true;
        }

        return false;
    }

    private static Map<?, ?> safeMap(Map<?, ?> map) {
        return (map == null || map.isEmpty()) ? Collections.emptyMap() : map;
    }

    @SuppressWarnings("unchecked")
    protected static CompositeIterator createInParameterIterator(Object value) {
        Iterator it;
        // if the body is a String then honor quotes etc.
        if (value instanceof String) {
            String[] tokens = StringQuoteHelper.splitSafeQuote((String) value, ',', true);
            List<String> list = Arrays.asList(tokens);
            it = list.iterator();
        } else {
            it = ObjectHelper.createIterator(value, null);
        }
        CompositeIterator ci = new CompositeIterator();
        ci.add(it);
        return ci;
    }

    private static final class PopulateIterator implements Iterator<Object> {
        private static final String MISSING_PARAMETER_EXCEPTION =
                "Cannot find key [%s] in message body or headers to use when setting named parameter in query [%s]";
        private final String query;
        private final NamedQueryParser parser;
        private final Exchange exchange;
        private final Object body;
        private String nextParam;

        private PopulateIterator(String query, Exchange exchange, Object body) {
            this.query = query;
            this.exchange = exchange;
            this.body = body;
            this.parser = new NamedQueryParser(query);
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

            // is it a SQL in parameter
            boolean in = false;
            if (nextParam.startsWith("in:")) {
                in = true;
                nextParam = nextParam.substring(3);
            }

            Object next = null;
            try {
                boolean hasNext = hasParameter(nextParam, exchange, body);
                if (hasNext) {
                    next = lookupParameter(nextParam, exchange, body);
                    if (in && next != null) {
                        // if SQL IN we need to return an iterator that can iterate the parameter values
                        next = createInParameterIterator(next);
                    }
                } else {
                    throw new RuntimeExchangeException(String.format(MISSING_PARAMETER_EXCEPTION, nextParam, query), exchange);
                }
            } finally {
                nextParam = parser.next();
            }

            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
}
