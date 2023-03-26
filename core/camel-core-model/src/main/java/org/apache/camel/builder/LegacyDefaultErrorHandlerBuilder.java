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
package org.apache.camel.builder;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerConfiguration;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerProperties;
import org.apache.camel.processor.errorhandler.DefaultErrorHandler;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.slf4j.LoggerFactory;

/**
 * Legacy error handler for XML DSL in camel-spring-xml/camel-blueprint
 */
@Deprecated
public class LegacyDefaultErrorHandlerBuilder extends LegacyErrorHandlerBuilderSupport
        implements DefaultErrorHandlerProperties {

    private final DefaultErrorHandlerConfiguration configuration;

    public LegacyDefaultErrorHandlerBuilder() {
        configuration = createConfiguration();
    }

    DefaultErrorHandlerConfiguration createConfiguration() {
        return new DefaultErrorHandlerConfiguration();
    }

    @Override
    public boolean supportTransacted() {
        return false;
    }

    @Override
    public LegacyErrorHandlerBuilder cloneBuilder() {
        LegacyDefaultErrorHandlerBuilder answer = new LegacyDefaultErrorHandlerBuilder();
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(LegacyDefaultErrorHandlerBuilder other) {
        other.setLogger(configuration.getLogger());
        if (configuration.getRedeliveryPolicy() != null) {
            other.setRedeliveryPolicy(configuration.getRedeliveryPolicy().copy());
        }
        other.setOnRedelivery(configuration.getOnRedelivery());
        other.setOnRedeliveryRef(configuration.getOnRedeliveryRef());
        other.setRetryWhile(configuration.getRetryWhile());
        other.setRetryWhileRef(configuration.getRetryWhileRef());
        other.setDeadLetterUri(configuration.getDeadLetterUri());
        other.setOnPrepareFailure(configuration.getOnPrepareFailure());
        other.setOnPrepareFailureRef(configuration.getOnPrepareFailureRef());
        other.setOnExceptionOccurred(configuration.getOnExceptionOccurred());
        other.setOnExceptionOccurredRef(configuration.getOnExceptionOccurredRef());
        other.setDeadLetterHandleNewException(configuration.isDeadLetterHandleNewException());
        other.setUseOriginalMessage(configuration.isUseOriginalMessage());
        other.setUseOriginalBody(configuration.isUseOriginalBody());
        other.setAsyncDelayedRedelivery(configuration.isAsyncDelayedRedelivery());
        other.setExecutorServiceRef(configuration.getExecutorServiceRef());
    }

    // Builder methods
    // -------------------------------------------------------------------------
    public LegacyDefaultErrorHandlerBuilder backOffMultiplier(double backOffMultiplier) {
        getRedeliveryPolicy().backOffMultiplier(backOffMultiplier);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder collisionAvoidancePercent(double collisionAvoidancePercent) {
        getRedeliveryPolicy().collisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder redeliveryDelay(long delay) {
        getRedeliveryPolicy().redeliveryDelay(delay);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder delayPattern(String delayPattern) {
        getRedeliveryPolicy().delayPattern(delayPattern);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder maximumRedeliveries(int maximumRedeliveries) {
        getRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder disableRedelivery() {
        getRedeliveryPolicy().maximumRedeliveries(0);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        getRedeliveryPolicy().maximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder useCollisionAvoidance() {
        getRedeliveryPolicy().useCollisionAvoidance();
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder useExponentialBackOff() {
        getRedeliveryPolicy().useExponentialBackOff();
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        getRedeliveryPolicy().setRetriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        getRedeliveryPolicy().setRetryAttemptedLogLevel(retryAttemptedLogLevel);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder retryAttemptedLogInterval(int retryAttemptedLogInterval) {
        getRedeliveryPolicy().setRetryAttemptedLogInterval(retryAttemptedLogInterval);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder logStackTrace(boolean logStackTrace) {
        getRedeliveryPolicy().setLogStackTrace(logStackTrace);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder logRetryStackTrace(boolean logRetryStackTrace) {
        getRedeliveryPolicy().setLogRetryStackTrace(logRetryStackTrace);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder logHandled(boolean logHandled) {
        getRedeliveryPolicy().setLogHandled(logHandled);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder logNewException(boolean logNewException) {
        getRedeliveryPolicy().setLogNewException(logNewException);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder logExhausted(boolean logExhausted) {
        getRedeliveryPolicy().setLogExhausted(logExhausted);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder logRetryAttempted(boolean logRetryAttempted) {
        getRedeliveryPolicy().setLogRetryAttempted(logRetryAttempted);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder logExhaustedMessageHistory(boolean logExhaustedMessageHistory) {
        getRedeliveryPolicy().setLogExhaustedMessageHistory(logExhaustedMessageHistory);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder logExhaustedMessageBody(boolean logExhaustedMessageBody) {
        getRedeliveryPolicy().setLogExhaustedMessageBody(logExhaustedMessageBody);
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder exchangeFormatterRef(String exchangeFormatterRef) {
        getRedeliveryPolicy().setExchangeFormatterRef(exchangeFormatterRef);
        return this;
    }

    /**
     * Will allow asynchronous delayed redeliveries. The route, in particular the consumer's component, must support the
     * Asynchronous Routing Engine (e.g. seda)
     *
     * @see    RedeliveryPolicy#setAsyncDelayedRedelivery(boolean)
     * @return the builder
     */
    public LegacyDefaultErrorHandlerBuilder asyncDelayedRedelivery() {
        getRedeliveryPolicy().setAsyncDelayedRedelivery(true);
        return this;
    }

    /**
     * Controls whether to allow redelivery while stopping/shutting down a route that uses error handling.
     *
     * @param  allowRedeliveryWhileStopping <tt>true</tt> to allow redelivery, <tt>false</tt> to reject redeliveries
     * @return                              the builder
     */
    public LegacyDefaultErrorHandlerBuilder allowRedeliveryWhileStopping(boolean allowRedeliveryWhileStopping) {
        getRedeliveryPolicy().setAllowRedeliveryWhileStopping(allowRedeliveryWhileStopping);
        return this;
    }

    /**
     * Sets the thread pool to be used for redelivery.
     *
     * @param  executorService the scheduled thread pool to use
     * @return                 the builder.
     */
    public LegacyDefaultErrorHandlerBuilder executorService(ScheduledExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Sets a reference to a thread pool to be used for redelivery.
     *
     * @param  ref reference to a scheduled thread pool
     * @return     the builder.
     */
    public LegacyDefaultErrorHandlerBuilder executorServiceRef(String ref) {
        setExecutorServiceRef(ref);
        return this;
    }

    /**
     * Sets the logger used for caught exceptions
     *
     * @param  logger the logger
     * @return        the builder
     */
    public LegacyDefaultErrorHandlerBuilder logger(CamelLogger logger) {
        setLogger(logger);
        return this;
    }

    /**
     * Sets the logging level of exceptions caught
     *
     * @param  level the logging level
     * @return       the builder
     */
    public LegacyDefaultErrorHandlerBuilder loggingLevel(LoggingLevel level) {
        getLogger().setLevel(level);
        return this;
    }

    /**
     * Sets the log used for caught exceptions
     *
     * @param  log the logger
     * @return     the builder
     */
    public LegacyDefaultErrorHandlerBuilder log(org.slf4j.Logger log) {
        getLogger().setLog(log);
        return this;
    }

    /**
     * Sets the log used for caught exceptions
     *
     * @param  log the log name
     * @return     the builder
     */
    public LegacyDefaultErrorHandlerBuilder log(String log) {
        return log(LoggerFactory.getLogger(log));
    }

    /**
     * Sets the log used for caught exceptions
     *
     * @param  log the log class
     * @return     the builder
     */
    public LegacyDefaultErrorHandlerBuilder log(Class<?> log) {
        return log(LoggerFactory.getLogger(log));
    }

    /**
     * Sets a processor that should be processed <b>before</b> a redelivery attempt.
     * <p/>
     * Can be used to change the {@link org.apache.camel.Exchange} <b>before</b> its being redelivered.
     *
     * @param  processor the processor
     * @return           the builder
     */
    public LegacyDefaultErrorHandlerBuilder onRedelivery(Processor processor) {
        setOnRedelivery(processor);
        return this;
    }

    /**
     * Sets a reference for the processor to use <b>before</b> a redelivery attempt.
     *
     * @param  onRedeliveryRef the processor's reference
     * @return                 the builder
     * @see                    #onRedelivery(Processor)
     */
    public LegacyDefaultErrorHandlerBuilder onRedeliveryRef(String onRedeliveryRef) {
        setOnRedeliveryRef(onRedeliveryRef);
        return this;
    }

    /**
     * Sets the retry while expression.
     * <p/>
     * Will continue retrying until expression evaluates to <tt>false</tt>.
     *
     * @param  retryWhile expression that determines when to stop retrying
     * @return            the builder
     */
    public LegacyDefaultErrorHandlerBuilder retryWhile(Expression retryWhile) {
        setRetryWhile(ExpressionToPredicateAdapter.toPredicate(retryWhile));
        return this;
    }

    public LegacyDefaultErrorHandlerBuilder retryWhileRef(String retryWhileRef) {
        setRetryWhileRef(retryWhileRef);
        return this;
    }

    /**
     * Will use the original input {@link org.apache.camel.Message} (original body and headers) when an
     * {@link org.apache.camel.Exchange} is moved to the dead letter queue.
     * <p/>
     * <b>Notice:</b> this only applies when all redeliveries attempt have failed and the
     * {@link org.apache.camel.Exchange} is doomed for failure. <br/>
     * Instead of using the current inprogress {@link org.apache.camel.Exchange} IN message we use the original IN
     * message instead. This allows you to store the original input in the dead letter queue instead of the inprogress
     * snapshot of the IN message. For instance if you route transform the IN body during routing and then failed. With
     * the original exchange store in the dead letter queue it might be easier to manually re submit the
     * {@link org.apache.camel.Exchange} again as the IN message is the same as when Camel received it. So you should be
     * able to send the {@link org.apache.camel.Exchange} to the same input.
     * <p/>
     * The difference between useOriginalMessage and useOriginalBody is that the former includes both the original body
     * and headers, where as the latter only includes the original body. You can use the latter to enrich the message
     * with custom headers and include the original message body. The former wont let you do this, as its using the
     * original message body and headers as they are. You cannot enable both useOriginalMessage and useOriginalBody.
     * <p/>
     * The original input message is defensively copied, and the copied message body is converted to
     * {@link org.apache.camel.StreamCache} if possible, to ensure the body can be read when the original message is
     * being used later. If the body is not converted to {@link org.apache.camel.StreamCache} then the body will not be
     * able to re-read when accessed later.
     * <p/>
     * <b>Important:</b> The original input means the input message that are bounded by the current
     * {@link org.apache.camel.spi.UnitOfWork}. An unit of work typically spans one route, or multiple routes if they
     * are connected using internal endpoints such as direct or seda. When messages is passed via external endpoints
     * such as JMS or HTTP then the consumer will create a new unit of work, with the message it received as input as
     * the original input. Also some EIP patterns such as splitter, multicast, will create a new unit of work boundary
     * for the messages in their sub-route (eg the split message); however these EIPs have an option named
     * <tt>shareUnitOfWork</tt> which allows to combine with the parent unit of work in regard to error handling and
     * therefore use the parent original message.
     * <p/>
     * By default this feature is off.
     *
     * @return the builder
     * @see    #useOriginalBody()
     */
    public LegacyDefaultErrorHandlerBuilder useOriginalMessage() {
        setUseOriginalMessage(true);
        return this;
    }

    /**
     * Will use the original input {@link org.apache.camel.Message} body (original body only) when an
     * {@link org.apache.camel.Exchange} is moved to the dead letter queue.
     * <p/>
     * <b>Notice:</b> this only applies when all redeliveries attempt have failed and the
     * {@link org.apache.camel.Exchange} is doomed for failure. <br/>
     * Instead of using the current inprogress {@link org.apache.camel.Exchange} IN message we use the original IN
     * message instead. This allows you to store the original input in the dead letter queue instead of the inprogress
     * snapshot of the IN message. For instance if you route transform the IN body during routing and then failed. With
     * the original exchange store in the dead letter queue it might be easier to manually re submit the
     * {@link org.apache.camel.Exchange} again as the IN message is the same as when Camel received it. So you should be
     * able to send the {@link org.apache.camel.Exchange} to the same input.
     * <p/>
     * The difference between useOriginalMessage and useOriginalBody is that the former includes both the original body
     * and headers, where as the latter only includes the original body. You can use the latter to enrich the message
     * with custom headers and include the original message body. The former wont let you do this, as its using the
     * original message body and headers as they are. You cannot enable both useOriginalMessage and useOriginalBody.
     * <p/>
     * The original input message is defensively copied, and the copied message body is converted to
     * {@link org.apache.camel.StreamCache} if possible, to ensure the body can be read when the original message is
     * being used later. If the body is not converted to {@link org.apache.camel.StreamCache} then the body will not be
     * able to re-read when accessed later.
     * <p/>
     * <b>Important:</b> The original input means the input message that are bounded by the current
     * {@link org.apache.camel.spi.UnitOfWork}. An unit of work typically spans one route, or multiple routes if they
     * are connected using internal endpoints such as direct or seda. When messages is passed via external endpoints
     * such as JMS or HTTP then the consumer will create a new unit of work, with the message it received as input as
     * the original input. Also some EIP patterns such as splitter, multicast, will create a new unit of work boundary
     * for the messages in their sub-route (eg the split message); however these EIPs have an option named
     * <tt>shareUnitOfWork</tt> which allows to combine with the parent unit of work in regard to error handling and
     * therefore use the parent original message.
     * <p/>
     * By default this feature is off.
     *
     * @return the builder
     * @see    #useOriginalMessage()
     */
    public LegacyDefaultErrorHandlerBuilder useOriginalBody() {
        setUseOriginalBody(true);
        return this;
    }

    /**
     * Whether the dead letter channel should handle (and ignore) any new exception that may been thrown during sending
     * the message to the dead letter endpoint.
     * <p/>
     * The default value is <tt>true</tt> which means any such kind of exception is handled and ignored. Set this to
     * <tt>false</tt> to let the exception be propagated back on the {@link org.apache.camel.Exchange}. This can be used
     * in situations where you use transactions, and want to use Camel's dead letter channel to deal with exceptions
     * during routing, but if the dead letter channel itself fails because of a new exception being thrown, then by
     * setting this to <tt>false</tt> the new exceptions is propagated back and set on the
     * {@link org.apache.camel.Exchange}, which allows the transaction to detect the exception, and rollback.
     *
     * @param  handleNewException <tt>true</tt> to handle (and ignore), <tt>false</tt> to catch and propagated the
     *                            exception on the {@link org.apache.camel.Exchange}
     * @return                    the builder
     */
    public LegacyDefaultErrorHandlerBuilder deadLetterHandleNewException(boolean handleNewException) {
        setDeadLetterHandleNewException(handleNewException);
        return this;
    }

    /**
     * Sets a custom {@link Processor} to prepare the {@link org.apache.camel.Exchange} before handled by the failure
     * processor / dead letter channel. This allows for example to enrich the message before sending to a dead letter
     * queue.
     *
     * @param  processor the processor
     * @return           the builder
     */
    public LegacyDefaultErrorHandlerBuilder onPrepareFailure(Processor processor) {
        setOnPrepareFailure(processor);
        return this;
    }

    /**
     * Sets a reference for the processor to use before handled by the failure processor.
     *
     * @param  onPrepareFailureRef the processor's reference
     * @return                     the builder
     * @see                        #onPrepareFailure(Processor)
     */
    public LegacyDefaultErrorHandlerBuilder onPrepareFailureRef(String onPrepareFailureRef) {
        setOnPrepareFailureRef(onPrepareFailureRef);
        return this;
    }

    /**
     * Sets a custom {@link Processor} to process the {@link org.apache.camel.Exchange} just after an exception was
     * thrown. This allows to execute the processor at the same time the exception was thrown.
     * <p/>
     * Important: Any exception thrown from this processor will be ignored.
     *
     * @param  processor the processor
     * @return           the builder
     */
    public LegacyDefaultErrorHandlerBuilder onExceptionOccurred(Processor processor) {
        setOnExceptionOccurred(processor);
        return this;
    }

    /**
     * Sets a reference for the processor to use just after an exception was thrown.
     *
     * @param  onExceptionOccurredRef the processor's reference
     * @return                        the builder
     * @see                           #onExceptionOccurred(Processor)
     */
    public LegacyDefaultErrorHandlerBuilder onExceptionOccurredRef(String onExceptionOccurredRef) {
        setOnExceptionOccurredRef(onExceptionOccurredRef);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public boolean hasRedeliveryPolicy() {
        return configuration.getRedeliveryPolicy() != null;
    }

    @Override
    public RedeliveryPolicy getDefaultRedeliveryPolicy() {
        return RedeliveryPolicy.DEFAULT_POLICY;
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        if (configuration.getRedeliveryPolicy() == null) {
            configuration.setRedeliveryPolicy(createRedeliveryPolicy());
        }
        return configuration.getRedeliveryPolicy();
    }

    /**
     * Sets the redelivery policy
     */
    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        configuration.setRedeliveryPolicy(redeliveryPolicy);
    }

    @Override
    public boolean hasLogger() {
        return configuration.hasLogger();
    }

    public CamelLogger getLogger() {
        if (configuration.getLogger() == null) {
            configuration.setLogger(createLogger());
        }
        return configuration.getLogger();
    }

    public void setLogger(CamelLogger logger) {
        configuration.setLogger(logger);
    }

    public Processor getOnRedelivery() {
        return configuration.getOnRedelivery();
    }

    public void setOnRedelivery(Processor onRedelivery) {
        configuration.setOnRedelivery(onRedelivery);
    }

    public String getOnRedeliveryRef() {
        return configuration.getOnRedeliveryRef();
    }

    public void setOnRedeliveryRef(String onRedeliveryRef) {
        configuration.setOnRedeliveryRef(onRedeliveryRef);
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
        return configuration.getRetryWhile();
    }

    public void setRetryWhile(Predicate retryWhile) {
        configuration.setRetryWhile(retryWhile);
    }

    public String getRetryWhileRef() {
        return configuration.getRetryWhileRef();
    }

    public void setRetryWhileRef(String retryWhileRef) {
        configuration.setRetryWhileRef(retryWhileRef);
    }

    public String getDeadLetterUri() {
        return configuration.getDeadLetterUri();
    }

    public void setDeadLetterUri(String deadLetterUri) {
        configuration.setDeadLetterUri(deadLetterUri);
    }

    public boolean isDeadLetterHandleNewException() {
        return configuration.isDeadLetterHandleNewException();
    }

    public void setDeadLetterHandleNewException(boolean deadLetterHandleNewException) {
        configuration.setDeadLetterHandleNewException(deadLetterHandleNewException);
    }

    public boolean isUseOriginalMessage() {
        return configuration.isUseOriginalMessage();
    }

    public void setUseOriginalMessage(boolean useOriginalMessage) {
        configuration.setUseOriginalMessage(useOriginalMessage);
    }

    public boolean isUseOriginalBody() {
        return configuration.isUseOriginalBody();
    }

    public void setUseOriginalBody(boolean useOriginalBody) {
        configuration.setUseOriginalBody(useOriginalBody);
    }

    public boolean isAsyncDelayedRedelivery() {
        return configuration.isAsyncDelayedRedelivery();
    }

    public void setAsyncDelayedRedelivery(boolean asyncDelayedRedelivery) {
        configuration.setAsyncDelayedRedelivery(asyncDelayedRedelivery);
    }

    public ScheduledExecutorService getExecutorService() {
        return configuration.getExecutorService();
    }

    public void setExecutorService(ScheduledExecutorService executorService) {
        configuration.setExecutorService(executorService);
    }

    public String getExecutorServiceRef() {
        return configuration.getExecutorServiceRef();
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        configuration.setExecutorServiceRef(executorServiceRef);
    }

    public Processor getOnPrepareFailure() {
        return configuration.getOnPrepareFailure();
    }

    public void setOnPrepareFailure(Processor onPrepareFailure) {
        configuration.setOnPrepareFailure(onPrepareFailure);
    }

    public String getOnPrepareFailureRef() {
        return configuration.getOnPrepareFailureRef();
    }

    public void setOnPrepareFailureRef(String onPrepareFailureRef) {
        configuration.setOnPrepareFailureRef(onPrepareFailureRef);
    }

    public Processor getOnExceptionOccurred() {
        return configuration.getOnExceptionOccurred();
    }

    public void setOnExceptionOccurred(Processor onExceptionOccurred) {
        configuration.setOnExceptionOccurred(onExceptionOccurred);
    }

    public String getOnExceptionOccurredRef() {
        return configuration.getOnExceptionOccurredRef();
    }

    public void setOnExceptionOccurredRef(String onExceptionOccurredRef) {
        configuration.setOnExceptionOccurredRef(onExceptionOccurredRef);
    }

    protected RedeliveryPolicy createRedeliveryPolicy() {
        RedeliveryPolicy policy = new RedeliveryPolicy();
        policy.disableRedelivery();
        return policy;
    }

    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(DefaultErrorHandler.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "DefaultErrorHandlerBuilder";
    }

}
