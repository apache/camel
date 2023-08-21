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
package org.apache.camel.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A number of useful helper methods for working with Objects
 */
public final class ObjectHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectHelper.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectHelper() {
    }

    /**
     * A helper method for comparing objects for equality while handling nulls
     */
    public static boolean equal(Object a, Object b) {
        return equal(a, b, false);
    }

    /**
     * A helper method for comparing objects for equality while handling case insensitivity
     */
    public static boolean equalIgnoreCase(Object a, Object b) {
        return equal(a, b, true);
    }

    /**
     * A helper method for comparing objects for equality while handling nulls
     */
    public static boolean equal(final Object a, final Object b, final boolean ignoreCase) {
        if (a == b) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        if (ignoreCase) {
            if (a instanceof String && b instanceof String) {
                return ((String) a).equalsIgnoreCase((String) b);
            }
        }

        if (a.getClass().isArray() && b.getClass().isArray()) {
            // uses array based equals
            return Objects.deepEquals(a, b);
        } else {
            // use regular equals
            return a.equals(b);
        }
    }

    /**
     * A helper method for comparing byte arrays for equality while handling nulls
     */
    public static boolean equalByteArray(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    /**
     * Returns true if the given object is equal to any of the expected value
     */
    public static boolean isEqualToAny(Object object, Object... values) {
        for (Object value : values) {
            if (equal(object, value)) {
                return true;
            }
        }
        return false;
    }

    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof byte[]) {
            String str = new String((byte[]) value);
            if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                return Boolean.valueOf(str);
            }
        }
        if (value instanceof String) {
            // we only want to accept true or false as accepted values
            String str = (String) value;
            if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                return Boolean.valueOf(str);
            }
        }
        if (value instanceof Integer) {
            return (Integer) value > 0 ? Boolean.TRUE : Boolean.FALSE;
        }
        return null;
    }

    /**
     * Asserts whether the value is <b>not</b> <tt>null</tt>
     *
     * @param  value                    the value to test
     * @param  name                     the key that resolved the value
     * @return                          the passed {@code value} as is
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static <T> T notNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be specified");
        }

        return value;
    }

    /**
     * Asserts that the given {@code value} is neither {@code null} nor an emptyString.
     *
     * @param  value                    the value to test
     * @param  name                     the key that resolved the value
     * @return                          the passed {@code value} as is
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static String notNullOrEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must be specified and non-empty");
        }
        return value;
    }

    /**
     * Asserts whether the value is <b>not</b> <tt>null</tt>
     *
     * @param  value                    the value to test
     * @param  on                       additional description to indicate where this problem occurred (appended as
     *                                  toString())
     * @param  name                     the key that resolved the value
     * @return                          the passed {@code value} as is
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static <T> T notNull(T value, String name, Object on) {
        if (on == null) {
            notNull(value, name);
        } else if (value == null) {
            throw new IllegalArgumentException(name + " must be specified on: " + on);
        }

        return value;
    }

    /**
     * Tests whether the value is <tt>null</tt> or an empty string or an empty collection/map.
     *
     * @param  value the value, if its a String it will be tested for text length as well
     * @return       true if empty
     */
    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Tests whether the value is <tt>null</tt> or an an empty collection
     *
     * @param  value the value to test
     * @return       true if empty
     */
    public static boolean isEmpty(Collection<?> value) {
        return value == null || value.isEmpty();
    }

    /**
     * Tests whether the value is <tt>null</tt> or an an empty map
     *
     * @param  value the value to test
     * @return       true if empty
     */
    public static boolean isEmpty(Map<?, ?> value) {
        return value == null || value.isEmpty();
    }

    /**
     * Tests whether the value is <tt>null</tt>, an empty string or an empty collection/map.
     *
     * @param  value the value, if its a String it will be tested for text length as well
     * @return       true if empty
     */
    public static <T> boolean isEmpty(T value) {
        if (value == null) {
            return true;
        } else if (value instanceof String) {
            return isEmpty((String) value);
        } else if (value instanceof Collection) {
            return isEmpty((Collection<?>) value);
        } else if (value instanceof Map) {
            return isEmpty((Map<?, ?>) value);
        } else {
            return false;
        }
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt>, an empty string or an empty collection/map.
     *
     * @param  value the value, if its a String it will be tested for text length as well
     * @return       true if <b>not</b> empty
     */
    public static <T> boolean isNotEmpty(T value) {
        return !isEmpty(value);
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt> or an empty string
     *
     * @param  value the value, if its a String it will be tested for text length as well
     * @return       true if <b>not</b> empty
     */
    public static boolean isNotEmpty(String value) {
        return !isEmpty(value);
    }

    /**
     * Tests whether the value is <tt>null</tt> or an an empty collection
     *
     * @param  value the value to test
     * @return       true if empty
     */
    public static boolean isNotEmpty(Collection<?> value) {
        return !isEmpty(value);
    }

    /**
     * Tests whether the value is <tt>null</tt> or an an empty map
     *
     * @param  value the value to test
     * @return       true if empty
     */
    public static boolean isNotEmpty(Map<?, ?> value) {
        return !isEmpty(value);
    }

    /**
     * Returns the first non null object <tt>null</tt>.
     *
     * @param  values the values
     * @return        an Optional
     */
    public static Optional<Object> firstNotNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return Optional.of(value);
            }
        }

        return Optional.empty();
    }

    /**
     * Tests whether the value is <tt>null</tt>, an empty string, an empty collection or a map
     *
     * @param value    the value, if its a String it will be tested for text length as well
     * @param supplier the supplier, the supplier to be used to get a value if value is null
     */
    public static <T> T supplyIfEmpty(T value, Supplier<T> supplier) {
        org.apache.camel.util.ObjectHelper.notNull(supplier, "Supplier");
        if (isNotEmpty(value)) {
            return value;
        }

        return supplier.get();
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt>, an empty string, an empty collection or a map
     *
     * @param value    the value, if its a String it will be tested for text length as well
     * @param consumer the consumer, the operation to be executed against value if not empty
     */
    public static <T> void ifNotEmpty(T value, Consumer<T> consumer) {
        if (isNotEmpty(value)) {
            consumer.accept(value);
        }
    }

    /**
     * Returns the predicate matching boolean on a {@link List} result set where if the first element is a boolean its
     * value is used otherwise this method returns true if the collection is not empty
     *
     * @return <tt>true</tt> if the first element is a boolean and its value is true or if the list is non empty
     */
    public static boolean matches(List<?> list) {
        if (!list.isEmpty()) {
            Object value = list.get(0);
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else {
                // lets assume non-empty results are true
                return true;
            }
        }
        return false;
    }

    /**
     * A helper method to access a system property, catching any security exceptions
     *
     * @param  name         the name of the system property required
     * @param  defaultValue the default value to use if the property is not available or a security exception prevents
     *                      access
     * @return              the system property value or the default value if the property is not available or security
     *                      does not allow its access
     */
    public static String getSystemProperty(String name, String defaultValue) {
        try {
            return System.getProperty(name, defaultValue);
        } catch (Exception e) {
            LOG.debug("Caught security exception accessing system property: {}. Will use default value: {}",
                    name, defaultValue, e);

            return defaultValue;
        }
    }

    /**
     * A helper method to access a boolean system property, catching any security exceptions
     *
     * @param  name         the name of the system property required
     * @param  defaultValue the default value to use if the property is not available or a security exception prevents
     *                      access
     * @return              the boolean representation of the system property value or the default value if the property
     *                      is not available or security does not allow its access
     */
    public static boolean getSystemProperty(String name, Boolean defaultValue) {
        String result = getSystemProperty(name, defaultValue.toString());
        return Boolean.parseBoolean(result);
    }

    /**
     * Returns the type name of the given type or null if the type variable is null
     */
    public static String name(Class<?> type) {
        return type != null ? type.getName() : null;
    }

    /**
     * Returns the type name of the given value
     */
    public static String className(Object value) {
        return name(value != null ? value.getClass() : null);
    }

    /**
     * Returns the canonical type name of the given value
     */
    public static String classCanonicalName(Object value) {
        if (value != null) {
            return value.getClass().getCanonicalName();
        } else {
            return null;
        }
    }

    /**
     * Attempts to load the given class name using the thread context class loader or the class loader used to load this
     * class
     *
     * @param  name the name of the class to load
     * @return      the class or <tt>null</tt> if it could not be loaded
     */
    public static Class<?> loadClass(String name) {
        return loadClass(name, ObjectHelper.class.getClassLoader());
    }

    /**
     * Attempts to load the given class name using the thread context class loader or the given class loader
     *
     * @param  name   the name of the class to load
     * @param  loader the class loader to use after the thread context class loader
     * @return        the class or <tt>null</tt> if it could not be loaded
     */
    public static Class<?> loadClass(String name, ClassLoader loader) {
        return loadClass(name, loader, false);
    }

    /**
     * Attempts to load the given class name using the thread context class loader or the given class loader
     *
     * @param  name       the name of the class to load
     * @param  loader     the class loader to use after the thread context class loader
     * @param  needToWarn when <tt>true</tt> logs a warning when a class with the given name could not be loaded
     * @return            the class or <tt>null</tt> if it could not be loaded
     */
    public static Class<?> loadClass(String name, ClassLoader loader, boolean needToWarn) {
        // must clean the name so its pure java name, eg removing \n or whatever people can do in the Spring XML
        name = StringHelper.normalizeClassName(name);
        if (org.apache.camel.util.ObjectHelper.isEmpty(name)) {
            return null;
        }

        boolean array = false;

        // Try simple type first
        Class<?> clazz = loadSimpleType(name);
        if (clazz == null) {
            // special for array as we need to load the class and then after that instantiate an array class type
            if (name.endsWith("[]")) {
                name = name.substring(0, name.length() - 2);
                array = true;
            }
        }

        if (clazz == null) {
            // try context class loader
            clazz = doLoadClass(name, Thread.currentThread().getContextClassLoader());
        }
        if (clazz == null) {
            // then the provided loader
            clazz = doLoadClass(name, loader);
        }
        if (clazz == null) {
            // and fallback to the loader the loaded the ObjectHelper class
            clazz = doLoadClass(name, ObjectHelper.class.getClassLoader());
        }
        if (clazz != null && array) {
            Object arr = Array.newInstance(clazz, 0);
            clazz = arr.getClass();
        }

        if (clazz == null) {
            if (needToWarn) {
                LOG.warn("Cannot find class: {}", name);
            } else {
                LOG.debug("Cannot find class: {}", name);
            }
        }

        return clazz;
    }

    /**
     * Load a simple type
     *
     * @param  name the name of the class to load
     * @return      the class or <tt>null</tt> if it could not be loaded
     */
    public static Class<?> loadSimpleType(String name) {
        // special for byte[] or Object[] as its common to use
        if ("java.lang.byte[]".equals(name) || "byte[]".equals(name)) {
            return byte[].class;
        } else if ("java.lang.Byte[]".equals(name) || "Byte[]".equals(name)) {
            return Byte[].class;
        } else if ("java.lang.Object[]".equals(name) || "Object[]".equals(name)) {
            return Object[].class;
        } else if ("java.lang.String[]".equals(name) || "String[]".equals(name)) {
            return String[].class;
            // and these is common as well
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return String.class;
        } else if ("java.lang.Boolean".equals(name) || "Boolean".equals(name)) {
            return Boolean.class;
        } else if ("boolean".equals(name)) {
            return boolean.class;
        } else if ("java.lang.Integer".equals(name) || "Integer".equals(name)) {
            return Integer.class;
        } else if ("int".equals(name)) {
            return int.class;
        } else if ("java.lang.Long".equals(name) || "Long".equals(name)) {
            return Long.class;
        } else if ("long".equals(name)) {
            return long.class;
        } else if ("java.lang.Short".equals(name) || "Short".equals(name)) {
            return Short.class;
        } else if ("short".equals(name)) {
            return short.class;
        } else if ("java.lang.Byte".equals(name) || "Byte".equals(name)) {
            return Byte.class;
        } else if ("byte".equals(name)) {
            return byte.class;
        } else if ("java.lang.Float".equals(name) || "Float".equals(name)) {
            return Float.class;
        } else if ("float".equals(name)) {
            return float.class;
        } else if ("java.lang.Double".equals(name) || "Double".equals(name)) {
            return Double.class;
        } else if ("double".equals(name)) {
            return double.class;
        } else if ("java.lang.Character".equals(name) || "Character".equals(name)) {
            return Character.class;
        } else if ("char".equals(name)) {
            return char.class;
        }
        return null;
    }

    /**
     * Loads the given class with the provided classloader (may be null). Will ignore any class not found and return
     * null.
     *
     * @param  name   the name of the class to load
     * @param  loader a provided loader (may be null)
     * @return        the class, or null if it could not be loaded
     */
    private static Class<?> doLoadClass(String name, ClassLoader loader) {
        StringHelper.notEmpty(name, "name");
        if (loader == null) {
            return null;
        }

        try {
            LOG.trace("Loading class: {} using classloader: {}", name, loader);
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Cannot load class: {} using classloader: {}", name, loader, e);
            }
        }

        return null;
    }

    /**
     * Attempts to load the given resource as a stream using the thread context class loader or the class loader used to
     * load this class
     *
     * @param  name the name of the resource to load
     * @return      the stream or null if it could not be loaded
     */
    public static InputStream loadResourceAsStream(String name) {
        return loadResourceAsStream(name, null);
    }

    /**
     * Attempts to load the given resource as a stream using first the given class loader, then the thread context class
     * loader and finally the class loader used to load this class
     *
     * @param  name   the name of the resource to load
     * @param  loader optional classloader to attempt first
     * @return        the stream or null if it could not be loaded
     */
    public static InputStream loadResourceAsStream(String name, ClassLoader loader) {
        try {
            URL res = loadResourceAsURL(name, loader);
            return res != null ? res.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Attempts to load the given resource as a stream using the thread context class loader or the class loader used to
     * load this class
     *
     * @param  name the name of the resource to load
     * @return      the stream or null if it could not be loaded
     */
    public static URL loadResourceAsURL(String name) {
        return loadResourceAsURL(name, null);
    }

    /**
     * Attempts to load the given resource as a stream using the thread context class loader or the class loader used to
     * load this class
     *
     * @param  name   the name of the resource to load
     * @param  loader optional classloader to attempt first
     * @return        the stream or null if it could not be loaded
     */
    public static URL loadResourceAsURL(String name, ClassLoader loader) {

        URL url = null;
        String resolvedName = resolveUriPath(name);

        // #1 First, try the given class loader

        if (loader != null) {
            url = loader.getResource(resolvedName);
            if (url != null) {
                return url;
            }
        }

        // #2 Next, is the TCCL

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {

            url = tccl.getResource(resolvedName);
            if (url != null) {
                return url;
            }

            // #3 The TCCL may be able to see camel-core, but not META-INF resources

            try {

                Class<?> clazz = tccl.loadClass("org.apache.camel.impl.DefaultCamelContext");
                url = clazz.getClassLoader().getResource(resolvedName);
                if (url != null) {
                    return url;
                }

            } catch (ClassNotFoundException e) {
                // ignore
            }
        }

        // #4 Last, for the unlikely case that stuff can be loaded from camel-util

        url = ObjectHelper.class.getClassLoader().getResource(resolvedName);
        if (url != null) {
            return url;
        }

        url = ObjectHelper.class.getResource(resolvedName);
        return url;
    }

    /**
     * Attempts to load the given resources from the given package name using the thread context class loader or the
     * class loader used to load this class
     *
     * @param  uri the name of the package to load its resources
     * @return     the URLs for the resources or null if it could not be loaded
     */
    public static Enumeration<URL> loadResourcesAsURL(String uri) {
        return loadResourcesAsURL(uri, null);
    }

    /**
     * Attempts to load the given resources from the given package name using the thread context class loader or the
     * class loader used to load this class
     *
     * @param  uri    the name of the package to load its resources
     * @param  loader optional classloader to attempt first
     * @return        the URLs for the resources or null if it could not be loaded
     */
    public static Enumeration<URL> loadResourcesAsURL(String uri, ClassLoader loader) {

        Enumeration<URL> res = null;

        // #1 First, try the given class loader

        if (loader != null) {
            try {
                res = loader.getResources(uri);
                if (res != null) {
                    return res;
                }
            } catch (IOException e) {
                // ignore
            }
        }

        // #2 Next, is the TCCL

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {

            try {
                res = tccl.getResources(uri);
                if (res != null) {
                    return res;
                }
            } catch (IOException e1) {
                // ignore
            }

            // #3 The TCCL may be able to see camel-core, but not META-INF resources

            try {

                Class<?> clazz = tccl.loadClass("org.apache.camel.impl.DefaultCamelContext");
                res = clazz.getClassLoader().getResources(uri);
                if (res != null) {
                    return res;
                }

            } catch (ClassNotFoundException | IOException e) {
                // ignore
            }
        }

        // #4 Last, for the unlikely case that stuff can be loaded from camel-util

        try {
            res = ObjectHelper.class.getClassLoader().getResources(uri);
        } catch (IOException e) {
            // ignore
        }

        return res;
    }

    /**
     * Helper operation used to remove relative path notation from resources. Most critical for resources on the
     * Classpath as resource loaders will not resolve the relative paths correctly.
     *
     * @param  name the name of the resource to load
     * @return      the modified or unmodified string if there were no changes
     */
    private static String resolveUriPath(String name) {
        // compact the path and use / as separator as that's used for loading resources on the classpath
        return FileUtil.compactPath(name, '/');
    }

    /**
     * Tests whether the target method overrides the source method.
     * <p/>
     * Tests whether they have the same name, return type, and parameter list.
     *
     * @param  source the source method
     * @param  target the target method
     * @return        <tt>true</tt> if it override, <tt>false</tt> otherwise
     */
    public static boolean isOverridingMethod(Method source, Method target) {
        return isOverridingMethod(source, target, true);
    }

    /**
     * Tests whether the target method overrides the source method.
     * <p/>
     * Tests whether they have the same name, return type, and parameter list.
     *
     * @param  source the source method
     * @param  target the target method
     * @param  exact  <tt>true</tt> if the override must be exact same types, <tt>false</tt> if the types should be
     *                assignable
     * @return        <tt>true</tt> if it override, <tt>false</tt> otherwise
     */
    public static boolean isOverridingMethod(Method source, Method target, boolean exact) {
        return isOverridingMethod(target.getDeclaringClass(), source, target, exact);
    }

    /**
     * Tests whether the target method overrides the source method from the inheriting class.
     * <p/>
     * Tests whether they have the same name, return type, and parameter list.
     *
     * @param  inheritingClass the class inheriting the target method overriding the source method
     * @param  source          the source method
     * @param  target          the target method
     * @param  exact           <tt>true</tt> if the override must be exact same types, <tt>false</tt> if the types
     *                         should be assignable
     * @return                 <tt>true</tt> if it override, <tt>false</tt> otherwise
     */
    public static boolean isOverridingMethod(Class<?> inheritingClass, Method source, Method target, boolean exact) {

        if (source.equals(target)) {
            return true;
        } else if (target.getDeclaringClass().isAssignableFrom(source.getDeclaringClass())) {
            return false;
        } else if (!source.getDeclaringClass().isAssignableFrom(inheritingClass)
                || !target.getDeclaringClass().isAssignableFrom(inheritingClass)) {
            return false;
        }

        if (!source.getName().equals(target.getName())) {
            return false;
        }

        if (exact) {
            if (!source.getReturnType().equals(target.getReturnType())) {
                return false;
            }
        } else {
            if (!source.getReturnType().isAssignableFrom(target.getReturnType())) {
                boolean b1 = source.isBridge();
                boolean b2 = target.isBridge();
                // must not be bridge methods
                if (!b1 && !b2) {
                    return false;
                }
            }
        }

        // must have same number of parameter types
        if (source.getParameterCount() != target.getParameterCount()) {
            return false;
        }

        Class<?>[] sourceTypes = source.getParameterTypes();
        Class<?>[] targetTypes = target.getParameterTypes();
        // test if parameter types is the same as well
        for (int i = 0; i < source.getParameterCount(); i++) {
            if (exact) {
                if (!(sourceTypes[i].equals(targetTypes[i]))) {
                    return false;
                }
            } else {
                if (!(sourceTypes[i].isAssignableFrom(targetTypes[i]))) {
                    boolean b1 = source.isBridge();
                    boolean b2 = target.isBridge();
                    // must not be bridge methods
                    if (!b1 && !b2) {
                        return false;
                    }
                }
            }
        }

        // the have same name, return type and parameter list, so its overriding
        return true;
    }

    /**
     * Returns a list of methods which are annotated with the given annotation
     *
     * @param  type           the type to reflect on
     * @param  annotationType the annotation type
     * @return                a list of the methods found
     */
    public static List<Method> findMethodsWithAnnotation(
            Class<?> type,
            Class<? extends Annotation> annotationType) {
        return findMethodsWithAnnotation(type, annotationType, false);
    }

    /**
     * Returns a list of methods which are annotated with the given annotation
     *
     * @param  type                 the type to reflect on
     * @param  annotationType       the annotation type
     * @param  checkMetaAnnotations check for meta annotations
     * @return                      a list of the methods found
     */
    public static List<Method> findMethodsWithAnnotation(
            Class<?> type,
            Class<? extends Annotation> annotationType,
            boolean checkMetaAnnotations) {
        List<Method> answer = new ArrayList<>();
        do {
            Method[] methods = type.getDeclaredMethods();
            for (Method method : methods) {
                if (hasAnnotation(method, annotationType, checkMetaAnnotations)) {
                    answer.add(method);
                }
            }
            type = type.getSuperclass();
        } while (type != null);
        return answer;
    }

    /**
     * Checks if a Class or Method are annotated with the given annotation
     *
     * @param  elem                 the Class or Method to reflect on
     * @param  annotationType       the annotation type
     * @param  checkMetaAnnotations check for meta annotations
     * @return                      true if annotations is present
     */
    public static boolean hasAnnotation(
            AnnotatedElement elem, Class<? extends Annotation> annotationType,
            boolean checkMetaAnnotations) {
        if (elem.isAnnotationPresent(annotationType)) {
            return true;
        }
        if (checkMetaAnnotations) {
            for (Annotation a : elem.getAnnotations()) {
                for (Annotation meta : a.annotationType().getAnnotations()) {
                    if (meta.annotationType().getName().equals(annotationType.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Turns the given object arrays into a meaningful string
     *
     * @param  objects an array of objects or null
     * @return         a meaningful string
     */
    public static String asString(Object[] objects) {
        if (objects == null) {
            return "null";
        } else {
            StringBuilder buffer = new StringBuilder("{");
            int counter = 0;
            for (Object object : objects) {
                if (counter++ > 0) {
                    buffer.append(", ");
                }
                String text = (object == null) ? "null" : object.toString();
                buffer.append(text);
            }
            buffer.append("}");
            return buffer.toString();
        }
    }

    /**
     * Returns true if a class is assignable from another class like the {@link Class#isAssignableFrom(Class)} method
     * but which also includes coercion between primitive types to deal with Java 5 primitive type wrapping
     */
    public static boolean isAssignableFrom(Class<?> a, Class<?> b) {
        a = convertPrimitiveTypeToWrapperType(a);
        b = convertPrimitiveTypeToWrapperType(b);
        return a.isAssignableFrom(b);
    }

    /**
     * Returns if the given {@code clazz} type is a Java primitive array type.
     *
     * @param  clazz the Java type to be checked
     * @return       {@code true} if the given type is a Java primitive array type
     */
    public static boolean isPrimitiveArrayType(Class<?> clazz) {
        if (clazz != null && clazz.isArray()) {
            return clazz.getComponentType().isPrimitive();
        }
        return false;
    }

    /**
     * Checks if the given class has a subclass (extends or implements)
     *
     * @param clazz    the class
     * @param subClass the subclass (class or interface)
     */
    public static boolean isSubclass(Class<?> clazz, Class<?> subClass) {
        if (clazz == subClass) {
            return true;
        }
        if (clazz == null || subClass == null) {
            return false;
        }
        for (Class<?> aClass = clazz; aClass != null; aClass = aClass.getSuperclass()) {
            if (aClass == subClass) {
                return true;
            }
            if (subClass.isInterface()) {
                Class<?>[] interfaces = aClass.getInterfaces();
                for (Class<?> anInterface : interfaces) {
                    if (isSubclass(anInterface, subClass)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Used by camel-bean
     */
    public static int arrayLength(Object[] pojo) {
        return pojo.length;
    }

    /**
     * Converts primitive types such as int to its wrapper type like {@link Integer}
     */
    public static Class<?> convertPrimitiveTypeToWrapperType(Class<?> type) {
        Class<?> rc = type;
        if (type.isPrimitive()) {
            if (type == int.class) {
                rc = Integer.class;
            } else if (type == long.class) {
                rc = Long.class;
            } else if (type == double.class) {
                rc = Double.class;
            } else if (type == float.class) {
                rc = Float.class;
            } else if (type == short.class) {
                rc = Short.class;
            } else if (type == byte.class) {
                rc = Byte.class;
            } else if (type == boolean.class) {
                rc = Boolean.class;
            } else if (type == char.class) {
                rc = Character.class;
            }
        }
        return rc;
    }

    /**
     * Converts wrapper type like {@link Integer} to its primitive type, i.e. int.
     */
    public static Class<?> convertWrapperTypeToPrimitiveType(Class<?> type) {
        Class<?> rc = type;
        if (type == Integer.class) {
            rc = int.class;
        } else if (type == Long.class) {
            rc = long.class;
        } else if (type == Double.class) {
            rc = double.class;
        } else if (type == Float.class) {
            rc = float.class;
        } else if (type == Short.class) {
            rc = short.class;
        } else if (type == Byte.class) {
            rc = byte.class;
        } else if (type == Boolean.class) {
            rc = boolean.class;
        } else if (type == Character.class) {
            rc = char.class;
        }
        return rc;
    }

    /**
     * Helper method to return the default character set name
     */
    public static String getDefaultCharacterSet() {
        return Charset.defaultCharset().name();
    }

    /**
     * Returns the Java Bean property name of the given method, if it is a setter
     */
    public static String getPropertyName(Method method) {
        String propertyName = method.getName();
        if (propertyName.startsWith("set") && method.getParameterCount() == 1) {
            propertyName = StringHelper.decapitalize(propertyName.substring(3));
        }
        return propertyName;
    }

    /**
     * Returns true if the given collection of annotations matches the given type
     */
    public static boolean hasAnnotation(Annotation[] annotations, Class<?> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the annotation from the given instance.
     *
     * @param  instance the instance
     * @param  type     the annotation
     * @return          the annotation, or <tt>null</tt> if the instance does not have the given annotation
     */
    public static <A extends java.lang.annotation.Annotation> A getAnnotation(Object instance, Class<A> type) {
        return instance.getClass().getAnnotation(type);
    }

    /**
     * Converts the given value to the required type or throw a meaningful exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Class<T> toType, Object value) {
        if (toType == boolean.class) {
            return (T) cast(Boolean.class, value);
        } else if (toType.isPrimitive()) {
            Class<?> newType = convertPrimitiveTypeToWrapperType(toType);
            if (newType != toType) {
                return (T) cast(newType, value);
            }
        }
        try {
            return toType.cast(value);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    "Failed to convert: "
                                               + value + " to type: " + toType.getName() + " due to: " + e,
                    e);
        }
    }

    /**
     * Does the given class have a default public no-arg constructor.
     */
    public static boolean hasDefaultPublicNoArgConstructor(Class<?> type) {
        // getConstructors() returns only public constructors
        for (Constructor<?> ctr : type.getConstructors()) {
            if (ctr.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does the given class have a default no-arg constructor (public or inherited).
     */
    public static boolean hasDefaultNoArgConstructor(Class<?> type) {
        if (hasDefaultPublicNoArgConstructor(type)) {
            return true;
        }
        for (Constructor<?> ctr : type.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(ctr.getModifiers()) && ctr.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the type of the given object or null if the value is null
     */
    public static Object type(Object bean) {
        return bean != null ? bean.getClass() : null;
    }

    /**
     * Evaluate the value as a predicate which attempts to convert the value to a boolean otherwise true is returned if
     * the value is not null
     */
    public static boolean evaluateValuePredicate(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) {
                return false;
            } else if ("true".equalsIgnoreCase(str)) {
                return true;
            } else if ("false".equalsIgnoreCase(str)) {
                return false;
            }
        } else if (value instanceof NodeList) {
            // is it an empty dom with empty attributes
            if (value instanceof Node && ((Node) value).hasAttributes()) {
                return true;
            }
            NodeList list = (NodeList) value;
            return list.getLength() > 0;
        } else if (value instanceof Collection) {
            // is it an empty collection
            return !((Collection<?>) value).isEmpty();
        }
        return value != null;
    }

    /**
     * Creates an Iterable to walk the exception from the bottom up (the last caused by going upwards to the root
     * exception).
     *
     * @see              java.lang.Iterable
     * @param  exception the exception
     * @return           the Iterable
     */
    public static Iterable<Throwable> createExceptionIterable(Throwable exception) {
        List<Throwable> throwables = new ArrayList<>();

        Throwable current = exception;
        // spool to the bottom of the caused by tree
        while (current != null) {
            throwables.add(current);
            current = current.getCause();
        }
        Collections.reverse(throwables);

        return throwables;
    }

    /**
     * Creates an Iterator to walk the exception from the bottom up (the last caused by going upwards to the root
     * exception).
     *
     * @see              Iterator
     * @param  exception the exception
     * @return           the Iterator
     */
    public static Iterator<Throwable> createExceptionIterator(Throwable exception) {
        return createExceptionIterable(exception).iterator();
    }

    /**
     * Retrieves the given exception type from the exception.
     * <p/>
     * Is used to get the caused exception that typically have been wrapped in some sort of Camel wrapper exception
     * <p/>
     * The strategy is to look in the exception hierarchy to find the first given cause that matches the type. Will
     * start from the bottom (the real cause) and walk upwards.
     *
     * @param  type      the exception type wanted to retrieve
     * @param  exception the caused exception
     * @return           the exception found (or <tt>null</tt> if not found in the exception hierarchy)
     */
    public static <T> T getException(Class<T> type, Throwable exception) {
        if (exception == null) {
            return null;
        }

        //check the suppressed exception first
        for (Throwable throwable : exception.getSuppressed()) {
            if (type.isInstance(throwable)) {
                return type.cast(throwable);
            }
        }

        // walk the hierarchy and look for it
        for (final Throwable throwable : createExceptionIterable(exception)) {
            if (type.isInstance(throwable)) {
                return type.cast(throwable);
            }
        }

        // not found
        return null;
    }

    public static String getIdentityHashCode(Object object) {
        return "0x" + Integer.toHexString(System.identityHashCode(object));
    }

    /**
     * Lookup the constant field on the given class with the given name
     *
     * @param  clazz the class
     * @param  name  the name of the field to lookup
     * @return       the value of the constant field, or <tt>null</tt> if not found
     */
    public static String lookupConstantFieldValue(Class<?> clazz, String name) {
        if (clazz == null) {
            return null;
        }

        // remove leading dots
        if (name.startsWith(".")) {
            name = name.substring(1);
        }

        for (Field field : clazz.getFields()) {
            if (field.getName().equals(name)) {
                try {
                    Object v = field.get(null);
                    return v.toString();
                } catch (IllegalAccessException e) {
                    // ignore
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Is the given value a numeric NaN type
     *
     * @param  value the value
     * @return       <tt>true</tt> if its a {@link Float#NaN} or {@link Double#NaN}.
     */
    public static boolean isNaN(Object value) {
        return value instanceof Float && ((Float) value).isNaN()
                || value instanceof Double && ((Double) value).isNaN();
    }

    /**
     * Turns the input array to a list of objects.
     *
     * @param  objects an array of objects or null
     * @return         an object list
     */
    public static List<Object> asList(Object[] objects) {
        return objects != null ? Arrays.asList(objects) : Collections.emptyList();
    }

    /**
     * Adds the value to the list at the given index
     */
    public static void addListByIndex(List<Object> list, int idx, Object value) {
        if (idx < list.size()) {
            list.set(idx, value);
        } else if (idx == list.size()) {
            list.add(value);
        } else {
            // If the list implementation is based on an array, we
            // can increase tha capacity to the required value to
            // avoid potential re-allocation when invoking List::add.
            //
            // Note that ArrayList is the default List impl that
            // is automatically created if the property is null.
            if (list instanceof ArrayList) {
                ((ArrayList<?>) list).ensureCapacity(idx + 1);
            }
            while (list.size() < idx) {
                list.add(null);
            }
            list.add(idx, value);
        }
    }

}
