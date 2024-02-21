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
package org.apache.camel.language.joor;

import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * A set of helper as static imports for the Camel jOOR language.
 */
public final class JoorHelper {

    private JoorHelper() {
    }

    public static <T> T bodyAs(Message message, Class<T> type) {
        return message.getBody(type);
    }

    public static <T> Optional<T> optionalBodyAs(Message message, Class<T> type) {
        return Optional.ofNullable(message.getBody(type));
    }

    public static <T> T headerAs(Message message, String name, Class<T> type) {
        return message.getHeader(name, type);
    }

    public static <T> T headerAs(Message message, String name, Object defaultValue, Class<T> type) {
        return message.getHeader(name, defaultValue, type);
    }

    public static <T> Optional<T> optionalHeaderAs(Message message, String name, Class<T> type) {
        return Optional.ofNullable(message.getHeader(name, type));
    }

    public static <T> T exchangePropertyAs(Exchange exchange, String name, Class<T> type) {
        return exchange.getProperty(name, type);
    }

    public static <T> T exchangePropertyAs(Exchange exchange, String name, Object defaultValue, Class<T> type) {
        return exchange.getProperty(name, defaultValue, type);
    }

    public static <T> Optional<T> optionalExchangePropertyAs(Exchange exchange, String name, Class<T> type) {
        return Optional.ofNullable(exchange.getProperty(name, type));
    }

}
