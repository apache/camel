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

import org.apache.camel.kotlin.model.InterceptDsl
import org.apache.camel.kotlin.model.InterceptFromDsl
import org.apache.camel.kotlin.model.InterceptSendToEndpointDsl
import org.apache.camel.kotlin.model.OnExceptionDsl
import org.apache.camel.kotlin.model.rest.RestDsl
import org.apache.camel.model.ModelCamelContext
import org.apache.camel.model.app.RegistryBeanDefinition
import org.apache.camel.model.rest.RestConfigurationDefinition
import org.apache.camel.support.PropertyBindingSupport
import kotlin.reflect.KClass

@CamelDslMarker
class RootDsl(
    val ctx: ModelCamelContext
) {

    val routeBuilder = RouteBuilderImpl(ctx)

    internal fun build() {
        routeBuilder.addRoutesToCamelContext(ctx)
    }

    fun route(i: RouteDsl.() -> Unit) {
        val def = routeBuilder.routeCollection.route()
        RouteDsl(def).apply(i)
        ctx.addRouteDefinition(def)
    }

    fun bean(bean: String, value: Any) {
        ctx.registry.bind(bean, value)
    }

    inline fun <reified T> bean(bean: String, i: T.() -> Unit) {
        val instance = ctx.injector.newInstance(T::class.java)
        instance.apply(i)
        ctx.registry.bind(bean, instance)
    }

    inline fun bean(name: String, crossinline function: () -> Any) {
        ctx.registry.bind(name, function())
    }

    fun bean(i: RegistryBeanDsl.() -> Unit) {
        val def = RegistryBeanDefinition()
        RegistryBeanDsl(def).apply(i)
        val type = ctx.classResolver.resolveMandatoryClass(def.type)
        val instance = ctx.injector.newInstance(type)
        if (!def.properties.isNullOrEmpty()) {
            PropertyBindingSupport.bindProperties(ctx, instance, def.properties)
        }
        ctx.registry.bind(def.name, instance)
    }

    fun restConfiguration(i: RestConfigurationDefinition.() -> Unit) {
        routeBuilder.restConfiguration().apply(i)
    }

    fun rest(rest: String? = null, i: RestDsl.() -> Unit) {
        val restDef = routeBuilder.rest(rest)
        RestDsl(restDef).apply(i)
    }

    fun onException(vararg exceptions: KClass<out Throwable>, i: OnExceptionDsl.() -> Unit) {
        val def = routeBuilder.onException(*exceptions.map { it.java }.toTypedArray())
        OnExceptionDsl(def).apply(i)
    }

    fun intercept(i: InterceptDsl.() -> Unit) {
        val def = routeBuilder.intercept()
        InterceptDsl(def).apply(i)
    }

    fun interceptFrom(i: InterceptFromDsl.() -> Unit) {
        val def = routeBuilder.interceptFrom()
        InterceptFromDsl(def).apply(i)
    }

    fun interceptSendToEndpoint(uri: String, i: InterceptSendToEndpointDsl.() -> Unit) {
        val def = routeBuilder.interceptSendToEndpoint(uri)
        InterceptSendToEndpointDsl(def).apply(i)
    }
}