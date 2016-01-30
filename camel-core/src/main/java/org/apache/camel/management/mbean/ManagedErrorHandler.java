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
package org.apache.camel.management.mbean;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedErrorHandlerMBean;
import org.apache.camel.processor.ErrorHandlerSupport;
import org.apache.camel.processor.RedeliveryErrorHandler;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * @version 
 */
@ManagedResource(description = "Managed ErrorHandler")
public class ManagedErrorHandler implements ManagedErrorHandlerMBean {
    private final RouteContext routeContext;
    private final Processor errorHandler;
    private final ErrorHandlerFactory errorHandlerBuilder;

    public ManagedErrorHandler(RouteContext routeContext, Processor errorHandler, ErrorHandlerFactory builder) {
        this.routeContext = routeContext;
        this.errorHandler = errorHandler;
        this.errorHandlerBuilder = builder;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public RouteContext getRouteContext() {
        return routeContext;
    }

    public Processor getErrorHandler() {
        return errorHandler;
    }

    public ErrorHandlerFactory getErrorHandlerBuilder() {
        return errorHandlerBuilder;
    }

    public String getCamelId() {
        return routeContext.getCamelContext().getName();
    }

    public String getCamelManagementName() {
        return routeContext.getCamelContext().getManagementName();
    }

    public boolean isSupportRedelivery() {
        return errorHandler instanceof RedeliveryErrorHandler;
    }

    public boolean isDeadLetterChannel() {
        if (!isSupportRedelivery()) {
            return false;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getDeadLetter() != null;
    }

    public boolean isDeadLetterUseOriginalMessage() {
        if (!isSupportRedelivery()) {
            return false;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.isUseOriginalMessagePolicy();
    }

    public boolean isDeadLetterHandleNewException() {
        if (!isSupportRedelivery()) {
            return false;
        }

        // must be a dead letter channel
        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return isDeadLetterChannel() && redelivery.isDeadLetterHandleNewException();
    }

    public boolean isSupportTransactions() {
        if (errorHandler instanceof ErrorHandlerSupport) {
            ErrorHandlerSupport ehs = (ErrorHandlerSupport) errorHandler;
            return ehs.supportTransacted();
        } else {
            return false;
        }
    }

    public String getDeadLetterChannelEndpointUri() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getDeadLetterUri();
    }

    public Integer getMaximumRedeliveries() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getMaximumRedeliveries();
    }

    public void setMaximumRedeliveries(Integer maximum) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setMaximumRedeliveries(maximum);
    }

    public Long getMaximumRedeliveryDelay() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getMaximumRedeliveryDelay();
    }

    public void setMaximumRedeliveryDelay(Long delay) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setMaximumRedeliveryDelay(delay);
    }

    public Long getRedeliveryDelay() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getRedeliveryDelay();
    }

    public void setRedeliveryDelay(Long delay) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setRedeliveryDelay(delay);
    }

    public Double getBackOffMultiplier() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getBackOffMultiplier();
    }

    public void setBackOffMultiplier(Double multiplier) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setBackOffMultiplier(multiplier);
    }

    public Double getCollisionAvoidanceFactor() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getCollisionAvoidanceFactor();
    }

    public void setCollisionAvoidanceFactor(Double factor) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setCollisionAvoidanceFactor(factor);
    }

    public Double getCollisionAvoidancePercent() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return (double) redelivery.getRedeliveryPolicy().getCollisionAvoidancePercent();  
    }

    public void setCollisionAvoidancePercent(Double percent) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setCollisionAvoidancePercent(percent);
    }

    public String getDelayPattern() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getDelayPattern();
    }

    public void setDelayPattern(String pattern) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setDelayPattern(pattern);
    }

    public String getRetriesExhaustedLogLevel() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getRetriesExhaustedLogLevel().name();
    }

    public void setRetriesExhaustedLogLevel(String level) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setRetriesExhaustedLogLevel(LoggingLevel.valueOf(level));
    }

    public String getRetryAttemptedLogLevel() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getRetryAttemptedLogLevel().name();
    }

    public void setRetryAttemptedLogLevel(String level) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setRetryAttemptedLogLevel(LoggingLevel.valueOf(level));
    }

    public Boolean getLogStackTrace() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogStackTrace();
    }

    public void setLogStackTrace(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogStackTrace(log);
    }

    public Boolean getLogRetryStackTrace() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogRetryStackTrace();
    }

    public void setLogRetryStackTrace(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogRetryStackTrace(log);
    }

    public Boolean getLogHandled() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogHandled();
    }

    public void setLogHandled(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogHandled(log);
    }

    public Boolean getLogNewException() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogNewException();
    }

    public void setLogNewException(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogNewException(log);
    }

    public Boolean getLogExhaustedMessageHistory() {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogExhaustedMessageHistory();
    }

    public void setLogExhaustedMessageHistory(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogExhaustedMessageHistory(log);
    }

    public Boolean getLogExhaustedMessageBody() {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogExhaustedMessageBody();
    }

    public void setLogExhaustedMessageBody(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogExhaustedMessageBody(log);
    }

    public Boolean getLogContinued() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogHandled();
    }

    public void setLogContinued(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogContinued(log);
    }

    public Boolean getLogExhausted() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogExhausted();
    }

    public void setLogExhausted(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogExhausted(log);
    }

    public Boolean getUseCollisionAvoidance() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isUseCollisionAvoidance();
    }

    public void setUseCollisionAvoidance(Boolean avoidance) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setUseCollisionAvoidance(avoidance);
    }

    public Boolean getUseExponentialBackOff() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isUseExponentialBackOff();
    }

    public void setUseExponentialBackOff(Boolean backoff) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setUseExponentialBackOff(backoff);
    }

    public Boolean getAllowRedeliveryWhileStopping() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isAllowRedeliveryWhileStopping();
    }

    public void setAllowRedeliveryWhileStopping(Boolean allow) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setAllowRedeliveryWhileStopping(allow);
    }

    public Integer getPendingRedeliveryCount() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getPendingRedeliveryCount();
    }

}
