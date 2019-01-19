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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.util.ObjectHelper;

/**
 * Route to be executed when an exception is thrown
 */
@Metadata(label = "error")
@XmlRootElement(name = "onException")
@XmlAccessorType(XmlAccessType.FIELD)
public class OnExceptionDefinition extends ProcessorDefinition<OnExceptionDefinition> {
    @XmlElement(name = "exception", required = true)
    private List<String> exceptions = new ArrayList<>();
    @XmlElement(name = "onWhen") @AsPredicate
    private WhenDefinition onWhen;
    @XmlElement(name = "retryWhile") @AsPredicate
    private ExpressionSubElementDefinition retryWhile;
    @XmlElement(name = "redeliveryPolicy")
    private RedeliveryPolicyDefinition redeliveryPolicyType;
    @XmlAttribute(name = "redeliveryPolicyRef")
    private String redeliveryPolicyRef;
    @XmlElement(name = "handled") @AsPredicate
    private ExpressionSubElementDefinition handled;
    @XmlElement(name = "continued") @AsPredicate
    private ExpressionSubElementDefinition continued;
    @XmlAttribute(name = "onRedeliveryRef")
    private String onRedeliveryRef;
    @XmlAttribute(name = "onExceptionOccurredRef")
    private String onExceptionOccurredRef;
    @XmlAttribute(name = "useOriginalMessage")
    private Boolean useOriginalMessagePolicy;
    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<>();
    @XmlTransient
    private List<Class<? extends Throwable>> exceptionClasses;
    @XmlTransient
    private Predicate handledPolicy;
    @XmlTransient
    private Predicate continuedPolicy;
    @XmlTransient
    private Predicate retryWhilePolicy;
    @XmlTransient
    private Processor onRedelivery;
    @XmlTransient
    private Processor onExceptionOccurred;
    @XmlTransient
    private Boolean routeScoped;
    // TODO: in Camel 3.0 the OnExceptionDefinition should not contain state and ErrorHandler processors
    @XmlTransient
    private final Map<String, Processor> errorHandlers = new HashMap<>();

    public OnExceptionDefinition() {
    }

    public OnExceptionDefinition(List<Class<? extends Throwable>> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }

    public OnExceptionDefinition(Class<? extends Throwable> exceptionType) {
        exceptionClasses = new ArrayList<>();
        exceptionClasses.add(exceptionType);
    }

    public void setRouteScoped(boolean routeScoped) {
        this.routeScoped = routeScoped;
    }

    public boolean isRouteScoped() {
        // is context scoped by default
        return routeScoped != null ? routeScoped : false;
    }

    public Boolean getRouteScoped() {
        return routeScoped;
    }

    @Override
    public String toString() {
        return "OnException[" + description() + " -> " + getOutputs() + "]";
    }
    
    protected String description() {
        return getExceptionClasses() + (onWhen != null ? " " + onWhen : "");
    }

    @Override
    public String getShortName() {
        return "onException";
    }

    @Override
    public String getLabel() {
        return "onException[" + description() + "]";
    }
    
    @Override
    public boolean isAbstract() {
        return true;
    }

    @Override
    public boolean isTopLevelOnly() {
        return true;
    }

    public void validateConfiguration() {
        if (isInheritErrorHandler() != null && isInheritErrorHandler()) {
            throw new IllegalArgumentException(this + " cannot have the inheritErrorHandler option set to true");
        }

        List<Class<? extends Throwable>> exceptions = getExceptionClasses();
        if (exceptions == null || exceptions.isEmpty()) {
            throw new IllegalArgumentException("At least one exception must be configured on " + this);
        }

        // only one of handled or continued is allowed
        if (getHandledPolicy() != null && getContinuedPolicy() != null) {
            throw new IllegalArgumentException("Only one of handled or continued is allowed to be configured on: " + this);
        }

        // validate that at least some option is set as you cannot just have onException(Exception.class);
        if (outputs == null || getOutputs().isEmpty()) {
            // no outputs so there should be some sort of configuration
            ObjectHelper.firstNotNull(
                    handledPolicy,
                    continuedPolicy,
                    retryWhilePolicy,
                    redeliveryPolicyType,
                    useOriginalMessagePolicy,
                    onRedeliveryRef,
                    onRedelivery,
                    onExceptionOccurred)
                .orElseThrow(() -> new IllegalArgumentException(this + " is not configured."));
        }
    }

    // Fluent API
    //-------------------------------------------------------------------------

    @Override
    public OnExceptionDefinition onException(Class<? extends Throwable> exceptionType) {
        getExceptionClasses().add(exceptionType);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled handled or not
     * @return the builder
     */
    public OnExceptionDefinition handled(boolean handled) {
        Expression expression = ExpressionBuilder.constantExpression(Boolean.toString(handled));
        return handled(expression);
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled predicate that determines true or false
     * @return the builder
     */
    public OnExceptionDefinition handled(@AsPredicate Predicate handled) {
        setHandledPolicy(handled);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled expression that determines true or false
     * @return the builder
     */
    public OnExceptionDefinition handled(@AsPredicate Expression handled) {
        setHandledPolicy(ExpressionToPredicateAdapter.toPredicate(handled));
        return this;
    }

    /**
     * Sets whether the exchange should handle and continue routing from the point of failure.
     * <p/>
     * If this option is enabled then its considered handled as well.
     *
     * @param continued continued or not
     * @return the builder
     */
    public OnExceptionDefinition continued(boolean continued) {
        Expression expression = ExpressionBuilder.constantExpression(Boolean.toString(continued));
        return continued(expression);
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     * <p/>
     * If this option is enabled then its considered handled as well.
     *
     * @param continued predicate that determines true or false
     * @return the builder
     */
    public OnExceptionDefinition continued(@AsPredicate Predicate continued) {
        setContinuedPolicy(continued);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     * <p/>
     * If this option is enabled then its considered handled as well.
     *
     * @param continued expression that determines true or false
     * @return the builder
     */
    public OnExceptionDefinition continued(@AsPredicate Expression continued) {
        setContinuedPolicy(ExpressionToPredicateAdapter.toPredicate(continued));
        return this;
    }

    /**
     * Sets an additional predicate that should be true before the onException is triggered.
     * <p/>
     * To be used for fine grained controlling whether a thrown exception should be intercepted
     * by this exception type or not.
     *
     * @param predicate predicate that determines true or false
     * @return the builder
     */
    public OnExceptionDefinition onWhen(@AsPredicate Predicate predicate) {
        setOnWhen(new WhenDefinition(predicate));
        return this;
    }

    /**
     * Sets the retry while predicate.
     * <p/>
     * Will continue retrying until predicate returns <tt>false</tt>.
     *
     * @param retryWhile predicate that determines when to stop retrying
     * @return the builder
     */
    public OnExceptionDefinition retryWhile(@AsPredicate Predicate retryWhile) {
        setRetryWhilePolicy(retryWhile);
        return this;
    }

    /**
     * Sets the back off multiplier
     *
     * @param backOffMultiplier the back off multiplier
     * @return the builder
     */
    public OnExceptionDefinition backOffMultiplier(double backOffMultiplier) {
        getOrCreateRedeliveryPolicy().useExponentialBackOff();
        getOrCreateRedeliveryPolicy().backOffMultiplier(backOffMultiplier);
        return this;
    }

    /**
     * Sets the back off multiplier (supports property placeholders)
     *
     * @param backOffMultiplier the back off multiplier
     * @return the builder
     */
    public OnExceptionDefinition backOffMultiplier(String backOffMultiplier) {
        getOrCreateRedeliveryPolicy().useExponentialBackOff();
        getOrCreateRedeliveryPolicy().backOffMultiplier(backOffMultiplier);
        return this;
    }

    /**
     * Sets the collision avoidance factor
     *
     * @param collisionAvoidanceFactor the factor
     * @return the builder
     */
    public OnExceptionDefinition collisionAvoidanceFactor(double collisionAvoidanceFactor) {
        getOrCreateRedeliveryPolicy().useCollisionAvoidance();
        getOrCreateRedeliveryPolicy().collisionAvoidanceFactor(collisionAvoidanceFactor);
        return this;
    }

    /**
     * Sets the collision avoidance factor (supports property placeholders)
     *
     * @param collisionAvoidanceFactor the factor
     * @return the builder
     */
    public OnExceptionDefinition collisionAvoidanceFactor(String collisionAvoidanceFactor) {
        getOrCreateRedeliveryPolicy().useCollisionAvoidance();
        getOrCreateRedeliveryPolicy().collisionAvoidanceFactor(collisionAvoidanceFactor);
        return this;
    }

    /**
     * Sets the collision avoidance percentage
     *
     * @param collisionAvoidancePercent the percentage
     * @return the builder
     */
    public OnExceptionDefinition collisionAvoidancePercent(double collisionAvoidancePercent) {
        getOrCreateRedeliveryPolicy().useCollisionAvoidance();
        getOrCreateRedeliveryPolicy().collisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    /**
     * Sets the initial redelivery delay
     *
     * @param delay delay in millis
     * @return the builder
     */
    public OnExceptionDefinition redeliveryDelay(long delay) {
        getOrCreateRedeliveryPolicy().redeliveryDelay(delay);
        return this;
    }

    /**
     * Sets the initial redelivery delay (supports property placeholders)
     *
     * @param delay delay in millis
     * @return the builder
     */
    public OnExceptionDefinition redeliveryDelay(String delay) {
        getOrCreateRedeliveryPolicy().redeliveryDelay(delay);
        return this;
    }

    /**
     * Allow synchronous delayed redelivery.
     *
     * @see org.apache.camel.processor.RedeliveryPolicy#setAsyncDelayedRedelivery(boolean)
     * @return the builder
     */
    public OnExceptionDefinition asyncDelayedRedelivery() {
        getOrCreateRedeliveryPolicy().asyncDelayedRedelivery();
        return this;
    }

    /**
     * Sets the logging level to use when retries has exhausted
     *
     * @param retriesExhaustedLogLevel the logging level
     * @return the builder
     */
    public OnExceptionDefinition retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        getOrCreateRedeliveryPolicy().retriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }

    /**
     * Sets the logging level to use for logging retry attempts
     *
     * @param retryAttemptedLogLevel the logging level
     * @return the builder
     */
    public OnExceptionDefinition retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        getOrCreateRedeliveryPolicy().retryAttemptedLogLevel(retryAttemptedLogLevel);
        return this;
    }

    /**
     * Sets whether to log stacktrace for failed messages.
     */
    public OnExceptionDefinition logStackTrace(boolean logStackTrace) {
        getOrCreateRedeliveryPolicy().logStackTrace(logStackTrace);
        return this;
    }

    /**
     * Sets whether to log stacktrace for failed messages (supports property placeholders)
     */
    public OnExceptionDefinition logStackTrace(String logStackTrace) {
        getOrCreateRedeliveryPolicy().logStackTrace(logStackTrace);
        return this;
    }

    /**
     * Sets whether to log stacktrace for failed redelivery attempts
     */
    public OnExceptionDefinition logRetryStackTrace(boolean logRetryStackTrace) {
        getOrCreateRedeliveryPolicy().logRetryStackTrace(logRetryStackTrace);
        return this;
    }

    /**
     * Sets whether to log stacktrace for failed redelivery attempts (supports property placeholders)
     */
    public OnExceptionDefinition logRetryStackTrace(String logRetryStackTrace) {
        getOrCreateRedeliveryPolicy().logRetryStackTrace(logRetryStackTrace);
        return this;
    }

    /**
     * Sets whether to log errors even if its handled
     */
    public OnExceptionDefinition logHandled(boolean logHandled) {
        getOrCreateRedeliveryPolicy().logHandled(logHandled);
        return this;
    }

    /**
     * Sets whether to log errors even if its handled (supports property placeholders)
     */
    public OnExceptionDefinition logHandled(String logHandled) {
        getOrCreateRedeliveryPolicy().logHandled(logHandled);
        return this;
    }

    /**
     * Sets whether new exceptions should be logged or not (supports property placeholders).
     * Can be used to include or reduce verbose.
     * <p/>
     * A new exception is an exception that was thrown while handling a previous exception.
     */
    public OnExceptionDefinition logNewException(boolean logNewException) {
        getOrCreateRedeliveryPolicy().logNewException(logNewException);
        return this;
    }

    /**
     * Sets whether new exceptions should be logged or not (supports property placeholders).
     * Can be used to include or reduce verbose.
     * <p/>
     * A new exception is an exception that was thrown while handling a previous exception.
     */
    public OnExceptionDefinition logNewException(String logNewException) {
        getOrCreateRedeliveryPolicy().logNewException(logNewException);
        return this;
    }

    /**
     * Sets whether to log errors even if its continued
     */
    public OnExceptionDefinition logContinued(boolean logContinued) {
        getOrCreateRedeliveryPolicy().logContinued(logContinued);
        return this;
    }

    /**
     * Sets whether to log errors even if its continued (supports property placeholders)
     */
    public OnExceptionDefinition logContinued(String logContinued) {
        getOrCreateRedeliveryPolicy().logContinued(logContinued);
        return this;
    }

    /**
     * Sets whether to log retry attempts
     */
    public OnExceptionDefinition logRetryAttempted(boolean logRetryAttempted) {
        getOrCreateRedeliveryPolicy().logRetryAttempted(logRetryAttempted);
        return this;
    }

    /**
     * Sets whether to log retry attempts (supports property placeholders)
     */
    public OnExceptionDefinition logRetryAttempted(String logRetryAttempted) {
        getOrCreateRedeliveryPolicy().logRetryAttempted(logRetryAttempted);
        return this;
    }

    /**
     * Sets whether to log exhausted exceptions
     */
    public OnExceptionDefinition logExhausted(boolean logExhausted) {
        getOrCreateRedeliveryPolicy().logExhausted(logExhausted);
        return this;
    }

    /**
     * Sets whether to log exhausted exceptions (supports property placeholders)
     */
    public OnExceptionDefinition logExhausted(String logExhausted) {
        getOrCreateRedeliveryPolicy().logExhausted(logExhausted);
        return this;
    }

    /**
     * Sets whether to log exhausted exceptions with message history
     */
    public OnExceptionDefinition logExhaustedMessageHistory(boolean logExhaustedMessageHistory) {
        getOrCreateRedeliveryPolicy().logExhaustedMessageHistory(logExhaustedMessageHistory);
        return this;
    }

    /**
     * Sets whether to log exhausted exceptions with message history
     */
    public OnExceptionDefinition logExhaustedMessageHistory(String logExhaustedMessageHistory) {
        getOrCreateRedeliveryPolicy().logExhaustedMessageHistory(logExhaustedMessageHistory);
        return this;
    }

    /**
     * Sets whether to log exhausted message body with message history.
     * Requires <tt>logExhaustedMessageHistory</tt> to be enabled.
     */
    public OnExceptionDefinition logExhaustedMessageBody(boolean logExhaustedMessageBody) {
        getOrCreateRedeliveryPolicy().logExhaustedMessageBody(logExhaustedMessageBody);
        return this;
    }

    /**
     * Sets whether to log exhausted message body with message history.
     * Requires <tt>logExhaustedMessageHistory</tt> to be enabled.
     */
    public OnExceptionDefinition logExhaustedMessageBody(String logExhaustedMessageBody) {
        getOrCreateRedeliveryPolicy().logExhaustedMessageBody(logExhaustedMessageBody);
        return this;
    }

    /**
     * Sets the maximum redeliveries
     * <ul>
     * <li>5 = default value</li>
     * <li>0 = no redeliveries</li>
     * <li>-1 = redeliver forever</li>
     * </ul>
     *
     * @param maximumRedeliveries the value
     * @return the builder
     */
    public OnExceptionDefinition maximumRedeliveries(int maximumRedeliveries) {
        getOrCreateRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    /**
     * Sets the maximum redeliveries (supports property placeholders)
     * <ul>
     * <li>5 = default value</li>
     * <li>0 = no redeliveries</li>
     * <li>-1 = redeliver forever</li>
     * </ul>
     *
     * @param maximumRedeliveries the value
     * @return the builder
     */
    public OnExceptionDefinition maximumRedeliveries(String maximumRedeliveries) {
        getOrCreateRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    /**
     * Turn on collision avoidance.
     *
     * @return the builder
     */
    public OnExceptionDefinition useCollisionAvoidance() {
        getOrCreateRedeliveryPolicy().useCollisionAvoidance();
        return this;
    }

    /**
     * Turn on exponential back off
     *
     * @return the builder
     */
    public OnExceptionDefinition useExponentialBackOff() {
        getOrCreateRedeliveryPolicy().useExponentialBackOff();
        return this;
    }

    /**
     * Sets the maximum delay between redelivery
     *
     * @param maximumRedeliveryDelay the delay in millis
     * @return the builder
     */
    public OnExceptionDefinition maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        getOrCreateRedeliveryPolicy().maximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    /**
     * Sets the maximum delay between redelivery (supports property placeholders)
     *
     * @param maximumRedeliveryDelay the delay in millis
     * @return the builder
     */
    public OnExceptionDefinition maximumRedeliveryDelay(String maximumRedeliveryDelay) {
        getOrCreateRedeliveryPolicy().maximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    /**
     * Sets a reference to a {@link org.apache.camel.processor.RedeliveryPolicy} to lookup in the {@link org.apache.camel.spi.Registry} to be used.
     *
     * @param redeliveryPolicyRef reference to use for lookup
     * @return the builder
     */
    public OnExceptionDefinition redeliveryPolicyRef(String redeliveryPolicyRef) {
        setRedeliveryPolicyRef(redeliveryPolicyRef);
        return this;
    }

    /**
     * Sets the delay pattern with delay intervals.
     *
     * @param delayPattern the delay pattern
     * @return the builder
     */
    public OnExceptionDefinition delayPattern(String delayPattern) {
        getOrCreateRedeliveryPolicy().setDelayPattern(delayPattern);
        return this;
    }

    /**
     * Will use the original input message when an {@link org.apache.camel.Exchange} is moved to the dead letter queue.
     * <p/>
     * <b>Notice:</b> this only applies when all redeliveries attempt have failed and the {@link org.apache.camel.Exchange} is doomed for failure.
     * <br/>
     * Instead of using the current in-progress {@link org.apache.camel.Exchange} IN body we use the original IN body instead. This allows
     * you to store the original input in the dead letter queue instead of the in-progress snapshot of the IN body.
     * For instance if you route transform the IN body during routing and then failed. With the original exchange
     * store in the dead letter queue it might be easier to manually re submit the {@link org.apache.camel.Exchange} again as the IN body
     * is the same as when Camel received it. So you should be able to send the {@link org.apache.camel.Exchange} to the same input.
     * <p/>
     * By default this feature is off.
     *
     * @return the builder
     */
    public OnExceptionDefinition useOriginalMessage() {
        setUseOriginalMessagePolicy(Boolean.TRUE);
        return this;
    }

    /**
     * Sets a processor that should be processed <b>before</b> a redelivery attempt.
     * <p/>
     * Can be used to change the {@link org.apache.camel.Exchange} <b>before</b> its being redelivered.
     */
    public OnExceptionDefinition onRedelivery(Processor processor) {
        setOnRedelivery(processor);
        return this;
    }

    /**
     * Sets a reference to a processor that should be processed <b>before</b> a redelivery attempt.
     * <p/>
     * Can be used to change the {@link org.apache.camel.Exchange} <b>before</b> its being redelivered.
     *
     * @param ref  reference to the processor
     */
    public OnExceptionDefinition onRedeliveryRef(String ref) {
        setOnRedeliveryRef(ref);
        return this;
    }

    /**
     * Sets a processor that should be processed <b>just after</b> an exception occurred.
     * Can be used to perform custom logging about the occurred exception at the exact time it happened.
     * <p/>
     * Important: Any exception thrown from this processor will be ignored.
     */
    public OnExceptionDefinition onExceptionOccurred(Processor processor) {
        setOnExceptionOccurred(processor);
        return this;
    }

    /**
     * Sets a reference to a processor that should be processed <b>just after</b> an exception occurred.
     * Can be used to perform custom logging about the occurred exception at the exact time it happened.
     * <p/>
     * Important: Any exception thrown from this processor will be ignored.
     *
     * @param ref  reference to the processor
     */
    public OnExceptionDefinition onExceptionOccurredRef(String ref) {
        setOnExceptionOccurredRef(ref);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
    }

    public boolean isOutputSupported() {
        return true;
    }

    public List<Class<? extends Throwable>> getExceptionClasses() {
        return exceptionClasses;
    }

    public void setExceptionClasses(List<Class<? extends Throwable>> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    /**
     * A set of exceptions to react upon.
     */
    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }

    public Processor getErrorHandler(String routeId) {
        return errorHandlers.get(routeId);
    }
    
    public Collection<Processor> getErrorHandlers() {
        return errorHandlers.values();
    }

    public void setErrorHandler(String routeId, Processor errorHandler) {
        errorHandlers.put(routeId, errorHandler);
    }

    public RedeliveryPolicyDefinition getRedeliveryPolicyType() {
        return redeliveryPolicyType;
    }

    /**
     * Used for configuring redelivery options
     */
    public void setRedeliveryPolicyType(RedeliveryPolicyDefinition redeliveryPolicyType) {
        this.redeliveryPolicyType = redeliveryPolicyType;
    }

    public String getRedeliveryPolicyRef() {
        return redeliveryPolicyRef;
    }

    public void setRedeliveryPolicyRef(String redeliveryPolicyRef) {
        this.redeliveryPolicyRef = redeliveryPolicyRef;
    }

    public Predicate getHandledPolicy() {
        return handledPolicy;
    }

    public void setHandled(ExpressionSubElementDefinition handled) {
        this.handled = handled;
    }

    public ExpressionSubElementDefinition getContinued() {
        return continued;
    }

    public void setContinued(ExpressionSubElementDefinition continued) {
        this.continued = continued;
    }

    public ExpressionSubElementDefinition getHandled() {
        return handled;
    }

    public void setHandledPolicy(Predicate handledPolicy) {
        this.handledPolicy = handledPolicy;
    }

    public Predicate getContinuedPolicy() {
        return continuedPolicy;
    }

    public void setContinuedPolicy(Predicate continuedPolicy) {
        this.continuedPolicy = continuedPolicy;
    }

    public WhenDefinition getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(WhenDefinition onWhen) {
        this.onWhen = onWhen;
    }

    public ExpressionSubElementDefinition getRetryWhile() {
        return retryWhile;
    }

    public void setRetryWhile(ExpressionSubElementDefinition retryWhile) {
        this.retryWhile = retryWhile;
    }

    public Predicate getRetryWhilePolicy() {
        return retryWhilePolicy;
    }

    public void setRetryWhilePolicy(Predicate retryWhilePolicy) {
        this.retryWhilePolicy = retryWhilePolicy;
    }

    public Processor getOnRedelivery() {
        return onRedelivery;
    }

    public void setOnRedelivery(Processor onRedelivery) {
        this.onRedelivery = onRedelivery;
    }

    public String getOnRedeliveryRef() {
        return onRedeliveryRef;
    }

    public void setOnRedeliveryRef(String onRedeliveryRef) {
        this.onRedeliveryRef = onRedeliveryRef;
    }

    public Processor getOnExceptionOccurred() {
        return onExceptionOccurred;
    }

    public void setOnExceptionOccurred(Processor onExceptionOccurred) {
        this.onExceptionOccurred = onExceptionOccurred;
    }

    public String getOnExceptionOccurredRef() {
        return onExceptionOccurredRef;
    }

    public void setOnExceptionOccurredRef(String onExceptionOccurredRef) {
        this.onExceptionOccurredRef = onExceptionOccurredRef;
    }

    public Boolean getUseOriginalMessagePolicy() {
        return useOriginalMessagePolicy;
    }

    public void setUseOriginalMessagePolicy(Boolean useOriginalMessagePolicy) {
        this.useOriginalMessagePolicy = useOriginalMessagePolicy;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected RedeliveryPolicyDefinition getOrCreateRedeliveryPolicy() {
        if (redeliveryPolicyType == null) {
            redeliveryPolicyType = new RedeliveryPolicyDefinition();
        }
        return redeliveryPolicyType;
    }

}
