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
package org.apache.camel.kotlin.model

import org.apache.camel.AggregationStrategy
import org.apache.camel.Expression
import org.apache.camel.Predicate
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.AggregateDefinition
import org.apache.camel.model.OptimisticLockRetryPolicyDefinition
import org.apache.camel.processor.aggregate.AggregateController
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy
import org.apache.camel.spi.AggregationRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

@CamelDslMarker
class AggregateDsl(
    val def: AggregateDefinition
) : OptionalIdentifiedDsl(def) {

    fun eagerCheckCompletion(eagerCheckCompletion: Boolean) {
        def.eagerCheckCompletion = eagerCheckCompletion.toString()
    }

    fun eagerCheckCompletion(eagerCheckCompletion: String) {
        def.eagerCheckCompletion = eagerCheckCompletion
    }

    fun ignoreInvalidCorrelationKeys(ignoreInvalidCorrelationKeys: Boolean) {
        def.ignoreInvalidCorrelationKeys = ignoreInvalidCorrelationKeys.toString()
    }

    fun ignoreInvalidCorrelationKeys(ignoreInvalidCorrelationKeys: String) {
        def.ignoreInvalidCorrelationKeys = ignoreInvalidCorrelationKeys
    }

    fun closeCorrelationKeyOnCompletion(capacity: Int) {
        def.closeCorrelationKeyOnCompletion(capacity)
    }

    fun closeCorrelationKeyOnCompletion(capacity: String) {
        def.closeCorrelationKeyOnCompletion = capacity
    }

    fun discardOnCompletionTimeout(discardOnCompletionTimeout: Boolean) {
        def.discardOnCompletionTimeout = discardOnCompletionTimeout.toString()
    }

    fun discardOnCompletionTimeout(discardOnCompletionTimeout: String) {
        def.discardOnCompletionTimeout = discardOnCompletionTimeout
    }

    fun discardOnAggregationFailure(discardOnAggregationFailure: Boolean) {
        def.discardOnAggregationFailure = discardOnAggregationFailure.toString()
    }

    fun discardOnAggregationFailure(discardOnAggregationFailure: String) {
        def.discardOnAggregationFailure = discardOnAggregationFailure
    }

    fun completionFromBatchConsumer(completionFromBatchConsumer: Boolean) {
        def.completionFromBatchConsumer = completionFromBatchConsumer.toString()
    }

    fun completionFromBatchConsumer(completionFromBatchConsumer: String) {
        def.completionFromBatchConsumer = completionFromBatchConsumer
    }

    fun completionOnNewCorrelationGroup(completionOnNewCorrelationGroup: Boolean) {
        def.completionOnNewCorrelationGroup = completionOnNewCorrelationGroup.toString()
    }

    fun completionOnNewCorrelationGroup(completionOnNewCorrelationGroup: String) {
        def.completionOnNewCorrelationGroup = completionOnNewCorrelationGroup
    }

    fun completionSize(completionSize: Int) {
        def.completionSize(completionSize)
    }

    fun completionSize(completionSize: String) {
        def.completionSize(completionSize)
    }

    fun completionSize(completionSize: Expression) {
        def.completionSize(completionSize)
    }

    fun completionInterval(completionInterval: Long)  {
        def.completionInterval(completionInterval)
    }

    fun completionInterval(completionInterval: String)  {
        def.completionInterval(completionInterval)
    }

    fun completionTimeout(completionTimeout: Long) {
        def.completionTimeout(completionTimeout)
    }

    fun completionTimeout(completionTimeout: String) {
        def.completionTimeout(completionTimeout)
    }

    fun completionTimeout(completionTimeout: Expression) {
        def.completionTimeout(completionTimeout)
    }

    fun completionTimeoutCheckerInterval(completionTimeoutCheckerInterval: Long) {
        def.completionTimeoutCheckerInterval(completionTimeoutCheckerInterval)
    }

    fun completionTimeoutCheckerInterval(completionTimeoutCheckerInterval: String) {
        def.completionTimeoutCheckerInterval = completionTimeoutCheckerInterval
    }

    fun aggregationStrategy(aggregationStrategy: AggregationStrategy) {
        def.aggregationStrategy(aggregationStrategy)
    }

    fun aggregationStrategy(aggregationStrategy: String) {
        def.aggregationStrategy(aggregationStrategy)
    }

    fun aggregationStrategyMethodName(aggregationStrategyMethodName: String) {
        def.aggregationStrategyMethodName(aggregationStrategyMethodName)
    }

    fun aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull: Boolean) {
        def.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull.toString()
    }

    fun aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull: String) {
        def.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull
    }

    fun aggregationRepository(aggregationRepository: AggregationRepository) {
        def.aggregationRepository(aggregationRepository)
    }

    fun aggregationRepository(aggregationRepository: String) {
        def.aggregationRepository(aggregationRepository)
    }

    fun completionPredicate(completionPredicate: Predicate) {
        def.completionPredicate(completionPredicate)
    }

    fun completion(completion: Predicate) {
        completionPredicate(completion)
    }

    fun forceCompletionOnStop(forceCompletionOnStop: Boolean) {
        def.forceCompletionOnStop = forceCompletionOnStop.toString()
    }

    fun forceCompletionOnStop(forceCompletionOnStop: String) {
        def.forceCompletionOnStop = forceCompletionOnStop
    }

    fun completeAllOnStop(completeAllOnStop: Boolean) {
        def.completeAllOnStop = completeAllOnStop.toString()
    }

    fun completeAllOnStop(completeAllOnStop: String) {
        def.completeAllOnStop = completeAllOnStop
    }

    fun parallelProcessing(parallelProcessing: Boolean) {
        def.parallelProcessing(parallelProcessing)
    }

    fun parallelProcessing(parallelProcessing: String) {
        def.parallelProcessing = parallelProcessing
    }

    fun optimisticLocking(optimisticLocking: Boolean) {
        def.optimisticLocking = optimisticLocking.toString()
    }

    fun optimisticLocking(optimisticLocking: String) {
        def.optimisticLocking = optimisticLocking
    }

    fun optimisticLockRetryPolicy(optimisticLockRetryPolicy: OptimisticLockRetryPolicy) {
        def.optimisticLockRetryPolicy(optimisticLockRetryPolicy)
    }

    fun optimisticLockRetryPolicy(i: OptimisticLockRetryPolicyDsl.() -> Unit) {
        val optimisticLockRetryPolicyDef = OptimisticLockRetryPolicyDefinition()
        OptimisticLockRetryPolicyDsl(optimisticLockRetryPolicyDef).apply(i)
        def.optimisticLockRetryPolicyDefinition = optimisticLockRetryPolicyDef
    }

    fun executorService(executorService: ExecutorService) {
        def.executorService(executorService)
    }

    fun executorService(executorService: String) {
        def.executorService(executorService)
    }

    fun timeoutCheckerExecutorService(timeoutCheckerExecutorService: ScheduledExecutorService) {
        def.timeoutCheckerExecutorService(timeoutCheckerExecutorService)
    }

    fun timeoutCheckerExecutorService(timeoutCheckerExecutorService: String) {
        def.timeoutCheckerExecutorService(timeoutCheckerExecutorService)
    }

    fun aggregateController(aggregateController: AggregateController) {
        def.aggregateController(aggregateController)
    }

    fun aggregateController(aggregateController: String) {
        def.aggregateController(aggregateController)
    }
}

@CamelDslMarker
class OptimisticLockRetryPolicyDsl(
    val def: OptimisticLockRetryPolicyDefinition
) {

    fun maximumRetries(maximumRetries: Int) {
        def.maximumRetries = maximumRetries.toString()
    }

    fun maximumRetries(maximumRetries: String) {
        def.maximumRetries = maximumRetries
    }

    fun retryDelay(retryDelay: String) {
        def.retryDelay = retryDelay
    }

    fun maximumRetryDelay(maximumRetryDelay: String) {
        def.maximumRetryDelay = maximumRetryDelay
    }

    fun exponentialBackOff(exponentialBackOff: Boolean) {
        def.exponentialBackOff = exponentialBackOff.toString()
    }

    fun exponentialBackOff(exponentialBackOff: String) {
        def.exponentialBackOff = exponentialBackOff
    }

    fun randomBackOff(randomBackOff: Boolean) {
        def.randomBackOff = randomBackOff.toString()
    }

    fun randomBackOff(randomBackOff: String) {
        def.randomBackOff = randomBackOff
    }
}