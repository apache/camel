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
import org.apache.camel.Processor
import org.apache.camel.kotlin.StepsDsl
import org.apache.camel.model.MulticastDefinition
import java.util.concurrent.ExecutorService

class MulticastDsl(
    val def: MulticastDefinition
) : OptionalIdentifiedDsl(def) {

    fun outputs(i: StepsDsl.() -> Unit) {
        StepsDsl(def).apply(i)
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

    fun parallelProcessing(parallelProcessing: Boolean) {
        def.parallelProcessing = parallelProcessing.toString()
    }

    fun parallelProcessing(parallelProcessing: String) {
        def.parallelProcessing = parallelProcessing
    }

    fun synchronous(synchronous: Boolean) {
        def.synchronous(synchronous)
    }

    fun synchronous(synchronous: String) {
        def.synchronous(synchronous)
    }

    fun parallelAggregate(parallelAggregate: Boolean) {
        def.parallelAggregate(parallelAggregate)
    }

    fun parallelAggregate(parallelAggregate: String) {
        def.parallelAggregate(parallelAggregate)
    }

    fun streaming(streaming: Boolean) {
        def.streaming = streaming.toString()
    }

    fun streaming(streaming: String) {
        def.streaming = streaming
    }

    fun stopOnException(stopOnException: Boolean) {
        def.stopOnException = stopOnException.toString()
    }

    fun stopOnException(stopOnException: String) {
        def.stopOnException = stopOnException
    }

    fun executorService(executorService: ExecutorService) {
        def.executorService(executorService)
    }

    fun executorService(executorService: String) {
        def.executorService(executorService)
    }

    fun onPrepare(onPrepare: Processor) {
        def.onPrepare(onPrepare)
    }

    fun onPrepare(onPrepare: String) {
        def.onPrepare(onPrepare)
    }

    fun timeout(timeout: Long) {
        def.timeout(timeout)
    }

    fun timeout(timeout: String) {
        def.timeout(timeout)
    }

    fun shareUnitOfWork(shareUnitOfWork: Boolean) {
        def.shareUnitOfWork = shareUnitOfWork.toString()
    }

    fun shareUnitOfWork(shareUnitOfWork: String) {
        def.shareUnitOfWork = shareUnitOfWork
    }
}