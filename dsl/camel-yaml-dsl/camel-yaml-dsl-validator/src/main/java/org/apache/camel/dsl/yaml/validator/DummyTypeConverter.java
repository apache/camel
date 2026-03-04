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
package org.apache.camel.dsl.yaml.validator;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * During parsing then we can be more flexible and allow missing property placeholders to resolve to a hardcoded value,
 * so we can keep attempting to parse.
 */
public class DummyTypeConverter extends TypeConverterSupport {

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
        if (PropertyConfigurerSupport.MAGIC_VALUE.equals(value)) {
            // attempt to convert to the given type using different common inputs (boolean, numeric, string)
            if (boolean.class == type || Boolean.class == type) {
                return (T) Boolean.TRUE;
            } else if (type == int.class || type == Integer.class) {
                return (T) Integer.valueOf("1");
            } else if (type == long.class || type == Long.class) {
                return (T) Long.valueOf("1");
            } else if (type == double.class || type == Double.class) {
                return (T) Double.valueOf("1");
            } else if (type == float.class || type == Float.class) {
                return (T) Float.valueOf("1");
            } else if (type == short.class || type == Short.class) {
                return (T) Short.valueOf("1");
            } else if (type == byte.class || type == Byte.class) {
                return (T) Byte.valueOf("0");
            } else if (type == String.class) {
                return (T) PropertyConfigurerSupport.MAGIC_VALUE;
            }
        } else {
            String s = value.toString();
            // regular values so convert normally
            if (boolean.class == type || Boolean.class == type) {
                return (T) ObjectHelper.toBoolean(s);
            } else if (type == int.class || type == Integer.class) {
                return (T) ObjectConverter.toInteger(s);
            } else if (type == long.class || type == Long.class) {
                return (T) ObjectConverter.toLong(s);
            } else if (type == double.class || type == Double.class) {
                return (T) ObjectConverter.toDouble(s);
            } else if (type == float.class || type == Float.class) {
                return (T) ObjectConverter.toFloat(s);
            } else if (type == short.class || type == Short.class) {
                return (T) ObjectConverter.toShort(s);
            } else if (type == byte.class || type == Byte.class) {
                return (T) ObjectConverter.toByte(s);
            } else if (type == String.class) {
                return (T) s;
            }
        }
        return null;
    }

}
