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
package org.apache.camel.util;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for introspections of beans.
 */
public final class IntrospectionSupport {

    private static final transient Logger LOG = LoggerFactory.getLogger(IntrospectionSupport.class);
    private static final Pattern GETTER_PATTERN = Pattern.compile("(get|is)[A-Z].*");
    private static final Pattern SETTER_PATTERN = Pattern.compile("set[A-Z].*");
    private static final List<Method> EXCLUDED_METHODS = new ArrayList<Method>();

    static {
        // exclude all java.lang.Object methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Object.class.getMethods()));
        // exclude all java.lang.reflect.Proxy methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Proxy.class.getMethods()));
    }

    /**
     * Utility classes should not have a public constructor.
     */
    private IntrospectionSupport() {
    }

    public static boolean isGetter(Method method) {
        String name = method.getName();
        Class<?> type = method.getReturnType();
        Class<?> params[] = method.getParameterTypes();

        if (!GETTER_PATTERN.matcher(name).matches()) {
            return false;
        }

        // special for isXXX boolean
        if (name.startsWith("is")) {
            return params.length == 0 && type.getSimpleName().equalsIgnoreCase("boolean");
        }

        return params.length == 0 && !type.equals(Void.TYPE);
    }

    public static String getGetterShorthandName(Method method) {
        if (!isGetter(method)) {
            return method.getName();
        }

        String name = method.getName();
        if (name.startsWith("get")) {
            name = name.substring(3);
            name = name.substring(0, 1).toLowerCase() + name.substring(1);
        } else if (name.startsWith("is")) {
            name = name.substring(2);
            name = name.substring(0, 1).toLowerCase() + name.substring(1);
        }

        return name;
    }

    public static String getSetterShorthandName(Method method) {
        if (!isSetter(method)) {
            return method.getName();
        }

        String name = method.getName();
        if (name.startsWith("set")) {
            name = name.substring(3);
            name = name.substring(0, 1).toLowerCase() + name.substring(1);
        }

        return name;
    }

    public static boolean isSetter(Method method) {
        String name = method.getName();
        Class<?> type = method.getReturnType();
        Class<?> params[] = method.getParameterTypes();

        if (!SETTER_PATTERN.matcher(name).matches()) {
            return false;
        }

        return params.length == 1 && type.equals(Void.TYPE);
    }

    /**
     * Will inspect the target for properties.
     * <p/>
     * Notice a property must have both a getter/setter method to be included.
     *
     * @param target         the target bean
     * @param properties     the map to fill in found properties
     * @param optionPrefix   an optional prefix to append the property key
     * @return <tt>true</tt> if any properties was found, <tt>false</tt> otherwise.
     */
    @SuppressWarnings("unchecked")
    public static boolean getProperties(Object target, Map properties, String optionPrefix) {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");
        boolean rc = false;
        if (optionPrefix == null) {
            optionPrefix = "";
        }

        Class clazz = target.getClass();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (EXCLUDED_METHODS.contains(method)) {
                continue;
            }
            try {
                // must be properties which have setters
                if (isGetter(method) && hasSetter(target, method)) {
                    Object value = method.invoke(target);
                    String name = getGetterShorthandName(method);
                    properties.put(optionPrefix + name, value);
                    rc = true;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        return rc;
    }

    public static boolean hasSetter(Object target, Method getter) {
        String name = getGetterShorthandName(getter);

        Class clazz = target.getClass();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (EXCLUDED_METHODS.contains(method)) {
                continue;
            }
            if (isSetter(method)) {
                if (name.equals(getSetterShorthandName(method))) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasProperties(Map<String, Object> properties, String optionPrefix) {
        ObjectHelper.notNull(properties, "properties");

        if (ObjectHelper.isNotEmpty(optionPrefix)) {
            for (Object o : properties.keySet()) {
                String name = (String) o;
                if (name.startsWith(optionPrefix)) {
                    return true;
                }
            }
            // no parameters with this prefix
            return false;
        } else {
            return !properties.isEmpty();
        }
    }

    public static Object getProperty(Object target, String property) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(property, "property");

        property = property.substring(0, 1).toUpperCase(Locale.ENGLISH) + property.substring(1);

        Class<?> clazz = target.getClass();
        Method method = getPropertyGetter(clazz, property);
        return method.invoke(target);
    }

    public static Method getPropertyGetter(Class<?> type, String propertyName) throws NoSuchMethodException {
        if (isPropertyIsGetter(type, propertyName)) {
            return type.getMethod("is" + ObjectHelper.capitalize(propertyName));
        } else {
            return type.getMethod("get" + ObjectHelper.capitalize(propertyName));
        }
    }

    public static Method getPropertySetter(Class<?> type, String propertyName) throws NoSuchMethodException {
        String name = "set" + ObjectHelper.capitalize(propertyName);
        for (Method method : type.getMethods()) {
            if (isSetter(method) && method.getName().equals(name)) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getCanonicalName() + "." + name);
    }

    public static boolean isPropertyIsGetter(Class<?> type, String propertyName) {
        try {
            Method method = type.getMethod("is" + ObjectHelper.capitalize(propertyName));
            if (method != null) {
                return method.getReturnType().isAssignableFrom(boolean.class) || method.getReturnType().isAssignableFrom(Boolean.class);
            }
        } catch (NoSuchMethodException e) {
            // ignore
        }
        return false;
    }

    public static boolean setProperties(Object target, Map<String, Object> properties, String optionPrefix) throws Exception {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");
        boolean rc = false;

        for (Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Object> entry = it.next();
            String name = entry.getKey().toString();
            if (name.startsWith(optionPrefix)) {
                Object value = properties.get(name);
                name = name.substring(optionPrefix.length());
                if (setProperty(target, name, value)) {
                    it.remove();
                    rc = true;
                }
            }
        }

        return rc;
    }

    public static Map<String, Object> extractProperties(Map<String, Object> properties, String optionPrefix) {
        ObjectHelper.notNull(properties, "properties");

        HashMap<String, Object> rc = new LinkedHashMap<String, Object>(properties.size());

        for (Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Object> entry = it.next();
            String name = entry.getKey();
            if (name.startsWith(optionPrefix)) {
                Object value = properties.get(name);
                name = name.substring(optionPrefix.length());
                rc.put(name, value);
                it.remove();
            }
        }

        return rc;
    }

    public static boolean setProperties(TypeConverter typeConverter, Object target, Map<String, Object> properties) throws Exception {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");
        boolean rc = false;

        for (Iterator<Map.Entry<String, Object>> iter = properties.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<String, Object> entry = iter.next();
            if (setProperty(typeConverter, target, entry.getKey(), entry.getValue())) {
                iter.remove();
                rc = true;
            }
        }

        return rc;
    }

    public static boolean setProperties(Object target, Map<String, Object> properties) throws Exception {
        return setProperties(null, target, properties);
    }

    public static boolean setProperty(TypeConverter typeConverter, Object target, String name, Object value) throws Exception {
        try {
            Class<?> clazz = target.getClass();
            // find candidates of setter methods as there can be overloaded setters
            Set<Method> setters = findSetterMethods(typeConverter, clazz, name, value);
            if (setters.isEmpty()) {
                return false;
            }

            // loop and execute the best setter method
            Exception typeConvertionFailed = null;
            for (Method setter : setters) {
                // If the type is null or it matches the needed type, just use the value directly
                if (value == null || setter.getParameterTypes()[0].isAssignableFrom(value.getClass())) {
                    setter.invoke(target, value);
                    return true;
                } else {
                    // We need to convert it
                    try {
                        // ignore exceptions as there could be another setter method where we could type convert successfully
                        Object convertedValue = convert(typeConverter, setter.getParameterTypes()[0], value);
                        setter.invoke(target, convertedValue);
                        return true;
                    } catch (NoTypeConversionAvailableException e) {
                        typeConvertionFailed = e;
                    } catch (IllegalArgumentException e) {
                        typeConvertionFailed = e;
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Setter \"{}\" with parameter type \"{}\" could not be used for type conversions of {}",
                                new Object[]{setter, setter.getParameterTypes()[0], value});
                    }
                }
            }
            // we did not find a setter method to use, and if we did try to use a type converter then throw
            // this kind of exception as the caused by will hint this error
            if (typeConvertionFailed != null) {
                throw new IllegalArgumentException("Could not find a suitable setter for property: " + name
                        + " as there isn't a setter method with same type: " + value.getClass().getCanonicalName()
                        + " nor type conversion possible: " + typeConvertionFailed.getMessage());
            } else {
                return false;
            }
        } catch (InvocationTargetException e) {
            // lets unwrap the exception
            Throwable throwable = e.getCause();
            if (throwable instanceof Exception) {
                Exception exception = (Exception)throwable;
                throw exception;
            } else {
                Error error = (Error)throwable;
                throw error;
            }
        }
    }

    public static boolean setProperty(Object target, String name, Object value) throws Exception {
        return setProperty(null, target, name, value);
    }

    @SuppressWarnings("unchecked")
    private static Object convert(TypeConverter typeConverter, Class type, Object value)
        throws URISyntaxException, NoTypeConversionAvailableException {
        if (typeConverter != null) {
            return typeConverter.mandatoryConvertTo(type, value);
        }
        PropertyEditor editor = PropertyEditorManager.findEditor(type);
        if (editor != null) {
            editor.setAsText(value.toString());
            return editor.getValue();
        }
        if (type == URI.class) {
            return new URI(value.toString());
        }
        return null;
    }

    private static Set<Method> findSetterMethods(TypeConverter typeConverter, Class<?> clazz, String name, Object value) {
        Set<Method> candidates = new LinkedHashSet<Method>();

        // Build the method name.
        name = "set" + ObjectHelper.capitalize(name);
        while (clazz != Object.class) {
            // Since Object.class.isInstance all the objects,
            // here we just make sure it will be add to the bottom of the set.
            Method objectSetMethod = null;
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                Class<?> params[] = method.getParameterTypes();
                if (method.getName().equals(name) && params.length == 1) {
                    Class<?> paramType = params[0];
                    if (paramType.equals(Object.class)) {                        
                        objectSetMethod = method;
                    } else if (typeConverter != null || isSetter(method) || paramType.isInstance(value)) {
                        candidates.add(method);
                    }
                }
            }
            if (objectSetMethod != null) {
                candidates.add(objectSetMethod);
            }
            clazz = clazz.getSuperclass();
        }

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

}
