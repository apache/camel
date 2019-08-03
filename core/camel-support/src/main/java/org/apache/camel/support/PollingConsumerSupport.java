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
package org.apache.camel.support;

import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A useful base class for implementations of {@link PollingConsumer}
 */
public abstract class PollingConsumerSupport extends ServiceSupport implements PollingConsumer {

    private final Endpoint endpoint;
    private ExceptionHandler exceptionHandler;

    public PollingConsumerSupport(Endpoint endpoint) {
        this.endpoint = endpoint;
        this.exceptionHandler = new LoggingExceptionHandler(endpoint.getCamelContext(), getClass());
    }

    @Override
    public String toString() {
        return "PollingConsumer on " + endpoint;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public Processor getProcessor() {
        return null;
    }

    public ExceptionHandler getExceptionHandler() {
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
