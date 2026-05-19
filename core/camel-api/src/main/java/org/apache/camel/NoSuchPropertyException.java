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
 * Thrown when a mandatory exchange property is not available on an {@link Exchange}.
 * <p/>
 * Typically raised by {@link org.apache.camel.support.ExchangeHelper#getMandatoryProperty(Exchange, String, Class)}
 * when a processor or EIP requires a specific property to be present.
 *
 * @see org.apache.camel.support.ExchangeHelper#getMandatoryProperty(Exchange, String, Class)
 * @see NoSuchHeaderException
 */
public class NoSuchPropertyException extends CamelExchangeException {

    private final String propertyName;
    private final transient @Nullable Class<?> type;

    /**
     * @param exchange     the exchange that caused the error
     * @param propertyName the name of the missing exchange property
     */
    public NoSuchPropertyException(Exchange exchange, String propertyName) {
        this(exchange, propertyName, null);
    }

    /**
     * @param exchange     the exchange that caused the error
     * @param propertyName the name of the missing exchange property
     * @param type         the expected property type, or {@code null} if no specific type is required
     */
    public NoSuchPropertyException(Exchange exchange, String propertyName, @Nullable Class<?> type) {
        super("No '" + Objects.requireNonNull(propertyName, "propertyName") + "' exchange property available"
              + (type != null ? " of type: " + type.getName() : "")
              + reason(Objects.requireNonNull(exchange, "exchange"), propertyName), exchange);
        this.propertyName = propertyName;
        this.type = type;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public @Nullable Class<?> getType() {
        return type;
    }

    protected static String reason(Exchange exchange, String propertyName) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(propertyName, "propertyName");
        Object value = exchange.getProperty(propertyName);
        return valueDescription(value);
    }

    static String valueDescription(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        return " but has type: " + value.getClass().getCanonicalName();
    }
}
