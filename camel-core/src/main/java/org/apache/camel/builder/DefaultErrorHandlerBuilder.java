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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.processor.DefaultErrorHandler;
import org.apache.camel.processor.ErrorHandlerSupport;
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.builder.PredicateBuilder.toPredicate;

/**
 * The default error handler builder.
 *
 * @version $Revision$
 */
public class DefaultErrorHandlerBuilder extends ErrorHandlerBuilderSupport {

    protected Logger logger;
    protected ExceptionPolicyStrategy exceptionPolicyStrategy = ErrorHandlerSupport.createDefaultExceptionPolicyStrategy();
    protected RedeliveryPolicy redeliveryPolicy;
    protected Processor onRedelivery;
    protected Predicate handledPolicy;
    protected Predicate retryWhile;
    protected String retryWhileRef;
    protected Processor failureProcessor;
    protected Endpoint deadLetter;
    protected String deadLetterUri;
    protected boolean useOriginalMessage;
    protected boolean asyncDelayedRedelivery;

    public DefaultErrorHandlerBuilder() {
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        DefaultErrorHandler answer = new DefaultErrorHandler(routeContext.getCamelContext(), processor, getLogger(),
                getOnRedelivery(), getRedeliveryPolicy(), getHandledPolicy(), getExceptionPolicyStrategy(),
                getRetryWhilePolicy(routeContext.getCamelContext()));
        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

    public boolean supportTransacted() {
        return false;
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

    public DefaultErrorHandlerBuilder logExhausted(boolean logExhausted) {
        getRedeliveryPolicy().setLogExhausted(logExhausted);
        return this;
    }

    /**
     * Will allow asynchronous delayed redeliveries.
     *
     * @see org.apache.camel.processor.RedeliveryPolicy#setAsyncDelayedRedelivery(boolean)
     * @return the builder
     */
    public DefaultErrorHandlerBuilder asyncDelayedRedelivery() {
        getRedeliveryPolicy().setAsyncDelayedRedelivery(true);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  handled or not
     * @return the builder
     */
    @Deprecated
    public DefaultErrorHandlerBuilder handled(boolean handled) {
        Expression expression = ExpressionBuilder.constantExpression(Boolean.toString(handled));
        return handled(expression);
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  predicate that determines true or false
     * @return the builder
     */
    @Deprecated
    public DefaultErrorHandlerBuilder handled(Predicate handled) {
        this.setHandledPolicy(handled);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  expression that determines true or false
     * @return the builder
     */
    @Deprecated
    public DefaultErrorHandlerBuilder handled(Expression handled) {
        this.setHandledPolicy(toPredicate(handled));
        return this;
    }

    /**
     * Sets the logger used for caught exceptions
     *
     * @param logger the logger
     * @return the builder
     */
    public DefaultErrorHandlerBuilder logger(Logger logger) {
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
    public DefaultErrorHandlerBuilder log(Log log) {
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
        return log(LogFactory.getLog(log));
    }

    /**
     * Sets the log used for caught exceptions
     *
     * @param log the log class
     * @return the builder
     */
    public DefaultErrorHandlerBuilder log(Class<?> log) {
        return log(LogFactory.getLog(log));
    }

    /**
     * Sets the exception policy to use
     *
     * @return the builder
     */
    public DefaultErrorHandlerBuilder exceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        setExceptionPolicyStrategy(exceptionPolicyStrategy);
        return this;
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
        setRetryWhile(toPredicate(retryWhile));
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

    public Logger getLogger() {
        if (logger == null) {
            logger = createLogger();
        }
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Sets the exception policy strategy to use for resolving the {@link org.apache.camel.model.OnExceptionDefinition}
     * to use for a given thrown exception
     */
    public ExceptionPolicyStrategy getExceptionPolicyStrategy() {
        return exceptionPolicyStrategy;
    }

    public void setExceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        this.exceptionPolicyStrategy = exceptionPolicyStrategy;
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

    @Deprecated
    public Predicate getHandledPolicy() {
        if (handledPolicy == null) {
            handledPolicy = createHandledPolicy();
        }
        return handledPolicy;
    }

    @Deprecated
    public void setHandledPolicy(Predicate handled) {
        this.handledPolicy = handled;
    }

    /**
     * Sets the handled using a boolean and thus easier to use for Spring XML configuration as well
     */
    @Deprecated
    public void setHandled(boolean handled) {
        handled(handled);
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

    protected Predicate createHandledPolicy() {
        // should NOT be handled by default for default error handler
        return PredicateBuilder.toPredicate(ExpressionBuilder.constantExpression(false));
    }

    protected RedeliveryPolicy createRedeliveryPolicy() {
        RedeliveryPolicy policy = new RedeliveryPolicy();
        policy.disableRedelivery();
        policy.setRedeliveryDelay(0);
        return policy;
    }

    protected Logger createLogger() {
        return new Logger(LogFactory.getLog(DefaultErrorHandler.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "DefaultErrorHandlerBuilder";
    }

}