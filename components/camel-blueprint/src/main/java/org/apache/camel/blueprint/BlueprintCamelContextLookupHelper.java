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

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.aries.blueprint.ExtendedBeanMetadata;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.reflect.ComponentMetadata;

/**
 * A helper class to find the ids of the {@link BlueprintCamelContext} defined
 * in the {@link org.osgi.service.blueprint.container.BlueprintContainer}
 */
public final class BlueprintCamelContextLookupHelper {

    private BlueprintCamelContextLookupHelper() {
    }

    /**
     * Lookup all the {@link BlueprintCamelContext} in the {@link BlueprintContainer}.
     *
     * @param container the blueprint container, must be provided
     * @return a set with the ids of the {@link BlueprintCamelContext}, never <tt>null</tt>, but can be empty set.
     */
    public static Set<String> lookupBlueprintCamelContext(BlueprintContainer container) {
        Set<String> ids = new LinkedHashSet<String>();
        for (Object id : container.getComponentIds()) {
            ComponentMetadata meta = container.getComponentMetadata(id.toString());

            // must be extended meta, to see if its the blueprint camel context
            if (meta instanceof ExtendedBeanMetadata) {
                Class<?> clazz = ((ExtendedBeanMetadata) meta).getRuntimeClass();
                if (clazz != null && BlueprintCamelContext.class.isAssignableFrom(clazz)) {
                    // okay we found a BlueprintCamelContext
                    ids.add(meta.getId());
                }
            }
        }
        return ids;
    }
}
