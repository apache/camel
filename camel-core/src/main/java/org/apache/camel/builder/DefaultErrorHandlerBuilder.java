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
package org.apache.camel.builder;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.DefaultErrorHandler;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ExpressionToPredicateAdapter;
import org.slf4j.LoggerFactory;

/**
 * The default error handler builder.
 *
 * @version 
 */
public class DefaultErrorHandlerBuilder extends ErrorHandlerBuilderSupport {

    protected CamelLogger logger;
    protected RedeliveryPolicy redeliveryPolicy;
    protected Processor onRedelivery;
    protected Predicate retryWhile;
    protected String retryWhileRef;
    protected Processor failureProcessor;
    protected Endpoint deadLetter;
    protected String deadLetterUri;
    protected boolean deadLetterHandleNewException = true;
    protected boolean useOriginalMessage;
    protected boolean asyncDelayedRedelivery;
    protected String executorServiceRef;
    protected ScheduledExecutorService executorService;
    protected Processor onPrepareFailure;
    protected Processor onExceptionOccurred;

    public DefaultErrorHandlerBuilder() {
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        DefaultErrorHandler answer = new DefaultErrorHandler(routeContext.getCamelContext(), processor, getLogger(), getOnRedelivery(), 
            getRedeliveryPolicy(), getExceptionPolicyStrategy(), getRetryWhilePolicy(routeContext.getCamelContext()),
                getExecutorService(routeContext.getCamelContext()), getOnPrepareFailure(), getOnExceptionOccurred());
        // configure error handler before we can use it
        configure(routeContext, answer);
        return answer;
    }

    public boolean supportTransacted() {
        return false;
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        DefaultErrorHandlerBuilder answer = new DefaultErrorHandlerBuilder();
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(DefaultErrorHandlerBuilder other) {
        super.cloneBuilder(other);

        if (logger != null) {
            other.setLogger(logger);
        }
        if (redeliveryPolicy != null) {
            other.setRedeliveryPolicy(redeliveryPolicy.copy());
        }
        if (onRedelivery != null) {
            other.setOnRedelivery(onRedelivery);
        }
        if (retryWhile != null) {
            other.setRetryWhile(retryWhile);
        }
        if (retryWhileRef != null) {
            other.setRetryWhileRef(retryWhileRef);
        }
        if (failureProcessor != null) {
            other.setFailureProcessor(failureProcessor);
        }
        if (deadLetter != null) {
            other.setDeadLetter(deadLetter);
        }
        if (deadLetterUri != null) {
            other.setDeadLetterUri(deadLetterUri);
        }
        if (onPrepareFailure != null) {
            other.setOnPrepareFailure(onPrepareFailure);
        }
        if (onExceptionOccurred != null) {
            other.setOnExceptionOccurred(onExceptionOccurred);
        }
        other.setDeadLetterHandleNewException(deadLetterHandleNewException);
        other.setUseOriginalMessage(useOriginalMessage);
        other.setAsyncDelayedRedelivery(asyncDelayedRedelivery);
        other.setExecutorServiceRef(executorServiceRef);
    }

    // Builder methods
    // -------------------------------------------------------------------------
    public DefaultErrorHandlerBuilder backOffMultiplier(double backOffMultiplier) {
        getRedeliveryPolicy().backOffMultiplier(backOffMultiplier);
        return this;
    }

    public DefaultErrorHandlerBuilder collisionAvoidancePercent(double collisionAvoidancePercent) {
        getRedeliveryPolicy().collisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    /**
     * @deprecated will be removed in the near future. Use {@link #redeliveryDelay(long)} instead
     */
    @Deprecated
    public DefaultErrorHandlerBuilder redeliverDelay(long delay) {
        getRedeliveryPolicy().redeliveryDelay(delay);
        return this;
    }

    public DefaultErrorHandlerBuilder redeliveryDelay(long delay) {
        getRedeliveryPolicy().redeliveryDelay(delay);
        return this;
    }

    public DefaultErrorHandlerBuilder delayPattern(String delayPattern) {
        getRedeliveryPolicy().delayPattern(delayPattern);
        return this;
    }

    public DefaultErrorHandlerBuilder maximumRedeliveries(int maximumRedeliveries) {
        getRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    public DefaultErrorHandlerBuilder disableRedelivery() {
        getRedeliveryPolicy().maximumRedeliveries(0);
        return this;
    }

    public DefaultErrorHandlerBuilder maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        getRedeliveryPolicy().maximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    public DefaultErrorHandlerBuilder useCollisionAvoidance() {
        getRedeliveryPolicy().useCollisionAvoidance();
        return this;
    }

    public DefaultErrorHandlerBuilder useExponentialBackOff() {
        getRedeliveryPolicy().useExponentialBackOff();
        return this;
    }

    public DefaultErrorHandlerBuilder retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        getRedeliveryPolicy().setRetriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }

    public DefaultErrorHandlerBuilder retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        getRedeliveryPolicy().setRetryAttemptedLogLevel(retryAttemptedLogLevel);
        return this;
    }

    public DefaultErrorHandlerBuilder logStackTrace(boolean logStackTrace) {
        getRedeliveryPolicy().setLogStackTrace(logStackTrace);
        return this;
    }

    public DefaultErrorHandlerBuilder logRetryStackTrace(boolean logRetryStackTrace) {
        getRedeliveryPolicy().setLogRetryStackTrace(logRetryStackTrace);
        return this;
    }

    public DefaultErrorHandlerBuilder logHandled(boolean logHandled) {
        getRedeliveryPolicy().setLogHandled(logHandled);
        return this;
    }

    public DefaultErrorHandlerBuilder logNewException(boolean logNewException) {
        getRedeliveryPolicy().setLogNewException(logNewException);
        return this;
    }

    public DefaultErrorHandlerBuilder logExhausted(boolean logExhausted) {
        getRedeliveryPolicy().setLogExhausted(logExhausted);
        return this;
    }

    public DefaultErrorHandlerBuilder logExhaustedMessageHistory(boolean logExhaustedMessageHistory) {
        getRedeliveryPolicy().setLogExhaustedMessageHistory(logExhaustedMessageHistory);
        return this;
    }
    
    public DefaultErrorHandlerBuilder logExhaustedMessageBody(boolean logExhaustedMessageBody) {
        getRedeliveryPolicy().setLogExhaustedMessageBody(logExhaustedMessageBody);
        return this;
    }

    public DefaultErrorHandlerBuilder exchangeFormatterRef(String exchangeFormatterRef) {
        getRedeliveryPolicy().setExchangeFormatterRef(exchangeFormatterRef);
        return this;
    }

    /**
     * Will allow asynchronous delayed redeliveries. The route, in particular the consumer's component,
     * must support the Asynchronous Routing Engine (e.g. seda)
     *
     * @see org.apache.camel.processor.RedeliveryPolicy#setAsyncDelayedRedelivery(boolean)
     * @return the builder
     */
    public DefaultErrorHandlerBuilder asyncDelayedRedelivery() {
        getRedeliveryPolicy().setAsyncDelayedRedelivery(true);
        return this;
    }

    /**
     * Controls whether to allow redelivery while stopping/shutting down a route that uses error handling.
     *
     * @param allowRedeliveryWhileStopping <tt>true</tt> to allow redelivery, <tt>false</tt> to reject redeliveries
     * @return the builder
     */
    public DefaultErrorHandlerBuilder allowRedeliveryWhileStopping(boolean allowRedeliveryWhileStopping) {
        getRedeliveryPolicy().setAllowRedeliveryWhileStopping(allowRedeliveryWhileStopping);
        return this;
    }

    /**
     * Sets a reference to a thread pool to be used for redelivery.
     *
     * @param ref reference to a scheduled thread pool
     * @return the builder.
     */
    public DefaultErrorHandlerBuilder executorServiceRef(String ref) {
        setExecutorServiceRef(ref);
        return this;
    }

    /**
     * Sets the logger used for caught exceptions
     *
     * @param logger the logger
     * @return the builder
     */
    public DefaultErrorHandlerBuilder logger(CamelLogger logger) {
        setLogger(logger);
        return this;
    }

    /**
     * Sets the logging level of exceptions caught
     *
     * @param level the logging level
     * @return the builder
     */
    public DefaultErrorHandlerBuilder loggingLevel(LoggingLevel level) {
        getLogger().setLevel(level);
        return this;
    }

    /**
     * Sets the log used for caught exceptions
     *
     * @param log the logger
     * @return the builder
     */
    public DefaultErrorHandlerBuilder log(org.slf4j.Logger log) {
        getLogger().setLog(log);
        return this;
    }

    /**
     * Sets the log used for caught exceptions
     *
     * @param log the log name
     * @return the builder
     */
    public DefaultErrorHandlerBuilder log(String log) {
        return log(LoggerFactory.getLogger(log));
    }

    /**
     * Sets the log used for caught exceptions
     *
     * @param log the log class
     * @return the builder
     */
    public DefaultErrorHandlerBuilder log(Class<?> log) {
        return log(LoggerFactory.getLogger(log));
    }

    /**
     * Sets a processor that should be processed <b>before</b> a redelivery attempt.
     * <p/>
     * Can be used to change the {@link org.apache.camel.Exchange} <b>before</b> its being redelivered.
     *
     * @param processor the processor
     * @return the builder
     */
    public DefaultErrorHandlerBuilder onRedelivery(Processor processor) {
        setOnRedelivery(processor);
        return this;
    }

    /**
     * Sets the retry while expression.
     * <p/>
     * Will continue retrying until expression evaluates to <tt>false</tt>.
     *
     * @param retryWhile expression that determines when to stop retrying
     * @return the builder
     */
    public DefaultErrorHandlerBuilder retryWhile(Expression retryWhile) {
        setRetryWhile(ExpressionToPredicateAdapter.toPredicate(retryWhile));
        return this;
    }

    /**
     * Will use the original input {@link org.apache.camel.Message} when an {@link org.apache.camel.Exchange}
     * is moved to the dead letter queue.
     * <p/>
     * <b>Notice:</b> this only applies when all redeliveries attempt have failed and the {@link org.apache.camel.Exchange}
     * is doomed for failure.
     * <br/>
     * Instead of using the current inprogress {@link org.apache.camel.Exchange} IN message we use the original
     * IN message instead. This allows you to store the original input in the dead letter queue instead of the inprogress
     * snapshot of the IN message.
     * For instance if you route transform the IN body during routing and then failed. With the original exchange
     * store in the dead letter queue it might be easier to manually re submit the {@link org.apache.camel.Exchange}
     * again as the IN message is the same as when Camel received it.
     * So you should be able to send the {@link org.apache.camel.Exchange} to the same input.
     * <p/>
     * By default this feature is off.
     *
     * @return the builder
     */
    public DefaultErrorHandlerBuilder useOriginalMessage() {
        setUseOriginalMessage(true);
        return this;
    }

    /**
     * Whether the dead letter channel should handle (and ignore) any new exception that may been thrown during sending the
     * message to the dead letter endpoint.
     * <p/>
     * The default value is <tt>true</tt> which means any such kind of exception is handled and ignored. Set this to <tt>false</tt>
     * to let the exception be propagated back on the {@link org.apache.camel.Exchange}. This can be used in situations
     * where you use transactions, and want to use Camel's dead letter channel to deal with exceptions during routing,
     * but if the dead letter channel itself fails because of a new exception being thrown, then by setting this to <tt>false</tt>
     * the new exceptions is propagated back and set on the {@link org.apache.camel.Exchange}, which allows the transaction
     * to detect the exception, and rollback.
     *
     * @param handleNewException <tt>true</tt> to handle (and ignore), <tt>false</tt> to catch and propagated the exception on the {@link org.apache.camel.Exchange}
     * @return the builder
     */
    public DefaultErrorHandlerBuilder deadLetterHandleNewException(boolean handleNewException) {
        setDeadLetterHandleNewException(handleNewException);
        return this;
    }

    /**
     * @deprecated use {@link #deadLetterHandleNewException(boolean)}} with value <tt>false</tt>
     */
    @Deprecated
    public DefaultErrorHandlerBuilder checkException() {
        setDeadLetterHandleNewException(false);
        return this;
    }

    /**
     * Sets a custom {@link org.apache.camel.Processor} to prepare the {@link org.apache.camel.Exchange} before
     * handled by the failure processor / dead letter channel. This allows for example to enrich the message
     * before sending to a dead letter queue.
     *
     * @param processor the processor
     * @return the builder
     */
    public DefaultErrorHandlerBuilder onPrepareFailure(Processor processor) {
        setOnPrepareFailure(processor);
        return this;
    }

    /**
     * Sets a custom {@link org.apache.camel.Processor} to process the {@link org.apache.camel.Exchange} just after an exception was thrown.
     * This allows to execute the processor at the same time the exception was thrown.
     * <p/>
     * Important: Any exception thrown from this processor will be ignored.
     *
     * @param processor the processor
     * @return the builder
     */
    public DefaultErrorHandlerBuilder onExceptionOccurred(Processor processor) {
        setOnExceptionOccurred(processor);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public Processor getFailureProcessor() {
        return failureProcessor;
    }

    public void setFailureProcessor(Processor failureProcessor) {
        this.failureProcessor = failureProcessor;
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        if (redeliveryPolicy == null) {
            redeliveryPolicy = createRedeliveryPolicy();
        }
        return redeliveryPolicy;
    }

    /**
     * Sets the redelivery policy
     */
    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public CamelLogger getLogger() {
        if (logger == null) {
            logger = createLogger();
        }
        return logger;
    }

    public void setLogger(CamelLogger logger) {
        this.logger = logger;
    }

    public Processor getOnRedelivery() {
        return onRedelivery;
    }

    public void setOnRedelivery(Processor onRedelivery) {
        this.onRedelivery = onRedelivery;
    }

    public Predicate getRetryWhilePolicy(CamelContext context) {
        Predicate answer = getRetryWhile();

        if (getRetryWhileRef() != null) {
            // its a bean expression
            Language bean = context.resolveLanguage("bean");
            answer = bean.createPredicate(getRetryWhileRef());
        }

        return answer;
    }

    public Predicate getRetryWhile() {
        return retryWhile;
    }

    public void setRetryWhile(Predicate retryWhile) {
        this.retryWhile = retryWhile;
    }

    public String getRetryWhileRef() {
        return retryWhileRef;
    }

    public void setRetryWhileRef(String retryWhileRef) {
        this.retryWhileRef = retryWhileRef;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetter = null;
        this.deadLetterUri = deadLetterUri;
    }

    public Endpoint getDeadLetter() {
        return deadLetter;
    }

    public void setDeadLetter(Endpoint deadLetter) {
        this.deadLetter = deadLetter;
        this.deadLetterUri = deadLetter.getEndpointUri();
    }

    public boolean isDeadLetterHandleNewException() {
        return deadLetterHandleNewException;
    }

    public void setDeadLetterHandleNewException(boolean deadLetterHandleNewException) {
        this.deadLetterHandleNewException = deadLetterHandleNewException;
    }

    public boolean isUseOriginalMessage() {
        return useOriginalMessage;
    }

    public void setUseOriginalMessage(boolean useOriginalMessage) {
        this.useOriginalMessage = useOriginalMessage;
    }

    public boolean isAsyncDelayedRedelivery() {
        return asyncDelayedRedelivery;
    }

    public void setAsyncDelayedRedelivery(boolean asyncDelayedRedelivery) {
        this.asyncDelayedRedelivery = asyncDelayedRedelivery;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public Processor getOnPrepareFailure() {
        return onPrepareFailure;
    }

    public void setOnPrepareFailure(Processor onPrepareFailure) {
        this.onPrepareFailure = onPrepareFailure;
    }

    public Processor getOnExceptionOccurred() {
        return onExceptionOccurred;
    }

    public void setOnExceptionOccurred(Processor onExceptionOccurred) {
        this.onExceptionOccurred = onExceptionOccurred;
    }

    protected RedeliveryPolicy createRedeliveryPolicy() {
        RedeliveryPolicy policy = new RedeliveryPolicy();
        policy.disableRedelivery();
        return policy;
    }

    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(DefaultErrorHandler.class), LoggingLevel.ERROR);
    }

    protected synchronized ScheduledExecutorService getExecutorService(CamelContext camelContext) {
        if (executorService == null || executorService.isShutdown()) {
            // camel context will shutdown the executor when it shutdown so no need to shut it down when stopping
            if (executorServiceRef != null) {
                executorService = camelContext.getRegistry().lookupByNameAndType(executorServiceRef, ScheduledExecutorService.class);
                if (executorService == null) {
                    ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
                    ThreadPoolProfile profile = manager.getThreadPoolProfile(executorServiceRef);
                    executorService = manager.newScheduledThreadPool(this, executorServiceRef, profile);
                }
                if (executorService == null) {
                    throw new IllegalArgumentException("ExecutorServiceRef " + executorServiceRef + " not found in registry.");
                }
            } else {
                // no explicit configured thread pool, so leave it up to the error handler to decide if it need
                // a default thread pool from CamelContext#getErrorHandlerExecutorService
                executorService = null;
            }
        }
        return executorService;
    }

    @Override
    public String toString() {
        return "DefaultErrorHandlerBuilder";
    }

}
