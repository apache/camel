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

class Components {
    private final CamelContext context

    Components(CamelContext context) {
        this.context = context
    }

    Component get(String scheme) {
        return context.getComponent(scheme, true)
    }

    Component put(String scheme, Component instance) {
        context.addComponent(scheme, instance)

        return instance;
    }

    Component make(String scheme, String type) {
        final Class<?> clazz = context.getClassResolver().resolveClass(type)
        final Component instance = (Component)context.getInjector().newInstance(clazz)

        context.addComponent(scheme, instance)

        return instance;
    }
}
