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

/**
 * Bulk type converters that often comes out of the box with Apache Camel. Camel does a build phase where the Camel
 * artifacts are scanned for {@link org.apache.camel.Converter}s and then bulked together into a single source code
 * generated class. This class is then used at runtime as an optimized and really fast way of using all those type
 * converters by the {@link TypeConverterRegistry}.
 */
public interface BulkTypeConverters extends Ordered, TypeConverter {

    /**
     * Performs a lookup for a given type converter.
     *
     * @param  toType   the type to convert to
     * @param  fromType the type to convert from
     * @return          the type converter or <tt>null</tt> if not found.
     */
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
    <T> T convertTo(Class<?> from, Class<T> to, Exchange exchange, Object value) throws TypeConversionException;

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
    default <T> T tryConvertTo(Class<?> from, Class<T> to, Exchange exchange, Object value) throws TypeConversionException {
        try {
            convertTo(from, to, exchange, value);
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
    default <T> T mandatoryConvertTo(Class<?> from, Class<T> to, Exchange exchange, Object value)
            throws TypeConversionException, NoTypeConversionAvailableException {
        T t = convertTo(from, to, exchange, value);
        if (t == null) {
            throw new NoTypeConversionAvailableException(value, to);
        } else {
            return t;
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
    default <T> T convertTo(Class<T> type, Object value) throws TypeConversionException {
        return convertTo(value.getClass(), type, null, value);
    }

    @Override
    default <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
        return convertTo(value.getClass(), type, exchange, value);
    }

    @Override
    default <T> T mandatoryConvertTo(Class<T> type, Object value)
            throws TypeConversionException, NoTypeConversionAvailableException {
        return mandatoryConvertTo(value.getClass(), type, null, value);
    }

    @Override
    default <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value)
            throws TypeConversionException, NoTypeConversionAvailableException {
        return mandatoryConvertTo(value.getClass(), type, exchange, value);
    }

    @Override
    default <T> T tryConvertTo(Class<T> type, Object value) {
        return tryConvertTo(value.getClass(), type, null, value);
    }

    @Override
    default <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
        return tryConvertTo(value.getClass(), type, exchange, value);
    }
}
