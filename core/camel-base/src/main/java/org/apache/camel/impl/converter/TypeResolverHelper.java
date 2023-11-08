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

import java.util.Map;

import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConvertible;

/**
 * Helper methods for resolving the type conversions. This is an internal API and not meant for public usages.
 * <p>
 * In a broader sense: the methods of this code help with traversing the class hierarchy of the types involved in a
 * conversion, so that the correct TypeConverter can be used. This is a helper class to CoreTypeConverterRegistry.
 * <p>
 * In the CoreTypeConverterRegistry class, the registry of types if maintained in a ConcurrentMap that associates a type
 * pair with the resolver for it (i.e.: it associates pair representing a conversion from String to Integer to a type
 * converter - such as CamelBaseBulkConverterLoader).
 * <p>
 * NOTE 1: a lot of this code is in the hot path of the core engine, so change with extreme caution to prevent
 * performance issues on the core code.
 * <p>
 * NOTE 2: also, a lot of this code runs rather slow operations, so calling these methods should be avoided as much as
 * possible
 *
 */
final class TypeResolverHelper {
    private TypeResolverHelper() {

    }

    /**
     * Lookup the type converter in the registry (given a type to convert to and a type to convert from, along with a
     * mapping of all known converters)
     *
     * @param  toType     the type to convert to
     * @param  fromType   the type to convert from
     * @param  converters the map of all known converters
     * @return            the type converter or null if unknown
     */
    static TypeConverter doLookup(
            Class<?> toType, Class<?> fromType, Map<TypeConvertible<?, ?>, TypeConverter> converters) {
        return doLookup(new TypeConvertible<>(fromType, toType), converters);
    }

    private static TypeConverter doLookup(
            TypeConvertible<?, ?> typeConvertible, Map<TypeConvertible<?, ?>, TypeConverter> converters) {

        // try with base converters first
        final TypeConverter typeConverter = converters.get(typeConvertible);
        if (typeConverter != null) {
            return typeConverter;
        }

        final TypeConverter superConverterTc = tryMatch(typeConvertible, converters);
        if (superConverterTc != null) {
            return superConverterTc;
        }

        final TypeConverter primitiveAwareConverter = tryPrimitive(typeConvertible, converters);
        if (primitiveAwareConverter != null) {
            return primitiveAwareConverter;
        }

        // only do these tests as fallback and only on the target type
        if (!typeConvertible.getFrom().equals(Object.class)) {

            final TypeConverter assignableConverter
                    = tryAssignableFrom(typeConvertible, converters);
            if (assignableConverter != null) {
                return assignableConverter;
            }

            final TypeConverter objConverter = converters.get(new TypeConvertible<>(Object.class, typeConvertible.getTo()));
            if (objConverter != null) {
                return objConverter;
            }
        }

        // none found
        return null;
    }

    /**
     * Try the base converters. That is, those matching a direct conversion (i.e.: when the from and to types requested
     * do exist on the converters' map OR when the from and to types requested match for a _primitive type).
     * <p>
     * For instance: From String.class, To: int.class (would match a method such as myConverter(String, Integer) or
     * myConverter(String, int).
     *
     * @param  typeConvertible the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryAssignableFrom(
            TypeConvertible<?, ?> typeConvertible, Map<TypeConvertible<?, ?>, TypeConverter> converters) {

        /*
         Let's try classes derived from this toType: basically it traverses the entries looking for assignable types
         matching both the "from type" and the "to type" which are NOT Object (we usually try this later).
         */
        for (var entry : converters.entrySet()) {
            final TypeConvertible<?, ?> key = entry.getKey();
            if (key.isAssignableMatch(typeConvertible)) {
                return entry.getValue();
            } else {
                if (typeConvertible.isAssignableMatch(key)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Try to resolve the TypeConverter by forcing a costly and slow recursive check.
     *
     * @param  typeConvertible the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryMatch(
            TypeConvertible<?, ?> typeConvertible, Map<TypeConvertible<?, ?>, TypeConverter> converters) {
        for (var entry : converters.entrySet()) {
            if (entry.getKey().matches(typeConvertible)) {
                return entry.getValue();
            }

        }

        return null;
    }

    /**
     * Try to resolve the TypeConverter by forcing a costly and slow recursive check that takes into consideration that
     * the target type may have a primitive data type
     *
     * @param  typeConvertible the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryPrimitive(
            TypeConvertible<?, ?> typeConvertible, Map<TypeConvertible<?, ?>, TypeConverter> converters) {
        for (var entry : converters.entrySet()) {
            if (entry.getKey().matchesPrimitive(typeConvertible)) {
                return entry.getValue();
            }

        }

        return null;
    }

}
