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
 * Unchecked variant of {@link InvalidPayloadException}, thrown when the body of a {@link Message} cannot be converted
 * to the expected type.
 * <p/>
 * Used in contexts where a checked exception is not permitted, such as inside a {@link Processor} that calls
 * {@link Message#getMandatoryBody(Class)}.
 *
 * @see InvalidPayloadException
 * @see Message
 */
public class InvalidPayloadRuntimeException extends RuntimeExchangeException {

    private final transient @Nullable Class<?> type;

    /**
     * @param exchange the exchange that caused the error
     * @param type     the expected body type
     */
    public InvalidPayloadRuntimeException(Exchange exchange, Class<?> type) {
        this(exchange, type, exchange.getIn());
    }

    /**
     * @param exchange the exchange that caused the error
     * @param type     the expected body type
     * @param cause    the cause of the failure
     */
    public InvalidPayloadRuntimeException(Exchange exchange, Class<?> type, Throwable cause) {
        this(exchange, type, exchange.getIn(), cause);
    }

    /**
     * @param exchange the exchange that caused the error
     * @param type     the expected body type
     * @param message  the message with the invalid or missing payload
     */
    public InvalidPayloadRuntimeException(Exchange exchange, Class<?> type, Message message) {
        super("No body available of type: " + Objects.requireNonNull(type, "type").getName()
              + NoSuchPropertyException.valueDescription(Objects.requireNonNull(message, "message").getBody())
              + " on: " + message, Objects.requireNonNull(exchange, "exchange"));
        this.type = type;
    }

    /**
     * @param exchange the exchange that caused the error
     * @param type     the expected body type
     * @param message  the message with the invalid or missing payload
     * @param cause    the cause of the failure
     */
    public InvalidPayloadRuntimeException(Exchange exchange, Class<?> type, Message message, Throwable cause) {
        super("No body available of type: " + Objects.requireNonNull(type, "type").getName()
              + NoSuchPropertyException.valueDescription(Objects.requireNonNull(message, "message").getBody())
              + " on: " + message
              + ". Caused by: " + Objects.requireNonNull(cause, "cause").getMessage(),
              Objects.requireNonNull(exchange, "exchange"), cause);
        this.type = type;
    }

    /**
     * The expected type of the body
     */
    public @Nullable Class<?> getType() {
        return type;
    }
}
