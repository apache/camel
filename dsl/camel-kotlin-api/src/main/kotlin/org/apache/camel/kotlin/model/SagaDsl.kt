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
import org.apache.camel.kotlin.StepsDsl
import org.apache.camel.kotlin.UriDsl
import org.apache.camel.model.SagaCompletionMode
import org.apache.camel.model.SagaDefinition
import org.apache.camel.model.SagaPropagation
import org.apache.camel.saga.CamelSagaService
import java.time.Duration

@CamelDslMarker
class SagaDsl(
    val def: SagaDefinition
) : OptionalIdentifiedDsl(def) {

    fun outputs(i: StepsDsl.() -> Unit) {
        StepsDsl(def).apply(i)
    }

    fun compensation(i: UriDsl.() -> Unit) {
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        def.compensation(uri)
    }

    fun completion(i: UriDsl.() -> Unit) {
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        def.completion(uri)
    }

    fun propagation(propagation: SagaPropagation) {
        def.propagation(propagation)
    }

    fun propagation(propagation: String) {
        def.propagation = propagation
    }

    fun sagaService(sagaService: CamelSagaService) {
        def.sagaService(sagaService)
    }

    fun sagaService(sagaService: String) {
        def.sagaService(sagaService)
    }

    fun completionMode(completionMode: SagaCompletionMode) {
        def.completionMode(completionMode)
    }

    fun completionMode(completionMode: String) {
        def.completionMode(completionMode)
    }

    fun option(option: String, expression: Expression) {
        def.option(option, expression)
    }

    fun timeout(timeout: Duration) {
        def.timeout(timeout)
    }

    fun timeout(timeout: String) {
        def.timeout(timeout)
    }
}