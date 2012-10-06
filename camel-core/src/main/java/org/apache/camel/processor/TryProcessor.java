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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
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
public class TryProcessor extends ServiceSupport implements AsyncProcessor, Navigate<Processor>, Traceable {
    private static final transient Logger LOG = LoggerFactory.getLogger(TryProcessor.class);

    protected final AsyncProcessor tryProcessor;
    protected final DoCatchProcessor catchProcessor;
    protected final DoFinallyProcessor finallyProcessor;
    private List<AsyncProcessor> processors;

    public TryProcessor(Processor tryProcessor, List<CatchProcessor> catchClauses, Processor finallyProcessor) {
        this.tryProcessor = AsyncProcessorConverterHelper.convert(tryProcessor);
        this.catchProcessor = new DoCatchProcessor(catchClauses);
        this.finallyProcessor = new DoFinallyProcessor(finallyProcessor);
    }

    public String toString() {
        String finallyText = (finallyProcessor == null) ? "" : " Finally {" + finallyProcessor + "}";
        return "Try {" + tryProcessor + "} " + (catchProcessor != null ? catchProcessor : "") + finallyText;
    }

    public String getTraceLabel() {
        return "doTry";
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        Iterator<AsyncProcessor> processors = getProcessors().iterator();

        while (continueRouting(processors, exchange)) {
            ExchangeHelper.prepareOutToIn(exchange);

            // process the next processor
            AsyncProcessor processor = processors.next();
            boolean sync = process(exchange, callback, processor, processors);

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
        LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
        callback.done(true);
        return true;
    }

    protected boolean process(final Exchange exchange, final AsyncCallback callback,
                              final AsyncProcessor processor, final Iterator<AsyncProcessor> processors) {
        // this does the actual processing so log at trace level
        LOG.trace("Processing exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);

        // implement asynchronous routing logic in callback so we can have the callback being
        // triggered and then continue routing where we left
        boolean sync = AsyncProcessorHelper.process(processor, exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // we only have to handle async completion of the pipeline
                if (doneSync) {
                    return;
                }

                // continue processing the try .. catch .. finally asynchronously
                while (continueRouting(processors, exchange)) {
                    ExchangeHelper.prepareOutToIn(exchange);

                    // process the next processor
                    AsyncProcessor processor = processors.next();
                    doneSync = process(exchange, callback, processor, processors);

                    if (!doneSync) {
                        LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                        // the remainder of the try .. catch .. finally will be completed async
                        // so we break out now, then the callback will be invoked which then continue routing from where we left here
                        return;
                    }
                }

                ExchangeHelper.prepareOutToIn(exchange);
                LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                callback.done(false);
            }
        });

        return sync;
    }

    protected Collection<AsyncProcessor> getProcessors() {
        return processors;
    }

    protected boolean continueRouting(Iterator<AsyncProcessor> it, Exchange exchange) {
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
        processors = new ArrayList<AsyncProcessor>();
        processors.add(tryProcessor);
        processors.add(catchProcessor);
        processors.add(finallyProcessor);
        ServiceHelper.startServices(tryProcessor, catchProcessor, finallyProcessor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(finallyProcessor, catchProcessor, tryProcessor);
        processors.clear();
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>();
        if (tryProcessor != null) {
            answer.add(tryProcessor);
        }
        if (catchProcessor != null) {
            answer.add(catchProcessor);
        }
        if (finallyProcessor != null) {
            answer.add(finallyProcessor);
        }
        return answer;
    }

    public boolean hasNext() {
        return tryProcessor != null;
    }

    /**
     * Processor to handle do catch supporting asynchronous routing engine
     */
    private final class DoCatchProcessor extends ServiceSupport implements AsyncProcessor, Navigate<Processor>, Traceable {

        private final List<CatchProcessor> catchClauses;

        private DoCatchProcessor(List<CatchProcessor> catchClauses) {
            this.catchClauses = catchClauses;
        }

        public void process(Exchange exchange) throws Exception {
            AsyncProcessorHelper.process(this, exchange);
        }

        public boolean process(final Exchange exchange, final AsyncCallback callback) {
            Exception e = exchange.getException();

            if (catchClauses == null || e == null) {
                return true;
            }

            // find a catch clause to use
            CatchProcessor processor = null;
            for (CatchProcessor catchClause : catchClauses) {
                Throwable caught = catchClause.catches(exchange, e);
                if (caught != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("This TryProcessor catches the exception: {} caused by: {}", caught.getClass().getName(), e.getMessage());
                    }
                    processor = catchClause;
                    break;
                }
            }

            if (processor != null) {
                // create the handle processor which performs the actual logic
                // this processor just lookup the right catch clause to use and then let the
                // HandleDoCatchProcessor do all the hard work (separate of concerns)
                HandleDoCatchProcessor cool = new HandleDoCatchProcessor(processor);
                return AsyncProcessorHelper.process(cool, exchange, callback);
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("This TryProcessor does not catch the exception: {} caused by: {}", e.getClass().getName(), e.getMessage());
                }
            }

            return true;
        }

        @Override
        protected void doStart() throws Exception {
            ServiceHelper.startService(catchClauses);
        }

        @Override
        protected void doStop() throws Exception {
            ServiceHelper.stopServices(catchClauses);
        }

        @Override
        public String toString() {
            return "Catches{" + catchClauses + "}";
        }

        public String getTraceLabel() {
            return "doCatch";
        }

        public List<Processor> next() {
            List<Processor> answer = new ArrayList<Processor>();
            if (catchProcessor != null) {
                answer.addAll(catchClauses);
            }
            return answer;
        }

        public boolean hasNext() {
            return catchClauses != null && catchClauses.size() > 0;
        }
    }

    /**
     * Processor to handle do finally supporting asynchronous routing engine
     */
    private final class DoFinallyProcessor extends DelegateAsyncProcessor implements Traceable {

        private DoFinallyProcessor(Processor processor) {
            super(processor);
        }

        @Override
        protected boolean processNext(final Exchange exchange, final AsyncCallback callback) {
            // clear exception so finally block can be executed
            final Exception e = exchange.getException();
            exchange.setException(null);
            // but store the caught exception as a property
            if (e != null) {
                exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
            }
            // store the last to endpoint as the failure endpoint
            if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
                exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
            }

            boolean sync = super.processNext(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // we only have to handle async completion of the pipeline
                    if (doneSync) {
                        return;
                    }

                    if (e == null) {
                        exchange.removeProperty(Exchange.FAILURE_ENDPOINT);
                    } else {
                        // set exception back on exchange
                        exchange.setException(e);
                        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
                    }

                    // signal callback to continue routing async
                    ExchangeHelper.prepareOutToIn(exchange);
                    LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                    callback.done(false);
                }
            });

            if (sync) {
                if (e == null) {
                    exchange.removeProperty(Exchange.FAILURE_ENDPOINT);
                } else {
                    // set exception back on exchange
                    exchange.setException(e);
                    exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
                }
            }

            return sync;
        }

        @Override
        public String toString() {
            return "Finally{" + getProcessor() + "}";
        }

        public String getTraceLabel() {
            return "doFinally";
        }
    }

    /**
     * Processor to handle do catch supporting asynchronous routing engine
     */
    private final class HandleDoCatchProcessor extends DelegateAsyncProcessor {

        private final CatchProcessor catchClause;

        private HandleDoCatchProcessor(CatchProcessor processor) {
            super(processor);
            this.catchClause = processor;
        }

        @Override
        protected boolean processNext(final Exchange exchange, final AsyncCallback callback) {
            final Exception caught = exchange.getException();
            if (caught == null) {
                return true;
            }

            // store the last to endpoint as the failure endpoint
            if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
                exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
            }
            // give the rest of the pipeline another chance
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, caught);
            exchange.setException(null);
            // and we should not be regarded as exhausted as we are in a try .. catch block
            exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);

            // is the exception handled by the catch clause
            final Boolean handled = catchClause.handles(exchange);

            if (LOG.isDebugEnabled()) {
                LOG.debug("The exception is handled: {} for the exception: {} caused by: {}",
                        new Object[]{handled, caught.getClass().getName(), caught.getMessage()});
            }

            boolean sync = super.processNext(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // we only have to handle async completion of the pipeline
                    if (doneSync) {
                        return;
                    }

                    if (!handled) {
                        if (exchange.getException() == null) {
                            exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
                        }
                    }
                    // always clear redelivery exhausted in a catch clause
                    exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);

                    // signal callback to continue routing async
                    ExchangeHelper.prepareOutToIn(exchange);
                    callback.done(false);
                }
            });

            if (sync) {
                // set exception back on exchange
                if (!handled) {
                    if (exchange.getException() == null) {
                        exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
                    }
                }
                // always clear redelivery exhausted in a catch clause
                exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);
            }

            return sync;
        }
    }

}
