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
package org.apache.camel.converter;

import java.util.Collection;
import java.util.Iterator;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * Some core java.lang based <a
 * href="http://camel.apache.org/type-converter.html">Type Converters</a>
 *
 * @version 
 */
@Converter
public final class ObjectConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectConverter() {
    }

    /**
     * @deprecated not in use
     */
    @Deprecated
    public static boolean isCollection(Object value) {
        return value instanceof Collection || (value != null && value.getClass().isArray());
    }

    /**
     * Converts the given value to a boolean, handling strings or Boolean
     * objects; otherwise returning false if the value could not be converted to
     * a boolean
     */
    @Converter
    public static boolean toBool(Object value) {
        Boolean answer = toBoolean(value);
        return answer != null && answer;
    }

    /**
     * Converts the given value to a Boolean, handling strings or Boolean
     * objects; otherwise returning null if the value cannot be converted to a
     * boolean
     */
    @Converter
    public static Boolean toBoolean(Object value) {
        return ObjectHelper.toBoolean(value);
    }

    /**
     * Creates an iterator over the value
     */
    @Converter
    public static Iterator<?> iterator(Object value) {
        return ObjectHelper.createIterator(value);
    }

    /**
     * Creates an iterable over the value
     */
    @Converter
    public static Iterable<?> iterable(Object value) {
        return ObjectHelper.createIterable(value);
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter
    public static Byte toByte(Object value) {
        if (value instanceof Byte) {
            return (Byte) value;
        } else if (value instanceof Number) {
            Number number = (Number) value;
            return number.byteValue();
        } else if (value instanceof String) {
            return Byte.valueOf((String) value);
        } else {
            return null;
        }
    }

    @Converter
    public static char[] toCharArray(String value) {
        return value.toCharArray();
    }

    @Converter
    public static Character toCharacter(String value) {
        return toChar(value);
    }

    @Converter
    public static char toChar(String value) {
        // must be string with the length of 1
        if (value.length() != 1) {
            throw new IllegalArgumentException("String must have exactly a length of 1: " + value);
        }
        return value.charAt(0);
    }

    @Converter
    public static String fromCharArray(char[] value) {
        return new String(value);
    }
    
    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter
    public static Class<?> toClass(Object value, Exchange exchange) {
        if (value instanceof Class) {
            return (Class<?>) value;
        } else if (value instanceof String) {
            // prefer to use class resolver API
            if (exchange != null) {
                return exchange.getContext().getClassResolver().resolveClass((String) value);
            } else {
                return ObjectHelper.loadClass((String) value);
            }
        } else {
            return null;
        }
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter
    public static Short toShort(Object value) {
        if (value instanceof Short) {
            return (Short) value;
        } else if (value instanceof Number) {
            Number number = (Number) value;
            return number.shortValue();
        } else if (value instanceof String) {
            return Short.valueOf((String) value);
        } else {
            return null;
        }
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter
    public static Integer toInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            Number number = (Number) value;
            return number.intValue();
        } else if (value instanceof String) {
            return Integer.valueOf((String) value);
        } else {
            return null;
        }
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter
    public static Long toLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            Number number = (Number) value;
            return number.longValue();
        } else if (value instanceof String) {
            return Long.valueOf((String) value);
        } else {
            return null;
        }
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter
    public static Float toFloat(Object value) {
        if (value instanceof Float) {
            return (Float) value;
        } else if (value instanceof Number) {
            if (ObjectHelper.isNaN(value)) {
                return Float.NaN;
            }
            Number number = (Number) value;
            return number.floatValue();
        } else if (value instanceof String) {
            return Float.valueOf((String) value);
        } else {
            return null;
        }
    }

    /**
     * Returns the converted value, or null if the value is null
     */
    @Converter
    public static Double toDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            if (ObjectHelper.isNaN(value)) {
                return Double.NaN;
            }
            Number number = (Number) value;
            return number.doubleValue();
        } else if (value instanceof String) {
            return Double.valueOf((String) value);
        } else {
            return null;
        }
    }

    // add fast type converters from most common used

    @Converter
    public static String toString(Integer value) {
        return value.toString();
    }

    @Converter
    public static String toString(Long value) {
        return value.toString();
    }

    @Converter
    public static String toString(Boolean value) {
        return value.toString();
    }

    @Converter
    public static String toString(StringBuffer value) {
        return value.toString();
    }

    @Converter
    public static String toString(StringBuilder value) {
        return value.toString();
    }

    @Converter
    public static Integer toInteger(String value) {
        return Integer.valueOf(value);
    }

    @Converter
    public static Long toLong(String value) {
        return Long.valueOf(value);
    }

    @Converter
    public static Float toFloat(String value) {
        return Float.valueOf(value);
    }

    @Converter
    public static Double toDouble(String value) {
        return Double.valueOf(value);
    }

    @Converter
    public static Boolean toBoolean(String value) {
        return Boolean.parseBoolean(value);
    }

}
