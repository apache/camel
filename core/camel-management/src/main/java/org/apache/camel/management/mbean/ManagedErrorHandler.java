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
package org.apache.camel.management.mbean;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedErrorHandlerMBean;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.spi.ManagementStrategy;

@ManagedResource(description = "Managed ErrorHandler")
public class ManagedErrorHandler implements ManagedErrorHandlerMBean {
    private final Route route;
    private final Processor errorHandler;
    private final ErrorHandlerFactory errorHandlerBuilder;

    public ManagedErrorHandler(Route route, Processor errorHandler, ErrorHandlerFactory builder) {
        this.route = route;
        this.errorHandler = errorHandler;
        this.errorHandlerBuilder = builder;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public Route getRoute() {
        return route;
    }

    public Processor getErrorHandler() {
        return errorHandler;
    }

    public ErrorHandlerFactory getErrorHandlerBuilder() {
        return errorHandlerBuilder;
    }

    @Override
    public String getCamelId() {
        return route.getCamelContext().getName();
    }

    @Override
    public String getCamelManagementName() {
        return route.getCamelContext().getManagementName();
    }

    @Override
    public boolean isSupportRedelivery() {
        return errorHandler instanceof RedeliveryErrorHandler;
    }

    @Override
    public boolean isDeadLetterChannel() {
        if (!isSupportRedelivery()) {
            return false;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getDeadLetter() != null;
    }

    @Override
    public boolean isDeadLetterUseOriginalMessage() {
        if (!isSupportRedelivery()) {
            return false;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.isUseOriginalMessagePolicy();
    }

    @Override
    public boolean isDeadLetterUseOriginalBody() {
        if (!isSupportRedelivery()) {
            return false;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.isUseOriginalBodyPolicy();
    }

    @Override
    public boolean isDeadLetterHandleNewException() {
        if (!isSupportRedelivery()) {
            return false;
        }

        // must be a dead letter channel
        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return isDeadLetterChannel() && redelivery.isDeadLetterHandleNewException();
    }

    @Override
    public boolean isSupportTransactions() {
        if (errorHandler instanceof ErrorHandlerSupport) {
            ErrorHandlerSupport ehs = (ErrorHandlerSupport) errorHandler;
            return ehs.supportTransacted();
        } else {
            return false;
        }
    }

    @Override
    public String getDeadLetterChannelEndpointUri() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getDeadLetterUri();
    }

    @Override
    public Integer getMaximumRedeliveries() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getMaximumRedeliveries();
    }

    @Override
    public void setMaximumRedeliveries(Integer maximum) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setMaximumRedeliveries(maximum);
    }

    @Override
    public Long getMaximumRedeliveryDelay() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getMaximumRedeliveryDelay();
    }

    @Override
    public void setMaximumRedeliveryDelay(Long delay) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setMaximumRedeliveryDelay(delay);
    }

    @Override
    public Long getRedeliveryDelay() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getRedeliveryDelay();
    }

    @Override
    public void setRedeliveryDelay(Long delay) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setRedeliveryDelay(delay);
    }

    @Override
    public Double getBackOffMultiplier() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getBackOffMultiplier();
    }

    @Override
    public void setBackOffMultiplier(Double multiplier) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setBackOffMultiplier(multiplier);
    }

    @Override
    public Double getCollisionAvoidanceFactor() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getCollisionAvoidanceFactor();
    }

    @Override
    public void setCollisionAvoidanceFactor(Double factor) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setCollisionAvoidanceFactor(factor);
    }

    @Override
    public Double getCollisionAvoidancePercent() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return (double) redelivery.getRedeliveryPolicy().getCollisionAvoidancePercent();  
    }

    @Override
    public void setCollisionAvoidancePercent(Double percent) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setCollisionAvoidancePercent(percent);
    }

    @Override
    public String getDelayPattern() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getDelayPattern();
    }

    @Override
    public void setDelayPattern(String pattern) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setDelayPattern(pattern);
    }

    @Override
    public String getRetriesExhaustedLogLevel() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getRetriesExhaustedLogLevel().name();
    }

    @Override
    public void setRetriesExhaustedLogLevel(String level) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setRetriesExhaustedLogLevel(LoggingLevel.valueOf(level));
    }

    @Override
    public String getRetryAttemptedLogLevel() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().getRetryAttemptedLogLevel().name();
    }

    @Override
    public void setRetryAttemptedLogLevel(String level) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setRetryAttemptedLogLevel(LoggingLevel.valueOf(level));
    }

    @Override
    public Boolean getLogStackTrace() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogStackTrace();
    }

    @Override
    public void setLogStackTrace(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogStackTrace(log);
    }

    @Override
    public Boolean getLogRetryStackTrace() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogRetryStackTrace();
    }

    @Override
    public void setLogRetryStackTrace(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogRetryStackTrace(log);
    }

    @Override
    public Boolean getLogHandled() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogHandled();
    }

    @Override
    public void setLogHandled(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogHandled(log);
    }

    @Override
    public Boolean getLogNewException() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogNewException();
    }

    @Override
    public void setLogNewException(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogNewException(log);
    }

    @Override
    public Boolean getLogExhaustedMessageHistory() {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogExhaustedMessageHistory();
    }

    @Override
    public void setLogExhaustedMessageHistory(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogExhaustedMessageHistory(log);
    }

    @Override
    public Boolean getLogExhaustedMessageBody() {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogExhaustedMessageBody();
    }

    @Override
    public void setLogExhaustedMessageBody(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogExhaustedMessageBody(log);
    }

    @Override
    public Boolean getLogContinued() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogHandled();
    }

    @Override
    public void setLogContinued(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogContinued(log);
    }

    @Override
    public Boolean getLogExhausted() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isLogExhausted();
    }

    @Override
    public void setLogExhausted(Boolean log) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setLogExhausted(log);
    }

    @Override
    public Boolean getUseCollisionAvoidance() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isUseCollisionAvoidance();
    }

    @Override
    public void setUseCollisionAvoidance(Boolean avoidance) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setUseCollisionAvoidance(avoidance);
    }

    @Override
    public Boolean getUseExponentialBackOff() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isUseExponentialBackOff();
    }

    @Override
    public void setUseExponentialBackOff(Boolean backoff) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setUseExponentialBackOff(backoff);
    }

    @Override
    public Boolean getAllowRedeliveryWhileStopping() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getRedeliveryPolicy().isAllowRedeliveryWhileStopping();
    }

    @Override
    public void setAllowRedeliveryWhileStopping(Boolean allow) {
        if (!isSupportRedelivery()) {
            throw new IllegalArgumentException("This error handler does not support redelivery");
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        redelivery.getRedeliveryPolicy().setAllowRedeliveryWhileStopping(allow);
    }

    @Override
    public Integer getPendingRedeliveryCount() {
        if (!isSupportRedelivery()) {
            return null;
        }

        RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
        return redelivery.getPendingRedeliveryCount();
    }

}
