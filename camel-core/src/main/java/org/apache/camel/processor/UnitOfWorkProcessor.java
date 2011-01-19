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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultUnitOfWork;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * Ensures the {@link Exchange} is routed under the boundaries of an {@link org.apache.camel.spi.UnitOfWork}.
 * <p/>
 * Handles calling the {@link org.apache.camel.spi.UnitOfWork#done(org.apache.camel.Exchange)} method
 * when processing of an {@link Exchange} is complete.
 */
public final class UnitOfWorkProcessor extends DelegateAsyncProcessor {

    private static final transient Log LOG = LogFactory.getLog(UnitOfWorkProcessor.class);
    private final RouteContext routeContext;
    private final String routeId;

    public UnitOfWorkProcessor(Processor processor) {
        this(null, processor);
    }

    public UnitOfWorkProcessor(AsyncProcessor processor) {
        this(null, processor);
    }

    public UnitOfWorkProcessor(RouteContext routeContext, Processor processor) {
        super(processor);
        this.routeContext = routeContext;
        if (routeContext != null) {
            this.routeId = routeContext.getRoute().idOrCreate(routeContext.getCamelContext().getNodeIdFactory());
        } else {
            this.routeId = null;
        }
    }

    public UnitOfWorkProcessor(RouteContext routeContext, AsyncProcessor processor) {
        super(processor);
        this.routeContext = routeContext;
        if (routeContext != null) {
            this.routeId = routeContext.getRoute().idOrCreate(routeContext.getCamelContext().getNodeIdFactory());
        } else {
            this.routeId = null;
        }
    }

    @Override
    public String toString() {
        return "UnitOfWork(" + processor + ")";
    }

    public RouteContext getRouteContext() {
        return routeContext;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // if the exchange doesn't have from route id set, then set it if it originated
        // from this unit of work
        if (routeId != null && exchange.getFromRouteId() == null) {
            exchange.setFromRouteId(routeId);
        }

        if (exchange.getUnitOfWork() == null) {
            // If there is no existing UoW, then we should start one and
            // terminate it once processing is completed for the exchange.
            final DefaultUnitOfWork uow = new DefaultUnitOfWork(exchange);
            exchange.setUnitOfWork(uow);
            try {
                uow.start();
            } catch (Exception e) {
                callback.done(true);
                exchange.setException(e);
                return true;
            }

            // process the exchange
            try {
                return processor.process(exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // Order here matters. We need to complete the callbacks
                        // since they will likely update the exchange with some final results.
                        try {
                            callback.done(doneSync);
                        } finally {
                            doneUow(uow, exchange);
                        }
                    }
                });
            } catch (Throwable e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Caught unhandled exception while processing ExchangeId: " + exchange.getExchangeId(), e);
                }
                // fallback and catch any exceptions the process may not have caught
                // we must ensure to done the UoW in all cases and issue done on the callback
                doneUow(uow, exchange);
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        } else {
            // There was an existing UoW, so we should just pass through..
            // so that the guy the initiated the UoW can terminate it.
            return processor.process(exchange, callback);
        }
    }

    private void doneUow(DefaultUnitOfWork uow, Exchange exchange) {
        // unit of work is done
        try {
            if (exchange.getUnitOfWork() != null) {
                exchange.getUnitOfWork().done(exchange);
            }
        } catch (Throwable e) {
            LOG.warn("Exception occurred during done UnitOfWork for Exchange: " + exchange
                    + ". This exception will be ignored.", e);
        }
        try {
            uow.stop();
        } catch (Throwable e) {
            LOG.warn("Exception occurred during stopping UnitOfWork for Exchange: " + exchange
                    + ". This exception will be ignored.", e);
        }
        exchange.setUnitOfWork(null);
    }

}
