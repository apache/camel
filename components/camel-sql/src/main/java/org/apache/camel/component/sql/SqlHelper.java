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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;

public final class SqlHelper {

    private SqlHelper() {
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
