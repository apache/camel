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

import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Implements a <a
 * href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
 * Channel</a> after attempting to redeliver the message using the
 * {@link RedeliveryPolicy}
 *
 * @version $Revision$
 */
public class DeadLetterChannel extends ErrorHandlerSupport implements Processor {

    // TODO: Introduce option to allow async redelivery, eg to not block thread while delay
    // (eg the Timer task code). However we should consider using Channels that has internal
    // producer/consumer queues with "delayed" support so a redelivery is just to move an
    // exchange to this channel with the computed delay time
    // we need to provide option so end users can decide if they would like to spawn an async thread
    // or not. Also consider MEP as InOut does not work with async then as the original caller thread
    // is expecting a reply in the sync thread.

    // we can use a single shared static timer for async redeliveries
    private final Processor deadLetter;
    private final String deadLetterUri;
    private final Processor output;
    private final Processor redeliveryProcessor;
    private final RedeliveryPolicy redeliveryPolicy;
    private final Predicate handledPolicy;
    private final Logger logger;
    private final boolean useOriginalExchangePolicy;

    private class RedeliveryData {
        int redeliveryCounter;
        long redeliveryDelay;
        Predicate retryUntilPredicate;

        // default behavior which can be overloaded on a per exception basis
        RedeliveryPolicy currentRedeliveryPolicy = redeliveryPolicy;
        Processor deadLetterQueue = deadLetter;
        Processor onRedeliveryProcessor = redeliveryProcessor;
        Predicate handledPredicate = handledPolicy;
        boolean useOriginalExchange = useOriginalExchangePolicy;
    }
    
    /**
     * Creates the dead letter channel.
     *
     * @param output                    outer processor that should use this dead letter channel
     * @param deadLetter                the failure processor to send failed exchanges to
     * @param deadLetterUri             an optional uri for logging purpose
     * @param redeliveryProcessor       an optional processor to run before redelivert attempt
     * @param redeliveryPolicy          policy for redelivery
     * @param logger                    logger to use for logging failures and redelivery attempts
     * @param exceptionPolicyStrategy   strategy for onException handling
     * @param handledPolicy             policy for handling failed exception that are moved to the dead letter queue
     * @param useOriginalExchangePolicy should the original exchange be moved to the dead letter queue or the most recent exchange?
     */
    public DeadLetterChannel(Processor output, Processor deadLetter, String deadLetterUri, Processor redeliveryProcessor,
                             RedeliveryPolicy redeliveryPolicy, Logger logger, ExceptionPolicyStrategy exceptionPolicyStrategy,
                             Predicate handledPolicy, boolean useOriginalExchangePolicy) {
        this.output = output;
        this.deadLetter = deadLetter;
        this.deadLetterUri = deadLetterUri;
        this.redeliveryProcessor = redeliveryProcessor;
        this.redeliveryPolicy = redeliveryPolicy;
        this.logger = logger;
        this.handledPolicy = handledPolicy;
        this.useOriginalExchangePolicy = useOriginalExchangePolicy;
        setExceptionPolicy(exceptionPolicyStrategy);
    }

    @Override
    public String toString() {
        return "DeadLetterChannel[" + output + ", " + (deadLetterUri != null ? deadLetterUri : deadLetter) + "]";
    }

    public boolean supportTransacted() {
        return false;
    }

    public void process(Exchange exchange) throws Exception {
        processErrorHandler(exchange, new RedeliveryData());
    }

    /**
     * Processes the exchange decorated with this dead letter channel.
     */
    protected void processErrorHandler(final Exchange exchange, final RedeliveryData data) {

        while (true) {
            // we can't keep retrying if the route is being shutdown.
            if (!isRunAllowed()) {
                if (log.isDebugEnabled()) {
                    log.debug("Rejected execution as we are not started for exchange: " + exchange);
                }
                if (exchange.getException() == null) {
                    exchange.setException(new RejectedExecutionException());
                    return;
                }
            }

            // do not handle transacted exchanges that failed as this error handler does not support it
            if (exchange.isTransacted() && !supportTransacted() && exchange.getException() != null) {
                if (log.isDebugEnabled()) {
                    log.debug("This error handler does not support transacted exchanges."
                        + " Bypassing this error handler: " + this + " for exchangeId: " + exchange.getExchangeId());
                }
                return;
            }

            // did previous processing caused an exception?
            if (exchange.getException() != null) {
                handleException(exchange, data);
            }

            // compute if we should redeliver or not
            boolean shouldRedeliver = shouldRedeliver(exchange, data);
            if (!shouldRedeliver) {
                // no then move it to the dead letter queue
                deliverToDeadLetterQueue(exchange, data);
                // and we are finished since the exchanged was moved to the dead letter queue
                return;
            }

            // if we are redelivering then sleep before trying again
            if (data.redeliveryCounter > 0) {
                prepareExchangeForRedelivery(exchange);

                // wait until we should redeliver
                try {
                    data.redeliveryDelay = data.currentRedeliveryPolicy.sleep(data.redeliveryDelay, data.redeliveryCounter);
                } catch (InterruptedException e) {
                    log.debug("Sleep interrupted, are we stopping? " + (isStopping() || isStopped()));
                    // continue from top
                    continue;
                }

                // letting onRedeliver be executed
                deliverToRedeliveryProcessor(exchange, data);
            }

            // process the exchange
            try {
                output.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            // only process if the exchange hasn't failed
            // and it has not been handled by the error processor
            boolean done = exchange.getException() == null || ExchangeHelper.isFailureHandled(exchange);
            if (done) {
                return;
            }
            // error occurred so loop back around.....
        }

    }

    // Properties
    // -------------------------------------------------------------------------

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

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public Logger getLogger() {
        return logger;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    private void prepareExchangeForRedelivery(Exchange exchange) {
        // okay we will give it another go so clear the exception so we can try again
        if (exchange.getException() != null) {
            exchange.setException(null);
        }

        // clear rollback flags
        exchange.setProperty(Exchange.ROLLBACK_ONLY, null);

        // reset cached streams so they can be read again
        MessageHelper.resetStreamCache(exchange.getIn());
    }

    private void handleException(Exchange exchange, RedeliveryData data) {
        Throwable e = exchange.getException();

        // store the original caused exception in a property, so we can restore it later
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);

        // find the error handler to use (if any)
        OnExceptionDefinition exceptionPolicy = getExceptionPolicy(exchange, e);
        if (exceptionPolicy != null) {
            data.currentRedeliveryPolicy = exceptionPolicy.createRedeliveryPolicy(exchange.getContext(), data.currentRedeliveryPolicy);
            data.handledPredicate = exceptionPolicy.getHandledPolicy();
            data.retryUntilPredicate = exceptionPolicy.getRetryUntilPolicy();
            data.useOriginalExchange = exceptionPolicy.getUseOriginalExchangePolicy();

            // route specific failure handler?
            Processor processor = exceptionPolicy.getErrorHandler();
            if (processor != null) {
                data.deadLetterQueue = processor;
            }
            // route specific on redelivey?
            processor = exceptionPolicy.getOnRedelivery();
            if (processor != null) {
                data.onRedeliveryProcessor = processor;
            }
        }

        String msg = "Failed delivery for exchangeId: " + exchange.getExchangeId()
                + ". On delivery attempt: " + data.redeliveryCounter + " caught: " + e;
        logFailedDelivery(true, exchange, msg, data, e);

        data.redeliveryCounter = incrementRedeliveryCounter(exchange, e);
    }

    /**
     * Gives an optional configure redelivery processor a chance to process before the Exchange
     * will be redelivered. This can be used to alter the Exchange.
     */
    private void deliverToRedeliveryProcessor(final Exchange exchange, final RedeliveryData data) {
        if (data.onRedeliveryProcessor == null) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("RedeliveryProcessor " + data.onRedeliveryProcessor + " is processing Exchange: " + exchange + " before its redelivered");
        }

        try {
            data.onRedeliveryProcessor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        log.trace("Redelivery processor done");
    }

    /**
     * All redelivery attempts failed so move the exchange to the dead letter queue
     */
    private void deliverToDeadLetterQueue(final Exchange exchange, final RedeliveryData data) {
        if (data.deadLetterQueue == null) {
            return;
        }

        // we did not success with the redelivery so now we let the failure processor handle it
        ExchangeHelper.setFailureHandled(exchange);
        // must decrement the redelivery counter as we didn't process the redelivery but is
        // handling by the failure handler. So we must -1 to not let the counter be out-of-sync
        decrementRedeliveryCounter(exchange);
        // reset cached streams so they can be read again
        MessageHelper.resetStreamCache(exchange.getIn());

        // prepare original exchange if it should be moved instead of most recent
        if (data.useOriginalExchange) {
            if (log.isTraceEnabled()) {
                log.trace("Using the original exchange bodies in the DedLetterQueue instead of the current exchange bodies");
            }

            Exchange original = exchange.getUnitOfWork().getOriginalExchange();
            // replace exchange IN/OUT with from original
            exchange.setIn(original.getIn());
            if (original.hasOut()) {
                exchange.setOut(original.getOut());
            } else {
                exchange.setOut(null);
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("DeadLetterQueue " + data.deadLetterQueue + " is processing Exchange: " + exchange);
        }
        try {
            data.deadLetterQueue.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        log.trace("DedLetterQueue processor done");

        prepareExchangeAfterMovedToDeadLetterQueue(exchange, data.handledPredicate);

        String msg = "Failed delivery for exchangeId: " + exchange.getExchangeId()
                + ". Moved to the dead letter queue: " + data.deadLetterQueue;
        logFailedDelivery(false, exchange, msg, data, null);
    }

    private void prepareExchangeAfterMovedToDeadLetterQueue(Exchange exchange, Predicate handledPredicate) {
        if (handledPredicate == null || !handledPredicate.matches(exchange)) {
            if (log.isDebugEnabled()) {
                log.debug("This exchange is not handled so its marked as failed: " + exchange);
            }
            // exception not handled, put exception back in the exchange
            exchange.setProperty(Exchange.EXCEPTION_HANDLED, Boolean.FALSE);
            exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
        } else {
            if (log.isDebugEnabled()) {
                log.debug("This exchange is handled so its marked as not failed: " + exchange);
            }
            exchange.setProperty(Exchange.EXCEPTION_HANDLED, Boolean.TRUE);
        }
    }

    private void logFailedDelivery(boolean shouldRedeliver, Exchange exchange, String message, RedeliveryData data, Throwable e) {
        LoggingLevel newLogLevel;
        if (shouldRedeliver) {
            newLogLevel = data.currentRedeliveryPolicy.getRetryAttemptedLogLevel();
        } else {
            newLogLevel = data.currentRedeliveryPolicy.getRetriesExhaustedLogLevel();
        }
        if (exchange.isRollbackOnly()) {
            String msg = "Rollback exchange";
            if (exchange.getException() != null) {
                msg = msg + " due: " + exchange.getException().getMessage();
            }
            if (newLogLevel == LoggingLevel.ERROR || newLogLevel == LoggingLevel.FATAL) {
                // log intented rollback on maximum WARN level (no ERROR or FATAL)
                logger.log(msg, LoggingLevel.WARN);
            } else {
                // otherwise use the desired logging level
                logger.log(msg, newLogLevel);
            }
        } else if (data.currentRedeliveryPolicy.isLogStackTrace() && e != null) {
            logger.log(message, e, newLogLevel);
        } else {
            logger.log(message, newLogLevel);
        }
    }

    private boolean shouldRedeliver(Exchange exchange, RedeliveryData data) {
        return data.currentRedeliveryPolicy.shouldRedeliver(exchange, data.redeliveryCounter, data.retryUntilPredicate);
    }

    /**
     * Increments the redelivery counter and adds the redelivered flag if the
     * message has been redelivered
     */
    private int incrementRedeliveryCounter(Exchange exchange, Throwable e) {
        Message in = exchange.getIn();
        Integer counter = in.getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
        int next = 1;
        if (counter != null) {
            next = counter + 1;
        }
        in.setHeader(Exchange.REDELIVERY_COUNTER, next);
        in.setHeader(Exchange.REDELIVERED, Boolean.TRUE);
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
        ServiceHelper.startServices(output, deadLetter);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(deadLetter, output);
    }    
    
}
