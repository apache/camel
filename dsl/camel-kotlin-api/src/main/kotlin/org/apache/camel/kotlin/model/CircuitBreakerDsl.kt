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
import org.apache.camel.kotlin.StepsDsl
import org.apache.camel.model.CircuitBreakerDefinition
import org.apache.camel.model.FaultToleranceConfigurationDefinition
import org.apache.camel.model.Resilience4jConfigurationDefinition

@CamelDslMarker
class CircuitBreakerDsl(
    val def: CircuitBreakerDefinition
) : OptionalIdentifiedDsl(def) {

    fun resilience4jConfiguration(i: Resilience4jConfigurationDsl.() -> Unit) {
        val resilience4jDef = def.resilience4jConfiguration()
        Resilience4jConfigurationDsl(resilience4jDef).apply(i)
    }

    fun faultToleranceConfiguration(i: FaultToleranceConfigurationDsl.() -> Unit) {
        val faultToleranceDef = def.faultToleranceConfiguration()
        FaultToleranceConfigurationDsl(faultToleranceDef).apply(i)
    }

    fun configuration(configuration: String) {
        def.configuration(configuration)
    }

    fun outputs(i: StepsDsl.() -> Unit) {
        StepsDsl(def).apply(i)
    }

    fun onFallback(i: StepsDsl.() -> Unit) {
        val fallbackDef = def.onFallback()
        StepsDsl(fallbackDef).apply(i)
        fallbackDef.end()
    }

    fun onFallbackViaNetwork(i: StepsDsl.() -> Unit) {
        val fallbackDef = def.onFallbackViaNetwork()
        StepsDsl(fallbackDef).apply(i)
        fallbackDef.end()
    }
}

@CamelDslMarker
class Resilience4jConfigurationDsl(
    val def: Resilience4jConfigurationDefinition
) {

    fun circuitBreaker(circuitBreaker: String) {
        def.circuitBreaker(circuitBreaker)
    }

    fun config(config: String) {
        def.config(config)
    }

    fun failureRateThreshold(failureRateThreshold: Float) {
        def.failureRateThreshold(failureRateThreshold)
    }

    fun failureRateThreshold(failureRateThreshold: String) {
        def.failureRateThreshold = failureRateThreshold
    }

    fun permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState: Int) {
        def.permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
    }

    fun permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState: String) {
        def.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState
    }

    fun throwExceptionWhenHalfOpenOrOpenState(throwExceptionWhenHalfOpenOrOpenState: Boolean) {
        def.throwExceptionWhenHalfOpenOrOpenState(throwExceptionWhenHalfOpenOrOpenState)
    }

    fun throwExceptionWhenHalfOpenOrOpenState(throwExceptionWhenHalfOpenOrOpenState: String) {
        def.throwExceptionWhenHalfOpenOrOpenState = throwExceptionWhenHalfOpenOrOpenState
    }

    fun slidingWindowSize(slidingWindowSize: Int) {
        def.slidingWindowSize(slidingWindowSize)
    }

    fun slidingWindowSize(slidingWindowSize: String) {
        def.slidingWindowSize = slidingWindowSize
    }

    fun slidingWindowType(slidingWindowType: String) {
        def.slidingWindowType(slidingWindowType)
    }

    fun minimumNumberOfCalls(minimumNumberOfCalls: Int) {
        def.minimumNumberOfCalls(minimumNumberOfCalls)
    }

    fun minimumNumberOfCalls(minimumNumberOfCalls: String) {
        def.minimumNumberOfCalls = minimumNumberOfCalls
    }

    fun writableStackTraceEnabled(writableStackTraceEnabled: Boolean) {
        def.writableStackTraceEnabled(writableStackTraceEnabled)
    }

    fun writableStackTraceEnabled(writableStackTraceEnabled: String) {
        def.writableStackTraceEnabled = writableStackTraceEnabled
    }

    fun waitDurationInOpenState(waitDurationInOpenState: Int) {
        def.waitDurationInOpenState(waitDurationInOpenState)
    }

    fun waitDurationInOpenState(waitDurationInOpenState: String) {
        def.waitDurationInOpenState = waitDurationInOpenState
    }

    fun automaticTransitionFromOpenToHalfOpenEnabled(automaticTransitionFromOpenToHalfOpenEnabled: Boolean) {
        def.automaticTransitionFromOpenToHalfOpenEnabled(automaticTransitionFromOpenToHalfOpenEnabled)
    }

    fun automaticTransitionFromOpenToHalfOpenEnabled(automaticTransitionFromOpenToHalfOpenEnabled: String) {
        def.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled
    }

    fun slowCallRateThreshold(slowCallRateThreshold: Float) {
        def.slowCallRateThreshold(slowCallRateThreshold)
    }

    fun slowCallRateThreshold(slowCallRateThreshold: String) {
        def.slowCallRateThreshold = slowCallRateThreshold
    }

    fun slowCallDurationThreshold(slowCallDurationThreshold: Int) {
        def.slowCallDurationThreshold(slowCallDurationThreshold)
    }

    fun slowCallDurationThreshold(slowCallDurationThreshold: String) {
        def.slowCallDurationThreshold = slowCallDurationThreshold
    }

    fun bulkheadEnabled(bulkheadEnabled: Boolean) {
        def.bulkheadEnabled(bulkheadEnabled)
    }

    fun bulkheadEnabled(bulkheadEnabled: String) {
        def.bulkheadEnabled = bulkheadEnabled
    }

    fun bulkheadMaxConcurrentCalls(bulkheadMaxConcurrentCalls: Int) {
        def.bulkheadMaxConcurrentCalls(bulkheadMaxConcurrentCalls)
    }

    fun bulkheadMaxConcurrentCalls(bulkheadMaxConcurrentCalls: String) {
        def.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls
    }

    fun bulkheadMaxWaitDuration(bulkheadMaxWaitDuration: Int) {
        def.bulkheadMaxWaitDuration(bulkheadMaxWaitDuration)
    }

    fun bulkheadMaxWaitDuration(bulkheadMaxWaitDuration: String) {
        def.bulkheadMaxWaitDuration = bulkheadMaxWaitDuration
    }

    fun timeoutEnabled(timeoutEnabled: Boolean) {
        def.timeoutEnabled(timeoutEnabled)
    }

    fun timeoutEnabled(timeoutEnabled: String) {
        def.timeoutEnabled = timeoutEnabled
    }

    fun timeoutExecutorService(timeoutExecutorService: String) {
        def.timeoutExecutorService(timeoutExecutorService)
    }

    fun timeoutDuration(timeoutDuration: Int) {
        def.timeoutDuration(timeoutDuration)
    }

    fun timeoutDuration(timeoutDuration: String) {
        def.timeoutDuration = timeoutDuration
    }

    fun timeoutCancelRunningFuture(timeoutCancelRunningFuture: Boolean) {
        def.timeoutCancelRunningFuture(timeoutCancelRunningFuture)
    }

    fun timeoutCancelRunningFuture(timeoutCancelRunningFuture: String) {
        def.timeoutCancelRunningFuture = timeoutCancelRunningFuture
    }
}

@CamelDslMarker
class FaultToleranceConfigurationDsl(
    val def: FaultToleranceConfigurationDefinition
) {

    fun circuitBreaker(circuitBreaker: String) {
        def.circuitBreaker(circuitBreaker)
    }

    fun delay(delay: Long) {
        def.delay(delay)
    }

    fun delay(delay: String) {
        def.delay(delay)
    }

    fun successThreshold(successThreshold: Int) {
        def.successThreshold(successThreshold)
    }

    fun successThreshold(successThreshold: String) {
        def.successThreshold = successThreshold
    }

    fun requestVolumeThreshold(requestVolumeThreshold: Int) {
        def.requestVolumeThreshold(requestVolumeThreshold)
    }

    fun requestVolumeThreshold(requestVolumeThreshold: String) {
        def.requestVolumeThreshold = requestVolumeThreshold
    }

    fun failureRatio(failureRatio: Int) {
        def.failureRatio(failureRatio)
    }

    fun failureRatio(failureRatio: String) {
        def.failureRatio = failureRatio
    }

    fun timeoutEnabled(timeoutEnabled: Boolean) {
        def.timeoutEnabled(timeoutEnabled)
    }

    fun timeoutEnabled(timeoutEnabled: String) {
        def.timeoutEnabled = timeoutEnabled
    }

    fun timeoutDuration(timeoutDuration: Long) {
        def.timeoutDuration(timeoutDuration)
    }

    fun timeoutDuration(timeoutDuration: String) {
        def.timeoutDuration(timeoutDuration)
    }

    fun timeoutPoolSize(timeoutPoolSize: Int) {
        def.timeoutPoolSize(timeoutPoolSize)
    }

    fun timeoutPoolSize(timeoutPoolSize: String) {
        def.timeoutPoolSize = timeoutPoolSize
    }

    fun timeoutScheduledExecutorService(timeoutScheduledExecutorService: String) {
        def.timeoutScheduledExecutorService(timeoutScheduledExecutorService)
    }

    fun bulkheadEnabled(bulkheadEnabled: Boolean) {
        def.bulkheadEnabled(bulkheadEnabled)
    }

    fun bulkheadEnabled(bulkheadEnabled: String) {
        def.bulkheadEnabled = bulkheadEnabled
    }

    fun bulkheadMaxConcurrentCalls(bulkheadMaxConcurrentCalls: Int) {
        def.bulkheadMaxConcurrentCalls(bulkheadMaxConcurrentCalls)
    }

    fun bulkheadMaxConcurrentCalls(bulkheadMaxConcurrentCalls: String) {
        def.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls
    }

    fun bulkheadWaitingTaskQueue(bulkheadWaitingTaskQueue: Int) {
        def.bulkheadWaitingTaskQueue(bulkheadWaitingTaskQueue)
    }

    fun bulkheadWaitingTaskQueue(bulkheadWaitingTaskQueue: String) {
        def.bulkheadWaitingTaskQueue = bulkheadWaitingTaskQueue
    }

    fun bulkheadExecutorService(bulkheadExecutorService: String) {
        def.bulkheadExecutorService(bulkheadExecutorService)
    }
}