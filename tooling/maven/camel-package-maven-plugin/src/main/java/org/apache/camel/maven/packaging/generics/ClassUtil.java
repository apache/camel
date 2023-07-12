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
package org.apache.camel.maven.packaging.generics;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Utility classes with respect to the class operations.
 *
 * @author <a href="mailto:gurkanerdogdu@yahoo.com">Gurkan Erdogdu</a>
 * @since  1.0
 */
public final class ClassUtil {
    public static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPERS_MAP;

    static {
        Map<Class<?>, Class<?>> primitiveToWrappersMap = new HashMap<>();
        primitiveToWrappersMap.put(Integer.TYPE, Integer.class);
        primitiveToWrappersMap.put(Float.TYPE, Float.class);
        primitiveToWrappersMap.put(Double.TYPE, Double.class);
        primitiveToWrappersMap.put(Character.TYPE, Character.class);
        primitiveToWrappersMap.put(Long.TYPE, Long.class);
        primitiveToWrappersMap.put(Byte.TYPE, Byte.class);
        primitiveToWrappersMap.put(Short.TYPE, Short.class);
        primitiveToWrappersMap.put(Boolean.TYPE, Boolean.class);
        primitiveToWrappersMap.put(Void.TYPE, Void.class);
        PRIMITIVE_TO_WRAPPERS_MAP = Collections.unmodifiableMap(primitiveToWrappersMap);
    }

    public static final Type[] NO_TYPES = new Type[0];

    /*
     * Private constructor
     */
    private ClassUtil() {
        throw new UnsupportedOperationException();
    }

    public static boolean isSame(Type type1, Type type2) {
        if (type1 instanceof Class && ((Class<?>) type1).isPrimitive()) {
            type1 = PRIMITIVE_TO_WRAPPERS_MAP.get(type1);
        }
        if (type2 instanceof Class && ((Class<?>) type2).isPrimitive()) {
            type2 = PRIMITIVE_TO_WRAPPERS_MAP.get(type2);
        }
        return type1 == type2;
    }

    public static Class<?> getPrimitiveWrapper(Class<?> clazz) {
        return PRIMITIVE_TO_WRAPPERS_MAP.get(clazz);

    }

    /**
     * Gets the class of the given type arguments.
     * <p>
     * If the given type {@link Type} parameters is an instance of the {@link ParameterizedType}, it returns the raw
     * type otherwise it return the casted {@link Class} of the type argument.
     * </p>
     *
     * @param  type class or parametrized type
     * @return
     */
    public static Class<?> getClass(Type type) {
        return getClazz(type);
    }

    /**
     * Returns true if type is an instance of <code>ParameterizedType</code> else otherwise.
     *
     * @param  type type of the artifact
     * @return      true if type is an instance of <code>ParameterizedType</code>
     */
    public static boolean isParameterizedType(Type type) {
        return type instanceof ParameterizedType;
    }

    /**
     * Returns true if type is an instance of <code>WildcardType</code> else otherwise.
     *
     * @param  type type of the artifact
     * @return      true if type is an instance of <code>WildcardType</code>
     */
    public static boolean isWildCardType(Type type) {
        return type instanceof WildcardType;
    }

    /**
     * Returns true if rhs is assignable type to the lhs, false otherwise.
     *
     * @param  lhs left hand side class
     * @param  rhs right hand side class
     * @return     true if rhs is assignable to lhs
     */
    public static boolean isClassAssignableFrom(Class<?> lhs, Class<?> rhs) {
        if (lhs.isPrimitive()) {
            lhs = getPrimitiveWrapper(lhs);
        }

        if (rhs.isPrimitive()) {
            rhs = getPrimitiveWrapper(rhs);
        }

        if (lhs.isAssignableFrom(rhs)) {
            return true;
        }

        return false;
    }

    /**
     * Return raw class type for given type.
     *
     * @param  type base type instance
     * @return      class type for given type
     */
    public static Class<?> getClazz(Type type) {
        if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        } else if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof GenericArrayType arrayType) {
            return Array.newInstance(getClazz(arrayType.getGenericComponentType()), 0).getClass();
        } else if (type instanceof WildcardType wildcardType) {
            Type[] bounds = wildcardType.getUpperBounds();
            if (bounds.length > 1) {
                throw new IllegalArgumentException(
                        "Illegal use of wild card type with more than one upper bound: " + wildcardType);
            } else if (bounds.length == 0) {
                return Object.class;
            } else {
                return getClass(bounds[0]);
            }
        } else if (type instanceof TypeVariable<?> typeVariable) {
            if (typeVariable.getBounds().length > 1) {
                throw new IllegalArgumentException("Illegal use of type variable with more than one bound: " + typeVariable);
            } else {
                Type[] bounds = typeVariable.getBounds();
                if (bounds.length == 0) {
                    return Object.class;
                } else {
                    return getClass(bounds[0]);
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported type " + type);
        }
    }

    public static boolean isRawClassEquals(Type ipType, Type apiType) {
        Class<?> ipClass = getRawPrimitiveType(ipType);
        Class<?> apiClass = getRawPrimitiveType(apiType);

        if (ipClass == null || apiClass == null) {
            // we found some illegal generics
            return false;
        }

        return ipClass.equals(apiClass);
    }

    private static Class<?> getRawPrimitiveType(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isPrimitive()) {
                return getPrimitiveWrapper(clazz);
            }
            return clazz;
        }

        if (type instanceof ParameterizedType) {
            return getRawPrimitiveType(((ParameterizedType) type).getRawType());
        }

        return null;
    }

    /**
     * @param  fqAnnotationName a fully qualified runtime annotation name whose presence on the given class is to be
     *                          checked
     * @param  cl               the class to check
     * @return                  {@code true} if the given {@link Class} is annotated with the given
     *                          <strong>runtime</strong> annotation; {@code false} otherwise
     */
    public static boolean hasAnnotation(String fqAnnotationName, Class<?> cl) {
        return Stream.of(cl.getAnnotations())
                .map(annotation -> annotation.annotationType().getName())
                .anyMatch(fqAnnotationName::equals);
    }
}
