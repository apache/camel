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
package org.apache.camel.builder.component;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;

public abstract class AbstractComponentBuilder<C extends Component> {
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public C build() {
        final C component = buildConcreteComponent();

        configureComponentProperties(component);

        return component;
    }

    public C build(final CamelContext context) {
        final C component = buildConcreteComponent();

        configureComponentProperties(component, context);

        return component;
    }

    public void register(final CamelContext context, final String componentName) {
        final C component = buildConcreteComponent();

        configureComponentProperties(component, context);

        context.addComponent(componentName, component);
    }

    public void doSetProperty(final String key, final Object value) {
        properties.put(key, value);
    }

    protected abstract C buildConcreteComponent();

    protected abstract boolean setPropertyOnComponent(Component component, String name, Object value);

    private void configureComponentProperties(final Component component) {
        properties.forEach((key, value) -> setPropertyOnComponent(component, key, value));
    }

    private void configureComponentProperties(final Component component, final CamelContext context) {
        properties.forEach((key, value) -> setPropertyOnComponent(component, key, resolvePropertyValue(value, context)));
    }

    private Object resolvePropertyValue(final Object value, final CamelContext context) {
        if (value instanceof String) {
            return context.resolvePropertyPlaceholders((String) value);
        }
        return value;
    }
}

