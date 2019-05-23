/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.support;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;

import static org.apache.camel.support.IntrospectionSupport.findSetterMethods;
import static org.apache.camel.support.IntrospectionSupport.getOrElseProperty;

/**
 * A convenient support class for binding String valued properties to an instance which
 * uses a set of conventions:
 * <ul>
 *     <li>property placeholders - Keys and values using Camels property placeholder will be resolved</li>
 *     <li>nested - Properties can be nested using the dot syntax (OGNL and builder pattern using with as prefix), eg foo.bar=123</li>
 *     <li>reference by id - Values can refer to other beans in the registry by prefixing with # syntax, eg #myBean</li>
 *     <li>reference by type - Values can refer to singleton beans by their type in the registry by prefixing with #type: syntax, eg #type:com.foo.MyClassType</li>
 *     <li>new class - Values can refer to creating new beans by their class name syntax, eg class:com.foo.MyClassType</li>
 * </ul>
 * This implementations reuses parts of {@link IntrospectionSupport}.
 */
public final class PropertyBindingSupport {

    // TODO: Add support for auto binding to singleton instance by type from registry (boolean on|off)
    // TODO: Better exception message if something goes wrong (output target, name of property etc)

    private PropertyBindingSupport() {
    }

    /**
     * Binds the properties to the target object.
     *
     * @param camelContext  the camel context
     * @param target        the target object
     * @param properties    the properties
     * @return              true if one or more properties was bound, false otherwise
     */
    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties) throws Exception {
        boolean answer = false;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            answer |= bindProperty(camelContext, target, entry.getKey(), entry.getValue());
        }
        return answer;
    }

    /**
     * Binds the property to the target object.
     *
     * @param camelContext  the camel context
     * @param target        the target object
     * @param name          name of property
     * @param value         value of property
     * @return              true if property was bound, false otherwise
     */
    public static boolean bindProperty(CamelContext camelContext, Object target, String name, Object value) throws Exception {
        if (target != null && name != null) {
            return setProperty(camelContext, target, name, value);
        } else {
            return false;
        }
    }

    private static boolean setProperty(CamelContext context, Object target, String name, Object value) {
        Class<?> clazz = target.getClass();
        String refName = null;

        // resolve property placeholders
        name = context.resolvePropertyPlaceholders(name);
        if (value instanceof String) {
            // resolve property placeholders
            value = context.resolvePropertyPlaceholders(value.toString());
        }

        // if name has dot then we need to OGNL walk it
        if (name.indexOf('.') > 0) {
            String[] parts = name.split("\\.");
            Object newTarget = target;
            Class<?> newClass = clazz;
            // we should only iterate until until 2nd last so we use -1 in the for loop
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                Object prop = getOrElseProperty(newTarget, part, null);
                if (prop == null) {
                    // okay is there a setter so we can create a new instance and set it automatic
                    Set<Method> newSetters = findSetterMethods(newClass, part, true);
                    if (newSetters.size() == 1) {
                        Method method = newSetters.iterator().next();
                        Class<?> parameterType = method.getParameterTypes()[0];
                        if (parameterType != null && org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(parameterType)) {
                            Object instance = context.getInjector().newInstance(parameterType);
                            if (instance != null) {
                                org.apache.camel.support.ObjectHelper.invokeMethod(method, newTarget, instance);
                                newTarget = instance;
                                newClass = newTarget.getClass();
                            }
                        }
                    }
                } else {
                    newTarget = prop;
                    newClass = newTarget.getClass();
                }
            }
            // okay we found a nested property, then lets change to use that
            try {
                target = newTarget;
                name = parts[parts.length - 1];
                if (value instanceof String) {
                    if (value.toString().startsWith("class:")) {
                        // its a new class to be created
                        String className = value.toString().substring(6);
                        Class<?> type = context.getClassResolver().resolveMandatoryClass(className);
                        if (type != null) {
                            value = context.getInjector().newInstance(type);
                        }
                    } else if (value.toString().startsWith("#type:")) {
                        // its reference by type, so lookup the actual value and use it if there is only one instance in the registry
                        String typeName = value.toString().substring(6);
                        Class<?> type = context.getClassResolver().resolveMandatoryClass(typeName);
                        if (type != null) {
                            Set<?> types = context.getRegistry().findByType(type);
                            if (types.size() == 1) {
                                value = types.iterator().next();
                            }
                        }
                    } else if (EndpointHelper.isReferenceParameter(value.toString())) {
                        // okay its a reference so swap to lookup this which is already supported in IntrospectionSupport
                        refName = value.toString();
                        value = null;
                    }
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        try {
            return IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, value, refName, true);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

}
