/**
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.Registry;
import org.osgi.framework.Bundle;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.NoSuchComponentException;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.ReferenceMetadata;

public class BlueprintContainerRegistry implements Registry {

    private final BlueprintContainer blueprintContainer;

    public BlueprintContainerRegistry(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

    @Override
    public Object lookupByName(String name) {
        try {
            return blueprintContainer.getComponentInstance(name);
        } catch (NoSuchComponentException e) {
            return null;
        }
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        Object answer;
        try {
            answer = blueprintContainer.getComponentInstance(name);
        } catch (NoSuchComponentException e) {
            return null;
        }

        // just to be safe
        if (answer == null) {
            return null;
        }

        try {
            return type.cast(answer);
        } catch (Throwable e) {
            String msg = "Found bean: " + name + " in BlueprintContainer: " + blueprintContainer
                    + " of type: " + answer.getClass().getName() + " expected type was: " + type;
            throw new NoSuchBeanException(name, msg, e);
        }
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        return lookupByType(blueprintContainer, type);
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        Map<String, T> map = lookupByType(blueprintContainer, type);
        return new HashSet<T>(map.values());
    }

    @Override
    public Object lookup(String name) {
        return lookupByName(name);
    }

    @Override
    public <T> T lookup(String name, Class<T> type) {
        return lookupByNameAndType(name, type);
    }

    @Override
    public <T> Map<String, T> lookupByType(Class<T> type) {
        return findByTypeWithName(type);
    }

    public static <T> Map<String, T> lookupByType(BlueprintContainer blueprintContainer, Class<T> type) {
        return lookupByType(blueprintContainer, type, true);
    }

    public static <T> Map<String, T> lookupByType(BlueprintContainer blueprintContainer, Class<T> type, boolean includeNonSingletons) {
        Bundle bundle = (Bundle) blueprintContainer.getComponentInstance("blueprintBundle");
        Map<String, T> objects = new LinkedHashMap<String, T>();
        Set<String> ids = blueprintContainer.getComponentIds();
        for (String id : ids) {
            try {
                ComponentMetadata metadata = blueprintContainer.getComponentMetadata(id);
                Class<?> cl = null;
                if (metadata instanceof BeanMetadata) {
                    BeanMetadata beanMetadata = (BeanMetadata)metadata;
                    // should we skip the bean if its prototype and we are only looking for singletons?
                    if (!includeNonSingletons) {
                        String scope = beanMetadata.getScope();
                        if (BeanMetadata.SCOPE_PROTOTYPE.equals(scope)) {
                            continue;
                        }
                    }
                    cl = bundle.loadClass(beanMetadata.getClassName());
                } else if (metadata instanceof ReferenceMetadata) {
                    ReferenceMetadata referenceMetadata = (ReferenceMetadata)metadata;
                    cl = bundle.loadClass(referenceMetadata.getInterface());
                }
                if (cl != null && type.isAssignableFrom(cl)) {
                    Object o = blueprintContainer.getComponentInstance(metadata.getId());
                    objects.put(metadata.getId(), type.cast(o));
                }
            } catch (Throwable t) {
                // ignore
            }
        }
        return objects;
    }
}
