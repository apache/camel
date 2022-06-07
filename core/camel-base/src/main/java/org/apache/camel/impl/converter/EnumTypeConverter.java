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
package org.apache.camel.impl.converter;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * A type converter which is used to convert from String to enum type
 */
public class EnumTypeConverter extends TypeConverterSupport {

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        return doConvertTo(type, exchange, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T doConvertTo(Class<T> type, Exchange exchange, Object value) {
        if (type.isEnum()) {
            // is there a direct enum type converter
            TypeConverterRegistry tcr = exchange != null ? exchange.getContext().getTypeConverterRegistry() : null;
            if (tcr != null) {
                Class<?> fromType = value.getClass();
                TypeConverter tc = tcr.lookup(type, value.getClass());
                if (tc == null) {
                    // no direct converter but the enum may be a wrapper/primitive variant so try to lookup again
                    Class<?> primitiveType = ObjectHelper.convertWrapperTypeToPrimitiveType(fromType);
                    if (fromType != primitiveType) {
                        tc = tcr.lookup(type, primitiveType);
                    }
                }
                if (tc != null) {
                    return tc.convertTo(type, exchange, value);
                }
            }

            // convert to enum via its string based enum constant
            String text = value.toString();
            Class<Enum<?>> enumClass = (Class<Enum<?>>) type;

            // we want to match case insensitive for enums
            for (Enum<?> enumValue : enumClass.getEnumConstants()) {
                if (enumValue.name().equalsIgnoreCase(text)) {
                    return type.cast(enumValue);
                }
            }

            // add support for using dash or camel cased to common used upper cased underscore style for enum constants
            text = StringHelper.asEnumConstantValue(text);
            for (Enum<?> enumValue : enumClass.getEnumConstants()) {
                if (enumValue.name().equalsIgnoreCase(text)) {
                    return type.cast(enumValue);
                }
            }

            throw new IllegalArgumentException("Enum class " + type + " does not have any constant with value: " + text);
        }

        return null;
    }

}
