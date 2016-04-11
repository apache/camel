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
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements try/catch/finally type processing
 *
 * @version 
 */
public class TryProcessor extends ServiceSupport implements AsyncProcessor, Navigate<Processor>, Traceable, IdAware {
    private static final Logger LOG = LoggerFactory.getLogger(TryProcessor.class);

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

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        Iterator<Processor> processors = next().iterator();

        Object lastHandled = exchange.getProperty(Exchange.EXCEPTION_HANDLED);
        exchange.setProperty(Exchange.EXCEPTION_HANDLED, null);

        while (continueRouting(processors, exchange)) {
            exchange.setProperty(Exchange.TRY_ROUTE_BLOCK, true);
            ExchangeHelper.prepareOutToIn(exchange);

            // process the next processor
            Processor processor = processors.next();
            AsyncProcessor async = AsyncProcessorConverterHelper.convert(processor);
            boolean sync = process(exchange, callback, processors, async, lastHandled);

            // continue as long its being processed synchronously
            if (!sync) {
                LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                // the remainder of the try .. catch .. finally will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            LOG.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());
        }

        ExchangeHelper.prepareOutToIn(exchange);
        exchange.removeProperty(Exchange.TRY_ROUTE_BLOCK);
        exchange.setProperty(Exchange.EXCEPTION_HANDLED, lastHandled);
        LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
        callback.done(true);
        return true;
    }

    protected boolean process(final Exchange exchange, final AsyncCallback callback,
                              final Iterator<Processor> processors, final AsyncProcessor processor,
                              final Object lastHandled) {
        // this does the actual processing so log at trace level
        LOG.trace("Processing exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);

        // implement asynchronous routing logic in callback so we can have the callback being
        // triggered and then continue routing where we left
        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // we only have to handle async completion of the pipeline
                if (doneSync) {
                    return;
                }

                // continue processing the try .. catch .. finally asynchronously
                while (continueRouting(processors, exchange)) {
                    exchange.setProperty(Exchange.TRY_ROUTE_BLOCK, true);
                    ExchangeHelper.prepareOutToIn(exchange);

                    // process the next processor
                    AsyncProcessor processor = AsyncProcessorConverterHelper.convert(processors.next());
                    doneSync = process(exchange, callback, processors, processor, lastHandled);

                    if (!doneSync) {
                        LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                        // the remainder of the try .. catch .. finally will be completed async
                        // so we break out now, then the callback will be invoked which then continue routing from where we left here
                        return;
                    }
                }

                ExchangeHelper.prepareOutToIn(exchange);
                exchange.removeProperty(Exchange.TRY_ROUTE_BLOCK);
                exchange.setProperty(Exchange.EXCEPTION_HANDLED, lastHandled);
                LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                callback.done(false);
            }
        });

        return sync;
    }

    protected boolean continueRouting(Iterator<Processor> it, Exchange exchange) {
        Object stop = exchange.getProperty(Exchange.ROUTE_STOP);
        if (stop != null) {
            boolean doStop = exchange.getContext().getTypeConverter().convertTo(Boolean.class, stop);
            if (doStop) {
                LOG.debug("Exchange is marked to stop routing: {}", exchange);
                return false;
            }
        }

        // continue if there are more processors to route
        return it.hasNext();
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(tryProcessor, catchClauses, finallyProcessor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(tryProcessor, catchClauses, finallyProcessor);
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>();
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
