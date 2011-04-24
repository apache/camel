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

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.file.GenericFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A number of useful helper methods for working with Objects
 *
 * @version 
 */
public final class ObjectHelper {
    private static final transient Logger LOG = LoggerFactory.getLogger(ObjectHelper.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectHelper() {
    }

    /**
     * A helper method for comparing objects for equality in which it uses type coerce to coerce
     * types between the left and right values. This allows you to equal test eg String and Integer as
     * Camel will be able to coerce the types.
     */
    public static boolean typeCoerceEquals(TypeConverter converter, Object leftValue, Object rightValue) {
        // sanity check
        if (leftValue == null && rightValue == null) {
            // they are equal
            return true;
        } else if (leftValue == null || rightValue == null) {
            // only one of them is null so they are not equal
            return false;
        }

        // try without type coerce
        boolean answer = equal(leftValue, rightValue);
        if (answer) {
            return true;
        }

        // are they same type, if so return false as the equals returned false
        if (leftValue.getClass().isInstance(rightValue)) {
            return false;
        }

        // convert left to right
        Object value = converter.convertTo(rightValue.getClass(), leftValue);
        answer = equal(value, rightValue);
        if (answer) {
            return true;
        }

        // convert right to left
        value = converter.convertTo(leftValue.getClass(), rightValue);
        answer = equal(leftValue, value);
        return answer;
    }

    /**
     * A helper method for comparing objects for equality in which it uses type coerce to coerce
     * types between the left and right values. This allows you to equal test eg String and Integer as
     * Camel will be able to coerce the types
     */
    public static boolean typeCoerceNotEquals(TypeConverter converter, Object leftValue, Object rightValue) {
        return !typeCoerceEquals(converter, leftValue, rightValue);
    }

    /**
     * A helper method for comparing objects ordering in which it uses type coerce to coerce
     * types between the left and right values. This allows you to equal test eg String and Integer as
     * Camel will be able to coerce the types
     */
    @SuppressWarnings("unchecked")
    public static int typeCoerceCompare(TypeConverter converter, Object leftValue, Object rightValue) {

        // if both values is numeric then compare using numeric
        Long leftNum = converter.convertTo(Long.class, leftValue);
        Long rightNum = converter.convertTo(Long.class, rightValue);
        if (leftNum != null && rightNum != null) {
            return leftNum.compareTo(rightNum);
        }

        // prefer to NOT coerce to String so use the type which is not String
        // for example if we are comparing String vs Integer then prefer to coerce to Integer
        // as all types can be converted to String which does not work well for comparison
        // as eg "10" < 6 would return true, where as 10 < 6 will return false.
        // if they are both String then it doesn't matter
        if (rightValue instanceof String && (!(leftValue instanceof String))) {
            // if right is String and left is not then flip order (remember to * -1 the result then)
            return typeCoerceCompare(converter, rightValue, leftValue) * -1;
        }

        // prefer to coerce to the right hand side at first
        if (rightValue instanceof Comparable) {
            Object value = converter.convertTo(rightValue.getClass(), leftValue);
            if (value != null) {
                return ((Comparable) rightValue).compareTo(value) * -1;
            }
        }

        // then fallback to the left hand side
        if (leftValue instanceof Comparable) {
            Object value = converter.convertTo(leftValue.getClass(), rightValue);
            if (value != null) {
                return ((Comparable) leftValue).compareTo(value);
            }
        }

        // use regular compare
        return compare(leftValue, rightValue);
    }

    /**
     * A helper method for comparing objects for equality while handling nulls
     */
    public static boolean equal(Object a, Object b) {
        if (a == b) {
            return true;
        }

        if (a instanceof byte[] && b instanceof byte[]) {
            return equalByteArray((byte[])a, (byte[])b);
        }

        return a != null && b != null && a.equals(b);
    }

    /**
     * A helper method for comparing byte arrays for equality while handling
     * nulls
     */
    public static boolean equalByteArray(byte[] a, byte[] b) {
        if (a == b) {
            return true;
        }

        // loop and compare each byte
        if (a != null && b != null && a.length == b.length) {
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }
            // all bytes are equal
            return true;
        }

        return false;
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

    /**
     * A helper method for performing an ordered comparison on the objects
     * handling nulls and objects which do not handle sorting gracefully
     */
    public static int compare(Object a, Object b) {
        return compare(a, b, false);
    }

    /**
     * A helper method for performing an ordered comparison on the objects
     * handling nulls and objects which do not handle sorting gracefully
     *
     * @param a  the first object
     * @param b  the second object
     * @param ignoreCase  ignore case for string comparison
     */
    @SuppressWarnings("unchecked")
    public static int compare(Object a, Object b, boolean ignoreCase) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Ordered && b instanceof Ordered) {
            return ((Ordered) a).getOrder() - ((Ordered) b).getOrder();
        }
        if (ignoreCase && a instanceof String && b instanceof String) {
            return ((String) a).compareToIgnoreCase((String) b);
        }
        if (a instanceof Comparable) {
            Comparable comparable = (Comparable)a;
            return comparable.compareTo(b);
        }
        int answer = a.getClass().getName().compareTo(b.getClass().getName());
        if (answer == 0) {
            answer = a.hashCode() - b.hashCode();
        }
        return answer;
    }

    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean)value;
        }
        if (value instanceof String) {
            return "true".equalsIgnoreCase(value.toString()) ? Boolean.TRUE : Boolean.FALSE;
        }
        if (value instanceof Integer) {
            return (Integer)value > 0 ? Boolean.TRUE : Boolean.FALSE;
        }
        return null;
    }

    /**
     * Asserts whether the value is <b>not</b> <tt>null</tt>
     *
     * @param value  the value to test
     * @param name   the key that resolved the value
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static void notNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be specified");
        }
    }

    /**
     * Asserts whether the value is <b>not</b> <tt>null</tt>
     *
     * @param value  the value to test
     * @param on     additional description to indicate where this problem occurred (appended as toString())
     * @param name   the key that resolved the value
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static void notNull(Object value, String name, Object on) {
        if (on == null) {
            notNull(value, name);
        } else if (value == null) {
            throw new IllegalArgumentException(name + " must be specified on: " + on);
        }
    }

    /**
     * Asserts whether the string is <b>not</b> empty.
     *
     * @param value  the string to test
     * @param name   the key that resolved the value
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static void notEmpty(String value, String name) {
        if (isEmpty(value)) {
            throw new IllegalArgumentException(name + " must be specified and not empty");
        }
    }

    /**
     * Asserts whether the string is <b>not</b> empty.
     *
     * @param value  the string to test
     * @param on     additional description to indicate where this problem occurred (appended as toString())
     * @param name   the key that resolved the value
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static void notEmpty(String value, String name, Object on) {
        if (on == null) {
            notNull(value, name);
        } else if (isEmpty(value)) {
            throw new IllegalArgumentException(name + " must be specified and not empty on: " + on);
        }
    }

    /**
     * Tests whether the value is <tt>null</tt> or an empty string.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @return true if empty
     */
    public static boolean isEmpty(Object value) {
        return !isNotEmpty(value);
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt> or an empty string.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @return true if <b>not</b> empty
     */
    public static boolean isNotEmpty(Object value) {
        if (value == null) {
            return false;
        } else if (value instanceof String) {
            String text = (String) value;
            return text.trim().length() > 0;
        } else {
            return true;
        }
    }

    public static String[] splitOnCharacter(String value, String needle, int count) {
        String rc[] = new String[count];
        rc[0] = value;
        for (int i = 1; i < count; i++) {
            String v = rc[i - 1];
            int p = v.indexOf(needle);
            if (p < 0) {
                return rc;
            }
            rc[i - 1] = v.substring(0, p);
            rc[i] = v.substring(p + 1);
        }
        return rc;
    }

    /**
     * Removes any starting characters on the given text which match the given
     * character
     *
     * @param text the string
     * @param ch the initial characters to remove
     * @return either the original string or the new substring
     */
    public static String removeStartingCharacters(String text, char ch) {
        int idx = 0;
        while (text.charAt(idx) == ch) {
            idx++;
        }
        if (idx > 0) {
            return text.substring(idx);
        }
        return text;
    }

    public static String capitalize(String text) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        if (length == 0) {
            return text;
        }
        String answer = text.substring(0, 1).toUpperCase();
        if (length > 1) {
            answer += text.substring(1, length);
        }
        return answer;
    }

    public static String after(String text, String after) {
        if (!text.contains(after)) {
            return null;
        }
        return text.substring(text.indexOf(after) + after.length());
    }

    public static String before(String text, String before) {
        if (!text.contains(before)) {
            return null;
        }
        return text.substring(0, text.indexOf(before));
    }

    public static String between(String text, String after, String before) {
        text = after(text, after);
        if (text == null) {
            return null;
        }
        return before(text, before);
    }

    /**
     * Returns true if the collection contains the specified value
     */
    public static boolean contains(Object collectionOrArray, Object value) {
        if (collectionOrArray instanceof Collection) {
            Collection collection = (Collection)collectionOrArray;
            return collection.contains(value);
        } else if (collectionOrArray instanceof String && value instanceof String) {
            String str = (String)collectionOrArray;
            String subStr = (String)value;
            return str.contains(subStr);
        } else {
            Iterator iter = createIterator(collectionOrArray);
            while (iter.hasNext()) {
                if (equal(value, iter.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates an iterator over the value if the value is a collection, an
     * Object[] or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     * <p/>
     * Will default use comma for String separating String values.
     *
     * @param value  the value
     * @return the iterator
     */
    public static Iterator<Object> createIterator(Object value) {
        return createIterator(value, ",");
    }

    /**
     * Creates an iterator over the value if the value is a collection, an
     * Object[] or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     *
     * @param value  the value
     * @param  delimiter  delimiter for separating String values
     * @return the iterator
     */
    @SuppressWarnings("unchecked")
    public static Iterator<Object> createIterator(Object value, String delimiter) {
        // if its a message than we want to iterate its body
        if (value instanceof Message) {
            value = ((Message) value).getBody();
        }

        if (value == null) {
            return Collections.emptyList().iterator();
        } else if (value instanceof Iterator) {
            return (Iterator)value;
        } else if (value instanceof Iterable) {
            return ((Iterable)value).iterator();
        } else if (value.getClass().isArray()) {
            // TODO we should handle primitive array types?
            List<Object> list = Arrays.asList((Object[])value);
            return list.iterator();
        } else if (value instanceof NodeList) {
            // lets iterate through DOM results after performing XPaths
            final NodeList nodeList = (NodeList) value;
            return CastUtils.cast(new Iterator<Node>() {
                int idx = -1;

                public boolean hasNext() {
                    return (idx + 1) < nodeList.getLength();
                }

                public Node next() {
                    idx++;
                    return nodeList.item(idx);
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            });
        } else if (value instanceof String) {
            final String s = (String) value;

            // this code is optimized to only use a Scanner if needed, eg there is a delimiter

            if (s.contains(delimiter)) {
                // use a scanner if it contains the delimiter
                Scanner scanner = new Scanner((String)value);
                scanner.useDelimiter(delimiter);
                return CastUtils.cast(scanner);
            } else {
                // use a plain iterator that returns the value as is as there are only a single value
                return CastUtils.cast(new Iterator<String>() {
                    int idx = -1;

                    public boolean hasNext() {
                        // empty string should not be regarded as having next
                        return idx + 1 == 0 && ObjectHelper.isNotEmpty(s);
                    }

                    public String next() {
                        idx++;
                        return s;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        } else {
            return Collections.singletonList(value).iterator();
        }
    }

    /**
     * Returns the predicate matching boolean on a {@link List} result set where
     * if the first element is a boolean its value is used otherwise this method
     * returns true if the collection is not empty
     *
     * @return <tt>true</tt> if the first element is a boolean and its value
     *         is true or if the list is non empty
     */
    public static boolean matches(List list) {
        if (!list.isEmpty()) {
            Object value = list.get(0);
            if (value instanceof Boolean) {
                return (Boolean)value;
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
     * @param name         the name of the system property required
     * @param defaultValue the default value to use if the property is not
     *                     available or a security exception prevents access
     * @return the system property value or the default value if the property is
     *         not available or security does not allow its access
     */
    public static String getSystemProperty(String name, String defaultValue) {
        try {
            return System.getProperty(name, defaultValue);
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Caught security exception accessing system property: " + name + ". Will use default value: " + defaultValue, e);
            }
            return defaultValue;
        }
    }

    /**
     * A helper method to access a boolean system property, catching any
     * security exceptions
     *
     * @param name         the name of the system property required
     * @param defaultValue the default value to use if the property is not
     *                     available or a security exception prevents access
     * @return the boolean representation of the system property value or the
     *         default value if the property is not available or security does
     *         not allow its access
     */
    public static boolean getSystemProperty(String name, Boolean defaultValue) {
        String result = getSystemProperty(name, defaultValue.toString());
        return Boolean.parseBoolean(result);
    }
   
    /**
     * A helper method to access a camel context properties with a prefix
     *
     * @param prefix       the prefix
     * @param camelContext the camel context
     * @return the properties which holds the camel context properties with the prefix,
     *         and the key omit the prefix part
     */
    public static Properties getCamelPropertiesWithPrefix(String prefix, CamelContext camelContext) {
        Properties answer = new Properties();
        Map<String, String> camelProperties = camelContext.getProperties();
        if (camelProperties != null) {
            for (Map.Entry<String, String> entry : camelProperties.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(prefix)) {
                    answer.put(key.substring(prefix.length()), entry.getValue());
                }
            }
        }
        return answer;
    }

    /**
     * Returns the type name of the given type or null if the type variable is
     * null
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
     * Attempts to load the given class name using the thread context class
     * loader or the class loader used to load this class
     *
     * @param name the name of the class to load
     * @return the class or <tt>null</tt> if it could not be loaded
     */
    public static Class<?> loadClass(String name) {
        return loadClass(name, ObjectHelper.class.getClassLoader());
    }
    
    /**
     * Attempts to load the given class name using the thread context class
     * loader or the given class loader
     *
     * @param name the name of the class to load
     * @param loader the class loader to use after the thread context class loader
     * @return the class or <tt>null</tt> if it could not be loaded
     */
    public static Class<?> loadClass(String name, ClassLoader loader) {
        return loadClass(name, loader, true);
    }

    /**
     * Attempts to load the given class name using the thread context class
     * loader or the given class loader
     *
     * @param name the name of the class to load
     * @param loader the class loader to use after the thread context class loader
     * @param needToWarn when <tt>true</tt> logs a warning when a class with the given name could not be loaded
     * @return the class or <tt>null</tt> if it could not be loaded
     */
    public static Class<?> loadClass(String name, ClassLoader loader, boolean needToWarn) {
        // must clean the name so its pure java name, eg removing \n or whatever people can do in the Spring XML
        name = normalizeClassName(name);

        // Try simple type first
        Class<?> clazz = loadSimpleType(name);
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

        if (clazz == null) {
            if (needToWarn) {
                LOG.warn("Cannot find class: " + name);
            }
        }

        return clazz;
    }


    /**
     * Load a simple type
     *
     * @param name the name of the class to load
     * @return the class or <tt>null</tt> if it could not be loaded
     */
    public static Class<?> loadSimpleType(String name) {
        // special for byte[] as its common to use
        if ("java.lang.byte[]".equals(name) || "byte[]".equals(name)) {
            return byte[].class;
        // and these is common as well
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return String.class;
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
        }
        return null;
    }
    /**
     * Loads the given class with the provided classloader (may be null).
     * Will ignore any class not found and return null.
     *
     * @param name    the name of the class to load
     * @param loader  a provided loader (may be null)
     * @return the class, or null if it could not be loaded
     */
    private static Class<?> doLoadClass(String name, ClassLoader loader) {
        ObjectHelper.notEmpty(name, "name");
        if (loader == null) {
            return null;
        }
        try {
            LOG.trace("Loading class: {} using classloader: {}", name, loader);
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Cannot load class: " + name + " using classloader: " + loader, e);
            }

        }
        return null;
    }

    /**
     * Attempts to load the given resource as a stream using the thread context
     * class loader or the class loader used to load this class
     *
     * @param name the name of the resource to load
     * @return the stream or null if it could not be loaded
     */
    public static InputStream loadResourceAsStream(String name) {
        InputStream in = null;

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            in = contextClassLoader.getResourceAsStream(name);
        }
        if (in == null) {
            in = ObjectHelper.class.getClassLoader().getResourceAsStream(name);
        }

        return in;
    }

    /**
     * Attempts to load the given resource as a stream using the thread context
     * class loader or the class loader used to load this class
     *
     * @param name the name of the resource to load
     * @return the stream or null if it could not be loaded
     */
    public static URL loadResourceAsURL(String name) {
        URL url = null;

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            url = contextClassLoader.getResource(name);
        }
        if (url == null) {
            url = ObjectHelper.class.getClassLoader().getResource(name);
        }

        return url;
    }

    /**
     * A helper method to invoke a method via reflection and wrap any exceptions
     * as {@link RuntimeCamelException} instances
     *
     * @param method the method to invoke
     * @param instance the object instance (or null for static methods)
     * @param parameters the parameters to the method
     * @return the result of the method invocation
     */
    public static Object invokeMethod(Method method, Object instance, Object... parameters) {
        try {
            return method.invoke(instance, parameters);
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeCamelException(e.getCause());
        }
    }

    /**
     * Tests whether the target method overrides the source method.
     * <p/>
     * Tests whether they have the same name, return type, and parameter list.
     *
     * @param source  the source method
     * @param target  the target method
     * @return <tt>true</tt> if it override, <tt>false</tt> otherwise
     */
    public static boolean isOverridingMethod(Method source, Method target) {
        if (source.getName().equals(target.getName())
                && source.getReturnType().equals(target.getReturnType()) 
                && source.getParameterTypes().length == target.getParameterTypes().length) {

            // test if parameter types is the same as well
            for (int i = 0; i < source.getParameterTypes().length; i++) {
                if (!(source.getParameterTypes()[i].equals(target.getParameterTypes()[i]))) {
                    return false;
                }
            }

            // the have same name, return type and parameter list, so its overriding
            return true;
        }

        return false;
    }

    /**
     * Returns a list of methods which are annotated with the given annotation
     *
     * @param type the type to reflect on
     * @param annotationType the annotation type
     * @return a list of the methods found
     */
    public static List<Method> findMethodsWithAnnotation(Class<?> type,
                                                         Class<? extends Annotation> annotationType) {
        return findMethodsWithAnnotation(type, annotationType, false);
    }

    /**
     * Returns a list of methods which are annotated with the given annotation
     *
     * @param type the type to reflect on
     * @param annotationType the annotation type
     * @param checkMetaAnnotations check for meta annotations
     * @return a list of the methods found
     */
    public static List<Method> findMethodsWithAnnotation(Class<?> type,
                                                         Class<? extends Annotation> annotationType,
                                                         boolean checkMetaAnnotations) {
        List<Method> answer = new ArrayList<Method>();
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
     * @param elem the Class or Method to reflect on
     * @param annotationType the annotation type
     * @param checkMetaAnnotations check for meta annotations
     * @return true if annotations is present
     */
    public static boolean hasAnnotation(AnnotatedElement elem, Class<? extends Annotation> annotationType,
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
     * @param objects an array of objects or null
     * @return a meaningful string
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
     * Returns true if a class is assignable from another class like the
     * {@link Class#isAssignableFrom(Class)} method but which also includes
     * coercion between primitive types to deal with Java 5 primitive type
     * wrapping
     */
    public static boolean isAssignableFrom(Class<?> a, Class<?> b) {
        a = convertPrimitiveTypeToWrapperType(a);
        b = convertPrimitiveTypeToWrapperType(b);
        return a.isAssignableFrom(b);
    }

    /**
     * Converts primitive types such as int to its wrapper type like
     * {@link Integer}
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
            }
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
     * Returns the Java Bean property name of the given method, if it is a
     * setter
     */
    public static String getPropertyName(Method method) {
        String propertyName = method.getName();
        if (propertyName.startsWith("set") && method.getParameterTypes().length == 1) {
            propertyName = propertyName.substring(3, 4).toLowerCase() + propertyName.substring(4);
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
     * Closes the given resource if it is available, logging any closing
     * exceptions to the given log
     *
     * @param closeable the object to close
     * @param name the name of the resource
     * @param log the log to use when reporting closure warnings
     * @deprecated will be removed in Camel 3.0. Instead use {@link org.apache.camel.util.IOHelper#close(java.io.Closeable, String, org.slf4j.Logger)} instead
     */
    @Deprecated
    public static void close(Closeable closeable, String name, Logger log) {
        IOHelper.close(closeable, name, log);
    }


    /**
     * Converts the given value to the required type or throw a meaningful exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Class<T> toType, Object value) {
        if (toType == boolean.class) {
            return (T)cast(Boolean.class, value);
        } else if (toType.isPrimitive()) {
            Class newType = convertPrimitiveTypeToWrapperType(toType);
            if (newType != toType) {
                return (T)cast(newType, value);
            }
        }
        try {
            return toType.cast(value);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Failed to convert: " 
                + value + " to type: " + toType.getName() + " due to: " + e, e);
        }
    }

    /**
     * A helper method to create a new instance of a type using the default
     * constructor arguments.
     */
    public static <T> T newInstance(Class<T> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeCamelException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * A helper method to create a new instance of a type using the default
     * constructor arguments.
     */
    public static <T> T newInstance(Class<?> actualType, Class<T> expectedType) {
        try {
            Object value = actualType.newInstance();
            return cast(expectedType, value);
        } catch (InstantiationException e) {
            throw new RuntimeCamelException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Returns true if the given name is a valid java identifier
     */
    public static boolean isJavaIdentifier(String name) {
        if (name == null) {
            return false;
        }
        int size = name.length();
        if (size < 1) {
            return false;
        }
        if (Character.isJavaIdentifierStart(name.charAt(0))) {
            for (int i = 1; i < size; i++) {
                if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                    return false;
                }
            }
            return true;
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
     * Evaluate the value as a predicate which attempts to convert the value to
     * a boolean otherwise true is returned if the value is not null
     */
    public static boolean evaluateValuePredicate(Object value) {
        if (value instanceof Boolean) {
            return (Boolean)value;
        } else if (value instanceof String) {
            if ("true".equalsIgnoreCase((String)value)) {
                return true;
            } else if ("false".equalsIgnoreCase((String)value)) {
                return false;
            }
        } else if (value instanceof NodeList) {
            // is it an empty dom
            NodeList list = (NodeList) value;
            return list.getLength() > 0;
        } else if (value instanceof Collection) {
            // is it an empty collection
            Collection col = (Collection) value;
            return col.size() > 0;
        }
        return value != null;
    }

    /**
     * Wraps the caused exception in a {@link RuntimeCamelException} if its not
     * already such an exception.
     *
     * @param e the caused exception
     * @return the wrapper exception
     */
    public static RuntimeCamelException wrapRuntimeCamelException(Throwable e) {
        if (e instanceof RuntimeCamelException) {
            // don't double wrap
            return (RuntimeCamelException)e;
        } else {
            return new RuntimeCamelException(e);
        }
    }

    /**
     * Wraps the caused exception in a {@link CamelExecutionException} if its not
     * already such an exception.
     *
     * @param e the caused exception
     * @return the wrapper exception
     */
    public static CamelExecutionException wrapCamelExecutionException(Exchange exchange, Throwable e) {
        if (e instanceof CamelExecutionException) {
            // don't double wrap
            return (CamelExecutionException)e;
        } else {
            return new CamelExecutionException("Exception occurred during execution", exchange, e);
        }
    }

    /**
     * Cleans the string to pure java identifier so we can use it for loading class names.
     * <p/>
     * Especially from Spring DSL people can have \n \t or other characters that otherwise
     * would result in ClassNotFoundException
     *
     * @param name the class name
     * @return normalized classname that can be load by a class loader.
     */
    public static String normalizeClassName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (char ch : name.toCharArray()) {
            if (ch == '.' || ch == '[' || ch == ']' || ch == '-' || Character.isJavaIdentifierPart(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Creates an iterator to walk the exception from the bottom up
     * (the last caused by going upwards to the root exception).
     *
     * @param exception  the exception
     * @return the iterator
     */
    public static Iterator<Throwable> createExceptionIterator(Throwable exception) {
        return new ExceptionIterator(exception);
    }

    /**
     * Retrieves the given exception type from the exception.
     * <p/>
     * Is used to get the caused exception that typically have been wrapped in some sort
     * of Camel wrapper exception
     * <p/>
     * The strategy is to look in the exception hierarchy to find the first given cause that matches the type.
     * Will start from the bottom (the real cause) and walk upwards.
     *
     * @param type the exception type wanted to retrieve
     * @param exception the caused exception
     * @return the exception found (or <tt>null</tt> if not found in the exception hierarchy)
     */
    public static <T> T getException(Class<T> type, Throwable exception) {
        if (exception == null) {
            return null;
        }

        // walk the hierarchy and look for it
        Iterator<Throwable> it = createExceptionIterator(exception);
        while (it.hasNext()) {
            Throwable e = it.next();
            if (type.isInstance(e)) {
                return type.cast(e);
            }
        }

        // not found
        return null;
    }

    /**
     * Creates a {@link Scanner} for scanning the given value.
     *
     * @param exchange  the current exchange
     * @param value     the value, typically the message IN body
     * @return the scanner, is newer <tt>null</tt>
     */
    public static Scanner getScanner(Exchange exchange, Object value) {
        if (value instanceof GenericFile) {
            // generic file is just a wrapper for the real file so call again with the real file
            GenericFile<?> gf = (GenericFile<?>) value;
            return getScanner(exchange, gf.getFile());
        }

        String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);

        Scanner scanner = null;
        if (value instanceof Readable) {
            scanner = new Scanner((Readable)value);
        } else if (value instanceof InputStream) {
            scanner = charset == null ? new Scanner((InputStream)value) : new Scanner((InputStream)value, charset);
        } else if (value instanceof File) {
            try {
                scanner = charset == null ? new Scanner((File)value) : new Scanner((File)value, charset);
            } catch (FileNotFoundException e) {
                throw new RuntimeCamelException(e);
            }
        } else if (value instanceof String) {
            scanner = new Scanner((String)value);
        } else if (value instanceof ReadableByteChannel) {
            scanner = charset == null ? new Scanner((ReadableByteChannel)value) : new Scanner((ReadableByteChannel)value, charset);
        }

        if (scanner == null) {
            // value is not a suitable type, try to convert value to a string
            String text = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
            if (text != null) {
                scanner = new Scanner(text);
            }
        }

        if (scanner == null) {
            scanner = new Scanner("");
        }

        return scanner;
    }

    public static String getIdentityHashCode(Object object) {
        return "0x" + Integer.toHexString(System.identityHashCode(object));
    }

    /**
     * Lookup the constant field on the given class with the given name
     *
     * @param clazz  the class
     * @param name   the name of the field to lookup
     * @return the value of the constant field, or <tt>null</tt> if not found
     */
    public static String lookupConstantFieldValue(Class clazz, String name) {
        if (clazz == null) {
            return null;
        }

        for (Field field : clazz.getFields()) {
            if (field.getName().equals(name)) {
                try {
                    return (String) field.get(null);
                } catch (IllegalAccessException e) {
                    // ignore
                    return null;
                }
            }
        }

        return null;
    }

    private static final class ExceptionIterator implements Iterator<Throwable> {
        private List<Throwable> tree = new ArrayList<Throwable>();
        private Iterator<Throwable> it;

        public ExceptionIterator(Throwable exception) {
            Throwable current = exception;
            // spool to the bottom of the caused by tree
            while (current != null) {
                tree.add(current);
                current = current.getCause();
            }

            // reverse tree so we go from bottom to top
            Collections.reverse(tree);
            it = tree.iterator();
        }

        public boolean hasNext() {
            return it.hasNext();
        }

        public Throwable next() {
            return it.next();
        }

        public void remove() {
            it.remove();
        }
    }
}
