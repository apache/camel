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
 * Is thrown if the payload from the exchange could not be retrieved because of being null, wrong class type etc.
 */
public class InvalidPayloadException extends CamelExchangeException {

    private final transient Class<?> type;

    public InvalidPayloadException(Exchange exchange, Class<?> type) {
        this(exchange, type, exchange.getIn());
    }

    public InvalidPayloadException(Exchange exchange, Class<?> type, Message message) {
        super("No body available of type: " + type.getCanonicalName()
              + NoSuchPropertyException.valueDescription(message.getBody()) + " on: " + message, exchange);
        this.type = type;
    }

    public InvalidPayloadException(Exchange exchange, Class<?> type, Message message, Throwable cause) {
        super("No body available of type: " + type.getCanonicalName()
              + NoSuchPropertyException.valueDescription(message.getBody()) + " on: " + message
              + ". Caused by: " + cause.getMessage(), exchange, cause);
        this.type = type;
    }

    /**
     * The expected type of the body
     */
    public Class<?> getType() {
        return type;
    }
}
