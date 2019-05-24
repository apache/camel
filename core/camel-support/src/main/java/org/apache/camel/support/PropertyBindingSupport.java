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
package org.apache.camel.support;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.PropertyBindingException;

import static org.apache.camel.support.IntrospectionSupport.findSetterMethods;
import static org.apache.camel.support.IntrospectionSupport.getOrElseProperty;

/**
 * A convenient support class for binding String valued properties to an instance which
 * uses a set of conventions:
 * <ul>
 *     <li>property placeholders - Keys and values using Camels property placeholder will be resolved</li>
 *     <li>nested - Properties can be nested using the dot syntax (OGNL and builder pattern using with as prefix), eg foo.bar=123</li>
 *     <li>reference by id - Values can refer to other beans in the registry by prefixing with #id: or # syntax, eg #id:myBean or #myBean</li>
 *     <li>reference by type - Values can refer to singleton beans by their type in the registry by prefixing with #type: syntax, eg #type:com.foo.MyClassType</li>
 *     <li>autowire by type - Values can refer to singleton beans by auto wiring by setting the value to #autowire</li>
 *     <li>new class - Values can refer to creating new beans by their class name syntax, eg class:com.foo.MyClassType</li>
 * </ul>
 * This implementations reuses parts of {@link IntrospectionSupport}.
 */
public final class PropertyBindingSupport {

    public static class Builder {

        private boolean nesting = true;
        private boolean reference = true;
        private boolean placeholder = true;
        private boolean fluentBuilder = true;

        private Object target;
        private Map<String, Object> properties;

        public Builder withNesting(boolean nesting) {
            this.nesting = nesting;
            return this;
        }

        public Builder withReference(boolean reference) {
            this.reference = reference;
            return this;
        }

        public Builder withPlaceholder(boolean placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public Builder withFluentBuilder(boolean fluentBuilder) {
            this.fluentBuilder = fluentBuilder;
            return this;
        }

        public Builder withTarget(Object target) {
            this.target = target;
            return this;
        }

        public Builder withProperties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public boolean bind(CamelContext camelContext) {
            org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
            org.apache.camel.util.ObjectHelper.notNull(target, "target");
            org.apache.camel.util.ObjectHelper.notNull(properties, "properties");

            return bindProperties(camelContext, target, properties, nesting, fluentBuilder, reference, placeholder);
        }

    }

    // TODO: Add support for Map/List

    private PropertyBindingSupport() {
    }

    public static Builder build() {
        return new Builder();
    }

    @FunctionalInterface
    public interface OnAutowiring {

        /**
         * Callback when a property was autowired on a bean
         *
         * @param target        the targeted bean
         * @param propertyName  the name of the property
         * @param propertyType  the type of the property
         * @param value         the property value
         */
        void onAutowire(Object target, String propertyName, Class propertyType, Object value);

    }
    /**
     * This will discover all the properties on the target, and automatic bind the properties that are null by
     * looking up in the registry to see if there is a single instance of the same type as the property.
     * This is used for convention over configuration to automatic configure resources such as DataSource, Amazon Logins and
     * so on.
     *
     * @param camelContext  the camel context
     * @param target        the target object
     * @return              true if one ore more properties was auto wired
     */
    public static boolean autowireSingletonPropertiesFromRegistry(CamelContext camelContext, Object target) {
        return autowireSingletonPropertiesFromRegistry(camelContext, target, false,null);
    }

    /**
     * This will discover all the properties on the target, and automatic bind the properties by
     * looking up in the registry to see if there is a single instance of the same type as the property.
     * This is used for convention over configuration to automatic configure resources such as DataSource, Amazon Logins and
     * so on.
     *
     * @param camelContext  the camel context
     * @param target        the target object
     * @param bindNullOnly  whether to only autowire if the property has no default value or has not been configured explicit
     * @param callback      optional callback when a property was auto wired
     * @return              true if one ore more properties was auto wired
     */
    public static boolean autowireSingletonPropertiesFromRegistry(CamelContext camelContext, Object target, boolean bindNullOnly, OnAutowiring callback) {
        try {
            if (target != null) {
                return doAutowireSingletonPropertiesFromRegistry(camelContext, target, bindNullOnly, callback);
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, e);
        }

        return false;
    }

    private static boolean doAutowireSingletonPropertiesFromRegistry(CamelContext camelContext, Object target, boolean bindNullOnly, OnAutowiring callback) throws Exception {
        // when adding a component then support auto-configuring complex types
        // by looking up from registry, such as DataSource etc
        Map<String, Object> properties = new LinkedHashMap<>();
        IntrospectionSupport.getProperties(target, properties, null);

        boolean hit = false;

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Class<?> type = getGetterType(target, key);
            if (isComplexUserType(type)) {
                // if the property has not been set and its a complex type (not simple or string etc)
                if (!bindNullOnly || value == null) {
                    Set lookup = camelContext.getRegistry().findByType(type);
                    if (lookup.size() == 1) {
                        value = lookup.iterator().next();
                        if (value != null) {
                            hit |= IntrospectionSupport.setProperty(camelContext, target, key, value);
                            if (hit && callback != null) {
                                callback.onAutowire(target, key, type, value);
                            }
                        }
                    }
                }
            }
        };

        return hit;
    }

    /**
     * Binds the properties to the target object, and removes the property that was bound from properties.
     *
     * @param camelContext  the camel context
     * @param target        the target object
     * @param properties    the properties where the bound properties will be removed from
     * @return              true if one or more properties was bound
     */
    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties) {
        return bindProperties(camelContext, target, properties, true, true, true, true);
   }

    /**
     * Binds the properties to the target object, and removes the property that was bound from properties.
     *
     * @param camelContext  the camel context
     * @param target        the target object
     * @param properties    the properties where the bound properties will be removed from
     * @param nesting       whether nesting is in use
     * @param fluentBuilder whether fluent builder is allowed as a valid getter/setter
     * @param reference     whether reference parameter (syntax starts with #) is in use
     * @param placeholder   whether to use Camels property placeholder to resolve placeholders on keys and values
     * @return              true if one or more properties was bound
     */
    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties,
                                         boolean nesting, boolean fluentBuilder, boolean reference, boolean placeholder) {
        org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
        org.apache.camel.util.ObjectHelper.notNull(target, "target");
        org.apache.camel.util.ObjectHelper.notNull(properties, "properties");
        boolean rc = false;

        for (Iterator<Map.Entry<String, Object>> iter = properties.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<String, Object> entry = iter.next();
            if (bindProperty(camelContext, target, entry.getKey(), entry.getValue(), nesting, fluentBuilder, reference, placeholder)) {
                iter.remove();
                rc = true;
            }
        }

        return rc;
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
    public static boolean bindProperty(CamelContext camelContext, Object target, String name, Object value) {
        try {
            if (target != null && name != null) {
                return setProperty(camelContext, target, name, value, true, true, true, true);
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, name, e);
        }

        return false;
    }

    private static boolean bindProperty(CamelContext camelContext, Object target, String name, Object value,
                                boolean nesting, boolean fluentBuilder, boolean reference, boolean placeholder) {
        try {
            if (target != null && name != null) {
                return setProperty(camelContext, target, name, value, nesting, fluentBuilder, reference, placeholder);
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, name, e);
        }

        return false;
    }

    /**
     * Binds the mandatory property to the target object (will fail if not set/bound).
     *
     * @param camelContext  the camel context
     * @param target        the target object
     * @param name          name of property
     * @param value         value of property
     */
    public static void bindMandatoryProperty(CamelContext camelContext, Object target, String name, Object value) {
        try {
            if (target != null && name != null) {
                boolean bound = setProperty(camelContext, target, name, value, true, true, true, true);
                if (!bound) {
                    throw new PropertyBindingException(target, name);
                }
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, name, e);
        }
    }

    private static boolean setProperty(CamelContext context, Object target, String name, Object value,
                                       boolean nesting, boolean fluentBuilder, boolean reference, boolean placeholder) throws Exception {
        String refName = null;

        if (placeholder) {
            // resolve property placeholders
            name = context.resolvePropertyPlaceholders(name);
            if (value instanceof String) {
                // resolve property placeholders
                value = context.resolvePropertyPlaceholders(value.toString());
            }
        }

        // if name has dot then we need to OGNL walk it
        if (nesting) {
            if (name.indexOf('.') > 0) {
                String[] parts = name.split("\\.");
                Object newTarget = target;
                Class<?> newClass = target.getClass();
                // we should only iterate until until 2nd last so we use -1 in the for loop
                for (int i = 0; i < parts.length - 1; i++) {
                    String part = parts[i];
                    Object prop = getOrElseProperty(newTarget, part, null);
                    if (prop == null) {
                        // okay is there a setter so we can create a new instance and set it automatic
                        Method method = findBestSetterMethod(newClass, part, fluentBuilder);
                        if (method != null) {
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
                target = newTarget;
                name = parts[parts.length - 1];
            }
        }

        if (reference && value instanceof String) {
            if (value.toString().startsWith("#class:")) {
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
            } else if (value.toString().equals("#autowire")) {
                // we should get the type from the setter
                Method method = findBestSetterMethod(target.getClass(), name, fluentBuilder);
                if (method != null) {
                    Class<?> parameterType = method.getParameterTypes()[0];
                    if (parameterType != null) {
                        Set<?> types = context.getRegistry().findByType(parameterType);
                        if (types.size() == 1) {
                            value = types.iterator().next();
                        }
                    }
                }
            } else if (value.toString().startsWith("#id:")) {
                // okay its a reference so swap to lookup this which is already supported in IntrospectionSupport
                refName = ((String) value).substring(4);
                value = null;
            } else if (EndpointHelper.isReferenceParameter(value.toString())) {
                // okay its a reference so swap to lookup this which is already supported in IntrospectionSupport
                refName = value.toString();
                value = null;
            }
        }

        return IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, value, refName, fluentBuilder);
    }

    private static Method findBestSetterMethod(Class clazz, String name, boolean fluentBuilder) {
        // is there a direct setter?
        Set<Method> candidates = findSetterMethods(clazz, name, false);
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // okay now try with builder pattern
        if (fluentBuilder) {
            candidates = findSetterMethods(clazz, name, true);
            if (candidates.size() == 1) {
                return candidates.iterator().next();
            }
        }

        return null;
    }

    private static Class getGetterType(Object target, String name) {
        try {
            Method getter = IntrospectionSupport.getPropertyGetter(target.getClass(), name);
            if (getter != null) {
                return getter.getReturnType();
            }
        } catch (NoSuchMethodException e) {
            // ignore
        }
        return null;
    }

    private static boolean isComplexUserType(Class type) {
        // lets consider all non java, as complex types
        return type != null && !type.isPrimitive() && !type.getName().startsWith("java");
    }

}
