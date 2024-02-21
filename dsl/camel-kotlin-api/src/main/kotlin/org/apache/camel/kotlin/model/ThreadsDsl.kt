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
import org.apache.camel.model.ThreadsDefinition
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@CamelDslMarker
class ThreadsDsl(
    val def: ThreadsDefinition
) : OptionalIdentifiedDsl(def) {

    fun executorService(executorService: String) {
        def.executorService(executorService)
    }

    fun executorService(executorService: ExecutorService) {
        def.executorService(executorService)
    }

    fun poolSize(poolSize: Int) {
        def.poolSize(poolSize)
    }

    fun poolSize(poolSize: String) {
        def.poolSize(poolSize)
    }

    fun maxPoolSize(poolSize: Int) {
        def.maxPoolSize(poolSize)
    }

    fun maxPoolSize(poolSize: String) {
        def.maxPoolSize(poolSize)
    }

    fun keepAliveTime(keepAliveTime: Long) {
        def.keepAliveTime(keepAliveTime)
    }

    fun keepAliveTime(keepAliveTime: String) {
        def.keepAliveTime(keepAliveTime)
    }

    fun timeUnit(timeUnit: TimeUnit) {
        def.timeUnit(timeUnit)
    }

    fun timeUnit(timeUnit: String) {
        def.timeUnit(timeUnit)
    }

    fun maxQueueSize(maxQueueSize: Int) {
        def.maxQueueSize(maxQueueSize)
    }

    fun maxQueueSize(maxQueueSize: String) {
        def.maxQueueSize(maxQueueSize)
    }

    fun rejectedPolicy(rejectedPolicy: ThreadPoolRejectedPolicy) {
        def.rejectedPolicy(rejectedPolicy)
    }

    fun rejectedPolicy(rejectedPolicy: String) {
        def.rejectedPolicy(rejectedPolicy)
    }

    fun threadName(threadName: String) {
        def.threadName(threadName)
    }

    fun callerRunsWhenRejected(callerRunsWhenRejected: Boolean) {
        def.callerRunsWhenRejected(callerRunsWhenRejected)
    }

    fun callerRunsWhenRejected(callerRunsWhenRejected: String) {
        def.callerRunsWhenRejected(callerRunsWhenRejected)
    }

    fun allowCoreThreadTimeOut(allowCoreThreadTimeOut: Boolean) {
        def.allowCoreThreadTimeOut(allowCoreThreadTimeOut)
    }

    fun allowCoreThreadTimeOut(allowCoreThreadTimeOut: String) {
        def.allowCoreThreadTimeOut(allowCoreThreadTimeOut)
    }
}