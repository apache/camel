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
package org.apache.camel.dsl.groovy.common.model

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.apache.camel.Predicate
import org.apache.camel.Processor
import org.apache.camel.builder.endpoint.EndpointBuilderFactory

class BeansConfiguration implements EndpointBuilderFactory {
    private final CamelContext context

    BeansConfiguration(CamelContext context) {
        this.context = context
    }

    def propertyMissing(String name, value) {
        context.registry.bind(name, value)
    }

    def methodMissing(String name, arguments) {
        final Object[] args = arguments as Object[]

        if (args != null && args.length == 2) {
            def type = args[0] as Class<?>
            def clos = args[1] as Closure<?>
            def bean = context.injector.newInstance(type)

            clos.resolveStrategy = Closure.DELEGATE_ONLY
            clos.delegate = bean
            clos.call()

            context.registry.bind(name, type, bean)
        } else if (args != null && args.length == 1) {
            def clos = args[0] as Closure<?>
            clos.resolveStrategy = Closure.DELEGATE_ONLY

            context.registry.bind(name, clos.call())
        }
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
