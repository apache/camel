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
import org.apache.camel.util.ObjectHelper;

/**
 * Some core java.lang based <a
 * href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 *
 * @version $Revision$
 */
@Converter
public final class ObjectConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectConverter() {
    }

    public static boolean isCollection(Object value) {
        // TODO we should handle primitive array types?
        return value instanceof Collection || (value != null && value.getClass().isArray());
    }

    /**
     * Creates an iterator over the value
     *
     * @deprecated use {@link org.apache.camel.util.ObjectHelper#createIterator(Object)}. Will be removed in Camel 2.0.
     */
    @SuppressWarnings("unchecked")
    @Converter
    @Deprecated
    public static Iterator iterator(Object value) {
        return ObjectHelper.createIterator(value);
    }

    /**
     * Converts the given value to a boolean, handling strings or Boolean
     * objects; otherwise returning false if the value could not be converted to
     * a boolean
     */
    @Converter
    public static boolean toBool(Object value) {
        Boolean answer = toBoolean(value);
        if (answer != null) {
            return answer.booleanValue();
        }
        return false;
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
     * Returns the boolean value, or null if the value is null
     */
    @Converter
    public static Boolean toBoolean(Boolean value) {
        if (value != null) {
            return value;
        }
        return Boolean.FALSE;
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
            return Byte.valueOf(number.byteValue());
        } else if (value instanceof String) {
            return Byte.valueOf((String) value);
        } else {
            return null;
        }
    }

    @Converter
    public static byte[] toByteArray(String value) {
        return value.getBytes();
    }

    @Converter
    public static char[] toCharArray(String value) {
        return value.toCharArray();
    }

    @Converter
    public static String fromCharArray(char[] value) {
        return new String(value);
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
            return Short.valueOf(number.shortValue());
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
            return Integer.valueOf(number.intValue());
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
            return Long.valueOf(number.longValue());
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
            Number number = (Number) value;
            return Float.valueOf(number.floatValue());
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
            Number number = (Number) value;
            return Double.valueOf(number.doubleValue());
        } else if (value instanceof String) {
            return Double.valueOf((String) value);
        } else {
            return null;
        }
    }



}
