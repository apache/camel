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
package org.apache.camel.spring.boot.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.EndpointHelper.isReferenceParameter;

/**
 * To help configuring Camel properties that have been defined in Spring Boot configuration files.
 */
public final class CamelPropertiesHelper {

    private static final Logger LOG = LoggerFactory.getLogger(CamelContextHelper.class);

    private CamelPropertiesHelper() {
    }

    /**
     * Sets the properties on the target bean.
     * <p/>
     * This implementation sets the properties using the following algorithm:
     * <ul>
     *     <li>Value as reference lookup - If the value uses Camel reference syntax, eg #beanId then the bean is looked up from Registry and set on the target</li>
     *     <li>Value as-is - The value is attempted to be converted to the class type of the bean setter method; this is for regular types like String, numbers etc</li>
     *     <li>Value as lookup - the bean is looked up from Registry and if there is a bean then its set on the target</li>
     * </ul>
     * When an option has been set on the target bean, then its removed from the given properties map. If all the options has been set, then the map will be empty.
     *
     * @param context        the CamelContext
     * @param target         the target bean
     * @param properties     the properties
     * @param failIfNotSet   whether to fail if an option either does not exists on the target bean or if the option cannot be due no suitable setter methods with the given type
     * @return <tt>true</tt> if at least one option was configured
     * @throws IllegalArgumentException is thrown if an option cannot be configured on the bean because there is no suitable setter method and failOnNoSet is true.
     * @throws Exception for any other kind of error
     */
    public static boolean setCamelProperties(CamelContext context, Object target, Map<String, Object> properties, boolean failIfNotSet) throws Exception {
        ObjectHelper.notNull(context, "context");
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");
        boolean rc = false;

        Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String name = entry.getKey();
            Object value = entry.getValue();
            String stringValue = value != null ? value.toString() : null;
            boolean hit = false;
            if (stringValue != null && isReferenceParameter(stringValue)) {
                // its a #beanId reference lookup
                hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, null, stringValue, true);
            } else if (value != null) {
                // its a value to be used as-is (or type converted)
                try {
                    hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, value);
                } catch (IllegalArgumentException e) {
                    // no we could not and this would be thrown if we attempted to set a value on a property which we cannot do type conversion as
                    // then maybe the value refers to a spring bean in the registry so try this
                    hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, null, stringValue, true);
                }
            }

            if (hit) {
                // must remove as its a valid option and we could configure it
                it.remove();
                rc = true;
            } else if (failIfNotSet) {
                throw new IllegalArgumentException("Cannot configure option [" + name + "] with value [" + stringValue
                    + "] as the bean class [" + ObjectHelper.classCanonicalName(target)
                    + "] has no suitable setter method, or not possible to lookup a bean with the id [" + stringValue + "] in Spring Boot registry");
            }
        }

        return rc;
    }

    /**
     * Inspects the given object and resolves any property placeholders from its properties.
     * <p/>
     * This implementation will check all the getter/setter pairs on this instance and for all the values
     * (which is a String type) will be property placeholder resolved.
     *
     * @param camelContext the Camel context
     * @param target       the object that should have the properties (eg getter/setter) resolved
     * @throws Exception is thrown if property placeholders was used and there was an error resolving them
     * @see CamelContext#resolvePropertyPlaceholders(String)
     * @see org.apache.camel.component.properties.PropertiesComponent
     */
    public static void resolvePropertyPlaceholders(CamelContext camelContext, Object target) throws Exception {
        LOG.trace("Resolving property placeholders for: {}", target);

        // find all getter/setter which we can use for property placeholders
        Map<String, Object> properties = new HashMap<>();
        IntrospectionSupport.getProperties(target, properties, null);

        Map<String, Object> changedProperties = new HashMap<>();
        if (!properties.isEmpty()) {
            LOG.trace("There are {} properties on: {}", properties.size(), target);
            // lookup and resolve properties for String based properties
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                // the name is always a String
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    // value must be a String, as a String is the key for a property placeholder
                    String text = (String) value;
                    text = camelContext.resolvePropertyPlaceholders(text);
                    if (text != value) {
                        // invoke setter as the text has changed
                        boolean changed = IntrospectionSupport.setProperty(camelContext.getTypeConverter(), target, name, text);
                        if (!changed) {
                            throw new IllegalArgumentException("No setter to set property: " + name + " to: " + text + " on: " + target);
                        }
                        changedProperties.put(name, value);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Changed property [{}] from: {} to: {}", name, value, text);
                        }
                    }
                }
            }
        }
    }
}
