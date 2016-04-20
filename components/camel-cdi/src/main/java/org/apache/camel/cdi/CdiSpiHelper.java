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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;

import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.cdi.AnyLiteral.ANY;
import static org.apache.camel.cdi.DefaultLiteral.DEFAULT;

@Vetoed
final class CdiSpiHelper {

    private CdiSpiHelper() {
    }

    static <T extends Annotation> T getQualifierByType(InjectionPoint ip, Class<T> type) {
        return getFirstElementOfType(ip.getQualifiers(), type);
    }

    static <E, T extends E> T getFirstElementOfType(Collection<E> collection, Class<T> type) {
        for (E item : collection) {
            if ((item != null) && type.isAssignableFrom(item.getClass())) {
                return ObjectHelper.cast(type, item);
            }
        }
        return null;
    }

    @SafeVarargs
    static <T> Set<T> excludeElementOfTypes(Set<T> annotations, Class<? extends T>... exclusions) {
        Set<T> set = new HashSet<>();
        for (T annotation : annotations) {
            boolean exclude = false;
            for (Class<? extends T> exclusion : exclusions) {
                if (exclusion.isAssignableFrom(annotation.getClass())) {
                    exclude = true;
                    break;
                }
            }
            if (!exclude) {
                set.add(annotation);
            }
        }
        return set;
    }

    static Predicate<Annotation> isAnnotationType(Class<? extends Annotation> clazz) {
        Objects.requireNonNull(clazz);
        return annotation -> clazz.equals(annotation.annotationType());
    }

    static Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) {
            return Class.class.cast(type);
        } else if (type instanceof ParameterizedType) {
            return getRawType(ParameterizedType.class.cast(type).getRawType());
        } else if (type instanceof TypeVariable<?>) {
            return getBound(TypeVariable.class.cast(type).getBounds());
        } else if (type instanceof WildcardType) {
            return getBound(WildcardType.class.cast(type).getUpperBounds());
        } else if (type instanceof GenericArrayType) {
            Class<?> rawType = getRawType(GenericArrayType.class.cast(type).getGenericComponentType());
            if (rawType != null) {
                return Array.newInstance(rawType, 0).getClass();
            }
        }
        throw new UnsupportedOperationException("Unable to retrieve raw type for [" + type + "]");
    }

    private static Class<?> getBound(Type[] bounds) {
        if (bounds.length == 0) {
            return Object.class;
        } else {
            return getRawType(bounds[0]);
        }
    }

    @SafeVarargs
    static boolean hasAnnotation(AnnotatedType<?> type, Class<? extends Annotation>... annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (hasAnnotation(type, annotation)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasAnnotation(AnnotatedType<?> type, Class<? extends Annotation> annotation) {
        if (type.isAnnotationPresent(annotation)) {
            return true;
        }
        for (AnnotatedMethod<?> method : type.getMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        for (AnnotatedConstructor<?> constructor : type.getConstructors()) {
            if (constructor.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        for (AnnotatedField<?> field : type.getFields()) {
            if (field.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    static Set<Annotation> getQualifiers(Annotated annotated, BeanManager manager) {
        Set<Annotation> qualifiers = new HashSet<>();
        for (Annotation annotation : annotated.getAnnotations()) {
            if (manager.isQualifier(annotation.annotationType())) {
                qualifiers.add(annotation);
            }
        }
        if (qualifiers.isEmpty()) {
            qualifiers.add(DEFAULT);
        }
        qualifiers.add(ANY);
        return qualifiers;
    }

    /**
     * Generates a unique signature for {@link Bean}.
     */
    static String createBeanId(Bean<?> bean) {
        return Stream.of(bean.getName(),
            bean.getScope().getName(),
            createAnnotationCollectionId(bean.getQualifiers()),
            createTypeCollectionId(bean.getTypes()))
            .filter(s -> s != null)
            .collect(Collectors.joining(","));
    }

    /**
     * Generates a unique signature of a collection of types.
     */
    private static String createTypeCollectionId(Collection<? extends Type> types) {
        return types.stream()
            .sorted((t1, t2) -> createTypeId(t1).compareTo(createTypeId(t2)))
            .map(CdiSpiHelper::createTypeId)
            .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Generates a unique signature for a {@link Type}.
     */
    private static String createTypeId(Type type) {
        if (type instanceof Class<?>) {
            return Class.class.cast(type).getName();
        }

        if (type instanceof ParameterizedType) {
            return createTypeId(((ParameterizedType) type).getRawType())
                + Stream.of(((ParameterizedType) type).getActualTypeArguments())
                .map(CdiSpiHelper::createTypeId)
                .collect(Collectors.joining(",", "<", ">"));
        }

        if (type instanceof TypeVariable<?>) {
            return TypeVariable.class.cast(type).getName();
        }

        if (type instanceof GenericArrayType) {
            return createTypeId(GenericArrayType.class.cast(type).getGenericComponentType());
        }

        throw new UnsupportedOperationException("Unable to create type id for type [" + type + "]");
    }

    /**
     * Generates a unique signature for a collection of annotations.
     */
    private static String createAnnotationCollectionId(Collection<Annotation> annotations) {
        if (annotations.isEmpty()) {
            return "";
        }

        return annotations.stream()
            .sorted((a1, a2) -> a1.annotationType().getName().compareTo(a2.annotationType().getName()))
            .map(CdiSpiHelper::createAnnotationId)
            .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Generates a unique signature for an {@link Annotation}.
     */
    private static String createAnnotationId(Annotation annotation) {
        Method[] methods = AccessController.doPrivileged(
            (PrivilegedAction<Method[]>) () -> annotation.annotationType().getDeclaredMethods());

        return Stream.of(methods)
            .sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
            .collect(() -> new StringJoiner(",", "@(", ")"),
                (joiner, method) -> {
                    try {
                        joiner
                            .add(method.getName()).add("=")
                            .add(method.invoke(annotation).toString());
                    } catch (NullPointerException | IllegalArgumentException | IllegalAccessException | InvocationTargetException cause) {
                        throw new RuntimeException(
                            "Error while accessing member [" + method.getName() + "]"
                                + " of annotation [" + annotation.annotationType().getName() + "]", cause);
                    }
                },
                StringJoiner::merge)
            .toString();
    }
}