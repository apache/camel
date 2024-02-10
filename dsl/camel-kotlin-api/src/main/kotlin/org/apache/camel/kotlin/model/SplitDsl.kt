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
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.SplitDefinition
import java.util.concurrent.ExecutorService

@CamelDslMarker
class SplitDsl(
    val def: SplitDefinition
) : OptionalIdentifiedDsl(def) {

    fun delimiter(delimiter: String) {
        def.delimiter(delimiter)
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
        def.aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull)
    }

    fun aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull: String) {
        def.aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull)
    }

    fun parallelProcessing(parallelProcessing: Boolean) {
        def.parallelProcessing(parallelProcessing)
    }

    fun parallelProcessing(parallelProcessing: String) {
        def.parallelProcessing(parallelProcessing)
    }

    fun parallelAggregate(parallelAggregate: Boolean) {
        def.parallelAggregate(parallelAggregate)
    }

    fun parallelAggregate(parallelAggregate: String) {
        def.parallelAggregate(parallelAggregate)
    }

    fun synchronous(synchronous: Boolean) {
        def.synchronous(synchronous)
    }

    fun synchronous(synchronous: String) {
        def.synchronous(synchronous)
    }

    fun streaming(streaming: Boolean) {
        def.streaming(streaming)
    }

    fun streaming(streaming: String) {
        def.streaming(streaming)
    }

    fun stopOnException(stopOnException: Boolean) {
        def.stopOnException(stopOnException)
    }

    fun stopOnException(stopOnException: String) {
        def.stopOnException(stopOnException)
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
        def.shareUnitOfWork(shareUnitOfWork)
    }

    fun shareUnitOfWork(shareUnitOfWork: String) {
        def.shareUnitOfWork(shareUnitOfWork)
    }
}