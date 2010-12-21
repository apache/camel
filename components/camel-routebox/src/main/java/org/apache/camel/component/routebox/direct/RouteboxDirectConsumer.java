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
package org.apache.camel.component.routebox.direct;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.SuspendableService;
import org.apache.camel.component.routebox.RouteboxConsumer;
import org.apache.camel.component.routebox.RouteboxServiceSupport;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ShutdownAware;

public class RouteboxDirectConsumer extends RouteboxServiceSupport implements RouteboxConsumer, ShutdownAware, SuspendableService {
    protected ProducerTemplate producer;
    private final Processor processor;
    private volatile AsyncProcessor asyncProcessor;
    private ExceptionHandler exceptionHandler;

    public RouteboxDirectConsumer(RouteboxDirectEndpoint endpoint, Processor processor) {
        super(endpoint);
        this.processor = processor;
        producer = endpoint.getConfig().getInnerProducerTemplate();
    }
    
    protected void doStart() throws Exception {
        // add consumer to endpoint
        boolean existing = this == ((RouteboxDirectEndpoint)getRouteboxEndpoint()).getConsumer();
        if (!existing && ((RouteboxDirectEndpoint)getRouteboxEndpoint()).hasConsumer(this)) {
            throw new IllegalArgumentException("Cannot add a 2nd consumer to the same endpoint. Endpoint " + getRouteboxEndpoint() + " only allows one consumer.");
        }
        if (!existing) {
            ((RouteboxDirectEndpoint)getRouteboxEndpoint()).addConsumer(this);
        }
        
        // now start the inner context
        if (!isStartedInnerContext()) {
            doStartInnerContext(); 
        }
        
    }

    protected void doStop() throws Exception {
        ((RouteboxDirectEndpoint)getRouteboxEndpoint()).removeConsumer(this);
        
        // now stop the inner context
        if (isStartedInnerContext()) {
            doStopInnerContext();
        }

    }

    protected void doSuspend() throws Exception {
        ((RouteboxDirectEndpoint)getRouteboxEndpoint()).removeConsumer(this);
    }

    protected void doResume() throws Exception {
        // resume by using the start logic
        doStart();
    }
    
    public Exchange processRequest(Exchange exchange) {
        return exchange;
        
    }
    
    /**
     * Provides an {@link org.apache.camel.AsyncProcessor} interface to the configured
     * processor on the consumer. If the processor does not implement the interface,
     * it will be adapted so that it does.
     */
    public synchronized AsyncProcessor getAsyncProcessor() {
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

    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     * 
     * @param t the exception to handle
     */
    protected void handleException(Throwable t) {
        Throwable newt = (t == null) ? new IllegalArgumentException("Handling [null] exception") : t;
        getExceptionHandler().handleException(newt);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.spi.ShutdownAware#deferShutdown(org.apache.camel.ShutdownRunningTask)
     */
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // deny stopping on shutdown as we want direct consumers to run in case some other queues
        // depend on this consumer to run, so it can complete its exchanges
        return true;
    }

    /* (non-Javadoc)
     * @see org.apache.camel.spi.ShutdownAware#getPendingExchangesSize()
     */
    public int getPendingExchangesSize() {
        // return 0 as we do not have an internal memory queue with a variable size
        // of inflight messages. 
        return 0;
    }

    /* (non-Javadoc)
     * @see org.apache.camel.spi.ShutdownAware#prepareShutdown()
     */
    public void prepareShutdown() {
        
    }
    
    public Endpoint getEndpoint() {
        return (Endpoint) getRouteboxEndpoint();
    }

    public Processor getProcessor() {
        return processor;
    }

}
