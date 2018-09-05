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
package org.apache.camel.component.reactive.streams.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.apache.camel.Exchange;

public final class BodyConverter<T> implements Function<Exchange, T> {
    private static final ConcurrentMap<Class<?>, BodyConverter<?>> CACHE = new ConcurrentHashMap<>();

    private final Class<T> type;

    BodyConverter(Class<T> type) {
        this.type = type;
    }

    @Override
    public T apply(Exchange exchange) {
        T answer;

        if (exchange.hasOut()) {
            answer = exchange.getOut().getBody(type);
        } else {
            answer = exchange.getIn().getBody(type);
        }

        return answer;
    }

    public static <C> BodyConverter<C> forType(Class<C> type) {
        return BodyConverter.class.cast(
            CACHE.computeIfAbsent(type, BodyConverter::new)
        );
    }
}
