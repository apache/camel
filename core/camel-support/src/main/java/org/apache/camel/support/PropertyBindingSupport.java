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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.StringHelper.startsWithIgnoreCase;

/**
 * A convenient support class for binding String valued properties to an instance which uses a set of conventions:
 * <ul>
 * <li>property placeholders - Keys and values using Camels property placeholder will be resolved</li>
 * <li>nested - Properties can be nested using the dot syntax (OGNL and builder pattern using with as prefix), eg
 * foo.bar=123</li>
 * <li>map</li> - Properties can lookup in Map's using map syntax, eg foo[bar] where foo is the name of the property
 * that is a Map instance, and bar is the name of the key.</li>
 * <li>list</li> - Properties can refer or add to in List's using list syntax, eg foo[0] where foo is the name of the
 * property that is a List instance, and 0 is the index. To refer to the last element, then use last as key.</li>
 * <li>reference by property placeholder id - Values can refer to a property placeholder key with #property:myKey</li>
 * <li>reference by bean id - Values can refer to other beans in the registry by prefixing with # or #bean: eg #myBean
 * or #bean:myBean. It is recommended to favour using `#bean:` syntax to make it obvious it's a bean reference.</li>
 * <li>reference by type - Values can refer to singleton beans by their type in the registry by prefixing with #type:
 * syntax, eg #type:com.foo.MyClassType</li>
 * <li>autowire by type - Values can refer to singleton beans by auto wiring by setting the value to #autowired</li>
 * <li>reference new class - Values can refer to creating new beans by their class name by prefixing with #class, eg
 * #class:com.foo.MyClassType. The class is created using a default no-arg constructor, however if you need to create
 * the instance via a factory method then you specify the method as shown: #class:com.foo.MyClassType#myFactoryMethod.
 * And if the factory method requires parameters they can be specified as follows:
 * #class:com.foo.MyClassType#myFactoryMethod('Hello World', 5, true). Or if you need to create the instance via
 * constructor parameters then you can specify the parameters as shown: #class:com.foo.MyClass('Hello World', 5, true).
 * If the factory method is on another bean or class, then you must specify this as shown:
 * #class:com.foo.MyClassType#com.foo.MyFactory:myFactoryMethod. Where com.foo.MyFactory either refers to an class name,
 * or can refer to an existing bean by id, such as: #class:com.foo.MyClassType#myFactoryBean:myFactoryMethod.</li>.
 * <li>valueAs(type):value</li> - To declare that the value should be converted to the given type, such as
 * #valueAs(int):123 which indicates that the value 123 should be converted to an integer.
 * <li>ignore case - Whether to ignore case for property keys</li>
 * </ul>
 *
 * <p>
 * Keys with dash style is supported and will internally be converted from dash to camel case style (eg
 * queue-connection-factory => queueConnectionFactory)
 * <p>
 * Keys can be marked as optional if the key name starts with a question mark, such as:
 *
 * <pre>
 * foo=123
 * ?bar=false
 * </pre>
 * <p>
 * Where foo is mandatory, and bar is optional.
 *
 * <p>
 * Values can be marked as optional property placeholder if the values name starts with a question mark, such as:
 *
 * <pre>
 * username={{?clientUserName}}
 * </pre>
 * <p>
 * Where the username property will only be set if the property placeholder <tt>clientUserName</tt> exists, otherwise
 * the username is not affected.
 * </p>
 */
public final class PropertyBindingSupport {

    private PropertyBindingSupport() {
    }

    public static Builder build() {
        return new Builder();
    }

    /**
     * Binds the properties to the target object, and removes the property that was bound from properties.
     * <p/>
     * This method uses the default settings, and if you need to configure any setting then use the fluent builder
     * {@link #build()} where each option can be customized, such as whether parameter should be removed, or whether
     * options are mandatory etc.
     *
     * @param  camelContext the camel context
     * @param  target       the target object
     * @param  properties   the properties (as flat key=value paris) where the bound properties will be removed
     * @return              true if one or more properties was bound
     * @see                 #build()
     */
    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties) {
        // mandatory parameters
        org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
        org.apache.camel.util.ObjectHelper.notNull(target, "target");
        org.apache.camel.util.ObjectHelper.notNull(properties, "properties");

        return PropertyBindingSupport.build().bind(camelContext, target, properties);
    }

    /**
     * Binds the properties to the target object, and removes the property that was bound from properties.
     * <p/>
     * This method uses the default settings, and if you need to configure any setting then use the fluent builder
     * {@link #build()} where each option can be customized, such as whether parameter should be removed, or whether
     * options are mandatory etc.
     *
     * @param  camelContext the camel context
     * @param  target       the target object
     * @param  properties   the properties as (map of maps) where the properties will be flattened, and bound properties
     *                      will be removed
     * @return              true if one or more properties was bound
     * @see                 #build()
     */
    public static boolean bindWithFlattenProperties(CamelContext camelContext, Object target, Map<String, Object> properties) {
        // mandatory parameters
        org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
        org.apache.camel.util.ObjectHelper.notNull(target, "target");
        org.apache.camel.util.ObjectHelper.notNull(properties, "properties");

        return PropertyBindingSupport.build().withFlattenProperties(true).bind(camelContext, target, properties);
    }

    /**
     * Sets the properties to the given target.
     *
     * @param context    the context into which the properties must be set.
     * @param target     the object to which the properties must be set.
     * @param properties the properties to set.
     */
    public static void setPropertiesOnTarget(CamelContext context, Object target, Map<String, Object> properties) {
        org.apache.camel.util.ObjectHelper.notNull(context, "context");
        org.apache.camel.util.ObjectHelper.notNull(target, "target");
        org.apache.camel.util.ObjectHelper.notNull(properties, "properties");

        if (target instanceof CamelContext) {
            throw new UnsupportedOperationException("Configuring the Camel Context is not supported");
        }

        PropertyConfigurer configurer = null;
        if (target instanceof Component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(target);
            configurer = ((Component) target).getComponentPropertyConfigurer();
        }

        if (configurer == null) {
            // see if there is a configurer for it
            configurer = PluginHelper.getConfigurerResolver(context)
                    .resolvePropertyConfigurer(target.getClass().getSimpleName(), context);
        }

        try {
            PropertyBindingSupport.build()
                    .withMandatory(true)
                    .withRemoveParameters(false)
                    .withConfigurer(configurer)
                    .withIgnoreCase(true)
                    .withFlattenProperties(true)
                    .bind(context, target, properties);
        } catch (PropertyBindingException e) {
            String key = e.getOptionKey();
            if (key == null) {
                String prefix = e.getOptionPrefix();
                if (prefix != null && !prefix.endsWith(".")) {
                    prefix = "." + prefix;
                }

                key = prefix != null
                        ? prefix + "." + e.getPropertyName()
                        : e.getPropertyName();
            }

            // enrich the error with more precise details with option prefix and key
            throw new PropertyBindingException(
                    e.getTarget(),
                    e.getPropertyName(),
                    e.getValue(),
                    null,
                    key,
                    e.getCause());
        }
    }

    /**
     * Binds the properties with the given prefix to the target object, and removes the property that was bound from
     * properties. Note that the prefix is removed from the key before the property is bound.
     *
     * @param  camelContext       the camel context
     * @param  target             the target object
     * @param  properties         the properties where the bound properties will be removed from
     * @param  optionPrefix       the prefix used to filter properties
     * @param  ignoreCase         whether to ignore case for property keys
     * @param  removeParameter    whether to remove bound parameters
     * @param  flattenProperties  whether properties should be flattened (when properties is a map of maps)
     * @param  mandatory          whether all parameters must be bound
     * @param  optional           whether parameters can be optional such as configuring endpoints that are lenient
     * @param  nesting            whether nesting is in use
     * @param  deepNesting        whether deep nesting is in use, where Camel will attempt to walk as deep as possible
     *                            by creating new objects in the OGNL graph if a property has a setter and the object
     *                            can be created from a default no-arg constructor.
     * @param  fluentBuilder      whether fluent builder is allowed as a valid getter/setter
     * @param  allowPrivateSetter whether autowiring components allows to use private setter method when setting the
     *                            value
     * @param  reference          whether reference parameter (syntax starts with #) is in use
     * @param  placeholder        whether to use Camels property placeholder to resolve placeholders on keys and values
     * @param  reflection         whether to allow using reflection (when there is no configurer available).
     * @param  configurer         to use an optional {@link PropertyConfigurer} to configure the properties
     * @param  listener           optional listener
     * @return                    true if one or more properties was bound
     */
    private static boolean doBindProperties(
            CamelContext camelContext, Object target, Map<String, Object> properties,
            String optionPrefix, boolean ignoreCase, boolean removeParameter, boolean flattenProperties,
            boolean mandatory, boolean optional,
            boolean nesting, boolean deepNesting, boolean fluentBuilder, boolean allowPrivateSetter,
            boolean reference, boolean placeholder,
            boolean reflection, PropertyConfigurer configurer,
            PropertyBindingListener listener) {

        if (properties == null || properties.isEmpty()) {
            return false;
        }

        if (flattenProperties) {
            properties = new FlattenMap(properties);
        }
        if (listener == null && camelContext != null) {
            listener = camelContext.getRegistry().findSingleByType(PropertyBindingListener.class);
        }

        boolean answer = false;

        if (optionPrefix != null) {
            properties = new OptionPrefixMap(properties, optionPrefix);
        }

        Map<String, Object> sorted;
        if (properties.size() > 1) {
            // need to process them in specific order so use a sorted map
            // and use our comparator
            sorted = new TreeMap<>(new PropertyBindingKeyComparator(properties));
            sorted.putAll(properties);
        } else {
            // no need to sort as there is only 1 element
            sorted = properties;
        }

        // process each property and bind it
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // if nesting is not allowed, then only bind properties without dots (OGNL graph)
            if (!nesting && isDotKey(key)) {
                continue;
            }

            // attempt to bind the property
            if (listener != null) {
                listener.bindProperty(target, key, value);
            }
            boolean hit = doBuildPropertyOgnlPath(camelContext, target, key, value, deepNesting, fluentBuilder,
                    allowPrivateSetter, ignoreCase, reference, placeholder, mandatory, optional, reflection, configurer);
            if (hit && removeParameter) {
                properties.remove(key);
            }
            answer |= hit;
        }

        return answer;
    }

    private static boolean doBuildPropertyOgnlPath(
            final CamelContext camelContext, final Object originalTarget, String name, final Object value,
            boolean deepNesting, boolean fluentBuilder, boolean allowPrivateSetter,
            boolean ignoreCase, boolean reference, boolean placeholder, boolean mandatory, boolean optional,
            boolean reflection, PropertyConfigurer configurer) {

        if (name.startsWith("?")) {
            // the name marks the option as optional
            name = name.substring(1);
            optional = true;
        }

        Object newTarget = originalTarget;
        Class<?> newClass = originalTarget.getClass();
        String newName = name;

        if (configurer == null) {
            // do we have a configurer by any chance
            configurer = PropertyConfigurerHelper.resolvePropertyConfigurer(camelContext, newClass);
        }

        // we should only walk and create OGNL path for the middle graph
        String[] parts;
        boolean quoted = StringHelper.isQuoted(name);
        if (quoted) {
            // remove quotes around the key
            name = StringHelper.removeLeadingAndEndingQuotes(name);
            newName = name;
            parts = new String[] { name };
        } else if (isDotKey(name)) {
            parts = splitKey(name);
        } else {
            parts = new String[] { name };
        }
        // last node should not be walked here (that happens later)
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object prop = null;
            // get ognl path for this part
            if (configurer != null) {
                prop = getOrCreatePropertyOgnlPathViaConfigurer(camelContext, newTarget, part, ignoreCase, configurer);
            }
            if (prop == null && reflection) {
                // no configurer or not possible with configurer so fallback and use reflection
                prop = getOrCreatePropertyOgnlPathViaReflection(camelContext, newTarget, part, ignoreCase);
            }
            if (prop == null) {
                if (!deepNesting) {
                    // okay we cannot go further down
                    return false;
                }
                // create ognl path for this part
                if (configurer != null) {
                    prop = attemptCreateNewInstanceViaConfigurer(camelContext, newTarget, part, ignoreCase,
                            configurer);
                }
                if (prop == null && reflection) {
                    // no configurer or not possible with configurer so fallback and use reflection
                    prop = attemptCreateNewInstanceViaReflection(camelContext, newTarget, newClass, part, fluentBuilder,
                            allowPrivateSetter,
                            ignoreCase);
                }
            }
            if (prop == null) {
                if (optional) {
                    return false;
                } else if (mandatory) {
                    // there is no getter with this given name, so lets report this as a problem
                    throw new IllegalArgumentException(
                            "Cannot find getter method: " + part + " on bean: " + newClass
                                                       + " when binding property: " + name);
                }
            } else {
                // okay ognl path is success (either get existing or created empty object)
                // now lets update the target/name/class before next iterator (next part)
                if (configurer instanceof PropertyConfigurerGetter) {
                    // lets see if we have a specialized configurer
                    String key = StringHelper.before(part, "[", part);

                    // if its a map/list/array type then find out what type the collection uses
                    // so we can use that to lookup as configurer
                    Class<?> collectionType = (Class<?>) ((PropertyConfigurerGetter) configurer)
                            .getCollectionValueType(newTarget, undashKey(key), ignoreCase);

                    if (collectionType == null) {
                        collectionType = prop.getClass();
                    }

                    configurer = PropertyConfigurerHelper.resolvePropertyConfigurer(camelContext, collectionType);
                    if (configurer == null) {
                        if (Map.class.isAssignableFrom(collectionType)) {
                            configurer = MapConfigurer.INSTANCE;
                        }
                    }
                }
                // prepare for next iterator
                newTarget = prop;
                newClass = newTarget.getClass();
                // do not ignore remaining parts, which was not traversed
                newName = Arrays.stream(parts, i + 1, parts.length).collect(Collectors.joining("."));
                // if we have not yet found a configurer for the new target
                if (configurer == null) {
                    configurer = PropertyConfigurerHelper.resolvePropertyConfigurer(camelContext, newTarget);
                }
            }
        }

        // we have walked down to the last part of the ognl path and are ready to set the last piece with the value
        // now this is actually also a bit complex so lets use another method for that
        return doSetPropertyValue(camelContext, newTarget, newName, value, ignoreCase, mandatory,
                fluentBuilder, allowPrivateSetter, reference, placeholder, optional, reflection, configurer);
    }

    private static Object attemptCreateNewInstanceViaReflection(
            CamelContext camelContext, Object newTarget, Class newClass, String name, boolean fluentBuilder,
            boolean allowPrivateSetter, boolean ignoreCase) {

        // if the name has collection lookup then ignore that as we want to create the instance
        String key = StringHelper.before(name, "[", name);

        Object answer = null;
        Method method = findBestSetterMethod(camelContext, newClass, key, fluentBuilder, allowPrivateSetter, ignoreCase);
        if (method != null) {
            Class<?> parameterType = method.getParameterTypes()[0];
            Object obj = getObjectForType(camelContext, parameterType);

            if (obj != null) {
                org.apache.camel.support.ObjectHelper.invokeMethod(method, newTarget, obj);
                answer = obj;
            }
        }
        return answer;
    }

    private static Object attemptCreateNewInstanceViaConfigurer(
            CamelContext camelContext, Object newTarget, String name,
            boolean ignoreCase, PropertyConfigurer configurer) {

        // if the name has collection lookup then ignore that as we want to create the instance
        String key = StringHelper.before(name, "[", name);

        Object answer = null;
        Class<?> parameterType = null;
        if (configurer instanceof PropertyConfigurerGetter) {
            parameterType = ((PropertyConfigurerGetter) configurer).getOptionType(key, true);
        }
        if (parameterType != null) {
            Object obj = getObjectForType(camelContext, parameterType);
            if (obj != null) {
                boolean hit = configurer.configure(camelContext, newTarget, undashKey(key), obj, ignoreCase);
                if (hit) {
                    answer = obj;
                }
            }
        }
        return answer;
    }

    private static Object getObjectForCollectionType(Class<?> type) {
        if (Properties.class.isAssignableFrom(type)) {
            return new Properties();
        } else if (Map.class.isAssignableFrom(type)) {
            return new LinkedHashMap<>();
        } else if (Collection.class.isAssignableFrom(type)) {
            return new ArrayList<>();
        } else if (type.isArray()) {
            return Array.newInstance(type.getComponentType(), 0);
        }

        return null;
    }

    private static Object getObjectForCollectionType(Class<?> type, String errorMessage) {
        Object ret = getObjectForCollectionType(type);

        // not a map or list
        if (ret == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return ret;
    }

    private static Object getObjectForType(CamelContext camelContext, Class<?> parameterType) {
        // special for properties/map/list/array
        Object obj = getObjectForCollectionType(parameterType);

        if (obj == null && org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(parameterType)) {
            obj = camelContext.getInjector().newInstance(parameterType);
        }
        return obj;
    }

    private static boolean doSetPropertyValue(
            CamelContext camelContext, Object target, String name, Object value,
            boolean ignoreCase, boolean mandatory,
            boolean fluentBuilder, boolean allowPrivateSetter,
            boolean reference, boolean placeholder, boolean optional,
            boolean reflection, PropertyConfigurer configurer) {

        String key = name;
        Object text = value;

        if (placeholder) {
            // resolve property placeholders
            key = camelContext.resolvePropertyPlaceholders(key);
            if (text instanceof String) {
                // resolve property placeholders
                String s = text.toString();
                text = camelContext.resolvePropertyPlaceholders(s);
                if (text == null && s.startsWith(PropertiesComponent.PREFIX_TOKEN + "?")) {
                    // it was an optional value, so we should not try to set the property but regard it as a "hit"
                    return true;
                }
            }
        }

        // prepare the value before it is bound
        try {
            Object str = resolveValue(camelContext, target, key, text, ignoreCase, fluentBuilder,
                    allowPrivateSetter, reflection, configurer);
            // resolve property placeholders
            if (str instanceof String) {
                // resolve property placeholders
                str = camelContext.resolvePropertyPlaceholders(str.toString());
            }
            if (str == null && reference && mandatory && !optional) {
                // we could not resolve the reference and this is mandatory
                throw new PropertyBindingException(target, key, value);
            }
            value = str;
        } catch (Exception e) {
            // report the exception using the long key and parent target
            throw new PropertyBindingException(target, key, text, e);
        }

        // okay we are ready to set the value, but the property key
        // can still be complex such as a map/list/array so we need to handle them specially than a regular key
        boolean bound = false;
        try {
            if (isCollectionKey(name)) {
                // collection key (list,map,array)
                if (configurer != null) {
                    bound = setPropertyCollectionViaConfigurer(camelContext, target, key, value, ignoreCase, configurer);
                }
                if (!bound && reflection) {
                    // fallback to reflection based
                    bound = setPropertyCollectionViaReflection(camelContext, target, key, value, ignoreCase, reference,
                            optional);
                }
            } else {
                // regular key
                if (configurer != null) {
                    bound = setSimplePropertyViaConfigurer(camelContext, target, key, value, ignoreCase, configurer);
                }
                if (!bound && reflection) {
                    // fallback to reflection based
                    bound = setSimplePropertyViaReflection(camelContext, target, key, value, fluentBuilder, allowPrivateSetter,
                            reflection, ignoreCase);
                }
                // if the target value is a map type, then we can skip reflection
                // and set the entry
                if (!bound && Map.class.isAssignableFrom(target.getClass())) {
                    ((Map) target).put(key, value);
                    bound = true;
                }
                // if the target value is a list type (and key is digit),
                // then we can skip reflection and set the entry
                if (!bound && List.class.isAssignableFrom(target.getClass()) && StringHelper.isDigit(key)) {
                    try {
                        // key must be digit
                        int idx = Integer.parseInt(key);
                        org.apache.camel.util.ObjectHelper.addListByIndex((List) target, idx, value);
                        bound = true;
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        } catch (PropertyBindingException e) {
            throw e;
        } catch (Exception e) {
            throw new PropertyBindingException(target, key, value, e);
        }

        if (mandatory && !optional && !bound) {
            throw new PropertyBindingException(target, key, value);
        }

        return bound;
    }

    private static boolean isCollectionKey(String name) {
        return name.contains("[") && name.endsWith("]");
    }

    private static boolean setPropertyCollectionViaReflection(
            CamelContext context, Object target, String name, Object value,
            boolean ignoreCase, boolean reference, boolean optional)
            throws Exception {

        final BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);

        int pos = name.indexOf('[');
        String lookupKey = name.substring(pos + 1, name.length() - 1);
        lookupKey = StringHelper.removeLeadingAndEndingQuotes(lookupKey);
        String key = name.substring(0, pos);

        Object obj = null;
        if (pos == 0) {
            // there are no prefix key to invoke as getter first, so check if target is an object
            // we can use for collection
            if (target instanceof Map || target instanceof List || target.getClass().isArray()) {
                obj = target;
            }
        } else {
            obj = bi.getOrElseProperty(target, key, null, ignoreCase);
        }
        if (obj == null) {
            // it was supposed to be a list or map, but its null, so lets create a new list or map and set it automatically
            Method getter = bi.getPropertyGetter(target.getClass(), key, ignoreCase);
            if (getter != null) {
                // what type does it have
                Class<?> returnType = getter.getReturnType();
                obj = getObjectForCollectionType(returnType);
            } else {
                // fallback as map type
                obj = new LinkedHashMap<>();
            }
            boolean hit = bi.setProperty(context, target, key, obj);
            if (!hit) {
                if (optional) {
                    return false;
                }
                throw new IllegalArgumentException(
                        "Cannot set property: " + name + " as a Map because target bean has no setter method for the Map");
            }
        }

        // special for reference (we should not do this for options that are String type)
        // this is only required for reflection (as configurer does this automatic in a more safe way)
        if (value instanceof String) {
            String str = value.toString();
            if (reference && isReferenceParameter(str)) {
                Object bean = CamelContextHelper.lookup(context, str.substring(1));
                if (bean != null) {
                    value = bean;
                }
            }
        }

        if (obj instanceof Map) {
            // this supports both Map and Properties
            Map map = (Map) obj;
            map.put(lookupKey, value);
            return true;
        } else if (obj instanceof List) {
            List list = (List) obj;
            if (isNotEmpty(lookupKey)) {
                int idx = Integer.parseInt(lookupKey);
                org.apache.camel.util.ObjectHelper.addListByIndex(list, idx, value);
            } else {
                list.add(value);
            }
            return true;
        } else if (obj.getClass().isArray() && lookupKey != null) {
            int idx = Integer.parseInt(lookupKey);
            int size = Array.getLength(obj);
            if (idx >= size) {
                obj = Arrays.copyOf((Object[]) obj, idx + 1);
                // replace array
                boolean hit = bi.setProperty(context, target, key, obj);
                if (!hit) {
                    throw new IllegalArgumentException(
                            "Cannot set property: " + name
                                                       + " as an array because target bean has no setter method for the array");
                }
            }
            Array.set(obj, idx, value);
            return true;
        } else {
            // not a map or list
            throw new IllegalArgumentException(
                    "Cannot set property: " + name
                                               + " as either a Map/List/array because target bean is not a Map, List or array type: "
                                               + target);
        }
    }

    private static boolean setPropertyCollectionViaConfigurer(
            CamelContext camelContext, Object target, String name, Object value,
            boolean ignoreCase, PropertyConfigurer configurer) {

        final Object originalTarget = target;

        int pos = name.indexOf('[');
        String lookupKey = name.substring(pos + 1, name.length() - 1);
        lookupKey = StringHelper.removeLeadingAndEndingQuotes(lookupKey);
        String key = name.substring(0, pos);
        String undashKey = undashKey(key);

        Object obj = null;
        if (configurer instanceof PropertyConfigurerGetter) {
            obj = ((PropertyConfigurerGetter) configurer).getOptionValue(target, undashKey, ignoreCase);
        }
        if (obj == null) {
            // it was supposed to be a list or map, but its null, so lets create a new list or map and set it automatically
            Class<?> returnType = null;
            if (configurer instanceof PropertyConfigurerGetter) {
                returnType = ((PropertyConfigurerGetter) configurer).getOptionType(undashKey, true);
            }
            if (returnType == null) {
                return false;
            }
            obj = getObjectForCollectionType(returnType);

            if (obj != null) {
                // set
                boolean hit = configurer.configure(camelContext, target, undashKey, obj, ignoreCase);
                if (!hit) {
                    // not a map or list
                    throw new IllegalArgumentException(
                            "Cannot set property: " + name
                                                       + " as either a Map/List/array because target bean is not a Map, List or array type: "
                                                       + target);
                }
                target = obj;
            }
        }

        if (obj == null) {
            return false;
        }

        if (obj instanceof Map) {
            // this supports both Map and Properties
            Map map = (Map) obj;
            map.put(lookupKey, value);
            return true;
        } else if (obj instanceof List) {
            List list = (List) obj;
            if (isNotEmpty(lookupKey)) {
                int idx = Integer.parseInt(lookupKey);
                if (idx < list.size()) {
                    list.set(idx, value);
                } else if (idx == list.size()) {
                    list.add(value);
                } else {
                    // If the list implementation is based on an array, we
                    // can increase tha capacity to the required value to
                    // avoid potential re-allocation weh invoking List::add.
                    //
                    // Note that ArrayList is the default List impl that
                    // is automatically created if the property is null.
                    if (list instanceof ArrayList) {
                        ((ArrayList) list).ensureCapacity(idx + 1);
                    }
                    while (list.size() < idx) {
                        list.add(null);
                    }
                    list.add(idx, value);
                }
            } else {
                list.add(value);
            }
            return true;
        } else if (obj.getClass().isArray()) {
            int idx = Integer.parseInt(lookupKey);
            int size = Array.getLength(obj);
            if (idx >= size) {
                obj = Arrays.copyOf((Object[]) obj, idx + 1);
                // replace array
                boolean hit = configurer.configure(camelContext, originalTarget, undashKey, obj, ignoreCase);
                if (!hit) {
                    throw new IllegalArgumentException(
                            "Cannot set property: " + name
                                                       + " as an array because target bean has no setter method for the array");
                }
            }
            Array.set(obj, idx, value);
            return true;
        } else {
            // not a map or list
            throw new IllegalArgumentException(
                    "Cannot set property: " + name
                                               + " as either a Map/List/array because target bean is not a Map, List or array type: "
                                               + target);
        }
    }

    private static boolean setSimplePropertyViaConfigurer(
            CamelContext camelContext, Object target, String key, Object value,
            boolean ignoreCase, PropertyConfigurer configurer) {
        try {
            return configurer.configure(camelContext, target, undashKey(key), value, ignoreCase);
        } catch (Exception e) {
            throw new PropertyBindingException(target, key, value, e);
        }
    }

    private static boolean setSimplePropertyViaReflection(
            CamelContext camelContext, Object target, String name, Object value,
            boolean fluentBuilder, boolean allowPrivateSetter, boolean reference,
            boolean ignoreCase) {

        try {
            if (name != null) {
                return doSetSimplePropertyViaReflection(camelContext, target, name, value, false, ignoreCase, fluentBuilder,
                        allowPrivateSetter, reference);
            }
        } catch (Exception e) {
            throw new PropertyBindingException(target, name, value, e);
        }

        return false;
    }

    private static Object resolveAutowired(
            CamelContext context, Object target, String name, Object value,
            boolean ignoreCase, boolean fluentBuilder, boolean allowPrivateSetter,
            boolean reflection, PropertyConfigurer configurer) {

        String undashKey = undashKey(name);

        if (value instanceof String) {
            String str = value.toString();
            if (str.equals("#autowired")) {
                // we should get the type from the setter
                Class<?> parameterType = null;
                if (configurer instanceof PropertyConfigurerGetter) {
                    // favour using configurer
                    parameterType = ((PropertyConfigurerGetter) configurer).getOptionType(undashKey, true);
                }
                if (parameterType == null && reflection) {
                    // fallback to reflection
                    Method method
                            = findBestSetterMethod(context, target.getClass(), undashKey, fluentBuilder, allowPrivateSetter,
                                    ignoreCase);
                    if (method != null) {
                        parameterType = method.getParameterTypes()[0];
                    } else {
                        throw new IllegalStateException(
                                "Cannot find setter method with name: " + undashKey + " on class: "
                                                        + target.getClass().getName()
                                                        + " to use for autowiring");
                    }
                }
                if (parameterType != null) {
                    value = context.getRegistry().mandatoryFindSingleByType(parameterType);
                }
            }
        }
        return value;
    }

    private static Object resolveValue(
            CamelContext context, Object target, String name, Object value,
            boolean ignoreCase, boolean fluentBuilder, boolean allowPrivateSetter,
            boolean reflection, PropertyConfigurer configurer)
            throws Exception {
        if (value instanceof String) {
            String str = value.toString();
            if (str.startsWith("#property:")) {
                String key = str.substring(10);
                // the key may have property placeholder so resolve those first
                key = context.resolvePropertyPlaceholders(key);
                Optional<String> resolved = context.getPropertiesComponent().resolveProperty(key);
                if (resolved.isPresent()) {
                    value = resolved.get();
                } else {
                    throw new IllegalArgumentException("Property with key " + key + " not found by properties component");
                }
            } else if (str.equals("#autowired")) {
                value = resolveAutowired(context, target, name, value, ignoreCase, fluentBuilder, allowPrivateSetter,
                        reflection, configurer);
            } else {
                value = resolveBean(context, value);
            }
        }
        return value;
    }

    private static boolean doSetSimplePropertyViaReflection(
            CamelContext context, Object target, String name, Object value, boolean mandatory,
            boolean ignoreCase, boolean fluentBuilder,
            boolean allowPrivateSetter, boolean reference)
            throws Exception {

        String refName = null;
        if (reference && value instanceof String) {
            String str = value.toString();
            if (str.startsWith("#bean:")) {
                // okay it's a reference so swap to look up this which is already supported in IntrospectionSupport
                refName = "#" + ((String) value).substring(6);
                value = null;
            } else if (str.equals("#autowired")) {
                value = resolveAutowired(context, target, name, value, ignoreCase, fluentBuilder, allowPrivateSetter, true,
                        null);
            } else if (isReferenceParameter(str)) {
                // special for reference (we should not do this for options that are String type)
                // this is only required for reflection (as configurer does this automatic in a more safe way)
                Object bean = CamelContextHelper.lookup(context, str.substring(1));
                if (bean != null) {
                    value = bean;
                }
            }
        }

        boolean hit = PluginHelper.getBeanIntrospection(context).setProperty(context,
                context.getTypeConverter(), target, name, value, refName, fluentBuilder, allowPrivateSetter, ignoreCase);
        if (!hit && mandatory) {
            // there is no setter with this given name, so lets report this as a problem
            throw new IllegalArgumentException(
                    "Cannot find setter method: " + name + " on bean: " + target + " of type: " + target.getClass().getName()
                                               + " when binding property: " + name);
        }
        return hit;
    }

    private static Object getOrCreatePropertyOgnlPathViaConfigurer(
            CamelContext context, Object target, String property, boolean ignoreCase, PropertyConfigurer configurer) {
        String key = property;
        String lookupKey = null;

        // support maps in keys
        if (property.contains("[") && property.endsWith("]")) {
            int pos = property.indexOf('[');
            lookupKey = property.substring(pos + 1, property.length() - 1);
            key = property.substring(0, pos);
        }
        String undashKey = undashKey(key);

        Object answer = null;
        Class<?> type = null;

        if (configurer instanceof PropertyConfigurerGetter) {
            answer = ((PropertyConfigurerGetter) configurer).getOptionValue(target, undashKey, ignoreCase);
        }
        if (answer != null) {
            type = answer.getClass();
        } else if (configurer instanceof PropertyConfigurerGetter) {
            type = ((PropertyConfigurerGetter) configurer).getOptionType(undashKey, true);
        }

        if (answer == null && type == null) {
            // not possible to build
            return null;
        }

        if (answer == null) {
            if (lookupKey != null) {
                answer = getObjectForCollectionType(type, "Cannot set property: " + property
                                                          + " as either a Map/List/array because target bean is not a Map, List or array type: "
                                                          + target);

                boolean hit = configurer.configure(context, target, undashKey, answer, ignoreCase);
                if (!hit) {
                    throw new IllegalArgumentException(
                            "Cannot set property: " + key
                                                       + " as an map/list/array because target bean has no suitable setter method");
                }
            }
        }

        if (answer instanceof Map && lookupKey != null) {
            Map map = (Map) answer;
            answer = map.get(lookupKey);
            if (answer == null) {
                // okay there was no element in the list, so create a new empty instance if we can know its parameter type
                Class<?> parameterType = null;
                if (configurer instanceof PropertyConfigurerGetter) {
                    parameterType = (Class<?>) ((PropertyConfigurerGetter) configurer).getCollectionValueType(target, undashKey,
                            ignoreCase);
                }
                if (parameterType != null
                        && org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(parameterType)) {
                    Object instance = context.getInjector().newInstance(parameterType);
                    map.put(lookupKey, instance);
                    answer = instance;
                }
            }
        } else if (answer instanceof List) {
            List list = (List) answer;
            if (isNotEmpty(lookupKey)) {
                int idx = Integer.parseInt(lookupKey);
                answer = list.size() > idx ? list.get(idx) : null;
            } else {
                if (list.isEmpty()) {
                    answer = null;
                } else {
                    answer = list.get(list.size() - 1);
                }
            }
            if (answer == null) {
                // okay there was no element in the list, so create a new empty instance if we can know its parameter type
                Class<?> parameterType = null;
                if (configurer instanceof PropertyConfigurerGetter) {
                    parameterType = (Class<?>) ((PropertyConfigurerGetter) configurer).getCollectionValueType(target, undashKey,
                            ignoreCase);
                }
                if (parameterType != null
                        && org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(parameterType)) {
                    Object instance = context.getInjector().newInstance(parameterType);
                    list.add(instance);
                    answer = instance;
                }
            }
        } else if (type != null && type.isArray() && lookupKey != null) {
            Object[] arr = (Object[]) answer;
            int idx = Integer.parseInt(lookupKey);
            int size = arr.length;
            if (idx >= size) {
                // index outside current array size, so enlarge array
                arr = Arrays.copyOf(arr, idx + 1);
                // replace array
                boolean hit = configurer.configure(context, target, undashKey, arr, true);
                if (!hit) {
                    throw new IllegalArgumentException(
                            "Cannot set property: " + key
                                                       + " as an array because target bean has no setter method for the array");
                }
            }
            Object instance = arr[idx];
            if (instance == null) {
                instance = context.getInjector().newInstance(type.getComponentType());
                Array.set(arr, idx, instance);
            }
            answer = instance;
        }

        return answer;
    }

    private static Object getOrCreatePropertyOgnlPathViaReflection(
            CamelContext context, Object target, String property, boolean ignoreCase) {
        String key = property;
        String lookupKey = null;

        // support maps in keys
        if (property.contains("[") && property.endsWith("]")) {
            int pos = property.indexOf('[');
            lookupKey = property.substring(pos + 1, property.length() - 1);
            key = property.substring(0, pos);
        }

        Object answer;
        Class<?> type = null;

        final BeanIntrospection introspection = PluginHelper.getBeanIntrospection(context);
        answer = introspection.getOrElseProperty(target, key, null, ignoreCase);
        if (answer != null) {
            type = answer.getClass();
        } else {
            // the value is null then lets find out what type it is via its getter
            try {
                Method method = introspection.getPropertyGetter(target.getClass(), key, ignoreCase);
                if (method != null) {
                    type = method.getReturnType();
                }
            } catch (NoSuchMethodException e) {
                // ignore
            }
        }

        if (answer == null && type == null) {
            // not possible to build
            return null;
        }

        if (answer == null) {
            if (lookupKey != null) {
                answer = getObjectForCollectionType(type, "Cannot set property: " + property
                                                          + " as either a Map/List/array because target bean is not a Map, List or array type: "
                                                          + target);
                boolean hit = false;
                try {
                    hit = introspection.setProperty(context, target, key, answer);
                } catch (Exception e) {
                    // ignore
                }
                if (!hit) {
                    throw new IllegalArgumentException(
                            "Cannot set property: " + key
                                                       + " as an map/list/array because target bean has no suitable setter method");
                }
            }
        }

        if (answer instanceof Map && lookupKey != null) {
            Map map = (Map) answer;
            answer = map.get(lookupKey);
            if (answer == null) {
                Class<?> parameterType = null;
                try {
                    // our only hope is that the List has getter/setter that use a generic type to specify what kind of class
                    // they contains so we can use that to know the parameter type
                    Method method = introspection.getPropertyGetter(target.getClass(), key, ignoreCase);
                    if (method != null) {
                        String typeName = method.getGenericReturnType().getTypeName();
                        // its a map (Map<String, com.foo.MyObject>) so we look for , >
                        String fqn = StringHelper.between(typeName, ",", ">");
                        if (fqn != null) {
                            fqn = fqn.trim();
                            parameterType = context.getClassResolver().resolveClass(fqn);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                if (parameterType != null
                        && org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(parameterType)) {
                    Object instance = context.getInjector().newInstance(parameterType);
                    map.put(lookupKey, instance);
                    answer = instance;
                }
            }
        } else if (answer instanceof List) {
            List list = (List) answer;
            if (isNotEmpty(lookupKey)) {
                int idx = Integer.parseInt(lookupKey);
                answer = list.size() > idx ? list.get(idx) : null;
            } else {
                if (list.isEmpty()) {
                    answer = null;
                } else {
                    answer = list.get(list.size() - 1);
                }
            }
            if (answer == null) {
                // okay there was no element in the list, so create a new empty instance if we can know its parameter type
                Class<?> parameterType = null;
                try {
                    // our only hope is that the List has getter/setter that use a generic type to specify what kind of class
                    // they contain, so we can use that to know the parameter type
                    Method method = introspection.getPropertyGetter(target.getClass(), key, ignoreCase);
                    if (method != null) {
                        // it's a list (List<com.foo.MyObject>) so we look for < >
                        String typeName = method.getGenericReturnType().getTypeName();
                        String fqn = StringHelper.between(typeName, "<", ">");
                        if (fqn != null) {
                            parameterType = context.getClassResolver().resolveClass(fqn);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                if (parameterType != null
                        && org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(parameterType)) {
                    Object instance = context.getInjector().newInstance(parameterType);
                    list.add(instance);
                    answer = instance;
                }
            }
        } else if (type != null && type.isArray() && lookupKey != null) {
            Object[] arr = (Object[]) answer;
            int idx = Integer.parseInt(lookupKey);
            int size = arr.length;
            if (idx >= size) {
                // index outside current array size, so enlarge array
                arr = Arrays.copyOf(arr, idx + 1);
                // replace array
                boolean hit = false;
                try {
                    hit = introspection.setProperty(context, target, key, arr);
                } catch (Exception e) {
                    // ignore
                }
                if (!hit) {
                    throw new IllegalArgumentException(
                            "Cannot set property: " + key
                                                       + " as an array because target bean has no setter method for the array");
                }
            }
            Object instance = arr[idx];
            if (instance == null) {
                instance = context.getInjector().newInstance(type.getComponentType());
                Array.set(arr, idx, instance);
            }
            answer = instance;
        }

        return answer;
    }

    private static Method findBestSetterMethod(
            CamelContext context, Class<?> clazz, String name,
            boolean fluentBuilder, boolean allowPrivateSetter, boolean ignoreCase) {
        // is there a direct setter?
        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(context);
        Set<Method> candidates = beanIntrospection.findSetterMethods(clazz, name,
                false, allowPrivateSetter, ignoreCase);
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // okay now try with builder pattern
        if (fluentBuilder) {
            candidates = beanIntrospection.findSetterMethods(clazz, name,
                    fluentBuilder, allowPrivateSetter, ignoreCase);
            if (candidates.size() == 1) {
                return candidates.iterator().next();
            }
        }

        return null;
    }

    /**
     * Is the given parameter a reference parameter (starting with a # char)
     *
     * @param  obj the parameter
     * @return     <tt>true</tt> if its a reference parameter
     */
    private static boolean isReferenceParameter(Object obj) {
        if (obj == null) {
            return false;
        }
        String parameter = obj.toString();
        parameter = parameter.trim();
        if (!parameter.startsWith("#")) {
            return false;
        }

        // non reference parameters are
        // #bean: #class: #type: #property: #convert: #autowired
        if (parameter.equals("#autowired")
                || parameter.startsWith("#bean:")
                || parameter.startsWith("#class:")
                || parameter.startsWith("#type:")
                || parameter.startsWith("#property:")
                || parameter.startsWith("#valueAs(:")) {
            return false;
        }

        return true;
    }

    /**
     * Creates a new bean instance using the constructor that takes the given set of parameters.
     *
     * @param  camelContext the camel context
     * @param  type         the class type of the bean to create
     * @param  parameters   the parameters for the constructor
     * @return              the created bean, or null if there was no constructor that matched the given set of
     *                      parameters
     * @throws Exception    is thrown if error creating the bean
     */
    public static Object newInstanceConstructorParameters(CamelContext camelContext, Class<?> type, String parameters)
            throws Exception {
        String[] params = StringQuoteHelper.splitSafeQuote(parameters, ',', false);
        Constructor<?> found = findMatchingConstructor(type.getConstructors(), params);
        if (found != null) {
            Object[] arr = new Object[found.getParameterCount()];
            for (int i = 0; i < found.getParameterCount(); i++) {
                Class<?> paramType = found.getParameterTypes()[i];
                Object param = params[i];
                Object val = null;
                // special as we may refer to other #bean or #type in the parameter
                if (param instanceof String) {
                    String str = param.toString();
                    if (str.startsWith("#")) {
                        Object bean = resolveBean(camelContext, param);
                        if (bean != null) {
                            val = bean;
                        }
                    }
                }
                // unquote text
                if (val instanceof String) {
                    val = StringHelper.removeLeadingAndEndingQuotes((String) val);
                }
                if (val != null) {
                    val = camelContext.getTypeConverter().tryConvertTo(paramType, val);
                } else {
                    val = camelContext.getTypeConverter().convertTo(paramType, param);
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
     * @param  constructors the constructors
     * @param  params       the parameters
     * @return              the constructor, or null if no matching constructor can be found
     */
    private static Constructor<?> findMatchingConstructor(Constructor<?>[] constructors, String[] params) {
        List<Constructor<?>> candidates = new ArrayList<>();
        Constructor<?> fallbackCandidate = null;

        for (Constructor<?> ctr : constructors) {
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
     * Creates a new bean instance using a public static factory method from the given class
     *
     * @param  camelContext the camel context
     * @param  type         the class with the public static factory method
     * @param  parameters   optional parameters for the factory method
     * @return              the created bean, or null if there was no factory method (optionally matched the given set
     *                      of parameters)
     * @throws Exception    is thrown if error creating the bean
     */
    public static Object newInstanceFactoryParameters(
            CamelContext camelContext, Class<?> type, String factoryMethod, String parameters)
            throws Exception {
        String[] params = StringQuoteHelper.splitSafeQuote(parameters, ',', false);
        Method found = findMatchingFactoryMethod(type.getMethods(), factoryMethod, params);
        if (found != null) {
            Object[] arr = new Object[found.getParameterCount()];
            for (int i = 0; i < found.getParameterCount(); i++) {
                Class<?> paramType = found.getParameterTypes()[i];
                Object param = params[i];
                Object val = null;
                // special as we may refer to other #bean or #type in the parameter
                if (param instanceof String) {
                    String str = param.toString();
                    if (str.startsWith("#")) {
                        Object bean = resolveBean(camelContext, param);
                        if (bean != null) {
                            val = bean;
                        }
                    }
                }
                // unquote text
                if (val instanceof String) {
                    val = StringHelper.removeLeadingAndEndingQuotes((String) val);
                }
                if (val != null) {
                    val = camelContext.getTypeConverter().tryConvertTo(paramType, val);
                } else {
                    val = camelContext.getTypeConverter().convertTo(paramType, param);
                }
                arr[i] = val;
            }

            return found.invoke(null, arr);
        }
        return null;
    }

    /**
     * Finds the best matching factory methods for the given parameters.
     * <p/>
     * This implementation is similar to the logic in camel-bean.
     *
     * @param  methods       the methods
     * @param  factoryMethod the name of the factory method
     * @param  params        the parameters
     * @return               the constructor, or null if no matching constructor can be found
     */
    private static Method findMatchingFactoryMethod(Method[] methods, String factoryMethod, String[] params) {
        List<Method> candidates = new ArrayList<>();
        Method fallbackCandidate = null;

        for (Method method : methods) {
            // must match factory method name
            if (!factoryMethod.equals(method.getName())) {
                continue;
            }
            // must be a public static method that returns something
            if (!Modifier.isStatic(method.getModifiers())
                    || !Modifier.isPublic(method.getModifiers())
                    || method.getReturnType() == Void.TYPE) {
                continue;
            }
            // must match number of parameters
            if (method.getParameterCount() != params.length) {
                continue;
            }

            boolean matches = true;
            for (int i = 0; i < method.getParameterCount(); i++) {
                String parameter = params[i];
                if (parameter != null) {
                    // must trim
                    parameter = parameter.trim();
                }

                Class<?> parameterType = getValidParameterType(parameter);
                Class<?> expectedType = method.getParameterTypes()[i];

                if (parameterType != null && expectedType != null) {
                    // skip java.lang.Object type, when we have multiple possible methods we want to avoid it if possible
                    if (Object.class.equals(expectedType)) {
                        fallbackCandidate = method;
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
                candidates.add(method);
            }
        }

        return candidates.size() == 1 ? candidates.get(0) : fallbackCandidate;
    }

    /**
     * Determines and maps the given value is valid according to the supported values by the bean component.
     * <p/>
     * This implementation is similar to the logic in camel-bean.
     *
     * @param  value the value
     * @return       the parameter type the given value is being mapped as, or <tt>null</tt> if not valid.
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
     * @param  camelContext the camel context
     * @param  value        how to resolve the bean with a prefix of either #class:, #type: or #bean:
     * @return              the resolve bean
     * @throws Exception    is thrown if error resolving the bean, or if the value is invalid.
     */
    public static Object resolveBean(CamelContext camelContext, Object value) throws Exception {
        if (!(value instanceof String strval)) {
            return value;
        }

        Object answer = value;

        // resolve placeholders
        strval = camelContext.resolvePropertyPlaceholders(strval);

        if (strval.startsWith("#class:")) {
            // it's a new class to be created
            String className = strval.substring(7);
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
                Class<?> factoryClass = null;
                String typeOrRef = StringHelper.before(factoryMethod, ":");
                if (typeOrRef != null) {
                    // use another class with factory method
                    factoryMethod = StringHelper.after(factoryMethod, ":");
                    // special to support factory method parameters
                    Object existing = camelContext.getRegistry().lookupByName(typeOrRef);
                    if (existing != null) {
                        factoryClass = existing.getClass();
                    } else {
                        factoryClass = camelContext.getClassResolver().resolveMandatoryClass(typeOrRef);
                    }
                }
                if (parameters != null) {
                    Class<?> target = factoryClass != null ? factoryClass : type;
                    answer = newInstanceFactoryParameters(camelContext, target, factoryMethod, parameters);
                } else {
                    answer = camelContext.getInjector().newInstance(type, factoryClass, factoryMethod);
                }
                if (answer == null) {
                    throw new IllegalStateException(
                            "Cannot create bean instance using factory method: " + className + "#" + factoryMethod);
                }
            } else if (parameters != null) {
                // special to support constructor parameters
                answer = newInstanceConstructorParameters(camelContext, type, parameters);
            } else {
                answer = camelContext.getInjector().newInstance(type);
            }
            if (answer == null) {
                throw new IllegalStateException("Cannot create instance of class: " + className);
            }
        } else if (strval.startsWith("#type:")) {
            // its reference by type, so lookup the actual value and use it if there is only one instance in the registry
            String typeName = strval.substring(6);
            Class<?> type = camelContext.getClassResolver().resolveMandatoryClass(typeName);
            answer = camelContext.getRegistry().mandatoryFindSingleByType(type);
        } else if (strval.startsWith("#bean:")) {
            String key = strval.substring(6);
            answer = CamelContextHelper.mandatoryLookup(camelContext, key);
        } else if (strval.startsWith("#valueAs(")) {
            String text = strval.substring(8);
            String typeName = StringHelper.between(text, "(", ")");
            String constant = StringHelper.after(text, ":");
            if (typeName == null || constant == null) {
                throw new IllegalArgumentException("Illegal syntax: " + text + " when using function #valueAs(type):value");
            }
            Class<?> type = camelContext.getClassResolver().resolveMandatoryClass(typeName);
            answer = camelContext.getTypeConverter().mandatoryConvertTo(type, constant);
        }

        return answer;
    }

    private static String undashKey(String key) {
        // as we un-dash property keys then we need to prepare this for the configurer (reflection does this automatic)
        key = StringHelper.dashToCamelCase(key);
        return key;
    }

    private static boolean isDotKey(String key) {
        // we only want to know if there is a dot in OGNL path, so any map keys [iso.code] is accepted

        if (key.indexOf('[') == -1 && key.indexOf('.') != -1) {
            return true;
        }

        boolean mapKey = false;
        for (char ch : key.toCharArray()) {
            if (ch == '[') {
                mapKey = true;
            } else if (ch == ']') {
                mapKey = false;
            }
            if (ch == '.' && !mapKey) {
                return true;
            }
        }
        return false;
    }

    private static String[] splitKey(String key) {
        // split the key into parts separated by dot (but handle map keys [iso.code] etc.
        List<String> parts = new ArrayList<>();

        boolean mapKey = false;
        StringBuilder sb = new StringBuilder();
        for (char ch : key.toCharArray()) {
            if (ch == '[') {
                mapKey = true;
            } else if (ch == ']') {
                mapKey = false;
            }
            if (ch == '.' && !mapKey) {
                // dont include the separator dot
                parts.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        if (!sb.isEmpty()) {
            parts.add(sb.toString());
        }

        return parts.toArray(new String[0]);
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
     * To use a fluent builder style to configure this property binding support.
     */
    public static class Builder {

        private CamelContext camelContext;
        private Object target;
        private Map<String, Object> properties;
        private boolean removeParameters = true;
        private boolean flattenProperties;
        private boolean mandatory;
        private boolean optional;
        private boolean nesting = true;
        private boolean deepNesting = true;
        private boolean reference = true;
        private boolean placeholder = true;
        private boolean fluentBuilder = true;
        private boolean allowPrivateSetter = true;
        private boolean ignoreCase;
        private String optionPrefix;
        private boolean reflection = true;
        private PropertyConfigurer configurer;
        private PropertyBindingListener listener;

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
            if (this.properties != null) {
                // there may be existing options so add those if missing
                // we need to mutate existing as we are may be removing bound properties
                this.properties.forEach(properties::putIfAbsent);
            }
            this.properties = properties;
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
         * Whether properties should be flattened (when properties is a map of maps).
         */
        public Builder withFlattenProperties(boolean flattenProperties) {
            this.flattenProperties = flattenProperties;
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
         * Whether parameters can be optional such as configuring endpoints that are lenient
         */
        public Builder withOptional(boolean optional) {
            this.optional = optional;
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
         * Whether deep nesting is in use, where Camel will attempt to walk as deep as possible by creating new objects
         * in the OGNL graph if a property has a setter and the object can be created from a default no-arg constructor.
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
         * Whether properties should be filtered by prefix. * Note that the prefix is removed from the key before the
         * property is bound.
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
         * Whether properties should be filtered by prefix. Note that the prefix is removed from the key before the
         * property is bound.
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
         * Whether to allow using reflection (when there is no configurer available).
         */
        public Builder withReflection(boolean reflection) {
            this.reflection = reflection;
            return this;
        }

        /**
         * To use the property binding listener.
         */
        public Builder withListener(PropertyBindingListener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Binds the properties to the target object, and builds the output as the given type, by invoking the build
         * method (uses build as name)
         *
         * @param type the type of the output class
         */
        public <T> T build(Class<T> type) {
            return build(type, "build");
        }

        /**
         * Binds the properties to the target object, and builds the output as the given type, by invoking the build
         * method (via reflection).
         *
         * @param type        the type of the output class
         * @param buildMethod the name of the builder method to invoke
         */
        public <T> T build(Class<T> type, String buildMethod) {
            // first bind
            bind();

            // then invoke the build method on target via reflection
            try {
                Object out = ObjectHelper.invokeMethodSafe(buildMethod, target);
                return camelContext.getTypeConverter().convertTo(type, out);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
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

            if (properties == null || properties.isEmpty()) {
                return false;
            }

            return doBindProperties(camelContext, target, removeParameters ? properties : new HashMap<>(properties),
                    optionPrefix, ignoreCase, removeParameters, flattenProperties, mandatory, optional,
                    nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder, reflection, configurer,
                    listener);
        }

        /**
         * Binds the properties to the target object, and removes the property that was bound from properties.
         *
         * @param  camelContext the camel context
         * @param  target       the target object
         * @param  properties   the properties where the bound properties will be removed from
         * @return              true if one or more properties was bound
         */
        public boolean bind(CamelContext camelContext, Object target, Map<String, Object> properties) {
            CamelContext context = camelContext != null ? camelContext : this.camelContext;
            Object obj = target != null ? target : this.target;
            Map<String, Object> prop = properties != null ? properties : this.properties;

            return doBindProperties(context, obj, removeParameters ? prop : new HashMap<>(prop),
                    optionPrefix, ignoreCase, removeParameters, flattenProperties, mandatory, optional,
                    nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder, reflection, configurer,
                    listener);
        }

        /**
         * Binds the property to the target object.
         *
         * @param  camelContext the camel context
         * @param  target       the target object
         * @param  key          the property key
         * @param  value        the property value
         * @return              true if the property was bound
         */
        public boolean bind(CamelContext camelContext, Object target, String key, Object value) {
            Map<String, Object> properties = new HashMap<>(1);
            properties.put(key, value);

            return doBindProperties(camelContext, target, properties, optionPrefix, ignoreCase, true, false, mandatory,
                    optional,
                    nesting, deepNesting, fluentBuilder, allowPrivateSetter, reference, placeholder, reflection, configurer,
                    listener);
        }

    }

    /**
     * Used for making it easier to support using option prefix in property binding and to remove the bound properties
     * from the input map.
     */
    private static class OptionPrefixMap extends LinkedHashMap<String, Object> {

        private final String optionPrefix;
        private final Map<String, Object> originalMap;

        public OptionPrefixMap(Map<String, Object> map, String optionPrefix) {
            this.originalMap = map;
            this.optionPrefix = optionPrefix;
            // copy from original map into our map without the option prefix
            map.forEach((k, v) -> {
                if (startsWithIgnoreCase(k, optionPrefix)) {
                    put(k.substring(optionPrefix.length()), v);
                } else if (startsWithIgnoreCase(k, "?" + optionPrefix)) {
                    put(k.substring(optionPrefix.length() + 1), v);
                }
            });
        }

        @Override
        public Object remove(Object key) {
            // we only need to care about the remove method,
            // so we can remove the corresponding key from the original map
            Set<String> toBeRemoved = new HashSet<>();
            originalMap.forEach((k, v) -> {
                if (startsWithIgnoreCase(k, optionPrefix)) {
                    toBeRemoved.add(k);
                } else if (startsWithIgnoreCase(k, "?" + optionPrefix)) {
                    toBeRemoved.add(k);
                }
            });
            toBeRemoved.forEach(originalMap::remove);

            return super.remove(key);
        }

    }

    /**
     * Used for flatten properties when they are a map of maps
     */
    private static class FlattenMap extends LinkedHashMap<String, Object> {

        private final Map<String, Object> originalMap;

        public FlattenMap(Map<String, Object> map) {
            this.originalMap = map;
            flatten("", originalMap);
        }

        @SuppressWarnings("unchecked")
        private void flatten(String prefix, Map<?, Object> map) {
            for (Map.Entry<?, Object> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                boolean optional = key.startsWith("?");
                if (optional) {
                    key = key.substring(1);
                }
                Object value = entry.getValue();
                String keyPrefix = (optional ? "?" : "") + (prefix.isEmpty() ? key : prefix + "." + key);
                if (value instanceof Map) {
                    flatten(keyPrefix, (Map<?, Object>) value);
                } else {
                    put(keyPrefix, value);
                }
            }
        }

        @Override
        public Object remove(Object key) {
            // we only need to care about the remove method,
            // so we can remove the corresponding key from the original map

            // walk key with dots to remove right node
            String[] parts = splitKey(key.toString());
            Map map = originalMap;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                Object obj = map.get(part);
                if (i == parts.length - 1) {
                    map.remove(part);
                } else if (obj instanceof Map) {
                    map = (Map) obj;
                }
            }

            // remove empty middle maps
            Object answer = super.remove(key);
            if (super.isEmpty()) {
                originalMap.clear();
            }
            return answer;
        }

    }

    /**
     * Used for sorting the property keys when doing property binding. We need to sort the keys in a specific order so
     * we process the binding in a way that allows us to walk down the OGNL object graph and build empty nodes on the
     * fly, and as well handle map/list and array types as well.
     */
    private static final class PropertyBindingKeyComparator implements Comparator<String> {

        private final Map<String, Object> map;

        private PropertyBindingKeyComparator(Map<String, Object> map) {
            this.map = map;
        }

        @Override
        public int compare(String o1, String o2) {
            // 1) sort by nested level (shortest OGNL graph first)
            int n1 = StringHelper.countChar(o1, '.');
            int n2 = StringHelper.countChar(o2, '.');
            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
            // 2) sort by reference (as it may refer to other beans in the OGNL graph)
            Object v1 = map.get(o1);
            Object v2 = map.get(o2);
            boolean ref1 = v1 instanceof String && ((String) v1).startsWith("#");
            boolean ref2 = v2 instanceof String && ((String) v2).startsWith("#");
            if (ref1 != ref2) {
                return Boolean.compare(ref1, ref2);
            }
            // 3) sort by name
            return o1.compareTo(o2);
        }
    }

    private static final class MapConfigurer implements PropertyConfigurer {
        public static final PropertyConfigurer INSTANCE = new MapConfigurer();

        @SuppressWarnings("unchecked")
        @Override
        public boolean configure(
                CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            ((Map) target).put(name, value);
            return true;
        }
    }

}
