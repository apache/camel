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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.PropertyBindingException;

import static org.apache.camel.support.IntrospectionSupport.findSetterMethods;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * A convenient support class for binding String valued properties to an instance which
 * uses a set of conventions:
 * <ul>
 *     <li>property placeholders - Keys and values using Camels property placeholder will be resolved</li>
 *     <li>nested - Properties can be nested using the dot syntax (OGNL and builder pattern using with as prefix), eg foo.bar=123</li>
 *     <li>map</li> - Properties can lookup in Map's using map syntax, eg foo[bar] where foo is the name of the property that is a Map instance, and bar is the name of the key.</li>
 *     <li>list</li> - Properties can refer or add to in List's using list syntax, eg foo[0] where foo is the name of the property that is a
 *                     List instance, and 0 is the index. To refer to the last element, then use last as key.</li>
 *     <li>reference by bean id - Values can refer to other beans in the registry by prefixing with with # or #bean: eg #myBean or #bean:myBean</li>
 *     <li>reference by type - Values can refer to singleton beans by their type in the registry by prefixing with #type: syntax, eg #type:com.foo.MyClassType</li>
 *     <li>autowire by type - Values can refer to singleton beans by auto wiring by setting the value to #autowired</li>
 *     <li>reference new class - Values can refer to creating new beans by their class name by prefixing with #class, eg #class:com.foo.MyClassType</li>
 * </ul>
 * <p/>
 * This implementations reuses parts of {@link IntrospectionSupport}.
 */
public final class PropertyBindingSupport {

    /**
     * To use a fluent builder style to configure this property binding support.
     */
    public static class Builder {

        private boolean nesting = true;
        private boolean deepNesting = true;
        private boolean reference = true;
        private boolean placeholder = true;
        private boolean fluentBuilder = true;
        private boolean allowPrivateSetter = true;
        private String optionPrefix;

        /**
         * Whether nesting is in use
         */
        public Builder withNesting(boolean nesting) {
            this.nesting = nesting;
            return this;
        }

        /**
         * Whether deep nesting is in use, where Camel will attempt to walk as deep as possible by creating new objects in the OGNL graph if
         * a property has a setter and the object can be created from a default no-arg constructor.
         */
        public Builder withDeepNesting(boolean deepNesting) {
            this.deepNesting = deepNesting;
            return this;
        }

        /**
         * Whether reference parameter (syntax starts with #) is in use
         */
        public Builder withReference(boolean reference) {
            this.reference = reference;
            return this;
        }

        /**
         * Whether to use Camels property placeholder to resolve placeholders on keys and values
         */
        public Builder withPlaceholder(boolean placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Whether fluent builder is allowed as a valid getter/setter
         */
        public Builder withFluentBuilder(boolean fluentBuilder) {
            this.fluentBuilder = fluentBuilder;
            return this;
        }

        /**
         * Whether properties should be filtered by prefix.         *
         * Note that the prefix is removed from the key before the property is bound.
         */
        public Builder withOptionPrefix(String optionPrefix) {
            this.optionPrefix = optionPrefix;
            return this;
        }

        /**
         * Whether properties should be filtered by prefix.         *
         * Note that the prefix is removed from the key before the property is bound.
         */
        public Builder withAllowPrivateSetter(boolean allowPrivateSetter) {
            this.allowPrivateSetter = allowPrivateSetter;
            return this;
        }

        /**
         * Binds the properties to the target object, and removes the property that was bound from properties.
         *
         * @param camelContext  the camel context
         * @param target        the target object
         * @param properties    the properties where the bound properties will be removed from
         * @return              true if one or more properties was bound
         */
        public boolean bind(CamelContext camelContext, Object target, Map<String, Object> properties) {
            org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
            org.apache.camel.util.ObjectHelper.notNull(target, "target");
            org.apache.camel.util.ObjectHelper.notNull(properties, "properties");

            return bindProperties(camelContext, target, properties, optionPrefix, nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder);
        }

    }

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
        return autowireSingletonPropertiesFromRegistry(camelContext, target, false, false, null);
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
     * @param deepNesting   whether to attempt to walk as deep down the object graph by creating new empty objects on the way if needed (Camel can only create
     *                      new empty objects if they have a default no-arg constructor, also mind that this may lead to creating many empty objects, even
     *                      if they will not have any objects autowired from the registry, so use this with caution)
     * @param callback      optional callback when a property was auto wired
     * @return              true if one ore more properties was auto wired
     */
    public static boolean autowireSingletonPropertiesFromRegistry(CamelContext camelContext, Object target,
                                                                  boolean bindNullOnly, boolean deepNesting, OnAutowiring callback) {
        try {
            if (target != null) {
                Set<Object> parents = new HashSet<>();
                return doAutowireSingletonPropertiesFromRegistry(camelContext, target, parents, bindNullOnly, deepNesting, callback);
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, e);
        }

        return false;
    }

    private static boolean doAutowireSingletonPropertiesFromRegistry(CamelContext camelContext, Object target, Set<Object> parents,
                                                                     boolean bindNullOnly, boolean deepNesting, OnAutowiring callback) throws Exception {

        Map<String, Object> properties = new LinkedHashMap<>();
        IntrospectionSupport.getProperties(target, properties, null);

        boolean hit = false;

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Class<?> type = getGetterType(target, key);

            boolean skip = parents.contains(value) || value instanceof CamelContext;
            if (skip) {
                // we have already covered this as parent of parents so dont walk down this as we want to avoid
                // circular dependencies when walking the OGNL graph, also we dont want to walk down CamelContext
                continue;
            }

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

                // attempt to create new instances to walk down the tree if its null (deepNesting option)
                if (value == null && deepNesting) {
                    // okay is there a setter so we can create a new instance and set it automatic
                    Method method = findBestSetterMethod(target.getClass(), key, true, true);
                    if (method != null) {
                        Class<?> parameterType = method.getParameterTypes()[0];
                        if (parameterType != null && org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(parameterType)) {
                            Object instance = camelContext.getInjector().newInstance(parameterType);
                            if (instance != null) {
                                org.apache.camel.support.ObjectHelper.invokeMethod(method, target, instance);
                                target = instance;
                                // remember this as parent and also autowire nested properties
                                // do not walk down if it point to our-selves (circular reference)
                                parents.add(target);
                                value = instance;
                                hit |= doAutowireSingletonPropertiesFromRegistry(camelContext, value, parents, bindNullOnly, deepNesting, callback);
                            }
                        }
                    }
                } else if (value != null) {
                    // remember this as parent and also autowire nested properties
                    // do not walk down if it point to our-selves (circular reference)
                    parents.add(target);
                    hit |= doAutowireSingletonPropertiesFromRegistry(camelContext, value, parents, bindNullOnly, deepNesting, callback);
                }
            }
        }

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
        return bindProperties(camelContext, target, properties, null);
    }

    /**
     * Binds the properties with the given prefix to the target object, and removes the property that was bound from properties.
     * Note that the prefix is removed from the key before the property is bound.
     *
     * @param camelContext  the camel context
     * @param target        the target object
     * @param properties    the properties where the bound properties will be removed from
     * @param optionPrefix  the prefix used to filter properties
     * @return              true if one or more properties was bound
     */
    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties, String optionPrefix) {
        return bindProperties(camelContext, target, properties, optionPrefix, true, true, true, true, true, true);
    }

    /**
     * Binds the properties with the given prefix to the target object, and removes the property that was bound from properties.
     *
     * @param camelContext        the camel context
     * @param target              the target object
     * @param properties          the properties where the bound properties will be removed from
     * @param nesting             whether nesting is in use
     * @param deepNesting         whether deep nesting is in use, where Camel will attempt to walk as deep as possible by creating new objects in the OGNL graph if
     *                            a property has a setter and the object can be created from a default no-arg constructor.
     * @param fluentBuilder       whether fluent builder is allowed as a valid getter/setter
     * @param allowPrivateSetter  whether autowiring components allows to use private setter method when setting the value
     * @param reference           whether reference parameter (syntax starts with #) is in use
     * @param placeholder         whether to use Camels property placeholder to resolve placeholders on keys and values
     * @return                    true if one or more properties was bound
     */
    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties,
                                         boolean nesting, boolean deepNesting, boolean fluentBuilder, boolean allowPrivateSetter,
                                         boolean reference, boolean placeholder) {

        return bindProperties(camelContext, target, properties, null, nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder);
    }

    /**
     * Binds the properties with the given prefix to the target object, and removes the property that was bound from properties.
     * Note that the prefix is removed from the key before the property is bound.
     *
     * @param camelContext        the camel context
     * @param target              the target object
     * @param properties          the properties where the bound properties will be removed from
     * @param optionPrefix        the prefix used to filter properties
     * @param nesting             whether nesting is in use
     * @param deepNesting         whether deep nesting is in use, where Camel will attempt to walk as deep as possible by creating new objects in the OGNL graph if
     *                            a property has a setter and the object can be created from a default no-arg constructor.
     * @param fluentBuilder       whether fluent builder is allowed as a valid getter/setter
     * @param allowPrivateSetter  whether autowiring components allows to use private setter method when setting the value
     * @param reference           whether reference parameter (syntax starts with #) is in use
     * @param placeholder         whether to use Camels property placeholder to resolve placeholders on keys and values
     * @return                    true if one or more properties was bound
     */
    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties,
                                         String optionPrefix,
                                         boolean nesting, boolean deepNesting, boolean fluentBuilder, boolean allowPrivateSetter,
                                         boolean reference, boolean placeholder) {
        org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
        org.apache.camel.util.ObjectHelper.notNull(target, "target");
        org.apache.camel.util.ObjectHelper.notNull(properties, "properties");
        boolean rc = false;

        // must set reference parameters first before the other bindings
        setReferenceProperties(camelContext, target, properties);

        for (Iterator<Map.Entry<String, Object>> iter = properties.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<String, Object> entry = iter.next();
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isNotEmpty(optionPrefix)) {
                if (!key.startsWith(optionPrefix)) {
                    continue;
                }
                key = key.substring(optionPrefix.length());
            }

            if (bindProperty(camelContext, target, key, value, nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder)) {
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
                return setProperty(camelContext, target, name, value, false, true, true, true, true, true, true);
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, name, e);
        }

        return false;
    }

    private static boolean bindProperty(CamelContext camelContext, Object target, String name, Object value,
                                        boolean nesting, boolean deepNesting, boolean fluentBuilder, boolean allowPrivateSetter, boolean reference, boolean placeholder) {
        try {
            if (target != null && name != null) {
                return setProperty(camelContext, target, name, value, false, nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder);
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
                boolean bound = setProperty(camelContext, target, name, value, true, true, true, true, true, true, true);
                if (!bound) {
                    throw new PropertyBindingException(target, name);
                }
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, name, e);
        }
    }

    private static boolean setProperty(CamelContext context, Object target, String name, Object value, boolean mandatory,
                                       boolean nesting, boolean deepNesting, boolean fluentBuilder, boolean allowPrivateSetter,
                                       boolean reference, boolean placeholder) throws Exception {
        String refName = null;

        if (placeholder) {
            // resolve property placeholders
            name = context.resolvePropertyPlaceholders(name);
            if (value instanceof String) {
                // resolve property placeholders
                value = context.resolvePropertyPlaceholders(value.toString());
            }
        }

        String ognlPath = name;

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
                        if (!deepNesting) {
                            // okay we cannot go further down
                            break;
                        }
                        // okay is there a setter so we can create a new instance and set it automatic
                        Method method = findBestSetterMethod(newClass, part, fluentBuilder, allowPrivateSetter);
                        if (method != null) {
                            Class<?> parameterType = method.getParameterTypes()[0];
                            Object instance = null;
                            if (parameterType != null && org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(parameterType)) {
                                instance = context.getInjector().newInstance(parameterType);
                            }
                            if (instance != null) {
                                org.apache.camel.support.ObjectHelper.invokeMethod(method, newTarget, instance);
                                newTarget = instance;
                                newClass = newTarget.getClass();
                            }
                        } else {
                            if (mandatory) {
                                // there is no getter with this given name, so lets report this as a problem
                                throw new IllegalArgumentException("Cannot find getter method: " + part + " on bean: " + newClass + " when binding property: " + ognlPath);
                            }
                        }
                    } else {
                        newTarget = prop;
                        newClass = newTarget.getClass();
                    }
                }
                if (newTarget != target) {
                    // okay we found a nested property, then lets change to use that
                    target = newTarget;
                    name = parts[parts.length - 1];
                }
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
            } else if (value.toString().equals("#autowired")) {
                // we should get the type from the setter
                Method method = findBestSetterMethod(target.getClass(), name, fluentBuilder, allowPrivateSetter);
                if (method != null) {
                    Class<?> parameterType = method.getParameterTypes()[0];
                    if (parameterType != null) {
                        Set<?> types = context.getRegistry().findByType(parameterType);
                        if (types.size() == 1) {
                            value = types.iterator().next();
                        }
                    }
                }
            } else if (value.toString().startsWith("#bean:")) {
                // okay its a reference so swap to lookup this which is already supported in IntrospectionSupport
                refName = "#" + ((String) value).substring(6);
                value = null;
            }
        }

        boolean hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, value, refName, fluentBuilder, allowPrivateSetter);
        if (!hit && mandatory) {
            // there is no setter with this given name, so lets report this as a problem
            throw new IllegalArgumentException("Cannot find setter method: " + name + " on bean: " + target + " when binding property: " + ognlPath);
        }
        return hit;
    }

    private static Object getOrElseProperty(Object target, String property, Object defaultValue) {
        String key = property;
        String lookupKey = null;

        // support maps in keys
        if (property.contains("[") && property.endsWith("]")) {
            int pos = property.indexOf('[');
            lookupKey = property.substring(pos + 1, property.length() - 1);
            key = property.substring(0, pos);
        }

        Object answer = IntrospectionSupport.getOrElseProperty(target, key, defaultValue);
        if (answer instanceof Map && lookupKey != null) {
            Map map = (Map) answer;
            answer = map.getOrDefault(lookupKey, defaultValue);
        } else if (answer instanceof List) {
            List list = (List) answer;
            if (isNotEmpty(lookupKey)) {
                int idx = Integer.valueOf(lookupKey);
                answer = list.get(idx);
            } else {
                if (list.isEmpty()) {
                    answer = null;
                } else {
                    answer = list.get(list.size() - 1);
                }
            }
        }

        return answer != null ? answer : defaultValue;
    }

    private static Method findBestSetterMethod(Class clazz, String name, boolean fluentBuilder, boolean allowPrivateSetter) {
        // is there a direct setter?
        Set<Method> candidates = findSetterMethods(clazz, name, fluentBuilder, allowPrivateSetter);
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // okay now try with builder pattern
        if (fluentBuilder) {
            candidates = findSetterMethods(clazz, name, fluentBuilder, allowPrivateSetter);
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
        return type != null && !type.isPrimitive() && !type.getName().startsWith("java.");
    }

    private static void setReferenceProperties(CamelContext context, Object target, Map<String, Object> parameters) {
        Iterator<Map.Entry<String, Object>> it = parameters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String name = entry.getKey();

            // we only support basic keys
            if (name.contains(".") || name.contains("[") || name.contains("]")) {
                continue;
            }

            Object v = entry.getValue();
            String value = v != null ? v.toString() : null;
            if (isReferenceParameter(value)) {
                try {
                    boolean hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, null, value, true);
                    if (hit) {
                        // must remove as its a valid option and we could configure it
                        it.remove();
                    }
                } catch (Exception e) {
                    throw new PropertyBindingException(target, e);
                }
            }
        }
    }

    /**
     * Is the given parameter a reference parameter (starting with a # char)
     *
     * @param parameter the parameter
     * @return <tt>true</tt> if its a reference parameter
     */
    private static boolean isReferenceParameter(String parameter) {
        return parameter != null && parameter.trim().startsWith("#");
    }


}
