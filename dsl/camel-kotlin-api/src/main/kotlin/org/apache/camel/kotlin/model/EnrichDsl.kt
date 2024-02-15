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
import org.apache.camel.Expression
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl
import org.apache.camel.model.EnrichDefinition
import org.apache.camel.model.language.SimpleExpression

@CamelDslMarker
class EnrichDsl(
    val def: EnrichDefinition
) : OptionalIdentifiedDsl(def) {

    fun uri(i: UriDsl.() -> Unit) {
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        def.expression = SimpleExpression(uri)
    }

    fun uri(uri: Expression) {
        def.setExpression(uri)
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
        def.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull
    }

    fun aggregateOnException(aggregateOnException: Boolean) {
        def.aggregateOnException(aggregateOnException)
    }

    fun aggregateOnException(aggregateOnException: String) {
        def.aggregateOnException = aggregateOnException
    }

    fun shareUnitOfWork(shareUnitOfWork: Boolean) {
        def.shareUnitOfWork = shareUnitOfWork.toString()
    }

    fun shareUnitOfWork(shareUnitOfWork: String) {
        def.shareUnitOfWork = shareUnitOfWork
    }

    fun cacheSize(cacheSize: Int) {
        def.cacheSize = cacheSize.toString()
    }

    fun cacheSize(cacheSize: String){
        def.cacheSize = cacheSize
    }

    fun ignoreInvalidEndpoint(ignoreInvalidEndpoint: Boolean = true) {
        def.ignoreInvalidEndpoint = ignoreInvalidEndpoint.toString()
    }

    fun ignoreInvalidEndpoint(ignoreInvalidEndpoint: String) {
        def.ignoreInvalidEndpoint = ignoreInvalidEndpoint
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