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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.Metadata;

/**
 * To configure re-delivery for error handling
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "redeliveryPolicy")
@XmlAccessorType(XmlAccessType.FIELD)
public class RedeliveryPolicyDefinition {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String maximumRedeliveries;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String redeliveryDelay;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String asyncDelayedRedelivery;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Double")
    private String backOffMultiplier;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String useExponentialBackOff;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Double")
    private String collisionAvoidanceFactor;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String useCollisionAvoidance;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String maximumRedeliveryDelay;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.LoggingLevel")
    private String retriesExhaustedLogLevel;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.LoggingLevel")
    private String retryAttemptedLogLevel;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String retryAttemptedLogInterval;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logRetryAttempted;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logStackTrace;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logRetryStackTrace;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logHandled;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logNewException;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logContinued;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logExhausted;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logExhaustedMessageHistory;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String logExhaustedMessageBody;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String disableRedelivery;
    @XmlAttribute
    private String delayPattern;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String allowRedeliveryWhileStopping;
    @XmlAttribute
    private String exchangeFormatterRef;

    @Override
    public String toString() {
        return "RedeliveryPolicy[maximumRedeliveries: " + maximumRedeliveries + "]";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Allow synchronous delayed redelivery. The route, in particular the
     * consumer's component, must support the Asynchronous Routing Engine (e.g.
     * seda).
     *
     * @return the builder
     */
    public RedeliveryPolicyDefinition asyncDelayedRedelivery() {
        setAsyncDelayedRedelivery("true");
        return this;
    }

    /**
     * Controls whether to allow redelivery while stopping/shutting down a route
     * that uses error handling.
     *
     * @param allowRedeliveryWhileStopping <tt>true</tt> to allow redelivery,
     *            <tt>false</tt> to reject redeliveries
     * @return the builder
     */
    public RedeliveryPolicyDefinition allowRedeliveryWhileStopping(boolean allowRedeliveryWhileStopping) {
        return allowRedeliveryWhileStopping(Boolean.toString(allowRedeliveryWhileStopping));
    }

    /**
     * Controls whether to allow redelivery while stopping/shutting down a route
     * that uses error handling.
     *
     * @param allowRedeliveryWhileStopping <tt>true</tt> to allow redelivery,
     *            <tt>false</tt> to reject redeliveries
     * @return the builder
     */
    public RedeliveryPolicyDefinition allowRedeliveryWhileStopping(String allowRedeliveryWhileStopping) {
        setAllowRedeliveryWhileStopping(allowRedeliveryWhileStopping);
        return this;
    }

    /**
     * Sets the back off multiplier
     *
     * @param backOffMultiplier the back off multiplier
     * @return the builder
     */
    public RedeliveryPolicyDefinition backOffMultiplier(double backOffMultiplier) {
        return backOffMultiplier(Double.toString(backOffMultiplier));
    }

    /**
     * Sets the back off multiplier (supports property placeholders)
     *
     * @param backOffMultiplier the back off multiplier
     * @return the builder
     */
    public RedeliveryPolicyDefinition backOffMultiplier(String backOffMultiplier) {
        setBackOffMultiplier(backOffMultiplier);
        return this;
    }

    /**
     * Sets the collision avoidance percentage
     *
     * @param collisionAvoidancePercent the percentage
     * @return the builder
     */
    public RedeliveryPolicyDefinition collisionAvoidancePercent(double collisionAvoidancePercent) {
        setCollisionAvoidanceFactor(Double.toString(collisionAvoidancePercent * 0.01d));
        return this;
    }

    /**
     * Sets the collision avoidance factor
     *
     * @param collisionAvoidanceFactor the factor
     * @return the builder
     */
    public RedeliveryPolicyDefinition collisionAvoidanceFactor(double collisionAvoidanceFactor) {
        return collisionAvoidanceFactor(Double.toString(collisionAvoidanceFactor));
    }

    /**
     * Sets the collision avoidance factor (supports property placeholders)
     *
     * @param collisionAvoidanceFactor the factor
     * @return the builder
     */
    public RedeliveryPolicyDefinition collisionAvoidanceFactor(String collisionAvoidanceFactor) {
        setCollisionAvoidanceFactor(collisionAvoidanceFactor);
        return this;
    }

    /**
     * Sets the initial redelivery delay
     *
     * @param delay delay in millis
     * @return the builder
     */
    public RedeliveryPolicyDefinition redeliveryDelay(long delay) {
        return redeliveryDelay(Long.toString(delay));
    }

    /**
     * Sets the initial redelivery delay (supports property placeholders)
     *
     * @param delay delay in millis
     * @return the builder
     */
    public RedeliveryPolicyDefinition redeliveryDelay(String delay) {
        setRedeliveryDelay(delay);
        return this;
    }

    /**
     * Sets the logging level to use when retries has exhausted
     *
     * @param retriesExhaustedLogLevel the logging level
     * @return the builder
     */
    public RedeliveryPolicyDefinition retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        return retriesExhaustedLogLevel(retriesExhaustedLogLevel.name());
    }

    /**
     * Sets the logging level to use when retries has exhausted
     *
     * @param retriesExhaustedLogLevel the logging level
     * @return the builder
     */
    public RedeliveryPolicyDefinition retriesExhaustedLogLevel(String retriesExhaustedLogLevel) {
        setRetriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }

    /**
     * Sets the logging level to use for logging retry attempts
     *
     * @param retryAttemptedLogLevel the logging level
     * @return the builder
     */
    public RedeliveryPolicyDefinition retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        return retryAttemptedLogLevel(retryAttemptedLogLevel.name());
    }

    /**
     * Sets the logging level to use for logging retry attempts
     *
     * @param retryAttemptedLogLevel the logging level
     * @return the builder
     */
    public RedeliveryPolicyDefinition retryAttemptedLogLevel(String retryAttemptedLogLevel) {
        setRetryAttemptedLogLevel(retryAttemptedLogLevel);
        return this;
    }

    /**
     * Sets the interval to use for logging retry attempts
     *
     * @param retryAttemptedLogInterval the retry logging interval
     * @return the builder
     */
    public RedeliveryPolicyDefinition retryAttemptedLogInterval(String retryAttemptedLogInterval) {
        setRetryAttemptedLogInterval(retryAttemptedLogInterval);
        return this;
    }

    /**
     * Sets whether stack traces should be logged. Can be used to include or
     * reduce verbose.
     *
     * @param logStackTrace whether stack traces should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logStackTrace(boolean logStackTrace) {
        return logStackTrace(Boolean.toString(logStackTrace));
    }

    /**
     * Sets whether stack traces should be logged (supports property
     * placeholders) Can be used to include or reduce verbose.
     *
     * @param logStackTrace whether stack traces should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logStackTrace(String logStackTrace) {
        setLogStackTrace(logStackTrace);
        return this;
    }

    /**
     * Sets whether stack traces should be logged when an retry attempt failed.
     * Can be used to include or reduce verbose.
     *
     * @param logRetryStackTrace whether stack traces should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logRetryStackTrace(boolean logRetryStackTrace) {
        return logRetryStackTrace(Boolean.toString(logRetryStackTrace));
    }

    /**
     * Sets whether stack traces should be logged when an retry attempt failed
     * (supports property placeholders). Can be used to include or reduce
     * verbose.
     *
     * @param logRetryStackTrace whether stack traces should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logRetryStackTrace(String logRetryStackTrace) {
        setLogRetryStackTrace(logRetryStackTrace);
        return this;
    }

    /**
     * Sets whether retry attempts should be logged or not. Can be used to
     * include or reduce verbose.
     *
     * @param logRetryAttempted whether retry attempts should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logRetryAttempted(boolean logRetryAttempted) {
        return logRetryAttempted(Boolean.toString(logRetryAttempted));
    }

    /**
     * Sets whether retry attempts should be logged or not (supports property
     * placeholders). Can be used to include or reduce verbose.
     *
     * @param logRetryAttempted whether retry attempts should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logRetryAttempted(String logRetryAttempted) {
        setLogRetryAttempted(logRetryAttempted);
        return this;
    }

    /**
     * Sets whether handled exceptions should be logged or not. Can be used to
     * include or reduce verbose.
     *
     * @param logHandled whether handled exceptions should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logHandled(boolean logHandled) {
        return logHandled(Boolean.toString(logHandled));
    }

    /**
     * Sets whether handled exceptions should be logged or not (supports
     * property placeholders). Can be used to include or reduce verbose.
     *
     * @param logHandled whether handled exceptions should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logHandled(String logHandled) {
        setLogHandled(logHandled);
        return this;
    }

    /**
     * Sets whether new exceptions should be logged or not. Can be used to
     * include or reduce verbose.
     * <p/>
     * A new exception is an exception that was thrown while handling a previous
     * exception.
     *
     * @param logNewException whether new exceptions should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logNewException(boolean logNewException) {
        return logNewException(Boolean.toString(logNewException));
    }

    /**
     * Sets whether new exceptions should be logged or not (supports property
     * placeholders). Can be used to include or reduce verbose.
     * <p/>
     * A new exception is an exception that was thrown while handling a previous
     * exception.
     *
     * @param logNewException whether new exceptions should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logNewException(String logNewException) {
        setLogNewException(logNewException);
        return this;
    }

    /**
     * Sets whether continued exceptions should be logged or not. Can be used to
     * include or reduce verbose.
     *
     * @param logContinued whether continued exceptions should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logContinued(boolean logContinued) {
        return logContinued(Boolean.toString(logContinued));
    }

    /**
     * Sets whether continued exceptions should be logged or not (supports
     * property placeholders). Can be used to include or reduce verbose.
     *
     * @param logContinued whether continued exceptions should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logContinued(String logContinued) {
        setLogContinued(logContinued);
        return this;
    }

    /**
     * Sets whether exhausted exceptions should be logged or not. Can be used to
     * include or reduce verbose.
     *
     * @param logExhausted whether exhausted exceptions should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logExhausted(boolean logExhausted) {
        return logExhausted(Boolean.toString(logExhausted));
    }

    /**
     * Sets whether exhausted exceptions should be logged or not (supports
     * property placeholders). Can be used to include or reduce verbose.
     *
     * @param logExhausted whether exhausted exceptions should be logged or not
     * @return the builder
     */
    public RedeliveryPolicyDefinition logExhausted(String logExhausted) {
        setLogExhausted(logExhausted);
        return this;
    }

    /**
     * Sets whether exhausted exceptions should be logged including message
     * history or not (supports property placeholders). Can be used to include
     * or reduce verbose.
     *
     * @param logExhaustedMessageHistory whether exhausted exceptions should be
     *            logged with message history
     * @return the builder
     */
    public RedeliveryPolicyDefinition logExhaustedMessageHistory(boolean logExhaustedMessageHistory) {
        setLogExhaustedMessageHistory(Boolean.toString(logExhaustedMessageHistory));
        return this;
    }

    /**
     * Sets whether exhausted exceptions should be logged including message
     * history or not (supports property placeholders). Can be used to include
     * or reduce verbose.
     *
     * @param logExhaustedMessageHistory whether exhausted exceptions should be
     *            logged with message history
     * @return the builder
     */
    public RedeliveryPolicyDefinition logExhaustedMessageHistory(String logExhaustedMessageHistory) {
        setLogExhaustedMessageHistory(logExhaustedMessageHistory);
        return this;
    }

    /**
     * Sets whether exhausted message body should be logged including message
     * history or not (supports property placeholders). Can be used to include
     * or reduce verbose. Requires <tt>logExhaustedMessageHistory</tt> to be
     * enabled.
     *
     * @param logExhaustedMessageBody whether exhausted message body should be
     *            logged with message history
     * @return the builder
     */
    public RedeliveryPolicyDefinition logExhaustedMessageBody(boolean logExhaustedMessageBody) {
        setLogExhaustedMessageBody(Boolean.toString(logExhaustedMessageBody));
        return this;
    }

    /**
     * Sets whether exhausted message body should be logged including message
     * history or not (supports property placeholders). Can be used to include
     * or reduce verbose. Requires <tt>logExhaustedMessageHistory</tt> to be
     * enabled.
     *
     * @param logExhaustedMessageBody whether exhausted message body should be
     *            logged with message history
     * @return the builder
     */
    public RedeliveryPolicyDefinition logExhaustedMessageBody(String logExhaustedMessageBody) {
        setLogExhaustedMessageBody(logExhaustedMessageBody);
        return this;
    }

    /**
     * Sets the maximum redeliveries
     * <ul>
     * <li>x = redeliver at most x times</li>
     * <li>0 = no redeliveries</li>
     * <li>-1 = redeliver forever</li>
     * </ul>
     *
     * @param maximumRedeliveries the value
     * @return the builder
     */
    public RedeliveryPolicyDefinition maximumRedeliveries(int maximumRedeliveries) {
        return maximumRedeliveries(Integer.toString(maximumRedeliveries));
    }

    /**
     * Sets the maximum redeliveries (supports property placeholders)
     * <ul>
     * <li>x = redeliver at most x times</li>
     * <li>0 = no redeliveries</li>
     * <li>-1 = redeliver forever</li>
     * </ul>
     *
     * @param maximumRedeliveries the value
     * @return the builder
     */
    public RedeliveryPolicyDefinition maximumRedeliveries(String maximumRedeliveries) {
        setMaximumRedeliveries(maximumRedeliveries);
        return this;
    }

    /**
     * Turn on collision avoidance.
     *
     * @return the builder
     */
    public RedeliveryPolicyDefinition useCollisionAvoidance() {
        setUseCollisionAvoidance("true");
        return this;
    }

    /**
     * Turn on exponential backk off
     *
     * @return the builder
     */
    public RedeliveryPolicyDefinition useExponentialBackOff() {
        setUseExponentialBackOff("true");
        return this;
    }

    /**
     * Sets the maximum delay between redelivery
     *
     * @param maximumRedeliveryDelay the delay in millis
     * @return the builder
     */
    public RedeliveryPolicyDefinition maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        return maximumRedeliveryDelay(Long.toString(maximumRedeliveryDelay));
    }

    /**
     * Sets the maximum delay between redelivery (supports property
     * placeholders)
     *
     * @param maximumRedeliveryDelay the delay in millis
     * @return the builder
     */
    public RedeliveryPolicyDefinition maximumRedeliveryDelay(String maximumRedeliveryDelay) {
        setMaximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    /**
     * Sets the delay pattern with delay intervals.
     *
     * @param delayPattern the delay pattern
     * @return the builder
     */
    public RedeliveryPolicyDefinition delayPattern(String delayPattern) {
        setDelayPattern(delayPattern);
        return this;
    }

    /**
     * Sets the reference of the instance of
     * {@link org.apache.camel.spi.ExchangeFormatter} to generate the log
     * message from exchange.
     *
     * @param exchangeFormatterRef name of the instance of
     *            {@link org.apache.camel.spi.ExchangeFormatter}
     * @return the builder
     */
    public RedeliveryPolicyDefinition exchangeFormatterRef(String exchangeFormatterRef) {
        setExchangeFormatterRef(exchangeFormatterRef);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    public void setMaximumRedeliveries(String maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    public String getRedeliveryDelay() {
        return redeliveryDelay;
    }

    public void setRedeliveryDelay(String redeliveryDelay) {
        this.redeliveryDelay = redeliveryDelay;
    }

    public String getAsyncDelayedRedelivery() {
        return asyncDelayedRedelivery;
    }

    public void setAsyncDelayedRedelivery(String asyncDelayedRedelivery) {
        this.asyncDelayedRedelivery = asyncDelayedRedelivery;
    }

    public String getBackOffMultiplier() {
        return backOffMultiplier;
    }

    public void setBackOffMultiplier(String backOffMultiplier) {
        this.backOffMultiplier = backOffMultiplier;
    }

    public String getUseExponentialBackOff() {
        return useExponentialBackOff;
    }

    public void setUseExponentialBackOff(String useExponentialBackOff) {
        this.useExponentialBackOff = useExponentialBackOff;
    }

    public String getCollisionAvoidanceFactor() {
        return collisionAvoidanceFactor;
    }

    public void setCollisionAvoidanceFactor(String collisionAvoidanceFactor) {
        this.collisionAvoidanceFactor = collisionAvoidanceFactor;
    }

    public String getUseCollisionAvoidance() {
        return useCollisionAvoidance;
    }

    public void setUseCollisionAvoidance(String useCollisionAvoidance) {
        this.useCollisionAvoidance = useCollisionAvoidance;
    }

    public String getMaximumRedeliveryDelay() {
        return maximumRedeliveryDelay;
    }

    public void setMaximumRedeliveryDelay(String maximumRedeliveryDelay) {
        this.maximumRedeliveryDelay = maximumRedeliveryDelay;
    }

    public String getRetriesExhaustedLogLevel() {
        return retriesExhaustedLogLevel;
    }

    public void setRetriesExhaustedLogLevel(String retriesExhaustedLogLevel) {
        this.retriesExhaustedLogLevel = retriesExhaustedLogLevel;
    }

    public String getRetryAttemptedLogLevel() {
        return retryAttemptedLogLevel;
    }

    public void setRetryAttemptedLogLevel(String retryAttemptedLogLevel) {
        this.retryAttemptedLogLevel = retryAttemptedLogLevel;
    }

    public String getRetryAttemptedLogInterval() {
        return retryAttemptedLogInterval;
    }

    public void setRetryAttemptedLogInterval(String retryAttemptedLogInterval) {
        this.retryAttemptedLogInterval = retryAttemptedLogInterval;
    }

    public String getLogRetryAttempted() {
        return logRetryAttempted;
    }

    public void setLogRetryAttempted(String logRetryAttempted) {
        this.logRetryAttempted = logRetryAttempted;
    }

    public String getLogStackTrace() {
        return logStackTrace;
    }

    public void setLogStackTrace(String logStackTrace) {
        this.logStackTrace = logStackTrace;
    }

    public String getLogRetryStackTrace() {
        return logRetryStackTrace;
    }

    public void setLogRetryStackTrace(String logRetryStackTrace) {
        this.logRetryStackTrace = logRetryStackTrace;
    }

    public String getLogHandled() {
        return logHandled;
    }

    public void setLogHandled(String logHandled) {
        this.logHandled = logHandled;
    }

    public String getLogNewException() {
        return logNewException;
    }

    public void setLogNewException(String logNewException) {
        this.logNewException = logNewException;
    }

    public String getLogContinued() {
        return logContinued;
    }

    public void setLogContinued(String logContinued) {
        this.logContinued = logContinued;
    }

    public String getLogExhausted() {
        return logExhausted;
    }

    public void setLogExhausted(String logExhausted) {
        this.logExhausted = logExhausted;
    }

    public String getLogExhaustedMessageHistory() {
        return logExhaustedMessageHistory;
    }

    public void setLogExhaustedMessageHistory(String logExhaustedMessageHistory) {
        this.logExhaustedMessageHistory = logExhaustedMessageHistory;
    }

    public String getLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    public void setLogExhaustedMessageBody(String logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    public String getDisableRedelivery() {
        return disableRedelivery;
    }

    /**
     * Disables redelivery (same as setting maximum redeliveries to 0)
     */
    public void setDisableRedelivery(String disableRedelivery) {
        this.disableRedelivery = disableRedelivery;
    }

    public String getDelayPattern() {
        return delayPattern;
    }

    public void setDelayPattern(String delayPattern) {
        this.delayPattern = delayPattern;
    }

    public String getAllowRedeliveryWhileStopping() {
        return allowRedeliveryWhileStopping;
    }

    public void setAllowRedeliveryWhileStopping(String allowRedeliveryWhileStopping) {
        this.allowRedeliveryWhileStopping = allowRedeliveryWhileStopping;
    }

    public String getExchangeFormatterRef() {
        return exchangeFormatterRef;
    }

    public void setExchangeFormatterRef(String exchangeFormatterRef) {
        this.exchangeFormatterRef = exchangeFormatterRef;
    }

}
