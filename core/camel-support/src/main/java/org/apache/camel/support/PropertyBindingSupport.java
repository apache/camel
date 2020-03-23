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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

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
 *     <li>reference new class - Values can refer to creating new beans by their class name by prefixing with #class, eg #class:com.foo.MyClassType.
 *                               The class is created using a default no-arg constructor, however if you need to create the instance via a factory method
 *                               then you specify the method as shown: #class:com.foo.MyClassType#myFactoryMethod.
 *                               Or if you need to create the instance via constructor parameters then you can specify the parameters as shown:
 *                               #class:com.foo.MyClass('Hello World', 5, true)</li>.
 *     <li>ignore case - Whether to ignore case for property keys<li>
 * </ul>
 *
 * Keys can be marked as optional if the key name starts with a question mark, such as:
 * <pre>
 * foo=123
 * ?bar=false
 * </pre>
 * Where foo is mandatory, and bar is optional.
 */
public final class PropertyBindingSupport {

    /**
     * To use a fluent builder style to configure this property binding support.
     */
    public static class Builder {

        private CamelContext camelContext;
        private Object target;
        private Map<String, Object> properties;
        private boolean removeParameters = true;
        private boolean mandatory;
        private boolean nesting = true;
        private boolean deepNesting = true;
        private boolean reference = true;
        private boolean placeholder = true;
        private boolean fluentBuilder = true;
        private boolean allowPrivateSetter = true;
        private boolean ignoreCase;
        private String optionPrefix;
        private PropertyConfigurer configurer;

        /**
         * CamelContext to be used
         */
        public Builder withCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
            return this;
        }

        /**
         * Target object that should have parameters bound
         */
        public Builder withTarget(Object target) {
            this.target = target;
            return this;
        }

        /**
         * The properties to use for binding
         */
        public Builder withProperties(Map<String, Object> properties) {
            if (this.properties == null) {
                this.properties = properties;
            } else {
                // there may be existing options so add those if missing
                // we need to mutate existing as we are may be removing bound properties
                this.properties.forEach(properties::putIfAbsent);
                this.properties = properties;
            }
            return this;
        }

        /**
         * Adds property to use for binding
         */
        public Builder withProperty(String key, Object value) {
            if (this.properties == null) {
                this.properties = new LinkedHashMap<>();
            }
            this.properties.put(key, value);
            return this;
        }

        /**
         * Whether parameters should be removed when its bound
         */
        public Builder withRemoveParameters(boolean removeParameters) {
            this.removeParameters = removeParameters;
            return this;
        }

        /**
         * Whether all parameters should be mandatory and successfully bound
         */
        public Builder withMandatory(boolean mandatory) {
            this.mandatory = mandatory;
            return this;
        }

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
        public Builder withAllowPrivateSetter(boolean allowPrivateSetter) {
            this.allowPrivateSetter = allowPrivateSetter;
            return this;
        }

        /**
         * Whether to ignore case in the property names (keys).
         */
        public Builder withIgnoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }

        /**
         * Whether properties should be filtered by prefix.
         * Note that the prefix is removed from the key before the property is bound.
         */
        public Builder withOptionPrefix(String optionPrefix) {
            this.optionPrefix = optionPrefix;
            return this;
        }

        /**
         * Whether to use the configurer to configure the properties.
         */
        public Builder withConfigurer(PropertyConfigurer configurer) {
            this.configurer = configurer;
            return this;
        }

        /**
         * Binds the properties to the target object, and removes the property that was bound from properties.
         *
         * @return true if one or more properties was bound
         */
        public boolean bind() {
            // mandatory parameters
            org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
            org.apache.camel.util.ObjectHelper.notNull(target, "target");
            org.apache.camel.util.ObjectHelper.notNull(properties, "properties");

            return doBindProperties(camelContext, target,  removeParameters ? properties : new HashMap<>(properties),
                    optionPrefix, ignoreCase, true, mandatory,
                    nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder, configurer);
        }

        /**
         * Binds the properties to the target object, and removes the property that was bound from properties.
         *
         * @param camelContext the camel context
         * @param target       the target object
         * @param properties   the properties where the bound properties will be removed from
         * @return true if one or more properties was bound
         */
        public boolean bind(CamelContext camelContext, Object target, Map<String, Object> properties) {
            CamelContext context = camelContext != null ? camelContext : this.camelContext;
            Object obj = target != null ? target : this.target;
            Map<String, Object> prop = properties != null ? properties : this.properties;

            return doBindProperties(context, obj, removeParameters ? prop : new HashMap<>(prop),
                    optionPrefix, ignoreCase, true, mandatory,
                    nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder, configurer);
        }

        /**
         * Binds the property to the target object.
         *
         * @param camelContext the camel context
         * @param target       the target object
         * @param key          the property key
         * @param value        the property value
         * @return true if the property was bound
         */
        public boolean bind(CamelContext camelContext, Object target, String key, Object value) {
            Map<String, Object> properties = new HashMap<>(1);
            properties.put(key, value);

            return doBindProperties(camelContext, target, properties, optionPrefix, ignoreCase, true, mandatory,
                    nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder, configurer);
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
         * @param target       the targeted bean
         * @param propertyName the name of the property
         * @param propertyType the type of the property
         * @param value        the property value
         */
        void onAutowire(Object target, String propertyName, Class propertyType, Object value);

    }

    /**
     * This will discover all the properties on the target, and automatic bind the properties that are null by
     * looking up in the registry to see if there is a single instance of the same type as the property.
     * This is used for convention over configuration to automatic configure resources such as DataSource, Amazon Logins and
     * so on.
     *
     * @param camelContext the camel context
     * @param target       the target object
     * @return true if one ore more properties was auto wired
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
     * @param camelContext the camel context
     * @param target       the target object
     * @param bindNullOnly whether to only autowire if the property has no default value or has not been configured explicit
     * @param deepNesting  whether to attempt to walk as deep down the object graph by creating new empty objects on the way if needed (Camel can only create
     *                     new empty objects if they have a default no-arg constructor, also mind that this may lead to creating many empty objects, even
     *                     if they will not have any objects autowired from the registry, so use this with caution)
     * @param callback     optional callback when a property was auto wired
     * @return true if one ore more properties was auto wired
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

    private static boolean doAutowireSingletonPropertiesFromRegistry(final CamelContext camelContext, Object target, Set<Object> parents,
                                                                     boolean bindNullOnly, boolean deepNesting, OnAutowiring callback) throws Exception {

        // properties of all the current values from the target
        Map<String, Object> properties = new LinkedHashMap<>();

        // if there a configurer
        PropertyConfigurer configurer = null;
        PropertyConfigurerGetter getter = null;
        if (target instanceof Component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(target);
            configurer = ((Component) target).getComponentPropertyConfigurer();
        }
        if (configurer == null) {
            String name = target.getClass().getSimpleName();
            if (target instanceof ExtendedCamelContext) {
                // special for camel context itself as we have an extended configurer
                name = "ExtendedCamelContext";
            }
            // see if there is a configurer for it
            configurer = camelContext.adapt(ExtendedCamelContext.class).getConfigurerResolver().resolvePropertyConfigurer(name, camelContext);
        }

        // use configurer to get all the current options and its values
        Map<String, Object> getterAllOption = null;
        if (configurer instanceof PropertyConfigurerGetter) {
            getter = (PropertyConfigurerGetter) configurer;
            final PropertyConfigurerGetter lambdaGetter = getter;
            final Object lambdaTarget = target;
            getterAllOption = getter.getAllOptions(target);
            getterAllOption.forEach((key, type) -> {
                // we only need the complex types
                if (isComplexUserType((Class) type)) {
                    Object value = lambdaGetter.getOptionValue(lambdaTarget, key, true);
                    properties.put(key, value);
                }
            });
        } else {
            // okay use reflection based
            camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection().getProperties(target, properties, null);
        }

        boolean hit = false;

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // skip based on some known names
            if ("basicPropertyBinding".equals(key) || "bridgeErrorHandler".equals(key) || "lazyStartProducer".equals(key)) {
                continue;
            }

            boolean skip = parents.contains(value) || value instanceof CamelContext;
            if (skip) {
                // we have already covered this as parent of parents so dont walk down this as we want to avoid
                // circular dependencies when walking the OGNL graph, also we dont want to walk down CamelContext
                continue;
            }

            Class<?> type;
            if (getterAllOption != null) {
                // use getter configurer to know the property class type
                type = (Class<?>) getterAllOption.get(key);
            } else {
                // okay fallback to use reflection based
                type = getGetterType(camelContext, target, key, false);
            }
            if (type != null && CamelContext.class.isAssignableFrom(type)) {
                // the camel context is usually bound by other means so don't bind it to the target object
                // and most important do not walk it down and re-configure it.
                //
                // In some cases, such as Camel Quarkus, the Registry and the Context itself are added to
                // the IoC Container and an attempt to auto re-wire the Context may ends up in a circular
                // reference and a subsequent stack overflow.
                continue;
            }

            if (isComplexUserType(type)) {
                // if the property has not been set and its a complex type (not simple or string etc)
                if (!bindNullOnly || value == null) {
                    Set lookup = camelContext.getRegistry().findByType(type);
                    if (lookup.size() == 1) {
                        value = lookup.iterator().next();
                        if (value != null) {
                            if (configurer != null) {
                                // favour using source code generated configurer
                                hit = configurer.configure(camelContext, target, key, value, true);
                            }
                            if (!hit) {
                                // fallback to use reflection based
                                hit = camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection().setProperty(camelContext, target, key, value);
                            }
                            if (hit && callback != null) {
                                callback.onAutowire(target, key, type, value);
                            }
                        }
                    }
                }

                // attempt to create new instances to walk down the tree if its null (deepNesting option)
                if (value == null && deepNesting) {
                    // okay is there a setter so we can create a new instance and set it automatic
                    Method method = findBestSetterMethod(camelContext, target.getClass(), key, true, true, false);
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
                } else if (value != null && deepNesting) {
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
     * <p/>
     * This method uses the default settings, and if you need to configure any setting then use
     * the fluent builder {@link #build()} where each option can be customized, such as whether parameter
     * should be removed, or whether options are mandatory etc.
     *
     * @param camelContext the camel context
     * @param target       the target object
     * @param properties   the properties where the bound properties will be removed from
     * @return true if one or more properties was bound
     * @see #build()
     */
    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties) {
        // mandatory parameters
        org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
        org.apache.camel.util.ObjectHelper.notNull(target, "target");
        org.apache.camel.util.ObjectHelper.notNull(properties, "properties");

        return PropertyBindingSupport.build().bind(camelContext, target, properties);
    }

    /**
     * Binds the properties with the given prefix to the target object, and removes the property that was bound from properties.
     * Note that the prefix is removed from the key before the property is bound.
     *
     * @param camelContext       the camel context
     * @param target             the target object
     * @param properties         the properties where the bound properties will be removed from
     * @param optionPrefix       the prefix used to filter properties
     * @param ignoreCase         whether to ignore case for property keys
     * @param removeParameter    whether to remove bound parameters
     * @param mandatory          whether all parameters must be bound
     * @param nesting            whether nesting is in use
     * @param deepNesting        whether deep nesting is in use, where Camel will attempt to walk as deep as possible by creating new objects in the OGNL graph if
     *                           a property has a setter and the object can be created from a default no-arg constructor.
     * @param fluentBuilder      whether fluent builder is allowed as a valid getter/setter
     * @param allowPrivateSetter whether autowiring components allows to use private setter method when setting the value
     * @param reference          whether reference parameter (syntax starts with #) is in use
     * @param placeholder        whether to use Camels property placeholder to resolve placeholders on keys and values
     * @param configurer         to use an optional {@link org.apache.camel.spi.PropertyConfigurer} to configure the properties
     * @return true if one or more properties was bound
     */
    private static boolean doBindProperties(CamelContext camelContext, Object target, Map<String, Object> properties,
                                            String optionPrefix, boolean ignoreCase, boolean removeParameter, boolean mandatory,
                                            boolean nesting, boolean deepNesting, boolean fluentBuilder, boolean allowPrivateSetter,
                                            boolean reference, boolean placeholder,
                                            PropertyConfigurer configurer) {

        final String uOptionPrefix = ignoreCase && isNotEmpty(optionPrefix) ? optionPrefix.toUpperCase(Locale.US) : "";
        final int size = properties.size();

        // use configuer first to set the options it can do
        if (configurer != null) {
            for (Iterator<Map.Entry<String, Object>> iter = properties.entrySet().iterator(); iter.hasNext();) {
                Map.Entry<String, Object> entry = iter.next();
                String key = entry.getKey();
                Object value = entry.getValue();

                final boolean optional = key.startsWith("?");
                if (optional) {
                    key = key.substring(1);
                }

                // property configurer does not support nested names so skip if the name has a dot
                if (key.indexOf('.') == -1) {
                    try {
                        // PropertyConfigurer works by invoking the methods directly but it does
                        // not resolve property placeholders eventually defined in the value before invoking
                        // the setter.
                        if (value instanceof String) {
                            value = camelContext.resolvePropertyPlaceholders((String) value);
                        }
                        value = resolveValue(camelContext, target, key, value, ignoreCase, fluentBuilder, allowPrivateSetter);
                        boolean hit = configurer.configure(camelContext, target, key, value, ignoreCase);
                        if (removeParameter && hit) {
                            iter.remove();
                        }
                    } catch (Exception e) {
                        throw new PropertyBindingException(target, key, value, e);
                    }
                }
            }
        }

        // then we must set reference parameters before the other bindings
        setReferenceProperties(camelContext, target, properties);

        // sort the keys by nesting level and set the remainder
        properties.keySet().stream()
            .sorted(Comparator.comparingInt(s -> StringHelper.countChar(s, '.')))
            .forEach(key -> {
                Object text = properties.get(key);
                final String propertyKey = key;
                final boolean optional = key.startsWith("?");
                if (optional) {
                    key = key.substring(1);
                }
                if (placeholder) {
                    // resolve property placeholders
                    key = camelContext.resolvePropertyPlaceholders(key);
                    if (text instanceof String) {
                        // resolve property placeholders
                        text = camelContext.resolvePropertyPlaceholders(text.toString());
                    }
                }
                final Object value = text;

                if (isNotEmpty(optionPrefix)) {
                    boolean match = key.startsWith(optionPrefix) || ignoreCase && key.toUpperCase(Locale.US).startsWith(uOptionPrefix);
                    if (!match) {
                        return;
                    }
                    key = key.substring(optionPrefix.length());
                }

                boolean bound = false;
                if (configurer != null) {
                    // attempt configurer first
                    try {
                        bound = configurer.configure(camelContext, target, key, value, ignoreCase);
                    } catch (Exception e) {
                        throw new PropertyBindingException(target, key, value, e);
                    }
                }
                if (!bound) {
                    bound = bindProperty(camelContext, target, key, value, ignoreCase, nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder);
                }
                if (bound && removeParameter) {
                    properties.remove(propertyKey);
                }
                if (mandatory && !optional && !bound) {
                    throw new PropertyBindingException(target, propertyKey, value);
                }
            }
        );

        return properties.size() != size;
    }

    private static boolean bindProperty(CamelContext camelContext, Object target, String name, Object value,
                                        boolean ignoreCase, boolean nesting, boolean deepNesting, boolean fluentBuilder,
                                        boolean allowPrivateSetter, boolean reference, boolean placeholder) {
        try {
            if (target != null && name != null) {
                return setProperty(camelContext, target, name, value, false, ignoreCase, nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder);
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, name, value, e);
        }

        return false;
    }

    private static Object resolveValue(CamelContext context, Object target, String name, Object value,
                                       boolean ignoreCase, boolean fluentBuilder, boolean allowPrivateSetter) throws Exception {
        if (value instanceof String) {
            if (value.toString().equals("#autowired")) {
                // we should get the type from the setter
                Method method = findBestSetterMethod(context, target.getClass(), name, fluentBuilder, allowPrivateSetter, ignoreCase);
                if (method != null) {
                    Class<?> parameterType = method.getParameterTypes()[0];
                    Set<?> types = context.getRegistry().findByType(parameterType);
                    if (types.size() == 1) {
                        value = types.iterator().next();
                    } else if (types.size() > 1) {
                        throw new IllegalStateException("Cannot select single type: " + parameterType + " as there are " + types.size() + " beans in the registry with this type");
                    } else {
                        throw new IllegalStateException("Cannot select single type: " + parameterType + " as there are no beans in the registry with this type");
                    }
                } else {
                    throw new IllegalStateException("Cannot find setter method with name: " + name + " on class: " + target.getClass().getName() + " to use for autowiring");
                }
            } else {
                value = resolveBean(context, name, value);
            }
        }
        return value;
    }

    private static boolean setProperty(CamelContext context, Object target, String name, Object value, boolean mandatory,
                                       boolean ignoreCase, boolean nesting, boolean deepNesting, boolean fluentBuilder,
                                       boolean allowPrivateSetter, boolean reference, boolean placeholder) throws Exception {
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
                    Object prop = getOrElseProperty(context, newTarget, part, null, ignoreCase);
                    if (prop == null) {
                        if (!deepNesting) {
                            // okay we cannot go further down
                            break;
                        }
                        // okay is there a setter so we can create a new instance and set it automatic
                        Method method = findBestSetterMethod(context, newClass, part, fluentBuilder, allowPrivateSetter, ignoreCase);
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
            if (value.toString().startsWith("#bean:")) {
                // okay its a reference so swap to lookup this which is already supported in IntrospectionSupport
                refName = "#" + ((String) value).substring(6);
                value = null;
            } else {
                value = resolveValue(context, target, name, value, ignoreCase, fluentBuilder, allowPrivateSetter);
            }
        }
        boolean hit = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().setProperty(context, context.getTypeConverter(), target, name, value, refName, fluentBuilder, allowPrivateSetter, ignoreCase);
        if (!hit && mandatory) {
            // there is no setter with this given name, so lets report this as a problem
            throw new IllegalArgumentException("Cannot find setter method: " + name + " on bean: " + target + " of type: " + target.getClass().getName() + " when binding property: " + ognlPath);
        }
        return hit;
    }

    private static Object getOrElseProperty(CamelContext context, Object target, String property, Object defaultValue, boolean ignoreCase) {
        String key = property;
        String lookupKey = null;

        // support maps in keys
        if (property.contains("[") && property.endsWith("]")) {
            int pos = property.indexOf('[');
            lookupKey = property.substring(pos + 1, property.length() - 1);
            key = property.substring(0, pos);
        }

        Object answer = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getOrElseProperty(target, key, defaultValue, ignoreCase);
        if (answer instanceof Map && lookupKey != null) {
            Map map = (Map) answer;
            answer = map.getOrDefault(lookupKey, defaultValue);
        } else if (answer instanceof List) {
            List list = (List) answer;
            if (isNotEmpty(lookupKey)) {
                int idx = Integer.parseInt(lookupKey);
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

    private static Method findBestSetterMethod(CamelContext context, Class clazz, String name,
                                               boolean fluentBuilder, boolean allowPrivateSetter, boolean ignoreCase) {
        // is there a direct setter?
        Set<Method> candidates = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().findSetterMethods(clazz, name, false, allowPrivateSetter, ignoreCase);
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // okay now try with builder pattern
        if (fluentBuilder) {
            candidates = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().findSetterMethods(clazz, name, fluentBuilder, allowPrivateSetter, ignoreCase);
            if (candidates.size() == 1) {
                return candidates.iterator().next();
            }
        }

        return null;
    }

    private static Class getGetterType(CamelContext context, Object target, String name, boolean ignoreCase) {
        try {
            if (ignoreCase) {
                Method getter = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getPropertyGetter(target.getClass(), name, true);
                if (getter != null) {
                    return getter.getReturnType();
                }
            } else {
                Method getter = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getPropertyGetter(target.getClass(), name, false);
                if (getter != null) {
                    return getter.getReturnType();
                }
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

            final boolean optional = name.startsWith("?");
            if (optional) {
                name = name.substring(1);
            }

            // we only support basic keys
            if (name.contains(".") || name.contains("[") || name.contains("]")) {
                continue;
            }

            Object v = entry.getValue();
            String value = v != null ? v.toString() : null;
            if (isReferenceParameter(value)) {
                try {
                    boolean hit = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().setProperty(context, context.getTypeConverter(), target, name, null, value, true, false, false);
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

    private static Object newInstanceConstructorParameters(CamelContext camelContext, Class<?> type, String parameters) throws Exception {
        String[] params = StringQuoteHelper.splitSafeQuote(parameters, ',');
        Constructor found = findMatchingConstructor(type.getConstructors(), params);
        if (found != null) {
            Object[] arr = new Object[found.getParameterCount()];
            for (int i = 0; i < found.getParameterCount(); i++) {
                Class<?> paramType = found.getParameterTypes()[i];
                Object param = params[i];
                Object val = camelContext.getTypeConverter().convertTo(paramType, param);
                // unquote text
                if (val instanceof String) {
                    val = StringHelper.removeLeadingAndEndingQuotes((String) val);
                }
                arr[i] = val;
            }
            return found.newInstance(arr);
        }
        return null;
    }

    /**
     * Finds the best matching constructor for the given parameters.
     * <p/>
     * This implementation is similar to the logic in camel-bean.
     *
     * @param constructors the constructors
     * @param params       the parameters
     * @return the constructor, or null if no matching constructor can be found
     */
    private static Constructor findMatchingConstructor(Constructor<?>[] constructors, String[] params) {
        List<Constructor> candidates = new ArrayList<>();
        Constructor fallbackCandidate = null;

        for (Constructor ctr : constructors) {
            if (ctr.getParameterCount() != params.length) {
                continue;
            }

            boolean matches = true;
            for (int i = 0; i < ctr.getParameterCount(); i++) {
                String parameter = params[i];
                if (parameter != null) {
                    // must trim
                    parameter = parameter.trim();
                }

                Class<?> parameterType = getValidParameterType(parameter);
                Class<?> expectedType = ctr.getParameterTypes()[i];

                if (parameterType != null && expectedType != null) {
                    // skip java.lang.Object type, when we have multiple possible methods we want to avoid it if possible
                    if (Object.class.equals(expectedType)) {
                        fallbackCandidate = ctr;
                        matches = false;
                        break;
                    }

                    boolean matchingTypes = isParameterMatchingType(parameterType, expectedType);
                    if (!matchingTypes) {
                        matches = false;
                        break;
                    }
                }
            }

            if (matches) {
                candidates.add(ctr);
            }
        }

        return candidates.size() == 1 ? candidates.get(0) : fallbackCandidate;
    }

    /**
     * Determines and maps the given value is valid according to the supported
     * values by the bean component.
     * <p/>
     * This implementation is similar to the logic in camel-bean.
     *
     * @param value the value
     * @return the parameter type the given value is being mapped as, or <tt>null</tt> if not valid.
     */
    private static Class<?> getValidParameterType(String value) {
        if (org.apache.camel.util.ObjectHelper.isEmpty(value)) {
            return null;
        }

        // trim value
        value = value.trim();

        // single quoted is valid
        if (value.startsWith("'") && value.endsWith("'")) {
            return String.class;
        }

        // double quoted is valid
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return String.class;
        }

        // true or false is valid (boolean)
        if (value.equals("true") || value.equals("false")) {
            return Boolean.class;
        }

        // null is valid (to force a null value)
        if (value.equals("null")) {
            return Object.class;
        }

        // simple language tokens is valid
        if (StringHelper.hasStartToken(value, "simple")) {
            return Object.class;
        }

        // numeric is valid
        boolean numeric = true;
        for (char ch : value.toCharArray()) {
            if (!Character.isDigit(ch)) {
                numeric = false;
                break;
            }
        }
        if (numeric) {
            return Number.class;
        }

        // not valid
        return null;
    }

    private static boolean isParameterMatchingType(Class<?> parameterType, Class<?> expectedType) {
        if (Number.class.equals(parameterType)) {
            // number should match long/int/etc.
            if (Integer.class.isAssignableFrom(expectedType) || Long.class.isAssignableFrom(expectedType)
                    || int.class.isAssignableFrom(expectedType) || long.class.isAssignableFrom(expectedType)) {
                return true;
            }
        }
        if (Boolean.class.equals(parameterType)) {
            // boolean should match both Boolean and boolean
            if (Boolean.class.isAssignableFrom(expectedType) || boolean.class.isAssignableFrom(expectedType)) {
                return true;
            }
        }
        return parameterType.isAssignableFrom(expectedType);
    }

    /**
     * Resolves the value as either a class, type or bean.
     *
     * @param camelContext       the camel context
     * @param name               the name of the bean
     * @param value              how to resolve the bean with a prefix of either class#:, type#: or bean#:
     * @return the resolve bean
     * @throws Exception is thrown if error resolving the bean, or if the value is invalid.
     */
    public static Object resolveBean(CamelContext camelContext, String name, Object value) throws Exception {
        if (value.toString().startsWith("#class:")) {
            // its a new class to be created
            String className = value.toString().substring(7);
            String factoryMethod = null;
            String parameters = null;
            if (className.endsWith(")") && className.indexOf('(') != -1) {
                parameters = StringHelper.after(className, "(");
                parameters = parameters.substring(0, parameters.length() - 1); // clip last )
                className = StringHelper.before(className, "(");
            }
            if (className != null && className.indexOf('#') != -1) {
                factoryMethod = StringHelper.after(className, "#");
                className = StringHelper.before(className, "#");
            }
            Class<?> type = camelContext.getClassResolver().resolveMandatoryClass(className);
            if (factoryMethod != null) {
                value = camelContext.getInjector().newInstance(type, factoryMethod);
            } else if (parameters != null) {
                // special to support constructor parameters
                value = newInstanceConstructorParameters(camelContext, type, parameters);
            } else {
                value = camelContext.getInjector().newInstance(type);
            }
            if (value == null) {
                throw new IllegalStateException("Cannot create instance of class: " + className);
            }
        } else if (value.toString().startsWith("#type:")) {
            // its reference by type, so lookup the actual value and use it if there is only one instance in the registry
            String typeName = value.toString().substring(6);
            Class<?> type = camelContext.getClassResolver().resolveMandatoryClass(typeName);
            Set<?> types = camelContext.getRegistry().findByType(type);
            if (types.size() == 1) {
                value = types.iterator().next();
            } else if (types.size() > 1) {
                throw new IllegalStateException("Cannot select single type: " + typeName + " as there are " + types.size() + " beans in the registry with this type");
            } else {
                throw new IllegalStateException("Cannot select single type: " + typeName + " as there are no beans in the registry with this type");
            }
        } else if (value.toString().startsWith("#bean:")) {
            String key = value.toString().substring(6);
            value = camelContext.getRegistry().lookupByName(key);
        }

        return value;
    }

}
