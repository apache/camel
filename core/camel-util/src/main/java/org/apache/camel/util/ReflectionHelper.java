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
package org.apache.camel.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Helper for working with reflection on classes.
 * <p/>
 * This code is based on org.apache.camel.spring.util.ReflectionUtils class.
 */
public final class ReflectionHelper {

    private ReflectionHelper() {
        // utility class
    }

    /**
     * Callback interface invoked on each field in the hierarchy.
     */
    @FunctionalInterface
    public interface FieldCallback {

        /**
         * Perform an operation using the given field.
         *
         * @param field the field to operate on
         */
        void doWith(Field field) throws IllegalArgumentException, IllegalAccessException;
    }

    /**
     * Action to take on each method.
     */
    @FunctionalInterface
    public interface MethodCallback {

        /**
         * Perform an operation using the given method.
         *
         * @param method the method to operate on
         */
        void doWith(Method method) throws IllegalArgumentException, IllegalAccessException;
    }

    /**
     * Action to take on each class.
     */
    @FunctionalInterface
    public interface ClassCallback {

        /**
         * Perform an operation using the given class.
         *
         * @param clazz the class to operate on
         */
        void doWith(Class<?> clazz) throws IllegalArgumentException, IllegalAccessException;
    }

    /**
     * Perform the given callback operation on the nested (inner) classes.
     *
     * @param clazz class to start looking at
     * @param cc    the callback to invoke for each inner class (excluding the class itself)
     */
    public static void doWithClasses(Class<?> clazz, ClassCallback cc) throws IllegalArgumentException {
        // and then nested classes
        Class<?>[] classes = clazz.getDeclaredClasses();
        for (Class<?> aClazz : classes) {
            try {
                cc.doWith(aClazz);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Shouldn't be illegal to access class '" + aClazz.getName() + "': " + ex);
            }
        }
    }

    /**
     * Invoke the given callback on all fields in the target class, going up the class hierarchy to get all declared
     * fields.
     *
     * @param clazz the target class to analyze
     * @param fc    the callback to invoke for each field
     */
    public static void doWithFields(Class<?> clazz, FieldCallback fc) throws IllegalArgumentException {
        // Keep backing up the inheritance hierarchy.
        Class<?> targetClass = clazz;
        do {
            Field[] fields = targetClass.getDeclaredFields();
            for (Field field : fields) {
                try {
                    fc.doWith(field);
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException("Shouldn't be illegal to access field '" + field.getName() + "': " + ex);
                }
            }
            targetClass = targetClass.getSuperclass();
        } while (targetClass != null && targetClass != Object.class);
    }

    /**
     * Perform the given callback operation on all matching methods of the given class and superclasses (or given
     * interface and super-interfaces).
     * <p/>
     * <b>Important:</b> This method does not take the {@link java.lang.reflect.Method#isBridge() bridge methods} into
     * account.
     *
     * @param clazz class to start looking at
     * @param mc    the callback to invoke for each method
     */
    public static void doWithMethods(Class<?> clazz, MethodCallback mc) throws IllegalArgumentException {
        // Keep backing up the inheritance hierarchy.
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isBridge()) {
                // skip the bridge methods which in Java 8 leads to problems with inheritance
                // see https://bugs.openjdk.java.net/browse/JDK-6695379
                continue;
            }
            try {
                mc.doWith(method);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Shouldn't be illegal to access method '" + method.getName() + "': " + ex);
            }
        }
        if (clazz.getSuperclass() != null) {
            doWithMethods(clazz.getSuperclass(), mc);
        } else if (clazz.isInterface()) {
            for (Class<?> superIfc : clazz.getInterfaces()) {
                doWithMethods(superIfc, mc);
            }
        }
    }

    /**
     * Attempt to find a {@link Method} on the supplied class with the supplied name and parameter types. Searches all
     * superclasses up to {@code Object}.
     * <p>
     * Returns {@code null} if no {@link Method} can be found.
     *
     * @param  clazz      the class to introspect
     * @param  name       the name of the method
     * @param  paramTypes the parameter types of the method (may be {@code null} to indicate any signature)
     * @return            the Method object, or {@code null} if none found
     */
    public static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        ObjectHelper.notNull(clazz, "Class must not be null");
        ObjectHelper.notNull(name, "Method name must not be null");
        Class<?> searchType = clazz;
        while (searchType != null) {
            Method[] methods = searchType.isInterface() ? searchType.getMethods() : searchType.getDeclaredMethods();
            for (Method method : methods) {
                if (name.equals(method.getName())
                        && (paramTypes == null || Arrays.equals(paramTypes, method.getParameterTypes()))) {
                    return method;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    public static void setField(Field f, Object instance, Object value) {
        try {
            boolean oldAccessible = false;
            try {
                oldAccessible = f.canAccess(instance);
            } catch (Exception e) {
                // ignore
            }
            boolean shouldSetAccessible = !Modifier.isPublic(f.getModifiers()) && !oldAccessible;
            if (shouldSetAccessible) {
                f.setAccessible(true);
            }
            f.set(instance, value);
            if (shouldSetAccessible) {
                f.setAccessible(oldAccessible);
            }
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Cannot inject value of class: " + value.getClass() + " into: " + f);
        }
    }

    public static Object getField(Field f, Object instance) {
        try {
            boolean oldAccessible = false;
            try {
                oldAccessible = f.canAccess(instance);
            } catch (Exception e) {
                // ignore
            }
            boolean shouldSetAccessible = !Modifier.isPublic(f.getModifiers()) && !oldAccessible;
            if (shouldSetAccessible) {
                f.setAccessible(true);
            }
            Object answer = f.get(instance);
            if (shouldSetAccessible) {
                f.setAccessible(oldAccessible);
            }
            return answer;
        } catch (Exception ex) {
            // ignore
        }
        return null;
    }

}
