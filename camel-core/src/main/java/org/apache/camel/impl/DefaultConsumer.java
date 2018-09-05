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
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnitOfWorkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default consumer useful for implementation inheritance.
 *
 * @version 
 */
public class DefaultConsumer extends ServiceSupport implements Consumer, RouteAware {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private transient String consumerToString;
    private final Endpoint endpoint;
    private final Processor processor;
    private volatile AsyncProcessor asyncProcessor;
    private ExceptionHandler exceptionHandler;
    private Route route;

    public DefaultConsumer(Endpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
        this.exceptionHandler = new LoggingExceptionHandler(endpoint.getCamelContext(), getClass());
    }

    @Override
    public String toString() {
        if (consumerToString == null) {
            consumerToString = "Consumer[" + URISupport.sanitizeUri(endpoint.getEndpointUri()) + "]";
        }
        return consumerToString;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    /**
     * If the consumer needs to defer done the {@link org.apache.camel.spi.UnitOfWork} on
     * the processed {@link Exchange} then this method should be use to create and start
     * the {@link UnitOfWork} on the exchange.
     *
     * @param exchange the exchange
     * @return the created and started unit of work
     * @throws Exception is thrown if error starting the unit of work
     *
     * @see #doneUoW(org.apache.camel.Exchange)
     */
    public UnitOfWork createUoW(Exchange exchange) throws Exception {
        // if the exchange doesn't have from route id set, then set it if it originated
        // from this unit of work
        if (route != null && exchange.getFromRouteId() == null) {
            exchange.setFromRouteId(route.getId());
        }

        UnitOfWork uow = endpoint.getCamelContext().getUnitOfWorkFactory().createUnitOfWork(exchange);
        exchange.setUnitOfWork(uow);
        uow.start();
        return uow;
    }

    /**
     * If the consumer needs to defer done the {@link org.apache.camel.spi.UnitOfWork} on
     * the processed {@link Exchange} then this method should be executed when the consumer
     * is finished processing the message.
     *
     * @param exchange the exchange
     *
     * @see #createUoW(org.apache.camel.Exchange)
     */
    public void doneUoW(Exchange exchange) {
        UnitOfWorkHelper.doneUow(exchange.getUnitOfWork(), exchange);
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Provides an {@link org.apache.camel.AsyncProcessor} interface to the configured
     * processor on the consumer. If the processor does not implement the interface,
     * it will be adapted so that it does.
     */
    public synchronized AsyncProcessor getAsyncProcessor() {
        if (asyncProcessor == null) {            
            asyncProcessor = AsyncProcessorConverterHelper.convert(processor);
        }
        return asyncProcessor;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    protected void doStop() throws Exception {
        log.debug("Stopping consumer: {}", this);
        ServiceHelper.stopServices(processor);
    }

    protected void doStart() throws Exception {
        log.debug("Starting consumer: {}", this);
        ServiceHelper.startServices(processor);
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

    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     *
     * @param message additional message about the exception
     * @param t the exception to handle
     */
    protected void handleException(String message, Throwable t) {
        Throwable newt = (t == null) ? new IllegalArgumentException("Handling [null] exception") : t;
        getExceptionHandler().handleException(message, newt);
    }
}
