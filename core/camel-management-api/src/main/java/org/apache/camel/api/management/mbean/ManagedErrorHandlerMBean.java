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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;

public interface ManagedErrorHandlerMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Does the error handler support redelivery")
    boolean isSupportRedelivery();

    @ManagedAttribute(description = "Is this error handler a dead letter channel")
    boolean isDeadLetterChannel();

    @ManagedAttribute(description = "When a message is moved to dead letter channel is it the original message or recent message")
    boolean isDeadLetterUseOriginalMessage();

    @ManagedAttribute(description = "When a message is moved to dead letter channel is it the original message body or recent message body")
    boolean isDeadLetterUseOriginalBody();

    @ManagedAttribute(description = "Does this error handler handle new exceptions which may occur during error handling")
    boolean isDeadLetterHandleNewException();

    @ManagedAttribute(description = "Does this error handler support transactions")
    boolean isSupportTransactions();

    @ManagedAttribute(description = "Endpoint Uri for the dead letter channel where dead message is move to", mask = true)
    String getDeadLetterChannelEndpointUri();

    @ManagedAttribute(description = "Number of Exchanges scheduled for redelivery (waiting to be redelivered in the future)")
    Integer getPendingRedeliveryCount();

    @ManagedAttribute(description = "RedeliveryPolicy for maximum redeliveries")
    Integer getMaximumRedeliveries();

    @ManagedAttribute(description = "RedeliveryPolicy for maximum redeliveries")
    void setMaximumRedeliveries(Integer maximum);

    @ManagedAttribute(description = "RedeliveryPolicy for maximum redelivery delay")
    Long getMaximumRedeliveryDelay();

    @ManagedAttribute(description = "RedeliveryPolicy for maximum redelivery delay")
    void setMaximumRedeliveryDelay(Long delay);

    @ManagedAttribute(description = "RedeliveryPolicy for redelivery delay")
    Long getRedeliveryDelay();

    @ManagedAttribute(description = "RedeliveryPolicy for redelivery delay")
    void setRedeliveryDelay(Long delay);

    @ManagedAttribute(description = "RedeliveryPolicy for backoff multiplier")
    Double getBackOffMultiplier();

    @ManagedAttribute(description = "RedeliveryPolicy for backoff multiplier")
    void setBackOffMultiplier(Double multiplier);

    @ManagedAttribute(description = "RedeliveryPolicy for collision avoidance factor")
    Double getCollisionAvoidanceFactor();

    @ManagedAttribute(description = "RedeliveryPolicy for collision avoidance factor")
    void setCollisionAvoidanceFactor(Double factor);

    @ManagedAttribute(description = "RedeliveryPolicy for collision avoidance percent")
    Double getCollisionAvoidancePercent();

    @ManagedAttribute(description = "RedeliveryPolicy for collision avoidance percent")
    void setCollisionAvoidancePercent(Double percent);

    @ManagedAttribute(description = "RedeliveryPolicy for delay pattern")
    String getDelayPattern();

    @ManagedAttribute(description = "RedeliveryPolicy for delay pattern")
    void setDelayPattern(String pattern);

    @ManagedAttribute(description = "RedeliveryPolicy for logging level when retries exhausted")
    String getRetriesExhaustedLogLevel();

    @ManagedAttribute(description = "RedeliveryPolicy for logging level when retries exhausted")
    void setRetriesExhaustedLogLevel(String level);

    @ManagedAttribute(description = "RedeliveryPolicy for logging level when attempting retry")
    String getRetryAttemptedLogLevel();

    @ManagedAttribute(description = "RedeliveryPolicy for logging level when attempting retry")
    void setRetryAttemptedLogLevel(String level);

    @ManagedAttribute(description = "RedeliveryPolicy for logging stack traces")
    Boolean getLogStackTrace();

    @ManagedAttribute(description = "RedeliveryPolicy for logging stack traces")
    void setLogStackTrace(Boolean log);

    @ManagedAttribute(description = "RedeliveryPolicy for logging redelivery stack traces")
    Boolean getLogRetryStackTrace();

    @ManagedAttribute(description = "RedeliveryPolicy for logging redelivery stack traces")
    void setLogRetryStackTrace(Boolean log);

    @ManagedAttribute(description = "RedeliveryPolicy for logging handled exceptions")
    Boolean getLogHandled();

    @ManagedAttribute(description = "RedeliveryPolicy for logging handled exceptions")
    void setLogHandled(Boolean log);

    @ManagedAttribute(description = "RedeliveryPolicy for logging new exceptions")
    Boolean getLogNewException();

    @ManagedAttribute(description = "RedeliveryPolicy for logging new exceptions")
    void setLogNewException(Boolean log);

    @ManagedAttribute(description = "RedeliveryPolicy for logging exhausted with message history")
    Boolean getLogExhaustedMessageHistory();

    @ManagedAttribute(description = "RedeliveryPolicy for logging exhausted with message history")
    void setLogExhaustedMessageHistory(Boolean log);

    @ManagedAttribute(description = "RedeliveryPolicy for logging exhausted with message history")
    Boolean getLogExhaustedMessageBody();

    @ManagedAttribute(description = "RedeliveryPolicy for logging exhausted with message body")
    void setLogExhaustedMessageBody(Boolean log);

    @ManagedAttribute(description = "RedeliveryPolicy for logging handled and continued exceptions")
    Boolean getLogContinued();

    @ManagedAttribute(description = "RedeliveryPolicy for logging handled and continued exceptions")
    void setLogContinued(Boolean log);

    @ManagedAttribute(description = "RedeliveryPolicy for logging exhausted exceptions")
    Boolean getLogExhausted();

    @ManagedAttribute(description = "RedeliveryPolicy for logging exhausted exceptions")
    void setLogExhausted(Boolean log);

    @ManagedAttribute(description = "RedeliveryPolicy for using collision avoidance")
    Boolean getUseCollisionAvoidance();

    @ManagedAttribute(description = "RedeliveryPolicy for using collision avoidance")
    void setUseCollisionAvoidance(Boolean avoidance);

    @ManagedAttribute(description = "RedeliveryPolicy for using exponential backoff")
    Boolean getUseExponentialBackOff();

    @ManagedAttribute(description = "RedeliveryPolicy for using exponential backoff")
    void setUseExponentialBackOff(Boolean backoff);

    @ManagedAttribute(description = "RedeliveryPolicy for allow redelivery while stopping")
    Boolean getAllowRedeliveryWhileStopping();

    @ManagedAttribute(description = "RedeliveryPolicy for allow redelivery while stopping")
    void setAllowRedeliveryWhileStopping(Boolean allow);

}