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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Ordered;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.Scanner;
import org.apache.camel.util.StringHelper;

/**
 * A number of useful helper methods for working with Objects
 */
public final class ObjectHelper {
  
    static {
        DEFAULT_PATTERN = Pattern.compile(",(?!(?:[^\\(,]|[^\\)],[^\\)])+\\))");
    }

    private static final Pattern DEFAULT_PATTERN;
    private static final String DEFAULT_DELIMITER = ",";

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectHelper() {
    }


    /**
     * A helper method for comparing objects for equality in which it uses type coercion to coerce
     * types between the left and right values. This allows you test for equality for example with
     * a String and Integer type as Camel will be able to coerce the types.
     */
    public static boolean typeCoerceEquals(TypeConverter converter, Object leftValue, Object rightValue) {
        return typeCoerceEquals(converter, leftValue, rightValue, false);
    }

    /**
     * A helper method for comparing objects for equality in which it uses type coercion to coerce
     * types between the left and right values. This allows you test for equality for example with
     * a String and Integer type as Camel will be able to coerce the types.
     */
    public static boolean typeCoerceEquals(TypeConverter converter, Object leftValue, Object rightValue, boolean ignoreCase) {
        // sanity check
        if (leftValue == null && rightValue == null) {
            // they are equal
            return true;
        } else if (leftValue == null || rightValue == null) {
            // only one of them is null so they are not equal
            return false;
        }

        // try without type coerce
        boolean answer = org.apache.camel.util.ObjectHelper.equal(leftValue, rightValue, ignoreCase);
        if (answer) {
            return true;
        }

        // are they same type, if so return false as the equals returned false
        if (leftValue.getClass().isInstance(rightValue)) {
            return false;
        }

        // convert left to right
        Object value = converter.tryConvertTo(rightValue.getClass(), leftValue);
        answer = org.apache.camel.util.ObjectHelper.equal(value, rightValue, ignoreCase);
        if (answer) {
            return true;
        }

        // convert right to left
        value = converter.tryConvertTo(leftValue.getClass(), rightValue);
        answer = org.apache.camel.util.ObjectHelper.equal(leftValue, value, ignoreCase);
        return answer;
    }

    /**
     * A helper method for comparing objects for inequality in which it uses type coercion to coerce
     * types between the left and right values.  This allows you test for inequality for example with
     * a String and Integer type as Camel will be able to coerce the types.
     */
    public static boolean typeCoerceNotEquals(TypeConverter converter, Object leftValue, Object rightValue) {
        return !typeCoerceEquals(converter, leftValue, rightValue);
    }

    /**
     * A helper method for comparing objects ordering in which it uses type coercion to coerce
     * types between the left and right values.  This allows you test for ordering for example with
     * a String and Integer type as Camel will be able to coerce the types.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int typeCoerceCompare(TypeConverter converter, Object leftValue, Object rightValue) {

        // if both values is numeric then compare using numeric
        Long leftNum = converter.tryConvertTo(Long.class, leftValue);
        Long rightNum = converter.tryConvertTo(Long.class, rightValue);
        if (leftNum != null && rightNum != null) {
            return leftNum.compareTo(rightNum);
        }

        // also try with floating point numbers
        Double leftDouble = converter.tryConvertTo(Double.class, leftValue);
        Double rightDouble = converter.tryConvertTo(Double.class, rightValue);
        if (leftDouble != null && rightDouble != null) {
            return leftDouble.compareTo(rightDouble);
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
            Object value = converter.tryConvertTo(rightValue.getClass(), leftValue);
            if (value != null) {
                return ((Comparable) rightValue).compareTo(value) * -1;
            }
        }

        // then fallback to the left hand side
        if (leftValue instanceof Comparable) {
            Object value = converter.tryConvertTo(leftValue.getClass(), rightValue);
            if (value != null) {
                return ((Comparable) leftValue).compareTo(value);
            }
        }

        // use regular compare
        return compare(leftValue, rightValue);
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
            if (parameters != null) {
                return method.invoke(instance, parameters);
            } else {
                return method.invoke(instance);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        } catch (InvocationTargetException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e.getCause());
        }
    }

    /**
     * A helper method to invoke a method via reflection in a safe way by allowing to invoke
     * methods that are not accessible by default and wrap any exceptions
     * as {@link RuntimeCamelException} instances
     *
     * @param method the method to invoke
     * @param instance the object instance (or null for static methods)
     * @param parameters the parameters to the method
     * @return the result of the method invocation
     */
    public static Object invokeMethodSafe(Method method, Object instance, Object... parameters) throws InvocationTargetException, IllegalAccessException {
        Object answer;
        if (!method.isAccessible()) {
            method.setAccessible(true);
        }
        if (parameters != null) {
            answer = method.invoke(instance, parameters);
        } else {
            answer = method.invoke(instance);
        }
        return answer;
    }

    /**
     * A helper method to create a new instance of a type using the default
     * constructor arguments.
     */
    public static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * A helper method to create a new instance of a type using the default
     * constructor arguments.
     */
    public static <T> T newInstance(Class<?> actualType, Class<T> expectedType) {
        try {
            Object value = actualType.getDeclaredConstructor().newInstance();
            return org.apache.camel.util.ObjectHelper.cast(expectedType, value);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
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
    @SuppressWarnings({"unchecked", "rawtypes"})
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

    /**
     * Calling the Callable with the setting of TCCL with the camel context application classloader.
     *
     * @param call the Callable instance
     * @param exchange the exchange
     * @return the result of Callable return
     */
    public static Object callWithTCCL(Callable<?> call, Exchange exchange) throws Exception {
        ClassLoader apcl = null;
        if (exchange != null && exchange.getContext() != null) {
            apcl = exchange.getContext().getApplicationContextClassLoader();
        }
        return callWithTCCL(call, apcl);
    }

    /**
     * Calling the Callable with the setting of TCCL with a given classloader.
     *
     * @param call        the Callable instance
     * @param classloader the class loader
     * @return the result of Callable return
     */
    public static Object callWithTCCL(Callable<?> call, ClassLoader classloader) throws Exception {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            if (classloader != null) {
                Thread.currentThread().setContextClassLoader(classloader);
            }
            return call.call();
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    /**
     * Creates an iterable over the value if the value is a collection, an
     * Object[], a String with values separated by comma,
     * or a primitive type array; otherwise to simplify the caller's code,
     * we just create a singleton collection iterator over a single value
     * <p/>
     * Will default use comma for String separating String values.
     * This method does <b>not</b> allow empty values
     *
     * @param value  the value
     * @return the iterable
     */
    public static Iterable<?> createIterable(Object value) {
        return createIterable(value, DEFAULT_DELIMITER);
    }

    /**
     * Creates an iterable over the value if the value is a collection, an
     * Object[], a String with values separated by the given delimiter,
     * or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     * <p/>
     * This method does <b>not</b> allow empty values
     *
     * @param value      the value
     * @param delimiter  delimiter for separating String values
     * @return the iterable
     */
    public static Iterable<?> createIterable(Object value, String delimiter) {
        return createIterable(value, delimiter, false);
    }

    public static Iterable<String> createIterable(String value) {
        return createIterable(value, DEFAULT_DELIMITER);
    }

    public static Iterable<String> createIterable(String value, String delimiter) {
        return createIterable(value, delimiter, false);
    }

    public static Iterable<String> createIterable(String value, String delimiter, boolean allowEmptyValues) {
        return createIterable(value, delimiter, allowEmptyValues, false);
    }

    public static Iterable<String> createIterable(String value, String delimiter, boolean allowEmptyValues, boolean pattern) {
        if (value == null) {
            return Collections.emptyList();
        } else if (delimiter != null && (pattern || value.contains(delimiter))) {
            if (DEFAULT_DELIMITER.equals(delimiter)) {
                // we use the default delimiter which is a comma, then cater for bean expressions with OGNL
                // which may have balanced parentheses pairs as well.
                // if the value contains parentheses we need to balance those, to avoid iterating
                // in the middle of parentheses pair, so use this regular expression (a bit hard to read)
                // the regexp will split by comma, but honor parentheses pair that may include commas
                // as well, eg if value = "bean=foo?method=killer(a,b),bean=bar?method=great(a,b)"
                // then the regexp will split that into two:
                // -> bean=foo?method=killer(a,b)
                // -> bean=bar?method=great(a,b)
                // http://stackoverflow.com/questions/1516090/splitting-a-title-into-separate-parts
                return () -> new Scanner(value, DEFAULT_PATTERN);
            }
            return () -> new Scanner(value, delimiter);
        } else if (allowEmptyValues || org.apache.camel.util.ObjectHelper.isNotEmpty(value)) {
            return Collections.singletonList(value);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Creates an iterator over the value if the value is a {@link Stream}, collection, an
     * Object[], a String with values separated by comma,
     * or a primitive type array; otherwise to simplify the caller's code,
     * we just create a singleton collection iterator over a single value
     * <p/>
     * Will default use comma for String separating String values.
     * This method does <b>not</b> allow empty values
     *
     * @param value  the value
     * @return the iterator
     */
    public static Iterator<?> createIterator(Object value) {
        return createIterator(value, DEFAULT_DELIMITER);
    }

    /**
     * Creates an iterator over the value if the value is a {@link Stream}, collection, an
     * Object[], a String with values separated by the given delimiter,
     * or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     * <p/>
     * This method does <b>not</b> allow empty values
     *
     * @param value      the value
     * @param delimiter  delimiter for separating String values
     * @return the iterator
     */
    public static Iterator<?> createIterator(Object value, String delimiter) {
        return createIterator(value, delimiter, false);
    }

    /**
     * Creates an iterator over the value if the value is a {@link Stream}, collection, an
     * Object[], a String with values separated by the given delimiter,
     * or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     *
     * </p> In case of primitive type arrays the returned {@code Iterator} iterates
     * over the corresponding Java primitive wrapper objects of the given elements
     * inside the {@code value} array. That's we get an autoboxing of the primitive
     * types here for free as it's also the case in Java language itself.
     *
     * @param value             the value
     * @param delimiter         delimiter for separating String values
     * @param allowEmptyValues  whether to allow empty values
     * @return the iterator
     */
    public static Iterator<?> createIterator(Object value, String delimiter, boolean allowEmptyValues) {
        if (value instanceof Stream) {
            return ((Stream) value).iterator();
        }
        return createIterable(value, delimiter, allowEmptyValues, false).iterator();
    }

    /**
     * Creates an iterator over the value if the value is a {@link Stream}, collection, an
     * Object[], a String with values separated by the given delimiter,
     * or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     *
     * </p> In case of primitive type arrays the returned {@code Iterator} iterates
     * over the corresponding Java primitive wrapper objects of the given elements
     * inside the {@code value} array. That's we get an autoboxing of the primitive
     * types here for free as it's also the case in Java language itself.
     *
     * @param value             the value
     * @param delimiter         delimiter for separating String values
     * @param allowEmptyValues  whether to allow empty values
     * @param pattern           whether the delimiter is a pattern
     * @return the iterator
     */
    public static Iterator<?> createIterator(Object value, String delimiter,
                                                  boolean allowEmptyValues, boolean pattern) {
        if (value instanceof Stream) {
            return ((Stream) value).iterator();
        }
        return createIterable(value, delimiter, allowEmptyValues, pattern).iterator();
    }

    /**
     * Creates an iterable over the value if the value is a collection, an
     * Object[], a String with values separated by the given delimiter,
     * or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     *
     * </p> In case of primitive type arrays the returned {@code Iterable} iterates
     * over the corresponding Java primitive wrapper objects of the given elements
     * inside the {@code value} array. That's we get an autoboxing of the primitive
     * types here for free as it's also the case in Java language itself.
     *
     * @param value             the value
     * @param delimiter         delimiter for separating String values
     * @param allowEmptyValues  whether to allow empty values
     * @return the iterable
     * @see Iterable
     */
    public static Iterable<?> createIterable(Object value, String delimiter,
                                                  final boolean allowEmptyValues) {
        return createIterable(value, delimiter, allowEmptyValues, false);
    }

    /**
     * Creates an iterable over the value if the value is a collection, an
     * Object[], a String with values separated by the given delimiter,
     * or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     *
     * </p> In case of primitive type arrays the returned {@code Iterable} iterates
     * over the corresponding Java primitive wrapper objects of the given elements
     * inside the {@code value} array. That's we get an autoboxing of the primitive
     * types here for free as it's also the case in Java language itself.
     *
     * @param value             the value
     * @param delimiter         delimiter for separating String values
     * @param allowEmptyValues  whether to allow empty values
     * @param pattern           whether the delimiter is a pattern
     * @return the iterable
     * @see Iterable
     */
    @SuppressWarnings("unchecked")
    public static Iterable<?> createIterable(Object value, String delimiter,
                                             final boolean allowEmptyValues, final boolean pattern) {

        // if its a message than we want to iterate its body
        if (value instanceof Message) {
            value = ((Message) value).getBody();
        }

        if (value == null) {
            return Collections.emptyList();
        } else if (value instanceof Iterator) {
            final Iterator<Object> iterator = (Iterator<Object>)value;
            return new Iterable<Object>() {
                @Override
                public Iterator<Object> iterator() {
                    return iterator;
                }
            };
        } else if (value instanceof Iterable) {
            return (Iterable<Object>)value;
        } else if (value.getClass().isArray()) {
            if (org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(value.getClass())) {
                final Object array = value;
                return (Iterable<Object>) () -> new Iterator<Object>() {
                    private int idx;

                    public boolean hasNext() {
                        return idx < Array.getLength(array);
                    }

                    public Object next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException("no more element available for '" + array + "' at the index " + idx);
                        }

                        return Array.get(array, idx++);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            } else {
                return Arrays.asList((Object[]) value);
            }
        } else if (value instanceof NodeList) {
            // lets iterate through DOM results after performing XPaths
            final NodeList nodeList = (NodeList) value;
            return (Iterable<Node>) () -> new Iterator<Node>() {
                private int idx;

                public boolean hasNext() {
                    return idx < nodeList.getLength();
                }

                public Node next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException("no more element available for '" + nodeList + "' at the index " + idx);
                    }

                    return nodeList.item(idx++);
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        } else if (value instanceof String) {
            final String s = (String) value;

            // this code is optimized to only use a Scanner if needed, eg there is a delimiter

            if (delimiter != null && (pattern || s.contains(delimiter))) {
                if (DEFAULT_DELIMITER.equals(delimiter)) {
                    // we use the default delimiter which is a comma, then cater for bean expressions with OGNL
                    // which may have balanced parentheses pairs as well.
                    // if the value contains parentheses we need to balance those, to avoid iterating
                    // in the middle of parentheses pair, so use this regular expression (a bit hard to read)
                    // the regexp will split by comma, but honor parentheses pair that may include commas
                    // as well, eg if value = "bean=foo?method=killer(a,b),bean=bar?method=great(a,b)"
                    // then the regexp will split that into two:
                    // -> bean=foo?method=killer(a,b)
                    // -> bean=bar?method=great(a,b)
                    // http://stackoverflow.com/questions/1516090/splitting-a-title-into-separate-parts
                    return (Iterable<String>) () -> new Scanner(s, DEFAULT_PATTERN);
                }
                return (Iterable<String>) () -> new Scanner(s, delimiter);
            } else {
                return (Iterable<Object>) () -> {
                    // use a plain iterator that returns the value as is as there are only a single value
                    return new Iterator<Object>() {
                        private int idx;

                        public boolean hasNext() {
                            return idx == 0 && (allowEmptyValues || org.apache.camel.util.ObjectHelper.isNotEmpty(s));
                        }

                        public Object next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException("no more element available for '" + s + "' at the index " + idx);
                            }

                            idx++;
                            return s;
                        }

                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                };
            }
        } else {
            return Collections.singletonList(value);
        }
    }

    /**
     * Returns true if the collection contains the specified value
     */
    public static boolean contains(Object collectionOrArray, Object value) {
        // favor String types
        if (collectionOrArray != null && (collectionOrArray instanceof StringBuffer || collectionOrArray instanceof StringBuilder)) {
            collectionOrArray = collectionOrArray.toString();
        }
        if (value != null && (value instanceof StringBuffer || value instanceof StringBuilder)) {
            value = value.toString();
        }

        if (collectionOrArray instanceof Collection) {
            Collection<?> collection = (Collection<?>)collectionOrArray;
            return collection.contains(value);
        } else if (collectionOrArray instanceof String && value instanceof String) {
            String str = (String)collectionOrArray;
            String subStr = (String)value;
            return str.contains(subStr);
        } else {
            Iterator<?> iter = createIterator(collectionOrArray);
            while (iter.hasNext()) {
                if (org.apache.camel.util.ObjectHelper.equal(value, iter.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the collection contains the specified value by considering case insensitivity
     */
    public static boolean containsIgnoreCase(Object collectionOrArray, Object value) {
        // favor String types
        if (collectionOrArray != null && (collectionOrArray instanceof StringBuffer || collectionOrArray instanceof StringBuilder)) {
            collectionOrArray = collectionOrArray.toString();
        }
        if (value != null && (value instanceof StringBuffer || value instanceof StringBuilder)) {
            value = value.toString();
        }

        if (collectionOrArray instanceof Collection) {
            Collection<?> collection = (Collection<?>)collectionOrArray;
            return collection.contains(value);
        } else if (collectionOrArray instanceof String && value instanceof String) {
            String str = (String)collectionOrArray;
            String subStr = (String)value;
            return StringHelper.containsIgnoreCase(str, subStr);
        } else {
            Iterator<?> iter = createIterator(collectionOrArray);
            while (iter.hasNext()) {
                if (org.apache.camel.util.ObjectHelper.equalIgnoreCase(value, iter.next())) {
                    return true;
                }
            }
        }
        return false;
    }
}
