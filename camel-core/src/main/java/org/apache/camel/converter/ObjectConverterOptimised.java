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

import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * Optimised {@link ObjectConverter}
 */
public final class ObjectConverterOptimised {

    private ObjectConverterOptimised() {
    }

    public static Object convertTo(final Class<?> type, final Exchange exchange, final Object value) throws Exception {
        // converting to a String is very common
        if (type == String.class) {
            Class fromType = value.getClass();
            if (fromType == boolean.class || fromType == Boolean.class) {
                return value.toString();
            } else if (fromType == int.class || fromType == Integer.class) {
                return value.toString();
            } else if (fromType == long.class || fromType == Long.class) {
                return value.toString();
            } else if (fromType == char[].class) {
                return ObjectConverter.fromCharArray((char[]) value);
            } else if (fromType == StringBuffer.class || fromType == StringBuilder.class) {
                return value.toString();
            }
            return null;
        }

        if (type == boolean.class || type == Boolean.class) {
            return ObjectConverter.toBoolean(value);
        } else if (type == int.class || type == Integer.class) {
            return ObjectConverter.toInteger(value);
        } else if (type == long.class || type == Long.class) {
            return ObjectConverter.toLong(value);
        } else if (type == byte.class || type == Byte.class) {
            return ObjectConverter.toByte(value);
        } else if (type == double.class || type == Double.class) {
            return ObjectConverter.toDouble(value);
        } else if (type == float.class || type == Float.class) {
            return ObjectConverter.toFloat(value);
        } else if (type == short.class || type == Short.class) {
            return ObjectConverter.toShort(value);
        } else if ((type == char.class || type == Character.class) && value.getClass() == String.class) {
            return ObjectConverter.toCharacter((String) value);
        } else if ((type == char[].class || type == Character[].class) && value.getClass() == String.class) {
            return ObjectConverter.toCharArray((String) value);
        }

        if (type == Iterator.class) {
            return ObjectHelper.createIterator(value);
        } else if (type == Iterable.class) {
            return ObjectHelper.createIterable(value);
        }

        if (type == Class.class) {
            return ObjectConverter.toClass(value, exchange);
        }

        // no optimised type converter found
        return null;
    }

}
