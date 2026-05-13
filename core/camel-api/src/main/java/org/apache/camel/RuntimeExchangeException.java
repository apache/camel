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
 * A runtime exception caused by a specific message {@link Exchange}
 */
public class RuntimeExchangeException extends RuntimeCamelException {
    private final transient @Nullable Exchange exchange;

    /**
     * @param message  the detail message
     * @param exchange the exchange that caused the error
     */
    public RuntimeExchangeException(String message, @Nullable Exchange exchange) {
        super(createMessage(Objects.requireNonNull(message, "message"), exchange));
        this.exchange = exchange;
    }

    /**
     * @param message  the detail message
     * @param exchange the exchange that caused the error
     * @param cause    the cause of the failure
     */
    public RuntimeExchangeException(String message, @Nullable Exchange exchange, Throwable cause) {
        super(createMessage(Objects.requireNonNull(message, "message"), exchange),
              Objects.requireNonNull(cause, "cause"));
        this.exchange = exchange;
    }

    /**
     * Returns the exchange which caused the exception
     * <p/>
     * Can be <tt>null</tt>
     */
    public @Nullable Exchange getExchange() {
        return exchange;
    }

    protected static String createMessage(String message, @Nullable Exchange exchange) {
        Objects.requireNonNull(message, "message");
        if (exchange != null) {
            return message + " on the exchange: " + exchange;
        } else {
            return message;
        }
    }

}
