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
package org.apache.camel.dsl.kotlin

import org.apache.camel.ErrorHandlerFactory
import org.apache.camel.Exchange
import org.apache.camel.Predicate
import org.apache.camel.Processor
import org.apache.camel.builder.BuilderSupport
import org.apache.camel.builder.EndpointConsumerBuilder
import org.apache.camel.builder.endpoint.EndpointBuilderFactory
import org.apache.camel.builder.endpoint.EndpointRouteBuilder
import org.apache.camel.dsl.kotlin.model.BeansConfiguration
import org.apache.camel.dsl.kotlin.model.CamelConfiguration
import org.apache.camel.dsl.kotlin.model.RestConfiguration
import org.apache.camel.model.*
import org.apache.camel.model.rest.RestDefinition
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(fileExtension = EXTENSION, compilationConfiguration = KotlinCompilationConfiguration::class)
abstract class KotlinDSL(
        private val builder : EndpointRouteBuilder) : BuilderSupport(builder.context), EndpointBuilderFactory {

    fun rest(): RestDefinition {
        return builder.rest()
    }

    fun rest(block: RestConfiguration.() -> Unit) {
        RestConfiguration(builder).block()
    }

    fun beans(block: BeansConfiguration.() -> Unit) {
        BeansConfiguration(context).block()
    }

    fun camel(block: CamelConfiguration.() -> Unit) {
        CamelConfiguration(context).block()
    }

    fun from(uri: String): RouteDefinition {
        return builder.from(uri)
    }

    fun from(endpoint: EndpointConsumerBuilder): RouteDefinition {
        return builder.from(endpoint)
    }

    fun intercept() : InterceptDefinition {
        return builder.intercept()
    }

    fun onException(exception: Class<out Throwable>) : OnExceptionDefinition {
        return builder.onException(exception)
    }

    fun onCompletion() : OnCompletionDefinition {
        return builder.onCompletion()
    }

    fun interceptFrom() : InterceptFromDefinition {
        return builder.interceptFrom()
    }

    fun interceptFrom(uri: String) : InterceptFromDefinition{
        return builder.interceptFrom(uri)
    }

    fun interceptSendToEndpoint(uri: String) : InterceptSendToEndpointDefinition {
        return builder.interceptSendToEndpoint(uri)
    }

    fun errorHandler(handler: ErrorHandlerFactory) {
        builder.errorHandler(handler)
    }

    fun processor(fn: (Exchange) -> Unit) : Processor {
        return Processor { exchange -> fn(exchange) }
    }
    fun predicate(fn: (Exchange) -> Boolean) : Predicate {
        return Predicate { exchange -> fn(exchange) }
    }
}