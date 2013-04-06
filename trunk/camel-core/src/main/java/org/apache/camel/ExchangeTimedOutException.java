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
 * An exception thrown if an InOut exchange times out receiving the OUT message
 *
 * @version 
 */
public class ExchangeTimedOutException extends CamelExchangeException {
    private static final long serialVersionUID = -7899162905421788853L;

    private final long timeout;

    public ExchangeTimedOutException(Exchange exchange, long timeout) {
        super("The OUT message was not received within: " + timeout + " millis", exchange);
        this.timeout = timeout;
    }

    public ExchangeTimedOutException(Exchange exchange, long timeout, String message) {
        super("The OUT message was not received within: " + timeout + " millis due " + message, exchange);
        this.timeout = timeout;
    }

    /**
     * Return the timeout which expired in milliseconds
     */
    public long getTimeout() {
        return timeout;
    }
}