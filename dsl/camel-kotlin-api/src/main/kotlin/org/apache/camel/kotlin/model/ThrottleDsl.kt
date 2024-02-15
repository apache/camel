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
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.ThrottleDefinition
import java.util.concurrent.ExecutorService

@CamelDslMarker
class ThrottleDsl(
    val def: ThrottleDefinition
) : OptionalIdentifiedDsl(def) {

    fun mode(mode: String) {
        def.mode(mode)
    }

    fun correlationExpression(correlationExpression: Expression) {
        def.correlationExpression(correlationExpression)
    }

    fun callerRunsWhenRejected(callerRunsWhenRejected: Boolean) {
        def.callerRunsWhenRejected(callerRunsWhenRejected)
    }

    fun callerRunsWhenRejected(callerRunsWhenRejected: String) {
        def.callerRunsWhenRejected(callerRunsWhenRejected)
    }

    fun asyncDelayed(asyncDelayed: Boolean) {
        def.asyncDelayed(asyncDelayed)
    }

    fun asyncDelayed(asyncDelayed: String) {
        def.asyncDelayed(asyncDelayed)
    }

    fun rejectExecution(rejectExecution: Boolean) {
        def.rejectExecution(rejectExecution)
    }

    fun rejectExecution(rejectExecution: String) {
        def.rejectExecution(rejectExecution)
    }

    fun timePeriodMillis(timePeriodMillis: String) {
        def.timePeriodMillis = timePeriodMillis
    }

    fun timePeriodMillis(timePeriodMillis: Long) {
        def.timePeriodMillis = timePeriodMillis.toString()
    }

    fun executorService(executorService: ExecutorService) {
        def.executorService(executorService)
    }

    fun executorService(executorService: String) {
        def.executorService(executorService)
    }
}