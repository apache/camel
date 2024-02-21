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
import org.apache.camel.model.LoadBalanceDefinition
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition
import org.apache.camel.model.loadbalancer.WeightedLoadBalancerDefinition
import org.apache.camel.processor.loadbalancer.LoadBalancer
import kotlin.reflect.KClass

@CamelDslMarker
class LoadBalanceDsl(
    val def: LoadBalanceDefinition
) {

    fun inheritErrorHandler(inheritErrorHandler: Boolean) {
        def.inheritErrorHandler(inheritErrorHandler)
    }

    fun custom(custom: LoadBalancer) {
        def.loadBalance(custom)
    }

    fun custom(custom: String) {
        def.custom(custom)
    }

    fun failover(i: FailoverLoadBalancerDsl.() -> Unit = {}) {
        val failoverDef = FailoverLoadBalancerDefinition()
        FailoverLoadBalancerDsl(failoverDef).apply(i)
        def.loadBalancerType = failoverDef
    }

    fun weighted(i: WeightedLoadBalancerDsl.() -> Unit) {
        val weightedDef = WeightedLoadBalancerDefinition()
        WeightedLoadBalancerDsl(weightedDef).apply(i)
        def.loadBalancerType = weightedDef
    }

    fun roundRobin() {
        def.roundRobin()
    }

    fun random() {
        def.random()
    }

    fun sticky(sticky: Expression) {
        def.sticky(sticky)
    }

    fun topic() {
        def.topic()
    }

    fun outputs(i: StepsDsl.() -> Unit) {
        StepsDsl(def).apply(i)
    }
}

@CamelDslMarker
class FailoverLoadBalancerDsl(
    val def: FailoverLoadBalancerDefinition
) {

    fun exceptions(vararg exceptions: String) {
        def.exceptions = exceptions.toMutableList()
    }

    fun exceptionTypes(vararg exceptionTypes: KClass<*>) {
        def.exceptionTypes = exceptionTypes.map { it.java }.toMutableList()
    }

    fun maximumFailoverAttempts(maximumFailoverAttempts: Int) {
        def.maximumFailoverAttempts = maximumFailoverAttempts.toString()
    }

    fun maximumFailoverAttempts(maximumFailoverAttempts: String) {
        def.maximumFailoverAttempts = maximumFailoverAttempts
    }

    fun roundRobin(roundRobin: Boolean) {
        def.roundRobin = roundRobin.toString()
    }

    fun roundRobin(roundRobin: String) {
        def.roundRobin = roundRobin
    }

    fun sticky(sticky: Boolean) {
        def.sticky = sticky.toString()
    }

    fun sticky(sticky: String) {
        def.sticky = sticky
    }
}

@CamelDslMarker
class WeightedLoadBalancerDsl(
    val def: WeightedLoadBalancerDefinition
) {

    fun distributionRatio(distributionRatio: String) {
        def.distributionRatio = distributionRatio
    }

    fun distributionRatioDelimiter(distributionRatioDelimiter: String) {
        def.distributionRatioDelimiter = distributionRatioDelimiter
    }

    fun roundRobin(roundRobin: Boolean) {
        def.roundRobin = roundRobin.toString()
    }

    fun roundRobin(roundRobin: String) {
        def.roundRobin = roundRobin
    }
}