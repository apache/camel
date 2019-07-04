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
package org.apache.camel.blueprint;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.aries.blueprint.ext.PropertyPlaceholderExt;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ReflectionHelper;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blueprint {@link PropertiesSource} which supports looking up
 * property placeholders from the Blueprint Property Placeholder Service.
 */
public class BlueprintPropertiesSource extends ServiceSupport implements PropertiesSource {

    private static final Logger LOG = LoggerFactory.getLogger(BlueprintPropertiesSource.class);
    private final BlueprintContainer container;
    private final List<String> ids;
    private final Set<PropertyPlaceholderWrapper> placeholders = new LinkedHashSet<>();

    public BlueprintPropertiesSource(BlueprintContainer container, List<String> ids) {
        this.container = container;
        this.ids = ids;
    }

    @Override
    public String getName() {
        return "BlueprintPropertiesSource" + ids;
    }

    @Override
    public String getProperty(String name) {
        String answer = null;

        for (PropertyPlaceholderWrapper placeholder : placeholders) {
            boolean isDefault = false;
            if (placeholders.size() > 1) {
                // okay we have multiple placeholders and we want to return the answer that
                // is not the default placeholder if there is multiple keys
                Map map = placeholder.getDefaultProperties();
                isDefault = map != null && map.containsKey(name);
                LOG.trace("Blueprint property key: {} is part of default properties: {}", name, isDefault);
            }

            try {
                String candidate = placeholder.retrieveValue(name);
                if (candidate != null) {
                    if (answer == null || !isDefault) {
                        LOG.trace("Blueprint candidate property key: {} as value: {}", name, answer);
                        answer = candidate;
                    }
                }
            } catch (Exception ex) {
                // Here we just catch the exception and try to use other candidate
            }
        }
        LOG.debug("Blueprint getProperty: {}={}", name, answer);

        return answer;
    }

    /**
     * Adds the given Blueprint property placeholder service with the given id
     *
     * @param id id of the Blueprint property placeholder service to add.
     */
    private void addPropertyPlaceholder(String id) {
        Object component = container.getComponentInstance(id);

        if (component instanceof PropertyPlaceholderExt) {
            Class<?> clazz = component.getClass();
            if (clazz != null) {
                LOG.debug("Adding Blueprint PropertyPlaceholder: {}", id);
                Method method = ReflectionHelper.findMethod(clazz, "retrieveValue", String.class);
                Method defaultMethod = ReflectionHelper.findMethod(clazz, "getDefaultProperties");
                if (method != null) {
                    method.setAccessible(true);
                    if (defaultMethod != null) {
                        defaultMethod.setAccessible(true);
                    }
                    placeholders.add(new PropertyPlaceholderWrapper(component, method, defaultMethod));
                } else {
                    throw new IllegalStateException("Cannot add blueprint property placeholder: " + id
                            + " as the method retrieveValue is not found");
                }
            }
        }
    }

    @Override
    protected void doInit() throws Exception {
        for (String id : ids) {
            addPropertyPlaceholder(id);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    private class PropertyPlaceholderWrapper {

        private final Object delegate;
        private final Method method;
        private final Method defaultMethod;

        PropertyPlaceholderWrapper(Object delegate, Method method, Method defaultMethod) {
            this.delegate = delegate;
            this.method = method;
            this.defaultMethod = defaultMethod;
        }

        String retrieveValue(String key) {
            Object v = ObjectHelper.invokeMethod(method, delegate, key);
            return v == null ? null : v.toString();
        }

        Map getDefaultProperties() {
            if (defaultMethod != null) {
                return (Map) ObjectHelper.invokeMethod(defaultMethod, delegate);
            }
            return null;
        }
    }

}
