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
import org.apache.camel.util.CamelContextHelper;

/**
 * A factory which instantiates {@link RedeliveryPolicy} objects
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelRedeliveryPolicyFactoryBean extends AbstractCamelFactoryBean<RedeliveryPolicy> {

    @XmlAttribute
    private String maximumRedeliveries;
    @XmlAttribute
    private String redeliveryDelay;
    @XmlAttribute
    private String asyncDelayedRedelivery;
    @XmlAttribute
    private String backOffMultiplier;
    @XmlAttribute
    private String useExponentialBackOff;
    @XmlAttribute
    private String collisionAvoidanceFactor;
    @XmlAttribute
    private String useCollisionAvoidance;
    @XmlAttribute
    private String maximumRedeliveryDelay;
    @XmlAttribute
    private LoggingLevel retriesExhaustedLogLevel;
    @XmlAttribute
    private LoggingLevel retryAttemptedLogLevel;
    @XmlAttribute
    private String logRetryAttempted;
    @XmlAttribute
    private String logStackTrace;
    @XmlAttribute
    private String logRetryStackTrace;
    @XmlAttribute
    private String logHandled;
    @XmlAttribute
    private String logContinued;
    @XmlAttribute
    private String logExhausted;
    @XmlAttribute
    private String logExhaustedMessageHistory;
    @XmlAttribute
    private String disableRedelivery;
    @XmlAttribute
    private String delayPattern;
    @XmlAttribute
    private String allowRedeliveryWhileStopping;
    @XmlAttribute
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