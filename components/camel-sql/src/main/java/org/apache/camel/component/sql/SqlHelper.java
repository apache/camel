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
package org.apache.camel.component.sql;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;

public final class SqlHelper {

    /**
     * A named parameter as the component takes it: the placeholder, an optional in: prefix, and a name, a simple
     * expression or a $simple{} expression. The placeholder is inserted when the pattern is built, so it follows the
     * endpoint's own placeholder option rather than assuming #.
     */
    private static final String NAMED = ":%s(in:)?(\\w+|\\$\\{[^}]*\\}|\\$simple\\{[^}]*\\})";

    /**
     * A colon followed by a name or a simple expression, which is what a named parameter is written as in other tools
     * and is not one here. Not preceded by a colon, so a Postgres ::cast is not one, and a name never starts with a
     * digit, so a time literal such as 12:30 is not one either.
     * <p/>
     * It looks for the shapes that are actually written -- {@code :name}, {@code :${...}}, {@code :$simple{...}} and
     * {@code :$name} -- and not for a colon followed by some other punctuation, which would be more likely to catch SQL
     * than a mistake. So with the placeholder set to something other than the default, a query that uses {@code :#name}
     * is not reported.
     */
    private static final Pattern MISSING_PLACEHOLDER = Pattern.compile(
            "(?<![:\\w]):([A-Za-z_]\\w*|\\$simple\\{[^}]*\\}|\\$\\{[^}]*\\}|\\$\\w+)");

    private SqlHelper() {
    }

    /**
     * The first thing in the query that looks like a named parameter but has no placeholder, or null when there is
     * none. {@code :customer} and {@code :${body[customer]}} are how other tools spell a named parameter; this
     * component takes {@code :#customer} and {@code :#${body[customer]}}, and passes anything else to the database
     * untouched, which then complains about its own syntax and names neither Camel nor the query as written
     * (CAMEL-25039).
     *
     * @param  query       the query as the endpoint was given it, before the placeholder is substituted
     * @param  placeholder the endpoint's placeholder, normally #
     * @return             the offending text, or null
     */
    public static String findParameterMissingPlaceholder(String query, String placeholder) {
        if (query == null || placeholder == null || placeholder.isEmpty()) {
            return null;
        }
        // a quoted literal is data, not SQL: ':00' in a timestamp, or a name that happens to follow a colon
        String stripped = blankQuoted(query);
        // the parameters that are written correctly are not what we are looking for, and one of them ends in a name
        // after a colon (:#in:myList), so take them out before looking for what is left
        String rest = Pattern.compile(String.format(NAMED, Pattern.quote(placeholder))).matcher(stripped)
                .replaceAll(" ");
        Matcher matcher = MISSING_PLACEHOLDER.matcher(rest);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * The query with the contents of every single quoted literal replaced by spaces, so what is inside one is not read
     * as SQL. A doubled quote inside a literal escapes it, as in SQL.
     */
    private static String blankQuoted(String query) {
        StringBuilder sb = new StringBuilder(query);
        boolean quoted = false;
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\'') {
                quoted = !quoted;
            } else if (quoted) {
                sb.setCharAt(i, ' ');
            }
        }
        return sb.toString();
    }

    /**
     * Resolve the query by loading the query from the classpath or file resource if needed.
     */
    public static String resolveQuery(CamelContext camelContext, String query, String placeholder)
            throws NoTypeConversionAvailableException, IOException {
        String answer = query;
        if (ResourceHelper.hasScheme(query)) {
            try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, query)) {
                answer = camelContext.getTypeConverter().mandatoryConvertTo(String.class, is);
            }
            answer = resolvePlaceholders(answer, placeholder);
        }
        return answer;
    }

    public static String resolvePlaceholders(String query, String placeholder) {
        String answer = query;
        if (placeholder != null) {
            answer = answer.replaceAll(placeholder, "?");
        }
        // skip lines with comments
        StringJoiner sj = new StringJoiner("\n");
        String[] lines = answer.split("\n");
        for (String line : lines) {
            String trim = line.trim();
            if (!trim.isEmpty() && !trim.startsWith("--")) {
                sj.add(line);
            }
        }
        answer = sj.toString();
        return answer;
    }

    public static Object lookupParameter(String nextParam, Exchange exchange, Object batchBody) {
        Object body = batchBody != null ? batchBody : exchange.getMessage().getBody();
        Map<?, ?> bodyMap = safeMap(exchange.getContext().getTypeConverter().tryConvertTo(Map.class, exchange, body));
        Map<?, ?> headersMap = safeMap(exchange.getIn().getHeaders());
        Map<?, ?> variablesMap = safeMap(exchange.getVariables());

        Object answer = null;
        if ((nextParam.startsWith("$simple{") || nextParam.startsWith("${")) && nextParam.endsWith("}")) {
            if (batchBody != null) {
                // in batch mode then need to work on a copy of the original exchange and set the batch body
                exchange = ExchangeHelper.createCopy(exchange, true);
                exchange.getMessage().setBody(batchBody);
            }
            Expression exp = exchange.getContext().resolveLanguage("simple").createExpression(nextParam);
            answer = exp.evaluate(exchange, Object.class);
        } else if (bodyMap.containsKey(nextParam)) {
            answer = bodyMap.get(nextParam);
        } else if (headersMap.containsKey(nextParam)) {
            answer = headersMap.get(nextParam);
        } else if (variablesMap.containsKey(nextParam)) {
            answer = variablesMap.get(nextParam);
        }

        return answer;
    }

    public static boolean hasParameter(String nextParam, Exchange exchange, Object body) {
        Map<?, ?> bodyMap = safeMap(exchange.getContext().getTypeConverter().tryConvertTo(Map.class, body));
        Map<?, ?> headersMap = safeMap(exchange.getIn().getHeaders());
        Map<?, ?> variablesMap = safeMap(exchange.getVariables());

        if ((nextParam.startsWith("$simple{") || nextParam.startsWith("${")) && nextParam.endsWith("}")) {
            return true;
        } else if (bodyMap.containsKey(nextParam)) {
            return true;
        } else if (headersMap.containsKey(nextParam)) {
            return true;
        } else if (variablesMap.containsKey(nextParam)) {
            return true;
        }

        return false;
    }

    private static Map<?, ?> safeMap(Map<?, ?> map) {
        return (map == null || map.isEmpty()) ? Collections.emptyMap() : map;
    }
}
