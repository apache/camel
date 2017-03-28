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
package org.apache.camel.component.elsql;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.language.simple.SimpleLanguage;
import org.springframework.jdbc.core.namedparam.AbstractSqlParameterSource;

/**
 * A {@link org.springframework.jdbc.core.namedparam.SqlParameterSource} that is used by {@link com.opengamma.elsql.ElSql}
 * to lookup parameter values. This source will lookup in the Camel {@link Exchange} and {@link org.apache.camel.Message}
 * assuming they are Map based.
 */
public class ElsqlSqlMapSource extends AbstractSqlParameterSource {

    // use the maps from the Camel Message as they are case insensitive which makes it easier for end users to work with
    private final Exchange exchange;
    private final Map<?, ?> bodyMap;
    private final Map<?, ?> headersMap;

    public ElsqlSqlMapSource(Exchange exchange, Object body) {
        this.exchange = exchange;
        this.bodyMap = safeMap(exchange.getContext().getTypeConverter().tryConvertTo(Map.class, body));
        this.headersMap = safeMap(exchange.getIn().getHeaders());
    }

    private static Map<?, ?> safeMap(Map<?, ?> map) {
        return (map == null || map.isEmpty()) ? Collections.emptyMap() : map;
    }

    @Override
    public boolean hasValue(String paramName) {
        if ("body".equals(paramName)) {
            return true;
        } else if (paramName.startsWith("${") && paramName.endsWith("}")) {
            return true;
        } else {
            return bodyMap.containsKey(paramName) || headersMap.containsKey(paramName);
        }
    }

    @Override
    public Object getValue(String paramName) throws IllegalArgumentException {
        Object answer;
        if ("body".equals(paramName)) {
            answer = exchange.getIn().getBody();
        } else if (paramName.startsWith("${") && paramName.endsWith("}")) {
            // its a simple language expression
            answer = SimpleLanguage.expression(paramName).evaluate(exchange, Object.class);
        } else {
            answer = bodyMap.get(paramName);
            if (answer == null) {
                answer = headersMap.get(paramName);
            }
        }
        return answer;
    }
}
