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

import org.apache.camel.Expression
import org.apache.camel.LoggingLevel
import org.apache.camel.Predicate
import org.apache.camel.Processor
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.StepsDsl
import org.apache.camel.model.OnExceptionDefinition

@CamelDslMarker
class OnExceptionDsl(
    val def: OnExceptionDefinition
) {

    fun outputs(i: StepsDsl.() -> Unit) {
        StepsDsl(def).apply(i)
    }

    fun handled(handled: Boolean) {
        def.handled(handled)
    }

    fun handled(handled: Predicate) {
        def.handled(handled)
    }

    fun handled(handled: Expression) {
        def.handled(handled)
    }

    fun continued(continued: Boolean) {
        def.continued(continued)
    }

    fun continued(continued: Predicate) {
        def.continued(continued)
    }

    fun continued(continued: Expression) {
        def.continued(continued)
    }

    fun onWhen(onWhen: Predicate) {
        def.onWhen(onWhen)
    }

    fun retryWhile(retryWhile: Predicate) {
        def.retryWhile(retryWhile)
    }

    fun backOffMultiplier(backOffMultiplier: Double) {
        def.backOffMultiplier(backOffMultiplier)
    }

    fun backOffMultiplier(backOffMultiplier: String) {
        def.backOffMultiplier(backOffMultiplier)
    }

    fun collisionAvoidanceFactor(collisionAvoidanceFactor: Double) {
        def.collisionAvoidanceFactor(collisionAvoidanceFactor)
    }

    fun collisionAvoidanceFactor(collisionAvoidanceFactor: String) {
        def.collisionAvoidanceFactor(collisionAvoidanceFactor)
    }

    fun collisionAvoidancePercent(collisionAvoidancePercent: Double) {
        def.collisionAvoidancePercent(collisionAvoidancePercent)
    }

    fun redeliveryDelay(redeliveryDelay: Long) {
        def.redeliveryDelay(redeliveryDelay)
    }

    fun redeliveryDelay(redeliveryDelay: String) {
        def.redeliveryDelay(redeliveryDelay)
    }

    fun asyncDelayedRedelivery() {
        def.asyncDelayedRedelivery()
    }

    fun retriesExhaustedLogLevel(retriesExhaustedLogLevel: LoggingLevel) {
        def.retriesExhaustedLogLevel(retriesExhaustedLogLevel)
    }

    fun retryAttemptedLogLevel(retryAttemptedLogLevel: LoggingLevel) {
        def.retryAttemptedLogLevel(retryAttemptedLogLevel)
    }

    fun logStackTrace(logStackTrace: Boolean) {
        def.logStackTrace(logStackTrace)
    }

    fun logStackTrace(logStackTrace: String) {
        def.logStackTrace(logStackTrace)
    }

    fun logRetryStackTrace(logRetryStackTrace: Boolean) {
        def.logRetryStackTrace(logRetryStackTrace)
    }

    fun logRetryStackTrace(logRetryStackTrace: String) {
        def.logRetryStackTrace(logRetryStackTrace)
    }

    fun logHandled(logHandled: Boolean) {
        def.logHandled(logHandled)
    }

    fun logHandled(logHandled: String) {
        def.logHandled(logHandled)
    }

    fun logNewException(logNewException: Boolean) {
        def.logNewException(logNewException)
    }

    fun logNewException(logNewException: String) {
        def.logNewException(logNewException)
    }

    fun logContinued(logContinued: Boolean) {
        def.logContinued(logContinued)
    }

    fun logContinued(logContinued: String) {
        def.logContinued(logContinued)
    }

    fun logRetryAttempted(logRetryAttempted: Boolean) {
        def.logRetryAttempted(logRetryAttempted)
    }

    fun logRetryAttempted(logRetryAttempted: String) {
        def.logRetryAttempted(logRetryAttempted)
    }

    fun logExhausted(logExhausted: Boolean) {
        def.logExhausted(logExhausted)
    }

    fun logExhausted(logExhausted: String) {
        def.logExhausted(logExhausted)
    }

    fun logExhaustedMessageHistory(logExhaustedMessageHistory: Boolean) {
        def.logExhaustedMessageHistory(logExhaustedMessageHistory)
    }

    fun logExhaustedMessageHistory(logExhaustedMessageHistory: String) {
        def.logExhaustedMessageHistory(logExhaustedMessageHistory)
    }

    fun logExhaustedMessageBody(logExhaustedMessageBody: Boolean) {
        def.logExhaustedMessageBody(logExhaustedMessageBody)
    }

    fun logExhaustedMessageBody(logExhaustedMessageBody: String) {
        def.logExhaustedMessageBody(logExhaustedMessageBody)
    }

    fun maximumRedeliveries(maximumRedeliveries: Int) {
        def.maximumRedeliveries(maximumRedeliveries)
    }

    fun maximumRedeliveries(maximumRedeliveries: String) {
        def.maximumRedeliveries(maximumRedeliveries)
    }

    fun useCollisionAvoidance() {
        def.useCollisionAvoidance()
    }

    fun useExponentialBackOff() {
        def.useExponentialBackOff()
    }

    fun maximumRedeliveryDelay(maximumRedeliveryDelay: Long) {
        def.maximumRedeliveryDelay(maximumRedeliveryDelay)
    }

    fun maximumRedeliveryDelay(maximumRedeliveryDelay: String) {
        def.maximumRedeliveryDelay(maximumRedeliveryDelay)
    }

    fun redeliveryPolicyRef(redeliveryPolicyRef: String) {
        def.redeliveryPolicyRef(redeliveryPolicyRef)
    }

    fun delayPattern(delayPattern: String) {
        def.delayPattern(delayPattern)
    }

    fun useOriginalMessage() {
        def.useOriginalMessage()
    }

    fun useOriginalBody() {
        def.useOriginalBody()
    }

    fun onRedelivery(onRedelivery: Processor) {
        def.onRedelivery(onRedelivery)
    }

    fun onRedeliveryRef(onRedeliveryRef: String) {
        def.onRedeliveryRef(onRedeliveryRef)
    }

    fun onExceptionOccurred(onExceptionOccurred: Processor) {
        def.onExceptionOccurred(onExceptionOccurred)
    }

    fun onExceptionOccurredRef(onExceptionOccurredRef: String) {
        def.onExceptionOccurredRef(onExceptionOccurredRef)
    }
}