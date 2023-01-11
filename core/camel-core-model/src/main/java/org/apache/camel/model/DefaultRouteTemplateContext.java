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
package org.apache.camel.model;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.support.LocalBeanRegistry;
import org.apache.camel.util.StringHelper;

/**
 * Default {@link RouteTemplateContext}.
 */
@XmlTransient
public final class DefaultRouteTemplateContext implements RouteTemplateContext {

    private final CamelContext camelContext;
    private final LocalBeanRegistry registry;
    private final Map<String, Object> parameters;
    private Consumer<RouteTemplateContext> configurer;

    public DefaultRouteTemplateContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        // we just need the simple registry that also supports supplier style
        this.registry = new LocalBeanRegistry();
        this.parameters = new HashMap<>();
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    @Override
    public void bind(String id, Object bean) {
        if (bean instanceof BeanSupplier) {
            // need to unwrap bean supplier as regular supplier
            BeanSupplier<Object> bs = (BeanSupplier<Object>) bean;
            registry.bind(id, (Supplier<Object>) () -> bs.get(DefaultRouteTemplateContext.this));
        } else {
            registry.bind(id, bean);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void bind(String id, Class<?> type, Object bean) {
        if (bean instanceof BeanSupplier) {
            // need to unwrap bean supplier as regular supplier
            BeanSupplier<Object> bs = (BeanSupplier<Object>) bean;
            registry.bind(id, type, () -> bs.get(this));
        } else {
            registry.bind(id, type, bean);
        }
    }

    @Override
    public void bind(String id, Class<?> type, Supplier<Object> bean) {
        registry.bind(id, type, bean);
    }

    @Override
    public void bindAsPrototype(String id, Class<?> type, Supplier<Object> bean) {
        registry.bindAsPrototype(id, type, bean);
    }

    @Override
    public Object getProperty(String name) {
        return parameters.get(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String name, Class<?> type) {
        Object value = parameters.get(name);
        return (T) camelContext.getTypeConverter().tryConvertTo(type, value);
    }

    @Override
    public void setParameter(String name, Object value) {
        parameters.put(name, value);
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public boolean hasParameter(String name) {
        if (parameters.containsKey(name)) {
            return true;
        }
        // lookup key with both dash and camel style
        name = StringHelper.dashToCamelCase(name);
        if (parameters.containsKey(name)) {
            return true;
        }
        name = StringHelper.camelCaseToDash(name);
        if (parameters.containsKey(name)) {
            return true;
        }
        return false;
    }

    @Override
    public BeanRepository getLocalBeanRepository() {
        return registry;
    }

    @Override
    public Consumer<RouteTemplateContext> getConfigurer() {
        return configurer;
    }

    @Override
    public void setConfigurer(Consumer<RouteTemplateContext> configurer) {
        this.configurer = configurer;
    }
}
