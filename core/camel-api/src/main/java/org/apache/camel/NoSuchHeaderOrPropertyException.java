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

public class NoSuchHeaderOrPropertyException extends CamelExchangeException {

    private final String headerName;
    private final String propertyName;
    private final transient Class<?> type;

    public NoSuchHeaderOrPropertyException(Exchange exchange, String headerName, String propertyName, Class<?> type) {
        super(String.format(
                "No '%s' header or '%s' property available of type: %s (header: %s, property: %s)",
                headerName,
                propertyName,
                type.getName(),
                header(exchange, headerName),
                property(exchange, headerName)),
              exchange);

        this.headerName = headerName;
        this.propertyName = propertyName;
        this.type = type;
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Class<?> getType() {
        return type;
    }

    protected static String header(Exchange exchange, String headerName) {
        Object value = exchange.getMessage().getHeader(headerName);
        return valueDescription(value);
    }

    protected static String property(Exchange exchange, String propertyName) {
        Object value = exchange.getProperty(propertyName);
        return valueDescription(value);
    }

    static String valueDescription(Object value) {
        if (value == null) {
            return "null";
        }
        return "has value: " + value + " of type: " + value.getClass().getCanonicalName();
    }
}
