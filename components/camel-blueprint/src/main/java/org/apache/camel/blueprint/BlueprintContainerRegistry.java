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

import org.apache.camel.spi.Registry;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.ReferenceMetadata;

import java.util.LinkedHashMap;
import java.util.Map;

public class BlueprintContainerRegistry implements Registry {

    private final BlueprintContainer blueprintContainer;

    public BlueprintContainerRegistry(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

    public Object lookup(String name) {
        return blueprintContainer.getComponentInstance(name);
    }

    public <T> T lookup(String name, Class<T> type) {
        return type.cast(blueprintContainer.getComponentInstance(name));
    }

    public <T> Map<String, T> lookupByType(Class<T> type) {
        return lookupByType(blueprintContainer, type);
    }

    public static <T> Map<String, T> lookupByType(BlueprintContainer blueprintContainer, Class<T> type) {
        Map<String, T> objects = new LinkedHashMap<String, T>();
        for (ComponentMetadata metadata : blueprintContainer.getMetadata(ComponentMetadata.class)) {
            boolean isSingleton = metadata instanceof BeanMetadata
                        && (BeanMetadata.SCOPE_SINGLETON.equals(((BeanMetadata) metadata).getScope())
                            || ((BeanMetadata) metadata).getScope() == null);
            boolean isReference = metadata instanceof ReferenceMetadata;
            if (isSingleton || isReference) {
                Object o = blueprintContainer.getComponentInstance( metadata.getId() );
                if (type.isInstance( o )) {
                    objects.put( metadata.getId(), type.cast(o) );
                }
            }
        }
        return objects;
    }

}
