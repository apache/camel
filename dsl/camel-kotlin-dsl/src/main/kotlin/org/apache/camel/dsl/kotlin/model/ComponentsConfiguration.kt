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
import org.apache.camel.Component

class ComponentsConfiguration(val context: CamelContext) {
    inline fun <reified T : Component> component(name: String, block: T.() -> Unit) : T {
        var target = context.getComponent(name, true, false)
        var bind = false

        if (target != null && !T::class.java.isInstance(target)) {
            throw IllegalArgumentException("Type mismatch, expected: ${T::class.java}, got: ${target.javaClass}")
        }

        // if the component is not found, let's create a new one. This is
        // equivalent to create a new named component, useful to create
        // multiple instances of the same component but with different setup
        if (target == null) {
            target = context.injector.newInstance(T::class.java)
            bind = true
        }

        block.invoke(target as T)

        if (bind) {
            context.registry.bind(name, T::class.java, target)
        }

        return target
    }
}