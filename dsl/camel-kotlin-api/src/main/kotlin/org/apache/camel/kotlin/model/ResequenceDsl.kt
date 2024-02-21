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

import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.ResequenceDefinition
import org.apache.camel.model.config.BatchResequencerConfig
import org.apache.camel.model.config.StreamResequencerConfig
import org.apache.camel.processor.resequencer.ExpressionResultComparator
import org.apache.camel.util.TimeUtils
import java.time.Duration

@CamelDslMarker
class ResequenceDsl(
    val def: ResequenceDefinition
) : OptionalIdentifiedDsl(def) {

    fun batch(i: BatchResequenceDsl.() -> Unit = {}) {
        val config = BatchResequencerConfig.getDefault()
        BatchResequenceDsl(config).apply(i)
        def.batch(config)
    }

    fun stream(i: StreamResequenceDsl.() -> Unit = {}) {
        val config = StreamResequencerConfig.getDefault()
        StreamResequenceDsl(config).apply(i)
        def.stream(config)
    }
}

@CamelDslMarker
class BatchResequenceDsl(
    val def: BatchResequencerConfig
) {

    fun batchSize(batchSize: Int) {
        def.batchSize = batchSize.toString()
    }

    fun batchSize(batchSize: String) {
        def.batchSize = batchSize
    }

    fun batchTimeout(batchTimeout: Duration) {
        def.batchTimeout = TimeUtils.printDuration(batchTimeout, true)
    }

    fun batchTimeout(batchTimeout: Long) {
        def.batchTimeout = batchTimeout.toString()
    }

    fun batchTimeout(batchTimeout: String) {
        def.batchTimeout = batchTimeout
    }

    fun allowDuplicates(allowDuplicates: Boolean) {
        def.allowDuplicates = allowDuplicates.toString()
    }

    fun allowDuplicates(allowDuplicates: String) {
        def.allowDuplicates = allowDuplicates
    }

    fun reverse(reverse: Boolean) {
        def.reverse = reverse.toString()
    }

    fun reverse(reverse: String) {
        def.reverse = reverse
    }

    fun ignoreInvalidExchanges(ignoreInvalidExchanges: Boolean) {
        def.ignoreInvalidExchanges = ignoreInvalidExchanges.toString()
    }

    fun ignoreInvalidExchanges(ignoreInvalidExchanges: String) {
        def.ignoreInvalidExchanges = ignoreInvalidExchanges
    }
}

@CamelDslMarker
class StreamResequenceDsl(
    val def: StreamResequencerConfig
) {

    fun capacity(capacity: Int) {
        def.capacity = capacity.toString()
    }

    fun capacity(capacity: String) {
        def.capacity = capacity
    }

    fun timeout(timeout: Duration) {
        def.timeout = TimeUtils.printDuration(timeout, true)
    }

    fun timeout(timeout: Long) {
        def.timeout = timeout.toString()
    }

    fun timeout(timeout: String) {
        def.timeout = timeout
    }

    fun deliveryAttemptInterval(deliveryAttemptInterval: Duration) {
        def.deliveryAttemptInterval = TimeUtils.printDuration(deliveryAttemptInterval, true)
    }

    fun deliveryAttemptInterval(deliveryAttemptInterval: Long) {
        def.deliveryAttemptInterval = deliveryAttemptInterval.toString()
    }

    fun deliveryAttemptInterval(deliveryAttemptInterval: String) {
        def.deliveryAttemptInterval = deliveryAttemptInterval
    }

    fun ignoreInvalidExchanges(ignoreInvalidExchanges: Boolean) {
        def.ignoreInvalidExchanges = ignoreInvalidExchanges.toString()
    }

    fun ignoreInvalidExchanges(ignoreInvalidExchanges: String) {
        def.ignoreInvalidExchanges = ignoreInvalidExchanges
    }

    fun rejectOld(rejectOld: Boolean) {
        def.rejectOld = rejectOld.toString()
    }

    fun rejectOld(rejectOld: String) {
        def.rejectOld = rejectOld
    }

    fun comparator(comparator: ExpressionResultComparator) {
        def.comparatorBean = comparator
    }

    fun comparator(comparator: String) {
        def.comparator = comparator
    }
}