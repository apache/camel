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
import org.apache.camel.Component

class ComponentsConfiguration {
    private final CamelContext context

    ComponentsConfiguration(CamelContext context) {
        this.context = context
    }

    def component(String name, Closure<?> callable) {
        def target = context.getComponent(name, true, false)
        if (target == null) {
            throw new IllegalArgumentException("Unable to find a component with name: ${name}")
        }

        // Just make sure the closure context is belong to component
        callable.resolveStrategy = Closure.DELEGATE_ONLY
        callable.delegate = new BeanConfiguration(context, target)
        callable.call()
    }

    def component(String name, Class<? extends Component> type, Closure <?> callable) {
        def target = context.getComponent(name, true, false)
        def bind = false

        if (target != null && !type.isInstance(target)) {
            throw new IllegalArgumentException("Type mismatch, expected: ${type} , got: ${target.class}")
        }

        // if the component is not found, let's create a new one. This is
        // equivalent to create a new named component, useful to create
        // multiple instances of the same component but with different setup
        if (target == null) {
            target = context.injector.newInstance(type)
            bind = true
        }

        // Just make sure the closure context is belong to component
        callable.resolveStrategy = Closure.DELEGATE_ONLY
        callable.delegate = new BeanConfiguration(context, target)
        callable.call()

        if (bind) {
            context.registry.bind(name, type, target)
        }
    }

    def methodMissing(String name, arguments) {
        final Object[] args = arguments as Object[]

        if (args != null) {
            if (args.length == 1) {
                def clos = args[0]

                if (clos instanceof Closure) {
                    return component(name, clos)
                }
            }
            if (args.length == 2) {
                def type = args[0]
                def clos = args[1]

                if (type instanceof Class && Component.class.isAssignableFrom(type) && clos instanceof Closure) {
                    return component(name, type, clos)
                }
            }
        }

        throw new MissingMethodException(name, this, args)
    }

}
