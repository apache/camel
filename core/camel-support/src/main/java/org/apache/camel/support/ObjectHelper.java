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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Ordered;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConverter;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.Scanner;
import org.apache.camel.util.StringHelper;

/**
 * A number of useful helper methods for working with Objects
 */
public final class ObjectHelper {

    static {
        PARENTHESIS_PATTERN = Pattern.compile(",(?!(?:[^\\(,]|[^\\)],[^\\)])+\\))");
    }

    private static final Pattern PARENTHESIS_PATTERN;
    private static final String DEFAULT_DELIMITER = ",";
    private static final char DEFAULT_DELIMITER_CHAR = ',';

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectHelper() {
    }

    /**
     * A helper method for comparing objects for equality in which it uses type coercion to coerce types between the
     * left and right values. This allows you test for equality for example with a String and Integer type as Camel will
     * be able to coerce the types.
     */
    public static boolean typeCoerceEquals(TypeConverter converter, Object leftValue, Object rightValue) {
        return typeCoerceEquals(converter, leftValue, rightValue, false);
    }

    /**
     * A helper method for comparing objects for equality in which it uses type coercion to coerce types between the
     * left and right values. This allows you test for equality for example with a String and Integer type as Camel will
     * be able to coerce the types.
     */
    public static boolean typeCoerceEquals(TypeConverter converter, Object leftValue, Object rightValue, boolean ignoreCase) {
        // sanity check
        if (leftValue == null || rightValue == null) {
            return evalNulls(leftValue, rightValue);
        }

        // optimize for common combinations of comparing numbers
        if (leftValue instanceof String) {
            return typeCoerceString(converter, leftValue, rightValue, ignoreCase);
        } else if (rightValue instanceof String &&
                (leftValue instanceof Integer || leftValue instanceof Long) && isNumber((String) rightValue)) {
            return typeCoerceIntLong(leftValue, (String) rightValue);
        } else if (leftValue instanceof Integer && rightValue instanceof Integer) {
            return integerPairComparison(leftValue, rightValue);
        } else if (leftValue instanceof Long && rightValue instanceof Long) {
            return longPairComparison(leftValue, rightValue);
        } else if (leftValue instanceof Double && rightValue instanceof Double) {
            return doublePairComparison(leftValue, rightValue);
        } else if (rightValue instanceof String && leftValue instanceof Boolean) {
            return booleanStringComparison((Boolean) leftValue, (String) rightValue);
        }

        // try without type coerce
        return tryConverters(converter, leftValue, rightValue, ignoreCase);
    }

    private static boolean typeCoerceString(TypeConverter converter, Object leftValue, Object rightValue, boolean ignoreCase) {
        if (rightValue instanceof String) {
            return typeCoerceStringPair((String) leftValue, (String) rightValue, ignoreCase);
        } else if ((rightValue instanceof Integer || rightValue instanceof Long) &&
                isNumber((String) leftValue)) {
            return typeCoerceILString((String) leftValue, rightValue);
        } else if (rightValue instanceof Double && isFloatingNumber((String) leftValue)) {
            return stringDoubleComparison((String) leftValue, (Double) rightValue);
        } else if (rightValue instanceof Boolean) {
            return stringBooleanComparison(leftValue, rightValue);
        }

        return tryConverters(converter, leftValue, rightValue, ignoreCase);
    }

    private static boolean evalNulls(Object leftValue, Object rightValue) {
        // they are equal
        if (leftValue == rightValue) {
            return true;
        }
        // only one of them is null so they are not equal
        return false;
    }

    private static boolean tryConverters(TypeConverter converter, Object leftValue, Object rightValue, boolean ignoreCase) {
        final boolean isEqual = org.apache.camel.util.ObjectHelper.equal(leftValue, rightValue, ignoreCase);
        if (isEqual) {
            return true;
        }

        // are they same type, if so return false as the equals returned false
        if (leftValue.getClass().isInstance(rightValue)) {
            return false;
        }

        // convert left to right
        StreamCache sc = null;
        try {
            if (leftValue instanceof StreamCache) {
                sc = (StreamCache) leftValue;
            }
            Object value = converter.tryConvertTo(rightValue.getClass(), leftValue);
            final boolean isEqualLeftToRight = org.apache.camel.util.ObjectHelper.equal(value, rightValue, ignoreCase);
            if (isEqualLeftToRight) {
                return true;
            }

            // convert right to left
            if (rightValue instanceof StreamCache) {
                sc = (StreamCache) rightValue;
            }
            value = converter.tryConvertTo(leftValue.getClass(), rightValue);
            return org.apache.camel.util.ObjectHelper.equal(leftValue, value, ignoreCase);
        } finally {
            if (sc != null) {
                sc.reset();
            }
        }
    }

    private static boolean booleanStringComparison(Boolean leftBool, String rightValue) {
        Boolean rightBool = Boolean.valueOf(rightValue);
        return leftBool.compareTo(rightBool) == 0;
    }

    private static boolean doublePairComparison(Object leftValue, Object rightValue) {
        return doublePairComparison((Double) leftValue, (Double) rightValue);
    }

    private static boolean doublePairComparison(Double leftValue, Double rightValue) {
        return leftValue.compareTo(rightValue) == 0;
    }

    private static boolean longPairComparison(Object leftValue, Object rightValue) {
        return longPairComparison((Long) leftValue, (Long) rightValue);
    }

    private static boolean longPairComparison(Long leftValue, Long rightValue) {
        return leftValue.compareTo(rightValue) == 0;
    }

    private static boolean integerPairComparison(Object leftValue, Object rightValue) {
        return integerPairComparison((Integer) leftValue, (Integer) rightValue);
    }

    private static boolean integerPairComparison(Integer leftValue, Integer rightValue) {
        return leftValue.compareTo(rightValue) == 0;
    }

    private static boolean stringBooleanComparison(Object leftValue, Object rightValue) {
        return stringBooleanComparison((String) leftValue, (Boolean) rightValue);
    }

    private static boolean stringBooleanComparison(String leftValue, Boolean rightValue) {
        Boolean leftBool = Boolean.valueOf(leftValue);
        return leftBool.compareTo(rightValue) == 0;
    }

    private static boolean stringDoubleComparison(String leftValue, Double rightValue) {
        return doublePairComparison(Double.valueOf(leftValue), rightValue);
    }

    private static boolean typeCoerceIntLong(Object leftValue, String rightValue) {
        if (leftValue instanceof Integer) {
            return integerPairComparison((Integer) leftValue, Integer.valueOf(rightValue));
        } else {
            return longPairComparison((Long) leftValue, Long.valueOf(rightValue));
        }
    }

    private static boolean typeCoerceILString(String leftValue, Object rightValue) {
        if (rightValue instanceof Integer) {
            return integerPairComparison(Integer.valueOf(leftValue), (Integer) rightValue);
        } else {
            return longPairComparison(Long.valueOf(leftValue), (Long) rightValue);
        }
    }

    private static boolean typeCoerceStringPair(String leftNum, String rightNum, boolean ignoreCase) {
        if (isNumber(leftNum) && isNumber(rightNum)) {
            // favour to use numeric comparison
            return longPairComparison(Long.parseLong(leftNum), Long.parseLong(rightNum));
        }
        if (ignoreCase) {
            return leftNum.compareToIgnoreCase(rightNum) == 0;
        } else {
            return leftNum.compareTo(rightNum) == 0;
        }
    }

    /**
     * A helper method for comparing objects for inequality in which it uses type coercion to coerce types between the
     * left and right values. This allows you test for inequality for example with a String and Integer type as Camel
     * will be able to coerce the types.
     */
    public static boolean typeCoerceNotEquals(TypeConverter converter, Object leftValue, Object rightValue) {
        return !typeCoerceEquals(converter, leftValue, rightValue);
    }

    /**
     * A helper method for comparing objects ordering in which it uses type coercion to coerce types between the left
     * and right values. This allows you test for ordering for example with a String and Integer type as Camel will be
     * able to coerce the types.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static int typeCoerceCompare(TypeConverter converter, Object leftValue, Object rightValue) {

        // optimize for common combinations of comparing numbers
        if (leftValue instanceof String && rightValue instanceof String) {
            String leftNum = (String) leftValue;
            String rightNum = (String) rightValue;
            if (isNumber(leftNum) && isNumber(rightNum)) {
                // favour to use numeric comparison
                Long num1 = Long.parseLong(leftNum);
                Long num2 = Long.parseLong(rightNum);
                return num1.compareTo(num2);
            }
            return leftNum.compareTo(rightNum);
        } else if (leftValue instanceof Integer && rightValue instanceof Integer) {
            Integer leftNum = (Integer) leftValue;
            Integer rightNum = (Integer) rightValue;
            return leftNum.compareTo(rightNum);
        } else if (leftValue instanceof Long && rightValue instanceof Long) {
            Long leftNum = (Long) leftValue;
            Long rightNum = (Long) rightValue;
            return leftNum.compareTo(rightNum);
        } else if (leftValue instanceof Double && rightValue instanceof Double) {
            Double leftNum = (Double) leftValue;
            Double rightNum = (Double) rightValue;
            return leftNum.compareTo(rightNum);
        } else if ((rightValue instanceof Integer || rightValue instanceof Long) &&
                leftValue instanceof String && isNumber((String) leftValue)) {
            if (rightValue instanceof Integer) {
                Integer leftNum = Integer.valueOf((String) leftValue);
                Integer rightNum = (Integer) rightValue;
                return leftNum.compareTo(rightNum);
            } else {
                Long leftNum = Long.valueOf((String) leftValue);
                Long rightNum = (Long) rightValue;
                return leftNum.compareTo(rightNum);
            }
        } else if (rightValue instanceof String &&
                (leftValue instanceof Integer || leftValue instanceof Long) && isNumber((String) rightValue)) {
            if (leftValue instanceof Integer) {
                Integer leftNum = (Integer) leftValue;
                Integer rightNum = Integer.valueOf((String) rightValue);
                return leftNum.compareTo(rightNum);
            } else {
                Long leftNum = (Long) leftValue;
                Long rightNum = Long.valueOf((String) rightValue);
                return leftNum.compareTo(rightNum);
            }
        } else if (rightValue instanceof Double && leftValue instanceof String && isFloatingNumber((String) leftValue)) {
            Double leftNum = Double.valueOf((String) leftValue);
            Double rightNum = (Double) rightValue;
            return leftNum.compareTo(rightNum);
        } else if (rightValue instanceof Boolean && leftValue instanceof String) {
            Boolean leftBool = Boolean.valueOf((String) leftValue);
            Boolean rightBool = (Boolean) rightValue;
            return leftBool.compareTo(rightBool);
        } else if (rightValue instanceof String && leftValue instanceof Boolean) {
            Boolean leftBool = (Boolean) leftValue;
            Boolean rightBool = Boolean.valueOf((String) rightValue);
            return leftBool.compareTo(rightBool);
        }

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
        if (rightValue instanceof String && !(leftValue instanceof String)) {
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
     * Checks whether the text is an integer number
     */
    public static boolean isNumber(String text) {
        final int startPos = findStartPosition(text);
        if (startPos < 0) {
            return false;
        }

        for (int i = startPos; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    private static int findStartPosition(String text) {
        if (text == null || text.isEmpty()) {
            // invalid
            return -1;
        }

        int startPos = 0;
        if (text.charAt(0) == '-') {
            if (text.length() == 1) {
                // invalid
                return -1;
            }

            // skip leading negative
            startPos = 1;
        }

        return startPos;
    }

    /**
     * Checks whether the text is a float point number
     */
    public static boolean isFloatingNumber(String text) {
        final int startPos = findStartPosition(text);
        if (startPos < 0) {
            return false;
        }

        boolean dots = false;
        for (int i = startPos; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isDigit(ch)) {
                if (ch == '.') {
                    if (dots) {
                        return false;
                    }
                    dots = true;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A helper method to invoke a method via reflection and wrap any exceptions as {@link RuntimeCamelException}
     * instances
     *
     * @param  method     the method to invoke
     * @param  instance   the object instance (or null for static methods)
     * @param  parameters the parameters to the method
     * @return            the result of the method invocation
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
     * A helper method to invoke a method via reflection in a safe way by allowing to invoke methods that are not
     * accessible by default and wrap any exceptions as {@link RuntimeCamelException} instances
     *
     * @param  method     the method to invoke
     * @param  instance   the object instance (or null for static methods)
     * @param  parameters the parameters to the method
     * @return            the result of the method invocation
     */
    public static Object invokeMethodSafe(Method method, Object instance, Object... parameters)
            throws InvocationTargetException, IllegalAccessException {
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
     * A helper method to invoke a method via reflection in a safe way by allowing to invoke methods that are not
     * accessible by default and wrap any exceptions as {@link RuntimeCamelException} instances
     *
     * @param  name       the method name
     * @param  instance   the object instance (or null for static methods)
     * @param  parameters the parameters to the method
     * @return            the result of the method invocation
     */
    public static Object invokeMethodSafe(String name, Object instance, Object... parameters)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        // find method first
        Class<?>[] arr = null;
        if (parameters != null) {
            arr = new Class[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Object p = parameters[i];
                if (p != null) {
                    arr[i] = p.getClass();
                }
            }
        }
        Method m = ReflectionHelper.findMethod(instance.getClass(), name, arr);
        if (m != null) {
            return invokeMethodSafe(m, instance, parameters);
        } else {
            throw new NoSuchMethodException(name);
        }
    }

    /**
     * A helper method to create a new instance of a type using the default constructor arguments.
     */
    public static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * A helper method to create a new instance of a type using the default constructor arguments.
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
     * A helper method for performing an ordered comparison on the objects handling nulls and objects which do not
     * handle sorting gracefully
     */
    public static int compare(Object a, Object b) {
        return compare(a, b, false);
    }

    /**
     * A helper method for performing an ordered comparison on the objects handling nulls and objects which do not
     * handle sorting gracefully
     *
     * @param a          the first object
     * @param b          the second object
     * @param ignoreCase ignore case for string comparison
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
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
            Comparable comparable = (Comparable) a;
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
     * @param  call     the Callable instance
     * @param  exchange the exchange
     * @return          the result of Callable return
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
     * @param  call        the Callable instance
     * @param  classloader the class loader
     * @return             the result of Callable return
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
     * Creates an iterable over the value if the value is a collection, an Object[], a String with values separated by
     * comma, or a primitive type array; otherwise to simplify the caller's code, we just create a singleton collection
     * iterator over a single value
     * <p/>
     * Will default use comma for String separating String values. This method does <b>not</b> allow empty values
     *
     * @param  value the value
     * @return       the iterable
     */
    public static Iterable<?> createIterable(Object value) {
        return createIterable(value, DEFAULT_DELIMITER);
    }

    /**
     * Creates an iterable over the value if the value is a collection, an Object[], a String with values separated by
     * the given delimiter, or a primitive type array; otherwise to simplify the caller's code, we just create a
     * singleton collection iterator over a single value
     * <p/>
     * This method does <b>not</b> allow empty values
     *
     * @param  value     the value
     * @param  delimiter delimiter for separating String values
     * @return           the iterable
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
            // if its the default delimiter and the value has parenthesis
            if (DEFAULT_DELIMITER.equals(delimiter)) {
                if (value.indexOf('(') != -1 && value.indexOf(')') != -1) {
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
                    return () -> new Scanner(value, PARENTHESIS_PATTERN);
                } else {
                    // optimized split string on default delimiter
                    int count = StringHelper.countChar(value, DEFAULT_DELIMITER_CHAR) + 1;
                    return () -> StringHelper.splitOnCharacterAsIterator(value, DEFAULT_DELIMITER_CHAR, count);
                }
            } else if (pattern) {
                return () -> new StringIteratorForPattern(value, delimiter);
            }
            return () -> new StringIterator(value, delimiter);
        } else if (allowEmptyValues || org.apache.camel.util.ObjectHelper.isNotEmpty(value)) {
            return Collections.singletonList(value);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Creates an iterator over the value if the value is a {@link Stream}, collection, an Object[], a String with
     * values separated by comma, or a primitive type array; otherwise to simplify the caller's code, we just create a
     * singleton collection iterator over a single value
     * <p/>
     * Will default use comma for String separating String values. This method does <b>not</b> allow empty values
     *
     * @param  value the value
     * @return       the iterator
     */
    public static Iterator<?> createIterator(Object value) {
        return createIterator(value, DEFAULT_DELIMITER);
    }

    /**
     * Creates an iterator over the value if the value is a {@link Stream}, collection, an Object[], a String with
     * values separated by the given delimiter, or a primitive type array; otherwise to simplify the caller's code, we
     * just create a singleton collection iterator over a single value
     * <p/>
     * This method does <b>not</b> allow empty values
     *
     * @param  value     the value
     * @param  delimiter delimiter for separating String values
     * @return           the iterator
     */
    public static Iterator<?> createIterator(Object value, String delimiter) {
        return createIterator(value, delimiter, false);
    }

    /**
     * Creates an iterator over the value if the value is a {@link Stream}, collection, an Object[], a String with
     * values separated by the given delimiter, or a primitive type array; otherwise to simplify the caller's code, we
     * just create a singleton collection iterator over a single value
     *
     * </p>
     * In case of primitive type arrays the returned {@code Iterator} iterates over the corresponding Java primitive
     * wrapper objects of the given elements inside the {@code value} array. That's we get an autoboxing of the
     * primitive types here for free as it's also the case in Java language itself.
     *
     * @param  value            the value
     * @param  delimiter        delimiter for separating String values
     * @param  allowEmptyValues whether to allow empty values
     * @return                  the iterator
     */
    public static Iterator<?> createIterator(Object value, String delimiter, boolean allowEmptyValues) {
        if (value instanceof Stream) {
            return ((Stream) value).iterator();
        }
        return createIterable(value, delimiter, allowEmptyValues, false).iterator();
    }

    /**
     * Creates an iterator over the value if the value is a {@link Stream}, collection, an Object[], a String with
     * values separated by the given delimiter, or a primitive type array; otherwise to simplify the caller's code, we
     * just create a singleton collection iterator over a single value
     *
     * </p>
     * In case of primitive type arrays the returned {@code Iterator} iterates over the corresponding Java primitive
     * wrapper objects of the given elements inside the {@code value} array. That's we get an autoboxing of the
     * primitive types here for free as it's also the case in Java language itself.
     *
     * @param  value            the value
     * @param  delimiter        delimiter for separating String values
     * @param  allowEmptyValues whether to allow empty values
     * @param  pattern          whether the delimiter is a pattern
     * @return                  the iterator
     */
    public static Iterator<?> createIterator(
            Object value, String delimiter,
            boolean allowEmptyValues, boolean pattern) {
        if (value instanceof Stream) {
            return ((Stream) value).iterator();
        }
        return createIterable(value, delimiter, allowEmptyValues, pattern).iterator();
    }

    /**
     * Creates an iterable over the value if the value is a collection, an Object[], a String with values separated by
     * the given delimiter, or a primitive type array; otherwise to simplify the caller's code, we just create a
     * singleton collection iterator over a single value
     *
     * </p>
     * In case of primitive type arrays the returned {@code Iterable} iterates over the corresponding Java primitive
     * wrapper objects of the given elements inside the {@code value} array. That's we get an autoboxing of the
     * primitive types here for free as it's also the case in Java language itself.
     *
     * @param  value            the value
     * @param  delimiter        delimiter for separating String values
     * @param  allowEmptyValues whether to allow empty values
     * @return                  the iterable
     * @see                     Iterable
     */
    public static Iterable<?> createIterable(
            Object value, String delimiter,
            final boolean allowEmptyValues) {
        return createIterable(value, delimiter, allowEmptyValues, false);
    }

    /**
     * Creates an iterable over the value if the value is a collection, an Object[], a String with values separated by
     * the given delimiter, or a primitive type array; otherwise to simplify the caller's code, we just create a
     * singleton collection iterator over a single value
     * </p>
     * In case of primitive type arrays the returned {@code Iterable} iterates over the corresponding Java primitive
     * wrapper objects of the given elements inside the {@code value} array. That's we get an autoboxing of the
     * primitive types here for free as it's also the case in Java language itself.
     *
     * @param  value            the value
     * @param  delimiter        delimiter for separating String values
     * @param  allowEmptyValues whether to allow empty values
     * @param  pattern          whether the delimiter is a pattern
     * @return                  the iterable
     * @see                     Iterable
     */
    @SuppressWarnings("unchecked")
    public static Iterable<?> createIterable(
            Object value, String delimiter,
            final boolean allowEmptyValues, final boolean pattern) {

        // if its a message than we want to iterate its body
        if (value instanceof Message) {
            value = ((Message) value).getBody();
        }

        if (value == null) {
            return Collections.emptyList();
        }
        if (fastStringCheck(value)) {
            return createStringIterator((String) value, delimiter, allowEmptyValues, pattern);
        }
        if (fastIsMap(value)) {
            Map<?, ?> map = (Map<?, ?>) value;
            return map.entrySet();
        }
        if (value.getClass().isArray()) {
            return createArrayIterator(value);
        }

        return trySlowIterables(value);
    }

    private static Iterable<?> trySlowIterables(Object value) {
        if (value instanceof Iterator) {
            final Iterator<Object> iterator = (Iterator<Object>) value;
            return (Iterable<Object>) () -> iterator;
        } else if (value instanceof Iterable) {
            return (Iterable<Object>) value;
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            return map.entrySet();
        } else if (value instanceof NodeList) {
            // lets iterate through DOM results after performing XPaths
            final NodeList nodeList = (NodeList) value;
            return (Iterable<Node>) () -> createNodeListIterator(nodeList);
        } else {
            return Collections.singletonList(value);
        }
    }

    private static boolean fastStringCheck(Object obj) {
        return obj.getClass() == String.class;
    }

    private static boolean fastIsMap(Object obj) {
        return obj.getClass() == HashMap.class
                || obj.getClass() == ConcurrentHashMap.class
                || obj.getClass() == ConcurrentSkipListMap.class
                || obj.getClass() == EnumMap.class
                || obj.getClass() == LinkedHashMap.class
                || obj.getClass() == TreeMap.class
                || obj.getClass() == WeakHashMap.class;

    }

    private static Iterable<Object> createArrayIterator(Object value) {
        if (org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(value.getClass())) {
            final Object array = value;
            return () -> createPrimitiveArrayIterator(array);
        } else {
            return Arrays.asList((Object[]) value);
        }
    }

    private static Iterable<?> createStringIterator(String value, String delimiter, boolean allowEmptyValues, boolean pattern) {
        final String s = value;

        // this code is optimized to only use a Scanner if needed, eg there is a delimiter

        if (delimiter != null && (pattern || s.contains(delimiter))) {
            // if its the default delimiter and the value has parenthesis
            if (DEFAULT_DELIMITER.equals(delimiter)) {
                return createDelimitedStringIterator(s);
            } else if (pattern) {
                return (Iterable<String>) () -> new StringIteratorForPattern(s, delimiter);
            }
            return (Iterable<String>) () -> new StringIterator(s, delimiter);
        } else {
            return (Iterable<Object>) () -> createPlainIterator(allowEmptyValues, s);
        }
    }

    private static Iterable<String> createDelimitedStringIterator(String s) {
        if (s.indexOf('(') != -1 && s.indexOf(')') != -1) {
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
            return (Iterable<String>) () -> new Scanner(s, PARENTHESIS_PATTERN);
        } else {
            // optimized split string on default delimiter
            int count = StringHelper.countChar(s, DEFAULT_DELIMITER_CHAR) + 1;
            return (Iterable<String>) () -> StringHelper.splitOnCharacterAsIterator(s, DEFAULT_DELIMITER_CHAR,
                    count);
        }
    }

    private static Iterator<Object> createPlainIterator(boolean allowEmptyValues, String s) {
        // use a plain iterator that returns the value as is as there are only a single value
        return new Iterator<>() {
            private int idx;

            public boolean hasNext() {
                return idx == 0 && (allowEmptyValues || org.apache.camel.util.ObjectHelper.isNotEmpty(s));
            }

            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException(
                            "no more element available for '" + s + "' at the index " + idx);
                }

                idx++;
                return s;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static Iterator<Node> createNodeListIterator(NodeList nodeList) {
        return new Iterator<>() {
            private int idx;

            public boolean hasNext() {
                return idx < nodeList.getLength();
            }

            public Node next() {
                if (!hasNext()) {
                    throw new NoSuchElementException(
                            "no more element available for '" + nodeList + "' at the index " + idx);
                }

                return nodeList.item(idx++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static Iterator<Object> createPrimitiveArrayIterator(Object array) {
        return new Iterator<>() {
            private int idx;

            public boolean hasNext() {
                return idx < Array.getLength(array);
            }

            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException(
                            "no more element available for '" + array + "' at the index " + idx);
                }

                return Array.get(array, idx++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Returns true if the collection contains the specified value
     */
    public static boolean typeCoerceContains(
            TypeConverter typeConverter, Object collectionOrArray, Object value, boolean ignoreCase) {

        // unwrap file
        if (collectionOrArray instanceof WrappedFile<?> wf) {
            collectionOrArray = wf.getBody();
        }
        if (collectionOrArray instanceof StreamCache sc) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                sc.writeTo(bos);
                collectionOrArray = bos.toByteArray();
            } catch (IOException e) {
                // ignore
            } finally {
                sc.reset();
            }
        }
        // favor String types
        if (value instanceof StringBuffer || value instanceof StringBuilder) {
            value = value.toString();
        }
        if (collectionOrArray instanceof StringBuffer || collectionOrArray instanceof StringBuilder) {
            collectionOrArray = collectionOrArray.toString();
        }
        if (collectionOrArray instanceof byte[] arr) {
            collectionOrArray = new String(arr);
        }
        if (collectionOrArray instanceof Collection) {
            Collection<?> collection = (Collection<?>) collectionOrArray;
            if (ignoreCase) {
                String lower = value.toString().toLowerCase(Locale.ENGLISH);
                return collection.stream().anyMatch(c -> c.toString().toLowerCase(Locale.ENGLISH).contains(lower));
            } else {
                return collection.contains(value);
            }
        } else if (collectionOrArray instanceof String) {
            String str = (String) collectionOrArray;
            String subStr;
            if (value instanceof String) {
                subStr = (String) value;
            } else {
                subStr = typeConverter.tryConvertTo(String.class, value);
            }
            if (subStr != null) {
                if (ignoreCase) {
                    String lower = subStr.toLowerCase(Locale.ENGLISH);
                    return str.toLowerCase(Locale.ENGLISH).contains(lower);
                } else {
                    return str.contains(subStr);
                }
            }
        }

        Iterator<?> iter = createIterator(collectionOrArray);
        while (iter.hasNext()) {
            if (typeCoerceEquals(typeConverter, value, iter.next(), ignoreCase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An {@link Iterator} to split an input {@code String} content according to a specific {@code String} literal as
     * separator.
     */
    private static class StringIterator implements Iterator<String> {

        /**
         * Flag indicating that the indexes have already been computed.
         */
        private boolean computed;
        /**
         * The current {@code from} index.
         */
        private int from;
        /**
         * The current {@code to} index.
         */
        private int to;
        /**
         * The content to split.
         */
        private final String content;
        /**
         * The separator to use when splitting the content.
         */
        private final String separator;
        /**
         * The length of the separator.
         */
        private final int separatorLength;
        /**
         * The length of the part of the content to split.
         */
        private final int contentLength;

        /**
         * Construct a {@code StringIterator} with the specified content and separator.
         *
         * @param content   the content to split.
         * @param separator the separator to use when splitting the content.
         */
        StringIterator(String content, String separator) {
            this.content = content;
            this.separator = separator;
            this.separatorLength = separator.length();
            boolean skipStart = content.startsWith(separator);
            boolean skipEnd = content.endsWith(separator);
            if (skipStart && skipEnd) {
                this.from = separatorLength;
                this.contentLength = content.length() - separatorLength;
            } else if (skipStart) {
                this.from = separatorLength;
                this.contentLength = content.length();
            } else if (skipEnd) {
                this.contentLength = content.length() - separatorLength;
            } else {
                this.contentLength = content.length();
            }
        }

        @Override
        public boolean hasNext() {
            if (computed) {
                return to != -1;
            } else if (to == -1) {
                return false;
            }
            int index = content.indexOf(separator, from);
            if (index == -1 || index == contentLength) {
                to = contentLength;
            } else {
                to = index;
            }
            computed = true;
            return true;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String answer;
            if (to == contentLength) {
                answer = content.substring(from, contentLength);
                to = -1;
            } else {
                answer = content.substring(from, to);
                from = to + separatorLength;
            }
            computed = false;
            return answer;
        }
    }

    /**
     * An {@link Iterator} to split an input {@code String} content according to a specific pattern as separator.
     */
    private static class StringIteratorForPattern implements Iterator<String> {

        /**
         * Flag indicating that the indexes have already been computed.
         */
        private boolean computed;
        /**
         * The current {@code from} index.
         */
        private int from;
        /**
         * The current {@code to} index.
         */
        private int to;
        /**
         * The content to split.
         */
        private final String content;
        /**
         * The matcher that will match the content to split against the pattern used as separator.
         */
        private final Matcher matcher;
        /**
         * The length of the part of the content to split.
         */
        private int contentLength;

        /**
         * Construct a {@code StringIterator} with the specified content and separator.
         *
         * @param content the content to split.
         * @param pattern the pattern to use when splitting the content.
         */
        StringIteratorForPattern(String content, String pattern) {
            this.content = content;
            this.matcher = Pattern.compile(pattern).matcher(content);
            matcher.useTransparentBounds(true);
            matcher.useAnchoringBounds(false);
            this.contentLength = content.length();
        }

        @Override
        public boolean hasNext() {
            for (;;) {
                if (computed) {
                    return to != -1;
                } else if (to == -1) {
                    return false;
                }
                if (matcher.find(from)) {
                    to = matcher.start();
                    if (from == to) {
                        from = matcher.end();
                        continue;
                    } else if (matcher.end() == contentLength) {
                        contentLength = to;
                    }
                } else {
                    to = contentLength;
                }
                computed = true;
                return true;
            }
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String answer;
            if (to == contentLength) {
                answer = content.substring(from, contentLength);
                to = -1;
            } else {
                answer = content.substring(from, to);
                from = matcher.end();
            }
            computed = false;
            return answer;
        }
    }
}
