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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.model.ExceptionType;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.AsyncProcessorHelper;
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

    private class RedeliveryData {
        int redeliveryCounter;
        long redeliveryDelay;
        boolean sync = true;

        // default behaviour which can be overloaded on a per exception basis
        RedeliveryPolicy currentRedeliveryPolicy = redeliveryPolicy;
        Processor failureProcessor = deadLetter;
    }

    private static final transient Log LOG = LogFactory.getLog(DeadLetterChannel.class);
    private static final String FAILURE_HANDLED_PROPERTY = DeadLetterChannel.class.getName() + ".FAILURE_HANDLED";
    private Processor output;
    private Processor deadLetter;
    private AsyncProcessor outputAsync;
    private RedeliveryPolicy redeliveryPolicy;
    private Logger logger;

    public DeadLetterChannel(Processor output, Processor deadLetter) {
        this(output, deadLetter, new RedeliveryPolicy(), DeadLetterChannel.createDefaultLogger(),
            ErrorHandlerSupport.createDefaultExceptionPolicyStrategy());
    }

    public DeadLetterChannel(Processor output, Processor deadLetter, RedeliveryPolicy redeliveryPolicy, Logger logger, ExceptionPolicyStrategy exceptionPolicyStrategy) {
        this.deadLetter = deadLetter;
        this.output = output;
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
        return "DeadLetterChannel[" + output + ", " + deadLetter + ", " + redeliveryPolicy + "]";
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        return process(exchange, callback, new RedeliveryData());
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback, final RedeliveryData data) {

        while (true) {
            // We can't keep retrying if the route is being shutdown.
            if (!isRunAllowed()) {
                if (exchange.getException() == null) {
                    exchange.setException(new RejectedExecutionException());
                }
                callback.done(data.sync);
                return data.sync;
            }

            // if the exchange is transacted then let the underlysing system handle the redelivery etc.
            // this DeadLetterChannel is only for non transacted exchanges
            if (exchange.isTransacted() && exchange.getException() != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Transacted Exchange, this DeadLetterChannel is bypassed: " + exchange);
                }
                return data.sync;
            }

            if (exchange.getException() != null) {
                Throwable e = exchange.getException();
                exchange.setException(null); // Reset it since we are handling it.

                logger.log("Failed delivery for exchangeId: " + exchange.getExchangeId() + ". On delivery attempt: " + data.redeliveryCounter + " caught: " + e, e);
                data.redeliveryCounter = incrementRedeliveryCounter(exchange, e);

                ExceptionType exceptionPolicy = getExceptionPolicy(exchange, e);
                if (exceptionPolicy != null) {
                    data.currentRedeliveryPolicy = exceptionPolicy.createRedeliveryPolicy(data.currentRedeliveryPolicy);
                    Processor processor = exceptionPolicy.getErrorHandler();
                    if (processor != null) {
                        data.failureProcessor = processor;
                    }
                }
            }

            if (!data.currentRedeliveryPolicy.shouldRedeliver(data.redeliveryCounter)) {
                setFailureHandled(exchange, true);
                AsyncProcessor afp = AsyncProcessorTypeConverter.convert(data.failureProcessor);
                boolean sync = afp.process(exchange, new AsyncCallback() {
                    public void done(boolean sync) {
                        restoreExceptionOnExchange(exchange);
                        callback.done(data.sync);
                    }
                });

                restoreExceptionOnExchange(exchange);
                logger.log("Failed delivery for exchangeId: " + exchange.getExchangeId() + ". Handled by the failure processor: " + data.failureProcessor);
                return sync;
            }

            if (data.redeliveryCounter > 0) {
                // Figure out how long we should wait to resend this message.
                data.redeliveryDelay = data.currentRedeliveryPolicy.sleep(data.redeliveryDelay);
            }

            exchange.setProperty(EXCEPTION_CAUSE_PROPERTY, exchange.getException());
            exchange.setException(null);
            
            boolean sync = outputAsync.process(exchange, new AsyncCallback() {
                public void done(boolean sync) {
                    // Only handle the async case...
                    if (sync) {
                        return;
                    }
                    data.sync = false;
                    if (exchange.getException() != null) {
                        process(exchange, callback, data);
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

    public static boolean isFailureHandled(Exchange exchange) {
        return exchange.getProperty(FAILURE_HANDLED_PROPERTY) != null;
    }

    public static void setFailureHandled(Exchange exchange, boolean isHandled) {
        if (isHandled) {
            exchange.setProperty(FAILURE_HANDLED_PROPERTY, exchange.getException());
            exchange.setException(null);
        } else {
            exchange.setException(exchange.getProperty(FAILURE_HANDLED_PROPERTY, Throwable.class));
            exchange.removeProperty(FAILURE_HANDLED_PROPERTY);
        }

    }

    public static void restoreExceptionOnExchange(Exchange exchange) {
        exchange.setException(exchange.getProperty(FAILURE_HANDLED_PROPERTY, Throwable.class));
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
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
        exchange.setException(e);
        return next;
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
