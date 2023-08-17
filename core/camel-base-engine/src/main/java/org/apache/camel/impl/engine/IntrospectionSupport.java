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
package org.apache.camel.impl.engine;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.ObjectHelper.invokeMethodSafe;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * Helper for introspections of beans.
 * <p/>
 * <b>Important: </b> Its recommended to call the {@link #stop()} method when {@link org.apache.camel.CamelContext} is
 * being stopped. This allows to clear the introspection cache.
 * <p/>
 * This implementation will skip methods from <tt>java.lang.Object</tt> and <tt>java.lang.reflect.Proxy</tt>.
 * <p/>
 * This implementation will use a cache when the {@link #getProperties(Object, java.util.Map, String)} method is being
 * used. Also the {@link #cacheClass(Class)} method gives access to the introspect cache.
 * <p/>
 * This class is only for internal use by Camel - not for end users; use {@link BeanIntrospection} instead.
 */
final class IntrospectionSupport {

    private static final Logger LOG = LoggerFactory.getLogger(IntrospectionSupport.class);
    private static final List<Method> EXCLUDED_METHODS = new ArrayList<>();
    // use a cache to speedup introspecting for known classes during startup
    // use a weak cache as we don't want the cache to keep around as it reference classes
    // which could prevent classloader to unload classes if being referenced from this cache
    private static final Map<Class<?>, BeanIntrospection.ClassInfo> CACHE = LRUCacheFactory.newLRUWeakCache(1000);
    private static final Pattern SECRETS = Pattern.compile(".*(passphrase|password|secretKey).*", Pattern.CASE_INSENSITIVE);

    static {
        // exclude all java.lang.Object methods as we don't want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Object.class.getMethods()));
        // exclude all java.lang.reflect.Proxy methods as we don't want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Proxy.class.getMethods()));
    }

    private static final Set<Class<?>> PRIMITIVE_CLASSES = new HashSet<>();

    static {
        PRIMITIVE_CLASSES.add(String.class);
        PRIMITIVE_CLASSES.add(Character.class);
        PRIMITIVE_CLASSES.add(Boolean.class);
        PRIMITIVE_CLASSES.add(Byte.class);
        PRIMITIVE_CLASSES.add(Short.class);
        PRIMITIVE_CLASSES.add(Integer.class);
        PRIMITIVE_CLASSES.add(Long.class);
        PRIMITIVE_CLASSES.add(Float.class);
        PRIMITIVE_CLASSES.add(Double.class);
        PRIMITIVE_CLASSES.add(char.class);
        PRIMITIVE_CLASSES.add(boolean.class);
        PRIMITIVE_CLASSES.add(byte.class);
        PRIMITIVE_CLASSES.add(short.class);
        PRIMITIVE_CLASSES.add(int.class);
        PRIMITIVE_CLASSES.add(long.class);
        PRIMITIVE_CLASSES.add(float.class);
        PRIMITIVE_CLASSES.add(double.class);
    }

    /**
     * Utility classes should not have a public constructor.
     */
    private IntrospectionSupport() {
    }

    /**
     * {@link org.apache.camel.CamelContext} should call this stop method when its stopping.
     * <p/>
     * This implementation will clear its introspection cache.
     */
    static void stop() {
        clearCache();
    }

    /**
     * Clears the introspection cache.
     */
    static void clearCache() {
        if (LOG.isDebugEnabled() && CACHE instanceof LRUCache<Class<?>, BeanIntrospection.ClassInfo> localCache) {
            LOG.debug("Clearing cache[size={}, hits={}, misses={}, evicted={}]", localCache.size(), localCache.getHits(),
                    localCache.getMisses(), localCache.getEvicted());
        }
        CACHE.clear();
    }

    static long getCacheCounter() {
        return CACHE.size();
    }

    static boolean isGetter(Method method) {
        String name = method.getName();
        Class<?> type = method.getReturnType();
        int parameterCount = method.getParameterCount();

        // is it a getXXX method
        if (name.startsWith("get") && name.length() >= 4 && Character.isUpperCase(name.charAt(3))) {
            return parameterCount == 0 && !type.equals(Void.TYPE);
        }

        // special for isXXX boolean
        if (name.startsWith("is") && name.length() >= 3 && Character.isUpperCase(name.charAt(2))) {
            return parameterCount == 0 && type.getSimpleName().equalsIgnoreCase("boolean");
        }

        return false;
    }

    static String getGetterShorthandName(Method method) {
        if (!isGetter(method)) {
            return method.getName();
        }

        String name = method.getName();
        if (name.startsWith("get")) {
            name = StringHelper.decapitalize(name.substring(3));
        } else if (name.startsWith("is")) {
            name = StringHelper.decapitalize(name.substring(2));
        }

        return name;
    }

    static String getSetterShorthandName(Method method) {
        if (!isSetter(method)) {
            return method.getName();
        }

        String name = method.getName();
        if (name.startsWith("set")) {
            name = name.substring(3);
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }

        return name;
    }

    static boolean isSetter(Method method, boolean allowBuilderPattern) {
        String name = method.getName();
        Class<?> type = method.getReturnType();
        int parameterCount = method.getParameterCount();
        Class<?> self = method.getDeclaringClass();

        // is it a setXXX method
        boolean validName = name.startsWith("set") && name.length() >= 4 && Character.isUpperCase(name.charAt(3));
        if (validName && parameterCount == 1) {
            // a setXXX can also be a builder pattern so check for its return type is itself
            return type.equals(Void.TYPE) || allowBuilderPattern && ObjectHelper.isSubclass(self, type);
        }
        // or if it's a builder method
        if (allowBuilderPattern && parameterCount == 1 && ObjectHelper.isSubclass(self, type)) {
            return true;
        }

        return false;
    }

    static boolean isSetter(Method method) {
        return isSetter(method, false);
    }

    /**
     * Will inspect the target for properties.
     * <p/>
     * Notice a property must have both a getter/setter method to be included. Notice all <tt>null</tt> values will be
     * included.
     *
     * @param  target       the target bean
     * @param  properties   the map to fill in found properties
     * @param  optionPrefix an optional prefix to append the property key
     * @return              <tt>true</tt> if any properties was found, <tt>false</tt> otherwise.
     */
    static boolean getProperties(Object target, Map<String, Object> properties, String optionPrefix) {
        return getProperties(target, properties, optionPrefix, true);
    }

    /**
     * Will inspect the target for properties.
     * <p/>
     * Notice a property must have both a getter/setter method to be included.
     *
     * @param  target       the target bean
     * @param  properties   the map to fill in found properties
     * @param  optionPrefix an optional prefix to append the property key
     * @param  includeNull  whether to include <tt>null</tt> values
     * @return              <tt>true</tt> if any properties was found, <tt>false</tt> otherwise.
     */
    static boolean getProperties(
            Object target, Map<String, Object> properties, String optionPrefix, boolean includeNull) {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");
        boolean rc = false;
        if (optionPrefix == null) {
            optionPrefix = "";
        }

        BeanIntrospection.ClassInfo cache = cacheClass(target.getClass());

        for (BeanIntrospection.MethodInfo info : cache.methods) {
            Method method = info.method;
            // we can only get properties if we have both a getter and a setter
            if (info.isGetter && info.hasGetterAndSetter) {
                String name = info.getterOrSetterShorthandName;
                try {
                    // we may want to set options on classes that has package view visibility, so override the accessible
                    Object value = invokeMethodSafe(method, target, null);
                    if (value != null || includeNull) {
                        properties.put(optionPrefix + name, value);
                        rc = true;
                    }
                } catch (Exception e) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Error invoking getter method {}. This exception is ignored.", method, e);
                    }
                }
            }
        }

        return rc;
    }

    /**
     * Introspects the given class.
     *
     * @param  clazz the class
     * @return       the introspection result as a {@link BeanIntrospection.ClassInfo} structure.
     */
    static BeanIntrospection.ClassInfo cacheClass(Class<?> clazz) {
        BeanIntrospection.ClassInfo cache = CACHE.get(clazz);
        if (cache == null) {
            cache = doIntrospectClass(clazz);
            CACHE.put(clazz, cache);
        }
        return cache;
    }

    static BeanIntrospection.ClassInfo doIntrospectClass(Class<?> clazz) {
        BeanIntrospection.ClassInfo answer = new BeanIntrospection.ClassInfo();
        answer.clazz = clazz;

        // loop each method on the class and gather details about the method
        // especially about getter/setters
        List<BeanIntrospection.MethodInfo> found = new ArrayList<>();
        Method[] methods = clazz.getMethods();
        Map<String, BeanIntrospection.MethodInfo> getters = new HashMap<>(methods.length);
        Map<String, BeanIntrospection.MethodInfo> setters = new HashMap<>(methods.length);
        for (Method method : methods) {
            if (EXCLUDED_METHODS.contains(method)) {
                continue;
            }

            BeanIntrospection.MethodInfo cache = new BeanIntrospection.MethodInfo();
            cache.method = method;
            if (isGetter(method)) {
                cache.isGetter = true;
                cache.isSetter = false;
                cache.getterOrSetterShorthandName = getGetterShorthandName(method);
                getters.put(cache.getterOrSetterShorthandName, cache);
            } else if (isSetter(method)) {
                cache.isGetter = false;
                cache.isSetter = true;
                cache.getterOrSetterShorthandName = getSetterShorthandName(method);
                setters.put(cache.getterOrSetterShorthandName, cache);
            } else {
                cache.isGetter = false;
                cache.isSetter = false;
            }
            found.add(cache);
        }

        // for all getter/setter, find out if there is a corresponding getter/setter,
        // so we have a read/write bean property.
        for (BeanIntrospection.MethodInfo info : found) {
            info.hasGetterAndSetter = false;
            if (info.isGetter) {
                info.hasGetterAndSetter = setters.containsKey(info.getterOrSetterShorthandName);
            } else if (info.isSetter) {
                info.hasGetterAndSetter = getters.containsKey(info.getterOrSetterShorthandName);
            }
        }

        answer.methods = found.toArray(new BeanIntrospection.MethodInfo[0]);
        return answer;
    }

    static Object getProperty(Object target, String propertyName)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(propertyName, "property");

        propertyName = StringHelper.capitalize(propertyName);

        Class<?> clazz = target.getClass();
        Method method = getPropertyGetter(clazz, propertyName);
        return method.invoke(target);
    }

    static Object getOrElseProperty(Object target, String propertyName, Object defaultValue, boolean ignoreCase) {
        try {
            if (ignoreCase) {
                Class<?> clazz = target.getClass();
                Method method = getPropertyGetter(clazz, propertyName, true);
                if (method != null) {
                    return method.invoke(target);
                } else {
                    // not found so return default value
                    return defaultValue;
                }
            } else {
                return getProperty(target, propertyName);
            }
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static Method getPropertyGetter(Class<?> type, String propertyName) throws NoSuchMethodException {
        return getPropertyGetter(type, propertyName, false);
    }

    static Method getPropertyGetter(Class<?> type, String propertyName, boolean ignoreCase)
            throws NoSuchMethodException {
        if (ignoreCase) {
            List<Method> methods = new ArrayList<>();
            methods.addAll(Arrays.asList(type.getDeclaredMethods()));
            methods.addAll(Arrays.asList(type.getMethods()));
            for (Method m : methods) {
                if (isGetter(m)) {
                    if (m.getName().startsWith("is") && m.getName().substring(2).equalsIgnoreCase(propertyName)) {
                        return m;
                    } else if (m.getName().startsWith("get") && m.getName().substring(3).equalsIgnoreCase(propertyName)) {
                        return m;
                    }
                }
            }
            // not found
            return null;
        } else {
            if (isPropertyIsGetter(type, propertyName)) {
                return type.getMethod("is" + StringHelper.capitalize(propertyName, true));
            } else {
                return type.getMethod("get" + StringHelper.capitalize(propertyName, true));
            }
        }
    }

    static Method getPropertySetter(Class<?> type, String propertyName) throws NoSuchMethodException {
        String name = "set" + StringHelper.capitalize(propertyName, true);
        for (Method method : type.getMethods()) {
            if (isSetter(method) && method.getName().equals(name)) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getCanonicalName() + "." + name);
    }

    static boolean isPropertyIsGetter(Class<?> type, String propertyName) {
        try {
            Method method = type.getMethod("is" + StringHelper.capitalize(propertyName, true));
            if (method != null) {
                return method.getReturnType().isAssignableFrom(boolean.class)
                        || method.getReturnType().isAssignableFrom(Boolean.class);
            }
        } catch (NoSuchMethodException e) {
            // ignore
        }
        return false;
    }

    /**
     * This method supports three modes to set a property:
     *
     * 1. Setting a Map property where the property name refers to a map via name[aKey] where aKey is the map key to
     * use.
     *
     * 2. Setting a property that has already been resolved, this is the case when {@code context} and {@code refName}
     * are NULL and {@code value} is non-NULL.
     *
     * 3. Setting a property that has not yet been resolved, the property will be resolved based on the suitable methods
     * found matching the property name on the {@code target} bean. For this mode to be triggered the parameters
     * {@code context} and {@code refName} must NOT be NULL, and {@code value} MUST be NULL.
     */
    static boolean setProperty(
            CamelContext context, TypeConverter typeConverter, Object target, String name, Object value, String refName,
            boolean allowBuilderPattern)
            throws Exception {
        return setProperty(context, typeConverter, target, name, value, refName, allowBuilderPattern, false, false);
    }

    /**
     * This method supports three modes to set a property:
     *
     * 1. Setting a Map property where the property name refers to a map via name[aKey] where aKey is the map key to
     * use.
     *
     * 2. Setting a property that has already been resolved, this is the case when {@code context} and {@code refName}
     * are NULL and {@code value} is non-NULL.
     *
     * 3. Setting a property that has not yet been resolved, the property will be resolved based on the suitable methods
     * found matching the property name on the {@code target} bean. For this mode to be triggered the parameters
     * {@code context} and {@code refName} must NOT be NULL, and {@code value} MUST be NULL.
     */
    static boolean setProperty(
            CamelContext context, TypeConverter typeConverter, Object target, String name, Object value, String refName,
            boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase)
            throws Exception {

        // does the property name include a lookup key, then we need to set the property as a map or list
        if (name.contains("[") && name.endsWith("]")) {
            int pos = name.indexOf('[');
            String lookupKey = name.substring(pos + 1, name.length() - 1);
            String key = name.substring(0, pos);

            Object obj = IntrospectionSupport.getOrElseProperty(target, key, null, ignoreCase);
            if (obj == null) {
                // it was supposed to be a list or map, but its null, so lets create a new list or map and set it automatically
                Method getter = IntrospectionSupport.getPropertyGetter(target.getClass(), key, ignoreCase);
                if (getter != null) {
                    // what type does it have
                    Class<?> returnType = getter.getReturnType();
                    if (Map.class.isAssignableFrom(returnType)) {
                        obj = new LinkedHashMap<>();
                    } else if (Collection.class.isAssignableFrom(returnType)) {
                        obj = new ArrayList<>();
                    } else if (returnType.isArray()) {
                        obj = Array.newInstance(returnType.getComponentType(), 0);
                    }
                } else {
                    // fallback as map type
                    obj = new LinkedHashMap<>();
                }
                boolean hit = IntrospectionSupport.setProperty(context, target, key, obj);
                if (!hit) {
                    throw new IllegalArgumentException(
                            "Cannot set property: " + name + " as a Map because target bean has no setter method for the Map");
                }
            }
            if (obj instanceof Map) {
                Map map = (Map) obj;
                if (context != null && refName != null && value == null) {
                    String s = refName.replace("#", "");
                    value = CamelContextHelper.lookup(context, s);
                }
                map.put(lookupKey, value);
                return true;
            } else if (obj instanceof List) {
                List list = (List) obj;
                if (context != null && refName != null && value == null) {
                    String s = refName.replace("#", "");
                    value = CamelContextHelper.lookup(context, s);
                }
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
            } else if (obj.getClass().isArray() && lookupKey != null) {
                if (context != null && refName != null && value == null) {
                    String s = refName.replace("#", "");
                    value = CamelContextHelper.lookup(context, s);
                }
                int idx = Integer.parseInt(lookupKey);
                int size = Array.getLength(obj);
                if (idx >= size) {
                    obj = Arrays.copyOf((Object[]) obj, idx + 1);

                    // replace array
                    boolean hit = IntrospectionSupport.setProperty(context, target, key, obj);
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

        Class<?> clazz = target.getClass();
        Collection<Method> setters;

        // we need to lookup the value from the registry
        if (context != null && refName != null && value == null) {
            setters = findSetterMethodsOrderedByParameterType(clazz, name, allowBuilderPattern, allowPrivateSetter, ignoreCase);
        } else {
            // find candidates of setter methods as there can be overloaded setters
            setters = findSetterMethods(clazz, name, value, allowBuilderPattern, allowPrivateSetter, ignoreCase);
        }
        if (setters.isEmpty()) {
            return false;
        }

        // loop and execute the best setter method
        Exception typeConversionFailed = null;
        Method stringSetterMethod = null;
        Iterator<Method> it = setters.iterator();
        while (it.hasNext()) {
            Method setter = it.next();
            Class<?> parameterType = setter.getParameterTypes()[0];
            if (parameterType.getName().equals("java.lang.String")) {
                stringSetterMethod = setter;
            }
            Object ref = value;
            // try and lookup the reference based on the method
            if (context != null && refName != null && ref == null) {
                String s = refName.replace("#", "");
                ref = CamelContextHelper.lookup(context, s);
                if (ref == null) {
                    // try the next method if nothing was found
                    // if we did not found a good candidate then fallback to use the string setter (if possible) with the actual ref name value as-is
                    if (!it.hasNext() && stringSetterMethod != null) {
                        setter = stringSetterMethod;
                        ref = refName;
                    } else {
                        continue;
                    }
                } else {
                    // setter method has not the correct type
                    // (must use ObjectHelper.isAssignableFrom which takes primitive types into account)
                    boolean assignable = ObjectHelper.isAssignableFrom(parameterType, ref.getClass());
                    if (!assignable) {
                        continue;
                    }
                }
            }

            try {
                try {
                    // If the type is null or it matches the needed type, just use the value directly
                    if (value == null || ObjectHelper.isAssignableFrom(parameterType, ref.getClass())) {
                        // we may want to set options on classes that has package view visibility, so override the accessible
                        setter.setAccessible(true);
                        setter.invoke(target, ref);
                        if (LOG.isTraceEnabled()) {
                            // hide sensitive data
                            String val = ref != null ? ref.toString() : "";
                            if (SECRETS.matcher(name).find()) {
                                val = "xxxxxx";
                            }
                            LOG.trace("Configured property: {} on bean: {} with value: {}", name, target, val);
                        }
                        return true;
                    } else {
                        // We need to convert it
                        // special for boolean values with string values as we only want to accept "true" or "false"
                        if ((parameterType == Boolean.class || parameterType == boolean.class) && ref instanceof String) {
                            String val = (String) ref;
                            if (!val.equalsIgnoreCase("true") && !val.equalsIgnoreCase("false")) {
                                throw new IllegalArgumentException(
                                        "Cannot convert the String value: " + ref + " to type: " + parameterType
                                                                   + " as the value is not true or false");
                            }
                        }
                        Object convertedValue
                                = typeConverter != null ? typeConverter.mandatoryConvertTo(parameterType, ref) : ref;
                        // we may want to set options on classes that has package view visibility, so override the accessible
                        setter.setAccessible(true);
                        setter.invoke(target, convertedValue);
                        if (LOG.isTraceEnabled()) {
                            // hide sensitive data
                            String val = ref.toString();
                            if (SECRETS.matcher(name).find()) {
                                val = "xxxxxx";
                            }
                            LOG.trace("Configured property: {} on bean: {} with value: {}", name, target, val);
                        }
                        return true;
                    }
                } catch (InvocationTargetException e) {
                    // lets unwrap the exception
                    Throwable throwable = e.getCause();
                    if (throwable instanceof Exception) {
                        throw (Exception) throwable;
                    } else {
                        throw (Error) throwable;
                    }
                }
                // ignore exceptions as there could be another setter method where we could type convert successfully
            } catch (SecurityException | NoTypeConversionAvailableException | IllegalArgumentException e) {
                typeConversionFailed = e;
            }

            LOG.trace("Setter \"{}\" with parameter type \"{}\" could not be used for type conversions of {}",
                    setter, parameterType, ref);
        }

        if (typeConversionFailed != null && !isPropertyPlaceholder(context, value)) {
            // we did not find a setter method to use, and if we did try to use a type converter then throw
            // this kind of exception as the caused by will hint this error
            throw new IllegalArgumentException(
                    "Could not find a suitable setter for property: " + name
                                               + " as there isn't a setter method with same type: "
                                               + (value != null ? value.getClass().getCanonicalName() : "[null]")
                                               + " nor type conversion possible: " + typeConversionFailed.getMessage());
        } else {
            return false;
        }
    }

    static boolean isPropertyPlaceholder(CamelContext context, Object value) {
        if (context != null && value != null) {
            String text = value.toString();
            return text.contains(PropertiesComponent.PREFIX_TOKEN) && text.contains(PropertiesComponent.SUFFIX_TOKEN);
        }
        return false;
    }

    static boolean setProperty(CamelContext context, Object target, String name, Object value) throws Exception {
        // allow build pattern as a setter as well
        return setProperty(context, context != null ? context.getTypeConverter() : null, target, name, value, null, true, false,
                false);
    }

    static boolean setProperty(
            CamelContext context, TypeConverter typeConverter, Object target, String name, Object value)
            throws Exception {
        // allow build pattern as a setter as well
        return setProperty(context, typeConverter, target, name, value, null, true, false, false);
    }

    static boolean setProperty(TypeConverter typeConverter, Object target, String name, Object value) throws Exception {
        // allow build pattern as a setter as well
        return setProperty(null, typeConverter, target, name, value, null, true, false, false);
    }

    static boolean setProperty(Object target, String name, Object value, boolean allowBuilderPattern) throws Exception {
        return setProperty(null, null, target, name, value, null, allowBuilderPattern, false, false);
    }

    static boolean setProperty(Object target, String name, Object value) throws Exception {
        // allow build pattern as a setter as well
        return setProperty(target, name, value, true);
    }

    static Set<Method> findSetterMethods(
            Class<?> clazz, String name,
            boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase) {
        Set<Method> candidates = new LinkedHashSet<>();

        // Build the method name
        String builderName = "with" + StringHelper.capitalize(name, true);
        String builderName2 = StringHelper.capitalize(name, true);
        builderName2 = Character.toLowerCase(builderName2.charAt(0)) + builderName2.substring(1);
        String setName = "set" + StringHelper.capitalize(name, true);
        while (clazz != Object.class) {
            // Since Object.class.isInstance all the objects,
            // here we just make sure it will be add to the bottom of the set.
            Method objectSetMethod = null;
            Method[] methods = allowPrivateSetter ? clazz.getDeclaredMethods() : clazz.getMethods();
            for (Method method : methods) {
                boolean validName;
                if (ignoreCase) {
                    validName = method.getName().equalsIgnoreCase(setName)
                            || allowBuilderPattern && method.getName().equalsIgnoreCase(builderName)
                            || allowBuilderPattern && method.getName().equalsIgnoreCase(builderName2);
                } else {
                    validName = method.getName().equals(setName)
                            || allowBuilderPattern && method.getName().equals(builderName)
                            || allowBuilderPattern && method.getName().equals(builderName2);
                }
                if (validName) {
                    if (isSetter(method, allowBuilderPattern)) {
                        Class<?>[] params = method.getParameterTypes();
                        if (params[0].equals(Object.class)) {
                            objectSetMethod = method;
                        } else {
                            candidates.add(method);
                        }
                    }
                }
            }
            if (objectSetMethod != null) {
                candidates.add(objectSetMethod);
            }
            clazz = clazz.getSuperclass();
        }
        return candidates;
    }

    static Set<Method> findSetterMethods(
            Class<?> clazz, String name, Object value,
            boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase) {
        Set<Method> candidates = findSetterMethods(clazz, name, allowBuilderPattern, allowPrivateSetter, ignoreCase);

        if (candidates.isEmpty()) {
            return candidates;
        } else if (candidates.size() == 1) {
            // only one
            return candidates;
        } else {
            // find the best match if possible
            LOG.trace("Found {} suitable setter methods for setting {}", candidates.size(), name);
            // prefer to use the one with the same instance if any exists
            for (Method method : candidates) {
                if (method.getParameterTypes()[0].isInstance(value)) {
                    LOG.trace("Method {} is the best candidate as it has parameter with same instance type", method);
                    // retain only this method in the answer
                    candidates.clear();
                    candidates.add(method);
                    return candidates;
                }
            }
            // fallback to return what we have found as candidates so far
            return candidates;
        }
    }

    static List<Method> findSetterMethodsOrderedByParameterType(
            Class<?> target, String propertyName,
            boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase) {
        List<Method> answer = new LinkedList<>();
        List<Method> primitives = new LinkedList<>();
        Set<Method> setters = findSetterMethods(target, propertyName, allowBuilderPattern, allowPrivateSetter, ignoreCase);
        for (Method setter : setters) {
            Class<?> parameterType = setter.getParameterTypes()[0];
            if (PRIMITIVE_CLASSES.contains(parameterType)) {
                primitives.add(setter);
            } else {
                answer.add(setter);
            }
        }
        // primitives get added last
        answer.addAll(primitives);
        return answer;
    }

}
