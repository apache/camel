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
package org.apache.camel.core.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CamelContextHelper;

/**
 * A factory which instantiates {@link RedeliveryPolicy} objects
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelRedeliveryPolicyFactoryBean extends AbstractCamelFactoryBean<RedeliveryPolicy> {

    @XmlAttribute
    @Metadata(description = "Sets the maximum number of times a message exchange will be redelivered. Setting a negative value will retry forever.")
    private String maximumRedeliveries;
    @XmlAttribute
    @Metadata(defaultValue = "1000", description = "Sets the maximum redelivery delay. Use -1 if you wish to have no maximum")
    private String redeliveryDelay;
    @XmlAttribute
    @Metadata(defaultValue = "false", description = "Sets whether asynchronous delayed redelivery is allowed. This is disabled by default. "
        + "When enabled it allows Camel to schedule a future task for delayed redelivery which prevents current thread from blocking while waiting. "
        + "Exchange which is transacted will however always use synchronous delayed redelivery because the transaction must execute in the same thread context.")
    private String asyncDelayedRedelivery;
    @XmlAttribute
    @Metadata(defaultValue = "2", description = "Sets the multiplier used to increase the delay between redeliveries if useExponentialBackOff is enabled")
    private String backOffMultiplier;
    @XmlAttribute
    @Metadata(defaultValue = "false", description = "Enables/disables exponential backoff using the backOffMultiplier to increase the time between retries")
    private String useExponentialBackOff;
    @XmlAttribute
    @Metadata(defaultValue = "0.15", description = "Sets the factor used for collision avoidance if enabled via useCollisionAvoidance.")
    private String collisionAvoidanceFactor;
    @XmlAttribute
    @Metadata(defaultValue = "false", description = "Enables/disables collision avoidance which adds some randomization to the backoff timings to reduce contention probability")
    private String useCollisionAvoidance;
    @XmlAttribute
    @Metadata(defaultValue = "60000", description = "Sets the maximum redelivery delay. Use -1 if you wish to have no maximum")
    private String maximumRedeliveryDelay;
    @XmlAttribute
    @Metadata(defaultValue = "ERROR", description = "Sets the logging level to use for log messages when retries have been exhausted.")
    private LoggingLevel retriesExhaustedLogLevel;
    @XmlAttribute
    @Metadata(defaultValue = "DEBUG", description = "Sets the logging level to use for log messages when retries are attempted.")
    private LoggingLevel retryAttemptedLogLevel;
    @XmlAttribute
    @Metadata(defaultValue = "true", description = "Sets whether to log retry attempts")
    private String logRetryAttempted;
    @XmlAttribute
    @Metadata(defaultValue = "true", description = "Sets whether stack traces should be logged or not")
    private String logStackTrace;
    @XmlAttribute
    @Metadata(defaultValue = "false", description = "Sets whether stack traces should be logged or not")
    private String logRetryStackTrace;
    @XmlAttribute
    @Metadata(defaultValue = "false", description = "Sets whether errors should be logged even if its handled")
    private String logHandled;
    @XmlAttribute
    @Metadata(defaultValue = "false", description = "Sets whether errors should be logged even if its continued")
    private String logContinued;
    @XmlAttribute
    @Metadata(defaultValue = "true", description = "Sets whether exhausted exceptions should be logged or not")
    private String logExhausted;
    @XmlAttribute
    @Metadata(defaultValue = "false", description = "Sets whether to log exhausted errors including message history")
    private String logExhaustedMessageHistory;
    @XmlAttribute
    @Metadata(defaultValue = "false", description = "Disables redelivery by setting maximum redeliveries to 0.")
    private String disableRedelivery;
    @XmlAttribute
    @Metadata(description = "Sets an optional delay pattern to use instead of fixed delay.")
    private String delayPattern;
    @XmlAttribute
    @Metadata(defaultValue = "true", description = "Controls whether to allow redelivery while stopping/shutting down a route that uses error handling.")
    private String allowRedeliveryWhileStopping;
    @XmlAttribute
    @Metadata(description = "Sets the reference of the instance of {@link org.apache.camel.spi.ExchangeFormatter} to generate the log message from exchange.")
    private String exchangeFormatterRef;

    public RedeliveryPolicy getObject() throws Exception {
        RedeliveryPolicy answer = new RedeliveryPolicy();
        CamelContext context = getCamelContext();

        // copy across the properties - if they are set
        if (maximumRedeliveries != null) {
            answer.setMaximumRedeliveries(CamelContextHelper.parseInteger(context, maximumRedeliveries));
        }
        if (redeliveryDelay != null) {
            answer.setRedeliveryDelay(CamelContextHelper.parseLong(context, redeliveryDelay));
        }
        if (asyncDelayedRedelivery != null) {
            if (CamelContextHelper.parseBoolean(context, asyncDelayedRedelivery)) {
                answer.asyncDelayedRedelivery();
            }
        }
        if (retriesExhaustedLogLevel != null) {
            answer.setRetriesExhaustedLogLevel(retriesExhaustedLogLevel);
        }
        if (retryAttemptedLogLevel != null) {
            answer.setRetryAttemptedLogLevel(retryAttemptedLogLevel);
        }
        if (backOffMultiplier != null) {
            answer.setBackOffMultiplier(CamelContextHelper.parseDouble(context, backOffMultiplier));
        }
        if (useExponentialBackOff != null) {
            answer.setUseExponentialBackOff(CamelContextHelper.parseBoolean(context, useExponentialBackOff));
        }
        if (collisionAvoidanceFactor != null) {
            answer.setCollisionAvoidanceFactor(CamelContextHelper.parseDouble(context, collisionAvoidanceFactor));
        }
        if (useCollisionAvoidance != null) {
            answer.setUseCollisionAvoidance(CamelContextHelper.parseBoolean(context, useCollisionAvoidance));
        }
        if (maximumRedeliveryDelay != null) {
            answer.setMaximumRedeliveryDelay(CamelContextHelper.parseLong(context, maximumRedeliveryDelay));
        }
        if (logStackTrace != null) {
            answer.setLogStackTrace(CamelContextHelper.parseBoolean(context, logStackTrace));
        }
        if (logRetryStackTrace != null) {
            answer.setLogRetryStackTrace(CamelContextHelper.parseBoolean(context, logRetryStackTrace));
        }
        if (logHandled != null) {
            answer.setLogHandled(CamelContextHelper.parseBoolean(context, logHandled));
        }
        if (logContinued != null) {
            answer.setLogContinued(CamelContextHelper.parseBoolean(context, logContinued));
        }
        if (logRetryAttempted != null) {
            answer.setLogRetryAttempted(CamelContextHelper.parseBoolean(context, logRetryAttempted));
        }
        if (logExhausted != null) {
            answer.setLogExhausted(CamelContextHelper.parseBoolean(context, logExhausted));
        }
        if (logExhaustedMessageHistory != null) {
            answer.setLogExhaustedMessageHistory(CamelContextHelper.parseBoolean(context, logExhaustedMessageHistory));
        }
        if (disableRedelivery != null) {
            if (CamelContextHelper.parseBoolean(context, disableRedelivery)) {
                answer.setMaximumRedeliveries(0);
            }
        }
        if (delayPattern != null) {
            answer.setDelayPattern(CamelContextHelper.parseText(context, delayPattern));
        }
        if (allowRedeliveryWhileStopping != null) {
            answer.setAllowRedeliveryWhileStopping(CamelContextHelper.parseBoolean(context, allowRedeliveryWhileStopping));
        }
        if (exchangeFormatterRef != null) {
            answer.setExchangeFormatterRef(exchangeFormatterRef);
        }

        return answer;
    }

    public Class<RedeliveryPolicy> getObjectType() {
        return RedeliveryPolicy.class;
    }

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

    public LoggingLevel getRetriesExhaustedLogLevel() {
        return retriesExhaustedLogLevel;
    }

    public void setRetriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        this.retriesExhaustedLogLevel = retriesExhaustedLogLevel;
    }

    public LoggingLevel getRetryAttemptedLogLevel() {
        return retryAttemptedLogLevel;
    }

    public void setRetryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        this.retryAttemptedLogLevel = retryAttemptedLogLevel;
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

    public String getDisableRedelivery() {
        return disableRedelivery;
    }

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