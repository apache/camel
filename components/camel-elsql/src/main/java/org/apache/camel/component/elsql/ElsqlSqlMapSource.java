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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public class ElsqlSqlMapSource extends MapSqlParameterSource {

    private final Exchange exchange;
    private final Map<?, ?> bodyMap;
    private final Map<?, ?> headersMap;

    public ElsqlSqlMapSource(Exchange exchange, Object body) {
        this.exchange = exchange;
        this.bodyMap = safeMap(exchange.getContext().getTypeConverter().tryConvertTo(Map.class, body));
        this.headersMap = safeMap(exchange.getIn().getHeaders());

        addValue("body", body);

        for (Map.Entry<?, ?> entry : bodyMap.entrySet()) {
            String name = entry.getKey().toString();
            Object value = entry.getValue();
            addValue(name, value);
        }
        for (Map.Entry<?, ?> entry : headersMap.entrySet()) {
            String name = entry.getKey().toString();
            Object value = entry.getValue();
            addValue(name, value);
        }
    }

    private static Map<?, ?> safeMap(Map<?, ?> map) {
        return (map == null || map.isEmpty()) ? Collections.emptyMap() : map;
    }

}
