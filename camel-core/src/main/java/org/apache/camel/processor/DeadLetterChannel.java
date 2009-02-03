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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.model.ExceptionType;
import org.apache.camel.model.LoggingLevel;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implements a <a
 * href="http://activemq.apache.org/camel/dead-letter-channel.html">Dead Letter
 * Channel</a> after attempting to redeliver the message using the
 * {@link RedeliveryPolicy}
 *
 * @version $Revision$
 */
public class DeadLetterChannel extends ErrorHandlerSupport implements AsyncProcessor {
    public static final String REDELIVERY_COUNTER = "org.apache.camel.RedeliveryCounter";
    public static final String REDELIVERED = "org.apache.camel.Redelivered";
    public static final String EXCEPTION_CAUSE_PROPERTY = "CamelCauseException";
    public static final String CAUGHT_EXCEPTION_HEADER = "org.apache.camel.CamelCaughtException";    
    
    private static final transient Log LOG = LogFactory.getLog(DeadLetterChannel.class);
    private static final String FAILURE_HANDLED_PROPERTY = DeadLetterChannel.class.getName() + ".FAILURE_HANDLED";
    
    private static Timer timer = new Timer();
    private Processor output;
    private Processor deadLetter;
    private AsyncProcessor outputAsync;
    private RedeliveryPolicy redeliveryPolicy;
    private Logger logger;
    private Processor redeliveryProcessor;

    private class RedeliveryData {
        int redeliveryCounter;
        long redeliveryDelay;
        boolean sync = true;
        Predicate handledPredicate;

        // default behavior which can be overloaded on a per exception basis
        RedeliveryPolicy currentRedeliveryPolicy = redeliveryPolicy;
        Processor failureProcessor = deadLetter;
    }
    
    private class RedeliverTimerTask extends TimerTask {
        private final Exchange exchange;
        private final AsyncCallback callback;
        private final RedeliveryData data;
        
        public RedeliverTimerTask(Exchange exchange, AsyncCallback callback, RedeliveryData data) {
            this.exchange = exchange;
            this.callback = callback;
            this.data = data;
        }

        @Override
        public void run() {
            //only handle the real AsyncProcess the exchange 
            outputAsync.process(exchange, new AsyncCallback() {
                public void done(boolean sync) {
                    // Only handle the async case...
                    if (sync) {
                        return;
                    }
                    data.sync = false;
                    // only process if the exchange hasn't failed
                    // and it has not been handled by the error processor
                    if (exchange.getException() != null && !isFailureHandled(exchange)) {
                        // if we are redelivering then sleep before trying again
                        asyncProcess(exchange, callback, data);
                    } else {
                        callback.done(sync);
                    }
                }
            });                
        } 
    }

    public DeadLetterChannel(Processor output, Processor deadLetter, Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy, Logger logger, ExceptionPolicyStrategy exceptionPolicyStrategy) {
        this.output = output;
        this.deadLetter = deadLetter;
        this.redeliveryProcessor = redeliveryProcessor;
        this.outputAsync = AsyncProcessorTypeConverter.convert(output);
        this.redeliveryPolicy = redeliveryPolicy;
        this.logger = logger;
        setExceptionPolicy(exceptionPolicyStrategy);
    }

    public static <E extends Exchange> Logger createDefaultLogger() {
        return new Logger(LOG, LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "DeadLetterChannel[" + output + ", " + deadLetter + "]";
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        return process(exchange, callback, new RedeliveryData());
    }

    /**
     * Processes the exchange using decorated with this dead letter channel.
     */
    protected boolean process(final Exchange exchange, final AsyncCallback callback, final RedeliveryData data) {

        while (true) {
            // we can't keep retrying if the route is being shutdown.
            if (!isRunAllowed()) {
                if (exchange.getException() == null) {
                    exchange.setException(new RejectedExecutionException());
                }
                callback.done(data.sync);
                return data.sync;
            }

            // if the exchange is transacted then let the underlying system handle the redelivery etc.
            // this DeadLetterChannel is only for non transacted exchanges
            if (exchange.isTransacted() && exchange.getException() != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("This is a transacted exchange, bypassing this DeadLetterChannel: " + this + " for exchange: " + exchange);
                }
                return data.sync;
            }

            // did previous processing caused an exception?
            if (exchange.getException() != null) {
                handleException(exchange, data);
            }

            // compute if we should redeliver or not
            boolean shouldRedeliver = shouldRedeliver(exchange, data);
            if (!shouldRedeliver) {
                return deliverToFaultProcessor(exchange, callback, data);
            }

            // if we are redelivering then sleep before trying again
            if (data.redeliveryCounter > 0) {
                // okay we will give it another go so clear the exception so we can try again
                if (exchange.getException() != null) {
                    exchange.setException(null);
                }

                // reset cached streams so they can be read again
                MessageHelper.resetStreamCache(exchange.getIn());

                // wait until we should redeliver
                data.redeliveryDelay = data.currentRedeliveryPolicy.sleep(data.redeliveryDelay);

                // letting onRedeliver be executed
                deliverToRedeliveryProcessor(exchange, callback, data);
            }

            // process the exchange
            boolean sync = outputAsync.process(exchange, new AsyncCallback() {
                public void done(boolean sync) {
                    // Only handle the async case...
                    if (sync) {
                        return;
                    }
                    data.sync = false;
                    // only process if the exchange hasn't failed
                    // and it has not been handled by the error processor
                    if (exchange.getException() != null && !isFailureHandled(exchange)) {                        
                        //TODO Call the Timer for the asyncProcessor
                        asyncProcess(exchange, callback, data);
                    } else {
                        callback.done(sync);
                    }
                }
            });
            if (!sync) {
                // It is going to be processed async..
                return false;
            }
            if (exchange.getException() == null || isFailureHandled(exchange)) {
                // If everything went well.. then we exit here..
                callback.done(true);
                return true;
            }
            // error occurred so loop back around.....
        }

    }

    protected void asyncProcess(final Exchange exchange, final AsyncCallback callback, final RedeliveryData data) {
        // set the timer here
        if (!isRunAllowed()) {
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            callback.done(data.sync);
            return;
        }

        // if the exchange is transacted then let the underlying system handle the redelivery etc.
        // this DeadLetterChannel is only for non transacted exchanges
        if (exchange.isTransacted() && exchange.getException() != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("This is a transacted exchange, bypassing this DeadLetterChannel: " + this + " for exchange: " + exchange);
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
            deliverToFaultProcessor(exchange, callback, data);
            return;
        }
        
        // process the next try
        // if we are redelivering then sleep before trying again
        if (data.redeliveryCounter > 0) {
            // okay we will give it another go so clear the exception so we can try again
            if (exchange.getException() != null) {
                exchange.setException(null);
            }
            // wait until we should redeliver
            data.redeliveryDelay = data.currentRedeliveryPolicy.getRedeliveryDelay(data.redeliveryDelay);
            timer.schedule(new RedeliverTimerTask(exchange, callback, data), data.redeliveryDelay);

            // letting onRedeliver be executed
            deliverToRedeliveryProcessor(exchange, callback, data);
        }
    }
    
    private void handleException(Exchange exchange, RedeliveryData data) {
        Throwable e = exchange.getException();
        // set the original caused exception
        exchange.setProperty(EXCEPTION_CAUSE_PROPERTY, e);

        // find the error handler to use (if any)
        ExceptionType exceptionPolicy = getExceptionPolicy(exchange, e);
        if (exceptionPolicy != null) {
            data.currentRedeliveryPolicy = exceptionPolicy.createRedeliveryPolicy(exchange.getContext(), data.currentRedeliveryPolicy);
            data.handledPredicate = exceptionPolicy.getHandledPolicy();
            Processor processor = exceptionPolicy.getErrorHandler();
            if (processor != null) {
                data.failureProcessor = processor;
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
    private boolean deliverToRedeliveryProcessor(final Exchange exchange, final AsyncCallback callback,
                                            final RedeliveryData data) {
        if (redeliveryProcessor == null) {
            return true;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("RedeliveryProcessor " + redeliveryProcessor + " is processing Exchange before its redelivered");
        }
        AsyncProcessor afp = AsyncProcessorTypeConverter.convert(redeliveryProcessor);
        boolean sync = afp.process(exchange, new AsyncCallback() {
            public void done(boolean sync) {
                callback.done(data.sync);
            }
        });

        return sync;
    }

    private boolean deliverToFaultProcessor(final Exchange exchange, final AsyncCallback callback,
                                            final RedeliveryData data) {
        // we did not success with the redelivery so now we let the failure processor handle it
        setFailureHandled(exchange);
        // must decrement the redelivery counter as we didn't process the redelivery but is
        // handling by the failure handler. So we must -1 to not let the counter be out-of-sync
        decrementRedeliveryCounter(exchange);

        AsyncProcessor afp = AsyncProcessorTypeConverter.convert(data.failureProcessor);
        boolean sync = afp.process(exchange, new AsyncCallback() {
            public void done(boolean sync) {
                restoreExceptionOnExchange(exchange, data.handledPredicate);
                callback.done(data.sync);
            }
        });

        String msg = "Failed delivery for exchangeId: " + exchange.getExchangeId()
                + ". Handled by the failure processor: " + data.failureProcessor;
        logFailedDelivery(false, exchange, msg, data, null);

        return sync;
    }

    // Properties
    // -------------------------------------------------------------------------

    public static boolean isFailureHandled(Exchange exchange) {
        return exchange.getProperty(FAILURE_HANDLED_PROPERTY) != null
            || exchange.getIn().getHeader(CAUGHT_EXCEPTION_HEADER) != null;
    }

    public static void setFailureHandled(Exchange exchange) {
        exchange.setProperty(FAILURE_HANDLED_PROPERTY, exchange.getException());
        exchange.getIn().setHeader(CAUGHT_EXCEPTION_HEADER, exchange.getException());
        exchange.setException(null);
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

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * Sets the redelivery policy
     */
    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Sets the logger strategy; which {@link Log} to use and which
     * {@link LoggingLevel} to use
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected static void restoreExceptionOnExchange(Exchange exchange, Predicate handledPredicate) {
        if (handledPredicate == null || !handledPredicate.matches(exchange)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("This exchange is not handled so its marked as failed: " + exchange);
            }
            // exception not handled, put exception back in the exchange
            exchange.setException(exchange.getProperty(FAILURE_HANDLED_PROPERTY, Throwable.class));
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("This exchange is handled so its marked as not failed: " + exchange);
            }
            exchange.setProperty(Exchange.EXCEPTION_HANDLED_PROPERTY, Boolean.TRUE);
        }
    }

    private void logFailedDelivery(boolean shouldRedeliver, Exchange exchange, String message, RedeliveryData data, Throwable e) {
        LoggingLevel newLogLevel;
        if (shouldRedeliver) {
            newLogLevel = data.currentRedeliveryPolicy.getRetryAttemptedLogLevel();
        } else {
            newLogLevel = data.currentRedeliveryPolicy.getRetriesExhaustedLogLevel();
        }
        if (e != null) {
            logger.log(message, e, newLogLevel);
        } else {
            logger.log(message, newLogLevel);
        }
    }

    private boolean shouldRedeliver(Exchange exchange, RedeliveryData data) {
        return data.currentRedeliveryPolicy.shouldRedeliver(data.redeliveryCounter);
    }

    /**
     * Increments the redelivery counter and adds the redelivered flag if the
     * message has been redelivered
     */
    protected int incrementRedeliveryCounter(Exchange exchange, Throwable e) {
        Message in = exchange.getIn();
        Integer counter = in.getHeader(REDELIVERY_COUNTER, Integer.class);
        int next = 1;
        if (counter != null) {
            next = counter + 1;
        }
        in.setHeader(REDELIVERY_COUNTER, next);
        in.setHeader(REDELIVERED, Boolean.TRUE);
        return next;
    }

    /**
     * Prepares the redelivery counter and boolean flag for the failure handle processor
     */
    private void decrementRedeliveryCounter(Exchange exchange) {
        Message in = exchange.getIn();
        Integer counter = in.getHeader(REDELIVERY_COUNTER, Integer.class);
        if (counter != null) {
            int prev = counter - 1;
            in.setHeader(REDELIVERY_COUNTER, prev);
            // set boolean flag according to counter
            in.setHeader(REDELIVERED, prev > 0 ? Boolean.TRUE : Boolean.FALSE);
        } else {
            // not redelivered
            in.setHeader(REDELIVERY_COUNTER, 0);
            in.setHeader(REDELIVERED, Boolean.FALSE);
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
