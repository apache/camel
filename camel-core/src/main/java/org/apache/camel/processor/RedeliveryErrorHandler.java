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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Navigate;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.ShutdownPrepared;
import org.apache.camel.spi.SubUnitOfWorkCallback;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;

/**
 * Base redeliverable error handler that also supports a final dead letter queue in case
 * all redelivery attempts fail.
 * <p/>
 * This implementation should contain all the error handling logic and the sub classes
 * should only configure it according to what they support.
 *
 * @version
 */
public abstract class RedeliveryErrorHandler extends ErrorHandlerSupport implements AsyncProcessor, ShutdownPrepared, Navigate<Processor> {

    protected final AtomicInteger redeliverySleepCounter = new AtomicInteger();
    protected ScheduledExecutorService executorService;
    protected final CamelContext camelContext;
    protected final Processor deadLetter;
    protected final String deadLetterUri;
    protected final boolean deadLetterHandleNewException;
    protected final Processor output;
    protected final AsyncProcessor outputAsync;
    protected final Processor redeliveryProcessor;
    protected final RedeliveryPolicy redeliveryPolicy;
    protected final Predicate retryWhilePolicy;
    protected final CamelLogger logger;
    protected final boolean useOriginalMessagePolicy;
    protected boolean redeliveryEnabled;
    protected volatile boolean preparingShutdown;
    protected final ExchangeFormatter exchangeFormatter;
    protected final boolean customExchangeFormatter;
    protected final Processor onPrepareProcessor;
    protected final Processor onExceptionProcessor;

    /**
     * Contains the current redelivery data
     */
    protected class RedeliveryData {
        Exchange original;
        boolean sync = true;
        int redeliveryCounter;
        long redeliveryDelay;
        Predicate retryWhilePredicate;
        boolean redeliverFromSync;

        // default behavior which can be overloaded on a per exception basis
        RedeliveryPolicy currentRedeliveryPolicy;
        Processor deadLetterProcessor;
        Processor failureProcessor;
        Processor onRedeliveryProcessor;
        Processor onPrepareProcessor;
        Processor onExceptionProcessor;
        Predicate handledPredicate;
        Predicate continuedPredicate;
        boolean useOriginalInMessage;
        boolean handleNewException;

        public RedeliveryData() {
            // init with values from the error handler
            this.retryWhilePredicate = retryWhilePolicy;
            this.currentRedeliveryPolicy = redeliveryPolicy;
            this.deadLetterProcessor = deadLetter;
            this.onRedeliveryProcessor = redeliveryProcessor;
            this.onPrepareProcessor = RedeliveryErrorHandler.this.onPrepareProcessor;
            this.onExceptionProcessor = RedeliveryErrorHandler.this.onExceptionProcessor;
            this.handledPredicate = getDefaultHandledPredicate();
            this.useOriginalInMessage = useOriginalMessagePolicy;
            this.handleNewException = deadLetterHandleNewException;
        }
    }

    /**
     * Tasks which performs asynchronous redelivery attempts, and being triggered by a
     * {@link java.util.concurrent.ScheduledExecutorService} to avoid having any threads blocking if a task
     * has to be delayed before a redelivery attempt is performed.
     */
    private class AsyncRedeliveryTask implements Callable<Boolean> {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final RedeliveryData data;

        AsyncRedeliveryTask(Exchange exchange, AsyncCallback callback, RedeliveryData data) {
            this.exchange = exchange;
            this.callback = callback;
            this.data = data;
        }

        public Boolean call() throws Exception {
            // prepare for redelivery
            prepareExchangeForRedelivery(exchange, data);

            // letting onRedeliver be executed at first
            deliverToOnRedeliveryProcessor(exchange, data);

            if (log.isTraceEnabled()) {
                log.trace("Redelivering exchangeId: {} -> {} for Exchange: {}", new Object[]{exchange.getExchangeId(), outputAsync, exchange});
            }

            // emmit event we are doing redelivery
            EventHelper.notifyExchangeRedelivery(exchange.getContext(), exchange, data.redeliveryCounter);

            // process the exchange (also redelivery)
            boolean sync;
            if (data.redeliverFromSync) {
                // this redelivery task was scheduled from synchronous, which we forced to be asynchronous from
                // this error handler, which means we have to invoke the callback with false, to have the callback
                // be notified when we are done
                sync = outputAsync.process(exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        log.trace("Redelivering exchangeId: {} done sync: {}", exchange.getExchangeId(), doneSync);

                        // mark we are in sync mode now
                        data.sync = false;

                        // only process if the exchange hasn't failed
                        // and it has not been handled by the error processor
                        if (isDone(exchange)) {
                            callback.done(false);
                            return;
                        }

                        // error occurred so loop back around which we do by invoking the processAsyncErrorHandler
                        processAsyncErrorHandler(exchange, callback, data);
                    }
                });
            } else {
                // this redelivery task was scheduled from asynchronous, which means we should only
                // handle when the asynchronous task was done
                sync = outputAsync.process(exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        log.trace("Redelivering exchangeId: {} done sync: {}", exchange.getExchangeId(), doneSync);

                        // this callback should only handle the async case
                        if (doneSync) {
                            return;
                        }

                        // mark we are in async mode now
                        data.sync = false;

                        // only process if the exchange hasn't failed
                        // and it has not been handled by the error processor
                        if (isDone(exchange)) {
                            callback.done(doneSync);
                            return;
                        }
                        // error occurred so loop back around which we do by invoking the processAsyncErrorHandler
                        processAsyncErrorHandler(exchange, callback, data);
                    }
                });
            }

            return sync;
        }
    }

    public RedeliveryErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger,
                                  Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy, Processor deadLetter,
                                  String deadLetterUri, boolean deadLetterHandleNewException, boolean useOriginalMessagePolicy,
                                  Predicate retryWhile, ScheduledExecutorService executorService, Processor onPrepareProcessor, Processor onExceptionProcessor) {

        ObjectHelper.notNull(camelContext, "CamelContext", this);
        ObjectHelper.notNull(redeliveryPolicy, "RedeliveryPolicy", this);

        this.camelContext = camelContext;
        this.redeliveryProcessor = redeliveryProcessor;
        this.deadLetter = deadLetter;
        this.output = output;
        this.outputAsync = AsyncProcessorConverterHelper.convert(output);
        this.redeliveryPolicy = redeliveryPolicy;
        this.logger = logger;
        this.deadLetterUri = deadLetterUri;
        this.deadLetterHandleNewException = deadLetterHandleNewException;
        this.useOriginalMessagePolicy = useOriginalMessagePolicy;
        this.retryWhilePolicy = retryWhile;
        this.executorService = executorService;
        this.onPrepareProcessor = onPrepareProcessor;
        this.onExceptionProcessor = onExceptionProcessor;

        if (ObjectHelper.isNotEmpty(redeliveryPolicy.getExchangeFormatterRef())) {
            ExchangeFormatter formatter = camelContext.getRegistry().lookupByNameAndType(redeliveryPolicy.getExchangeFormatterRef(), ExchangeFormatter.class);
            if (formatter != null) {
                this.exchangeFormatter = formatter;
                this.customExchangeFormatter = true;
            } else {
                throw new IllegalArgumentException("Cannot find the exchangeFormatter by using reference id " + redeliveryPolicy.getExchangeFormatterRef());
            }
        } else {
            this.customExchangeFormatter = false;
            // setup exchange formatter to be used for message history dump
            DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();
            formatter.setShowExchangeId(true);
            formatter.setMultiline(true);
            formatter.setShowHeaders(true);
            formatter.setStyle(DefaultExchangeFormatter.OutputStyle.Fixed);
            try {
                Integer maxChars = CamelContextHelper.parseInteger(camelContext, camelContext.getProperty(Exchange.LOG_DEBUG_BODY_MAX_CHARS));
                if (maxChars != null) {
                    formatter.setMaxChars(maxChars);
                }
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
            this.exchangeFormatter = formatter;
        }
    }

    public boolean supportTransacted() {
        return false;
    }

    @Override
    public boolean hasNext() {
        return output != null;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(output);
        return answer;
    }

    protected boolean isRunAllowed(RedeliveryData data) {
        // if camel context is forcing a shutdown then do not allow running
        boolean forceShutdown = camelContext.getShutdownStrategy().forceShutdown(this);
        if (forceShutdown) {
            log.trace("isRunAllowed() -> false (Run not allowed as ShutdownStrategy is forcing shutting down)");
            return false;
        }

        // redelivery policy can control if redelivery is allowed during stopping/shutdown
        // but this only applies during a redelivery (counter must > 0)
        if (data.redeliveryCounter > 0) {
            if (data.currentRedeliveryPolicy.allowRedeliveryWhileStopping) {
                log.trace("isRunAllowed() -> true (Run allowed as RedeliverWhileStopping is enabled)");
                return true;
            } else if (preparingShutdown) {
                // we are preparing for shutdown, now determine if we can still run
                boolean answer = isRunAllowedOnPreparingShutdown();
                log.trace("isRunAllowed() -> {} (Run not allowed as we are preparing for shutdown)", answer);
                return answer;
            }
        }

        // we cannot run if we are stopping/stopped
        boolean answer = !isStoppingOrStopped();
        log.trace("isRunAllowed() -> {} (Run allowed if we are not stopped/stopping)", answer);
        return answer;
    }

    protected boolean isRunAllowedOnPreparingShutdown() {
        return false;
    }

    protected boolean isRedeliveryAllowed(RedeliveryData data) {
        // redelivery policy can control if redelivery is allowed during stopping/shutdown
        // but this only applies during a redelivery (counter must > 0)
        if (data.redeliveryCounter > 0) {
            boolean stopping = isStoppingOrStopped();
            if (!preparingShutdown && !stopping) {
                log.trace("isRedeliveryAllowed() -> true (we are not stopping/stopped)");
                return true;
            } else {
                // we are stopping or preparing to shutdown
                if (data.currentRedeliveryPolicy.allowRedeliveryWhileStopping) {
                    log.trace("isRedeliveryAllowed() -> true (Redelivery allowed as RedeliverWhileStopping is enabled)");
                    return true;
                } else {
                    log.trace("isRedeliveryAllowed() -> false (Redelivery not allowed as RedeliverWhileStopping is disabled)");
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // prepare for shutdown, eg do not allow redelivery if configured
        log.trace("Prepare shutdown on error handler {}", this);
        preparingShutdown = true;
    }

    public void process(Exchange exchange) throws Exception {
        if (output == null) {
            // no output then just return
            return;
        }
        AsyncProcessorHelper.process(this, exchange);
    }

    /**
     * Process the exchange using redelivery error handling.
     */
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final RedeliveryData data = new RedeliveryData();

        // do a defensive copy of the original Exchange, which is needed for redelivery so we can ensure the
        // original Exchange is being redelivered, and not a mutated Exchange
        data.original = defensiveCopyExchangeIfNeeded(exchange);

        // use looping to have redelivery attempts
        while (true) {

            // can we still run
            if (!isRunAllowed(data)) {
                log.trace("Run not allowed, will reject executing exchange: {}", exchange);
                if (exchange.getException() == null) {
                    exchange.setException(new RejectedExecutionException());
                }
                // we cannot process so invoke callback
                callback.done(data.sync);
                return data.sync;
            }

            // did previous processing cause an exception?
            boolean handle = shouldHandleException(exchange);
            if (handle) {
                handleException(exchange, data, isDeadLetterChannel());
                onExceptionOccurred(exchange, data);
            }

            // compute if we are exhausted, and whether redelivery is allowed
            boolean exhausted = isExhausted(exchange, data);
            boolean redeliverAllowed = isRedeliveryAllowed(data);

            // if we are exhausted or redelivery is not allowed, then deliver to failure processor (eg such as DLC)
            if (!redeliverAllowed || exhausted) {
                Processor target = null;
                boolean deliver = true;

                // the unit of work may have an optional callback associated we need to leverage
                SubUnitOfWorkCallback uowCallback = exchange.getUnitOfWork().getSubUnitOfWorkCallback();
                if (uowCallback != null) {
                    // signal to the callback we are exhausted
                    uowCallback.onExhausted(exchange);
                    // do not deliver to the failure processor as its been handled by the callback instead
                    deliver = false;
                }

                if (deliver) {
                    // should deliver to failure processor (either from onException or the dead letter channel)
                    target = data.failureProcessor != null ? data.failureProcessor : data.deadLetterProcessor;
                }
                // we should always invoke the deliverToFailureProcessor as it prepares, logs and does a fair
                // bit of work for exhausted exchanges (its only the target processor which may be null if handled by a savepoint)
                boolean isDeadLetterChannel = isDeadLetterChannel() && (target == null || target == data.deadLetterProcessor);
                boolean sync = deliverToFailureProcessor(target, isDeadLetterChannel, exchange, data, callback);
                // we are breaking out
                return sync;
            }

            if (data.redeliveryCounter > 0) {
                // calculate delay
                data.redeliveryDelay = determineRedeliveryDelay(exchange, data.currentRedeliveryPolicy, data.redeliveryDelay, data.redeliveryCounter);

                if (data.redeliveryDelay > 0) {
                    // okay there is a delay so create a scheduled task to have it executed in the future

                    if (data.currentRedeliveryPolicy.isAsyncDelayedRedelivery() && !exchange.isTransacted()) {

                        // we are doing a redelivery then a thread pool must be configured (see the doStart method)
                        ObjectHelper.notNull(executorService, "Redelivery is enabled but ExecutorService has not been configured.", this);

                        // let the RedeliverTask be the logic which tries to redeliver the Exchange which we can used a scheduler to
                        // have it being executed in the future, or immediately
                        // we are continuing asynchronously

                        // mark we are routing async from now and that this redelivery task came from a synchronous routing
                        data.sync = false;
                        data.redeliverFromSync = true;
                        AsyncRedeliveryTask task = new AsyncRedeliveryTask(exchange, callback, data);

                        // schedule the redelivery task
                        if (log.isTraceEnabled()) {
                            log.trace("Scheduling redelivery task to run in {} millis for exchangeId: {}", data.redeliveryDelay, exchange.getExchangeId());
                        }
                        executorService.schedule(task, data.redeliveryDelay, TimeUnit.MILLISECONDS);

                        return false;
                    } else {
                        // async delayed redelivery was disabled or we are transacted so we must be synchronous
                        // as the transaction manager requires to execute in the same thread context
                        try {
                            // we are doing synchronous redelivery and use thread sleep, so we keep track using a counter how many are sleeping
                            redeliverySleepCounter.incrementAndGet();
                            data.currentRedeliveryPolicy.sleep(data.redeliveryDelay);
                            redeliverySleepCounter.decrementAndGet();
                        } catch (InterruptedException e) {
                            redeliverySleepCounter.decrementAndGet();
                            // we was interrupted so break out
                            exchange.setException(e);
                            // mark the exchange to stop continue routing when interrupted
                            // as we do not want to continue routing (for example a task has been cancelled)
                            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
                            callback.done(data.sync);
                            return data.sync;
                        }
                    }
                }

                // prepare for redelivery
                prepareExchangeForRedelivery(exchange, data);

                // letting onRedeliver be executed
                deliverToOnRedeliveryProcessor(exchange, data);

                // emmit event we are doing redelivery
                EventHelper.notifyExchangeRedelivery(exchange.getContext(), exchange, data.redeliveryCounter);
            }

            // process the exchange (also redelivery)
            boolean sync = outputAsync.process(exchange, new AsyncCallback() {
                public void done(boolean sync) {
                    // this callback should only handle the async case
                    if (sync) {
                        return;
                    }

                    // mark we are in async mode now
                    data.sync = false;

                    // if we are done then notify callback and exit
                    if (isDone(exchange)) {
                        callback.done(sync);
                        return;
                    }

                    // error occurred so loop back around which we do by invoking the processAsyncErrorHandler
                    // method which takes care of this in a asynchronous manner
                    processAsyncErrorHandler(exchange, callback, data);
                }
            });

            if (!sync) {
                // the remainder of the Exchange is being processed asynchronously so we should return
                return false;
            }
            // we continue to route synchronously

            // if we are done then notify callback and exit
            boolean done = isDone(exchange);
            if (done) {
                callback.done(true);
                return true;
            }

            // error occurred so loop back around.....
        }
    }

    /**
     * <p>Determines the redelivery delay time by first inspecting the Message header {@link Exchange#REDELIVERY_DELAY}
     * and if not present, defaulting to {@link RedeliveryPolicy#calculateRedeliveryDelay(long, int)}</p>
     *
     * <p>In order to prevent manipulation of the RedeliveryData state, the values of {@link RedeliveryData#redeliveryDelay}
     * and {@link RedeliveryData#redeliveryCounter} are copied in.</p>
     *
     * @param exchange The current exchange in question.
     * @param redeliveryPolicy The RedeliveryPolicy to use in the calculation.
     * @param redeliveryDelay The default redelivery delay from RedeliveryData
     * @param redeliveryCounter The redeliveryCounter
     * @return The time to wait before the next redelivery.
     */
    protected long determineRedeliveryDelay(Exchange exchange, RedeliveryPolicy redeliveryPolicy, long redeliveryDelay, int redeliveryCounter) {
        Message message = exchange.getIn();
        Long delay = message.getHeader(Exchange.REDELIVERY_DELAY, Long.class);
        if (delay == null) {
            delay = redeliveryPolicy.calculateRedeliveryDelay(redeliveryDelay, redeliveryCounter);
            log.debug("Redelivery delay calculated as {}", delay);
        } else {
            log.debug("Redelivery delay is {} from Message Header [{}]", delay, Exchange.REDELIVERY_DELAY);
        }
        return delay;
    }

    /**
     * This logic is only executed if we have to retry redelivery asynchronously, which have to be done from the callback.
     * <p/>
     * And therefore the logic is a bit different than the synchronous <tt>processErrorHandler</tt> method which can use
     * a loop based redelivery technique. However this means that these two methods in overall have to be in <b>sync</b>
     * in terms of logic.
     */
    protected void processAsyncErrorHandler(final Exchange exchange, final AsyncCallback callback, final RedeliveryData data) {
        // can we still run
        if (!isRunAllowed(data)) {
            log.trace("Run not allowed, will reject executing exchange: {}", exchange);
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            callback.done(data.sync);
            return;
        }

        // did previous processing cause an exception?
        boolean handle = shouldHandleException(exchange);
        if (handle) {
            handleException(exchange, data, isDeadLetterChannel());
            onExceptionOccurred(exchange, data);
        }

        // compute if we are exhausted or not
        boolean exhausted = isExhausted(exchange, data);
        if (exhausted) {
            Processor target = null;
            boolean deliver = true;

            // the unit of work may have an optional callback associated we need to leverage
            UnitOfWork uow = exchange.getUnitOfWork();
            if (uow != null) {
                SubUnitOfWorkCallback uowCallback = uow.getSubUnitOfWorkCallback();
                if (uowCallback != null) {
                    // signal to the callback we are exhausted
                    uowCallback.onExhausted(exchange);
                    // do not deliver to the failure processor as its been handled by the callback instead
                    deliver = false;
                }
            }

            if (deliver) {
                // should deliver to failure processor (either from onException or the dead letter channel)
                target = data.failureProcessor != null ? data.failureProcessor : data.deadLetterProcessor;
            }
            // we should always invoke the deliverToFailureProcessor as it prepares, logs and does a fair
            // bit of work for exhausted exchanges (its only the target processor which may be null if handled by a savepoint)
            boolean isDeadLetterChannel = isDeadLetterChannel() && target == data.deadLetterProcessor;
            deliverToFailureProcessor(target, isDeadLetterChannel, exchange, data, callback);
            // we are breaking out
            return;
        }

        if (data.redeliveryCounter > 0) {
            // we are doing a redelivery then a thread pool must be configured (see the doStart method)
            ObjectHelper.notNull(executorService, "Redelivery is enabled but ExecutorService has not been configured.", this);

            // let the RedeliverTask be the logic which tries to redeliver the Exchange which we can used a scheduler to
            // have it being executed in the future, or immediately
            // Note: the data.redeliverFromSync should be kept as is, in case it was enabled previously
            // to ensure the callback will continue routing from where we left
            AsyncRedeliveryTask task = new AsyncRedeliveryTask(exchange, callback, data);

            // calculate the redelivery delay
            data.redeliveryDelay = determineRedeliveryDelay(exchange, data.currentRedeliveryPolicy, data.redeliveryDelay, data.redeliveryCounter);

            if (data.redeliveryDelay > 0) {
                // schedule the redelivery task
                if (log.isTraceEnabled()) {
                    log.trace("Scheduling redelivery task to run in {} millis for exchangeId: {}", data.redeliveryDelay, exchange.getExchangeId());
                }
                executorService.schedule(task, data.redeliveryDelay, TimeUnit.MILLISECONDS);
            } else {
                // execute the task immediately
                executorService.submit(task);
            }
        }
    }

    /**
     * Performs a defensive copy of the exchange if needed
     *
     * @param exchange the exchange
     * @return the defensive copy, or <tt>null</tt> if not needed (redelivery is not enabled).
     */
    protected Exchange defensiveCopyExchangeIfNeeded(Exchange exchange) {
        // only do a defensive copy if redelivery is enabled
        if (redeliveryEnabled) {
            return ExchangeHelper.createCopy(exchange, true);
        } else {
            return null;
        }
    }

    /**
     * Strategy whether the exchange has an exception that we should try to handle.
     * <p/>
     * Standard implementations should just look for an exception.
     */
    protected boolean shouldHandleException(Exchange exchange) {
        return exchange.getException() != null;
    }

    /**
     * Strategy to determine if the exchange is done so we can continue
     */
    protected boolean isDone(Exchange exchange) {
        boolean answer = isCancelledOrInterrupted(exchange);

        // only done if the exchange hasn't failed
        // and it has not been handled by the failure processor
        // or we are exhausted
        if (!answer) {
            answer = exchange.getException() == null
                || ExchangeHelper.isFailureHandled(exchange)
                || ExchangeHelper.isRedeliveryExhausted(exchange);
        }

        log.trace("Is exchangeId: {} done? {}", exchange.getExchangeId(), answer);
        return answer;
    }

    /**
     * Strategy to determine if the exchange was cancelled or interrupted
     */
    protected boolean isCancelledOrInterrupted(Exchange exchange) {
        boolean answer = false;

        if (ExchangeHelper.isInterrupted(exchange)) {
            // mark the exchange to stop continue routing when interrupted
            // as we do not want to continue routing (for example a task has been cancelled)
            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
            answer = true;
        }

        log.trace("Is exchangeId: {} interrupted? {}", exchange.getExchangeId(), answer);
        return answer;
    }

    /**
     * Returns the output processor
     */
    public Processor getOutput() {
        return output;
    }

    /**
     * Returns the dead letter that message exchanges will be sent to if the
     * redelivery attempts fail
     */
    public Processor getDeadLetter() {
        return deadLetter;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public boolean isUseOriginalMessagePolicy() {
        return useOriginalMessagePolicy;
    }

    public boolean isDeadLetterHandleNewException() {
        return deadLetterHandleNewException;
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public CamelLogger getLogger() {
        return logger;
    }

    protected Predicate getDefaultHandledPredicate() {
        // Default is not not handle errors
        return null;
    }

    protected void prepareExchangeForContinue(Exchange exchange, RedeliveryData data, boolean isDeadLetterChannel) {
        Exception caught = exchange.getException();

        // we continue so clear any exceptions
        exchange.setException(null);
        // clear rollback flags
        exchange.setProperty(Exchange.ROLLBACK_ONLY, null);
        // reset cached streams so they can be read again
        MessageHelper.resetStreamCache(exchange.getIn());

        // its continued then remove traces of redelivery attempted and caught exception
        exchange.getIn().removeHeader(Exchange.REDELIVERED);
        exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
        exchange.getIn().removeHeader(Exchange.REDELIVERY_MAX_COUNTER);
        exchange.removeProperty(Exchange.FAILURE_HANDLED);
        // keep the Exchange.EXCEPTION_CAUGHT as property so end user knows the caused exception

        // create log message
        String msg = "Failed delivery for " + ExchangeHelper.logIds(exchange);
        msg = msg + ". Exhausted after delivery attempt: " + data.redeliveryCounter + " caught: " + caught;
        msg = msg + ". Handled and continue routing.";

        // log that we failed but want to continue
        logFailedDelivery(false, false, false, isDeadLetterChannel, true, exchange, msg, data, null);
    }

    protected void prepareExchangeForRedelivery(Exchange exchange, RedeliveryData data) {
        if (!redeliveryEnabled) {
            throw new IllegalStateException("Redelivery is not enabled on " + this + ". Make sure you have configured the error handler properly.");
        }
        // there must be a defensive copy of the exchange
        ObjectHelper.notNull(data.original, "Defensive copy of Exchange is null", this);

        // okay we will give it another go so clear the exception so we can try again
        exchange.setException(null);

        // clear rollback flags
        exchange.setProperty(Exchange.ROLLBACK_ONLY, null);

        // TODO: We may want to store these as state on RedeliveryData so we keep them in case end user messes with Exchange
        // and then put these on the exchange when doing a redelivery / fault processor

        // preserve these headers
        Integer redeliveryCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
        Integer redeliveryMaxCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER, Integer.class);
        Boolean redelivered = exchange.getIn().getHeader(Exchange.REDELIVERED, Boolean.class);

        // we are redelivering so copy from original back to exchange
        exchange.getIn().copyFrom(data.original.getIn());
        exchange.setOut(null);
        // reset cached streams so they can be read again
        MessageHelper.resetStreamCache(exchange.getIn());

        // put back headers
        if (redeliveryCounter != null) {
            exchange.getIn().setHeader(Exchange.REDELIVERY_COUNTER, redeliveryCounter);
        }
        if (redeliveryMaxCounter != null) {
            exchange.getIn().setHeader(Exchange.REDELIVERY_MAX_COUNTER, redeliveryMaxCounter);
        }
        if (redelivered != null) {
            exchange.getIn().setHeader(Exchange.REDELIVERED, redelivered);
        }
    }

    protected void handleException(Exchange exchange, RedeliveryData data, boolean isDeadLetterChannel) {
        Exception e = exchange.getException();

        // store the original caused exception in a property, so we can restore it later
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);

        // find the error handler to use (if any)
        OnExceptionDefinition exceptionPolicy = getExceptionPolicy(exchange, e);
        if (exceptionPolicy != null) {
            data.currentRedeliveryPolicy = exceptionPolicy.createRedeliveryPolicy(exchange.getContext(), data.currentRedeliveryPolicy);
            data.handledPredicate = exceptionPolicy.getHandledPolicy();
            data.continuedPredicate = exceptionPolicy.getContinuedPolicy();
            data.retryWhilePredicate = exceptionPolicy.getRetryWhilePolicy();
            data.useOriginalInMessage = exceptionPolicy.getUseOriginalMessagePolicy() != null && exceptionPolicy.getUseOriginalMessagePolicy();

            // route specific failure handler?
            Processor processor = null;
            UnitOfWork uow = exchange.getUnitOfWork();
            if (uow != null && uow.getRouteContext() != null) {
                String routeId = uow.getRouteContext().getRoute().getId();
                processor = exceptionPolicy.getErrorHandler(routeId);
            } else if (!exceptionPolicy.getErrorHandlers().isEmpty()) {
                // note this should really not happen, but we have this code as a fail safe
                // to be backwards compatible with the old behavior
                log.warn("Cannot determine current route from Exchange with id: {}, will fallback and use first error handler.", exchange.getExchangeId());
                processor = exceptionPolicy.getErrorHandlers().iterator().next();
            }
            if (processor != null) {
                data.failureProcessor = processor;
            }

            // route specific on redelivery?
            processor = exceptionPolicy.getOnRedelivery();
            if (processor != null) {
                data.onRedeliveryProcessor = processor;
            }
            // route specific on exception occurred?
            processor = exceptionPolicy.getOnExceptionOccurred();
            if (processor != null) {
                data.onExceptionProcessor = processor;
            }
        }

        // only log if not failure handled or not an exhausted unit of work
        if (!ExchangeHelper.isFailureHandled(exchange) && !ExchangeHelper.isUnitOfWorkExhausted(exchange)) {
            String msg = "Failed delivery for " + ExchangeHelper.logIds(exchange)
                    + ". On delivery attempt: " + data.redeliveryCounter + " caught: " + e;
            logFailedDelivery(true, false, false, false, isDeadLetterChannel, exchange, msg, data, e);
        }

        data.redeliveryCounter = incrementRedeliveryCounter(exchange, e, data);
    }

    /**
     * Gives an optional configured OnExceptionOccurred processor a chance to process just after an exception
     * was thrown while processing the Exchange. This allows to execute the processor at the same time the exception was thrown.
     */
    protected void onExceptionOccurred(Exchange exchange, final RedeliveryData data) {
        if (data.onExceptionProcessor == null) {
            return;
        }

        // run this synchronously as its just a Processor
        try {
            if (log.isTraceEnabled()) {
                log.trace("OnExceptionOccurred processor {} is processing Exchange: {} due exception occurred", data.onExceptionProcessor, exchange);
            }
            data.onExceptionProcessor.process(exchange);
        } catch (Throwable e) {
            // we dont not want new exception to override existing, so log it as a WARN
            log.warn("Error during processing OnExceptionOccurred. This exception is ignored.", e);
        }
        log.trace("OnExceptionOccurred processor done");
    }

    /**
     * Gives an optional configured redelivery processor a chance to process before the Exchange
     * will be redelivered. This can be used to alter the Exchange.
     */
    protected void deliverToOnRedeliveryProcessor(final Exchange exchange, final RedeliveryData data) {
        if (data.onRedeliveryProcessor == null) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Redelivery processor {} is processing Exchange: {} before its redelivered",
                    data.onRedeliveryProcessor, exchange);
        }

        // run this synchronously as its just a Processor
        try {
            data.onRedeliveryProcessor.process(exchange);
        } catch (Throwable e) {
            exchange.setException(e);
        }
        log.trace("Redelivery processor done");
    }

    /**
     * All redelivery attempts failed so move the exchange to the dead letter queue
     */
    protected boolean deliverToFailureProcessor(final Processor processor, final boolean isDeadLetterChannel, final Exchange exchange,
                                                final RedeliveryData data, final AsyncCallback callback) {
        boolean sync = true;

        Exception caught = exchange.getException();

        // we did not success with the redelivery so now we let the failure processor handle it
        // clear exception as we let the failure processor handle it
        exchange.setException(null);

        final boolean shouldHandle = shouldHandle(exchange, data);
        final boolean shouldContinue = shouldContinue(exchange, data);

        // regard both handled or continued as being handled
        boolean handled = false;

        // always handle if dead letter channel
        boolean handleOrContinue = isDeadLetterChannel || shouldHandle || shouldContinue;
        if (handleOrContinue) {
            // its handled then remove traces of redelivery attempted
            exchange.getIn().removeHeader(Exchange.REDELIVERED);
            exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
            exchange.getIn().removeHeader(Exchange.REDELIVERY_MAX_COUNTER);
            exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);

            // and remove traces of rollback only and uow exhausted markers
            exchange.removeProperty(Exchange.ROLLBACK_ONLY);
            exchange.removeProperty(Exchange.UNIT_OF_WORK_EXHAUSTED);

            handled = true;
        } else {
            // must decrement the redelivery counter as we didn't process the redelivery but is
            // handling by the failure handler. So we must -1 to not let the counter be out-of-sync
            decrementRedeliveryCounter(exchange);
        }

        // is the a failure processor to process the Exchange
        if (processor != null) {

            // prepare original IN body if it should be moved instead of current body
            if (data.useOriginalInMessage) {
                log.trace("Using the original IN message instead of current");
                Message original = ExchangeHelper.getOriginalInMessage(exchange);
                exchange.setIn(original);
                if (exchange.hasOut()) {
                    log.trace("Removing the out message to avoid some uncertain behavior");
                    exchange.setOut(null);
                }
            }

            // reset cached streams so they can be read again
            MessageHelper.resetStreamCache(exchange.getIn());

            // invoke custom on prepare
            if (onPrepareProcessor != null) {
                try {
                    log.trace("OnPrepare processor {} is processing Exchange: {}", onPrepareProcessor, exchange);
                    onPrepareProcessor.process(exchange);
                } catch (Exception e) {
                    // a new exception was thrown during prepare
                    exchange.setException(e);
                }
            }

            log.trace("Failure processor {} is processing Exchange: {}", processor, exchange);

            // store the last to endpoint as the failure endpoint
            exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
            // and store the route id so we know in which route we failed
            UnitOfWork uow = exchange.getUnitOfWork();
            if (uow != null && uow.getRouteContext() != null) {
                exchange.setProperty(Exchange.FAILURE_ROUTE_ID, uow.getRouteContext().getRoute().getId());
            }

            // fire event as we had a failure processor to handle it, which there is a event for
            final boolean deadLetterChannel = processor == data.deadLetterProcessor;

            EventHelper.notifyExchangeFailureHandling(exchange.getContext(), exchange, processor, deadLetterChannel, deadLetterUri);

            // the failure processor could also be asynchronous
            AsyncProcessor afp = AsyncProcessorConverterHelper.convert(processor);
            sync = afp.process(exchange, new AsyncCallback() {
                public void done(boolean sync) {
                    log.trace("Failure processor done: {} processing Exchange: {}", processor, exchange);
                    try {
                        prepareExchangeAfterFailure(exchange, data, isDeadLetterChannel, shouldHandle, shouldContinue);
                        // fire event as we had a failure processor to handle it, which there is a event for
                        EventHelper.notifyExchangeFailureHandled(exchange.getContext(), exchange, processor, deadLetterChannel, deadLetterUri);
                    } finally {
                        // if the fault was handled asynchronously, this should be reflected in the callback as well
                        data.sync &= sync;
                        callback.done(data.sync);
                    }
                }
            });
        } else {
            try {
                // invoke custom on prepare
                if (onPrepareProcessor != null) {
                    try {
                        log.trace("OnPrepare processor {} is processing Exchange: {}", onPrepareProcessor, exchange);
                        onPrepareProcessor.process(exchange);
                    } catch (Exception e) {
                        // a new exception was thrown during prepare
                        exchange.setException(e);
                    }
                }
                // no processor but we need to prepare after failure as well
                prepareExchangeAfterFailure(exchange, data, isDeadLetterChannel, shouldHandle, shouldContinue);
            } finally {
                // callback we are done
                callback.done(data.sync);
            }
        }

        // create log message
        String msg = "Failed delivery for " + ExchangeHelper.logIds(exchange);
        msg = msg + ". Exhausted after delivery attempt: " + data.redeliveryCounter + " caught: " + caught;
        if (processor != null) {
            if (isDeadLetterChannel && deadLetterUri != null) {
                msg = msg + ". Handled by DeadLetterChannel: [" + URISupport.sanitizeUri(deadLetterUri) + "]";
            } else {
                msg = msg + ". Processed by failure processor: " + processor;
            }
        }

        // log that we failed delivery as we are exhausted
        logFailedDelivery(false, false, handled, false, isDeadLetterChannel, exchange, msg, data, null);

        return sync;
    }

    protected void prepareExchangeAfterFailure(final Exchange exchange, final RedeliveryData data, final boolean isDeadLetterChannel,
                                               final boolean shouldHandle, final boolean shouldContinue) {

        Exception newException = exchange.getException();

        // we could not process the exchange so we let the failure processor handled it
        ExchangeHelper.setFailureHandled(exchange);

        // honor if already set a handling
        boolean alreadySet = exchange.getProperty(Exchange.ERRORHANDLER_HANDLED) != null;
        if (alreadySet) {
            boolean handled = exchange.getProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.class);
            log.trace("This exchange has already been marked for handling: {}", handled);
            if (!handled) {
                // exception not handled, put exception back in the exchange
                exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
                // and put failure endpoint back as well
                exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
            }
            return;
        }

        // dead letter channel is special
        if (shouldContinue) {
            log.trace("This exchange is continued: {}", exchange);
            // okay we want to continue then prepare the exchange for that as well
            prepareExchangeForContinue(exchange, data, isDeadLetterChannel);
        } else if (shouldHandle) {
            log.trace("This exchange is handled so its marked as not failed: {}", exchange);
            exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.TRUE);
        } else {
            // okay the redelivery policy are not explicit set to true, so we should allow to check for some
            // special situations when using dead letter channel
            if (isDeadLetterChannel) {

                // DLC is always handling the first thrown exception,
                // but if its a new exception then use the configured option
                boolean handled = newException == null || data.handleNewException;

                // when using DLC then log new exception whether its being handled or not, as otherwise it may appear as
                // the DLC swallow new exceptions by default (which is by design to ensure the DLC always complete,
                // to avoid causing endless poison messages that fails forever)
                if (newException != null && data.currentRedeliveryPolicy.isLogNewException()) {
                    String uri = URISupport.sanitizeUri(deadLetterUri);
                    String msg = "New exception occurred during processing by the DeadLetterChannel[" + uri + "] due " + newException.getMessage();
                    if (handled) {
                        msg += ". The new exception is being handled as deadLetterHandleNewException=true.";
                    } else {
                        msg += ". The new exception is not handled as deadLetterHandleNewException=false.";
                    }
                    logFailedDelivery(false, true, handled, false, true, exchange, msg, data, newException);
                }

                if (handled) {
                    log.trace("This exchange is handled so its marked as not failed: {}", exchange);
                    exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.TRUE);
                    return;
                }
            }

            // not handled by default
            prepareExchangeAfterFailureNotHandled(exchange);
        }
    }

    private void prepareExchangeAfterFailureNotHandled(Exchange exchange) {
        log.trace("This exchange is not handled or continued so its marked as failed: {}", exchange);
        // exception not handled, put exception back in the exchange
        exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.FALSE);
        exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
        // and put failure endpoint back as well
        exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
        // and store the route id so we know in which route we failed
        UnitOfWork uow = exchange.getUnitOfWork();
        if (uow != null && uow.getRouteContext() != null) {
            exchange.setProperty(Exchange.FAILURE_ROUTE_ID, uow.getRouteContext().getRoute().getId());
        }
    }

    private void logFailedDelivery(boolean shouldRedeliver, boolean newException, boolean handled, boolean continued, boolean isDeadLetterChannel,
                                   Exchange exchange, String message, RedeliveryData data, Throwable e) {
        if (logger == null) {
            return;
        }

        if (!exchange.isRollbackOnly()) {
            if (newException && !data.currentRedeliveryPolicy.isLogNewException()) {
                // do not log new exception
                return;
            }

            // if we should not rollback, then check whether logging is enabled

            if (!newException && handled && !data.currentRedeliveryPolicy.isLogHandled()) {
                // do not log handled
                return;
            }

            if (!newException && continued && !data.currentRedeliveryPolicy.isLogContinued()) {
                // do not log handled
                return;
            }

            if (!newException && shouldRedeliver && !data.currentRedeliveryPolicy.isLogRetryAttempted()) {
                // do not log retry attempts
                return;
            }

            if (!newException && !shouldRedeliver && !data.currentRedeliveryPolicy.isLogExhausted()) {
                // do not log exhausted
                return;
            }
        }

        LoggingLevel newLogLevel;
        boolean logStackTrace;
        if (exchange.isRollbackOnly()) {
            newLogLevel = data.currentRedeliveryPolicy.getRetriesExhaustedLogLevel();
            logStackTrace = data.currentRedeliveryPolicy.isLogStackTrace();
        } else if (shouldRedeliver) {
            newLogLevel = data.currentRedeliveryPolicy.getRetryAttemptedLogLevel();
            logStackTrace = data.currentRedeliveryPolicy.isLogRetryStackTrace();
        } else {
            newLogLevel = data.currentRedeliveryPolicy.getRetriesExhaustedLogLevel();
            logStackTrace = data.currentRedeliveryPolicy.isLogStackTrace();
        }
        if (e == null) {
            e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        }

        if (newException) {
            // log at most WARN level
            if (newLogLevel == LoggingLevel.ERROR) {
                newLogLevel = LoggingLevel.WARN;
            }
            String msg = message;
            if (msg == null) {
                msg = "New exception " + ExchangeHelper.logIds(exchange);
                // special for logging the new exception
                Throwable cause = e;
                if (cause != null) {
                    msg = msg + " due: " + cause.getMessage();
                }
            }

            if (e != null && logStackTrace) {
                logger.log(msg, e, newLogLevel);
            } else {
                logger.log(msg, newLogLevel);
            }
        } else if (exchange.isRollbackOnly()) {
            String msg = "Rollback " + ExchangeHelper.logIds(exchange);
            Throwable cause = exchange.getException() != null ? exchange.getException() : exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
            if (cause != null) {
                msg = msg + " due: " + cause.getMessage();
            }

            // should we include message history
            if (!shouldRedeliver && data.currentRedeliveryPolicy.isLogExhaustedMessageHistory()) {
                // only use the exchange formatter if we should log exhausted message body (and if using a custom formatter then always use it)
                ExchangeFormatter formatter = customExchangeFormatter
                    ? exchangeFormatter : (data.currentRedeliveryPolicy.isLogExhaustedMessageBody() || camelContext.isLogExhaustedMessageBody() ? exchangeFormatter : null);
                String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, false);
                if (routeStackTrace != null) {
                    msg = msg + "\n" + routeStackTrace;
                }
            }

            if (newLogLevel == LoggingLevel.ERROR) {
                // log intended rollback on maximum WARN level (no ERROR)
                logger.log(msg, LoggingLevel.WARN);
            } else {
                // otherwise use the desired logging level
                logger.log(msg, newLogLevel);
            }
        } else {
            String msg = message;
            // should we include message history
            if (!shouldRedeliver && data.currentRedeliveryPolicy.isLogExhaustedMessageHistory()) {
                // only use the exchange formatter if we should log exhausted message body (and if using a custom formatter then always use it)
                ExchangeFormatter formatter = customExchangeFormatter
                    ? exchangeFormatter : (data.currentRedeliveryPolicy.isLogExhaustedMessageBody() || camelContext.isLogExhaustedMessageBody() ? exchangeFormatter : null);
                String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, e != null && logStackTrace);
                if (routeStackTrace != null) {
                    msg = msg + "\n" + routeStackTrace;
                }
            }

            if (e != null && logStackTrace) {
                logger.log(msg, e, newLogLevel);
            } else {
                logger.log(msg, newLogLevel);
            }
        }
    }

    /**
     * Determines whether the exchange is exhausted (or anyway marked to not continue such as rollback).
     * <p/>
     * If the exchange is exhausted, then we will not continue processing, but let the
     * failure processor deal with the exchange.
     *
     * @param exchange the current exchange
     * @param data     the redelivery data
     * @return <tt>false</tt> to continue/redeliver, or <tt>true</tt> to exhaust.
     */
    private boolean isExhausted(Exchange exchange, RedeliveryData data) {
        // if marked as rollback only then do not continue/redeliver
        boolean exhausted = exchange.getProperty(Exchange.REDELIVERY_EXHAUSTED, false, Boolean.class);
        if (exhausted) {
            log.trace("This exchange is marked as redelivery exhausted: {}", exchange);
            return true;
        }

        // if marked as rollback only then do not continue/redeliver
        boolean rollbackOnly = exchange.getProperty(Exchange.ROLLBACK_ONLY, false, Boolean.class);
        if (rollbackOnly) {
            log.trace("This exchange is marked as rollback only, so forcing it to be exhausted: {}", exchange);
            return true;
        }
        // its the first original call so continue
        if (data.redeliveryCounter == 0) {
            return false;
        }
        // its a potential redelivery so determine if we should redeliver or not
        boolean redeliver = data.currentRedeliveryPolicy.shouldRedeliver(exchange, data.redeliveryCounter, data.retryWhilePredicate);
        return !redeliver;
    }

    /**
     * Determines whether or not to continue if we are exhausted.
     *
     * @param exchange the current exchange
     * @param data     the redelivery data
     * @return <tt>true</tt> to continue, or <tt>false</tt> to exhaust.
     */
    private boolean shouldContinue(Exchange exchange, RedeliveryData data) {
        if (data.continuedPredicate != null) {
            return data.continuedPredicate.matches(exchange);
        }
        // do not continue by default
        return false;
    }

    /**
     * Determines whether or not to handle if we are exhausted.
     *
     * @param exchange the current exchange
     * @param data     the redelivery data
     * @return <tt>true</tt> to handle, or <tt>false</tt> to exhaust.
     */
    private boolean shouldHandle(Exchange exchange, RedeliveryData data) {
        if (data.handledPredicate != null) {
            return data.handledPredicate.matches(exchange);
        }
        // do not handle by default
        return false;
    }

    /**
     * Increments the redelivery counter and adds the redelivered flag if the
     * message has been redelivered
     */
    private int incrementRedeliveryCounter(Exchange exchange, Throwable e, RedeliveryData data) {
        Message in = exchange.getIn();
        Integer counter = in.getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
        int next = 1;
        if (counter != null) {
            next = counter + 1;
        }
        in.setHeader(Exchange.REDELIVERY_COUNTER, next);
        in.setHeader(Exchange.REDELIVERED, Boolean.TRUE);
        // if maximum redeliveries is used, then provide that information as well
        if (data.currentRedeliveryPolicy.getMaximumRedeliveries() > 0) {
            in.setHeader(Exchange.REDELIVERY_MAX_COUNTER, data.currentRedeliveryPolicy.getMaximumRedeliveries());
        }
        return next;
    }

    /**
     * Prepares the redelivery counter and boolean flag for the failure handle processor
     */
    private void decrementRedeliveryCounter(Exchange exchange) {
        Message in = exchange.getIn();
        Integer counter = in.getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
        if (counter != null) {
            int prev = counter - 1;
            in.setHeader(Exchange.REDELIVERY_COUNTER, prev);
            // set boolean flag according to counter
            in.setHeader(Exchange.REDELIVERED, prev > 0 ? Boolean.TRUE : Boolean.FALSE);
        } else {
            // not redelivered
            in.setHeader(Exchange.REDELIVERY_COUNTER, 0);
            in.setHeader(Exchange.REDELIVERED, Boolean.FALSE);
        }
    }

    /**
     * Determines if redelivery is enabled by checking if any of the redelivery policy
     * settings may allow redeliveries.
     *
     * @return <tt>true</tt> if redelivery is possible, <tt>false</tt> otherwise
     * @throws Exception can be thrown
     */
    private boolean determineIfRedeliveryIsEnabled() throws Exception {
        // determine if redeliver is enabled either on error handler
        if (getRedeliveryPolicy().getMaximumRedeliveries() != 0) {
            // must check for != 0 as (-1 means redeliver forever)
            return true;
        }
        if (retryWhilePolicy != null) {
            return true;
        }

        // or on the exception policies
        if (!exceptionPolicies.isEmpty()) {
            // walk them to see if any of them have a maximum redeliveries > 0 or retry until set
            for (OnExceptionDefinition def : exceptionPolicies.values()) {

                String ref = def.getRedeliveryPolicyRef();
                if (ref != null) {
                    // lookup in registry if ref provided
                    RedeliveryPolicy policy = CamelContextHelper.mandatoryLookup(camelContext, ref, RedeliveryPolicy.class);
                    if (policy.getMaximumRedeliveries() != 0) {
                        // must check for != 0 as (-1 means redeliver forever)
                        return true;
                    }
                } else if (def.getRedeliveryPolicy() != null) {
                    Integer max = CamelContextHelper.parseInteger(camelContext, def.getRedeliveryPolicy().getMaximumRedeliveries());
                    if (max != null && max != 0) {
                        // must check for != 0 as (-1 means redeliver forever)
                        return true;
                    }
                }

                if (def.getRetryWhilePolicy() != null || def.getRetryWhile() != null) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets the number of exchanges that are pending for redelivery
     */
    public int getPendingRedeliveryCount() {
        int answer = redeliverySleepCounter.get();
        if (executorService != null && executorService instanceof ThreadPoolExecutor) {
            answer += ((ThreadPoolExecutor) executorService).getQueue().size();
        }

        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(output, outputAsync, deadLetter);

        // determine if redeliver is enabled or not
        redeliveryEnabled = determineIfRedeliveryIsEnabled();
        if (log.isTraceEnabled()) {
            log.trace("Redelivery enabled: {} on error handler: {}", redeliveryEnabled, this);
        }

        // we only need thread pool if redelivery is enabled
        if (redeliveryEnabled) {
            if (executorService == null) {
                // use default shared executor service
                executorService = camelContext.getErrorHandlerExecutorService();
            }
            if (log.isDebugEnabled()) {
                log.debug("Using ExecutorService: {} for redeliveries on error handler: {}", executorService, this);
            }
        }

        // reset flag when starting
        preparingShutdown = false;
        redeliverySleepCounter.set(0);
    }

    @Override
    protected void doStop() throws Exception {
        // noop, do not stop any services which we only do when shutting down
        // as the error handler can be context scoped, and should not stop in case
        // a route stops
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(deadLetter, output, outputAsync);
    }
}
