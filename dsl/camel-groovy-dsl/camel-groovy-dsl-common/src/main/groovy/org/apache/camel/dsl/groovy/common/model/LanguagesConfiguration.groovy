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
import org.apache.camel.spi.Language

class LanguagesConfiguration {
    private final CamelContext context

    LanguagesConfiguration(CamelContext context) {
        this.context = context
    }

    def language(String name, Closure<?> callable) {
        def target = context.resolveLanguage(name)
        if (target == null) {
            throw new IllegalArgumentException("Unable to find a language with name: ${name}")
        }

        // Just make sure the closure context is belong to component
        callable.resolveStrategy = Closure.DELEGATE_ONLY
        callable.delegate = new BeanConfiguration(context, target)
        callable.call()

        // let's the camel context be aware of the new dataformat
        context.registry.bind(name, Language.class, target)
    }

    def language(String name, Class<? extends Language> type, Closure <?> callable) {
        def target = context.registry.lookupByNameAndType(name, type)
        def bind = false

        // if the language is not found, let's create a new one. This is
        // equivalent to create a new named language, useful to create
        // multiple instances of the same language but with different setup
        if (target == null) {
            target = context.injector.newInstance(type)

            bind = true
        }

        // Just make sure the closure context is belong to dataformat
        callable.resolveStrategy = Closure.DELEGATE_ONLY
        callable.delegate = new BeanConfiguration(context, target)
        callable.call()

        if (bind) {
            // let's the camel context be aware of the new dataformat
            context.registry.bind(name, type, target)
        }
    }

    def methodMissing(String name, arguments) {
        final Object[] args = arguments as Object[]

        if (args != null) {
            if (args.length == 1) {
                def clos = args[0]

                if (clos instanceof Closure) {
                    return language(name, clos)
                }
            }
            if (args.length == 2) {
                def type = args[0]
                def clos = args[1]

                if (type instanceof Class && Language.class.isAssignableFrom(type) && clos instanceof Closure) {
                    return language(name,type, clos)
                }
            }
        }

        throw new MissingMethodException(name, this, args)
    }
}
