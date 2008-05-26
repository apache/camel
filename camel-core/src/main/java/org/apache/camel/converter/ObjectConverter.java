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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.Converter;

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
     * Creates an iterator over the value if the value is a collection, an
     * Object[] or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     */
    @Converter
    public static Iterator iterator(Object value) {
        if (value == null) {
            return Collections.EMPTY_LIST.iterator();
        } else if (value instanceof Collection) {
            Collection collection = (Collection)value;
            return collection.iterator();
        } else if (value.getClass().isArray()) {
            // TODO we should handle primitive array types?
            List<Object> list = Arrays.asList((Object[]) value);
            return list.iterator();
        } else if (value instanceof NodeList) {
            // lets iterate through DOM results after performing XPaths
            final NodeList nodeList = (NodeList) value;
            return new Iterator<Node>() {
                int idx = -1;

                public boolean hasNext() {
                    return ++idx < nodeList.getLength();
                }

                public Node next() {
                    return nodeList.item(idx);
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        } else {
            return Collections.singletonList(value).iterator();
        }
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
        if (value instanceof Boolean) {
            return (Boolean)value;
        }
        if (value instanceof String) {
            return "true".equalsIgnoreCase(value.toString()) ? Boolean.TRUE : Boolean.FALSE;
        }
        return null;
    }

    /**
     * Returns the boolean value, or null if the value is null
     */
    @Converter
    public static Boolean toBoolean(Boolean value) {
        if (value != null) {
            return value.booleanValue();
        }
        return false;
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
            return Byte.parseByte((String) value);
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
            return number.shortValue();
        } else if (value instanceof String) {
            return Short.parseShort((String) value);
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
            return Integer.parseInt((String) value);
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
            return Long.parseLong((String) value);
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
            return number.floatValue();
        } else if (value instanceof String) {
            return Float.parseFloat((String) value);
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
            return number.doubleValue();
        } else if (value instanceof String) {
            return Double.parseDouble((String) value);
        } else {
            return null;
        }
    }



}
