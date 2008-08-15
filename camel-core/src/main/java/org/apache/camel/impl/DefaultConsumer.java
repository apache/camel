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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.ServiceHelper;

/**
 * A default consumer useful for implementation inheritance.
 *
 * @version $Revision$
 */
public class DefaultConsumer<E extends Exchange> extends ServiceSupport implements Consumer<E> {
    private Endpoint<E> endpoint;
    private Processor processor;
    private AsyncProcessor asyncProcessor;
    private ExceptionHandler exceptionHandler;

    public DefaultConsumer(Endpoint<E> endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
    }

    @Override
    public String toString() {
        return "Consumer on " + endpoint;
    }

    public Endpoint<E> getEndpoint() {
        return endpoint;
    }

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Provides an {@link AsyncProcessor} interface to the configured
     * processor on the consumer.  If the processor does not implement
     * the interface, it will be adapted so that it does.  
     */
    public AsyncProcessor getAsyncProcessor() {
        if (asyncProcessor == null) {
            asyncProcessor = AsyncProcessorTypeConverter.convert(processor);
        }
        return asyncProcessor;
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

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
    }

    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     * 
     * @param t the exception to handle
     */
    protected void handleException(Throwable t) {
        Throwable newt = (t == null) ? new Throwable("Handling [null] exception") : t;
        getExceptionHandler().handleException(newt);
    }
}
