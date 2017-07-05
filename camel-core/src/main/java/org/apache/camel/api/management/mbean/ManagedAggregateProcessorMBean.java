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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedAggregateProcessorMBean extends ManagedProcessorMBean {

    @ManagedAttribute(description = "The language for the expression")
    String getCorrelationExpressionLanguage();

    @ManagedAttribute(description = "Correlation Expression")
    String getCorrelationExpression();

    @ManagedAttribute(description = "Completion timeout in millis")
    long getCompletionTimeout();

    @ManagedAttribute(description = "The language for the expression")
    String getCompletionTimeoutLanguage();

    @ManagedAttribute(description = "Completion timeout expression")
    String getCompletionTimeoutExpression();

    @ManagedAttribute(description = "Completion interval in millis")
    long getCompletionInterval();

    @ManagedAttribute(description = "Completion timeout checker interval in millis")
    long getCompletionTimeoutCheckerInterval();

    @ManagedAttribute(description = "Completion size")
    int getCompletionSize();

    @ManagedAttribute(description = "The language for the expression")
    String getCompletionSizeExpressionLanguage();

    @ManagedAttribute(description = "Completion size expression")
    String getCompletionSizeExpression();

    @ManagedAttribute(description = "Complete from batch consumers")
    boolean isCompletionFromBatchConsumer();

    @ManagedAttribute(description = "Ignore invalid correlation keys")
    boolean isIgnoreInvalidCorrelationKeys();

    @ManagedAttribute(description = "Whether to close the correlation group on completion if this value is > 0.")
    Integer getCloseCorrelationKeyOnCompletion();

    @ManagedAttribute(description = "Parallel mode")
    boolean isParallelProcessing();

    @ManagedAttribute(description = "Optimistic locking")
    boolean isOptimisticLocking();

    @ManagedAttribute(description = "Whether or not to eager check for completion when a new incoming Exchange has been received")
    boolean isEagerCheckCompletion();

    @ManagedAttribute(description = "The language for the predicate")
    String getCompletionPredicateLanguage();

    @ManagedAttribute(description = "A Predicate to indicate when an aggregated exchange is complete")
    String getCompletionPredicate();

    @ManagedAttribute(description = "Whether or not exchanges which complete due to a timeout should be discarded")
    boolean isDiscardOnCompletionTimeout();

    @ManagedAttribute(description = "Indicates to complete all current aggregated exchanges when the context is stopped")
    boolean isForceCompletionOnStop();

    @ManagedAttribute(description = "Indicates to wait to complete all current and partial (pending) aggregated exchanges when the context is stopped")
    boolean isCompleteAllOnStop();

    @ManagedAttribute(description = "Number of completed exchanges which are currently in-flight")
    int getInProgressCompleteExchanges();

    @ManagedOperation(description = "Number of groups currently in the aggregation repository")
    int aggregationRepositoryGroups();

    @ManagedOperation(description = "To force completing a specific group by its key")
    int forceCompletionOfGroup(String key);

    @ManagedOperation(description = "To force complete of all groups")
    int forceCompletionOfAllGroups();

    @ManagedAttribute(description = "Current number of closed correlation keys in the memory cache")
    int getClosedCorrelationKeysCacheSize();

    @ManagedOperation(description = "Clear all the closed correlation keys stored in the cache")
    void clearClosedCorrelationKeysCache();

    @ManagedAttribute(description = "Total number of exchanges arrived into the aggregator")
    long getTotalIn();

    @ManagedAttribute(description = "Total number of exchanges completed and outgoing from the aggregator")
    long getTotalCompleted();

    @ManagedAttribute(description = "Total number of exchanged completed by completion size trigger")
    long getCompletedBySize();

    @ManagedAttribute(description = "Total number of exchanged completed by completion aggregation strategy trigger")
    long getCompletedByStrategy();

    @ManagedAttribute(description = "Total number of exchanged completed by completion interval (timeout) trigger")
    long getCompletedByInterval();

    @ManagedAttribute(description = "Total number of exchanged completed by completion timeout trigger")
    long getCompletedByTimeout();

    @ManagedAttribute(description = "Total number of exchanged completed by completion predicate trigger")
    long getCompletedByPredicate();

    @ManagedAttribute(description = "Total number of exchanged completed by completion batch consumer trigger")
    long getCompletedByBatchConsumer();

    @ManagedAttribute(description = "Total number of exchanged completed by completion force trigger")
    long getCompletedByForce();

    @ManagedOperation(description = " Reset the statistics counters")
    void resetStatistics();

    @ManagedAttribute(description = "Sets whether statistics is enabled")
    boolean isStatisticsEnabled();

    @ManagedAttribute(description = "Sets whether statistics is enabled")
    void setStatisticsEnabled(boolean statisticsEnabled);

}