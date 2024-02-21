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

import org.apache.camel.ExchangePattern
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.ToDynamicDefinition

@CamelDslMarker
open class ToDynamicDsl(
    open val def: ToDynamicDefinition
) : OptionalIdentifiedDsl(def) {

    fun cacheSize(cacheSize: Int) {
        def.cacheSize(cacheSize)
    }

    fun cacheSize(cacheSize: String) {
        def.cacheSize(cacheSize)
    }

    fun pattern(pattern: ExchangePattern) {
        def.pattern(pattern)
    }

    fun pattern(pattern: String) {
        def.pattern(pattern)
    }

    fun ignoreInvalidEndpoint(ignoreInvalidEndpoint: Boolean) {
        def.ignoreInvalidEndpoint(ignoreInvalidEndpoint)
    }

    fun ignoreInvalidEndpoint(ignoreInvalidEndpoint: String) {
        def.ignoreInvalidEndpoint(ignoreInvalidEndpoint)
    }

    fun allowOptimisedComponents(allowOptimisedComponents: Boolean) {
        def.allowOptimisedComponents(allowOptimisedComponents)
    }

    fun allowOptimisedComponents(allowOptimisedComponents: String) {
        def.allowOptimisedComponents(allowOptimisedComponents)
    }

    fun autoStartComponents(autoStartComponents: Boolean) {
        def.autoStartComponents(autoStartComponents.toString())
    }

    fun autoStartComponents(autoStartComponents: String) {
        def.autoStartComponents(autoStartComponents)
    }
}