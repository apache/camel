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
package org.apache.camel.component.redis;

import java.util.Collection;
import java.util.Map;

import org.apache.camel.Exchange;

class ExchangeConverter {
    String getKey(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.KEY, String.class);
    }

    String getStringValue(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, String.class);
    }

    Long getLongValue(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Long.class);
    }

    String getDestination(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.DESTINATION, String.class);
    }

    String getChannel(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.CHANNEL, String.class);
    }

    Object getMessage(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.MESSAGE, Object.class);
    }

    Long getIndex(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.INDEX, Long.class);
    }

    String getPivot(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.PIVOT, String.class);
    }

    String getPosition(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.POSITION, String.class);
    }

    Long getCount(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.COUNT, Long.class);
    }

    Long getStart(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.START, Long.class);
    }

    Long getEnd(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.END, Long.class);
    }

    Long getTimeout(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.TIMEOUT, Long.class);
    }

    Long getOffset(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.OFFSET, Long.class);
    }

    Long getValueAsLong(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Long.class);
    }

    Collection<String> getFields(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.FIELDS, Collection.class);
    }

    Map<String, Object> getValuesAsMap(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUES, Map.class);
    }

    Collection<String> getKeys(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.KEYS, Collection.class);
    }

    Object getValue(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Object.class);
    }

    Boolean getBooleanValue(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Boolean.class);
    }

    String getField(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.FIELD, String.class);
    }

    Long getTimestamp(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.TIMESTAMP, Long.class);
    }

    String getPattern(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.PATTERN, String.class);
    }

    Integer getDb(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.DB, Integer.class);
    }

    Double getScore(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.SCORE, Double.class);
    }

    Double getMin(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.MIN, Double.class);
    }

    Double getMax(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.MAX, Double.class);
    }

    Double getIncrement(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.INCREMENT, Double.class);
    }

    Boolean getWithScore(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.WITHSCORE, Boolean.class);
    }

    private static <T> T getInHeaderValue(Exchange exchange, String key, Class<T> aClass) {
        return exchange.getIn().getHeader(key, aClass);
    }

}
