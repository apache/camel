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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements try/catch/finally type processing
 */
public class TryProcessor extends AsyncProcessorSupport implements Navigate<Processor>, Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(TryProcessor.class);

    protected final CamelContext camelContext;
    protected final ReactiveExecutor reactiveExecutor;
    protected String id;
    protected String routeId;
    protected final Processor tryProcessor;
    protected final List<Processor> catchClauses;
    protected final Processor finallyProcessor;

    public TryProcessor(CamelContext camelContext, Processor tryProcessor, List<Processor> catchClauses, Processor finallyProcessor) {
        this.camelContext = camelContext;
        this.reactiveExecutor = camelContext.adapt(ExtendedCamelContext.class).getReactiveExecutor();
        this.tryProcessor = tryProcessor;
        this.catchClauses = catchClauses;
        this.finallyProcessor = finallyProcessor;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "doTry";
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        reactiveExecutor.schedule(new TryState(exchange, callback));
        return false;
    }

    class TryState implements Runnable {

        final Exchange exchange;
        final AsyncCallback callback;
        final Iterator<Processor> processors;
        final Object lastHandled;

        public TryState(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
            this.processors = next().iterator();
            this.lastHandled = exchange.getProperty(Exchange.EXCEPTION_HANDLED);
            exchange.setProperty(Exchange.EXCEPTION_HANDLED, null);
        }

        @Override
        public void run() {
            if (continueRouting(processors, exchange)) {
                exchange.setProperty(Exchange.TRY_ROUTE_BLOCK, true);
                ExchangeHelper.prepareOutToIn(exchange);

                // process the next processor
                Processor processor = processors.next();
                AsyncProcessor async = AsyncProcessorConverterHelper.convert(processor);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                }
                async.process(exchange, doneSync -> reactiveExecutor.schedule(this));
            } else {
                ExchangeHelper.prepareOutToIn(exchange);
                exchange.removeProperty(Exchange.TRY_ROUTE_BLOCK);
                exchange.setProperty(Exchange.EXCEPTION_HANDLED, lastHandled);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                }
                callback.done(false);
            }
        }

        @Override
        public String toString() {
            return "TryState";
        }
    }

    protected boolean continueRouting(Iterator<Processor> it, Exchange exchange) {
        if (exchange.isRouteStop()) {
            LOG.debug("Exchange is marked to stop routing: {}", exchange);
            return false;
        }

        // continue if there are more processors to route
        return it.hasNext();
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(tryProcessor, catchClauses, finallyProcessor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(tryProcessor, catchClauses, finallyProcessor);
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>();
        if (tryProcessor != null) {
            answer.add(tryProcessor);
        }
        if (catchClauses != null) {
            answer.addAll(catchClauses);
        }
        if (finallyProcessor != null) {
            answer.add(finallyProcessor);
        }
        return answer;
    }

    @Override
    public boolean hasNext() {
        return tryProcessor != null || catchClauses != null && !catchClauses.isEmpty() || finallyProcessor != null;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }
}
