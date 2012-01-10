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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.aries.blueprint.ExtendedBeanMetadata;
import org.apache.aries.blueprint.mutable.MutableReferenceMetadata;
import org.apache.camel.spi.Registry;
import org.osgi.framework.Bundle;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.NoSuchComponentException;

public class BlueprintContainerRegistry implements Registry {

    private final BlueprintContainer blueprintContainer;

    public BlueprintContainerRegistry(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

    public Object lookup(String name) {
        return blueprintContainer.getComponentInstance(name);
    }

    public <T> T lookup(String name, Class<T> type) {
        try {
            return type.cast(blueprintContainer.getComponentInstance(name));
        } catch (NoSuchComponentException e) {
            return null;
        }
    }

    public <T> Map<String, T> lookupByType(Class<T> type) {
        return lookupByType(blueprintContainer, type);
    }

    public static <T> Map<String, T> lookupByType(BlueprintContainer blueprintContainer, Class<T> type) {
        Map<String, T> objects = new LinkedHashMap<String, T>();
        for (ExtendedBeanMetadata metadata : blueprintContainer.getMetadata(ExtendedBeanMetadata.class)) {
            try {
                Class cl = metadata.getRuntimeClass();
                if (cl == null && metadata.getClassName() != null) {
                    Bundle bundle = (Bundle) blueprintContainer.getComponentInstance("blueprintBundle");
                    cl = bundle.loadClass(metadata.getClassName());
                }
                if (cl == null || type.isAssignableFrom(cl)) {
                    Object o = blueprintContainer.getComponentInstance(metadata.getId());
                    objects.put(metadata.getId(), type.cast(o));
                }
            } catch (Throwable t) {
                // ignore
            }
        }
        for (MutableReferenceMetadata metadata : blueprintContainer.getMetadata(MutableReferenceMetadata.class)) {
            try {
                Class cl = metadata.getRuntimeInterface();
                if (cl == null && metadata.getInterface() != null) {
                    Bundle bundle = (Bundle) blueprintContainer.getComponentInstance("blueprintBundle");
                    cl = bundle.loadClass(metadata.getInterface());
                }
                if (cl == null || type.isAssignableFrom(cl)) {
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
