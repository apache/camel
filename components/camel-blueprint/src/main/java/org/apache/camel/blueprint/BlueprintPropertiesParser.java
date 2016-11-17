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
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.aries.blueprint.ExtendedBeanMetadata;
import org.apache.aries.blueprint.ext.AbstractPropertyPlaceholder;
import org.apache.aries.blueprint.ext.PropertyPlaceholder;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
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

    private final PropertiesComponent propertiesComponent;
    private final BlueprintContainer container;
    private final PropertiesParser delegate;
    private final Set<AbstractPropertyPlaceholder> placeholders = new LinkedHashSet<AbstractPropertyPlaceholder>();
    private Method method;

    public BlueprintPropertiesParser(PropertiesComponent propertiesComponent, BlueprintContainer container, PropertiesParser delegate) {
        super(propertiesComponent);
        this.propertiesComponent = propertiesComponent;
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

        for (Object componentId : container.getComponentIds()) {
            String id = (String) componentId;
            ComponentMetadata meta = container.getComponentMetadata(id);
            if (meta instanceof ExtendedBeanMetadata) {
                Class<?> clazz = ((ExtendedBeanMetadata) meta).getRuntimeClass();
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
                    method = AbstractPropertyPlaceholder.class.getDeclaredMethod("retrieveValue", String.class);
                    method.setAccessible(true);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException("Cannot add blueprint property placeholder: " + id
                            + " as the method retrieveValue is not accessible", e);
                }
            }
        }
    }

    @Override
    public String parseProperty(String key, String value, Properties properties) {
        log.trace("Parsing property key: {} with value: {}", key, value);

        String answer = null;

        // prefer any override properties
        // this logic is special for BlueprintPropertiesParser as we otherwise prefer
        // to use the AbstractPropertyPlaceholder from OSGi blueprint config admins
        // service to lookup otherwise
        if (key != null && propertiesComponent.getOverrideProperties() != null) {
            answer = (String) propertiesComponent.getOverrideProperties().get(key);
        }

        // lookup key in blueprint and return its value
        if (answer == null && key != null) {
            for (AbstractPropertyPlaceholder placeholder : placeholders) {
                boolean isDefault = false;
                if (placeholders.size() > 1) {
                    // okay we have multiple placeholders and we want to return the answer that
                    // is not the default placeholder if there is multiple keys
                    if (placeholder instanceof PropertyPlaceholder) {
                        Map map = ((PropertyPlaceholder) placeholder).getDefaultProperties();
                        isDefault = map != null && map.containsKey(key);
                    }
                    log.trace("Blueprint property key: {} is part of default properties: {}", key, isDefault);
                }
                
                try {
                    String candidate = (String) ObjectHelper.invokeMethod(method, placeholder, key);
    
                    if (candidate != null) {
                        if (answer == null || !isDefault) {
                            log.trace("Blueprint parsed candidate property key: {} as value: {}", key, answer);
                            answer = candidate;
                        }
                    }
                } catch (Exception ex) {
                    // Here we just catch the exception and try to use other candidate
                }  
            }
            log.debug("Blueprint parsed property key: {} as value: {}", key, answer);
        }
        
        // if there is a delegate then let it parse the current answer as it may be jasypt which
        // need to decrypt values
        if (delegate != null) {
            String delegateAnswer = delegate.parseProperty(key, answer != null ? answer : value, properties);
            if (delegateAnswer != null) {
                answer = delegateAnswer;
                log.debug("Delegate property parser parsed the property key: {} as value: {}", key, answer);
            }
        }
        
        log.trace("Returning parsed property key: {} as value: {}", key, answer);
        return answer;
    }

}
