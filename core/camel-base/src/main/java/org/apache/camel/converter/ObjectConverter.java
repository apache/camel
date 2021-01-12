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
package org.apache.camel.converter;

import java.math.BigInteger;
import java.util.Iterator;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.support.ObjectHelper;

/**
 * Some core java.lang based <a href="http://camel.apache.org/type-converter.html">Type Converters</a>
 */
@Converter(generateBulkLoader = true)
public final class ObjectConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectConverter() {
    }

    /**
     * Converts the given value to a boolean, handling strings or Boolean objects; otherwise returning false if the
     * value could not be converted to a boolean
     */
    @Converter(order = 1)
    public static boolean toBool(Object value) {
        Boolean answer = toBoolean(value);
        if (answer == null) {
            throw new IllegalArgumentException("Cannot convert type: " + value.getClass().getName() + " to boolean");
        }
        return answer;
    }

    /**
     * Converts the given value to a Boolean, handling strings or Boolean objects; otherwise returning null if the value
     * cannot be converted to a boolean
     */
    @Converter(order = 2)
    public static Boolean toBoolean(Object value) {
        return org.apache.camel.util.ObjectHelper.toBoolean(value);
    }

    /**
     * Creates an iterator over the value
     */
    @Converter(order = 3)
    public static Iterator<?> iterator(Object value) {
        return ObjectHelper.createIterator(value);
    }

    /**
     * Creates an iterable over the value
     */
    @Converter(order = 4)
    public static Iterable<?> iterable(Object value) {
        return ObjectHelper.createIterable(value);
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter(order = 5, allowNull = true)
    public static Byte toByte(Number value) {
        if (org.apache.camel.util.ObjectHelper.isNaN(value)) {
            return null;
        }
        return value.byteValue();
    }

    @Converter(order = 6)
    public static Byte toByte(String value) {
        return Byte.valueOf(value);
    }

    @Converter(order = 7)
    public static char[] toCharArray(String value) {
        return value.toCharArray();
    }

    @Converter(order = 8)
    public static Character toCharacter(String value) {
        return toChar(value);
    }

    @Converter(order = 9)
    public static char toChar(String value) {
        // must be string with the length of 1
        if (value.length() != 1) {
            throw new IllegalArgumentException("String must have exactly a length of 1: " + value);
        }
        return value.charAt(0);
    }

    @Converter(order = 10)
    public static String fromCharArray(char[] value) {
        return new String(value);
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter(order = 11)
    public static Class<?> toClass(String value, Exchange exchange) {
        // prefer to use class resolver API
        if (exchange != null) {
            return exchange.getContext().getClassResolver().resolveClass(value);
        } else {
            return org.apache.camel.util.ObjectHelper.loadClass(value);
        }
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter(order = 12, allowNull = true)
    public static Short toShort(Number value) {
        if (org.apache.camel.util.ObjectHelper.isNaN(value)) {
            return null;
        }
        return value.shortValue();
    }

    @Converter(order = 13)
    public static Short toShort(String value) {
        return Short.valueOf(value);
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter(order = 14, allowNull = true)
    public static Integer toInteger(Number value) {
        if (org.apache.camel.util.ObjectHelper.isNaN(value)) {
            return null;
        }
        return value.intValue();
    }

    @Converter(order = 15)
    public static Integer toInteger(String value) {
        return Integer.valueOf(value);
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter(order = 16, allowNull = true)
    public static Long toLong(Number value) {
        if (org.apache.camel.util.ObjectHelper.isNaN(value)) {
            return null;
        }
        return value.longValue();
    }

    @Converter(order = 17)
    public static Long toLong(String value) {
        return Long.valueOf(value);
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter(order = 18, allowNull = true)
    public static BigInteger toBigInteger(Object value) {
        if (org.apache.camel.util.ObjectHelper.isNaN(value)) {
            return null;
        }
        if (value instanceof String) {
            return new BigInteger((String) value);
        }

        Long num = null;
        if (value instanceof Number) {
            Number number = (Number) value;
            num = number.longValue();
        }
        if (num != null) {
            return BigInteger.valueOf(num);
        } else {
            return null;
        }
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter(order = 19)
    public static Float toFloat(Number value) {
        if (org.apache.camel.util.ObjectHelper.isNaN(value)) {
            return Float.NaN;
        }
        return value.floatValue();
    }

    @Converter(order = 20)
    public static Float toFloat(String value) {
        return Float.valueOf(value);
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter(order = 21)
    public static Double toDouble(Number value) {
        if (org.apache.camel.util.ObjectHelper.isNaN(value)) {
            return Double.NaN;
        }
        return value.doubleValue();
    }

    @Converter(order = 22)
    public static Double toDouble(String value) {
        return Double.valueOf(value);
    }

    // add fast type converters from most common used

    @Converter(order = 23)
    public static String toString(Integer value) {
        return value.toString();
    }

    @Converter(order = 24)
    public static String toString(Long value) {
        return value.toString();
    }

    @Converter(order = 25)
    public static String toString(Boolean value) {
        return value.toString();
    }

    @Converter(order = 26)
    public static String toString(StringBuffer value) {
        return value.toString();
    }

    @Converter(order = 27)
    public static String toString(StringBuilder value) {
        return value.toString();
    }

    @Converter(order = 28)
    public static Boolean toBoolean(String value) {
        return org.apache.camel.util.ObjectHelper.toBoolean(value);
    }

    @Converter(order = 29)
    public static Number toNumber(String text) {
        // what kind of numeric is it
        boolean dot = text.indexOf('.') != -1;
        if (dot) {
            return Double.parseDouble(text);
        } else {
            // its either a long or integer value (lets just avoid bytes)
            long lon = Long.parseLong(text);
            if (lon < Integer.MAX_VALUE) {
                return Integer.valueOf(text);
            } else {
                return lon;
            }
        }
    }

}
