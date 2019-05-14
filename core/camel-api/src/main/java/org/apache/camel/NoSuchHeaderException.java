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

/**
 * An exception caused when a mandatory header is not available on a message
 * {@link Exchange}
 *
 * @see org.apache.camel.support.ExchangeHelper#getMandatoryHeader(Exchange, String, Class)
 */
public class NoSuchHeaderException extends CamelExchangeException {

    private final String headerName;
    private final transient Class<?> type;

    public NoSuchHeaderException(Exchange exchange, String headerName, Class<?> type) {
        super("No '" + headerName + "' header available of type: " + type.getName()
              + reason(exchange, headerName), exchange);
        this.headerName = headerName;
        this.type = type;
    }

    public String getHeaderName() {
        return headerName;
    }

    public Class<?> getType() {
        return type;
    }

    protected static String reason(Exchange exchange, String propertyName) {
        Object value = exchange.getProperty(propertyName);
        return valueDescription(value);
    }

    static String valueDescription(Object value) {
        if (value == null) {
            return "";
        }
        return " but has value: " + value + " of type: " + value.getClass().getCanonicalName();
    }
}