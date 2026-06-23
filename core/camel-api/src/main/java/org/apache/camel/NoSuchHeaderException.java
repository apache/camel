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
package org.apache.camel;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Thrown when a mandatory header is not available on the {@link Message} of an {@link Exchange}.
 * <p/>
 * Typically raised by {@link org.apache.camel.support.ExchangeHelper#getMandatoryHeader(Exchange, String, Class)} when
 * a processor requires a specific header to be present.
 *
 * @see org.apache.camel.support.ExchangeHelper#getMandatoryHeader(Exchange, String, Class)
 * @see NoSuchPropertyException
 * @see Message
 */
public class NoSuchHeaderException extends CamelExchangeException {

    private final String headerName;
    private final transient @Nullable Class<?> type;

    /**
     * @param message    the detail message
     * @param exchange   the exchange that caused the error
     * @param headerName the name of the missing header
     */
    public NoSuchHeaderException(String message, Exchange exchange, String headerName) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(exchange, "exchange"));
        this.headerName = Objects.requireNonNull(headerName, "headerName");
        this.type = null;
    }

    /**
     * @param exchange   the exchange that caused the error
     * @param headerName the name of the missing header
     * @param type       the expected header type, or {@code null} if no specific type is required
     */
    public NoSuchHeaderException(Exchange exchange, String headerName, @Nullable Class<?> type) {
        super("No '" + Objects.requireNonNull(headerName, "headerName") + "' header available"
              + (type != null ? " of type: " + type.getName() : "")
              + reason(Objects.requireNonNull(exchange, "exchange"), headerName), exchange);
        this.headerName = headerName;
        this.type = type;
    }

    public String getHeaderName() {
        return headerName;
    }

    public @Nullable Class<?> getType() {
        return type;
    }

    protected static String reason(Exchange exchange, String headerName) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(headerName, "headerName");
        Object value = exchange.getMessage().getHeader(headerName);
        return valueDescription(value);
    }

    static String valueDescription(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        return " but has value: " + value + " of type: " + value.getClass().getCanonicalName();
    }
}
