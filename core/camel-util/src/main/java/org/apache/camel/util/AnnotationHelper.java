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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class AnnotationHelper {

    /**
     * Returns a list of methods which are annotated with the given annotation
     *
     * @param  type           the type to reflect on
     * @param  annotationType the annotation type
     * @return                a list of the methods found
     */
    public static List<Method> findMethodsWithAnnotation(
            Class<?> type,
            Class<? extends Annotation> annotationType) {
        return findMethodsWithAnnotation(type, annotationType, false);
    }

    /**
     * Returns a list of methods which are annotated with the given annotation
     *
     * @param  type                 the type to reflect on
     * @param  annotationType       the annotation type
     * @param  checkMetaAnnotations check for meta annotations
     * @return                      a list of the methods found
     */
    public static List<Method> findMethodsWithAnnotation(
            Class<?> type,
            Class<? extends Annotation> annotationType,
            boolean checkMetaAnnotations) {
        List<Method> answer = new ArrayList<>();
        do {
            Method[] methods = type.getDeclaredMethods();
            for (Method method : methods) {
                if (hasAnnotation(method, annotationType, checkMetaAnnotations)) {
                    answer.add(method);
                }
            }
            type = type.getSuperclass();
        } while (type != null);
        return answer;
    }

    /**
     * Checks if a Class or Method are annotated with the given annotation
     *
     * @param  elem                 the Class or Method to reflect on
     * @param  annotationType       the annotation type
     * @param  checkMetaAnnotations check for meta annotations
     * @return                      true if annotations is present
     */
    public static boolean hasAnnotation(
            AnnotatedElement elem, Class<? extends Annotation> annotationType,
            boolean checkMetaAnnotations) {
        if (elem.isAnnotationPresent(annotationType)) {
            return true;
        }
        if (checkMetaAnnotations) {
            for (Annotation a : elem.getAnnotations()) {
                for (Annotation meta : a.annotationType().getAnnotations()) {
                    // NOTE: we perform an equality check as, by design, this has
                    // to be also supported in OSGI environments, therefore, instance equality may differ.
                    if (meta.annotationType().getName().equals(annotationType.getName())) { // NOSONAR
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if the class has been annotated with the given annotation (FQN class name) and if present, then returns
     * the value attribute.
     *
     * @param  clazz             the class
     * @param  fqnAnnotationName the FQN of the annotation
     * @return                   null if not annotated, otherwise the value, an empty string means annotation but has no
     *                           value
     */
    public static String getAnnotationValue(Class<?> clazz, String fqnAnnotationName) {
        for (Annotation ann : clazz.getAnnotations()) {
            if (ann.annotationType().getName().equals(fqnAnnotationName)) {
                String s = ann.toString();
                return StringHelper.between(s, "\"", "\"");
            }
        }
        return null;
    }

    /**
     * Checks if the class has been annotated with the given annotation (FQN class name).
     *
     * @param  clazz             the class
     * @param  fqnAnnotationName the FQN of the annotation
     * @return                   true if annotated or false if not
     */
    public static boolean hasAnnotation(Class<?> clazz, String fqnAnnotationName) {
        for (Annotation ann : clazz.getAnnotations()) {
            if (ann.annotationType().getName().equals(fqnAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the method has been annotated with the given annotation (FQN class name).
     *
     * @param  method            the method
     * @param  fqnAnnotationName the FQN of the annotation
     * @return                   true if annotated or false if not
     */
    public static boolean hasAnnotation(Method method, String fqnAnnotationName) {
        for (Annotation ann : method.getAnnotations()) {
            if (ann.annotationType().getName().equals(fqnAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the method has been annotated with the given annotation (FQN class name) and if present, then returns
     * the value attribute.
     *
     * @param  method            the method
     * @param  fqnAnnotationName the FQN of the annotation
     * @return                   null if not annotated, otherwise the value, an empty string means annotation but has no
     *                           value
     */
    public static String getAnnotationValue(Method method, String fqnAnnotationName) {
        return (String) getAnnotationValue(method, fqnAnnotationName, "value");
    }

    /**
     * Checks if the method has been annotated with the given annotation (FQN class name) and if present, then returns
     * the value attribute.
     *
     * @param  method            the field
     * @param  key               the annotation key
     * @param  fqnAnnotationName the FQN of the annotation
     * @return                   null if not annotated, otherwise the value, an empty string means annotation but has no
     *                           value
     */
    public static Object getAnnotationValue(Method method, String fqnAnnotationName, String key) {
        for (Annotation ann : method.getAnnotations()) {
            if (ann.annotationType().getName().equals(fqnAnnotationName)) {
                try {
                    Method m = ann.getClass().getDeclaredMethod(key);
                    return m.invoke(ann);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Checks if the field has been annotated with the given annotation (FQN class name).
     *
     * @param  field             the field
     * @param  fqnAnnotationName the FQN of the annotation
     * @return                   true if annotated or false if not
     */
    public static boolean hasAnnotation(Field field, String fqnAnnotationName) {
        for (Annotation ann : field.getAnnotations()) {
            if (ann.annotationType().getName().equals(fqnAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the field has been annotated with the given annotation (FQN class name) and if present, then returns
     * the value attribute.
     *
     * @param  field             the field
     * @param  fqnAnnotationName the FQN of the annotation
     * @return                   null if not annotated, otherwise the value, an empty string means annotation but has no
     *                           value
     */
    public static String getAnnotationValue(Field field, String fqnAnnotationName) {
        return (String) getAnnotationValue(field, fqnAnnotationName, "value");
    }

    /**
     * Checks if the field has been annotated with the given annotation (FQN class name) and if present, then returns
     * the value attribute.
     *
     * @param  field             the field
     * @param  key               the annotation key
     * @param  fqnAnnotationName the FQN of the annotation
     * @return                   null if not annotated, otherwise the value, an empty string means annotation but has no
     *                           value
     */
    public static Object getAnnotationValue(Field field, String fqnAnnotationName, String key) {
        for (Annotation ann : field.getAnnotations()) {
            if (ann.annotationType().getName().equals(fqnAnnotationName)) {
                try {
                    Method m = ann.getClass().getDeclaredMethod(key);
                    return m.invoke(ann);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

}
