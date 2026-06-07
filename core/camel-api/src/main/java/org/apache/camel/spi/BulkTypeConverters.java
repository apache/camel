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
package org.apache.camel.spi;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Ordered;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.jspecify.annotations.Nullable;

/**
 * A source-code-generated, compile-time-optimized bundle of {@link TypeConverter} instances for use with the
 * <a href="https://camel.apache.org/manual/type-converter.html">type conversion</a> infrastructure.
 * <p/>
 * During the Camel build phase, all classes annotated with {@link org.apache.camel.Converter} in a module are collected
 * and merged into a single generated class implementing this interface. At runtime the {@link TypeConverterRegistry}
 * registers each such bulk class once via {@link TypeConverterRegistry#addBulkTypeConverters(BulkTypeConverters)},
 * replacing the overhead of per-pair hash map lookups with a single polymorphic dispatch through the generated
 * {@link #convertTo(Class, Class, org.apache.camel.Exchange, Object)} method, significantly reducing overhead for
 * high-throughput routes.
 *
 * @see   TypeConverterRegistry
 * @see   org.apache.camel.Converter
 * @since 3.7
 */
public interface BulkTypeConverters extends Ordered, TypeConverter {

    /**
     * Performs a lookup for a given type converter.
     *
     * @param  toType   the type to convert to
     * @param  fromType the type to convert from
     * @return          the type converter or <tt>null</tt> if not found.
     */
    @Nullable
    TypeConverter lookup(Class<?> toType, Class<?> fromType);

    /**
     * Converts the value to the specified type in the context of an exchange
     * <p/>
     * Used when conversion requires extra information from the current exchange (such as encoding).
     *
     * @param  from                    the from type
     * @param  to                      the to type
     * @param  exchange                the current exchange
     * @param  value                   the value to be converted
     * @return                         the converted value, <tt>null</tt> if no converter can covert this, or
     *                                 <tt>Void.class</tt> if a converter converted the value to null and was allowed to
     *                                 return null.
     * @throws TypeConversionException is thrown if error during type conversion
     */
    <T> @Nullable T convertTo(Class<?> from, Class<T> to, @Nullable Exchange exchange, @Nullable Object value)
            throws TypeConversionException;

    /**
     * Tries to convert the value to the specified type, returning <tt>null</tt> if not possible to convert.
     * <p/>
     * This method will <b>not</b> throw an exception if an exception occurred during conversion.
     *
     * @param  from  the from type
     * @param  to    the to type
     * @param  value the value to be converted
     * @return       the converted value, or <tt>null</tt> if not possible to convert
     */
    default <T> @Nullable T tryConvertTo(Class<?> from, Class<T> to, @Nullable Exchange exchange, @Nullable Object value)
            throws TypeConversionException {
        try {
            Object t = convertTo(from, to, exchange, value);
            if (t == Void.class) {
                return null;
            }
            return (T) t;
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Converts the value to the specified type in the context of an exchange
     * <p/>
     * Used when conversion requires extra information from the current exchange (such as encoding).
     *
     * @param  from                               the from type
     * @param  to                                 the to type
     * @param  exchange                           the current exchange
     * @param  value                              the value to be converted
     * @return                                    the converted value, is never <tt>null</tt>
     * @throws TypeConversionException            is thrown if error during type conversion
     * @throws NoTypeConversionAvailableException if no type converters exists to convert to the given type
     */
    @SuppressWarnings("NullAway")
    default <T> T mandatoryConvertTo(Class<?> from, Class<T> to, @Nullable Exchange exchange, @Nullable Object value)
            throws TypeConversionException, NoTypeConversionAvailableException {
        Object t = convertTo(from, to, exchange, value);
        if (t == Void.class) {
            return null;
        } else if (t == null) {
            throw new NoTypeConversionAvailableException(value, to);
        } else {
            return (T) t;
        }
    }

    /**
     * Number of type converters included
     */
    int size();

    @Override
    default int getOrder() {
        return 0;
    }

    @Override
    default boolean allowNull() {
        return false;
    }

    @Override
    default <T> @Nullable T convertTo(Class<T> type, @Nullable Object value) throws TypeConversionException {
        if (value == null) {
            return null;
        }
        return convertTo(value.getClass(), type, null, value);
    }

    @Override
    default <T> @Nullable T convertTo(Class<T> type, @Nullable Exchange exchange, @Nullable Object value)
            throws TypeConversionException {
        if (value == null) {
            return null;
        }
        return convertTo(value.getClass(), type, exchange, value);
    }

    @Override
    default <T> T mandatoryConvertTo(Class<T> type, @Nullable Object value)
            throws TypeConversionException, NoTypeConversionAvailableException {
        return mandatoryConvertTo(value != null ? value.getClass() : type, type, null, value);
    }

    @Override
    default <T> T mandatoryConvertTo(Class<T> type, @Nullable Exchange exchange, @Nullable Object value)
            throws TypeConversionException, NoTypeConversionAvailableException {
        return mandatoryConvertTo(value != null ? value.getClass() : type, type, exchange, value);
    }

    @Override
    default <T> @Nullable T tryConvertTo(Class<T> type, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        return tryConvertTo(value.getClass(), type, null, value);
    }

    @Override
    default <T> @Nullable T tryConvertTo(Class<T> type, @Nullable Exchange exchange, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        return tryConvertTo(value.getClass(), type, exchange, value);
    }
}
