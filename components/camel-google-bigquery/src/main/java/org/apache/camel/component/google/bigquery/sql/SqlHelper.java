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
package org.apache.camel.component.google.bigquery.sql;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.support.ResourceHelper;

public final class SqlHelper {

    private static Pattern pattern = Pattern.compile("\\$\\{(\\w+)}");
    private static Pattern parameterPattern = Pattern.compile("@(\\w+)");

    private SqlHelper() {
    }

    /**
     * Resolve the query by loading the query from the classpath or file
     * resource if needed.
     */
    public static String resolveQuery(CamelContext camelContext, String query, String placeholder) throws NoTypeConversionAvailableException, IOException {
        String answer = query;
        if (ResourceHelper.hasScheme(query)) {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, query);
            answer = camelContext.getTypeConverter().mandatoryConvertTo(String.class, is);
            if (placeholder != null) {
                answer = answer.replaceAll(placeholder, "@");
            }
        }
        return answer;
    }

    /**
     * Replaces pattern in query in form of "${param}" with values from message
     * header Raises an error if param value not found in headers
     * 
     * @param exchange
     * @return Translated query text
     */
    public static String translateQuery(String query, Exchange exchange) {
        Message message = exchange.getMessage();
        Matcher matcher = pattern.matcher(query);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
            String paramKey = matcher.group(1);

            String value = message.getHeader(paramKey, String.class);
            if (value == null) {
                value = exchange.getProperty(paramKey, String.class);
                if (value == null) {
                    throw new RuntimeExchangeException("SQL pattern with name '" + paramKey + "' not found in the message headers", exchange);
                }
            }

            String replacement = Matcher.quoteReplacement(value);
            matcher.appendReplacement(stringBuffer, replacement);
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    /**
     * Extracts list of parameters in form "@name" from query text
     * 
     * @param query
     * @return list of parameter names
     */
    public static Set<String> extractParameterNames(String query) {
        Matcher matcher = parameterPattern.matcher(query);
        Set<String> result = new HashSet<>();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            result.add(paramName);
        }
        return result;
    }
}
