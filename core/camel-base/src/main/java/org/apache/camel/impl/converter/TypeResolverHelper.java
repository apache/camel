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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConvertible;
import org.apache.camel.util.ObjectHelper;

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
        if (typeConverter == CoreTypeConverterRegistry.MISS_CONVERTER) {
            // we have previously found no type converter for this pair of types
            return null;
        }
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
            if (objConverter != null && objConverter != CoreTypeConverterRegistry.MISS_CONVERTER) {
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
            if (entry.getValue() == CoreTypeConverterRegistry.MISS_CONVERTER) {
                continue;
            }
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
     * Try to resolve the TypeConverter by looking for a converter from a super type (super class or interface) of the
     * "from" type.
     * <p>
     * The type hierarchy is traversed breadth-first, so the nearest super type wins, and at each level the interfaces
     * are tried before the super class. {@link Object} is tried last. This makes the resolution deterministic, as it
     * does not depend on the iteration order of the converters map.
     *
     * @param  typeConvertible the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryMatch(
            TypeConvertible<?, ?> typeConvertible, Map<TypeConvertible<?, ?>, TypeConverter> converters) {
        return tryHierarchy(typeConvertible.getFrom(), typeConvertible.getTo(), converters);
    }

    /**
     * Try to resolve the TypeConverter by looking for a converter from a super type of the "from" type, taking into
     * consideration that the target type may be a primitive type.
     *
     * @param  typeConvertible the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryPrimitive(
            TypeConvertible<?, ?> typeConvertible, Map<TypeConvertible<?, ?>, TypeConverter> converters) {
        Class<?> to = ObjectHelper.convertPrimitiveTypeToWrapperType(typeConvertible.getTo());
        return tryHierarchy(typeConvertible.getFrom(), to, converters);
    }

    private static TypeConverter tryHierarchy(
            Class<?> from, Class<?> to, Map<TypeConvertible<?, ?>, TypeConverter> converters) {
        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (type == Object.class || !visited.add(type)) {
                continue;
            }
            TypeConverter answer = getConverter(type, to, converters);
            if (answer != null) {
                return answer;
            }
            Collections.addAll(queue, type.getInterfaces());
            if (type.getSuperclass() != null) {
                queue.add(type.getSuperclass());
            }
        }
        // the least specific type is tried last
        return from.isInterface() ? null : getConverter(Object.class, to, converters);
    }

    private static TypeConverter getConverter(
            Class<?> from, Class<?> to, Map<TypeConvertible<?, ?>, TypeConverter> converters) {
        TypeConverter answer = converters.get(new TypeConvertible<>(from, to));
        return answer != CoreTypeConverterRegistry.MISS_CONVERTER ? answer : null;
    }

}
