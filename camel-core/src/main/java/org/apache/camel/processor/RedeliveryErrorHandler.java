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

import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Base redeliverable error handler that also supports a final dead letter queue in case
 * all redelivery attempts fail.
 * <p/>
 * This implementation should contain all the error handling logic and the sub classes
 * should only configure it according to what they support.
 *
 * @version 
 */
public abstract class RedeliveryErrorHandler extends ErrorHandlerSupport implements AsyncProcessor {

    private static ScheduledExecutorService executorService;
    protected final CamelContext camelContext;
    protected final Processor deadLetter;
    protected final String deadLetterUri;
    protected final Processor output;
    protected final AsyncProcessor outputAsync;
    protected final Processor redeliveryProcessor;
    protected final RedeliveryPolicy redeliveryPolicy;
    protected final Predicate handledPolicy;
    protected final Predicate retryWhilePolicy;
    protected final CamelLogger logger;
    protected final boolean useOriginalMessagePolicy;

    /**
     * Contains the current redelivery data
     */
    protected class RedeliveryData {
        boolean sync = true;
        int redeliveryCounter;
        long redeliveryDelay;
        Predicate retryWhilePredicate = retryWhilePolicy;
        boolean redeliverFromSync;

        // default behavior which can be overloaded on a per exception basis
        RedeliveryPolicy currentRedeliveryPolicy = redeliveryPolicy;
        Processor deadLetterProcessor = deadLetter;
        Processor failureProcessor;
        Processor onRedeliveryProcessor = redeliveryProcessor;
        Predicate handledPredicate = handledPolicy;
        Predicate continuedPredicate;
        boolean useOriginalInMessage = useOriginalMessagePolicy;
        boolean asyncDelayedRedelivery = redeliveryPolicy.isAsyncDelayedRedelivery();
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

        public AsyncRedeliveryTask(Exchange exchange, AsyncCallback callback, RedeliveryData data) {
            this.exchange = exchange;
            this.callback = callback;
            this.data = data;
        }

        public Boolean call() throws Exception {
            // prepare for redelivery
            prepareExchangeForRedelivery(exchange);

            // letting onRedeliver be executed at first
            deliverToOnRedeliveryProcessor(exchange, data);

            if (log.isTraceEnabled()) {
                log.trace("Redelivering exchangeId: " + exchange.getExchangeId() + " -> " + outputAsync + " for Exchange: " + exchange);
            }

            // emmit event we are doing redelivery
            EventHelper.notifyExchangeRedelivery(exchange.getContext(), exchange, data.redeliveryCounter);

            // process the exchange (also redelivery)
            boolean sync;
            if (data.redeliverFromSync) {
                // this redelivery task was scheduled from synchronous, which we forced to be asynchronous from
                // this error handler, which means we have to invoke the callback with false, to have the callback
                // be notified when we are done
                sync = AsyncProcessorHelper.process(outputAsync, exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        if (log.isTraceEnabled()) {
                            log.trace("Redelivering exchangeId: " + exchange.getExchangeId() + " done sync: " + doneSync);
                        }

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
                sync = AsyncProcessorHelper.process(outputAsync, exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        if (log.isTraceEnabled()) {
                            log.trace("Redelivering exchangeId: " + exchange.getExchangeId() + " done sync: " + doneSync);
                        }

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

    public RedeliveryErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger, Processor redeliveryProcessor,
                                  RedeliveryPolicy redeliveryPolicy, Predicate handledPolicy, Processor deadLetter,
                                  String deadLetterUri, boolean useOriginalMessagePolicy, Predicate retryWhile) {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        ObjectHelper.notNull(redeliveryPolicy, "RedeliveryPolicy", this);

        this.camelContext = camelContext;
        this.redeliveryProcessor = redeliveryProcessor;
        this.deadLetter = deadLetter;
        this.output = output;
        this.outputAsync = AsyncProcessorTypeConverter.convert(output);
        this.redeliveryPolicy = redeliveryPolicy;
        this.logger = logger;
        this.deadLetterUri = deadLetterUri;
        this.handledPolicy = handledPolicy;
        this.useOriginalMessagePolicy = useOriginalMessagePolicy;
        this.retryWhilePolicy = retryWhile;
    }

    public boolean supportTransacted() {
        return false;
    }

    public void process(Exchange exchange) throws Exception {
        if (output == null) {
            // no output then just return
            return;
        }
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        return processErrorHandler(exchange, callback, new RedeliveryData());
    }

    /**
     * Process the exchange using redelivery error handling.
     */
    protected boolean processErrorHandler(final Exchange exchange, final AsyncCallback callback, final RedeliveryData data) {

        // use looping to have redelivery attempts
        while (true) {

            // can we still run
            if (!isRunAllowed()) {
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
                handleException(exchange, data);
            }

            // compute if we should redeliver or not
            boolean shouldRedeliver = shouldRedeliver(exchange, data);
            if (!shouldRedeliver) {
                // no we should not redeliver to the same output so either try an onException (if any given)
                // or the dead letter queue
                Processor target = data.failureProcessor != null ? data.failureProcessor : data.deadLetterProcessor;
                // deliver to the failure processor (either an on exception or dead letter queue
                boolean sync = deliverToFailureProcessor(target, exchange, data, callback);
                // we are breaking out
                return sync;
            }

            if (data.redeliveryCounter > 0) {
                // calculate delay
                data.redeliveryDelay = data.currentRedeliveryPolicy.calculateRedeliveryDelay(data.redeliveryDelay, data.redeliveryCounter);

                if (data.redeliveryDelay > 0) {
                    // okay there is a delay so create a scheduled task to have it executed in the future

                    if (data.currentRedeliveryPolicy.isAsyncDelayedRedelivery() && !exchange.isTransacted()) {
                        // let the RedeliverTask be the logic which tries to redeliver the Exchange which we can used a scheduler to
                        // have it being executed in the future, or immediately
                        // we are continuing asynchronously

                        // mark we are routing async from now and that this redelivery task came from a synchronous routing
                        data.sync = false;
                        data.redeliverFromSync = true;
                        AsyncRedeliveryTask task = new AsyncRedeliveryTask(exchange, callback, data);

                        // schedule the redelivery task
                        if (log.isTraceEnabled()) {
                            log.trace("Scheduling redelivery task to run in " + data.redeliveryDelay + " millis for exchangeId: " + exchange.getExchangeId());
                        }
                        executorService.schedule(task, data.redeliveryDelay, TimeUnit.MILLISECONDS);

                        return false;
                    } else {
                        // async delayed redelivery was disabled or we are transacted so we must be synchronous
                        // as the transaction manager requires to execute in the same thread context
                        try {
                            data.currentRedeliveryPolicy.sleep(data.redeliveryDelay);
                        } catch (InterruptedException e) {
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
                prepareExchangeForRedelivery(exchange);

                // letting onRedeliver be executed
                deliverToOnRedeliveryProcessor(exchange, data);

                // emmit event we are doing redelivery
                EventHelper.notifyExchangeRedelivery(exchange.getContext(), exchange, data.redeliveryCounter);
            }

            // process the exchange (also redelivery)
            boolean sync = AsyncProcessorHelper.process(outputAsync, exchange, new AsyncCallback() {
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
     * This logic is only executed if we have to retry redelivery asynchronously, which have to be done from the callback.
     * <p/>
     * And therefore the logic is a bit different than the synchronous <tt>processErrorHandler</tt> method which can use
     * a loop based redelivery technique. However this means that these two methods in overall have to be in <b>sync</b>
     * in terms of logic.
     */
    protected void processAsyncErrorHandler(final Exchange exchange, final AsyncCallback callback, final RedeliveryData data) {
        // can we still run
        if (!isRunAllowed()) {
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            callback.done(data.sync);
            return;
        }

        // did previous processing cause an exception?
        boolean handle = shouldHandleException(exchange);
        if (handle) {
            handleException(exchange, data);
        }

        // compute if we should redeliver or not
        boolean shouldRedeliver = shouldRedeliver(exchange, data);
        if (!shouldRedeliver) {
            // no we should not redeliver to the same output so either try an onException (if any given)
            // or the dead letter queue
            Processor target = data.failureProcessor != null ? data.failureProcessor : data.deadLetterProcessor;
            // deliver to the failure processor (either an on exception or dead letter queue
            deliverToFailureProcessor(target, exchange, data, callback);
            // we are breaking out
            return;
        }

        if (data.redeliveryCounter > 0) {
            // let the RedeliverTask be the logic which tries to redeliver the Exchange which we can used a scheduler to
            // have it being executed in the future, or immediately
            // Note: the data.redeliverFromSync should be kept as is, in case it was enabled previously
            // to ensure the callback will continue routing from where we left
            AsyncRedeliveryTask task = new AsyncRedeliveryTask(exchange, callback, data);

            // calculate the redelivery delay
            data.redeliveryDelay = data.currentRedeliveryPolicy.calculateRedeliveryDelay(data.redeliveryDelay, data.redeliveryCounter);
            if (data.redeliveryDelay > 0) {
                // schedule the redelivery task
                if (log.isTraceEnabled()) {
                    log.trace("Scheduling redelivery task to run in " + data.redeliveryDelay + " millis for exchangeId: " + exchange.getExchangeId());
                }
                executorService.schedule(task, data.redeliveryDelay, TimeUnit.MILLISECONDS);
            } else {
                // execute the task immediately
                executorService.submit(task);
            }
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

        if (log.isTraceEnabled()) {
            log.trace("Is exchangeId: " + exchange.getExchangeId() + " done? " + answer);
        }
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

        if (log.isTraceEnabled()) {
            log.trace("Is exchangeId: " + exchange.getExchangeId() + " interrupted? " + answer);
        }
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

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public CamelLogger getLogger() {
        return logger;
    }

    protected void prepareExchangeForContinue(Exchange exchange, RedeliveryData data) {
        Exception caught = exchange.getException();

        // continue is a kind of redelivery so reuse the logic to prepare
        prepareExchangeForRedelivery(exchange);
        // its continued then remove traces of redelivery attempted and caught exception
        exchange.getIn().removeHeader(Exchange.REDELIVERED);
        exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
        exchange.getIn().removeHeader(Exchange.REDELIVERY_MAX_COUNTER);
        // keep the Exchange.EXCEPTION_CAUGHT as property so end user knows the caused exception

        // create log message
        String msg = "Failed delivery for exchangeId: " + exchange.getExchangeId();
        msg = msg + ". Exhausted after delivery attempt: " + data.redeliveryCounter + " caught: " + caught;
        msg = msg + ". Handled and continue routing.";

        // log that we failed but want to continue
        logFailedDelivery(false, false, true, exchange, msg, data, null);
    }

    protected void prepareExchangeForRedelivery(Exchange exchange) {
        // okay we will give it another go so clear the exception so we can try again
        exchange.setException(null);

        // clear rollback flags
        exchange.setProperty(Exchange.ROLLBACK_ONLY, null);

        // reset cached streams so they can be read again
        MessageHelper.resetStreamCache(exchange.getIn());
    }

    protected void handleException(Exchange exchange, RedeliveryData data) {
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
            data.useOriginalInMessage = exceptionPolicy.isUseOriginalMessage();
            data.asyncDelayedRedelivery = exceptionPolicy.isAsyncDelayedRedelivery(exchange.getContext());

            // route specific failure handler?
            Processor processor = exceptionPolicy.getErrorHandler();
            if (processor != null) {
                data.failureProcessor = processor;
            }
            // route specific on redelivery?
            processor = exceptionPolicy.getOnRedelivery();
            if (processor != null) {
                data.onRedeliveryProcessor = processor;
            }
        }

        String msg = "Failed delivery for exchangeId: " + exchange.getExchangeId()
                + ". On delivery attempt: " + data.redeliveryCounter + " caught: " + e;
        logFailedDelivery(true, false, false, exchange, msg, data, e);

        data.redeliveryCounter = incrementRedeliveryCounter(exchange, e, data);
    }

    /**
     * Gives an optional configure redelivery processor a chance to process before the Exchange
     * will be redelivered. This can be used to alter the Exchange.
     */
    protected void deliverToOnRedeliveryProcessor(final Exchange exchange, final RedeliveryData data) {
        if (data.onRedeliveryProcessor == null) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Redelivery processor " + data.onRedeliveryProcessor + " is processing Exchange: " + exchange
                    + " before its redelivered");
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
    protected boolean deliverToFailureProcessor(final Processor processor, final Exchange exchange,
                                                final RedeliveryData data, final AsyncCallback callback) {
        boolean sync = true;

        Exception caught = exchange.getException();

        // we did not success with the redelivery so now we let the failure processor handle it
        // clear exception as we let the failure processor handle it
        exchange.setException(null);

        boolean handled = false;
        // regard both handled or continued as being handled
        if (shouldHandled(exchange, data) || shouldContinue(exchange, data)) {
            // its handled then remove traces of redelivery attempted
            exchange.getIn().removeHeader(Exchange.REDELIVERED);
            exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
            exchange.getIn().removeHeader(Exchange.REDELIVERY_MAX_COUNTER);
            handled = true;
        } else {
            // must decrement the redelivery counter as we didn't process the redelivery but is
            // handling by the failure handler. So we must -1 to not let the counter be out-of-sync
            decrementRedeliveryCounter(exchange);
        }

        // is the a failure processor to process the Exchange
        if (processor != null) {

            // reset cached streams so they can be read again
            MessageHelper.resetStreamCache(exchange.getIn());

            // prepare original IN body if it should be moved instead of current body
            if (data.useOriginalInMessage) {
                if (log.isTraceEnabled()) {
                    log.trace("Using the original IN message instead of current");
                }

                Message original = exchange.getUnitOfWork().getOriginalInMessage();
                exchange.setIn(original);
            }

            if (log.isTraceEnabled()) {
                log.trace("Failure processor " + processor + " is processing Exchange: " + exchange);
            }

            // store the last to endpoint as the failure endpoint
            exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));

            // the failure processor could also be asynchronous
            AsyncProcessor afp = AsyncProcessorTypeConverter.convert(processor);
            sync = AsyncProcessorHelper.process(afp, exchange, new AsyncCallback() {
                public void done(boolean sync) {
                    if (log.isTraceEnabled()) {
                        log.trace("Failure processor done: " + processor + " processing Exchange: " + exchange);
                    }
                    try {
                        prepareExchangeAfterFailure(exchange, data);
                        // fire event as we had a failure processor to handle it, which there is a event for
                        boolean deadLetterChannel = processor == data.deadLetterProcessor && data.deadLetterProcessor != null;
                        EventHelper.notifyExchangeFailureHandled(exchange.getContext(), exchange, processor, deadLetterChannel);
                    } finally {
                        // if the fault was handled asynchronously, this should be reflected in the callback as well
                        data.sync &= sync;
                        callback.done(data.sync);
                    }
                }
            });
        } else {
            try {
                // no processor but we need to prepare after failure as well
                prepareExchangeAfterFailure(exchange, data);
            } finally {
                // callback we are done
                callback.done(data.sync);
            }
        }

        // create log message
        String msg = "Failed delivery for exchangeId: " + exchange.getExchangeId();
        msg = msg + ". Exhausted after delivery attempt: " + data.redeliveryCounter + " caught: " + caught;
        if (processor != null) {
            msg = msg + ". Processed by failure processor: " + processor;
        }

        // log that we failed delivery as we are exhausted
        logFailedDelivery(false, handled, false, exchange, msg, data, null);

        return sync;
    }

    protected void prepareExchangeAfterFailure(final Exchange exchange, final RedeliveryData data) {
        // we could not process the exchange so we let the failure processor handled it
        ExchangeHelper.setFailureHandled(exchange);

        // honor if already set a handling
        boolean alreadySet = exchange.getProperty(Exchange.ERRORHANDLER_HANDLED) != null;
        if (alreadySet) {
            boolean handled = exchange.getProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.class);
            if (log.isTraceEnabled()) {
                log.trace("This exchange has already been marked for handling: " + handled);
            }
            if (handled) {
                exchange.setException(null);
            } else {
                // exception not handled, put exception back in the exchange
                exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
                // and put failure endpoint back as well
                exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
            }
            return;
        }

        if (shouldHandled(exchange, data)) {
            if (log.isTraceEnabled()) {
                log.trace("This exchange is handled so its marked as not failed: " + exchange);
            }
            exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.TRUE);
        } else if (shouldContinue(exchange, data)) {
            if (log.isTraceEnabled()) {
                log.trace("This exchange is continued: " + exchange);
            }
            // okay we want to continue then prepare the exchange for that as well
            prepareExchangeForContinue(exchange, data);
        } else {
            if (log.isTraceEnabled()) {
                log.trace("This exchange is not handled or continued so its marked as failed: " + exchange);
            }
            // exception not handled, put exception back in the exchange
            exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, Boolean.FALSE);
            exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
            // and put failure endpoint back as well
            exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
        }
    }

    private void logFailedDelivery(boolean shouldRedeliver, boolean handled, boolean continued, Exchange exchange, String message, RedeliveryData data, Throwable e) {
        if (logger == null) {
            return;
        }

        if (handled && !data.currentRedeliveryPolicy.isLogHandled()) {
            // do not log handled
            return;
        }

        if (continued && !data.currentRedeliveryPolicy.isLogContinued()) {
            // do not log handled
            return;
        }

        if (shouldRedeliver && !data.currentRedeliveryPolicy.isLogRetryAttempted()) {
            // do not log retry attempts
            return;
        }

        if (!shouldRedeliver && !data.currentRedeliveryPolicy.isLogExhausted()) {
            // do not log exhausted
            return;
        }

        LoggingLevel newLogLevel;
        boolean logStrackTrace;
        if (shouldRedeliver) {
            newLogLevel = data.currentRedeliveryPolicy.getRetryAttemptedLogLevel();
            logStrackTrace = data.currentRedeliveryPolicy.isLogRetryStackTrace();
        } else {
            newLogLevel = data.currentRedeliveryPolicy.getRetriesExhaustedLogLevel();
            logStrackTrace = data.currentRedeliveryPolicy.isLogStackTrace();
        }
        if (e == null) {
            e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        }

        if (exchange.isRollbackOnly()) {
            String msg = "Rollback exchange";
            if (exchange.getException() != null) {
                msg = msg + " due: " + exchange.getException().getMessage();
            }
            if (newLogLevel == LoggingLevel.ERROR) {
                // log intended rollback on maximum WARN level (no ERROR)
                logger.log(msg, LoggingLevel.WARN);
            } else {
                // otherwise use the desired logging level
                logger.log(msg, newLogLevel);
            }
        } else if (e != null && logStrackTrace) {
            logger.log(message, e, newLogLevel);
        } else {
            logger.log(message, newLogLevel);
        }
    }

    /**
     * Determines whether or not to we should try to redeliver
     *
     * @param exchange the current exchange
     * @param data     the redelivery data
     * @return <tt>true</tt> to redeliver, or <tt>false</tt> to exhaust.
     */
    private boolean shouldRedeliver(Exchange exchange, RedeliveryData data) {
        // if marked as rollback only then do not redeliver
        boolean rollbackOnly = exchange.getProperty(Exchange.ROLLBACK_ONLY, false, Boolean.class);
        if (rollbackOnly) {
            if (log.isTraceEnabled()) {
                log.trace("This exchange is marked as rollback only, should not be redelivered: " + exchange);
            }
            return false;
        }
        return data.currentRedeliveryPolicy.shouldRedeliver(exchange, data.redeliveryCounter, data.retryWhilePredicate);
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
    private boolean shouldHandled(Exchange exchange, RedeliveryData data) {
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

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(output, outputAsync, deadLetter);
        // use a shared scheduler
        if (executorService == null || executorService.isShutdown()) {
            // camel context will shutdown the executor when it shutdown so no need to shut it down when stopping
            executorService = camelContext.getExecutorServiceStrategy().newScheduledThreadPool(this, "ErrorHandlerRedeliveryTask");
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(deadLetter, output, outputAsync);
    }

}
