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
package org.apache.camel.impl;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.spi.ExceptionHandler;

/**
 * A useful base class for implementations of {@link PollingConsumer}
 * 
 * @version $Revision$
 */
public abstract class PollingConsumerSupport<E extends Exchange> extends ServiceSupport implements
    PollingConsumer<E> {
    private final Endpoint<E> endpoint;
    private ExceptionHandler exceptionHandler;

    public PollingConsumerSupport(Endpoint<E> endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String toString() {
        return "PullConsumer on " + endpoint;
    }

    public Endpoint<E> getEndpoint() {
        return endpoint;
    }

    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(getClass());
        }
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     * 
     * @param t the exception to handle
     */
    protected void handleException(Throwable t) {
        getExceptionHandler().handleException(t);
    }
}
