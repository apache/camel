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
package org.apache.camel.kotlin

import org.apache.camel.ErrorHandlerFactory
import org.apache.camel.ShutdownRoute
import org.apache.camel.ShutdownRunningTask
import org.apache.camel.kotlin.model.OnExceptionDsl
import org.apache.camel.model.RouteDefinition
import org.apache.camel.spi.RoutePolicy
import kotlin.reflect.KClass

@CamelDslMarker
class RouteDsl(
    val def: RouteDefinition
) {

    fun from(i: UriDsl.() -> Unit) {
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        def.from(uri)
    }

    fun steps(i: StepsDsl.() -> Unit) {
        StepsDsl(def).apply(i)
    }

    fun onException(vararg exceptions: KClass<out Throwable>, i: OnExceptionDsl.() -> Unit) {
        val onExceptionDef = def.onException(*exceptions.map { it.java }.toTypedArray())
        OnExceptionDsl(onExceptionDef).apply(i)
        onExceptionDef.end()
    }

    fun id(id: String) {
        def.routeId(id)
    }

    fun group(group: String) {
        def.group(group)
    }

    fun description(description: String) {
        def.description(description)
    }

    fun nodePrefixId(nodePrefixId: String) {
        def.nodePrefixId(nodePrefixId)
    }

    fun streamCaching(streamCaching: Boolean) {
        def.streamCache = streamCaching.toString()
    }

    fun streamCaching(streamCaching: String) {
        def.streamCache = streamCaching
    }

    fun tracing(tracing: Boolean) {
        def.trace = tracing.toString()
    }

    fun tracing(tracing: String) {
        def.trace = tracing
    }

    fun messageHistory(messageHistory: Boolean) {
        def.messageHistory = messageHistory.toString()
    }

    fun messageHistory(messageHistory: String) {
        def.messageHistory = messageHistory
    }

    fun logMask(logMask: Boolean) {
        def.logMask = logMask.toString()
    }

    fun logMask(logMask: String) {
        def.logMask = logMask
    }

    fun noDelayer() {
        def.noDelayer()
    }

    fun delayer(delayer: Long) {
        def.delayer(delayer)
    }

    fun errorHandler(errorHandler: String) {
        def.errorHandler(errorHandler)
    }

    fun errorHandler(errorHandler: ErrorHandlerFactory) {
        def.errorHandler(errorHandler)
    }

    fun autoStartup(autoStartup: Boolean) {
        def.autoStartup(autoStartup)
    }

    fun autoStartup(autoStartup: String) {
        def.autoStartup(autoStartup)
    }

    fun precondition(precondition: String) {
        def.precondition(precondition)
    }

    fun startupOrder(startupOrder: Int) {
        def.startupOrder(startupOrder)
    }

    fun routePolicy(vararg routePolicy: RoutePolicy) {
        def.routePolicy(*routePolicy)
    }

    fun routePolicy(function: () -> RoutePolicy) {
        def.routePolicy(function)
    }

    fun routePolicyRef(routePolicyRef: String) {
        def.routePolicyRef(routePolicyRef)
    }

    fun shutdownRoute(shutdownRoute: ShutdownRoute) {
        def.shutdownRoute(shutdownRoute)
    }

    fun shutdownRoute(shutdownRoute: String) {
        def.shutdownRoute(shutdownRoute)
    }

    fun shutdownRunningTask(shutdownRunningTask: ShutdownRunningTask) {
        def.shutdownRunningTask(shutdownRunningTask)
    }

    fun shutdownRunningTask(shutdownRunningTask: String) {
        def.shutdownRunningTask(shutdownRunningTask)
    }

    fun inputType(inputType: String) {
        def.inputType(inputType)
    }

    fun inputType(inputType: KClass<*>) {
        def.inputType(inputType.java)
    }

    fun inputTypeWithValidate(inputTypeWithValidate: String) {
        def.inputTypeWithValidate(inputTypeWithValidate)
    }

    fun inputTypeWithValidate(inputTypeWithValidate: KClass<*>) {
        def.inputTypeWithValidate(inputTypeWithValidate.java)
    }

    fun outputType(outputType: String) {
        def.outputType(outputType)
    }

    fun outputType(outputType: KClass<*>) {
        def.outputType(outputType.java)
    }

    fun outputTypeWithValidate(outputTypeWithValidate: String) {
        def.outputTypeWithValidate(outputTypeWithValidate)
    }

    fun outputTypeWithValidate(outputTypeWithValidate: KClass<*>) {
        def.outputTypeWithValidate(outputTypeWithValidate.java)
    }

    fun routeProperty(routeProperty: String, value: String) {
        def.routeProperty(routeProperty, value)
    }
}
