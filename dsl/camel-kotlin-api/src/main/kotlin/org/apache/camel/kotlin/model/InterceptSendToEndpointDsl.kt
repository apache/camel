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

import org.apache.camel.Predicate
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.StepsDsl
import org.apache.camel.model.InterceptSendToEndpointDefinition

@CamelDslMarker
class InterceptSendToEndpointDsl(
    val def: InterceptSendToEndpointDefinition
) {

    fun outputs(i: StepsDsl.() -> Unit) {
        StepsDsl(def).apply(i)
    }

    fun onWhen(onWhen: Predicate) {
        def.`when`(onWhen)
    }

    fun skipSendToOriginalEndpoint(skipSendToOriginalEndpoint: Boolean) {
        def.skipSendToOriginalEndpoint = skipSendToOriginalEndpoint.toString()
    }

    fun skipSendToOriginalEndpoint(skipSendToOriginalEndpoint: String) {
        def.skipSendToOriginalEndpoint = skipSendToOriginalEndpoint.toString()
    }

    fun afterUri(afterUri: String) {
        def.afterUri(afterUri)
    }
}