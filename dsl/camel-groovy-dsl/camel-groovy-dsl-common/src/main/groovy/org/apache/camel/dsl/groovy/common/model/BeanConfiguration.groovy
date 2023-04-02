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
import org.apache.camel.support.PluginHelper
import org.apache.camel.support.PropertyBindingSupport

class BeanConfiguration {
    private final CamelContext context
    private final Object target

    BeanConfiguration(CamelContext camelContext, Object target) {
        this.context = camelContext
        this.target = target
    }

    def methodMissing(String name, arguments) {
        Object value
        final Object[] args = arguments as Object[]

        if (args == null) {
            value = null
        } else if (args.length == 1) {
            value = args[0]
        } else {
            throw new IllegalArgumentException("Unable to set property '${name}' on target '${target.class.name}'")
        }

        if (value instanceof Closure<?>) {
            def m = this.target.metaClass.getMetaMethod(name, Closure.class)
            if (m) {
                m.invoke(target, args)
                // done
                return
            }
        }

        boolean bound = PropertyBindingSupport.build()
            .withCamelContext(context)
            .withTarget(target)
            .withProperty(name, value)
            .bind()

        if (!bound) {
            throw new MissingMethodException(name, this.target.class, args as Object[])
        }
    }

    def propertyMissing(String name, value) {
        boolean bound = PropertyBindingSupport.build()
            .withCamelContext(context)
            .withTarget(target)
            .withProperty(name, value)
            .bind()

        if (!bound) {
            throw new MissingPropertyException(name, this.target.class)
        }
    }

    def propertyMissing(String name) {
        def props = new HashMap<String, Object>()

        PluginHelper.getBeanIntrospection(context)
            .getProperties(target, props, null, false)

        return props[name]
    }
}
