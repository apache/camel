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
package org.apache.camel.dsl.kotlin.model

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.Predicate
import org.apache.camel.Processor
import org.apache.camel.builder.endpoint.EndpointBuilderFactory

class BeansConfiguration(
        val context: CamelContext) : EndpointBuilderFactory {

    inline fun <reified T : Any> bean(name: String, block: T.() -> Unit) {
        val bean = context.injector.newInstance(T::class.java)
        bean.block()

        context.registry.bind(name, T::class.java, bean)
    }

    inline fun bean(name: String, crossinline function: () -> Any ) {
        context.registry.bind(name, function())
    }

    fun processor(name: String, fn: (Exchange) -> Unit) {
        context.registry.bind(name, Processor { exchange -> fn(exchange) } )
    }

    fun predicate(name: String, fn: (Exchange) -> Boolean) {
        context.registry.bind(name, Predicate { exchange -> fn(exchange) } )
    }
}