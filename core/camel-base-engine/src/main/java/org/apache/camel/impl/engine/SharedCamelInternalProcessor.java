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

package org.apache.camel.impl.engine;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.SharedInternalProcessor;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.PluginHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Shared (thread safe) internal {@link Processor} that Camel routing engine used during routing for cross cutting
 * functionality such as:
 * <ul>
 * <li>Execute {@link UnitOfWork}</li>
 * <li>Keeping track which route currently is being routed</li>
 * <li>Execute {@link RoutePolicy}</li>
 * <li>Gather JMX performance statics</li>
 * <li>Tracing</li>
 * <li>Debugging</li>
 * <li>Message History</li>
 * <li>Stream Caching</li>
 * <li>{@link Transformer}</li>
 * </ul>
 * ... and more.
 * <p/>
 * This implementation executes this cross cutting functionality as a {@link CamelInternalProcessorAdvice} advice
 * (before and after advice) by executing the {@link CamelInternalProcessorAdvice#before(Exchange)} and
 * {@link CamelInternalProcessorAdvice#after(Exchange, Object)} callbacks in correct order during routing. This reduces
 * number of stack frames needed during routing, and reduce the number of lines in stacktraces, as well makes debugging
 * the routing engine easier for end users.
 * <p/>
 * <b>Debugging tips:</b> Camel end users whom want to debug their Camel applications with the Camel source code, then
 * make sure to read the source code of this class about the debugging tips, which you can find in the
 * {@link #process(Exchange, AsyncCallback, AsyncProcessor, Processor)} method.
 */
public class SharedCamelInternalProcessor implements SharedInternalProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SharedCamelInternalProcessor.class);
    private final ReactiveExecutor reactiveExecutor;
    private final AsyncProcessorAwaitManager awaitManager;
    private final ShutdownStrategy shutdownStrategy;
    private final CamelInternalProcessorAdvice<?> advice;

    public SharedCamelInternalProcessor(CamelContext camelContext, CamelInternalProcessorAdvice<?> advice) {
        this.reactiveExecutor = camelContext.getCamelContextExtension().getReactiveExecutor();
        this.awaitManager = PluginHelper.getAsyncProcessorAwaitManager(camelContext);
        this.shutdownStrategy = camelContext.getShutdownStrategy();
        this.advice = Objects.requireNonNull(advice, "advice");
    }

    /**
     * Synchronous API
     */
    public void process(Exchange exchange, AsyncProcessor processor, Processor resultProcessor) {
        awaitManager.process(
                new AsyncProcessor() {
                    @Override
                    public boolean process(Exchange exchange, AsyncCallback callback) {
                        return SharedCamelInternalProcessor.this.process(
                                exchange, callback, processor, resultProcessor);
                    }

                    @Override
                    public CompletableFuture<Exchange> processAsync(Exchange exchange) {
                        AsyncCallbackToCompletableFutureAdapter<Exchange> callback =
                                new AsyncCallbackToCompletableFutureAdapter<>(exchange);
                        process(exchange, callback);
                        return callback.getFuture();
                    }

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalStateException();
                    }
                },
                exchange);
    }

    /**
     * Asynchronous API
     */
    public boolean process(
            Exchange exchange, AsyncCallback originalCallback, AsyncProcessor processor, Processor resultProcessor) {
        if (processor == null || !continueProcessing(exchange)) {
            // no processor or we should not continue then we are done
            originalCallback.done(true);
            return true;
        }

        // optimise to use object array for states, and only for the number of advices that keep state
        final Object state;
        // optimise for loop using index access to avoid creating iterator object
        try {
            state = advice.before(exchange);
        } catch (Exception e) {
            return handleException(exchange, originalCallback, e);
        }

        // create internal callback which will execute the advices in reverse order when done
        AsyncCallback callback = new InternalCallback(state, exchange, originalCallback, resultProcessor);

        if (exchange.isTransacted()) {
            return processTransacted(exchange, processor, callback);
        } else {
            return processNonTransacted(exchange, processor, callback);
        }
    }

    private static boolean handleException(Exchange exchange, AsyncCallback originalCallback, Exception e) {
        exchange.setException(e);
        originalCallback.done(true);
        return true;
    }

    private static boolean processNonTransacted(Exchange exchange, AsyncProcessor processor, AsyncCallback callback) {
        final UnitOfWork uow = exchange.getUnitOfWork();

        // do uow before processing and if a value is returned then the uow wants to be processed after in the same
        // thread
        AsyncCallback async = callback;
        boolean beforeAndAfter = uow.isBeforeAfterProcess();
        if (beforeAndAfter) {
            async = uow.beforeProcess(processor, exchange, async);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing exchange for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
        }
        boolean sync = processor.process(exchange, async);

        // optimize to only do after uow processing if really needed
        if (beforeAndAfter) {
            // execute any after processor work (in current thread, not in the callback)
            uow.afterProcess(processor, exchange, callback, sync);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "Exchange processed and is continued routed {} for exchangeId: {} -> {}",
                    sync ? "synchronously" : "asynchronously",
                    exchange.getExchangeId(),
                    exchange);
        }
        return sync;
    }

    private static boolean processTransacted(Exchange exchange, AsyncProcessor processor, AsyncCallback callback) {
        // must be synchronized for transacted exchanges
        if (LOG.isTraceEnabled()) {
            if (exchange.isTransacted()) {
                LOG.trace(
                        "Transacted Exchange must be routed synchronously for exchangeId: {} -> {}",
                        exchange.getExchangeId(),
                        exchange);
            } else {
                LOG.trace(
                        "Synchronous UnitOfWork Exchange must be routed synchronously for exchangeId: {} -> {}",
                        exchange.getExchangeId(),
                        exchange);
            }
        }
        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    /**
     * Internal callback that executes the after advices.
     */
    private final class InternalCallback implements AsyncCallback {

        private final Object state;
        private final Exchange exchange;
        private final AsyncCallback callback;
        private final Processor resultProcessor;

        private InternalCallback(Object state, Exchange exchange, AsyncCallback callback, Processor resultProcessor) {
            this.state = state;
            this.exchange = exchange;
            this.callback = callback;
            this.resultProcessor = resultProcessor;
        }

        @Override
        public void done(boolean doneSync) {
            // NOTE: if you are debugging Camel routes, then all the code in the for loop below is internal only
            // so you can step straight to the finally-block and invoke the callback

            if (resultProcessor != null) {
                try {
                    resultProcessor.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }
            }

            // we should call after in reverse order
            try {
                AdviceIterator.runAfterTask(advice, state, exchange);
            } finally {
                // callback must be called
                if (callback != null) {
                    reactiveExecutor.schedule(callback);
                }
            }
        }
    }

    /**
     * Strategy to determine if we should continue processing the {@link Exchange}.
     */
    protected boolean continueProcessing(Exchange exchange) {
        if (exchange.isRouteStop()) {
            LOG.debug("Exchange is marked to stop routing: {}", exchange);
            return false;
        }

        if (shutdownStrategy.isForceShutdown()) {
            if (LOG.isDebugEnabled() || exchange.getException() == null) {
                String msg =
                        "Run not allowed as ShutdownStrategy is forcing shutting down, will reject executing exchange: "
                                + exchange;
                LOG.debug(msg);
                if (exchange.getException() == null) {
                    exchange.setException(new RejectedExecutionException(msg));
                }
            }
            return false;
        }

        // yes we can continue
        return true;
    }
}
