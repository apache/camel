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
import org.apache.camel.model.DelayDefinition
import java.util.concurrent.ExecutorService

@CamelDslMarker
class DelayDsl(
    val def: DelayDefinition
) : OptionalIdentifiedDsl(def) {

    fun asyncDelayed(asyncDelayed: Boolean) {
        def.asyncDelayed = asyncDelayed.toString()
    }

    fun asyncDelayed(asyncDelayed: String) {
        def.asyncDelayed = asyncDelayed
    }

    fun callerRunsWhenRejected(callerRunsWhenRejected: Boolean) {
        def.callerRunsWhenRejected = callerRunsWhenRejected.toString()
    }

    fun callerRunsWhenRejected(callerRunsWhenRejected: String) {
        def.callerRunsWhenRejected = callerRunsWhenRejected
    }

    fun executorService(executorService: ExecutorService) {
        def.executorService(executorService)
    }

    fun executorService(executorService: String) {
        def.executorService(executorService)
    }
}