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
package org.apache.camel.dsl.groovy.common

import org.apache.camel.ErrorHandlerFactory
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.apache.camel.Predicate
import org.apache.camel.Processor
import org.apache.camel.builder.BuilderSupport
import org.apache.camel.builder.EndpointConsumerBuilder
import org.apache.camel.builder.endpoint.EndpointBuilderFactory
import org.apache.camel.builder.endpoint.EndpointRouteBuilder
import org.apache.camel.dsl.groovy.common.model.BeansConfiguration
import org.apache.camel.dsl.groovy.common.model.CamelConfiguration
import org.apache.camel.dsl.groovy.common.model.Components
import org.apache.camel.dsl.groovy.common.model.RestConfiguration
import org.apache.camel.model.InterceptDefinition
import org.apache.camel.model.InterceptFromDefinition
import org.apache.camel.model.InterceptSendToEndpointDefinition
import org.apache.camel.model.OnCompletionDefinition
import org.apache.camel.model.OnExceptionDefinition
import org.apache.camel.model.RouteDefinition
import org.apache.camel.model.rest.RestConfigurationDefinition
import org.apache.camel.model.rest.RestDefinition
import org.apache.camel.spi.Registry

class GroovyDSL extends BuilderSupport implements EndpointBuilderFactory {
    final Registry registry
    final Components components
    final EndpointRouteBuilder builder

    GroovyDSL(EndpointRouteBuilder builder) {
        super(builder.context)

        this.registry = this.context.registry
        this.components = new Components(this.context)
        this.builder = builder
    }

    def beans(@DelegatesTo(BeansConfiguration) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = new BeansConfiguration(context)
        callable.call()
    }

    def camel(@DelegatesTo(CamelConfiguration) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = new CamelConfiguration(context)
        callable.call()
    }

    def rest(@DelegatesTo(RestConfiguration) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = new RestConfiguration(builder)
        callable.call()
    }

    RestDefinition rest() {
        return builder.rest()
    }

    RestConfigurationDefinition restConfiguration() {
        builder.restConfiguration();
    }

    RestDefinition rest(String path) {
        return builder.rest(path)
    }

    RouteDefinition from(String endpoint) {
        return builder.from(endpoint)
    }

    RouteDefinition from(EndpointConsumerBuilder endpoint) {
        return builder.from(endpoint)
    }

    OnExceptionDefinition onException(Class<? extends Throwable> exception) {
        return builder.onException(exception)
    }

    OnCompletionDefinition onCompletion() {
        return builder.onCompletion()
    }

    InterceptDefinition intercept() {
        return builder.intercept()
    }

    InterceptFromDefinition interceptFrom() {
        return builder.interceptFrom()
    }

    InterceptFromDefinition interceptFrom(String uri) {
        return builder.interceptFrom(uri)
    }

    InterceptSendToEndpointDefinition interceptSendToEndpoint(String uri) {
        return builder.interceptSendToEndpoint(uri)
    }

    void errorHandler(ErrorHandlerFactory handler) {
        builder.errorHandler(handler)
    }

    static Processor processor(@DelegatesTo(Exchange) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST

        return {
            callable.call(it)
        } as Processor
    }

    static Predicate predicate(@DelegatesTo(Exchange) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST

        return {
            return callable.call(it)
        } as Predicate
    }

    static Expression expression(@DelegatesTo(Exchange) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST

        return {
            return callable.call(it)
        } as Expression
    }

}
