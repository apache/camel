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
package org.apache.camel.spring.boot.util;

import java.util.Iterator;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * To help configuring Camel properties that have been defined in Spring Boot configuration files.
 */
public final class CamelPropertiesHelper {

    private CamelPropertiesHelper() {
    }

    /**
     * Sets the properties on the target bean.
     * <p/>
     * This method uses {@link PropertyBindingSupport} and therefore offers its capabilities such as:
     * <ul>
     *     <li>property placeholders - Keys and values using Camels property placeholder will be resolved</li>
     *     <li>nested - Properties can be nested using the dot syntax (OGNL and builder pattern using with as prefix), eg foo.bar=123</li>
     *     <li>map</li> - Properties can lookup in Map's using map syntax, eg foo[bar] where foo is the name of the property that is a Map instance, and bar is the name of the key.</li>
     *     <li>list</li> - Properties can refer or add to in List's using list syntax, eg foo[0] where foo is the name of the property that is a
     *                     List instance, and 0 is the index. To refer to the last element, then use last as key.</li>
     * </ul>
     * This implementation sets the properties using the following algorithm in the given order:
     * <ul>
     *     <li>reference by bean id - Values can refer to other beans in the registry by prefixing with with # or #bean: eg #myBean or #bean:myBean</li>
     *     <li>reference by type - Values can refer to singleton beans by their type in the registry by prefixing with #type: syntax, eg #type:com.foo.MyClassType</li>
     *     <li>autowire by type - Values can refer to singleton beans by auto wiring by setting the value to #autowired</li>
     *     <li>reference new class - Values can refer to creating new beans by their class name by prefixing with #class, eg #class:com.foo.MyClassType</li>
     *     <li>value as lookup - The value is used as-is (eg like #value) to lookup in the Registry if there is a bean then its set on the target</li>
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
            try {
                hit = PropertyBindingSupport.build().bind(context, target, name, value);
            } catch (PropertyBindingException e) {
                // no we could not and this would be thrown if we attempted to set a value on a property which we cannot do type conversion as
                // then maybe the value refers to a spring bean in the registry so try this
                if (stringValue != null) {
                    if (stringValue.startsWith("#")) {
                        stringValue = stringValue.substring(1);
                    }
                    // use #bean: to lookup
                    stringValue = "#bean:" + stringValue;
                    hit = PropertyBindingSupport.build().bind(context, target, name, stringValue);
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

}
