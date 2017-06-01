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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.OrderedComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Shared (thread safe) internal {@link Processor} that Camel routing engine used during routing for cross cutting functionality such as:
 * <ul>
 *     <li>Execute {@link UnitOfWork}</li>
 *     <li>Keeping track which route currently is being routed</li>
 *     <li>Execute {@link RoutePolicy}</li>
 *     <li>Gather JMX performance statics</li>
 *     <li>Tracing</li>
 *     <li>Debugging</li>
 *     <li>Message History</li>
 *     <li>Stream Caching</li>
 *     <li>{@link Transformer}</li>
 * </ul>
 * ... and more.
 * <p/>
 * This implementation executes this cross cutting functionality as a {@link CamelInternalProcessorAdvice} advice (before and after advice)
 * by executing the {@link CamelInternalProcessorAdvice#before(Exchange)} and
 * {@link CamelInternalProcessorAdvice#after(Exchange, Object)} callbacks in correct order during routing.
 * This reduces number of stack frames needed during routing, and reduce the number of lines in stacktraces, as well
 * makes debugging the routing engine easier for end users.
 * <p/>
 * <b>Debugging tips:</b> Camel end users whom want to debug their Camel applications with the Camel source code, then make sure to
 * read the source code of this class about the debugging tips, which you can find in the
 * {@link #process(Exchange, AsyncCallback, AsyncProcessor, Processor)} method.
 * <p/>
 * The added advices can implement {@link Ordered} to control in which order the advices are executed.
 */
public class SharedCamelInternalProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SharedCamelInternalProcessor.class);
    private final List<CamelInternalProcessorAdvice> advices = new ArrayList<CamelInternalProcessorAdvice>();

    public SharedCamelInternalProcessor(CamelInternalProcessorAdvice... advices) {
        if (advices != null) {
            this.advices.addAll(Arrays.asList(advices));
            // ensure advices are sorted so they are in the order we want
            this.advices.sort(OrderedComparator.get());
        }
    }

    /**
     * Synchronous API
     */
    public void process(Exchange exchange, AsyncProcessor processor, Processor resultProcessor) {
        final AsyncProcessorAwaitManager awaitManager = exchange.getContext().getAsyncProcessorAwaitManager();
        final CountDownLatch latch = new CountDownLatch(1);

        boolean sync = process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (!doneSync) {
                    awaitManager.countDown(exchange, latch);
                }
            }

            @Override
            public String toString() {
                return "Done " + processor;
            }
        }, processor, resultProcessor);

        if (!sync) {
            awaitManager.await(exchange, latch);
        }
    }

    /**
     * Asynchronous API
     */
    public boolean process(Exchange exchange, AsyncCallback callback, AsyncProcessor processor, Processor resultProcessor) {
        // ----------------------------------------------------------
        // CAMEL END USER - READ ME FOR DEBUGGING TIPS
        // ----------------------------------------------------------
        // If you want to debug the Camel routing engine, then there is a lot of internal functionality
        // the routing engine executes during routing messages. You can skip debugging this internal
        // functionality and instead debug where the routing engine continues routing to the next node
        // in the routes. The CamelInternalProcessor is a vital part of the routing engine, as its
        // being used in between the nodes. As an end user you can just debug the code in this class
        // in between the:
        //   CAMEL END USER - DEBUG ME HERE +++ START +++
        //   CAMEL END USER - DEBUG ME HERE +++ END +++
        // you can see in the code below.
        // ----------------------------------------------------------

        if (processor == null || !continueProcessing(exchange, processor)) {
            // no processor or we should not continue then we are done
            callback.done(true);
            return true;
        }

        // optimise to use object array for states
        final Object[] states = new Object[advices.size()];
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < advices.size(); i++) {
            CamelInternalProcessorAdvice task = advices.get(i);
            try {
                Object state = task.before(exchange);
                states[i] = state;
            } catch (Throwable e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        // create internal callback which will execute the advices in reverse order when done
        callback = new InternalCallback(states, exchange, callback, resultProcessor);

        // UNIT_OF_WORK_PROCESS_SYNC is @deprecated and we should remove it from Camel 3.0
        Object synchronous = exchange.removeProperty(Exchange.UNIT_OF_WORK_PROCESS_SYNC);
        if (exchange.isTransacted() || synchronous != null) {
            // must be synchronized for transacted exchanges
            if (LOG.isTraceEnabled()) {
                if (exchange.isTransacted()) {
                    LOG.trace("Transacted Exchange must be routed synchronously for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
                } else {
                    LOG.trace("Synchronous UnitOfWork Exchange must be routed synchronously for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
                }
            }
            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ START +++
            // ----------------------------------------------------------
            try {
                processor.process(exchange);
            } catch (Throwable e) {
                exchange.setException(e);
            }
            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ END +++
            // ----------------------------------------------------------
            callback.done(true);
            return true;
        } else {
            final UnitOfWork uow = exchange.getUnitOfWork();

            // allow unit of work to wrap callback in case it need to do some special work
            // for example the MDCUnitOfWork
            AsyncCallback async = callback;
            if (uow != null) {
                async = uow.beforeProcess(processor, exchange, callback);
            }

            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ START +++
            // ----------------------------------------------------------
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchange for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
            }
            boolean sync = processor.process(exchange, async);
            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ END +++
            // ----------------------------------------------------------

            // execute any after processor work (in current thread, not in the callback)
            if (uow != null) {
                uow.afterProcess(processor, exchange, callback, sync);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Exchange processed and is continued routed {} for exchangeId: {} -> {}",
                        new Object[]{sync ? "synchronously" : "asynchronously", exchange.getExchangeId(), exchange});
            }
            return sync;
        }
    }

    /**
     * Internal callback that executes the after advices.
     */
    private final class InternalCallback implements AsyncCallback {

        private final Object[] states;
        private final Exchange exchange;
        private final AsyncCallback callback;
        private final Processor resultProcessor;

        private InternalCallback(Object[] states, Exchange exchange, AsyncCallback callback, Processor resultProcessor) {
            this.states = states;
            this.exchange = exchange;
            this.callback = callback;
            this.resultProcessor = resultProcessor;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void done(boolean doneSync) {
            // NOTE: if you are debugging Camel routes, then all the code in the for loop below is internal only
            // so you can step straight to the finally block and invoke the callback

            if (resultProcessor != null) {
                try {
                    resultProcessor.process(exchange);
                } catch (Throwable e) {
                    exchange.setException(e);
                }
            }

            // we should call after in reverse order
            try {
                for (int i = advices.size() - 1; i >= 0; i--) {
                    CamelInternalProcessorAdvice task = advices.get(i);
                    Object state = states[i];
                    try {
                        task.after(exchange, state);
                    } catch (Throwable e) {
                        exchange.setException(e);
                        // allow all advices to complete even if there was an exception
                    }
                }
            } finally {
                // ----------------------------------------------------------
                // CAMEL END USER - DEBUG ME HERE +++ START +++
                // ----------------------------------------------------------
                // callback must be called
                callback.done(doneSync);
                // ----------------------------------------------------------
                // CAMEL END USER - DEBUG ME HERE +++ END +++
                // ----------------------------------------------------------
            }
        }
    }

    /**
     * Strategy to determine if we should continue processing the {@link Exchange}.
     */
    protected boolean continueProcessing(Exchange exchange, AsyncProcessor processor) {
        Object stop = exchange.getProperty(Exchange.ROUTE_STOP);
        if (stop != null) {
            boolean doStop = exchange.getContext().getTypeConverter().convertTo(Boolean.class, stop);
            if (doStop) {
                LOG.debug("Exchange is marked to stop routing: {}", exchange);
                return false;
            }
        }

        // determine if we can still run, or the camel context is forcing a shutdown
        if (processor instanceof Service) {
            boolean forceShutdown = exchange.getContext().getShutdownStrategy().forceShutdown((Service) processor);
            if (forceShutdown) {
                String msg = "Run not allowed as ShutdownStrategy is forcing shutting down, will reject executing exchange: " + exchange;
                LOG.debug(msg);
                if (exchange.getException() == null) {
                    exchange.setException(new RejectedExecutionException(msg));
                }
                return false;
            }
        }

        // yes we can continue
        return true;
    }

}
