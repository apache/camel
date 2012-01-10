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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.aries.blueprint.ExtendedBeanMetadata;
import org.apache.aries.blueprint.ext.AbstractPropertyPlaceholder;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.util.ObjectHelper;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.reflect.ComponentMetadata;

/**
 * Blueprint {@link PropertiesParser} which supports looking up
 * property placeholders from the Blueprint Property Placeholder Service.
 * <p/>
 * This implementation will sit on top of any existing configured
 * {@link PropertiesParser} and will delegate to those in case Blueprint could not
 * resolve the property.
 */
public class BlueprintPropertiesParser extends DefaultPropertiesParser {

    private final BlueprintContainer container;
    private PropertiesParser delegate;
    private final Set<AbstractPropertyPlaceholder> placeholders = new LinkedHashSet<AbstractPropertyPlaceholder>();
    private Method method;

    public BlueprintPropertiesParser(BlueprintContainer container, PropertiesParser delegate) {
        this.container = container;
        this.delegate = delegate;
    }

    /**
     * Lookup the ids of the Blueprint property placeholder services in the
     * Blueprint container.
     *
     * @return the ids, will be an empty array if none found.
     */
    public String[] lookupPropertyPlaceholderIds() {
        List<String> ids = new ArrayList<String>();

        for (String id : container.getComponentIds()) {
            ComponentMetadata meta = container.getComponentMetadata(id);
            if (meta instanceof ExtendedBeanMetadata) {
                Class clazz = ((ExtendedBeanMetadata) meta).getRuntimeClass();
                if (clazz != null && AbstractPropertyPlaceholder.class.isAssignableFrom(clazz)) {
                    ids.add(id);
                }
            }
        }

        return ids.toArray(new String[ids.size()]);
    }

    /**
     * Adds the given Blueprint property placeholder service with the given id
     *
     * @param id id of the Blueprint property placeholder service to add.
     */
    public void addPropertyPlaceholder(String id) {
        Object component = container.getComponentInstance(id);

        if (component instanceof AbstractPropertyPlaceholder) {
            AbstractPropertyPlaceholder placeholder = (AbstractPropertyPlaceholder) component;
            placeholders.add(placeholder);

            log.debug("Adding Blueprint PropertyPlaceholder: {}", id);

            if (method == null) {
                try {
                    method = AbstractPropertyPlaceholder.class.getDeclaredMethod("getProperty", String.class);
                    method.setAccessible(true);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException("Cannot add blueprint property placeholder: " + id
                            + " as the method getProperty is not accessible", e);
                }
            }
        }
    }

    @Override
    public String parseProperty(String key, String value, Properties properties) {
        log.trace("Parsing property key: {} with value: {}", key, value);

        // lookup key in blueprint and return its value
        if (key != null) {
            for (AbstractPropertyPlaceholder placeholder : placeholders) {
                value = (String) ObjectHelper.invokeMethod(method, placeholder, key);
                if (value != null) {
                    log.debug("Blueprint parsed property key: {} as value: {}", key, value);
                    break;
                }
            }
        }

        if (value == null && delegate != null) {
            // let delegate have a try since blueprint didn't resolve it
            value = delegate.parseProperty(key, value, properties);
        }

        log.trace("Returning parsed property key: {} as value: {}", key, value);
        return value;
    }

}
