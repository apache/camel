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
import java.util.Optional;

import org.apache.camel.TypeConverter;
import org.apache.camel.converter.TypeConvertable;

import static org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType;

/**
 * Helper methods for resolving the type conversions. This is an internal API and not meant for public usages.
 *
 * In a broader sense: the methods of this code help with traversing the class hierarchy of the types involved in a
 * conversion, so that the correct TypeConverter can be used. This is a helper class to CoreTypeConverterRegistry.
 *
 * In the CoreTypeConverterRegistry class, the registry of types if maintained in a ConcurrentMap that associates a type
 * pair with the resolver for it (i.e.: it associates pair representing a conversion from String to Integer to a type
 * converter - such as CamelBaseBulkConverterLoader).
 *
 * NOTE: a lot of this code is in the hot path of the core engine, so change with extreme caution to prevent performance
 * issues on the core code.
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
     * @param  isSuper    whether is passing the super class of a previously given type
     * @param  converters the map of all known converters
     * @return            the type converter or null if unknown
     */
    static TypeConverter doLookup(
            Class<?> toType, Class<?> fromType, boolean isSuper, Map<TypeConvertable<?, ?>, TypeConverter> converters) {
        return doLookup(new TypeConvertable<>(fromType, toType), isSuper, converters);
    }

    private static TypeConverter doLookup(
            TypeConvertable<?, ?> typeConvertable, boolean isSuper, Map<TypeConvertable<?, ?>, TypeConverter> converters) {
        if (typeConvertable.getFrom() != null) {

            // try with base converters first
            final TypeConverter baseConverters = tryBaseConverters(typeConvertable, converters);
            if (baseConverters != null) {
                return baseConverters;
            }

            // try the interfaces
            final TypeConverter interfaceConverter = tryInterfaceConverters(typeConvertable, converters);
            if (interfaceConverter != null) {
                return interfaceConverter;
            }

            // try super then
            final TypeConverter superConverter = trySuperConverters(typeConvertable, converters);
            if (superConverter != null) {
                return superConverter;
            }
        }

        // only do these tests as fallback and only on the target type (eg not on its super)
        if (!isSuper) {
            if (typeConvertable.getFrom() != null && !typeConvertable.getFrom().equals(Object.class)) {

                final TypeConverter assignableConverter
                        = tryAssignableFrom(typeConvertable.getFrom(), typeConvertable.getTo(), converters);
                if (assignableConverter != null) {
                    return assignableConverter;
                }

                final TypeConverter objConverter = converters.get(new TypeConvertable<>(Object.class, typeConvertable.getTo()));
                if (objConverter != null) {
                    return objConverter;
                }
            }
        }

        // none found
        return null;
    }

    /**
     * Try the base converters. That is, those matching a direct conversion (i.e.: when the from and to types requested
     * do exist on the converters' map OR when the from and to types requested match for a _primitive type).
     *
     * For instance: From String.class, To: int.class (would match a method such as myConverter(String, Integer) or
     * myConverter(String, int).
     *
     * @param  typeConvertable the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryBaseConverters(
            TypeConvertable<?, ?> typeConvertable, Map<TypeConvertable<?, ?>, TypeConverter> converters) {

        final TypeConverter tc = tryDirectMatchConverters(typeConvertable, converters);
        if (tc == null && typeConvertable.getTo().isPrimitive()) {
            return converters
                    .get(new TypeConvertable<>(
                            typeConvertable.getFrom(), convertPrimitiveTypeToWrapperType(typeConvertable.getTo())));
        }

        return tc;
    }

    private static TypeConverter tryAssignableFrom(
            Class<?> fromType, Class<?> toType, Map<TypeConvertable<?, ?>, TypeConverter> converters) {
        /*
         Let's try classes derived from this toType: basically it traverses the entries looking for assignable types
         matching both the "from type" and the "to type" which are NOT Object (we usually try this later).
         */
        final Optional<Map.Entry<TypeConvertable<?, ?>, TypeConverter>> first = converters.entrySet().stream()
                .filter(v -> v.getKey().getTo().isAssignableFrom(toType))
                .filter(v -> !v.getKey().getFrom().equals(Object.class) && v.getKey().getFrom().isAssignableFrom(fromType))
                .findFirst();

        return first.map(Map.Entry::getValue).orElse(null);

    }

    /**
     * Try a direct match conversion (i.e.: those which the type conversion pair have a direct entry on the converters)
     *
     * @param  typeConvertable the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryDirectMatchConverters(
            TypeConvertable<?, ?> typeConvertable, Map<TypeConvertable<?, ?>, TypeConverter> converters) {
        final TypeConverter typeConverter = converters.get(typeConvertable);
        if (typeConverter != null) {
            return typeConverter;
        }

        return null;
    }

    /**
     * Try to resolve a TypeConverter by looking at the parent class of a given "from" type. It looks at the type
     * hierarchy of the "from type" trying to match a suitable converter (i.e.: Integer -> Number). It will recursively
     * analyze the whole hierarchy, and it will also evaluate the interfaces implemented by such type.
     *
     * @param  typeConvertable the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter trySuperConverters(
            TypeConvertable<?, ?> typeConvertable, Map<TypeConvertable<?, ?>, TypeConverter> converters) {

        return doLookup(typeConvertable.getTo(), typeConvertable.getFrom().getSuperclass(), true, converters);
    }

    private static TypeConverter tryInterfaceConverters(
            Class<?> fromType, Class<?> toType, Map<TypeConvertable<?, ?>, TypeConverter> converters) {
        final Class<?>[] interfaceTypes = fromType.getInterfaces();

        for (Class<?> interfaceType : interfaceTypes) {
            final TypeConvertable<?, ?> interfaceTypeConvertable = new TypeConvertable<>(interfaceType, toType);

            TypeConverter typeConverter = tryBaseConverters(interfaceTypeConvertable, converters);
            if (typeConverter != null) {
                return typeConverter;
            } else {
                if (fromType.isArray()) {
                    typeConverter = tryNativeArrayConverters(interfaceTypeConvertable, converters);
                    if (typeConverter != null) {
                        return typeConverter;
                    }
                } else {
                    typeConverter = tryInterfaceConverters(interfaceTypeConvertable, converters);
                    if (typeConverter != null) {
                        return typeConverter;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Try to resolve a TypeConverter by looking at the interfaces implemented by a given "from" type. It looks at the
     * type hierarchy of the "from type" trying to match a suitable converter (i.e.: Integer -> Number). It will
     * recursively analyze the whole hierarchy.
     *
     * @param  typeConvertable the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryInterfaceConverters(
            TypeConvertable<?, ?> typeConvertable, Map<TypeConvertable<?, ?>, TypeConverter> converters) {
        return tryInterfaceConverters(typeConvertable.getFrom(), typeConvertable.getTo(), converters);
    }

    private static TypeConverter tryNativeArrayConverters(
            Class<?> fromType, Class<?> toType, Map<TypeConvertable<?, ?>, TypeConverter> converters) {
        if (fromType.isArray()) {
            // Let the fallback converters handle the primitive arrays
            if (!fromType.getComponentType().isPrimitive()) {
                /* We usually define our converters are receiving an object array (Object[]), however, this won't easily match:
                 * because an object array is not an interface or a super class of other array types (i.e.: not a super class
                 * of String[]). So, we take the direct road here and try check for an object array converter right away.
                 */
                return converters.get(new TypeConvertable<>(Object[].class, toType));
            }
        }

        return null;
    }

    /**
     * Try to resolve a TypeConverter by looking at Array matches for a given "from" type. This also includes evaluating
     * candidates matching a receiver of Object[] type.
     *
     * @param  typeConvertable the type converter pair
     * @param  converters      the map of all known converters
     * @return                 the type converter or null if unknown
     */
    static TypeConverter tryNativeArrayConverters(
            TypeConvertable<?, ?> typeConvertable, Map<TypeConvertable<?, ?>, TypeConverter> converters) {

        return tryNativeArrayConverters(typeConvertable.getFrom(), typeConvertable.getTo(), converters);
    }
}
