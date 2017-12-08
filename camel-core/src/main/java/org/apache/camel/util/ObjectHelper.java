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
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Component;
import org.apache.camel.ComponentAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Ordered;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.function.ThrowingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A number of useful helper methods for working with Objects
 *
 * @version 
 */
public final class ObjectHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ObjectHelper.class);
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
        boolean answer = equal(leftValue, rightValue, ignoreCase);
        if (answer) {
            return true;
        }

        // are they same type, if so return false as the equals returned false
        if (leftValue.getClass().isInstance(rightValue)) {
            return false;
        }

        // convert left to right
        Object value = converter.tryConvertTo(rightValue.getClass(), leftValue);
        answer = equal(value, rightValue, ignoreCase);
        if (answer) {
            return true;
        }

        // convert right to left
        value = converter.tryConvertTo(leftValue.getClass(), rightValue);
        answer = equal(leftValue, value, ignoreCase);
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
     * A helper method for comparing byte arrays for equality while handling
     * nulls
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

    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean)value;
        }
        if (value instanceof String) {
            return Boolean.valueOf((String)value);
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
     * @return the passed {@code value} as is
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static <T> T notNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be specified");
        }

        return value;
    }

    /**
     * Asserts whether the value is <b>not</b> <tt>null</tt>
     *
     * @param value  the value to test
     * @param on     additional description to indicate where this problem occurred (appended as toString())
     * @param name   the key that resolved the value
     * @return the passed {@code value} as is
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
     * Asserts whether the string is <b>not</b> empty.
     *
     * @param value the string to test
     * @param name the key that resolved the value
     * @return the passed {@code value} as is
     * @throws IllegalArgumentException is thrown if assertion fails
     * @deprecated use {@link StringHelper#notEmpty(String, String)} instead
     */
    @Deprecated
    public static String notEmpty(String value, String name) {
        return StringHelper.notEmpty(value, name);
    }

    /**
     * Asserts whether the string is <b>not</b> empty.
     *
     * @param value the string to test
     * @param on additional description to indicate where this problem occurred
     *            (appended as toString())
     * @param name the key that resolved the value
     * @return the passed {@code value} as is
     * @throws IllegalArgumentException is thrown if assertion fails
     * @deprecated use {@link StringHelper#notEmpty(String, String, Object)}
     *             instead
     */
    @Deprecated
    public static String notEmpty(String value, String name, Object on) {
        return StringHelper.notEmpty(value, name, on);
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
     * Tests whether the value is <b>not</b> <tt>null</tt>, an empty string or an empty collection/map.
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
        } else if (value instanceof Collection) {
            return !((Collection<?>)value).isEmpty();
        } else if (value instanceof Map) {
            return !((Map<?, ?>)value).isEmpty();
        } else {
            return true;
        }
    }


    /**
     * Returns the first non null object <tt>null</tt>.
     *
     * @param values the values
     * @return an Optional
     */
    public static Optional<Object> firstNotNull(Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                return Optional.of(values[i]);
            }
        }

        return Optional.empty();
    }

    /**
     * Tests whether the value is  <tt>null</tt>, an empty string, an empty collection or a map
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @param supplier  the supplier, the supplier to be used to get a value if value is null
     */
    public static <T> T supplyIfEmpty(T value, Supplier<T> supplier) {
        ObjectHelper.notNull(supplier, "Supplier");
        if (isNotEmpty(value)) {
            return value;
        }

        return supplier.get();
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt>, an empty string, an empty collection or a map
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @param consumer  the consumer, the operation to be executed against value if not empty
     */
    public static <T> void ifNotEmpty(T value, Consumer<T> consumer) {
        if (isNotEmpty(value)) {
            consumer.accept(value);
        }
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt>, an empty string, an empty collection or a map  and transform it using the given function.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @param function  the function to be executed against value if not empty
     */
    public static <I, R, T extends Throwable> Optional<R> applyIfNotEmpty(I value, ThrowingFunction<I, R, T> function) throws T {
        if (isNotEmpty(value)) {
            return Optional.ofNullable(function.apply(value));
        }

        return Optional.empty();
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt>, an empty string, an empty collection or a map and transform it using the given function.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @param consumer  the function to be executed against value if not empty
     * @param orElse  the supplier to use to retrieve a result if the given value is empty
     */
    public static <I, R, T extends Throwable> R applyIfNotEmpty(I value, ThrowingFunction<I, R, T> consumer, Supplier<R> orElse) throws T {
        if (isNotEmpty(value)) {
            return consumer.apply(value);
        }

        return orElse.get();
    }

    /**
     * @deprecated use
     *             {@link StringHelper#splitOnCharacter(String, String, int)} instead
     */
    @Deprecated
    public static String[] splitOnCharacter(String value, String needle, int count) {
        return StringHelper.splitOnCharacter(value, needle, count);
    }

    /**
     * Removes any starting characters on the given text which match the given
     * character
     *
     * @param text the string
     * @param ch the initial characters to remove
     * @return either the original string or the new substring
     * @deprecated use {@link StringHelper#removeStartingCharacters(String, char)} instead
     */
    @Deprecated
    public static String removeStartingCharacters(String text, char ch) {
        return StringHelper.removeStartingCharacters(text, ch);
    }

    /**
     * @deprecated use {@link StringHelper#capitalize(String)} instead
     */
    @Deprecated
    public static String capitalize(String text) {
        return StringHelper.capitalize(text);
    }

    /**
     * Returns the string after the given token
     *
     * @param text  the text
     * @param after the token
     * @return the text after the token, or <tt>null</tt> if text does not contain the token
     * @deprecated use {@link StringHelper#after(String, String)} instead
     */
    @Deprecated
    public static String after(String text, String after) {
        return StringHelper.after(text, after);
    }

    /**
     * Returns an object after the given token
     *
     * @param text the text
     * @param after the token
     * @param mapper a mapping function to convert the string after the token to
     *            type T
     * @return an Optional describing the result of applying a mapping function
     *         to the text after the token.
     * @deprecated use {@link StringHelper#after(String, String, Function)
     *             StringHelper.after(String, String, Function&lt;String,T&gt;)}
     *             instead
     */
    @Deprecated
    public static <T> Optional<T> after(String text, String after, Function<String, T> mapper) {
        return StringHelper.after(text, after, mapper);
    }

    /**
     * Returns the string before the given token
     *
     * @param text  the text
     * @param before the token
     * @return the text before the token, or <tt>null</tt> if text does not contain the token
     * @deprecated use {@link StringHelper#before(String, String)} instead
     */
    @Deprecated
    public static String before(String text, String before) {
        return StringHelper.before(text, before);
    }

    /**
     * Returns an object before the given token
     *
     * @param text the text
     * @param before the token
     * @param mapper a mapping function to convert the string before the token
     *            to type T
     * @return an Optional describing the result of applying a mapping function
     *         to the text before the token.
     * @deprecated use {@link StringHelper#before(String, String, Function)
     *             StringHelper.before(String, String, Function&lt;String,T&gt;)}
     *             instead
     */
    @Deprecated
    public static <T> Optional<T> before(String text, String before, Function<String, T> mapper) {
        return StringHelper.before(text, before, mapper);
    }

    /**
     * Returns the string between the given tokens
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @return the text between the tokens, or <tt>null</tt> if text does not contain the tokens
     * @deprecated use {@link StringHelper#between(String, String, String)} instead
     */
    @Deprecated
    public static String between(String text, String after, String before) {
        return StringHelper.between(text, after, before);
    }

    /**
     * Returns an object between the given token
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @param mapper a mapping function to convert the string between the token to type T
     * @return an Optional describing the result of applying a mapping function to the text between the token.
     * @deprecated use {@link StringHelper#between(String, String, String, Function)
     *             StringHelper.between(String, String, String, Function&lt;String,T&gt;)}
     *             instead
     */
    @Deprecated
    public static <T> Optional<T> between(String text, String after, String before, Function<String, T> mapper) {
        return StringHelper.between(text, after, before, mapper);
    }

    /**
     * Returns the string between the most outer pair of tokens
     * <p/>
     * The number of token pairs must be evenly, eg there must be same number of before and after tokens, otherwise <tt>null</tt> is returned
     * <p/>
     * This implementation skips matching when the text is either single or double quoted.
     * For example:
     * <tt>${body.matches("foo('bar')")</tt>
     * Will not match the parenthesis from the quoted text.
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @return the text between the outer most tokens, or <tt>null</tt> if text does not contain the tokens
     * @deprecated use {@link StringHelper#betweenOuterPair(String, char, char)} instead
     */
    @Deprecated
    public static String betweenOuterPair(String text, char before, char after) {
        return StringHelper.betweenOuterPair(text, before, after);
    }

    /**
     * Returns an object between the most outer pair of tokens
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @param mapper a mapping function to convert the string between the most outer pair of tokens to type T
     * @return an Optional describing the result of applying a mapping function to the text between the most outer pair of tokens.
     * @deprecated use {@link StringHelper#betweenOuterPair(String, char, char, Function)
     *             StringHelper.betweenOuterPair(String, char, char, Function&lt;String,T&gt;)}
     *             instead
     */
    @Deprecated
    public static <T> Optional<T> betweenOuterPair(String text, char before, char after, Function<String, T> mapper) {
        return StringHelper.betweenOuterPair(text, before, after, mapper);
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
            Iterator<Object> iter = createIterator(collectionOrArray);
            while (iter.hasNext()) {
                if (equal(value, iter.next())) {
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
            Iterator<Object> iter = createIterator(collectionOrArray);
            while (iter.hasNext()) {
                if (equalIgnoreCase(value, iter.next())) {
                    return true;
                }
            }
        }
        return false;
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
    public static Iterable<Object> createIterable(Object value) {
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
    public static Iterable<Object> createIterable(Object value, String delimiter) {
        return createIterable(value, delimiter, false);
    }

    /**
     * Creates an iterator over the value if the value is a collection, an
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
    public static Iterator<Object> createIterator(Object value) {
        return createIterator(value, DEFAULT_DELIMITER);
    }

    /**
     * Creates an iterator over the value if the value is a collection, an
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
    public static Iterator<Object> createIterator(Object value, String delimiter) {
        return createIterator(value, delimiter, false);
    }

    /**
     * Creates an iterator over the value if the value is a collection, an
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
    public static Iterator<Object> createIterator(Object value, String delimiter, boolean allowEmptyValues) {
        return createIterable(value, delimiter, allowEmptyValues, false).iterator();
    }

    /**
     * Creates an iterator over the value if the value is a collection, an
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
    public static Iterator<Object> createIterator(Object value, String delimiter,
                                                  boolean allowEmptyValues, boolean pattern) {
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
     * @see java.lang.Iterable
     */
    public static Iterable<Object> createIterable(Object value, String delimiter,
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
     * @see java.lang.Iterable
     */
    @SuppressWarnings("unchecked")
    public static Iterable<Object> createIterable(Object value, String delimiter,
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
            if (isPrimitiveArrayType(value.getClass())) {
                final Object array = value;
                return new Iterable<Object>() {
                    @Override
                    public Iterator<Object> iterator() {
                        return new Iterator<Object>() {
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
                    }
                };
            } else {
                return Arrays.asList((Object[]) value);
            }
        } else if (value instanceof NodeList) {
            // lets iterate through DOM results after performing XPaths
            final NodeList nodeList = (NodeList) value;
            return new Iterable<Object>() {
                @Override
                public Iterator<Object> iterator() {
                    return new Iterator<Object>() {
                        private int idx;

                        public boolean hasNext() {
                            return idx < nodeList.getLength();
                        }

                        public Object next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException("no more element available for '" + nodeList + "' at the index " + idx);
                            }

                            return nodeList.item(idx++);
                        }

                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            };
        } else if (value instanceof String) {
            final String s = (String) value;

            // this code is optimized to only use a Scanner if needed, eg there is a delimiter

            if (delimiter != null && (pattern || s.contains(delimiter))) {
                // use a scanner if it contains the delimiter or is a pattern
                final Scanner scanner = new Scanner((String)value);

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
                    delimiter = ",(?!(?:[^\\(,]|[^\\)],[^\\)])+\\))";
                }
                scanner.useDelimiter(delimiter);

                return new Iterable<Object>() {
                    @Override
                    public Iterator<Object> iterator() {
                        return CastUtils.cast(scanner);
                    }
                };
            } else {
                return new Iterable<Object>() {
                    @Override
                    public Iterator<Object> iterator() {
                        // use a plain iterator that returns the value as is as there are only a single value
                        return new Iterator<Object>() {
                            private int idx;

                            public boolean hasNext() {
                                return idx == 0 && (allowEmptyValues || ObjectHelper.isNotEmpty(s));
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
                    }
                };
            }
        } else {
            return Collections.singletonList(value);
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
    public static boolean matches(List<?> list) {
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
        Map<String, String> camelProperties = camelContext.getGlobalOptions();
        if (camelProperties != null) {
            for (Map.Entry<String, String> entry : camelProperties.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.startsWith(prefix)) {
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
        return loadClass(name, loader, false);
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
        if (ObjectHelper.isEmpty(name)) {
            return null;
        }

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
     * @param name the name of the class to load
     * @return the class or <tt>null</tt> if it could not be loaded
     */
    //CHECKSTYLE:OFF
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
    //CHECKSTYLE:ON

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
        return loadResourceAsStream(name, null);
    }

    /**
     * Attempts to load the given resource as a stream using the thread context
     * class loader or the class loader used to load this class
     *
     * @param name the name of the resource to load
     * @param loader optional classloader to attempt first
     * @return the stream or null if it could not be loaded
     */
    public static InputStream loadResourceAsStream(String name, ClassLoader loader) {
        InputStream in = null;

        String resolvedName = resolveUriPath(name);
        if (loader != null) {
            in = loader.getResourceAsStream(resolvedName);
        }
        if (in == null) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                in = contextClassLoader.getResourceAsStream(resolvedName);
            }
        }
        if (in == null) {
            in = ObjectHelper.class.getClassLoader().getResourceAsStream(resolvedName);
        }
        if (in == null) {
            in = ObjectHelper.class.getResourceAsStream(resolvedName);
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
        return loadResourceAsURL(name, null);
    }

    /**
     * Attempts to load the given resource as a stream using the thread context
     * class loader or the class loader used to load this class
     *
     * @param name the name of the resource to load
     * @param loader optional classloader to attempt first
     * @return the stream or null if it could not be loaded
     */
    public static URL loadResourceAsURL(String name, ClassLoader loader) {
        URL url = null;

        String resolvedName = resolveUriPath(name);
        if (loader != null) {
            url = loader.getResource(resolvedName);
        }
        if (url == null) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                url = contextClassLoader.getResource(resolvedName);
            }
        }
        if (url == null) {
            url = ObjectHelper.class.getClassLoader().getResource(resolvedName);
        }
        if (url == null) {
            url = ObjectHelper.class.getResource(resolvedName);
        }

        return url;
    }

    /**
     * Attempts to load the given resources from the given package name using the thread context
     * class loader or the class loader used to load this class
     *
     * @param packageName the name of the package to load its resources
     * @return the URLs for the resources or null if it could not be loaded
     */
    public static Enumeration<URL> loadResourcesAsURL(String packageName) {
        return loadResourcesAsURL(packageName, null);
    }

    /**
     * Attempts to load the given resources from the given package name using the thread context
     * class loader or the class loader used to load this class
     *
     * @param packageName the name of the package to load its resources
     * @param loader optional classloader to attempt first
     * @return the URLs for the resources or null if it could not be loaded
     */
    public static Enumeration<URL> loadResourcesAsURL(String packageName, ClassLoader loader) {
        Enumeration<URL> url = null;

        if (loader != null) {
            try {
                url = loader.getResources(packageName);
            } catch (IOException e) {
                // ignore
            }
        }

        if (url == null) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                try {
                    url = contextClassLoader.getResources(packageName);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        if (url == null) {
            try {
                url = ObjectHelper.class.getClassLoader().getResources(packageName);
            } catch (IOException e) {
                // ignore
            }
        }

        return url;
    }

    /**
     * Helper operation used to remove relative path notation from 
     * resources.  Most critical for resources on the Classpath
     * as resource loaders will not resolve the relative paths correctly.
     * 
     * @param name the name of the resource to load
     * @return the modified or unmodified string if there were no changes
     */
    private static String resolveUriPath(String name) {
        // compact the path and use / as separator as that's used for loading resources on the classpath
        return FileUtil.compactPath(name, '/');
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
            throw ObjectHelper.wrapRuntimeCamelException(e.getCause());
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
        return isOverridingMethod(source, target, true);
    }

    /**
     * Tests whether the target method overrides the source method.
     * <p/>
     * Tests whether they have the same name, return type, and parameter list.
     *
     * @param source  the source method
     * @param target  the target method
     * @param exact   <tt>true</tt> if the override must be exact same types, <tt>false</tt> if the types should be assignable
     * @return <tt>true</tt> if it override, <tt>false</tt> otherwise
     */
    public static boolean isOverridingMethod(Method source, Method target, boolean exact) {
        return isOverridingMethod(target.getDeclaringClass(), source, target, exact);
    }

    /**
     * Tests whether the target method overrides the source method from the
     * inheriting class.
     * <p/>
     * Tests whether they have the same name, return type, and parameter list.
     *
     * @param inheritingClass the class inheriting the target method overriding
     *            the source method
     * @param source the source method
     * @param target the target method
     * @param exact <tt>true</tt> if the override must be exact same types,
     *            <tt>false</tt> if the types should be assignable
     * @return <tt>true</tt> if it override, <tt>false</tt> otherwise
     */
    public static boolean isOverridingMethod(Class<?> inheritingClass, Method source, Method target, boolean exact) {

        if (source.equals(target)) {
            return true;
        } else if (target.getDeclaringClass().isAssignableFrom(source.getDeclaringClass())) {
            return false;
        } else if (!source.getDeclaringClass().isAssignableFrom(inheritingClass) || !target.getDeclaringClass().isAssignableFrom(inheritingClass)) {
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
     * Returns if the given {@code clazz} type is a Java primitive array type.
     * 
     * @param clazz the Java type to be checked
     * @return {@code true} if the given type is a Java primitive array type
     */
    public static boolean isPrimitiveArrayType(Class<?> clazz) {
        if (clazz != null && clazz.isArray()) {
            return clazz.getComponentType().isPrimitive();
        }
        return false;
    }

    public static int arrayLength(Object[] pojo) {
        return pojo.length;
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
            } else if (type == char.class) {
                rc = Character.class;
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
        if (propertyName.startsWith("set") && method.getParameterCount() == 1) {
            propertyName = propertyName.substring(3, 4).toLowerCase(Locale.ENGLISH) + propertyName.substring(4);
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
     * @param instance the instance
     * @param type  the annotation
     * @return the annotation, or <tt>null</tt> if the instance does not have the given annotation
     */
    public static <A extends java.lang.annotation.Annotation> A getAnnotation(Object instance, Class<A> type) {
        return instance.getClass().getAnnotation(type);
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
            Class<?> newType = convertPrimitiveTypeToWrapperType(toType);
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
     * Returns true if the given name is a valid java identifier
     * @deprecated use {@link StringHelper#isJavaIdentifier(String)} instead
     */
    @Deprecated
    public static boolean isJavaIdentifier(String name) {
        return StringHelper.isJavaIdentifier(name);
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
            // is it an empty dom with empty attributes
            if (value instanceof Node && ((Node)value).hasAttributes()) {
                return true;
            }
            NodeList list = (NodeList) value;
            return list.getLength() > 0;
        } else if (value instanceof Collection) {
            // is it an empty collection
            Collection<?> col = (Collection<?>) value;
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
     * Cleans the string to a pure Java identifier so we can use it for loading
     * class names.
     * <p/>
     * Especially from Spring DSL people can have \n \t or other characters that
     * otherwise would result in ClassNotFoundException
     *
     * @param name the class name
     * @return normalized classname that can be load by a class loader.
     * @deprecated use {@link StringHelper#normalizeClassName(String)} instead
     */
    @Deprecated
    public static String normalizeClassName(String name) {
        return StringHelper.normalizeClassName(name);
    }

    /**
     * Creates an Iterable to walk the exception from the bottom up
     * (the last caused by going upwards to the root exception).
     *
     * @see java.lang.Iterable
     * @param exception  the exception
     * @return the Iterable
     */
    public static Iterable<Throwable> createExceptionIterable(Throwable exception) {
        List<Throwable> throwables = new ArrayList<Throwable>();

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
     * Creates an Iterator to walk the exception from the bottom up
     * (the last caused by going upwards to the root exception).
     *
     * @see Iterator
     * @param exception  the exception
     * @return the Iterator
     */
    public static Iterator<Throwable> createExceptionIterator(Throwable exception) {
        return createExceptionIterable(exception).iterator();
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

    /**
     * Creates a {@link Scanner} for scanning the given value.
     *
     * @param exchange  the current exchange
     * @param value     the value, typically the message IN body
     * @return the scanner, is newer <tt>null</tt>
     */
    public static Scanner getScanner(Exchange exchange, Object value) {
        if (value instanceof WrappedFile) {
            WrappedFile<?> gf = (WrappedFile<?>) value;
            Object body = gf.getBody();
            if (body != null) {
                // we have loaded the file content into the body so use that
                value = body;
            } else {
                // generic file is just a wrapper for the real file so call again with the real file
                return getScanner(exchange, gf.getFile());
            }
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
    public static String lookupConstantFieldValue(Class<?> clazz, String name) {
        if (clazz == null) {
            return null;
        }

        // remove leading dots
        if (name.startsWith(",")) {
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
     * @param value the value
     * @return <tt>true</tt> if its a {@link Float#NaN} or {@link Double#NaN}.
     */
    public static boolean isNaN(Object value) {
        if (value == null || !(value instanceof Number)) {
            return false;
        }
        // value must be a number
        return value.equals(Float.NaN) || value.equals(Double.NaN);
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
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            if (classloader != null) {
                Thread.currentThread().setContextClassLoader(classloader);
            }
            return call.call();
        } finally {
            if (tccl != null) {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }
    }

    /**
     * Set the {@link CamelContext} context if the component is an instance of {@link CamelContextAware}.
     */
    public static <T> T trySetCamelContext(T object, CamelContext camelContext) {
        if (object instanceof CamelContextAware) {
            ((CamelContextAware) object).setCamelContext(camelContext);
        }

        return object;
    }

    /**
     * Set the {@link Component} context if the component is an instance of {@link ComponentAware}.
     */
    public static <T> T trySetComponent(T object, Component component) {
        if (object instanceof ComponentAware) {
            ((ComponentAware) object).setComponent(component);
        }

        return object;
    }
    
}
