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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ReactiveHelper;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Implements try/catch/finally type processing
 */
public class TryProcessor extends AsyncProcessorSupport implements Navigate<Processor>, Traceable, IdAware {

    protected String id;
    protected final Processor tryProcessor;
    protected final List<Processor> catchClauses;
    protected final Processor finallyProcessor;

    public TryProcessor(Processor tryProcessor, List<Processor> catchClauses, Processor finallyProcessor) {
        this.tryProcessor = tryProcessor;
        this.catchClauses = catchClauses;
        this.finallyProcessor = finallyProcessor;
    }

    public String toString() {
        String catchText = catchClauses == null || catchClauses.isEmpty() ? "" : " Catches {" + catchClauses + "}";
        String finallyText = (finallyProcessor == null) ? "" : " Finally {" + finallyProcessor + "}";
        return "Try {" + tryProcessor + "}" + catchText + finallyText;
    }

    public String getTraceLabel() {
        return "doTry";
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {

        ReactiveHelper.schedule(new TryState(exchange, callback));
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
                log.trace("Processing exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                async.process(exchange, doneSync -> ReactiveHelper.schedule(this));
            } else {
                ExchangeHelper.prepareOutToIn(exchange);
                exchange.removeProperty(Exchange.TRY_ROUTE_BLOCK);
                exchange.setProperty(Exchange.EXCEPTION_HANDLED, lastHandled);
                log.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                callback.done(false);
            }
        }

        public String toString() {
            return "TryState[" + exchange.getExchangeId() + "]";
        }
    }

    protected boolean continueRouting(Iterator<Processor> it, Exchange exchange) {
        Object stop = exchange.getProperty(Exchange.ROUTE_STOP);
        if (stop != null) {
            boolean doStop = exchange.getContext().getTypeConverter().convertTo(Boolean.class, stop);
            if (doStop) {
                log.debug("Exchange is marked to stop routing: {}", exchange);
                return false;
            }
        }

        // continue if there are more processors to route
        return it.hasNext();
    }

    protected void doStart() throws Exception {
        ServiceHelper.startService(tryProcessor, catchClauses, finallyProcessor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(tryProcessor, catchClauses, finallyProcessor);
    }

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

    public boolean hasNext() {
        return tryProcessor != null || catchClauses != null && !catchClauses.isEmpty() || finallyProcessor != null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
