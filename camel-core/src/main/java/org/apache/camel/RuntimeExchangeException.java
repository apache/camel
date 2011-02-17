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
package org.apache.camel;

/**
 * A runtime exception caused by a specific message {@link Exchange}
 *
 * @version 
 */
public class RuntimeExchangeException extends RuntimeCamelException {
    private static final long serialVersionUID = -8721487431101572630L;
    private final transient Exchange exchange;

    public RuntimeExchangeException(String message, Exchange exchange) {
        super(createMessage(message, exchange));
        this.exchange = exchange;
    }

    public RuntimeExchangeException(String message, Exchange exchange, Throwable cause) {
        super(createMessage(message, exchange), cause);
        this.exchange = exchange;
    }

    /**
     * Returns the exchange which caused the exception
     * <p/>
     * Can be <tt>null</tt>
     */
    public Exchange getExchange() {
        return exchange;
    }

    protected static String createMessage(String message, Exchange exchange) {
        if (exchange != null) {
            return message + " on the exchange: " + exchange;
        } else {
            return message;
        }
    }

}