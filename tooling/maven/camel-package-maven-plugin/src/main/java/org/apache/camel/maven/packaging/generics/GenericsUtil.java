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
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility classes for generic type operations.
 */
public final class GenericsUtil {

    /*
     * Private constructor
     */
    private GenericsUtil() {
    }

    /**
     * 5.2.3 and 5.2.4
     */
    public static boolean isAssignableFrom(boolean isDelegateOrEvent, boolean isProducer, Type requiredType, Type beanType) {
        if (requiredType instanceof Class) {
            return isAssignableFrom(isDelegateOrEvent, (Class<?>) requiredType, beanType);
        } else if (requiredType instanceof ParameterizedType) {
            return isAssignableFrom(isDelegateOrEvent, isProducer, (ParameterizedType) requiredType, beanType);
        } else if (requiredType instanceof TypeVariable) {
            return isAssignableFrom(isDelegateOrEvent, (TypeVariable<?>) requiredType, beanType);
        } else if (requiredType instanceof GenericArrayType) {
            return Class.class.isInstance(beanType) && Class.class.cast(beanType).isArray()
                    && isAssignableFrom(isDelegateOrEvent, (GenericArrayType) requiredType, beanType);
        } else if (requiredType instanceof WildcardType) {
            return isAssignableFrom(isDelegateOrEvent, (WildcardType) requiredType, beanType);
        } else {
            throw new IllegalArgumentException("Unsupported type " + requiredType.getClass());
        }
    }

    private static boolean isAssignableFrom(boolean isDelegateOrEvent, Class<?> injectionPointType, Type beanType) {
        if (beanType instanceof Class) {
            return isAssignableFrom(injectionPointType, (Class<?>) beanType);
        } else if (beanType instanceof TypeVariable) {
            return isAssignableFrom(isDelegateOrEvent, injectionPointType, (TypeVariable<?>) beanType);
        } else if (beanType instanceof ParameterizedType) {
            return isAssignableFrom(isDelegateOrEvent, injectionPointType, (ParameterizedType) beanType);
        } else if (beanType instanceof GenericArrayType) {
            return isAssignableFrom(isDelegateOrEvent, injectionPointType, (GenericArrayType) beanType);
        } else if (beanType instanceof WildcardType) {
            return isAssignableFrom(isDelegateOrEvent, (Type) injectionPointType, (WildcardType) beanType);
        } else {
            throw new IllegalArgumentException("Unsupported type " + injectionPointType);
        }
    }

    private static boolean isAssignableFrom(Class<?> injectionPointType, Class<?> beanType) {
        return ClassUtil.isClassAssignableFrom(injectionPointType, beanType);
    }

    private static boolean isAssignableFrom(boolean isDelegateOrEvent, Class<?> injectionPointType, TypeVariable<?> beanType) {
        for (Type bounds : beanType.getBounds()) {
            if (isAssignableFrom(isDelegateOrEvent, injectionPointType, bounds)) {
                return true;
            }
        }
        return false;
    }

    /**
     * CDI Spec. 5.2.4: "A parameterized bean type is considered assignable to a raw required type if the raw generics
     * are identical and all type parameters of the bean type are either unbounded type variables or java.lang.Object."
     */
    private static boolean isAssignableFrom(
            boolean isDelegateOrEvent, Class<?> injectionPointType, ParameterizedType beanType) {
        if (beanType.getRawType() != injectionPointType) {
            return false; // raw generics don't match
        }

        if (isDelegateOrEvent) {
            // for delegate and events we match 'in reverse' kind off
            // @Observes ProcessInjectionPoint<?, Instance> does also match
            // Instance<SomeBean>
            return isAssignableFrom(true, injectionPointType, beanType.getRawType());
        }

        for (Type typeArgument : beanType.getActualTypeArguments()) {
            if (typeArgument == Object.class) {
                continue;
            }
            if (!(typeArgument instanceof TypeVariable)) {
                return false; // neither object nor type variable
            }
            TypeVariable<?> typeVariable = (TypeVariable<?>) typeArgument;
            for (Type bounds : typeVariable.getBounds()) {
                if (bounds != Object.class) {
                    return false; // bound type variable
                }
            }
        }
        return true;
    }

    private static boolean isAssignableFrom(boolean isDelegateOrEvent, Class<?> injectionPointType, GenericArrayType beanType) {
        return injectionPointType.isArray() && isAssignableFrom(isDelegateOrEvent, injectionPointType.getComponentType(),
                beanType.getGenericComponentType());
    }

    private static boolean isAssignableFrom(boolean isDelegateOrEvent, Type injectionPointType, WildcardType beanType) {
        for (Type bounds : beanType.getLowerBounds()) {
            if (!isAssignableFrom(isDelegateOrEvent, false, bounds, injectionPointType)) {
                return false;
            }
        }
        for (Type bounds : beanType.getUpperBounds()) {
            if (isAssignableFrom(isDelegateOrEvent, false, injectionPointType, bounds)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssignableFrom(
            boolean isDelegateOrEvent, boolean isProducer, ParameterizedType injectionPointType, Type beanType) {
        if (beanType instanceof Class) {
            return isAssignableFrom(isDelegateOrEvent, isProducer, injectionPointType, (Class<?>) beanType);
        } else if (beanType instanceof TypeVariable) {
            return isAssignableFrom(isDelegateOrEvent, isProducer, injectionPointType, (TypeVariable<?>) beanType);
        } else if (beanType instanceof ParameterizedType) {
            return isAssignableFrom(isDelegateOrEvent, injectionPointType, (ParameterizedType) beanType);
        } else if (beanType instanceof WildcardType) {
            return isAssignableFrom(isDelegateOrEvent, injectionPointType, (WildcardType) beanType);
        } else if (beanType instanceof GenericArrayType) {
            return false;
        } else {
            throw new IllegalArgumentException("Unsupported type " + beanType.getClass());
        }
    }

    private static boolean isAssignableFrom(
            boolean isDelegateOrEvent, boolean isProducer, ParameterizedType injectionPointType, Class<?> beanType) {
        Class<?> rawInjectionPointType = getRawType(injectionPointType);
        if (rawInjectionPointType.equals(beanType)) {
            if (isProducer) {
                for (final Type t : injectionPointType.getActualTypeArguments()) {
                    if (!TypeVariable.class.isInstance(t) || !isNotBound(TypeVariable.class.cast(t).getBounds())) {
                        if (!Class.class.isInstance(t) || Object.class != t) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
        if (!rawInjectionPointType.isAssignableFrom(beanType)) {
            return false;
        }
        if (beanType.getSuperclass() != null
                && isAssignableFrom(isDelegateOrEvent, isProducer, injectionPointType, beanType.getGenericSuperclass())) {
            return true;
        }
        for (Type genericInterface : beanType.getGenericInterfaces()) {
            if (isAssignableFrom(isDelegateOrEvent, isProducer, injectionPointType, genericInterface)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssignableFrom(
            boolean isDelegateOrEvent, boolean isProducer, ParameterizedType injectionPointType, TypeVariable<?> beanType) {
        final Type[] types = beanType.getBounds();
        if (isNotBound(types)) {
            return true;
        }
        for (final Type bounds : types) {
            if (isAssignableFrom(isDelegateOrEvent, isProducer, injectionPointType, bounds)) {
                return true;
            }
        }
        return false;
    }

    /**
     * CDI Spec. 5.2.4
     */
    private static boolean isAssignableFrom(
            boolean isDelegateOrEvent, ParameterizedType injectionPointType, ParameterizedType beanType) {
        if (injectionPointType.getRawType() != beanType.getRawType()) {
            return false;
        }
        boolean swapParams = !isDelegateOrEvent;
        Type[] injectionPointTypeArguments = injectionPointType.getActualTypeArguments();
        Type[] beanTypeArguments = beanType.getActualTypeArguments();
        for (int i = 0; i < injectionPointTypeArguments.length; i++) {
            Type injectionPointTypeArgument = injectionPointTypeArguments[i];
            Type beanTypeArgument = beanTypeArguments[i];

            // for this special case it's actually an 'assignable to', thus we
            // swap the params, see CDI-389
            // but this special rule does not apply to Delegate injection
            // points...
            if (swapParams
                    && (injectionPointTypeArgument instanceof Class || injectionPointTypeArgument instanceof TypeVariable)
                    && beanTypeArgument instanceof TypeVariable) {
                final Type[] bounds = ((TypeVariable<?>) beanTypeArgument).getBounds();
                final boolean isNotBound = isNotBound(bounds);
                if (!isNotBound) {
                    for (final Type upperBound : bounds) {
                        if (!isAssignableFrom(true, false, upperBound, injectionPointTypeArgument)) {
                            return false;
                        }
                    }
                }
            } else if (swapParams && injectionPointTypeArgument instanceof TypeVariable) {
                return false;
            } else if (isDelegateOrEvent && injectionPointTypeArgument instanceof Class && beanTypeArgument instanceof Class) {
                // if no wildcard type was given then we require a real exact
                // match.
                return injectionPointTypeArgument.equals(beanTypeArgument);

            } else if (!isAssignableFrom(isDelegateOrEvent, false, injectionPointTypeArgument, beanTypeArgument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNotBound(final Type... bounds) {
        return bounds == null || bounds.length == 0 || bounds.length == 1 && Object.class == bounds[0];
    }

    private static boolean isAssignableFrom(boolean isDelegateOrEvent, TypeVariable<?> injectionPointType, Type beanType) {
        for (Type bounds : injectionPointType.getBounds()) {
            if (!isAssignableFrom(isDelegateOrEvent, false, bounds, beanType)) {
                return false;
            }
        }
        return true;
    }

    // rules are a bit different when in an array so we handle ParameterizedType
    // manually (not reusing isAssignableFrom)
    private static boolean isAssignableFrom(boolean isDelegateOrEvent, GenericArrayType injectionPointType, Type beanType) {
        final Type genericComponentType = injectionPointType.getGenericComponentType();
        final Class componentType = Class.class.cast(beanType).getComponentType();
        if (Class.class.isInstance(genericComponentType)) {
            return Class.class.cast(genericComponentType).isAssignableFrom(componentType);
        }
        if (ParameterizedType.class.isInstance(genericComponentType)) {
            return isAssignableFrom(isDelegateOrEvent, false, ParameterizedType.class.cast(genericComponentType).getRawType(),
                    componentType);
        }
        return isAssignableFrom(isDelegateOrEvent, false, genericComponentType, componentType);
    }

    private static boolean isAssignableFrom(boolean isDelegateOrEvent, WildcardType injectionPointType, Type beanType) {
        if (beanType instanceof TypeVariable) {
            return isAssignableFrom(isDelegateOrEvent, injectionPointType, (TypeVariable<?>) beanType);
        }
        for (Type bounds : injectionPointType.getLowerBounds()) {
            if (!isAssignableFrom(isDelegateOrEvent, false, beanType, bounds)) {
                return false;
            }
        }
        for (Type bounds : injectionPointType.getUpperBounds()) {
            Set<Type> beanTypeClosure = getTypeClosure(beanType);
            boolean isAssignable = false;
            for (Type beanSupertype : beanTypeClosure) {
                if (isAssignableFrom(isDelegateOrEvent, false, bounds, beanSupertype)
                        || Class.class.isInstance(bounds) && ParameterizedType.class.isInstance(beanSupertype)
                                && bounds == ParameterizedType.class.cast(beanSupertype).getRawType()) {
                    isAssignable = true;
                    break;
                }
            }
            if (!isAssignable) {
                return false;
            }
        }
        return true;
    }

    /**
     * CDI 1.1 Spec. 5.2.4, third bullet point
     */
    private static boolean isAssignableFrom(
            boolean isDelegateOrEvent, WildcardType injectionPointType, TypeVariable<?> beanType) {
        for (Type upperBound : injectionPointType.getUpperBounds()) {
            for (Type bound : beanType.getBounds()) {
                if (!isAssignableFrom(isDelegateOrEvent, false, upperBound, bound)
                        && !isAssignableFrom(isDelegateOrEvent, false, bound, upperBound)) {
                    return false;
                }
            }
        }
        for (Type lowerBound : injectionPointType.getLowerBounds()) {
            for (Type bound : beanType.getBounds()) {
                if (!isAssignableFrom(isDelegateOrEvent, false, bound, lowerBound)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @return <tt>true</tt>, if the specified type declaration contains an unresolved type variable.
     */
    public static boolean containsTypeVariable(Type type) {
        if (type instanceof Class) {
            return false;
        } else if (type instanceof TypeVariable) {
            return true;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return containTypeVariable(parameterizedType.getActualTypeArguments());
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            return containTypeVariable(wildcardType.getUpperBounds()) || containTypeVariable(wildcardType.getLowerBounds());
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            return containsTypeVariable(arrayType.getGenericComponentType());
        } else {
            throw new IllegalArgumentException("Unsupported type " + type.getClass().getName());
        }

    }

    public static boolean containTypeVariable(Type[] types) {
        for (Type type : types) {
            if (containsTypeVariable(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the actual type of the specified field for the type hierarchy specified by the given subclass
     */
    public static Type resolveType(Class<?> subclass, Field field) {
        return resolveType(field.getGenericType(), subclass, newSeenList());
    }

    /**
     * Resolves the actual type of the specified field for the type hierarchy specified by the given subclass
     */
    public static Type resolveSetterType(Class<?> subclass, Method method) {
        return resolveType(method.getParameterTypes()[0], subclass, newSeenList());
    }

    /**
     * Resolves the actual parameter generics of the specified method for the type hierarchy specified by the given
     * subclass
     */
    public static Type[] resolveParameterTypes(Class<?> subclass, Method method) {
        return resolveTypes(method.getGenericParameterTypes(), subclass);
    }

    public static Type resolveType(Type type, Type actualType, Collection<TypeVariable<?>> seen) {
        if (type instanceof Class) {
            return type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;

            Type[] resolvedTypeArguments;
            if (Enum.class.equals(parameterizedType.getRawType())) {
                // Enums derive from themselves, which would create an infinite
                // loop
                // we directly escape the loop if we detect this.
                resolvedTypeArguments = new Type[] { new OwbWildcardTypeImpl(new Type[] { Enum.class }, ClassUtil.NO_TYPES) };
            } else {
                resolvedTypeArguments = resolveTypes(parameterizedType.getActualTypeArguments(), actualType, seen);

            }

            return new OwbParametrizedTypeImpl(
                    parameterizedType.getOwnerType(), parameterizedType.getRawType(), resolvedTypeArguments);
        } else if (type instanceof TypeVariable) {
            TypeVariable<?> variable = (TypeVariable<?>) type;
            return resolveTypeVariable(variable, actualType, seen);
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = resolveTypes(wildcardType.getUpperBounds(), actualType, seen);
            Type[] lowerBounds = resolveTypes(wildcardType.getLowerBounds(), actualType, seen);
            return new OwbWildcardTypeImpl(upperBounds, lowerBounds);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            return createArrayType(resolveType(arrayType.getGenericComponentType(), actualType, seen));
        } else {
            throw new IllegalArgumentException("Unsupported type " + type.getClass().getName());
        }
    }

    public static Type[] resolveTypes(Type[] types, Type actualType, Collection<TypeVariable<?>> seen) {
        Type[] resolvedTypeArguments = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            final Type type = resolveType(types[i], actualType, seen);
            if (type != null) { // means a stackoverflow was avoided, just keep
                               // what we have
                resolvedTypeArguments[i] = type;
            }
        }
        return resolvedTypeArguments;
    }

    public static Type[] resolveTypes(Type[] types, Type actualType) {
        Type[] resolvedTypeArguments = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            resolvedTypeArguments[i] = resolveType(types[i], actualType, newSeenList());
        }
        return resolvedTypeArguments;
    }

    public static Set<Type> getTypeClosure(Type actualType) {
        return getTypeClosure(actualType, actualType);
    }

    /**
     * Returns the type closure for the specified parameters.
     * <h3>Example 1:</h3>
     * <p>
     * Take the following classes:
     * </p>
     * <code>
     * public class Foo<T> {
     * private T t;
     * }
     * public class Bar extends Foo<Number> {
     * }
     * </code>
     * <p>
     * To get the type closure of T in the context of Bar (which is {Number.class, Object.class}), you have to call this
     * method like
     * </p>
     * <code>
     * GenericUtil.getTypeClosure(Foo.class.getDeclaredField("t").getType(), Bar.class, Foo.class);
     * </code>
     * <h3>Example 2:</h3>
     * <p>
     * Take the following classes:
     * </p>
     * <code>
     * public class Foo<T> {
     * private T t;
     * }
     * public class Bar<T> extends Foo<T> {
     * }
     * </code>
     * <p>
     * To get the type closure of Bar<T> in the context of Foo<Number> (which are besides Object.class the
     * <tt>ParameterizedType</tt>s Bar<Number> and Foo<Number>), you have to call this method like
     * </p>
     * <code>
     * GenericUtil.getTypeClosure(Foo.class, new TypeLiteral<Foo<Number>>() {}.getType(), Bar.class);
     * </code>
     *
     * @param  type       the type to get the closure for
     * @param  actualType the context to bind type variables
     * @return            the type closure
     */
    public static Set<Type> getTypeClosure(Type type, Type actualType) {
        Class<?> rawType = getRawType(type);
        Class<?> actualRawType = getRawType(actualType);
        if (rawType.isAssignableFrom(actualRawType) && rawType != actualRawType) {
            return getTypeClosure(actualType, type);
        }
        if (hasTypeParameters(type)) {
            type = getParameterizedType(type);
        }
        return getDirectTypeClosure(type, actualType);
    }

    public static Set<Type> getDirectTypeClosure(final Type type, final Type actualType) {
        Set<Type> typeClosure = new HashSet<>();
        typeClosure.add(Object.class);
        fillTypeHierarchy(typeClosure, type, actualType);
        return typeClosure;
    }

    private static void fillTypeHierarchy(Set<Type> set, Type type, Type actualType) {
        if (type == null) {
            return;
        }
        Type resolvedType = GenericsUtil.resolveType(type, actualType, newSeenList());
        set.add(resolvedType);
        Class<?> resolvedClass = GenericsUtil.getRawType(resolvedType, actualType);
        if (resolvedClass.getSuperclass() != null) {
            fillTypeHierarchy(set, resolvedClass.getGenericSuperclass(), resolvedType);
        }
        for (Type interfaceType : resolvedClass.getGenericInterfaces()) {
            fillTypeHierarchy(set, interfaceType, resolvedType);
        }
    }

    private static Collection<TypeVariable<?>> newSeenList() {
        return new ArrayList<>();
    }

    public static boolean hasTypeParameters(Type type) {
        if (type instanceof Class) {
            Class<?> classType = (Class<?>) type;
            return classType.getTypeParameters().length > 0;
        }
        return false;
    }

    public static ParameterizedType getParameterizedType(Type type) {
        if (type instanceof ParameterizedType) {
            return (ParameterizedType) type;
        } else if (type instanceof Class) {
            Class<?> classType = (Class<?>) type;
            return new OwbParametrizedTypeImpl(classType.getDeclaringClass(), classType, classType.getTypeParameters());
        } else {
            throw new IllegalArgumentException(type.getClass().getSimpleName() + " is not supported");
        }
    }

    public static <T> Class<T> getRawType(Type type) {
        return getRawType(type, null);
    }

    static <T> Class<T> getRawType(Type type, Type actualType) {
        if (type instanceof Class) {
            return (Class<T>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return getRawType(parameterizedType.getRawType(), actualType);
        } else if (type instanceof TypeVariable) {
            TypeVariable<?> typeVariable = (TypeVariable<?>) type;
            Type mostSpecificType
                    = getMostSpecificType(getRawTypes(typeVariable.getBounds(), actualType), typeVariable.getBounds());
            return getRawType(mostSpecificType, actualType);
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type mostSpecificType = getMostSpecificType(getRawTypes(wildcardType.getUpperBounds(), actualType),
                    wildcardType.getUpperBounds());
            return getRawType(mostSpecificType, actualType);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            return getRawType(createArrayType(getRawType(arrayType.getGenericComponentType(), actualType)), actualType);
        } else {
            throw new IllegalArgumentException("Unsupported type " + type.getClass().getName());
        }
    }

    private static <T> Class<T>[] getRawTypes(Type[] types) {
        return getRawTypes(types, null);
    }

    private static <T> Class<T>[] getRawTypes(Type[] types, Type actualType) {
        Class<T>[] rawTypes = new Class[types.length];
        for (int i = 0; i < types.length; i++) {
            rawTypes[i] = getRawType(types[i], actualType);
        }
        return rawTypes;
    }

    private static Type getMostSpecificType(Class<?>[] types, Type[] genericTypes) {
        Class<?> mostSpecificType = types[0];
        int mostSpecificIndex = 0;
        for (int i = 0; i < types.length; i++) {
            if (mostSpecificType.isAssignableFrom(types[i])) {
                mostSpecificType = types[i];
                mostSpecificIndex = i;
            }
        }
        return genericTypes[mostSpecificIndex];
    }

    private static Type resolveTypeVariable(TypeVariable<?> variable, Type actualType, Collection<TypeVariable<?>> seen) {
        if (actualType == null) {
            return variable;
        }
        Class<?> declaringClass = getDeclaringClass(variable.getGenericDeclaration());
        Class<?> actualClass = getRawType(actualType);
        if (actualClass == declaringClass) {
            return resolveTypeVariable(variable, variable.getGenericDeclaration(), getParameterizedType(actualType), seen);
        } else if (actualClass.isAssignableFrom(declaringClass)) {
            Class<?> directSubclass = getDirectSubclass(declaringClass, actualClass);
            Type[] typeArguments = resolveTypeArguments(directSubclass, actualType);
            Type directSubtype = new OwbParametrizedTypeImpl(directSubclass.getDeclaringClass(), directSubclass, typeArguments);
            return resolveTypeVariable(variable, directSubtype, seen);
        } else {
            Type genericSuperclass = getGenericSuperclass(actualClass, declaringClass);
            if (genericSuperclass == null) {
                return variable;
            } else if (genericSuperclass instanceof Class) {
                // special handling for type erasure
                Class<?> superclass = (Class<?>) genericSuperclass;
                genericSuperclass = new OwbParametrizedTypeImpl(
                        superclass.getDeclaringClass(), superclass, getRawTypes(superclass.getTypeParameters()));
            } else {
                ParameterizedType genericSupertype = getParameterizedType(genericSuperclass);
                Type[] typeArguments = resolveTypeArguments(getParameterizedType(actualType), genericSupertype);
                genericSuperclass = new OwbParametrizedTypeImpl(
                        genericSupertype.getOwnerType(), genericSupertype.getRawType(), typeArguments);
            }
            Type resolvedType = resolveTypeVariable(variable, genericSuperclass, seen);
            if (resolvedType instanceof TypeVariable) {
                TypeVariable<?> resolvedTypeVariable = (TypeVariable<?>) resolvedType;
                TypeVariable<?>[] typeParameters = actualClass.getTypeParameters();
                for (int i = 0; i < typeParameters.length; i++) {
                    if (typeParameters[i].getName().equals(resolvedTypeVariable.getName())) {
                        resolvedType = getParameterizedType(actualType).getActualTypeArguments()[i];
                        break;
                    }
                }
            }
            return resolvedType;
        }
    }

    private static Class<?> getDeclaringClass(GenericDeclaration declaration) {
        if (declaration instanceof Class) {
            return (Class<?>) declaration;
        } else if (declaration instanceof Member) {
            return ((Member) declaration).getDeclaringClass();
        } else {
            throw new IllegalArgumentException("Unsupported type " + declaration.getClass());
        }
    }

    private static Type resolveTypeVariable(
            TypeVariable<?> variable, GenericDeclaration declaration, ParameterizedType type,
            Collection<TypeVariable<?>> seen) {
        int index = getIndex(declaration, variable);
        if (declaration instanceof Class) {
            if (index >= 0) {
                return type.getActualTypeArguments()[index];
            } else {
                index = getIndex(type, variable);
                if (index >= 0) {
                    return declaration.getTypeParameters()[index];
                }
            }
        } else {
            if (seen.contains(variable)) {
                return null;
            }
            seen.add(variable);

            Type[] resolvedBounds = resolveTypes(declaration.getTypeParameters()[index].getBounds(), type, seen);
            return OwbTypeVariableImpl.createTypeVariable(variable, resolvedBounds);
        }
        return variable;
    }

    private static int getIndex(GenericDeclaration declaration, TypeVariable<?> variable) {
        Type[] typeParameters = declaration.getTypeParameters();
        for (int i = 0; i < typeParameters.length; i++) {
            if (typeParameters[i] instanceof TypeVariable) {
                TypeVariable<?> variableArgument = (TypeVariable<?>) typeParameters[i];
                if (variableArgument.getName().equals(variable.getName())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int getIndex(ParameterizedType type, TypeVariable<?> variable) {
        Type[] actualTypeArguments = type.getActualTypeArguments();
        for (int i = 0; i < actualTypeArguments.length; i++) {
            if (actualTypeArguments[i] instanceof TypeVariable) {
                TypeVariable<?> variableArgument = (TypeVariable<?>) actualTypeArguments[i];
                if (variableArgument.getName().equals(variable.getName())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Class<?> getDirectSubclass(Class<?> declaringClass, Class<?> actualClass) {
        if (actualClass.isInterface()) {
            Class<?> subclass = declaringClass;
            for (Class<?> iface : declaringClass.getInterfaces()) {
                if (iface == actualClass) {
                    return subclass;
                }
                if (actualClass.isAssignableFrom(iface)) {
                    subclass = iface;
                } else {
                    subclass = declaringClass.getSuperclass();
                }
            }
            return getDirectSubclass(subclass, actualClass);
        } else {
            Class<?> directSubclass = declaringClass;
            while (directSubclass.getSuperclass() != actualClass) {
                directSubclass = directSubclass.getSuperclass();
            }
            return directSubclass;
        }
    }

    private static Type getGenericSuperclass(Class<?> subclass, Class<?> superclass) {
        if (!superclass.isInterface()) {
            return subclass.getGenericSuperclass();
        } else {
            for (Type genericInterface : subclass.getGenericInterfaces()) {
                if (getRawType(genericInterface) == superclass) {
                    return genericInterface;
                }
            }
        }
        return superclass;
    }

    private static Type[] resolveTypeArguments(Class<?> subclass, Type supertype) {
        if (supertype instanceof ParameterizedType) {
            ParameterizedType parameterizedSupertype = (ParameterizedType) supertype;
            return resolveTypeArguments(subclass, parameterizedSupertype);
        } else {
            return subclass.getTypeParameters();
        }
    }

    private static Type[] resolveTypeArguments(Class<?> subclass, ParameterizedType parameterizedSupertype) {
        Type genericSuperclass = getGenericSuperclass(subclass, getRawType(parameterizedSupertype));
        if (!(genericSuperclass instanceof ParameterizedType)) {
            return subclass.getTypeParameters();
        }
        ParameterizedType parameterizedSuperclass = (ParameterizedType) genericSuperclass;
        Type[] typeParameters = subclass.getTypeParameters();
        Type[] actualTypeArguments = parameterizedSupertype.getActualTypeArguments();
        return resolveTypeArguments(parameterizedSuperclass, typeParameters, actualTypeArguments);
    }

    private static Type[] resolveTypeArguments(ParameterizedType subtype, ParameterizedType parameterizedSupertype) {
        return resolveTypeArguments(getParameterizedType(getRawType(subtype)), parameterizedSupertype.getActualTypeArguments(),
                subtype.getActualTypeArguments());
    }

    private static Type[] resolveTypeArguments(
            ParameterizedType parameterizedType, Type[] typeParameters, Type[] actualTypeArguments) {
        Type[] resolvedTypeArguments = new Type[typeParameters.length];
        for (int i = 0; i < typeParameters.length; i++) {
            resolvedTypeArguments[i] = resolveTypeArgument(parameterizedType, typeParameters[i], actualTypeArguments);
        }
        return resolvedTypeArguments;
    }

    private static Type resolveTypeArgument(
            ParameterizedType parameterizedType, Type typeParameter, Type[] actualTypeArguments) {
        if (typeParameter instanceof TypeVariable) {
            TypeVariable<?> variable = (TypeVariable<?>) typeParameter;
            int index = getIndex(parameterizedType, variable);
            if (index == -1) {
                return typeParameter;
            } else {
                return actualTypeArguments[index];
            }
        } else if (typeParameter instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) typeParameter;
            return createArrayType(
                    resolveTypeArgument(parameterizedType, array.getGenericComponentType(), actualTypeArguments));
        } else {
            return typeParameter;
        }
    }

    private static Type createArrayType(Type componentType) {
        if (componentType instanceof Class) {
            return Array.newInstance((Class<?>) componentType, 0).getClass();
        } else {
            return new OwbGenericArrayTypeImpl(componentType);
        }
    }
}
